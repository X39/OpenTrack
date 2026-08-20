package dev.opentrack.app.presentation

import dev.opentrack.app.domain.model.TrackerDefinition
import dev.opentrack.app.domain.template.StarterTemplates
import dev.opentrack.app.ui.model.BuilderTemplateUi
import dev.opentrack.app.ui.model.OnboardingTemplateUi
import dev.opentrack.app.ui.model.TrackerGlyphUi
import dev.opentrack.app.ui.theme.SignalPalette
import java.time.Clock

private data class TemplateAppearance(
    val glyph: TrackerGlyphUi,
    val colorArgb: Long,
)

private fun appearance(key: String): TemplateAppearance = when (key) {
    StarterTemplates.MOMENT -> TemplateAppearance(TrackerGlyphUi.PULSE, SignalPalette.Lilac.value.toLong())
    StarterTemplates.WEIGHT -> TemplateAppearance(TrackerGlyphUi.SCALE, SignalPalette.Moss.value.toLong())
    StarterTemplates.ENERGY -> TemplateAppearance(TrackerGlyphUi.MOOD, SignalPalette.Sun.value.toLong())
    StarterTemplates.WORKOUT_SET -> TemplateAppearance(TrackerGlyphUi.FITNESS, SignalPalette.Coral.value.toLong())
    StarterTemplates.WATER -> TemplateAppearance(TrackerGlyphUi.WATER, SignalPalette.Sky.value.toLong())
    StarterTemplates.SLEEP -> TemplateAppearance(TrackerGlyphUi.SLEEP, SignalPalette.Lilac.value.toLong())
    StarterTemplates.DAILY_CHECK_IN -> TemplateAppearance(TrackerGlyphUi.CHECK, SignalPalette.Rose.value.toLong())
    else -> TemplateAppearance(TrackerGlyphUi.PULSE, SignalPalette.Moss.value.toLong())
}

internal fun starterTemplateUi() = StarterTemplates.available.map { descriptor ->
    val appearance = appearance(descriptor.key)
    OnboardingTemplateUi(
        id = descriptor.key,
        title = descriptor.title,
        description = descriptor.description,
        glyph = appearance.glyph,
        accent = androidx.compose.ui.graphics.Color(appearance.colorArgb.toULong()),
        selected = descriptor.key in setOf(StarterTemplates.WATER, StarterTemplates.WEIGHT),
    )
}

internal fun builderTemplateUi() = StarterTemplates.available.map { descriptor ->
    val appearance = appearance(descriptor.key)
    BuilderTemplateUi(
        id = descriptor.key,
        title = descriptor.title,
        description = descriptor.description,
        glyph = appearance.glyph,
        accent = androidx.compose.ui.graphics.Color(appearance.colorArgb.toULong()),
    )
}

internal fun starterTemplateDefinition(
    key: String,
    metric: Boolean,
    clock: Clock = Clock.systemUTC(),
): TrackerDefinition {
    val appearance = appearance(key)
    val unit = when (key) {
        StarterTemplates.WEIGHT, StarterTemplates.WORKOUT_SET -> if (metric) "kg" else "lb"
        else -> null
    }
    return StarterTemplates.instantiate(key, unit = unit, clock = clock).copy(
        iconKey = appearance.glyph.name,
        colorArgb = appearance.colorArgb,
    )
}
