package com.freezr

import com.freezr.data.model.Container
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals

class UuidGenerationTest {
    @Test fun newContainers_getDistinctUuids() {
        val a = Container(name = "A")
        val b = Container(name = "B")
        assertNotEquals(a.uuid, b.uuid)
    assertEquals(36, a.uuid.length)
    }
}
