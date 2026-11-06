package com.gympad.domain

import com.gympad.data.DayEnergyRow
import com.gympad.data.NutritionGoals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionTest {
    @Test fun `search ignores case and Polish letters`() {
        assertEquals("piers z kurczaka", searchKey("Pierś z KURCZAKA"))
        assertEquals("zolc l", searchKey("Żółć, Ł"))
        assertEquals(listOf("jablko", "zielone"), searchWords("  Jabłko   zielone! "))
    }

    @Test fun `barcodes match with or without leading zeros`() {
        val variants = barcodeVariants("036000291452")
        assertTrue("0036000291452" in variants)
        assertTrue("036000291452" in variants)
        assertTrue(barcodeVariants("abc").isEmpty())
        assertTrue(looksLikeBarcode("5900820000011"))
        assertFalse(looksLikeBarcode("590"))
        assertFalse(looksLikeBarcode("banana"))
    }

    @Test fun `amounts accept a comma and stay positive`() {
        assertEquals(12.5, parseAmount("12,5")!!, 0.0)
        assertEquals(null, parseAmount("0"))
        assertEquals(null, parseAmount("abc"))
        assertEquals("120", formatAmount(120.0))
        assertEquals("12.5", formatAmount(12.54))
    }

    @Test fun `days near the calorie goal are green, the rest yellow`() {
        val rows = listOf(DayEnergyRow(1, 2000.0), DayEnergyRow(2, 2400.0), DayEnergyRow(3, 900.0))
        val statuses = foodDayStatuses(rows, NutritionGoals(kcal = 2100))
        assertEquals(DayStatus.COMPLETE, statuses[1])
        assertEquals(DayStatus.PARTIAL, statuses[2])
        assertEquals(DayStatus.PARTIAL, statuses[3])
        assertEquals(DayStatus.COMPLETE, foodDayStatuses(rows, NutritionGoals())[3])
    }

    @Test fun `meal codes are stable`() {
        assertEquals(listOf(0, 1, 2, 3, 4), Meal.entries.map { it.code })
        assertEquals(Meal.SUPPER, Meal.of(4))
    }
}
