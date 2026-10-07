package de.beispiel.meintraining.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import de.beispiel.meintraining.R
import de.beispiel.meintraining.data.model.CardioValue
import de.beispiel.meintraining.data.model.IntensityUnit
import de.beispiel.meintraining.ui.CardioForm
import de.beispiel.meintraining.ui.components.SegmentToggle
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.TextSecondary
import de.beispiel.meintraining.util.cardioStepSuggestions
import de.beispiel.meintraining.util.formatCardioStepInput
import de.beispiel.meintraining.util.parseCardioStep

/**
 * Die Felder einer Cardio-Übung im Bearbeiten-Sheet – an der Stelle von Gewicht, Sätzen,
 * Wiederholungen und Progressionsschritt.
 *
 * Oben die Zielwerte, darunter der Pfeil: welcher Wert, um wie viel, in welche Richtung.
 * [lastEntry] ist die Zeile mit der letzten eingetragenen Einheit; sie steht unter der Dauer,
 * wo bei Kraftübungen der Gewichtsverlauf unter dem Gewicht steht.
 */
@Composable
internal fun CardioSheetFields(
    cardio: CardioForm,
    onChange: (CardioForm) -> Unit,
    lastEntry: @Composable () -> Unit = {}
) {
    // Text statt Ziffernblock: „7:30“ braucht den Doppelpunkt, und den hat der Ziffernblock der
    // meisten Tastaturen nicht.
    SheetTextField(
        value = cardio.duration,
        onValueChange = { onChange(cardio.copy(duration = it)) },
        label = stringResource(R.string.field_cardio_duration),
        keyboardType = KeyboardType.Text,
        isError = !cardio.isDurationValid,
        supportingText = stringResource(
            if (cardio.isDurationValid) R.string.hint_cardio_duration else R.string.error_cardio_duration
        )
    )
    lastEntry()

    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SheetFieldSpacing)) {
        SheetTextField(
            value = cardio.distance,
            onValueChange = { onChange(cardio.copy(distance = it)) },
            label = stringResource(R.string.field_cardio_distance),
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f)
        )
        SheetTextField(
            value = cardio.incline,
            onValueChange = { onChange(cardio.copy(incline = it)) },
            label = stringResource(R.string.field_cardio_incline),
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f)
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.SheetFieldSpacing),
        verticalAlignment = Alignment.Top
    ) {
        SheetTextField(
            value = cardio.intensity,
            onValueChange = { onChange(cardio.copy(intensity = it)) },
            label = stringResource(
                when (cardio.intensityUnit) {
                    IntensityUnit.KMH -> R.string.field_cardio_speed
                    IntensityUnit.LEVEL -> R.string.field_cardio_level
                }
            ),
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f)
        )
        // Auf der Höhe des Felds mittig, wie das „+“ neben dem Namen.
        Box(modifier = Modifier.height(Dimens.SheetFieldHeight), contentAlignment = Alignment.Center) {
            SegmentToggle(
                labels = listOf(
                    stringResource(R.string.cardio_unit_kmh),
                    stringResource(R.string.cardio_unit_level)
                ),
                selectedIndex = cardio.intensityUnit.ordinal,
                onSelect = { onChange(cardio.withIntensityUnit(IntensityUnit.entries[it])) },
                segmentWidth = Dimens.IntensityToggleWidth
            )
        }
    }

    Text(
        text = stringResource(R.string.hint_cardio_shared),
        style = AppTextStyles.ColumnLabel,
        color = TextSecondary,
        modifier = Modifier.padding(horizontal = Dimens.SectionSpacingLarge)
    )

    ArrowValueChoice(cardio = cardio, onChange = onChange)

    val arrowValue = cardio.arrowValue ?: return
    ArrowStep(cardio = cardio, value = arrowValue, onChange = onChange)
}

/**
 * „Der Pfeil in der Liste verschiebt“ und darunter die Werte zur Wahl, der gewählte blau. Ohne
 * Wahl – „Keinen“ – gibt es in der Liste keinen Pfeil.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArrowValueChoice(cardio: CardioForm, onChange: (CardioForm) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingSmall)) {
        Text(
            text = stringResource(R.string.cardio_arrow_label),
            style = AppTextStyles.ColumnLabel,
            color = TextSecondary
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingSmall),
            verticalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingSmall)
        ) {
            val choices = listOf(null) + CardioValue.entries
            choices.forEach { value ->
                SelectChip(
                    label = stringResource(arrowValueLabel(value, cardio.intensityUnit)),
                    isActive = value == cardio.arrowValue,
                    onClick = { onChange(cardio.withArrowValue(value)) }
                )
            }
        }
    }
}

/**
 * Der Schritt des Pfeils mit Schnellauswahl passend zum Wert und daneben die Richtung – derselbe
 * Aufbau wie beim Progressionsschritt einer Kraftübung.
 */
@Composable
private fun ArrowStep(cardio: CardioForm, value: CardioValue, onChange: (CardioForm) -> Unit) {
    val unit = stringResource(stepUnitLabel(value, cardio.intensityUnit))
    val valueName = stringResource(valueNameWithArticle(value, cardio.intensityUnit))
    SheetTextField(
        value = cardio.arrowStep,
        onValueChange = { onChange(cardio.copy(arrowStep = it)) },
        label = stringResource(R.string.field_cardio_step, unit),
        // Ein Schritt in der Dauer darf „0:30“ sein, braucht also wie die Dauer den Doppelpunkt.
        keyboardType = if (value == CardioValue.DURATION) KeyboardType.Text else KeyboardType.Decimal,
        imeAction = ImeAction.Done,
        supportingText = stringResource(
            if (cardio.arrowDown) R.string.hint_cardio_step_down else R.string.hint_cardio_step,
            valueName
        )
    )
    Text(
        text = stringResource(R.string.quick_select_label),
        style = AppTextStyles.ColumnLabel,
        color = TextSecondary
    )
    val activeStep = remember(cardio.arrowStep, value, cardio.intensityUnit) {
        parseCardioStep(cardio.arrowStep, value, cardio.intensityUnit)
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepChips(
            suggestions = cardioStepSuggestions(value, cardio.intensityUnit),
            activeStep = activeStep,
            labelOf = { formatCardioStepInput(it, value) },
            onSelect = { onChange(cardio.copy(arrowStep = it)) },
            modifier = Modifier.weight(1f)
        )
        ProgressionDirectionToggle(
            down = cardio.arrowDown,
            contentDescription = stringResource(R.string.cd_cardio_arrow_down),
            onClick = { onChange(cardio.copy(arrowDown = !cardio.arrowDown)) }
        )
    }
}

/** Beschriftung eines Werts in der Auswahl des Pfeils; `null` ist „Keinen“. */
private fun arrowValueLabel(value: CardioValue?, unit: IntensityUnit): Int = when (value) {
    null -> R.string.cardio_arrow_none
    CardioValue.DURATION -> R.string.cardio_value_duration
    CardioValue.DISTANCE -> R.string.cardio_value_distance
    CardioValue.INTENSITY -> when (unit) {
        IntensityUnit.KMH -> R.string.cardio_value_speed
        IntensityUnit.LEVEL -> R.string.cardio_value_level
    }
    CardioValue.INCLINE -> R.string.cardio_value_incline
}

/** „Die Dauer“, „Das Tempo“ … – für den Hinweis unter dem Schritt. */
private fun valueNameWithArticle(value: CardioValue, unit: IntensityUnit): Int = when (value) {
    CardioValue.DURATION -> R.string.cardio_value_duration_the
    CardioValue.DISTANCE -> R.string.cardio_value_distance_the
    CardioValue.INTENSITY -> when (unit) {
        IntensityUnit.KMH -> R.string.cardio_value_speed_the
        IntensityUnit.LEVEL -> R.string.cardio_value_level_the
    }
    CardioValue.INCLINE -> R.string.cardio_value_incline_the
}

/** Die Einheit in der Beschriftung des Schritts: „Schritt (min)“, „Schritt (km/h)“ … */
private fun stepUnitLabel(value: CardioValue, unit: IntensityUnit): Int = when (value) {
    CardioValue.DURATION -> R.string.cardio_unit_minutes
    CardioValue.DISTANCE -> R.string.cardio_unit_km
    CardioValue.INTENSITY -> when (unit) {
        IntensityUnit.KMH -> R.string.cardio_unit_kmh
        IntensityUnit.LEVEL -> R.string.cardio_step_unit_levels
    }
    CardioValue.INCLINE -> R.string.cardio_unit_percent
}
