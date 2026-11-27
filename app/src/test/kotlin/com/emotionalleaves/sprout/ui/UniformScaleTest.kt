package com.emotionalleaves.sprout.ui

import com.emotionalleaves.sprout.ui.common.uniformScaleFactor
import org.junit.Assert.assertEquals
import org.junit.Test

class UniformScaleTest {
    @Test fun scalesToTheDesignWidthWithinLimits() {
        assertEquals(1f, uniformScaleFactor(411), 0.001f)
        assertEquals(360 / 411f, uniformScaleFactor(360), 0.001f)
        assertEquals(0.85f, uniformScaleFactor(300), 0.001f)
        assertEquals(1.25f, uniformScaleFactor(800), 0.001f)
        assertEquals(1f, uniformScaleFactor(0), 0.001f)
    }
}
