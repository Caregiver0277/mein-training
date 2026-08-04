package de.beispiel.meintraining.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import de.beispiel.meintraining.data.local.RestTimerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Läutet eine abgelaufene Pausenuhr aus.
 *
 * Das System startet den Empfänger auch dann, wenn die App gar nicht offen ist – deshalb hängt
 * das Vibrieren hier und nicht an der Oberfläche.
 */
class RestTimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        vibrateTwice(context)

        val index = intent.getIntExtra(EXTRA_TIMER_INDEX, -1)
        if (index < 0) return

        // onReceive muss sofort zurückkehren, das Schreiben dauert aber ein paar Millisekunden.
        // goAsync hält den Empfänger so lange am Leben, bis die Uhr auch im Speicher wieder auf
        // Anfang steht – sonst stünde beim nächsten Öffnen der App eine Uhr auf 0:00.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                RestTimerStore(context).clearRun(index)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Zwei deutlich getrennte Stöße. Als Wecker angemeldet (USAGE_ALARM), damit das Handy auch
     * dann brummt, wenn es lautlos gestellt ist – im Studio ist es das meistens.
     */
    private fun vibrateTwice(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        if (vibrator == null || !vibrator.hasVibrator()) return

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        // Warten – brummen – Pause – brummen.
        val pattern = longArrayOf(0L, 450L, 250L, 450L)
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, NO_REPEAT), attributes)
    }

    private companion object {
        const val NO_REPEAT = -1
    }
}
