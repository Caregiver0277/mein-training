package de.beispiel.meintraining

import android.content.Context
import android.content.Intent

/**
 * Wohin ein Tipp auf eine Benachrichtigung führt.
 *
 * Eine Navigationsbibliothek gibt es nicht: Die Bereiche hängen an gemerkten Zuständen
 * (`MenuDestination` im Hauptscreen, `SettingsSection` in den Einstellungen). Das Ziel reist
 * deshalb als Angabe im Intent zur Activity und wird dort in diese Zustände übersetzt – beim Start
 * wie bei einer schon laufenden App (`onNewIntent`).
 */
enum class AppTarget {
    /** Der Trainingsplan, also der Hauptscreen. */
    PLAN,

    /** Der Deload-Bereich im Menü. */
    DELOAD,

    /** Einstellungen → Sicherung. */
    BACKUP;

    /** Der Intent, der die App bei diesem Ziel öffnet. */
    fun intent(context: Context): Intent = Intent(context, MainActivity::class.java)
        .putExtra(EXTRA_TARGET, name)
        // Läuft die App schon, kommt der Intent bei ihr an (onNewIntent), statt eine zweite
        // Activity über die erste zu legen – mit eigenem Zustand und doppeltem „Zurück“.
        .addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        )

    companion object {
        private const val EXTRA_TARGET = "de.beispiel.meintraining.ZIEL"

        /** Das Ziel aus einem Intent; `null`, wenn er keins trägt – etwa der Start vom Startbildschirm. */
        fun from(intent: Intent?): AppTarget? =
            intent?.getStringExtra(EXTRA_TARGET)?.let { name -> entries.firstOrNull { it.name == name } }
    }
}
