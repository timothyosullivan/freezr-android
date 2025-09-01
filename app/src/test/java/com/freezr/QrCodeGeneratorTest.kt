package com.freezr

import com.freezr.label.QrCodeGenerator
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class QrCodeGeneratorTest {
    @Test fun generatesSquareBitmap() {
        val m = QrCodeGenerator.matrix("test-data", 64)
        assertEquals(64, m.size)
        var black = 0; var white = 0
        for (i in 0 until 64 step 8) {
            if (m.get(i,i)) black++ else white++
        }
        assertTrue(black > 0 && white > 0)
    }
}
