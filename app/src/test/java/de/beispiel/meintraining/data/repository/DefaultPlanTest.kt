package de.beispiel.meintraining.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Plan ist von Hand abgetippt – diese Tests fangen die Fehler ab, die dabei entstehen
 * und in der App erst spät auffallen würden.
 */
class DefaultPlanTest {

    @Test
    fun planDecktAlleVierTageAb() {
        assertEquals(listOf(1, 2, 3, 4), DEFAULT_PLAN.map { it.dayId })
    }

    @Test
    fun gleicheUebungHatUeberallDasGleicheGewicht() {
        // Gewichte hängen am Namen. Zwei verschiedene Angaben für denselben Namen würden
        // sich beim Einspielen gegenseitig überschreiben.
        DEFAULT_PLAN.flatMap { it.exercises }
            .filter { it.weightKg != null }
            .groupBy { it.name }
            .forEach { (name, entries) ->
                val weights = entries.map { it.weightKg }.distinct()
                assertEquals("Widersprüchliche Gewichte für „$name“", 1, weights.size)
            }
    }

    @Test
    fun jedesSupersetHatMindestensZweiUebungen() {
        DEFAULT_PLAN.forEach { day ->
            day.exercises.filter { it.supersetGroup != null }
                .groupBy { it.supersetGroup }
                .forEach { (group, members) ->
                    assertTrue(
                        "Superset $group an Tag ${day.dayId} hat nur ${members.size} Übung",
                        members.size >= 2
                    )
                }
        }
    }

    @Test
    fun supersetsStehenDirektUntereinander() {
        // Nur zusammenhängende Blöcke ergeben einen geschlossenen grauen Kasten.
        DEFAULT_PLAN.forEach { day ->
            day.exercises.withIndex()
                .filter { it.value.supersetGroup != null }
                .groupBy { it.value.supersetGroup }
                .forEach { (group, entries) ->
                    val indices = entries.map { it.index }
                    assertEquals(
                        "Superset $group an Tag ${day.dayId} ist unterbrochen",
                        indices.last() - indices.first() + 1,
                        indices.size
                    )
                }
        }
    }

    @Test
    fun wiederholungsspannenSindNichtVertauscht() {
        DEFAULT_PLAN.flatMap { it.exercises }.forEach { exercise ->
            val min = exercise.repsMin
            val max = exercise.repsMax
            if (min != null && max != null) {
                assertTrue("„${exercise.name}“: $min-$max", min <= max)
            }
        }
    }

    @Test
    fun uebungenMitWiederholungenHabenAuchSaetze() {
        DEFAULT_PLAN.flatMap { it.exercises }
            .filter { it.repsMin != null || it.repsMax != null }
            .forEach { exercise ->
                assertTrue("„${exercise.name}“ ohne Sätze", exercise.sets != null)
            }
    }

    @Test
    fun geteilteUebungenKommenAnMehrerenTagenVor() {
        // Die Übungen, deren Gewicht laut Plan übertragen werden soll.
        val shared = listOf(
            "pull ups", "pushup", "Hüfte dehn routine", "couch stretch", "Triceps",
            "Cable lateral raise", "Face pulls", "Bizep curls", "Dead Bug"
        )
        shared.forEach { name ->
            val days = DEFAULT_PLAN.filter { day -> day.exercises.any { it.name == name } }
            assertTrue("„$name“ steht nur an ${days.size} Tag", days.size >= 2)
        }
    }
}
