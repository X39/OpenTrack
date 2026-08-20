package dev.opentrack.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import dev.opentrack.app.ui.component.TrackerGlyph
import dev.opentrack.app.ui.model.BuilderFieldKindUi
import dev.opentrack.app.ui.model.BuilderPayloadKindUi
import dev.opentrack.app.ui.model.QuickLogAction
import dev.opentrack.app.ui.model.QuickLogFieldUi
import dev.opentrack.app.ui.model.QuickLogOptionUi
import dev.opentrack.app.ui.model.QuickLogUiState
import dev.opentrack.app.ui.model.TrackerKindUi

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun QuickLogSheet(
    state: QuickLogUiState,
    onAction: (QuickLogAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = { onAction(QuickLogAction.Dismiss) },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrackerGlyph(state.glyph, state.accent, null)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(state.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        quickLogPrompt(state.kind),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TimestampRow(state.timestampLabel, { onAction(QuickLogAction.EditTimestamp) })
            when (state.kind) {
                TrackerKindUi.MOMENT -> MomentBody(state.accent)
                TrackerKindUi.NUMBER -> ValueBody(state, onAction)
                TrackerKindUi.DURATION -> ValueBody(state, onAction, keyboardType = KeyboardType.Text)
                TrackerKindUi.CHOICE -> {
                    OptionSelector(state.options, state.accent) { onAction(QuickLogAction.OptionSelected(it)) }
                    val selected = state.options.firstOrNull { it.selected }
                    if (selected != null && selected.payloadKind != BuilderPayloadKindUi.NONE) {
                        ValueBody(
                            state = state,
                            onAction = onAction,
                            label = selected.supporting ?: selected.payloadKind.inputLabel(),
                            unit = selected.payloadUnit,
                            keyboardType = selected.payloadKind.keyboardType(),
                        )
                    }
                }
                TrackerKindUi.RATING, TrackerKindUi.BOOLEAN -> {
                    OptionSelector(state.options, state.accent) { onAction(QuickLogAction.OptionSelected(it)) }
                }
                TrackerKindUi.COUNTER -> CounterBody(state, onAction)
                TrackerKindUi.GROUP -> GroupBody(state, onAction)
            }
            OutlinedTextField(
                value = state.note,
                onValueChange = { onAction(QuickLogAction.NoteChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note (optional)") },
                placeholder = { Text("Add context you may want later") },
                minLines = 2,
                maxLines = 4,
            )
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (state.editingEntryId != null) {
                TextButton(
                    onClick = { onAction(QuickLogAction.Delete) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Delete entry", color = MaterialTheme.colorScheme.error)
                }
            }
            Button(
                onClick = { onAction(QuickLogAction.Save) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canSave && !state.saving,
            ) { Text(if (state.saving) "Saving…" else saveLabel(state.kind, state.counterDelta)) }
        }
    }
}

@Composable
private fun TimestampRow(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Schedule, null, tint = MaterialTheme.colorScheme.primary)
            Text(label, Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Icon(Icons.Rounded.Edit, "Change timestamp", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MomentBody(accent: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.12f))) {
        Text(
            "That’s all. Saving records this moment at the time shown above.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ValueBody(
    state: QuickLogUiState,
    onAction: (QuickLogAction) -> Unit,
    label: String = if (state.kind == TrackerKindUi.DURATION) "Duration" else "Value",
    unit: String? = state.unit,
    keyboardType: KeyboardType = KeyboardType.Decimal,
) {
    OutlinedTextField(
        value = state.value,
        onValueChange = { onAction(QuickLogAction.ValueChanged(it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        suffix = unit?.let { suffix -> ({ Text(suffix) }) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineMedium,
    )
}

@Composable
private fun OptionSelector(options: List<QuickLogOptionUi>, accent: Color, onSelected: (String) -> Unit) {
    if (options.size <= 5) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option.selected,
                    onClick = { onSelected(option.id) },
                    label = { Text(option.label) },
                )
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                Surface(
                    modifier = Modifier.fillMaxWidth().selectable(
                        selected = option.selected,
                        role = Role.RadioButton,
                        onClick = { onSelected(option.id) },
                    ),
                    shape = MaterialTheme.shapes.medium,
                    color = if (option.selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = if (option.selected) androidx.compose.foundation.BorderStroke(1.dp, accent) else null,
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
                        Text(option.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        if (option.supporting != null) Text(option.supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun CounterBody(state: QuickLogUiState, onAction: (QuickLogAction) -> Unit) {
    var deltaInput by remember(state.trackerId, state.editingEntryId) { mutableStateOf(state.counterDelta.toString()) }
    LaunchedEffect(state.counterDelta) {
        if (deltaInput.toIntOrNull() != state.counterDelta) deltaInput = state.counterDelta.toString()
    }
    Card(colors = CardDefaults.cardColors(containerColor = state.accent.copy(alpha = 0.12f))) {
        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Signed change", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(state.counterDelta.signedLabel(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onAction(QuickLogAction.CounterDecrement) }) {
                    Icon(Icons.Rounded.Remove, "Subtract from counter")
                }
                Surface(shape = CircleShape, color = state.accent) {
                    IconButton(onClick = { onAction(QuickLogAction.CounterIncrement) }) {
                        Icon(Icons.Rounded.Add, "Add to counter", tint = Color.White)
                    }
                }
            }
            OutlinedTextField(
                value = deltaInput,
                onValueChange = { raw ->
                    deltaInput = raw
                    raw.toIntOrNull()?.let { onAction(QuickLogAction.CounterDeltaChanged(it)) }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Manual signed change") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                singleLine = true,
            )
            OutlinedButton(onClick = { onAction(QuickLogAction.CounterCorrection) }) {
                Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Reverse sign")
            }
        }
    }
}

@Composable
private fun GroupBody(state: QuickLogUiState, onAction: (QuickLogAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        state.fields.forEach { field -> GroupField(field, state.accent, onAction) }
    }
}

@Composable
private fun GroupField(field: QuickLogFieldUi, accent: Color, onAction: (QuickLogAction) -> Unit) {
    when (field.kind) {
        BuilderFieldKindUi.CHOICE, BuilderFieldKindUi.RATING, BuilderFieldKindUi.BOOLEAN -> {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(field.label + if (field.required) " *" else "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OptionSelector(field.options, accent) { onAction(QuickLogAction.FieldOptionSelected(field.id, it)) }
                field.options.firstOrNull {
                    it.selected && it.payloadKind != BuilderPayloadKindUi.NONE
                }?.let { selected ->
                    OutlinedTextField(
                        value = field.value,
                        onValueChange = { onAction(QuickLogAction.FieldChanged(field.id, it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(selected.supporting ?: selected.payloadKind.inputLabel()) },
                        suffix = selected.payloadUnit?.let { suffix -> ({ Text(suffix) }) },
                        isError = field.error != null,
                        keyboardOptions = KeyboardOptions(keyboardType = selected.payloadKind.keyboardType()),
                        singleLine = true,
                    )
                }
                field.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        BuilderFieldKindUi.MOMENT -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(field.label + if (field.required) " *" else "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onAction(QuickLogAction.EditFieldTimestamp(field.id)) },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Schedule, null)
                        Text(field.value.ifBlank { field.placeholder.ifBlank { "Choose date or time" } }, Modifier.weight(1f).padding(horizontal = 10.dp))
                        Icon(Icons.Rounded.Edit, "Edit ${field.label}")
                    }
                }
                field.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        else -> OutlinedTextField(
            value = field.value,
            onValueChange = { onAction(QuickLogAction.FieldChanged(field.id, it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(field.label + if (field.required) " *" else "") },
            placeholder = { if (field.placeholder.isNotBlank()) Text(field.placeholder) },
            suffix = field.suffix?.let { suffix -> ({ Text(suffix) }) },
            isError = field.error != null,
            supportingText = field.error?.let { error -> ({ Text(error) }) },
            keyboardOptions = KeyboardOptions(
                keyboardType = when (field.kind) {
                    BuilderFieldKindUi.NUMBER -> KeyboardType.Decimal
                    else -> KeyboardType.Text
                },
            ),
            singleLine = true,
        )
    }
}

private fun quickLogPrompt(kind: TrackerKindUi) = when (kind) {
    TrackerKindUi.MOMENT -> "Record that it happened"
    TrackerKindUi.NUMBER -> "Enter a value"
    TrackerKindUi.CHOICE -> "Choose an event"
    TrackerKindUi.RATING -> "Choose a rating"
    TrackerKindUi.GROUP -> "Complete this record"
    TrackerKindUi.BOOLEAN -> "Choose yes or no"
    TrackerKindUi.COUNTER -> "Adjust the counter"
    TrackerKindUi.DURATION -> "Enter a duration"
}

private fun saveLabel(kind: TrackerKindUi, delta: Int) = when (kind) {
    TrackerKindUi.MOMENT -> "Record now"
    TrackerKindUi.COUNTER -> "Apply ${delta.signedLabel()}"
    else -> "Save entry"
}

private fun Int.signedLabel() = if (this >= 0) "+$this" else toString()

private fun BuilderPayloadKindUi.inputLabel() = when (this) {
    BuilderPayloadKindUi.NONE -> "Attached value"
    BuilderPayloadKindUi.NUMBER -> "Number"
    BuilderPayloadKindUi.INTEGER -> "Whole number"
    BuilderPayloadKindUi.DURATION -> "Duration"
    BuilderPayloadKindUi.TEXT -> "Text"
}

private fun BuilderPayloadKindUi.keyboardType() = when (this) {
    BuilderPayloadKindUi.NUMBER -> KeyboardType.Decimal
    BuilderPayloadKindUi.DURATION -> KeyboardType.Text
    BuilderPayloadKindUi.INTEGER -> KeyboardType.Number
    BuilderPayloadKindUi.NONE, BuilderPayloadKindUi.TEXT -> KeyboardType.Text
}
