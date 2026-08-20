#requires -Version 5.1

[CmdletBinding()]
param(
    [string]$ImageName = "localhost/opentrack-android-builder:android36-v1",
    [string]$SigningDirectory,
    [switch]$RebuildImage,
    [switch]$Incremental,
    [switch]$SkipTests
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
$containerfile = Join-Path $projectRoot "Containerfile.android"
$utf8WithoutBom = [System.Text.UTF8Encoding]::new($false)
$temporaryFiles = [System.Collections.Generic.List[string]]::new()

function Invoke-Podman {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    & $script:podmanCommand @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Podman failed with exit code $LASTEXITCODE while running: podman $($Arguments -join ' ')"
    }
}

function New-RandomSecret {
    $bytes = New-Object byte[] 32
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    }
    finally {
        $generator.Dispose()
    }

    return -join ($bytes | ForEach-Object { $_.ToString("x2") })
}

function Write-EnvironmentFile {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][hashtable]$Values
    )

    foreach ($value in $Values.Values) {
        if ($value -match "[`r`n]") {
            throw "Signing values must not contain newline characters."
        }
    }

    $lines = @(
        "OPENTRACK_KEYSTORE_FILE=$($Values.OPENTRACK_KEYSTORE_FILE)",
        "OPENTRACK_KEYSTORE_PASSWORD=$($Values.OPENTRACK_KEYSTORE_PASSWORD)",
        "OPENTRACK_KEY_ALIAS=$($Values.OPENTRACK_KEY_ALIAS)",
        "OPENTRACK_KEY_PASSWORD=$($Values.OPENTRACK_KEY_PASSWORD)"
    )
    [System.IO.File]::WriteAllLines($Path, $lines, $script:utf8WithoutBom)
}

function Read-EnvironmentFile {
    param([Parameter(Mandatory = $true)][string]$Path)

    $values = @{}
    foreach ($line in [System.IO.File]::ReadAllLines($Path)) {
        if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith("#")) {
            continue
        }

        $separator = $line.IndexOf("=")
        if ($separator -le 0) {
            throw "Invalid signing environment line in '$Path'."
        }

        $values[$line.Substring(0, $separator)] = $line.Substring($separator + 1)
    }

    $requiredNames = @(
        "OPENTRACK_KEYSTORE_FILE",
        "OPENTRACK_KEYSTORE_PASSWORD",
        "OPENTRACK_KEY_ALIAS",
        "OPENTRACK_KEY_PASSWORD"
    )
    foreach ($name in $requiredNames) {
        if (-not $values.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($values[$name])) {
            throw "Signing environment file '$Path' is missing $name."
        }
    }

    return $values
}

function Get-FullPath {
    param([Parameter(Mandatory = $true)][string]$Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $Path))
}

$podman = Get-Command podman -ErrorAction SilentlyContinue
if ($null -eq $podman) {
    throw "Podman was not found on PATH. Install Podman Desktop and start its machine first."
}
$podmanCommand = $podman.Source

if (-not (Test-Path -LiteralPath $containerfile -PathType Leaf)) {
    throw "Containerfile not found: $containerfile"
}

try {
    & $podmanCommand "info" *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Podman is installed, but its machine is not available. Start it with 'podman machine start'."
    }

    & $podmanCommand "image" "exists" $ImageName
    $imageExists = $LASTEXITCODE -eq 0
    if ($RebuildImage -or -not $imageExists) {
        Write-Host "Building the pinned Android toolchain image '$ImageName'..."
        $imageBuildArguments = [System.Collections.Generic.List[string]]::new()
        $imageBuildArguments.AddRange([string[]]@(
            "build",
            "--platform", "linux/amd64",
            "--file", $containerfile,
            "--tag", $ImageName
        ))
        if ($RebuildImage) {
            $imageBuildArguments.Add("--no-cache")
            $imageBuildArguments.Add("--pull=always")
        }
        else {
            $imageBuildArguments.Add("--pull=missing")
        }
        $imageBuildArguments.Add($projectRoot)
        Invoke-Podman -Arguments $imageBuildArguments.ToArray()
    }

    $externalSigningNames = @(
        "OPENTRACK_KEYSTORE_FILE",
        "OPENTRACK_KEYSTORE_PASSWORD",
        "OPENTRACK_KEY_ALIAS",
        "OPENTRACK_KEY_PASSWORD"
    )
    $externalSigningCount = 0
    foreach ($name in $externalSigningNames) {
        if (-not [string]::IsNullOrWhiteSpace([System.Environment]::GetEnvironmentVariable($name))) {
            $externalSigningCount++
        }
    }

    $needsKeyGeneration = $false
    if ($externalSigningCount -gt 0) {
        if ($externalSigningCount -ne $externalSigningNames.Count) {
            throw "External signing is only partially configured. Set all four OPENTRACK signing environment variables."
        }

        $keystoreHostPath = Get-FullPath -Path $env:OPENTRACK_KEYSTORE_FILE
        if (-not (Test-Path -LiteralPath $keystoreHostPath -PathType Leaf)) {
            throw "External release keystore not found: $keystoreHostPath"
        }

        $signingEnvironmentPath = Join-Path ([System.IO.Path]::GetTempPath()) "opentrack-signing-$PID.env"
        $temporaryFiles.Add($signingEnvironmentPath)
        Write-EnvironmentFile -Path $signingEnvironmentPath -Values @{
            OPENTRACK_KEYSTORE_FILE = "/signing/opentrack-release-keystore"
            OPENTRACK_KEYSTORE_PASSWORD = $env:OPENTRACK_KEYSTORE_PASSWORD
            OPENTRACK_KEY_ALIAS = $env:OPENTRACK_KEY_ALIAS
            OPENTRACK_KEY_PASSWORD = $env:OPENTRACK_KEY_PASSWORD
        }
        $signingVolume = "${keystoreHostPath}:/signing/opentrack-release-keystore:ro"
        Write-Host "Using the externally supplied release signing key."
    }
    else {
        if ([string]::IsNullOrWhiteSpace($SigningDirectory)) {
            if ([string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
                $SigningDirectory = Join-Path $projectRoot ".signing"
            }
            else {
                $SigningDirectory = Join-Path $env:LOCALAPPDATA "OpenTrack\signing"
            }
        }

        $signingHostDirectory = Get-FullPath -Path $SigningDirectory
        [System.IO.Directory]::CreateDirectory($signingHostDirectory) | Out-Null
        $keystoreHostPath = Join-Path $signingHostDirectory "opentrack-release.p12"
        $signingEnvironmentPath = Join-Path $signingHostDirectory "opentrack-release.env"

        if ((Test-Path -LiteralPath $keystoreHostPath -PathType Leaf) -and
            -not (Test-Path -LiteralPath $signingEnvironmentPath -PathType Leaf)) {
            throw "The managed keystore exists but its credentials file is missing: $signingEnvironmentPath"
        }

        if (-not (Test-Path -LiteralPath $signingEnvironmentPath -PathType Leaf)) {
            $secret = New-RandomSecret
            Write-EnvironmentFile -Path $signingEnvironmentPath -Values @{
                OPENTRACK_KEYSTORE_FILE = "/signing/opentrack-release.p12"
                OPENTRACK_KEYSTORE_PASSWORD = $secret
                OPENTRACK_KEY_ALIAS = "opentrack"
                OPENTRACK_KEY_PASSWORD = $secret
            }
        }

        $signingValues = Read-EnvironmentFile -Path $signingEnvironmentPath
        if ($signingValues.OPENTRACK_KEYSTORE_FILE -ne "/signing/opentrack-release.p12") {
            throw "Managed signing file has an unexpected container keystore path."
        }

        $needsKeyGeneration = -not (Test-Path -LiteralPath $keystoreHostPath -PathType Leaf)
        $signingVolume = "${signingHostDirectory}:/signing"
        if ($needsKeyGeneration) {
            Write-Host "Creating a reusable local release signing key in '$signingHostDirectory'..."
            Invoke-Podman -Arguments @(
                "run", "--rm",
                "--platform", "linux/amd64",
                "--env-file", $signingEnvironmentPath,
                "--volume", $signingVolume,
                $ImageName,
                "bash", "-ec",
                'keytool -genkeypair -noprompt -storetype PKCS12 -keystore "$OPENTRACK_KEYSTORE_FILE" -storepass "$OPENTRACK_KEYSTORE_PASSWORD" -alias "$OPENTRACK_KEY_ALIAS" -keypass "$OPENTRACK_KEY_PASSWORD" -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=OpenTrack Local Release, OU=Android, O=OpenTrack, L=Local, ST=Local, C=DE"'
            )
            Write-Warning "Back up '$signingHostDirectory'. Losing this key prevents upgrades over APKs signed with it."
        }
        else {
            Write-Host "Using the existing local release signing key in '$signingHostDirectory'."
        }
    }

    $sdkPropertiesPath = Join-Path ([System.IO.Path]::GetTempPath()) "opentrack-local-$PID.properties"
    $temporaryFiles.Add($sdkPropertiesPath)
    [System.IO.File]::WriteAllText($sdkPropertiesPath, "sdk.dir=/opt/android-sdk`n", $utf8WithoutBom)

    $gradleTasks = [System.Collections.Generic.List[string]]::new()
    if (-not $Incremental) {
        $gradleTasks.Add("clean")
    }
    if (-not $SkipTests) {
        $gradleTasks.Add("testDebugUnitTest")
    }
    $gradleTasks.Add("assembleRelease")
    $taskList = $gradleTasks -join " "

    Write-Host "Building and verifying the signed release APK in Podman..."
    $buildCommand = "set -eu; gradle --no-daemon --console=plain --project-cache-dir /gradle-cache/project $taskList; " +
        'test -f /workspace/app/build/outputs/apk/release/app-release.apk; ' +
        'zipalign -c -P 16 4 /workspace/app/build/outputs/apk/release/app-release.apk; ' +
        'apksigner verify --verbose --print-certs /workspace/app/build/outputs/apk/release/app-release.apk'
    Invoke-Podman -Arguments @(
        "run", "--rm",
        "--platform", "linux/amd64",
        "--env-file", $signingEnvironmentPath,
        "--env", "GRADLE_USER_HOME=/gradle-cache/user-home",
        "--volume", "${projectRoot}:/workspace",
        "--volume", "${sdkPropertiesPath}:/workspace/local.properties:ro",
        "--volume", $signingVolume,
        "--volume", "opentrack-gradle-cache:/gradle-cache",
        "--workdir", "/workspace",
        $ImageName,
        "bash", "-ec", $buildCommand
    )

    $metadataPath = Join-Path $projectRoot "app\build\outputs\apk\release\output-metadata.json"
    if (-not (Test-Path -LiteralPath $metadataPath -PathType Leaf)) {
        throw "Gradle completed without release output metadata: $metadataPath"
    }

    $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
    $releaseElement = $metadata.elements | Select-Object -First 1
    if ($null -eq $releaseElement -or [string]::IsNullOrWhiteSpace($releaseElement.outputFile)) {
        throw "Release output metadata does not identify an APK."
    }

    $builtApk = Join-Path (Split-Path $metadataPath -Parent) $releaseElement.outputFile
    if (-not (Test-Path -LiteralPath $builtApk -PathType Leaf)) {
        throw "Signed release APK not found: $builtApk"
    }

    $versionName = if ([string]::IsNullOrWhiteSpace($releaseElement.versionName)) { "release" } else { $releaseElement.versionName }
    $artifactDirectory = Join-Path $projectRoot "artifacts"
    [System.IO.Directory]::CreateDirectory($artifactDirectory) | Out-Null
    $artifactPath = Join-Path $artifactDirectory "OpenTrack-$versionName-release.apk"
    Copy-Item -LiteralPath $builtApk -Destination $artifactPath -Force

    $hash = (Get-FileHash -LiteralPath $artifactPath -Algorithm SHA256).Hash.ToLowerInvariant()
    Write-Host ""
    Write-Host "Release APK: $artifactPath"
    Write-Host "SHA-256:    $hash"
}
finally {
    foreach ($temporaryFile in $temporaryFiles) {
        if (Test-Path -LiteralPath $temporaryFile -PathType Leaf) {
            Remove-Item -LiteralPath $temporaryFile -Force
        }
    }
}
