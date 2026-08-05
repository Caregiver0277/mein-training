package de.beispiel.meintraining.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-test.db"

/**
 * Migrationen sind der einzige Ort, an dem ein Fehler echte Daten vernichtet – und der einzige,
 * den man nicht durch erneutes Ausprobieren repariert.
 *
 * Die Tests bauen eine Datenbank in der alten Fassung von Hand auf und lassen sie anschließend
 * von Room öffnen. Room führt dabei alle Migrationen aus *und* vergleicht das Ergebnis mit dem
 * Schema der Entitäten – eine Migration, die eine Spalte oder einen Index verfehlt, lässt das
 * Öffnen scheitern. Für die Zeit vor [DATABASE_VERSION] gibt es keine exportierten Schemata,
 * deshalb dieser Weg statt `MigrationTestHelper`; für künftige Versionen liegen sie unter
 * `app/schemas` bereit.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DB)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
    }

    /**
     * Gewicht und Progressionsschritt wandern in die gemeinsame `ExerciseDefinition`. Kommt ein
     * Name mehrfach vor, muss das höchste Gewicht gewinnen – sonst ginge erarbeiteter
     * Fortschritt verloren.
     */
    @Test
    fun gleichnamigeUebungenBehaltenDasHoechsteGewicht() {
        createVersion1 { db ->
            db.execSQL(
                """
                INSERT INTO Exercise (id, dayId, name, weightKg, progressionStepKg, sets,
                    repsMin, repsMax, position)
                VALUES (1, 1, 'Bankdrücken', 60.0, 2.5, 3, 4, 6, 0)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO Exercise (id, dayId, name, weightKg, progressionStepKg, sets,
                    repsMin, repsMax, position)
                VALUES (2, 2, 'Bankdrücken', 65.0, 1.25, 3, 4, 6, 0)
                """.trimIndent()
            )
        }

        migrated { db ->
            db.query("SELECT name, weightKg, progressionStepKg FROM ExerciseDefinition")
                .use { cursor ->
                    assertEquals(1, cursor.count)
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Bankdrücken", cursor.getString(0))
                    assertEquals(65.0, cursor.getDouble(1), TOLERANCE)
                    assertEquals(1.25, cursor.getDouble(2), TOLERANCE)
                }
            // Die Zeilen der Trainingstage bleiben erhalten, nur ohne die Gewichtsspalten.
            db.query("SELECT COUNT(*) FROM Exercise").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2, cursor.getInt(0))
            }
        }
    }

    /** Übungen mit Gewicht bekommen einen Startpunkt, damit der Graph nicht bei null anfängt. */
    @Test
    fun uebungenMitGewichtBekommenEinenStartpunktImVerlauf() {
        createVersion1 { db ->
            db.execSQL(
                """
                INSERT INTO Exercise (id, dayId, name, weightKg, progressionStepKg, sets,
                    repsMin, repsMax, position)
                VALUES (1, 1, 'Bankdrücken', 60.0, 2.5, 3, 4, 6, 0)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO Exercise (id, dayId, name, weightKg, progressionStepKg, sets,
                    repsMin, repsMax, position)
                VALUES (2, 1, 'Dehnen', NULL, 2.5, NULL, NULL, NULL, 1)
                """.trimIndent()
            )
        }

        migrated { db ->
            db.query("SELECT exerciseName, weightKg FROM WeightLog").use { cursor ->
                assertEquals(1, cursor.count)
                assertTrue(cursor.moveToFirst())
                assertEquals("Bankdrücken", cursor.getString(0))
                assertEquals(60.0, cursor.getDouble(1), TOLERANCE)
            }
        }
    }

    /** Der ganze Weg von der ersten Fassung bis heute – so, wie ihn ein Altbestand geht. */
    @Test
    fun einAltbestandKommtVollstaendigDurch() {
        createVersion1 { db ->
            db.execSQL("INSERT INTO TrainingDay (id, name) VALUES (1, 'Tag 1')")
            db.execSQL(
                """
                INSERT INTO Exercise (id, dayId, name, weightKg, progressionStepKg, sets,
                    repsMin, repsMax, position)
                VALUES (1, 1, 'Klimmzüge', 5.0, 2.5, 3, 5, 9, 0)
                """.trimIndent()
            )
        }

        migrated { db ->
            // Gewicht und Schrittweite überstehen den ganzen Weg – auch das Entfernen der
            // Körpergewichtsspalte in Fassung 6.
            db.query("SELECT weightKg, progressionStepKg FROM ExerciseDefinition WHERE name = 'Klimmzüge'")
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(5.0, cursor.getDouble(0), TOLERANCE)
                    assertEquals(2.5, cursor.getDouble(1), TOLERANCE)
                }
            db.query("SELECT COUNT(*) FROM WorkoutSession").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            db.query("SELECT variation, supersetId FROM Exercise WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
                assertTrue(cursor.isNull(1))
            }
        }
    }

    /**
     * Körpergewichtsübungen fallen weg: Die Spalte verschwindet, Gewicht und Schrittweite
     * bleiben. Für diesen Schritt gibt es exportierte Schemata, deshalb der direkte Weg über
     * [MigrationTestHelper] statt über eine von Hand gebaute Datenbank.
     */
    @Test
    fun dieKoerpergewichtsspalteFaelltWegOhneDatenZuVerlieren() {
        helper.createDatabase(TEST_DB, 5).use { db ->
            db.execSQL(
                """
                INSERT INTO ExerciseDefinition (name, weightKg, progressionStepKg, usesBodyweight)
                VALUES ('Klimmzüge', 7.5, 1.25, 1)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO ExerciseDefinition (name, weightKg, progressionStepKg, usesBodyweight)
                VALUES ('Bankdrücken', 60.0, 2.5, 0)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, *AppDatabase.MIGRATIONS)
        db.use {
            // Die Zusatzlast bleibt als Gewicht stehen; sie stand auch vorher schon so in
            // der Trainingsliste.
            it.query("SELECT name, weightKg, progressionStepKg FROM ExerciseDefinition ORDER BY name")
                .use { cursor ->
                    assertEquals(2, cursor.count)
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Bankdrücken", cursor.getString(0))
                    assertEquals(60.0, cursor.getDouble(1), TOLERANCE)
                    assertTrue(cursor.moveToNext())
                    assertEquals("Klimmzüge", cursor.getString(0))
                    assertEquals(7.5, cursor.getDouble(1), TOLERANCE)
                    assertEquals(1.25, cursor.getDouble(2), TOLERANCE)
                }
        }
    }

    /**
     * Die Richtung der Progression kommt dazu. Für jede bestehende Übung muss dabei „nach oben“
     * herauskommen – bis hierher gab es nichts anderes, und ein Pfeil, der nach dem Update
     * plötzlich Gewicht abnimmt, wäre der ärgerlichste denkbare Nebeneffekt.
     */
    @Test
    fun dieRichtungKommtDazuUndZeigtWeiterNachOben() {
        helper.createDatabase(TEST_DB, 6).use { db ->
            db.execSQL(
                """
                INSERT INTO ExerciseDefinition (name, weightKg, progressionStepKg)
                VALUES ('Bankdrücken', 60.0, 2.5)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true, *AppDatabase.MIGRATIONS)
        db.use {
            it.query("SELECT weightKg, progressionStepKg, progressionDown FROM ExerciseDefinition")
                .use { cursor ->
                    assertEquals(1, cursor.count)
                    assertTrue(cursor.moveToFirst())
                    assertEquals(60.0, cursor.getDouble(0), TOLERANCE)
                    assertEquals(2.5, cursor.getDouble(1), TOLERANCE)
                    assertEquals(0, cursor.getInt(2))
                }
        }
    }

    /** Legt die Datenbank so an, wie Version 1 der App sie hinterlassen hat. */
    private fun createVersion1(fill: (SQLiteDatabase) -> Unit) {
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(TEST_DB), null)
        db.use {
            it.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `TrainingDay` (
                    `id` INTEGER NOT NULL,
                    `name` TEXT NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            it.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `Exercise` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `dayId` INTEGER NOT NULL,
                    `name` TEXT NOT NULL,
                    `weightKg` REAL,
                    `progressionStepKg` REAL NOT NULL,
                    `sets` INTEGER,
                    `repsMin` INTEGER,
                    `repsMax` INTEGER,
                    `position` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            it.execSQL("CREATE INDEX IF NOT EXISTS `index_Exercise_dayId` ON `Exercise` (`dayId`)")
            it.execSQL("CREATE INDEX IF NOT EXISTS `index_Exercise_name` ON `Exercise` (`name`)")
            fill(it)
            it.version = 1
        }
    }

    /**
     * Öffnet die Datenbank mit Room. Das führt die Migrationen aus und prüft anschließend, ob
     * das Ergebnis zum Schema der Entitäten passt.
     */
    private fun migrated(assertions: (SupportSQLiteDatabase) -> Unit) {
        val database = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()
        try {
            assertions(database.openHelper.writableDatabase)
        } finally {
            database.close()
        }
    }

    private companion object {
        const val TOLERANCE = 0.001
    }
}
