package de.beispiel.meintraining.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Sämtliche Maße an einer Stelle – im UI-Code stehen keine Magic Numbers. */
object Dimens {

    // Screen
    val ScreenPaddingHorizontal = 16.dp
    val ListBottomPadding = 24.dp

    // Kopfzeile
    val HeaderHeight = 56.dp
    val MenuButtonSize = 44.dp
    val MenuIconSize = 22.dp

    // Tag-Auswahl
    val TabHeight = 36.dp
    val TabSpacing = 8.dp

    // Übungskarte
    val CardHeight = 56.dp
    val CardSpacing = 8.dp
    val CardElevationResting = 0.dp
    val CardElevationDragged = 8.dp

    /** Linker Innenabstand der Karte; im Auswahlmodus steht dort stattdessen der Haken. */
    val CardPaddingStart = 12.dp
    val SelectionMarkWidth = 32.dp
    val SelectionMarkSize = 18.dp
    val SelectionBorderWidth = 1.dp

    // Superset: grauer Kasten um mehrere Karten
    val SupersetInset = 6.dp
    val SupersetInnerSpacing = 6.dp
    val CornerSuperset = RoundedCornerShape(14.dp)

    // Tracking
    val ChartHeight = 260.dp
    val LegendLineWidth = 28.dp
    val LegendLineHeight = 12.dp
    val PickerMaxHeight = 320.dp

    // Chips – feste Breiten, damit Spaltenkopf und Karte exakt übereinander liegen
    val ChipHeight = 30.dp
    val ChipWeightWidth = 66.dp
    val ChipSetsWidth = 74.dp
    val ChipSpacing = 8.dp
    val ChipPaddingHorizontal = 6.dp

    // Pfeil-Button
    val ArrowIconSize = 22.dp
    val TouchTargetSize = 48.dp

    // Buttons unter der Liste
    val AddButtonHeight = 48.dp
    val AddButtonBorderWidth = 1.dp
    val AddButtonWidth = 56.dp

    // Statistiken
    val WeekdayChartHeight = 132.dp
    val WeekdayBarMaxHeight = 84.dp
    val StatsBarMinHeight = 4.dp
    val StatsBarHeight = 10.dp
    val StatsLabelWidth = 64.dp
    val StatsValueWidth = 64.dp

    // Deload
    val WeekDotHeight = 32.dp
    val BulletSize = 6.dp
    val BadgeBorderWidth = 1.dp

    // Abstände zwischen den Blöcken
    val SectionSpacingSmall = 8.dp
    val SectionSpacingMedium = 12.dp
    val SectionSpacingLarge = 16.dp

    // Bearbeiten-Sheet
    val SheetPadding = 20.dp
    val SheetFieldSpacing = 12.dp

    /** Höhe eines OutlinedTextField ohne Hilfetext – daran richtet sich der „+“-Knopf aus. */
    val SheetFieldHeight = 56.dp

    // Ecken
    val CornerCard = RoundedCornerShape(12.dp)
    val CornerChip = RoundedCornerShape(8.dp)
    val CornerTab = RoundedCornerShape(8.dp)
    val CornerMenuButton = RoundedCornerShape(10.dp)
    val CornerAddButton = RoundedCornerShape(12.dp)
}
