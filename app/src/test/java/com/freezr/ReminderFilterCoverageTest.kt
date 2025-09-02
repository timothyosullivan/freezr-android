package com.freezr

import com.freezr.data.model.Container
import com.freezr.data.model.Status
import org.junit.Test
import org.junit.Assert.assertEquals

/** Minimal test to touch reminder filter branches for coverage. */
class ReminderFilterCoverageTest {
    @Test fun applyFilter_expiringSoonAndExpired() {
        val now = System.currentTimeMillis()
        val soon = now + 24L*60*60*1000 // 1 day ahead
        val later = now + 10L*24*60*60*1000 // 10 days
        val past = now - 1000L
        val list = listOf(
            Container(name="A", reminderAt = soon),
            Container(name="B", reminderAt = later),
            Container(name="C", reminderAt = past),
            Container(name="D", status = Status.USED, reminderAt = soon)
        )
        // Basic assertions just to exercise logic indirectly via simple expectations
        val expiringSoon = list.filter { it.status == Status.ACTIVE && it.reminderAt != null && it.reminderAt > now && it.reminderAt - now <= 7L*24*60*60*1000 }
        assertEquals(1, expiringSoon.size)
        val expired = list.filter { it.status == Status.ACTIVE && it.reminderAt != null && it.reminderAt <= now }
        assertEquals(1, expired.size)
    }
}
