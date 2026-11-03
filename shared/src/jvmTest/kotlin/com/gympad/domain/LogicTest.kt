package com.gympad.domain

import com.gympad.data.DayRow
import com.gympad.data.HistoryRow
import com.gympad.data.StatusRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    private fun status(day: Long, session: Long, row: Long, slot: Long?, planned: Int, done: Int) =
        StatusRow(day, session, row, slot, planned, done)

    private fun hist(day: Long, row: Long, index: Int, weight: Double, reps: Int, exercise: Long = 1) =
        HistoryRow(exercise, day, row, index, weight, reps)

    @Test fun `session with nothing ticked is a rest day`() {
        assertEquals(DayStatus.REST, dayStatuses(listOf(status(1, 1, 1, 1, 3, 0)))[1])
    }

    @Test fun `all planned sets ticked is complete`() {
        val rows = listOf(status(1, 1, 1, 1, 3, 3), status(1, 1, 2, 2, 2, 2))
        assertEquals(DayStatus.COMPLETE, dayStatuses(rows)[1])
    }

    @Test fun `untouched exercise makes the day partial`() {
        val rows = listOf(status(1, 1, 1, 1, 3, 3), status(1, 1, 2, 2, 3, 0))
        assertEquals(DayStatus.PARTIAL, dayStatuses(rows)[1])
    }

    @Test fun `sets split across swapped variants count toward one slot`() {
        val rows = listOf(status(1, 1, 1, 7, 3, 2), status(1, 1, 2, 7, 3, 1))
        assertEquals(DayStatus.COMPLETE, dayStatuses(rows)[1])
    }

    @Test fun `least complete session decides a day with two routines`() {
        val rows = listOf(status(1, 1, 1, 1, 3, 3), status(1, 2, 2, 2, 3, 1))
        assertEquals(DayStatus.PARTIAL, dayStatuses(rows)[1])
    }

    @Test fun `browsed second routine does not downgrade the day`() {
        val rows = listOf(status(1, 1, 1, 1, 3, 3), status(1, 2, 2, 2, 3, 0))
        assertEquals(DayStatus.COMPLETE, dayStatuses(rows)[1])
    }

    @Test fun `ghosts follow a reverse pyramid by set index`() {
        val previous = listOf(hist(1, 1, 0, 100.0, 5), hist(1, 1, 1, 90.0, 8), hist(1, 1, 2, 80.0, 10))
        val ghosts = ghostValues(3, previous, emptyMap())
        assertEquals(listOf(100.0, 90.0, 80.0), ghosts.map { it.weightKg })
        assertEquals(listOf(5, 8, 10), ghosts.map { it.reps })
    }

    @Test fun `typed values do not override ghosts when history exists`() {
        val previous = listOf(hist(1, 1, 0, 100.0, 5), hist(1, 1, 1, 90.0, 8))
        val ghosts = ghostValues(2, previous, mapOf(0 to GhostValue(105.0, 5)))
        assertEquals(90.0, ghosts[1].weightKg!!, 0.0)
    }

    @Test fun `extra rows repeat the final set from last time`() {
        val previous = listOf(hist(1, 1, 0, 100.0, 5), hist(1, 1, 1, 90.0, 8))
        assertEquals(GhostValue(90.0, 8), ghostValues(4, previous, emptyMap())[3])
    }

    @Test fun `new exercise cascades set one into later rows`() {
        val ghosts = ghostValues(3, emptyList(), mapOf(0 to GhostValue(60.0, 10)))
        assertNull(ghosts[0].weightKg)
        assertEquals(GhostValue(60.0, 10), ghosts[1])
        assertEquals(GhostValue(60.0, 10), ghosts[2])
    }

    @Test fun `last session ignores today and other exercises`() {
        val history = listOf(
            hist(5, 1, 0, 80.0, 8),
            hist(9, 2, 0, 85.0, 8),
            hist(10, 3, 0, 90.0, 8),
            hist(9, 4, 0, 200.0, 1, exercise = 2),
        )
        val sets = lastSessionSets(history, 1, beforeDay = 10)
        assertEquals(listOf(85.0), sets.map { it.weightKg })
    }

    @Test fun `personal record is heaviest weight then most reps and per exercise`() {
        val history = listOf(
            hist(1, 1, 0, 100.0, 3),
            hist(1, 1, 1, 100.0, 5),
            hist(2, 2, 0, 90.0, 20),
            hist(2, 3, 0, 300.0, 1, exercise = 2),
        )
        assertEquals(LiftValue(100.0, 5), personalRecord(history, 1))
    }

    @Test fun `bodyweight record falls back to most reps`() {
        val history = listOf(hist(1, 1, 0, 0.0, 8), hist(2, 2, 0, 0.0, 12))
        assertEquals(LiftValue(0.0, 12), personalRecord(history, 1))
    }

    private fun day(row: Long, slot: Long?, exercise: Long, planned: Int, index: Int?, completed: Boolean? = null) =
        DayRow(1, row, slot, exercise, "E$exercise", 0, planned, index, if (index != null) 50.0 else null, if (index != null) 5 else null, completed)

    @Test fun `editable card uses the swapped variant's history and sets only`() {
        val slot = TemplateSlot(7, 1, 3, listOf(TemplateVariant(1, 1, "Bar"), TemplateVariant(2, 2, "DB")))
        val history = listOf(hist(1, 1, 0, 100.0, 5, exercise = 1), hist(2, 2, 0, 30.0, 12, exercise = 2))
        val rows = listOf(day(10, 7, 1, 3, 0, true))
        val card = editableCards(listOf(slot), rows, history, mapOf(7L to 2L), epochDay = 5).single()
        assertEquals("DB", card.exerciseName)
        assertEquals(LiftValue(30.0, 12), card.personalRecord)
        assertEquals(30.0, card.sets[0].ghost.weightKg!!, 0.0)
        assertTrue(card.sets.none { it.completed })
        assertEquals(1, card.completedSets)
    }

    @Test fun `read-only cards hide the untouched variant placeholder`() {
        val rows = listOf(day(10, 7, 1, 3, null), day(11, 7, 2, 3, 0, true))
        val card = readOnlyCards(rows, emptyList(), epochDay = 5).single()
        assertEquals(2L, card.exerciseId)
    }

    @Test fun `read-only skipped exercise still shows its empty rows`() {
        val card = readOnlyCards(listOf(day(10, 7, 1, 3, null)), emptyList(), epochDay = 5).single()
        assertEquals(3, card.sets.size)
        assertTrue(card.sets.none { it.completed })
    }

    @Test fun `weight input accepts a comma and only one separator`() {
        assertEquals("82.5", sanitizeWeight("82,5"))
        assertEquals("82.55", sanitizeWeight("82.5.5"))
        assertEquals(82.5, parseWeight("82,5")!!, 0.0)
        assertEquals(82.5, parseWeight("82.5")!!, 0.0)
        assertEquals("80", formatWeight(80.0))
        assertEquals("82.5", formatWeight(82.5))
    }
}
