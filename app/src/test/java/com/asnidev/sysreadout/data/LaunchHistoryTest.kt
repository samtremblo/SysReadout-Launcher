package com.asnidev.sysreadout.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchHistoryTest {

    private val day = 24L * 60 * 60 * 1000

    @Test
    fun eachLaunchAddsOneAndOldOnesFade() {
        var h = LaunchHistory.bump(emptyMap(), "a", 0)
        h = LaunchHistory.bump(h, "a", 0)
        assertEquals(2.0, h.getValue("a").weightAt(0), 1e-9)
        assertEquals(1.0, h.getValue("a").weightAt(7 * day), 1e-9) // one half-life later
    }

    @Test
    fun recentUseBeatsOldHeavyUse() {
        var h = emptyMap<String, Usage>()
        repeat(10) { h = LaunchHistory.bump(h, "old", 0) }
        repeat(3) { h = LaunchHistory.bump(h, "new", 30 * day) }
        val now = 30 * day
        assertTrue(h.getValue("new").weightAt(now) > h.getValue("old").weightAt(now))
    }

    @Test
    fun theFaintestAreDroppedPastTheCap() {
        var h = emptyMap<String, Usage>()
        for (i in 0..LaunchHistory.MAX_ENTRIES) h = LaunchHistory.bump(h, "k$i", i * 1000L)
        assertEquals(LaunchHistory.MAX_ENTRIES, h.size)
        assertTrue("k0" !in h) // the oldest went
    }

    @Test
    fun jsonRoundTripAndGarbage() {
        val h = LaunchHistory.bump(emptyMap(), LaunchHistory.appKey(AppKey("p", "c", 0)), 123)
        assertEquals(h, LaunchHistory.fromJson(LaunchHistory.toJson(h)))
        assertEquals(emptyMap<String, Usage>(), LaunchHistory.fromJson("nope"))
        assertEquals(emptyMap<String, Usage>(), LaunchHistory.fromJson("""{"a":{"s":-1,"t":0}}"""))
    }
}
