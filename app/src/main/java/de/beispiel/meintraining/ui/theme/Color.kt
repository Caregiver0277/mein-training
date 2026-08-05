package de.beispiel.meintraining.ui.theme

import androidx.compose.ui.graphics.Color

/** Feste Farbpalette des dunklen Themes – es gibt bewusst keinen Light Mode. */
val ScreenBackground = Color(0xFF10141A)
val CardBackground = Color(0xFF1C222B)
val CardDraggedBackground = Color(0xFF2A313C)
val ChipBackground = Color(0xFF2B323C)

val TabActiveSurface = Color(0xFFC9CDD2)
val TabActiveText = Color(0xFF1A1E24)
val TabInactiveSurface = Color(0xFF262C35)
val TabInactiveText = Color(0xFFD4D8DE)

val MenuButtonSurface = Color(0xFF3A4049)
val MenuButtonIcon = Color(0xFFE6E9ED)

val TextPrimary = Color(0xFFF2F4F7)
val TextSecondary = Color(0xFF8A9099)
val TextDisabled = Color(0xFF4E555F)

val AccentBlue = Color(0xFF2F80ED)

/** Blau hinterlegt: die gerade gewählte Stufe der Schnellauswahl. */
val AccentBlueSurface = Color(0xFF16283F)

val OutlineColor = Color(0xFF3A4049)

/** Grün für „erledigt“: der Haken unter der Liste und die Deload-Woche im Zyklus. */
val AccentGreen = Color(0xFF3FA96B)
val AccentGreenSurface = Color(0xFF16301F)

/** Rot für den einen Knopf, der wirklich etwas zerstört: das Zurücksetzen der App. */
val AccentRed = Color(0xFFE05260)
val AccentRedSurface = Color(0xFF3A181C)

/** Marineblauer Kasten, der ein Superset zusammenfasst – hebt sich klar vom Rest ab. */
val SupersetBackground = Color(0xFF17356B)

/** Dünne Hilfslinien und Achsen im Tracking-Graphen. */
val ChartGridLine = Color(0xFF313943)

/**
 * Farben der Verlaufskurven, absteigend nach Auffälligkeit auf dunklem Grund.
 * Sechs Farben mal fünf Linienarten ergeben 30 unterscheidbare Kurven.
 */
val SeriesColors = listOf(
    Color(0xFF4C9AFF),
    Color(0xFF4ECB8F),
    Color(0xFFF2B441),
    Color(0xFFE86B7A),
    Color(0xFFB07CE8),
    Color(0xFF48C7D4)
)
