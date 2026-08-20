package dev.opentrack.app.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.opentrack.app.ui.component.SectionHeader
import dev.opentrack.app.ui.component.SignalTopBar
import dev.opentrack.app.ui.component.TrackerGlyph
import dev.opentrack.app.ui.model.BuilderFieldUi
import dev.opentrack.app.ui.model.BuilderFieldKindUi
import dev.opentrack.app.ui.model.CalendarSpanUi
import dev.opentrack.app.ui.model.CalendarRangeUi
import dev.opentrack.app.ui.model.CalendarWeekStartUi
import dev.opentrack.app.ui.model.BuilderOptionUi
import dev.opentrack.app.ui.model.BuilderPayloadKindUi
import dev.opentrack.app.ui.model.QuickLogModeUi
import dev.opentrack.app.ui.model.TimestampPrecisionUi
import dev.opentrack.app.ui.model.TrackerBuilderAction
import dev.opentrack.app.ui.model.TrackerBuilderUiState
import dev.opentrack.app.ui.model.TrackerGlyphUi
import dev.opentrack.app.ui.model.TrackerKindUi
import dev.opentrack.app.ui.theme.SignalPalette

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TrackerBuilderScreen(
    state: TrackerBuilderUiState,
    onAction: (TrackerBuilderAction) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stepTitles = listOf("Basics", "Data", "Quick log", "Presentation")
    Scaffold(
        modifier = modifier,
        topBar = {
            SignalTopBar(
                title = if (state.editingTrackerId == null) "New tracker" else "Edit tracker",
                onBack = { if (state.step == 0) onClose() else onAction(TrackerBuilderAction.Back) },
                actions = {
                    Text(
                        "${state.step + 1} / ${stepTitles.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                },
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.step > 0) {
                    OutlinedButton(
                        onClick = { onAction(TrackerBuilderAction.Back) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Back") }
                }
                Button(
                    onClick = { onAction(if (state.step == stepTitles.lastIndex) TrackerBuilderAction.Save else TrackerBuilderAction.Next) },
                    enabled = state.canContinue,
                    modifier = Modifier.weight(1f),
                ) { Text(if (state.step == stepTitles.lastIndex) "Save tracker" else "Continue") }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(stepTitles[state.step.coerceIn(stepTitles.indices)], style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    stepDescription(state.step),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            when (state.step) {
                0 -> basicsItems(state, onAction)
                1 -> dataItems(state, onAction)
                2 -> quickLogItems(state, onAction)
                else -> presentationItems(state, onAction)
            }
            state.errorMessage?.let { message ->
                item {
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    state.editingOptionId
        ?.let { editingId -> state.options.firstOrNull { it.id == editingId } }
        ?.let { option ->
            ModalBottomSheet(onDismissRequest = { onAction(TrackerBuilderAction.CloseOptionEditor) }) {
                OptionEditor(
                    title = "Edit option",
                    option = option,
                    onLabelChanged = { onAction(TrackerBuilderAction.OptionLabelChanged(option.id, it)) },
                    onPayloadKindChanged = { onAction(TrackerBuilderAction.OptionPayloadKindChanged(option.id, it)) },
                    onPayloadLabelChanged = { onAction(TrackerBuilderAction.OptionPayloadLabelChanged(option.id, it)) },
                    onPayloadUnitChanged = { onAction(TrackerBuilderAction.OptionPayloadUnitChanged(option.id, it)) },
                    allowPayload = state.kind == TrackerKindUi.CHOICE,
                    onDone = { onAction(TrackerBuilderAction.CloseOptionEditor) },
                )
            }
        }
    state.editingFieldId
        ?.let { editingId -> state.fields.firstOrNull { it.id == editingId } }
        ?.let { field ->
            ModalBottomSheet(onDismissRequest = { onAction(TrackerBuilderAction.CloseFieldEditor) }) {
                FieldEditor(field = field, onAction = onAction)
            }
        }
}

private fun androidx.compose.foundation.lazy.LazyListScope.basicsItems(
    state: TrackerBuilderUiState,
    onAction: (TrackerBuilderAction) -> Unit,
) {
    if (state.editingTrackerId == null && state.templates.isNotEmpty()) {
        item {
            SectionHeader("Start with a template")
            Text(
                "Choose a common setup, then customize any field before saving.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.templates.forEach { template ->
                    Card(
                        modifier = Modifier.width(196.dp).clickable {
                            onAction(TrackerBuilderAction.TemplateSelected(template.id))
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (state.selectedTemplateId == template.id) {
                                template.accent.copy(alpha = 0.14f)
                            } else MaterialTheme.colorScheme.surface
                        ),
                        border = if (state.selectedTemplateId == template.id) {
                            BorderStroke(1.5.dp, template.accent)
                        } else null,
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TrackerGlyph(template.glyph, template.accent, null, Modifier.size(34.dp))
                            Text(
                                template.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                minLines = 2,
                                maxLines = 2,
                            )
                            Text(
                                template.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                minLines = 3,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
    item {
        OutlinedTextField(
            value = state.name,
            onValueChange = { onAction(TrackerBuilderAction.NameChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Tracker name") },
            placeholder = { Text("e.g. Energy") },
            singleLine = true,
        )
    }
    item { SectionHeader("What do you want to record?") }
    if (state.editingTrackerId != null) {
        item {
            Text(
                "Tracker type is fixed after creation so existing entries keep their meaning.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    TrackerKindUi.entries.chunked(2).forEachIndexed { rowIndex, kinds ->
        item(key = "kind-row-$rowIndex") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                kinds.forEach { kind ->
                    KindCard(
                        kind = kind,
                        selected = state.kind == kind,
                        accent = state.accent,
                        enabled = state.editingTrackerId == null,
                        onClick = { onAction(TrackerBuilderAction.KindSelected(kind)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (kinds.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    item {
        SectionHeader("Timestamp")
        Text(
            "Every entry includes a timestamp. Choose how precisely it should appear.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimestampPrecisionUi.entries.forEach { precision ->
                FilterChip(
                    selected = state.precision == precision,
                    onClick = { onAction(TrackerBuilderAction.PrecisionSelected(precision)) },
                    label = { Text(precision.label) },
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.dataItems(
    state: TrackerBuilderUiState,
    onAction: (TrackerBuilderAction) -> Unit,
) {
    when (state.kind) {
        TrackerKindUi.NUMBER -> item { UnitEditor(state, onAction, "Unit", "kg, km, mg…") }
        TrackerKindUi.DURATION -> item { UnitEditor(state, onAction, "Display unit", "minutes") }
        TrackerKindUi.CHOICE, TrackerKindUi.RATING -> {
            item {
                SectionHeader(if (state.kind == TrackerKindUi.RATING) "Scale values" else "Choices")
                Text(
                    if (state.kind == TrackerKindUi.CHOICE) "Each choice can carry one number, duration, integer or short text value." else "Values are ordered from low to high.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            state.options.forEach { option -> item(key = option.id) { BuilderOptionRow(option, onAction) } }
            item {
                OutlinedButton(onClick = { onAction(TrackerBuilderAction.AddOption) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.size(8.dp))
                    Text("Add ${if (state.kind == TrackerKindUi.RATING) "scale value" else "choice"}")
                }
            }
        }
        TrackerKindUi.GROUP -> {
            item {
                SectionHeader("Fields")
                Text(
                    "Combine timestamps, numbers, choices, ratings, counters, durations, and yes/no fields. Every entry can also include a note.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            state.fields.forEach { field -> item(key = field.id) { BuilderFieldRow(field, onAction) } }
            item {
                OutlinedButton(onClick = { onAction(TrackerBuilderAction.AddField) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.size(8.dp))
                    Text("Add field")
                }
            }
        }
        TrackerKindUi.COUNTER -> item {
            var deltaInput by remember(state.editingTrackerId, state.kind) { mutableStateOf(state.counterDelta.toString()) }
            LaunchedEffect(state.counterDelta) {
                if (deltaInput.toIntOrNull() != state.counterDelta) deltaInput = state.counterDelta.toString()
            }
            Text("Default signed change", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "The card + button applies this amount instantly. You can edit a recorded entry later if it needs correction.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            OutlinedTextField(
                value = deltaInput,
                onValueChange = { raw ->
                    deltaInput = raw
                    raw.toIntOrNull()?.let { onAction(TrackerBuilderAction.CounterDeltaChanged(it)) }
                },
                label = { Text("Signed delta") },
                leadingIcon = { Text(if (state.counterDelta >= 0) "+" else "−") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                singleLine = true,
            )
        }
        TrackerKindUi.MOMENT -> {
            item {
                SectionHeader("Calendar content")
                Text(
                    "Choose what each timestamp calendar shows. Counts combine multiple recordings on the same day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                CalendarToggle("Day number", "Show 1, 2, 3… inside each day", state.calendarShowDayNumber) {
                    onAction(TrackerBuilderAction.CalendarShowDayNumberChanged(it))
                }
                CalendarToggle("Amount tracked", "Show how many timestamps were recorded", state.calendarShowCount) {
                    onAction(TrackerBuilderAction.CalendarShowCountChanged(it))
                }
                CalendarToggle("Weekday header", "Label columns with the day of the week", state.calendarShowWeekdayHeader) {
                    onAction(TrackerBuilderAction.CalendarShowWeekdayHeaderChanged(it))
                }
                CalendarToggle("Empty days", "Keep untracked days visible for context", state.calendarShowEmptyDays) {
                    onAction(TrackerBuilderAction.CalendarShowEmptyDaysChanged(it))
                }
            }
            item {
                Text("First day of the week", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CalendarWeekStartUi.entries.forEach { value ->
                        FilterChip(
                            selected = state.calendarWeekStart == value,
                            onClick = { onAction(TrackerBuilderAction.CalendarWeekStartChanged(value)) },
                            label = { Text(value.label) },
                        )
                    }
                }
            }
            item {
                Text("Calendar range", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Use four weeks for a compact month block or add two weeks for month boundaries.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CalendarRangeUi.entries.forEach { value ->
                        FilterChip(
                            selected = state.calendarRange == value,
                            onClick = { onAction(TrackerBuilderAction.CalendarRangeChanged(value)) },
                            label = { Text(value.label) },
                        )
                    }
                }
            }
            item {
                Text("Calendar width", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Two weeks uses 14 columns so wide cards fill the available space.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CalendarSpanUi.entries.forEach { value ->
                        FilterChip(
                            selected = state.calendarSpan == value,
                            onClick = { onAction(TrackerBuilderAction.CalendarSpanChanged(value)) },
                            label = { Text(value.label) },
                        )
                    }
                }
            }
        }
        TrackerKindUi.BOOLEAN -> item { InformationalCard("Simple by design", "Each entry records Yes or No. You can choose a one-tap default next.") }
        null -> item { InformationalCard("Choose a type first", "Go back and select what each entry should record.") }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.quickLogItems(
    state: TrackerBuilderUiState,
    onAction: (TrackerBuilderAction) -> Unit,
) {
    val availableModes = if (state.kind in setOf(
            TrackerKindUi.MOMENT,
            TrackerKindUi.COUNTER,
            TrackerKindUi.GROUP,
        )
    ) listOf(QuickLogModeUi.SMART) else QuickLogModeUi.entries
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            availableModes.forEach { mode ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onAction(TrackerBuilderAction.QuickModeSelected(mode)) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = if (state.quickLogMode == mode) BorderStroke(1.5.dp, state.accent) else null,
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = state.quickLogMode == mode, onClick = { onAction(TrackerBuilderAction.QuickModeSelected(mode)) })
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(mode.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(quickModeDescription(mode, state.kind), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    if (state.quickLogMode == QuickLogModeUi.PRESET && state.kind !in setOf(
            TrackerKindUi.MOMENT,
            TrackerKindUi.COUNTER,
            TrackerKindUi.GROUP,
        )
    ) {
        item {
            OutlinedTextField(
                value = state.quickPreset,
                onValueChange = { onAction(TrackerBuilderAction.QuickPresetChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(oneTapValueLabel(state.kind)) },
                placeholder = { Text(oneTapValuePlaceholder(state.kind, state.unit)) },
                singleLine = true,
            )
        }
    }
    if (state.kind == TrackerKindUi.COUNTER) {
        item {
            InformationalCard(
                "Counter quick action",
                "Tap + to apply ${state.counterDelta.signedLabel()} instantly. Edit the entry from History if it needs correction.",
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.presentationItems(
    state: TrackerBuilderUiState,
    onAction: (TrackerBuilderAction) -> Unit,
) {
    item {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                TrackerGlyph(state.glyph, state.accent, null)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(state.name.ifBlank { "Untitled tracker" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${state.kind?.conceptLabel() ?: "No type"} · ${state.precision.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    item {
        SectionHeader("Color")
        Text(
            "Use this color for calendar intensity, charts, and tracker highlights.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                "Moss" to SignalPalette.Moss,
                "Sky" to SignalPalette.Sky,
                "Coral" to SignalPalette.Coral,
                "Lilac" to SignalPalette.Lilac,
                "Rose" to SignalPalette.Rose,
                "Sun" to SignalPalette.Sun,
            ).forEach { (label, color) ->
                FilterChip(
                    selected = state.accent == color,
                    onClick = { onAction(TrackerBuilderAction.AccentSelected(color)) },
                    label = { Text(label) },
                    leadingIcon = { TrackerGlyph(state.glyph, color, null, Modifier.size(22.dp)) },
                )
            }
        }
    }
    item {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Add to dashboard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Create a useful starter widget with its own + button.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = state.addToDashboard, onCheckedChange = { onAction(TrackerBuilderAction.AddToDashboardChanged(it)) })
        }
    }
    item {
        InformationalCard(
            "Ready to track",
            "You can add more dashboard views, change the quick action, or edit this tracker at any time.",
        )
    }
}

@Composable
private fun CalendarToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun KindCard(
    kind: TrackerKindUi,
    selected: Boolean,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.selectable(
            selected = selected,
            enabled = enabled,
            role = Role.RadioButton,
            onClick = onClick,
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        ),
        border = if (selected) BorderStroke(1.5.dp, accent) else null,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            TrackerGlyph(kind.defaultGlyph(), accent, null, Modifier.size(38.dp))
            Text(
                text = kind.conceptLabel(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = kind.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 3,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun UnitEditor(state: TrackerBuilderUiState, onAction: (TrackerBuilderAction) -> Unit, title: String, hint: String) {
    OutlinedTextField(
        value = state.unit,
        onValueChange = { onAction(TrackerBuilderAction.UnitChanged(it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(title) },
        placeholder = { Text(hint) },
        enabled = state.editingTrackerId == null,
        supportingText = if (state.editingTrackerId != null) {
            { Text("Unit is fixed so earlier entries keep their meaning.") }
        } else null,
        singleLine = true,
    )
}

@Composable
private fun BuilderOptionRow(option: BuilderOptionUi, onAction: (TrackerBuilderAction) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onAction(TrackerBuilderAction.EditOption(option.id)) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(option.label.ifBlank { "Untitled option" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    if (option.payloadKind == BuilderPayloadKindUi.NONE) "No payload" else listOf(option.payloadKind.label, option.payloadLabel, option.payloadUnit).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = { onAction(TrackerBuilderAction.RemoveOption(option.id)) }) { Icon(Icons.Rounded.DeleteOutline, "Remove ${option.label}") }
        }
    }
}

@Composable
private fun BuilderFieldRow(field: BuilderFieldUi, onAction: (TrackerBuilderAction) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onAction(TrackerBuilderAction.EditField(field.id)) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(field.label.ifBlank { "Untitled field" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text("${field.kind.label}${if (field.required) " · Required" else ""}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = { onAction(TrackerBuilderAction.RemoveField(field.id)) }) { Icon(Icons.Rounded.DeleteOutline, "Remove ${field.label}") }
        }
    }
}

@Composable
private fun OptionEditor(
    title: String,
    option: BuilderOptionUi,
    onLabelChanged: (String) -> Unit,
    onPayloadKindChanged: (BuilderPayloadKindUi) -> Unit,
    onPayloadLabelChanged: (String) -> Unit,
    onPayloadUnitChanged: (String) -> Unit,
    allowPayload: Boolean,
    onDone: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        OptionFields(
            option = option,
            onLabelChanged = onLabelChanged,
            onPayloadKindChanged = onPayloadKindChanged,
            onPayloadLabelChanged = onPayloadLabelChanged,
            onPayloadUnitChanged = onPayloadUnitChanged,
            allowPayload = allowPayload,
        )
        Button(onClick = onDone, enabled = option.label.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}

@Composable
private fun OptionFields(
    option: BuilderOptionUi,
    onLabelChanged: (String) -> Unit,
    onPayloadKindChanged: (BuilderPayloadKindUi) -> Unit,
    onPayloadLabelChanged: (String) -> Unit,
    onPayloadUnitChanged: (String) -> Unit,
    allowPayload: Boolean = true,
) {
    OutlinedTextField(
        value = option.label,
        onValueChange = onLabelChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Option name") },
        placeholder = { Text("e.g. Bench press") },
        singleLine = true,
    )
    if (allowPayload) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Optional attached value", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            BuilderPayloadKindUi.entries.forEach { payloadKind ->
                FilterChip(
                    selected = option.payloadKind == payloadKind,
                    onClick = { onPayloadKindChanged(payloadKind) },
                    enabled = !option.payloadKindLocked,
                    label = { Text(payloadKind.label) },
                )
            }
        }
        if (option.payloadKindLocked) {
            Text(
                "Payload type is fixed so earlier entries remain readable.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        }
        if (option.payloadKind != BuilderPayloadKindUi.NONE) {
            OutlinedTextField(
                value = option.payloadLabel,
                onValueChange = onPayloadLabelChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Value label") },
                placeholder = { Text("e.g. Weight or repetitions") },
                singleLine = true,
            )
            if (option.payloadKind in setOf(BuilderPayloadKindUi.NUMBER, BuilderPayloadKindUi.INTEGER, BuilderPayloadKindUi.DURATION)) {
                OutlinedTextField(
                    value = option.payloadUnit,
                    onValueChange = onPayloadUnitChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Unit") },
                    placeholder = { Text(if (option.payloadKind == BuilderPayloadKindUi.DURATION) "minutes" else "kg, reps…") },
                    enabled = !option.payloadKindLocked,
                    singleLine = true,
                )
            }
        }
    }
}

@Composable
private fun FieldEditor(field: BuilderFieldUi, onAction: (TrackerBuilderAction) -> Unit) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Edit group field", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = field.label,
            onValueChange = { onAction(TrackerBuilderAction.FieldLabelChanged(field.id, it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Field label") },
            placeholder = { Text("e.g. Repetitions") },
            singleLine = true,
        )
        Text("Field type", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (field.structureLocked) {
            Text(
                "Field type is fixed so earlier group entries remain readable.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BuilderFieldKindUi.entries.forEach { kind ->
                FilterChip(
                    selected = field.kind == kind,
                    onClick = { onAction(TrackerBuilderAction.FieldKindChanged(field.id, kind)) },
                    enabled = !field.structureLocked,
                    label = { Text(kind.label) },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Required", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (field.requiredLocked) "Requirement is fixed to preserve earlier entries."
                    else "A group cannot be saved without this field.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = field.required,
                onCheckedChange = { onAction(TrackerBuilderAction.FieldRequiredChanged(field.id, it)) },
                enabled = !field.requiredLocked,
            )
        }
        if (field.kind in setOf(BuilderFieldKindUi.NUMBER, BuilderFieldKindUi.COUNTER, BuilderFieldKindUi.DURATION)) {
            OutlinedTextField(
                value = field.unit,
                onValueChange = { onAction(TrackerBuilderAction.FieldUnitChanged(field.id, it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Unit") },
                placeholder = { Text(if (field.kind == BuilderFieldKindUi.DURATION) "minutes" else "kg, reps…") },
                enabled = !field.structureLocked,
                supportingText = if (field.structureLocked) {
                    { Text("Unit is fixed so earlier entries keep their meaning.") }
                } else null,
                singleLine = true,
            )
        }
        if (field.kind == BuilderFieldKindUi.CHOICE || field.kind == BuilderFieldKindUi.RATING) {
            SectionHeader(if (field.kind == BuilderFieldKindUi.CHOICE) "Choices" else "Scale values")
            field.options.forEach { option ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Option", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                            IconButton(onClick = { onAction(TrackerBuilderAction.FieldRemoveOption(field.id, option.id)) }) {
                                Icon(Icons.Rounded.DeleteOutline, "Remove ${option.label}")
                            }
                        }
                        OptionFields(
                            option = option,
                            onLabelChanged = { onAction(TrackerBuilderAction.FieldOptionLabelChanged(field.id, option.id, it)) },
                            onPayloadKindChanged = { onAction(TrackerBuilderAction.FieldOptionPayloadKindChanged(field.id, option.id, it)) },
                            onPayloadLabelChanged = { onAction(TrackerBuilderAction.FieldOptionPayloadLabelChanged(field.id, option.id, it)) },
                            onPayloadUnitChanged = { onAction(TrackerBuilderAction.FieldOptionPayloadUnitChanged(field.id, option.id, it)) },
                            allowPayload = field.kind == BuilderFieldKindUi.CHOICE,
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = { onAction(TrackerBuilderAction.FieldAddOption(field.id)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.size(8.dp))
                Text("Add option")
            }
        }
        Button(
            onClick = { onAction(TrackerBuilderAction.CloseFieldEditor) },
            enabled = field.label.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Done") }
    }
}

@Composable
private fun InformationalCard(title: String, message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun stepDescription(step: Int) = when (step) {
    0 -> "Give it a clear name and choose the shape of each entry."
    1 -> "Define just enough structure to make logging and graphs useful."
    2 -> "Choose what the dashboard + button should do."
    else -> "Review the essentials before you start logging."
}

private fun quickModeDescription(mode: QuickLogModeUi, kind: TrackerKindUi?) = when (mode) {
    QuickLogModeUi.SMART -> if (kind == TrackerKindUi.MOMENT || kind == TrackerKindUi.COUNTER) "Record instantly; open a sheet only when a correction is needed." else "Open the smallest input needed for this tracker."
    QuickLogModeUi.PRESET -> "Record a configured value immediately and offer Undo."
}

private fun oneTapValueLabel(kind: TrackerKindUi?) = when (kind) {
    TrackerKindUi.CHOICE, TrackerKindUi.RATING -> "Option name"
    TrackerKindUi.BOOLEAN -> "Yes or No"
    TrackerKindUi.DURATION -> "Duration"
    else -> "One-tap value"
}

private fun oneTapValuePlaceholder(kind: TrackerKindUi?, unit: String) = when (kind) {
    TrackerKindUi.CHOICE, TrackerKindUi.RATING -> "Exact option name"
    TrackerKindUi.BOOLEAN -> "Yes or No"
    TrackerKindUi.DURATION -> if (unit.isBlank()) "Minutes or h:mm" else "$unit or h:mm"
    else -> "Value"
}

private fun TrackerKindUi.conceptLabel() = when (this) {
    TrackerKindUi.MOMENT -> "Timestamp / Moment"
    TrackerKindUi.NUMBER -> "Value / Number"
    TrackerKindUi.CHOICE -> "Enum / Choice"
    TrackerKindUi.RATING -> "Radio / Rating"
    TrackerKindUi.GROUP -> "Group"
    TrackerKindUi.BOOLEAN -> "Boolean / Yes-No"
    TrackerKindUi.COUNTER -> "Counter"
    TrackerKindUi.DURATION -> "Duration"
}

private fun TrackerKindUi.defaultGlyph() = when (this) {
    TrackerKindUi.MOMENT -> TrackerGlyphUi.PULSE
    TrackerKindUi.NUMBER -> TrackerGlyphUi.SCALE
    TrackerKindUi.CHOICE -> TrackerGlyphUi.FITNESS
    TrackerKindUi.RATING -> TrackerGlyphUi.MOOD
    TrackerKindUi.GROUP -> TrackerGlyphUi.CHECK
    TrackerKindUi.BOOLEAN -> TrackerGlyphUi.CHECK
    TrackerKindUi.COUNTER -> TrackerGlyphUi.COUNTER
    TrackerKindUi.DURATION -> TrackerGlyphUi.TIMER
}

private fun Int.signedLabel() = if (this >= 0) "+$this" else toString()
