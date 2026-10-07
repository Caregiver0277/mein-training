package de.beispiel.meintraining.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import de.beispiel.meintraining.reminder.ReminderNotifications

/** Wie eine Anfrage nach der Benachrichtigungs-Berechtigung ausging. */
enum class PermissionResult {
    /** Erlaubt – der Schalter darf an. */
    GRANTED,

    /** Abgelehnt; Android würde beim nächsten Mal noch einmal fragen. */
    DENIED,

    /**
     * Abgelehnt, und Android fragt nicht mehr – nach zweimaliger Ablehnung oder weil die
     * Benachrichtigungen der App in den Systemeinstellungen aus sind. Helfen kann nur noch der Weg
     * dorthin, siehe [openNotificationSettings].
     */
    BLOCKED
}

/**
 * Die Benachrichtigungs-Berechtigung, wie die Einstellungen sie brauchen: ob Nachrichten
 * ankommen ([allowed]) und eine Anfrage, die ihr Ergebnis zurückmeldet ([request]).
 */
@Stable
class NotificationPermission internal constructor(
    private val context: Context,
    private val launch: (onResult: (PermissionResult) -> Unit) -> Unit
) {
    /** Kommen Nachrichten an? Wird bei jeder Rückkehr in die App neu geprüft. */
    var allowed by mutableStateOf(ReminderNotifications.allowed(context))
        internal set

    /**
     * Fragt nach der Berechtigung, falls sie fehlt, und meldet das Ergebnis.
     *
     * Unter Android 13 und bei schon erteilter Berechtigung gibt es keinen Dialog; dann entscheidet
     * allein, ob die Benachrichtigungen der App in den Systemeinstellungen an sind.
     */
    fun request(onResult: (PermissionResult) -> Unit) {
        if (ReminderNotifications.hasPermission(context)) {
            allowed = ReminderNotifications.allowed(context)
            onResult(if (allowed) PermissionResult.GRANTED else PermissionResult.BLOCKED)
        } else {
            launch(onResult)
        }
    }

    internal fun refresh() {
        allowed = ReminderNotifications.allowed(context)
    }
}

@Composable
internal fun rememberNotificationPermission(): NotificationPermission {
    val context = LocalContext.current
    // Der Rückruf der laufenden Anfrage. Er überlebt ein Drehen während des Dialogs nicht – dann
    // bleibt der Schalter aus und ein zweites Tippen fragt erneut; Android merkt sich die Antwort.
    var pending by remember { mutableStateOf<((PermissionResult) -> Unit)?>(null) }
    var permission: NotificationPermission? = null

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permission?.refresh()
        val activity = context.findActivity()
        val result = when {
            granted && ReminderNotifications.allowed(context) -> PermissionResult.GRANTED
            granted -> PermissionResult.BLOCKED
            // Nach der ersten Ablehnung will Android eine Begründung sehen und fragt noch einmal;
            // nach der zweiten nicht mehr. Ohne Begründungswunsch ist der Dialog also vorbei.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS) ->
                PermissionResult.DENIED
            else -> PermissionResult.BLOCKED
        }
        pending?.invoke(result)
        pending = null
    }

    permission = remember(context, launcher) {
        NotificationPermission(context) { onResult ->
            pending = onResult
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    // In den Systemeinstellungen geändert: Beim Zurückkehren gilt der neue Stand.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permission.refresh() }
    return permission
}

/** Öffnet die Benachrichtigungs-Einstellungen dieser App. */
internal fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
