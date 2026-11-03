package com.gympad.domain

import com.gympad.data.DayRow
import com.gympad.data.HistoryRow
import com.gympad.data.StatusRow
import com.gympad.data.TemplateRow

enum class DayStatus { REST, PARTIAL, COMPLETE }

data class TemplateVariant(val variantId: Long, val exerciseId: Long, val name: String)

data class TemplateSlot(
    val slotId: Long,
    val routineId: Long,
    val defaultSets: Int,
    val variants: List<TemplateVariant>,
)

data class LiftValue(val weightKg: Double, val reps: Int)

data class GhostValue(val weightKg: Double?, val reps: Int?)

data class SetRowUi(
    val index: Int,
    val weightKg: Double?,
    val reps: Int?,
    val completed: Boolean,
    val ghost: GhostValue,
)

data class ExerciseCardUi(
    val key: String,
    val slotId: Long?,
    val exerciseId: Long,
    val exerciseName: String,
    val variants: List<TemplateVariant>,
    val lastDay: Long?,
    val lastSets: List<LiftValue>,
    val personalRecord: LiftValue?,
    val sets: List<SetRowUi>,
    val completedSets: Int,
    val plannedSets: Int,
)

fun toTemplate(rows: List<TemplateRow>): List<TemplateSlot> =
    rows.groupBy { it.slotId }.map { (slotId, slotRows) ->
        val first = slotRows.first()
        TemplateSlot(
            slotId = slotId,
            routineId = first.routineId,
            defaultSets = first.defaultSets,
            variants = slotRows.mapNotNull { row ->
                val variantId = row.variantId ?: return@mapNotNull null
                TemplateVariant(variantId, row.exerciseId ?: return@mapNotNull null, row.exerciseName.orEmpty())
            },
        )
    }

/**
 * A slot's sets may be split across variants when equipment was swapped mid-exercise, so
 * completion counts per slot, not per exercise. A session with nothing ticked wasn't started.
 */
fun dayStatuses(rows: List<StatusRow>): Map<Long, DayStatus> =
    rows.groupBy { it.epochDay }.mapValues { (_, dayRows) ->
        val started = dayRows.groupBy { it.sessionId }.values.filter { session -> session.any { it.completedSets > 0 } }
        when {
            started.isEmpty() -> DayStatus.REST
            started.all(::isSessionComplete) -> DayStatus.COMPLETE
            else -> DayStatus.PARTIAL
        }
    }

private fun isSessionComplete(session: List<StatusRow>): Boolean =
    session.groupBy { it.slotId ?: -it.sessionExerciseId }.values.all { slot ->
        slot.sumOf { it.completedSets } >= slot.maxOf { it.plannedSets }
    }

fun lastSessionSets(history: List<HistoryRow>, exerciseId: Long, beforeDay: Long): List<HistoryRow> {
    val earlier = history.filter { it.exerciseId == exerciseId && it.epochDay < beforeDay }
    val latest = earlier.maxWithOrNull(compareBy({ it.epochDay }, { it.sessionExerciseId })) ?: return emptyList()
    return earlier.filter { it.sessionExerciseId == latest.sessionExerciseId }.sortedBy { it.setIndex }
}

fun personalRecord(history: List<HistoryRow>, exerciseId: Long): LiftValue? =
    history.filter { it.exerciseId == exerciseId }
        .maxWithOrNull(compareBy({ it.weightKg }, { it.reps }))
        ?.let { LiftValue(it.weightKg, it.reps) }

/**
 * With history, each row mirrors the same set number last time, which is what makes pyramids
 * work. Rows beyond last time's count repeat its final set. Without history, values typed into
 * earlier rows cascade down.
 */
fun ghostValues(
    setCount: Int,
    previous: List<HistoryRow>,
    entered: Map<Int, GhostValue>,
): List<GhostValue> {
    if (previous.isNotEmpty()) {
        return List(setCount) { index ->
            val match = previous.lastOrNull { it.setIndex <= index } ?: previous.first()
            GhostValue(match.weightKg, match.reps)
        }
    }
    var weight: Double? = null
    var reps: Int? = null
    return List(setCount) { index ->
        val ghost = GhostValue(weight, reps)
        weight = entered[index]?.weightKg ?: weight
        reps = entered[index]?.reps ?: reps
        ghost
    }
}

fun editableCards(
    slots: List<TemplateSlot>,
    dayRows: List<DayRow>,
    history: List<HistoryRow>,
    chosenVariants: Map<Long, Long>,
    epochDay: Long,
): List<ExerciseCardUi> = slots.filter { it.variants.isNotEmpty() }.map { slot ->
    val slotRows = dayRows.filter { it.slotId == slot.slotId }
    val exerciseId = chosenVariants[slot.slotId]?.takeIf { id -> slot.variants.any { it.exerciseId == id } }
        ?: slotRows.maxByOrNull { it.sessionExerciseId }?.exerciseId?.takeIf { id -> slot.variants.any { it.exerciseId == id } }
        ?: slot.variants.first().exerciseId
    val variant = slot.variants.first { it.exerciseId == exerciseId }
    val entries = slotRows.filter { it.exerciseId == exerciseId && it.setIndex != null }
        .associateBy { it.setIndex!! }
    val planned = slotRows.maxOfOrNull { it.plannedSets } ?: slot.defaultSets
    val setCount = maxOf(planned, (entries.keys.maxOrNull() ?: -1) + 1)
    val previous = lastSessionSets(history, exerciseId, epochDay)
    val ghosts = ghostValues(setCount, previous, entries.mapValues { (_, r) -> GhostValue(r.weightKg, r.reps) })
    ExerciseCardUi(
        key = "slot-${slot.slotId}",
        slotId = slot.slotId,
        exerciseId = exerciseId,
        exerciseName = variant.name,
        variants = slot.variants,
        lastDay = previous.firstOrNull()?.epochDay,
        lastSets = previous.map { LiftValue(it.weightKg, it.reps) },
        personalRecord = personalRecord(history, exerciseId),
        sets = List(setCount) { index ->
            val entry = entries[index]
            SetRowUi(index, entry?.weightKg, entry?.reps, entry?.completed == true, ghosts[index])
        },
        completedSets = slotRows.count { it.completed == true },
        plannedSets = planned,
    )
}

/** Past days are rebuilt only from what was stored that day, never from the current template. */
fun readOnlyCards(
    dayRows: List<DayRow>,
    history: List<HistoryRow>,
    epochDay: Long,
): List<ExerciseCardUi> =
    dayRows.groupBy { it.slotId ?: -it.sessionExerciseId }.values.flatMap { slotRows ->
        val byExercise = slotRows.groupBy { it.sessionExerciseId }.values.toList()
        val logged = byExercise.filter { rows -> rows.any { it.setIndex != null } }
        val shown = logged.ifEmpty { byExercise.take(1) }
        val split = shown.size > 1
        shown.map { rows ->
            val first = rows.first()
            val entries = rows.filter { it.setIndex != null }.associateBy { it.setIndex!! }
            val highest = (entries.keys.maxOrNull() ?: -1) + 1
            val setCount = if (split) highest else maxOf(first.plannedSets, highest)
            val previous = lastSessionSets(history, first.exerciseId, epochDay)
            ExerciseCardUi(
                key = "row-${first.sessionExerciseId}",
                slotId = first.slotId,
                exerciseId = first.exerciseId,
                exerciseName = first.exerciseName,
                variants = emptyList(),
                lastDay = previous.firstOrNull()?.epochDay,
                lastSets = previous.map { LiftValue(it.weightKg, it.reps) },
                personalRecord = personalRecord(history, first.exerciseId),
                sets = List(setCount) { index ->
                    val entry = entries[index]
                    SetRowUi(index, entry?.weightKg, entry?.reps, entry?.completed == true, GhostValue(null, null))
                },
                completedSets = rows.count { it.completed == true },
                plannedSets = setCount,
            )
        }
    }

fun formatWeight(weightKg: Double?): String {
    if (weightKg == null) return ""
    return if (weightKg % 1.0 == 0.0) weightKg.toLong().toString() else weightKg.toString()
}

/** Normalizes to a dot: Polish keypads type a comma, and the separator on screen depends on the language. */
fun sanitizeWeight(text: String): String {
    val normalized = text.replace(',', '.').filter { it.isDigit() || it == '.' }
    val dot = normalized.indexOf('.')
    val single = if (dot < 0) normalized else normalized.substring(0, dot + 1) + normalized.substring(dot + 1).replace(".", "")
    return single.take(6)
}

fun sanitizeReps(text: String): String = text.filter { it.isDigit() }.take(3)

fun parseWeight(text: String): Double? = text.replace(',', '.').toDoubleOrNull()

fun parseReps(text: String): Int? = text.toIntOrNull()
