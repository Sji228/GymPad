package com.gympad.domain

import com.gympad.data.Measurement

/** Stored by [code], so names and order can change freely. Height isn't a chip: BMI uses it. */
enum class BodyMeasure(val code: String, val unit: String) {
    WEIGHT("weight", "kg"),
    NECK("neck", "cm"),
    CHEST("chest", "cm"),
    WAIST("waist", "cm"),
    BELLY("belly", "cm"),
    HIPS("hips", "cm"),
    THIGH("thigh", "cm"),
    CALF("calf", "cm"),
    ARM("arm", "cm"),
    BODY_FAT("body_fat", "%"),
    BMI("bmi", ""),
    HEIGHT("height", "cm");

    /** BMI is never typed; it follows from body weight and height. */
    val computed: Boolean get() = this == BMI

    companion object {
        val chips: List<BodyMeasure> = entries.filter { it != HEIGHT }

        fun of(code: String): BodyMeasure? = entries.firstOrNull { it.code == code }
    }
}

data class BodyPoint(val id: Long?, val epochDay: Long, val value: Double)

fun bmi(weightKg: Double, heightCm: Double): Double = weightKg / ((heightCm / 100) * (heightCm / 100))

/**
 * The points to chart for [measure], oldest first. BMI is worked out from every logged weight and
 * the latest height, so changing the height corrects the whole history at once.
 */
fun bodyPoints(all: List<Measurement>, measure: BodyMeasure): List<BodyPoint> {
    if (measure.computed) {
        val height = latestHeight(all) ?: return emptyList()
        return all.filter { it.kind == BodyMeasure.WEIGHT.code }
            .sortedBy { it.epochDay }
            .map { BodyPoint(null, it.epochDay, bmi(it.value, height)) }
    }
    return all.filter { it.kind == measure.code }.sortedBy { it.epochDay }.map { BodyPoint(it.id, it.epochDay, it.value) }
}

fun latestHeight(all: List<Measurement>): Double? =
    all.filter { it.kind == BodyMeasure.HEIGHT.code }.maxByOrNull { it.epochDay }?.value

/** Sensible bounds, so a slip like 820 kg for 82.0 is caught before it ruins a chart. */
fun BodyMeasure.accepts(value: Double): Boolean = when (this) {
    BodyMeasure.WEIGHT -> value in 20.0..400.0
    BodyMeasure.BODY_FAT -> value in 2.0..75.0
    BodyMeasure.HEIGHT -> value in 100.0..250.0
    BodyMeasure.BMI -> false
    else -> value in 5.0..250.0
}
