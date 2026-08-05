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
 * Das System startet den Empfänger auch dann, wenn die App gar nicht offen ist – deshalb hängen
 * Vibrieren und Ton hier und nicht an der Oberfläche.
 *
 * Vibriert wird immer, der Ton lässt sich abschalten (siehe [RestTimerStore.soundEnabled]): Das
 * Handy liegt beim Training oft neben Fremden, ein Piepsen ist nicht überall willkommen – ein
 * Brummen in der Tasche stört dagegen niemanden.
 */
class RestTimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        vibrateTwice(context)

        val index = intent.getIntExtra(EXTRA_TIMER_INDEX, -1)

        // onReceive muss sofort zurückkehren, Ton und Schreiben dauern aber ein paar hundert
        // Millisekunden. goAsync hält den Empfänger so lange am Leben, bis der Ton verklungen
        // und die Uhr auch im Speicher wieder auf Anfang steht – sonst bräche der Ton mitten
        // im Klingen ab und beim nächsten Öffnen der App stünde eine Uhr auf 0:00.
        val pending = goAsync()
        scope.launch {
            val store = RestTimerStore(context)
            try {
                // Erst zurücksetzen, dann klingeln: Das Zurücksetzen dauert Millisekunden, der
                // Ton eine knappe halbe Sekunde – umgekehrt stünde die abgelaufene Uhr so lange
                // auf 0:00. Ohne Kennung ist nicht zu erkennen, welche Uhr gemeint war; der Ton
                // kommt trotzdem, denn genau dafür gibt es den Wecker.
                if (index >= 0) store.clearRun(index)
                if (store.isSoundEnabled()) {
                    RestTimerSound.play(context, store.currentSoundVolume())
                }
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

        /**
         * Einer für alle Aufrufe. Das System legt für jeden Wecker ein neues Exemplar des
         * Empfängers an; ein Bereich je Aufruf ließe mit jedem Klingeln einen Job zurück, den
         * niemand mehr abbricht. Beendet wird ohnehin nicht der Bereich, sondern der Empfänger
         * über `pending.finish()`.
         */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
