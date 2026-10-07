package de.beispiel.meintraining.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.beispiel.meintraining.MeinTrainingApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Neue Zeitzone oder von Hand verstellte Uhr: Der Termin der Erinnerungen steht als fester
 * Zeitpunkt bei WorkManager und käme nun zur falschen Uhrzeit – 18:00 in Berlin ist 17:00 in
 * Lissabon. Deshalb wird er hier neu ausgerechnet.
 *
 * Beide Meldungen gehören zu den wenigen, die Android auch an im Manifest angemeldete Empfänger
 * schickt; sie kommen vom System, deshalb ist der Empfänger nicht exportiert.
 */
class ReminderTimeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIMEZONE_CHANGED && intent.action != Intent.ACTION_TIME_CHANGED) return
        val app = context.applicationContext as? MeinTrainingApp ?: return
        // Das Neuanmelden liest die Einstellungen und braucht dafür einen Augenblick; ohne
        // goAsync dürfte Android den Prozess gleich nach onReceive beenden.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                app.reminders.reschedule()
            } finally {
                pending.finish()
            }
        }
    }
}
