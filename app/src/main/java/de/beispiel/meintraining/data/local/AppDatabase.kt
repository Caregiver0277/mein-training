package de.beispiel.meintraining.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import de.beispiel.meintraining.data.model.Exercise
import de.beispiel.meintraining.data.model.ExerciseDefinition
import de.beispiel.meintraining.data.model.TrainingDay
import de.beispiel.meintraining.data.model.WeightLog
import de.beispiel.meintraining.data.model.WorkoutSession

/** Aktuelle Schemaversion; steht hier, damit auch die Tests sie benennen können. */
const val DATABASE_VERSION = 7

@Database(
    entities = [
        TrainingDay::class,
        Exercise::class,
        ExerciseDefinition::class,
        WeightLog::class,
        WorkoutSession::class
    ],
    version = DATABASE_VERSION,
    // Das exportierte Schema liegt unter app/schemas und ist die Grundlage künftiger
    // Migrationstests mit MigrationTestHelper.
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun trainingDayDao(): TrainingDayDao

    abstract fun exerciseDao(): ExerciseDao

    abstract fun exerciseDefinitionDao(): ExerciseDefinitionDao

    abstract fun weightLogDao(): WeightLogDao

    abstract fun workoutSessionDao(): WorkoutSessionDao

    companion object {
        private const val DATABASE_NAME = "mein_training.db"

        /**
         * Gewicht und Progressionsschritt wandern aus `Exercise` in die neue Tabelle
         * `ExerciseDefinition`, damit gleichnamige Übungen sie sich teilen. Kam ein Name
         * mehrfach mit unterschiedlichem Gewicht vor, gewinnt das höchste – so geht kein
         * bereits erarbeiteter Fortschritt verloren. Zusätzlich bekommt `Exercise` die
         * Spalte `variation`.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ExerciseDefinition` (
                        `name` TEXT NOT NULL,
                        `weightKg` REAL,
                        `progressionStepKg` REAL NOT NULL,
                        PRIMARY KEY(`name`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO `ExerciseDefinition` (name, weightKg, progressionStepKg)
                    SELECT name, MAX(weightKg), MIN(progressionStepKg) FROM `Exercise` GROUP BY name
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `Exercise_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `dayId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `variation` TEXT,
                        `sets` INTEGER,
                        `repsMin` INTEGER,
                        `repsMax` INTEGER,
                        `position` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `Exercise_new` (id, dayId, name, variation, sets, repsMin, repsMax, position)
                    SELECT id, dayId, name, NULL, sets, repsMin, repsMax, position FROM `Exercise`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `Exercise`")
                db.execSQL("ALTER TABLE `Exercise_new` RENAME TO `Exercise`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_Exercise_dayId` ON `Exercise` (`dayId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_Exercise_name` ON `Exercise` (`name`)")
            }
        }

        /**
         * Supersets und Gewichtsverlauf kommen dazu. Damit der Graph nicht bei null anfängt,
         * bekommt jede Übung mit Gewicht einen Startpunkt zum Zeitpunkt der Migration.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `Exercise` ADD COLUMN `supersetId` INTEGER")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `WeightLog` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `exerciseName` TEXT NOT NULL,
                        `weightKg` REAL NOT NULL,
                        `recordedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_WeightLog_exerciseName` " +
                        "ON `WeightLog` (`exerciseName`)"
                )
                db.execSQL(
                    """
                    INSERT INTO `WeightLog` (exerciseName, weightKg, recordedAt)
                    SELECT name, weightKg, ${System.currentTimeMillis()}
                    FROM `ExerciseDefinition` WHERE weightKg IS NOT NULL
                    """.trimIndent()
                )
            }
        }

        /** Abgehakte Trainings – Grundlage für Verlauf und Deload-Zyklus. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `WorkoutSession` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `dayId` INTEGER NOT NULL,
                        `completedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_WorkoutSession_completedAt` " +
                        "ON `WorkoutSession` (`completedAt`)"
                )
            }
        }

        /** Übungen können als Körpergewichtsübung markiert werden. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `ExerciseDefinition` " +
                        "ADD COLUMN `usesBodyweight` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * Körpergewichtsübungen fallen wieder weg.
         *
         * `weightKg` bleibt unverändert stehen: Bei einer Körpergewichtsübung war das die
         * Zusatzlast, und genau die stand auch schon vorher in der Trainingsliste. Der
         * Gewichtsverlauf bleibt ebenfalls unangetastet – dort stecken zwar noch Werte
         * samt Körpergewicht, aber das sind aufgezeichnete Messpunkte, die niemand
         * nachträglich umrechnen kann; sie lassen sich im Tracking einzeln löschen.
         *
         * `DROP COLUMN` gibt es erst in neueren SQLite-Fassungen, deshalb die Tabelle neu.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ExerciseDefinition_new` (
                        `name` TEXT NOT NULL,
                        `weightKg` REAL,
                        `progressionStepKg` REAL NOT NULL,
                        PRIMARY KEY(`name`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `ExerciseDefinition_new` (name, weightKg, progressionStepKg)
                    SELECT name, weightKg, progressionStepKg FROM `ExerciseDefinition`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `ExerciseDefinition`")
                db.execSQL("ALTER TABLE `ExerciseDefinition_new` RENAME TO `ExerciseDefinition`")
            }
        }

        /**
         * Die Richtung der Progression kommt dazu.
         *
         * Vorgabe 0: Bisher erhöhte der Pfeil immer, und genau dabei bleibt es für jede schon
         * angelegte Übung.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `ExerciseDefinition` " +
                        "ADD COLUMN `progressionDown` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /** Alle Migrationen in der Reihenfolge ihrer Versionen – auch für die Tests. */
        val MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7
        )

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(*MIGRATIONS)
                    // Ohne WAL besteht die Datenbank aus einer einzigen Datei. Das kostet bei
                    // dieser Datenmenge nichts und macht die Android-Sicherung verlässlich:
                    // Eine gesicherte .db ohne ihre -wal-Datei wäre beim Zurückspielen veraltet.
                    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                    .build()
                    .also { instance = it }
            }
    }
}
