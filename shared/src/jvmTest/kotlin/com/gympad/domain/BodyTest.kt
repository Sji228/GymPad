package com.gympad.domain

import com.gympad.data.Measurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyTest {
    private fun m(kind: BodyMeasure, day: Long, value: Double) = Measurement(day, kind.code, day, value)

    @Test fun `BMI follows every weight with the latest height`() {
        val all = listOf(
            m(BodyMeasure.WEIGHT, 1, 90.0),
            m(BodyMeasure.WEIGHT, 5, 81.0),
            m(BodyMeasure.HEIGHT, 1, 170.0),
            m(BodyMeasure.HEIGHT, 3, 180.0),
        )
        val points = bodyPoints(all, BodyMeasure.BMI)
        assertEquals(listOf(1L, 5L), points.map { it.epochDay })
        assertEquals(27.78, points[0].value, 0.01)
        assertEquals(25.0, points[1].value, 0.01)
        assertTrue(bodyPoints(listOf(m(BodyMeasure.WEIGHT, 1, 80.0)), BodyMeasure.BMI).isEmpty())
    }

    @Test fun `each measure charts only its own entries, oldest first`() {
        val all = listOf(m(BodyMeasure.WAIST, 9, 84.0), m(BodyMeasure.WAIST, 2, 86.5), m(BodyMeasure.NECK, 3, 38.0))
        assertEquals(listOf(86.5, 84.0), bodyPoints(all, BodyMeasure.WAIST).map { it.value })
    }

    @Test fun `typos are caught before they ruin a chart`() {
        assertTrue(BodyMeasure.WEIGHT.accepts(82.4))
        assertFalse(BodyMeasure.WEIGHT.accepts(824.0))
        assertFalse(BodyMeasure.BODY_FAT.accepts(150.0))
        assertFalse(BodyMeasure.BMI.accepts(24.0))
        assertEquals(11, BodyMeasure.chips.size)
    }
}
