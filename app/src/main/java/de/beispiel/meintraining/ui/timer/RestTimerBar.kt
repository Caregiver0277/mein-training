package de.beispiel.meintraining.ui.timer

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.beispiel.meintraining.R
import de.beispiel.meintraining.data.local.RestTimer
import de.beispiel.meintraining.ui.theme.AccentGreen
import de.beispiel.meintraining.ui.theme.AccentGreenSurface
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.CardBackground
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.MeinTrainingTheme
import de.beispiel.meintraining.ui.theme.MenuButtonSurface
import de.beispiel.meintraining.ui.theme.TextPrimary
import de.beispiel.meintraining.ui.theme.TextSecondary
import de.beispiel.meintraining.util.formatRestTime
import kotlinx.coroutines.delay

/**
 * Abstand zwischen zwei Aktualisierungen der laufenden Anzeige.
 *
 * Fein genug, dass der Balken gleitet statt einmal je Sekunde zu springen, und grob genug, dass
 * die Uhr nicht das ganze Gerät beschäftigt – siehe [RestTimerBar].
 */
private const val TICK_MILLIS = 100L

/** Hängt die Pausenuhren an ihr ViewModel. */
@Composable
fun RestTimerRoute(modifier: Modifier = Modifier) {
    val viewModel: RestTimerViewModel = viewModel(factory = RestTimerViewModel.Factory)
    val timers by viewModel.timers.collectAsStateWithLifecycle()

    RestTimerBar(
        timers = timers,
        onToggle = viewModel::onToggle,
        onReset = viewModel::onReset,
        onDurationChange = viewModel::onDurationChange,
        modifier = modifier
    )
}

/**
 * Zwei Pausenuhren nebeneinander über der Übungsliste.
 *
 * Zwei, weil sich die Pausen im Training unterscheiden: kurz nach Isolationsübungen, lang nach
 * schweren Sätzen. Beide stehen auf einer Höhe und sind gleich breit.
 *
 * Der eigentliche Zeitgeber liegt im System (siehe
 * [de.beispiel.meintraining.timer.RestTimerAlarm]); hier läuft nur die Anzeige mit.
 */
@Composable
fun RestTimerBar(
    timers: List<RestTimer>,
    onToggle: (Int) -> Unit,
    onReset: (Int) -> Unit,
    onDurationChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    /** Welche Uhr gerade eingestellt wird; `null`, solange kein Dialog offen ist. */
    var configuring by remember { mutableStateOf<Int?>(null) }

    /**
     * Die Anzeige braucht einen eigenen Takt: Im Speicher steht nur der Endzeitpunkt, die
     * Restzeit ergibt sich erst aus „jetzt“.
     *
     * Nichts wird dabei neu zusammengesetzt: Der Balken hängt am Zeichnen, die Ziffern an einer
     * abgeleiteten Sekunde. Neu *gezeichnet* wird aber bei jedem Takt, und genau daran hängt der
     * Preis. Bild für Bild getaktet – so lief es vorher – heißt auf einem 120-Hz-Gerät 120
     * Zeichendurchgänge je Sekunde für einen Balken, der bei drei Minuten Pause um etwa einen
     * Bildpunkt pro Sekunde wandert. [TICK_MILLIS] holt denselben gleitenden Eindruck für rund
     * ein Zwölftel der Arbeit: Ein Zehntel einer Sekunde ist bei jeder brauchbaren Pausenlänge
     * weniger als ein Bildpunkt Sprung, also nicht zu sehen.
     *
     * Getickt wird nur, solange wirklich eine Uhr läuft *und* der Bildschirm sie zeigt.
     * `LaunchedEffect` allein reicht dafür nicht: Die Composition überlebt eine angehaltene
     * Activity, der Takt liefe also in der Hosentasche weiter.
     */
    val frame = remember { mutableLongStateOf(System.currentTimeMillis()) }
    val isAnyRunning = timers.any { it.isRunning }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(isAnyRunning, lifecycleOwner) {
        if (!isAnyRunning) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                frame.longValue = System.currentTimeMillis()
                delay(TICK_MILLIS)
            }
        }
    }

    /**
     * Die Uhr des Augenblicks, in dem gerechnet wird – nicht der zuletzt getickte Stand.
     *
     * [frame] wird nur gelesen, damit Compose die Anzeige überhaupt an den Takt hängt; es liegt
     * nie vor der tatsächlichen Zeit, `maxOf` liefert also immer diese. Wichtig ist das gleich
     * zweimal: Im ersten Bild nach dem Start steht im Takt noch der Stand von vorhin – eine
     * gerade gestartete Uhr stünde einen Wimpernschlag lang eine Sekunde zu hoch, eine
     * fortgesetzte risse den Balken um die Pausenlänge nach rechts. Und zwischen zwei Takten
     * liegen [TICK_MILLIS]; ohne die echte Uhr bliebe die Anzeige innerhalb eines Bildes auf dem
     * zuletzt getickten Stand stehen.
     */
    val nowMillis = remember { { maxOf(frame.longValue, System.currentTimeMillis()) } }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.CardSpacing)
    ) {
        timers.forEachIndexed { index, timer ->
            RestTimerBox(
                timer = timer,
                nowMillis = nowMillis,
                onToggle = { onToggle(index) },
                onReset = { onReset(index) },
                onConfigure = { configuring = index }
            )
        }
    }

    // Über die Kennung nachgeschlagen statt blind indiziert: Der Dialog steht neben der Liste,
    // aus der er stammt, und eine Uhr weniger als erwartet wäre sonst ein Absturz.
    configuring?.let { index ->
        val timer = timers.getOrNull(index) ?: return@let
        RestTimerDialog(
            initialSeconds = timer.durationSeconds,
            onConfirm = { seconds ->
                configuring = null
                onDurationChange(index, seconds)
            },
            onDismiss = { configuring = null }
        )
    }
}

/**
 * Eine Uhr: links der Knopf, rechts daneben die Zeit.
 *
 * Vier Gesten auf engem Raum, deshalb liegen sie bewusst gestaffelt: Der Knopf fängt kurzen
 * Druck (starten/anhalten) und langen Druck (zurücksetzen) ab, die Fläche darum herum erbt den
 * kurzen Druck – ein größeres Ziel schadet nie – und nimmt den langen für das Einstellen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.RestTimerBox(
    timer: RestTimer,
    nowMillis: () -> Long,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onConfigure: () -> Unit
) {
    val haptics = LocalHapticFeedback.current

    /**
     * Nur die ganze Sekunde, nicht jeder Takt: `derivedStateOf` meldet sich erst, wenn sich die
     * Ziffern wirklich ändern. Ohne das baute sich die Zeile mit jedem Takt neu auf, um dieselbe
     * Zahl noch einmal hinzuschreiben.
     */
    val remaining by remember(timer, nowMillis) {
        derivedStateOf { timer.remainingSeconds(nowMillis()) }
    }

    val textColor by animateColorAsState(
        targetValue = when {
            timer.isRunning -> AccentGreen
            timer.isPaused -> TextSecondary
            else -> TextPrimary
        },
        label = "timerTextColor"
    )

    val isIdle = !timer.isRunning && !timer.isPaused

    Row(
        modifier = Modifier
            .weight(1f)
            .height(Dimens.TimerBoxHeight)
            .clip(Dimens.CornerCard)
            .background(CardBackground)
            // Wie viel der Pause noch aussteht, als Füllstand hinter der Zeile: Auf einen Blick
            // erkennbar, ohne die Ziffern lesen zu müssen. Der Füllstand wird erst hier beim
            // Zeichnen bestimmt und nicht oben im Rumpf – so hängt am Takt nur das Zeichnen.
            .drawBehind {
                // Im Ruhezustand stünde der Balken voll da und sähe nach „läuft“ aus.
                if (isIdle) return@drawBehind
                drawRect(
                    color = AccentGreenSurface,
                    size = Size(
                        width = size.width * timer.remainingFraction(nowMillis()),
                        height = size.height
                    )
                )
            }
            .combinedClickable(
                role = Role.Button,
                onClick = onToggle,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onConfigure()
                },
                onLongClickLabel = stringResource(R.string.timer_configure)
            )
            .padding(horizontal = Dimens.TimerBoxPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.TimerButtonSize)
                .clip(CircleShape)
                .background(if (timer.isRunning) AccentGreen else MenuButtonSurface)
                .combinedClickable(
                    role = Role.Button,
                    onClick = onToggle,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onReset()
                    },
                    onLongClickLabel = stringResource(R.string.timer_reset)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                // Eigene Vektoren statt Icons.Filled: „Pause“ steckt nur im großen
                // material-icons-extended, und das lohnt für ein Symbol nicht.
                painter = painterResource(
                    if (timer.isRunning) R.drawable.ic_timer_pause else R.drawable.ic_timer_play
                ),
                contentDescription = stringResource(
                    if (timer.isRunning) R.string.timer_pause else R.string.timer_start
                ),
                tint = if (timer.isRunning) CardBackground else TextPrimary,
                modifier = Modifier.size(Dimens.TimerIconSize)
            )
        }

        Text(
            text = formatRestTime(remaining),
            style = AppTextStyles.Timer,
            color = textColor,
            modifier = Modifier
                .weight(1f)
                .padding(start = Dimens.SectionSpacingSmall)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A, widthDp = 360)
@Composable
private fun RestTimerBarPreview() {
    MeinTrainingTheme {
        RestTimerBar(
            timers = listOf(
                RestTimer(durationSeconds = 90),
                RestTimer(
                    durationSeconds = 180,
                    endAtMillis = System.currentTimeMillis() + 72_000L
                )
            ),
            onToggle = {},
            onReset = {},
            onDurationChange = { _, _ -> },
            modifier = Modifier.padding(Dimens.ScreenPaddingHorizontal)
        )
    }
}
