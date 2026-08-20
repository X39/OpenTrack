package dev.opentrack.app.domain.model

class DomainValidationException(message: String) : IllegalArgumentException(message)

object DomainValidator {
    fun validate(definition: TrackerDefinition) {
        check(definition.id.isNotBlank(), "Tracker id is required")
        check(definition.name.isNotBlank(), "Tracker name is required")
        checkUnique(definition.fields.map { it.id }, "field ids")

        val activeFields = definition.fields.filter { it.archivedAt == null }
        when (definition.kind) {
            TrackerKind.TIMESTAMP -> check(activeFields.isEmpty(), "Timestamp trackers cannot have fields")
            TrackerKind.GROUP -> check(activeFields.isNotEmpty(), "Group trackers need at least one field")
            else -> {
                check(activeFields.size == 1, "${definition.kind} trackers need exactly one active field")
                check(activeFields.single().kind.name == definition.kind.name, "Field kind must match tracker kind")
                check(activeFields.single().required, "A standalone tracker field must be required")
            }
        }

        val labels = activeFields.map { it.label.trim().lowercase() }
        check(activeFields.all { it.label.isNotBlank() }, "Field labels are required")
        checkUnique(labels, "active field labels")
        definition.fields.forEach(::validateField)

        checkUnique(definition.presets.map { it.id }, "preset ids")
        val fieldIds = definition.fields.mapTo(hashSetOf()) { it.id }
        definition.presets.forEach { preset ->
            check(preset.id.isNotBlank(), "Preset id is required")
            check(preset.label.isNotBlank(), "Preset labels are required")
            check(preset.values.keys.all(fieldIds::contains), "Preset references a field from another tracker")
            check(preset.timestampModes.keys.all(fieldIds::contains), "Preset timestamp mode references an unknown field")
            preset.values.forEach { (fieldId, value) ->
                validateValue(definition.fields.first { it.id == fieldId }, value)
            }
        }
        definition.quickAdd.defaultPresetId?.let { defaultId ->
            check(definition.presets.any { it.id == defaultId }, "Default preset does not belong to the tracker")
        }
        if (definition.quickAdd.mode == QuickAddMode.DEFAULT_PRESET) {
            check(definition.quickAdd.defaultPresetId != null, "Default-preset mode needs a preset")
        }
    }

    fun validateEntry(
        definition: TrackerDefinition,
        entry: TrackerEntry,
        requireCurrentFields: Boolean = true,
    ) {
        check(entry.trackerId == definition.id, "Entry belongs to a different tracker")
        val fields = definition.fields.associateBy { it.id }
        check(entry.values.keys.all(fields::containsKey), "Entry contains an unknown field")

        if (definition.kind == TrackerKind.TIMESTAMP) {
            check(entry.values.isEmpty(), "Timestamp entries cannot contain field values")
        } else if (requireCurrentFields) {
            fields.values.filter { it.archivedAt == null && it.required }.forEach { field ->
                check(entry.values.containsKey(field.id), "${field.label} is required")
            }
        }
        entry.values.forEach { (fieldId, value) -> validateValue(fields.getValue(fieldId), value) }
    }

    fun validate(snapshot: BackupSnapshot) {
        checkUnique(snapshot.trackers.map { it.id }, "tracker ids")
        checkUnique(snapshot.entries.map { it.id }, "entry ids")
        checkUnique(snapshot.dashboards.map { it.id }, "dashboard ids")
        checkUnique(snapshot.trackers.flatMap { it.fields }.map { it.id }, "field ids")
        checkUnique(snapshot.trackers.flatMap { it.fields }.flatMap { it.options }.map { it.id }, "option ids")
        checkUnique(snapshot.trackers.flatMap { it.presets }.map { it.id }, "preset ids")
        val definitions = snapshot.trackers.associateBy { it.id }
        check(snapshot.entries.all { it.id.isNotBlank() }, "Entry ids are required")
        check(snapshot.dashboards.all { it.id.isNotBlank() }, "Dashboard ids are required")
        snapshot.trackers.forEach(::validate)
        snapshot.entries.forEach { entry ->
            val definition = definitions[entry.trackerId]
                ?: throw DomainValidationException("Entry ${entry.id} references an unknown tracker")
            // A backup may legitimately contain entries recorded before a field became required.
            // Stored values still have to match their field schemas, but old rows are not rewritten.
            validateEntry(definition, entry, requireCurrentFields = false)
        }
        validateDashboards(snapshot.dashboards, definitions)
    }

    private fun validateDashboards(
        dashboards: List<Dashboard>,
        definitions: Map<String, TrackerDefinition>,
    ) {
        val widgetIds = mutableSetOf<String>()
        val seriesIds = mutableSetOf<String>()
        dashboards.forEach { dashboard ->
            check(dashboard.name.isNotBlank(), "Dashboard name is required")
            dashboard.widgets.forEach { widget ->
                check(widget.id.isNotBlank(), "Widget id is required")
                check(widgetIds.add(widget.id), "Duplicate widget id")
                check(widget.series.isNotEmpty(), "Dashboard widgets need at least one series")
                check(widget.series.size == 1, "Dashboard widgets support one series; use another widget for another graph")
                widget.series.forEach { series ->
                    check(series.id.isNotBlank(), "Dashboard series id is required")
                    check(seriesIds.add(series.id), "Duplicate dashboard series id")
                    val tracker = definitions[series.trackerId]
                        ?: throw DomainValidationException("Dashboard references an unknown tracker")
                    series.fieldId?.let { fieldId ->
                        check(tracker.fields.any { it.id == fieldId }, "Dashboard references an unknown field")
                    }
                    val field = series.fieldId?.let { fieldId -> tracker.fields.first { it.id == fieldId } }
                        ?: tracker.fields.firstOrNull { it.archivedAt == null }
                    series.optionId?.let { optionId ->
                        check(field?.options?.any { it.id == optionId } == true, "Dashboard references an unknown option")
                    }
                    series.presetId?.let { presetId ->
                        check(tracker.presets.any { it.id == presetId }, "Dashboard references an unknown preset")
                    }
                }
            }
        }
    }

    private fun validateField(field: TrackerField) {
        check(field.id.isNotBlank(), "Field id is required")
        check(field.decimalPlaces in 0..12, "Decimal places must be between 0 and 12")
        if (field.kind == FieldKind.TIMESTAMP) {
            check(field.timestampPrecision != null, "Timestamp fields need a precision")
        }
        if (field.kind == FieldKind.ENUM || field.kind == FieldKind.RADIO) {
            val active = field.options.filter { it.archivedAt == null }
            if (field.archivedAt == null) {
                check(active.size >= if (field.kind == FieldKind.RADIO) 2 else 1, "Choice field has too few options")
            }
            checkUnique(active.map { it.label.trim().lowercase() }, "active option labels")
            checkUnique(field.options.map { it.id }, "option ids")
            check(field.options.all { it.id.isNotBlank() }, "Option ids are required")
            active.forEach { option ->
                check(option.label.isNotBlank(), "Option labels are required")
                if (field.kind == FieldKind.RADIO) {
                    check(option.radioScore?.isFinite() == true, "Radio options need a finite score")
                    check(option.payloadKind == EnumPayloadKind.NONE, "Radio options cannot carry payloads")
                }
            }
        } else {
            check(field.options.isEmpty(), "Only enum and radio fields can have options")
        }
    }

    private fun validateValue(field: TrackerField, value: FieldValue) {
        when (field.kind) {
            FieldKind.VALUE -> check(value is FieldValue.Decimal && value.value.isFinite(), "${field.label} needs a finite number")
            FieldKind.COUNTER -> check(value is FieldValue.Integer, "${field.label} needs an integer delta")
            FieldKind.DURATION -> check(value is FieldValue.DurationValue && !value.value.isNegative, "${field.label} needs a nonnegative duration")
            FieldKind.BOOLEAN -> check(value is FieldValue.BooleanValue, "${field.label} needs true or false")
            FieldKind.TIMESTAMP -> check(value is FieldValue.Timestamp, "${field.label} needs a timestamp")
            FieldKind.RADIO -> {
                val choice = value as? FieldValue.Choice
                    ?: throw DomainValidationException("${field.label} needs a choice")
                check(choice.payload == null, "Radio values cannot carry payloads")
                check(field.options.any { it.id == choice.optionId }, "Unknown radio option")
            }
            FieldKind.ENUM -> {
                val choice = value as? FieldValue.Choice
                    ?: throw DomainValidationException("${field.label} needs a choice")
                val option = field.options.firstOrNull { it.id == choice.optionId }
                    ?: throw DomainValidationException("Unknown enum option")
                validatePayload(option.payloadKind, choice.payload)
            }
        }
    }

    private fun validatePayload(kind: EnumPayloadKind, payload: FieldValue?) {
        when (kind) {
            EnumPayloadKind.NONE -> check(payload == null, "This option does not accept a payload")
            EnumPayloadKind.DECIMAL -> check(payload is FieldValue.Decimal && payload.value.isFinite(), "A finite decimal payload is required")
            EnumPayloadKind.INTEGER -> check(payload is FieldValue.Integer, "An integer payload is required")
            EnumPayloadKind.DURATION -> check(payload is FieldValue.DurationValue && !payload.value.isNegative, "A nonnegative duration payload is required")
            EnumPayloadKind.TEXT -> check(payload is FieldValue.Text && payload.value.isNotBlank(), "A text payload is required")
        }
    }

    private fun check(condition: Boolean, message: String) {
        if (!condition) throw DomainValidationException(message)
    }

    private fun <T> checkUnique(values: List<T>, label: String) {
        check(values.size == values.toSet().size, "Duplicate $label")
    }
}
