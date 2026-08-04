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
     * Getickt wird nur, solange wirklich eine Uhr läuft *und* der Bildschirm sie zeigt.
     * `LaunchedEffect` allein reicht dafür nicht: Die Composition überlebt eine angehaltene
     * Activity, der Takt liefe also in der Hosentasche weiter.
     *
     * Gewartet wird bis zur nächsten vollen Sekunde statt in festen Abständen. Die Anzeige
     * kennt nur ganze Sekunden – ein kürzerer Takt ergäbe mehrfach dasselbe Bild.
     */
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val isAnyRunning = timers.any { it.isRunning }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(isAnyRunning, lifecycleOwner) {
        if (!isAnyRunning) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val current = System.currentTimeMillis()
                now = current
                delay(MILLIS_PER_SECOND - current % MILLIS_PER_SECOND)
            }
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.CardSpacing)
    ) {
        timers.forEachIndexed { index, timer ->
            RestTimerBox(
                timer = timer,
                nowMillis = now,
                onToggle = { onToggle(index) },
                onReset = { onReset(index) },
                onConfigure = { configuring = index }
            )
        }
    }

    configuring?.let { index ->
        RestTimerDialog(
            initialSeconds = timers[index].durationSeconds,
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
    nowMillis: Long,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onConfigure: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val remaining = timer.remainingSeconds(nowMillis)

    val textColor by animateColorAsState(
        targetValue = when {
            timer.isRunning -> AccentGreen
            timer.isPaused -> TextSecondary
            else -> TextPrimary
        },
        label = "timerTextColor"
    )

    /**
     * Wie viel der Pause noch aussteht, als Füllstand hinter der Zeile: Auf einen Blick
     * erkennbar, ohne die Ziffern lesen zu müssen.
     *
     * Bewusst ohne `animateFloatAsState`: Der Zielwert wechselt mit jeder Sekunde, jede
     * Änderung startete also eine neue Feder und der Balken liefe die ganze Pause über in
     * voller Bildwiederholrate – für eine Strecke von rund einem Prozent je Sekunde. So
     * springt er einmal pro Sekunde, was man bei dieser Schrittweite nicht sieht.
     */
    val progress = if (timer.durationSeconds > 0) {
        (remaining.toFloat() / timer.durationSeconds).coerceIn(0f, 1f)
    } else {
        0f
    }
    val isIdle = !timer.isRunning && !timer.isPaused

    Row(
        modifier = Modifier
            .weight(1f)
            .height(Dimens.TimerBoxHeight)
            .clip(Dimens.CornerCard)
            .background(CardBackground)
            .drawBehind {
                // Im Ruhezustand stünde der Balken voll da und sähe nach „läuft“ aus.
                if (isIdle) return@drawBehind
                drawRect(
                    color = AccentGreenSurface,
                    size = Size(width = size.width * progress, height = size.height)
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

private const val MILLIS_PER_SECOND = 1000L

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
