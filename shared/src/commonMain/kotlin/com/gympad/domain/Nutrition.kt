package com.gympad.domain

import com.gympad.data.DayEnergyRow
import com.gympad.data.Food
import com.gympad.data.NutritionGoals
import java.text.Normalizer
import kotlin.math.abs

/** Stored by [code], so the order or names here can change without touching saved entries. */
enum class Meal(val code: Int) {
    BREAKFAST(0),
    BRUNCH(1),
    LUNCH(2),
    SNACKS(3),
    SUPPER(4);

    companion object {
        fun of(code: Int): Meal = entries.firstOrNull { it.code == code } ?: SNACKS
    }
}

data class Macros(val kcal: Double, val protein: Double, val carbs: Double, val fat: Double) {
    operator fun plus(other: Macros) =
        Macros(kcal + other.kcal, protein + other.protein, carbs + other.carbs, fat + other.fat)

    companion object {
        val ZERO = Macros(0.0, 0.0, 0.0, 0.0)
    }
}

fun Iterable<Macros>.total(): Macros = fold(Macros.ZERO, Macros::plus)

/** Something that can be logged: a food already in the diary, or a catalog product not yet copied. */
data class FoodItem(
    val foodId: Long?,
    val catalogCode: String?,
    val name: String,
    val namePl: String?,
    val brand: String?,
    val barcode: String?,
    val per100: Macros,
    val portionGrams: Double?,
    val packageGrams: Double?,
    val lastGrams: Double? = null,
    val custom: Boolean = false,
) {
    val key: String get() = foodId?.let { "food:$it" } ?: "catalog:$catalogCode"

    fun macrosFor(grams: Double): Macros = Macros(
        kcal = per100.kcal * grams / 100,
        protein = per100.protein * grams / 100,
        carbs = per100.carbs * grams / 100,
        fat = per100.fat * grams / 100,
    )
}

fun Food.toItem(lastGrams: Double? = null) = FoodItem(
    foodId = id,
    catalogCode = catalogCode,
    name = name,
    namePl = namePl,
    brand = brand,
    barcode = barcode,
    per100 = Macros(kcal, protein, carbs, fat),
    portionGrams = portionGrams,
    packageGrams = packageGrams,
    lastGrams = lastGrams,
    custom = catalogCode == null,
)

fun FoodItem.displayName(language: AppLanguage): String =
    if (language == AppLanguage.POLISH && namePl != null) namePl else name

/**
 * Lowercase ASCII words, so "Jabłko", "jablko" and "JABŁKO" all match. The catalog build script
 * applies the same rules to its search column; the two must stay in step.
 */
fun searchKey(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace('ł', 'l')
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

fun searchWords(query: String): List<String> = searchKey(query).split(' ').filter { it.isNotEmpty() }

fun FoodItem.matches(words: List<String>): Boolean {
    val key = searchKey(listOfNotNull(name, namePl, brand).joinToString(" "))
    return words.all { it in key }
}

/** Shop scanners and labels disagree about leading zeros: UPC-A 12 digits is EAN-13 with a 0 in front. */
fun barcodeVariants(code: String): List<String> {
    val digits = code.filter(Char::isDigit)
    if (digits.isEmpty()) return emptyList()
    val trimmed = digits.trimStart('0')
    return listOf(digits, "0$digits", trimmed, trimmed.padStart(13, '0'), trimmed.padStart(8, '0'))
        .filter { it.isNotEmpty() }
        .distinct()
}

fun looksLikeBarcode(text: String): Boolean = text.trim().let { it.length in 8..14 && it.all(Char::isDigit) }

/** Within 10% of the calorie goal counts as on target; with no goal, any logged day does. */
fun foodDayStatuses(rows: List<DayEnergyRow>, goals: NutritionGoals?): Map<Long, DayStatus> {
    val goal = goals?.kcal?.takeIf { it > 0 }
    return rows.associate { row ->
        row.epochDay to when {
            goal == null -> DayStatus.COMPLETE
            abs(row.kcal - goal) <= goal * 0.1 -> DayStatus.COMPLETE
            else -> DayStatus.PARTIAL
        }
    }
}

/** Grams as typed: "120", "120.5" or "120,5". */
fun parseAmount(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it < 100_000 }

/** Whole numbers stay whole, the rest keep one decimal: 120, 12.5. */
fun formatAmount(value: Double): String {
    val rounded = Math.round(value * 10) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}
