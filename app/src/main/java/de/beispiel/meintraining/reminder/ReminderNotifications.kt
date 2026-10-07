package de.beispiel.meintraining.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.beispiel.meintraining.AppTarget
import de.beispiel.meintraining.R

/** Die drei Erinnerungen; jede hat ihre eigene Nummer, eine neue ersetzt die vorige gleicher Art. */
enum class ReminderKind(val notificationId: Int, val target: AppTarget) {
    DELOAD(1, AppTarget.DELOAD),
    PAUSE(2, AppTarget.PLAN),
    BACKUP(3, AppTarget.BACKUP)
}

/**
 * Der Benachrichtigungskanal der Erinnerungen und das Zeigen einer Nachricht.
 *
 * Alles bleibt auf dem Gerät: Die Nachrichten entstehen hier und gehen an Android, ohne Server und
 * ohne Konto – die App hat nicht einmal die Berechtigung fürs Internet.
 */
object ReminderNotifications {

    private const val CHANNEL_ID = "erinnerungen"

    /**
     * Legt den eigenen Kanal an. Mehrfach aufgerufen passiert nichts weiter – Android behält
     * Einstellungen, die der Nutzer am Kanal vorgenommen hat.
     */
    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.reminder_channel_description) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /**
     * Kommen Nachrichten überhaupt an? Ab Android 13 braucht es dafür die Berechtigung; auf jeder
     * Version lassen sich die Benachrichtigungen der App oder nur dieser Kanal in den
     * Systemeinstellungen abschalten.
     */
    fun allowed(context: Context): Boolean {
        if (!hasPermission(context)) return false
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannel(CHANNEL_ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** Die Laufzeit-Berechtigung allein – unter Android 13 gibt es sie nicht, sie gilt als erteilt. */
    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Zeigt eine Erinnerung; ohne Berechtigung passiert nichts. */
    fun show(context: Context, kind: ReminderKind, title: String, text: String) {
        createChannel(context)
        val tap = PendingIntent.getActivity(
            context,
            // Je Art ein eigener PendingIntent: Bei gleicher Nummer ersetzte Android das Ziel
            // der einen Nachricht durch das der anderen.
            kind.notificationId,
            kind.target.intent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            // Längere Texte – der Grund einer gescheiterten Sicherung – stehen aufgeklappt ganz da.
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        // Die Prüfung steht hier und nicht nur beim Aufrufer: Zwischen Einschalten und Erinnern
        // kann die Berechtigung in den Systemeinstellungen entzogen worden sein.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            NotificationManagerCompat.from(context).notify(kind.notificationId, notification)
        }
    }
}
