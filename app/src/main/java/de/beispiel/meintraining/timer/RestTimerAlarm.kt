package de.beispiel.meintraining.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import de.beispiel.meintraining.MainActivity

/** Kennung der Uhr im Wecker-Intent. */
const val EXTRA_TIMER_INDEX = "de.beispiel.meintraining.TIMER_INDEX"

private const val ACTION_TIMER_DONE = "de.beispiel.meintraining.TIMER_DONE"

/**
 * Meldet das Ende einer Pausenuhr beim System an.
 *
 * Der Wecker liegt beim System und nicht in der App: Nur so vibriert das Handy auch dann, wenn
 * die App geschlossen und ihr Prozess längst beendet ist. Ein Zeitgeber innerhalb der App wäre
 * mit dem Prozess gestorben. In der App läuft deshalb nur die Anzeige – sie rechnet aus
 * demselben Endzeitpunkt und braucht dafür keine eigene Uhr.
 */
object RestTimerAlarm {

    fun schedule(context: Context, index: Int, triggerAtMillis: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        // FLAG_UPDATE_CURRENT legt notfalls an; das Ergebnis kann hier also nicht fehlen.
        val alarm = requireNotNull(
            alarmIntent(context, index, PendingIntent.FLAG_UPDATE_CURRENT)
        )

        // setAlarmClock ist der einzige Wecker, den auch der Doze-Modus nicht verschiebt – und
        // genau darum geht es hier: Bei ausgeschaltetem Bildschirm muss die Pause auf die
        // Sekunde enden. Fehlt die Erlaubnis für exakte Wecker, tut es ein ungefährer; ein paar
        // Sekunden Versatz sind besser als gar kein Klingeln.
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            manager.canScheduleExactAlarms()
        if (exactAllowed) {
            manager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAtMillis, openAppIntent(context)),
                alarm
            )
        } else {
            manager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, alarm)
        }
    }

    fun cancel(context: Context, index: Int) {
        // FLAG_NO_CREATE: Gibt es gar keinen Wecker, ist auch nichts abzubestellen.
        val alarm = alarmIntent(context, index, PendingIntent.FLAG_NO_CREATE) ?: return
        context.getSystemService(AlarmManager::class.java)?.cancel(alarm)
        alarm.cancel()
    }

    /**
     * Beide Uhren zeigen auf denselben Empfänger. Damit das System sie auseinanderhält, bekommt
     * jede eine eigene Kennung *und* eine eigene Aktion: Extras zählen beim Vergleich zweier
     * PendingIntents nicht mit, die Aktion schon.
     */
    private fun alarmIntent(context: Context, index: Int, flags: Int): PendingIntent? {
        val intent = Intent(context, RestTimerReceiver::class.java)
            .setAction("$ACTION_TIMER_DONE.$index")
            .putExtra(EXTRA_TIMER_INDEX, index)
        return PendingIntent.getBroadcast(
            context,
            index,
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Was das System öffnet, wenn man den Wecker in der Statusleiste antippt. */
    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}
