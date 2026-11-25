package com.asnidev.sysreadout.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GestureTest {

    private val key = AppKey("org.example", "org.example.Main", 0)

    @Test
    fun anAppSavedBeforeGesturesStillDecodes() {
        assertEquals(Gesture.app(key), Gesture.decode(key.encode()))
    }

    @Test
    fun anAppIsSavedTheOldWaySoOlderVersionsCanReadIt() {
        assertEquals(key.encode(), Gesture.app(key).encode())
    }

    @Test
    fun everyActionRoundTrips() {
        GestureKind.entries.filter { it != GestureKind.APP && it != GestureKind.NONE }.forEach {
            assertEquals(Gesture(it), Gesture.decode(Gesture(it).encode()))
        }
    }

    @Test
    fun nothingAndGarbageAreNone() {
        assertNull(Gesture.NONE.encode())
        assertEquals(Gesture.NONE, Gesture.decode(null))
        assertEquals(Gesture.NONE, Gesture.decode("action:TELEPORT"))
        assertEquals(Gesture.NONE, Gesture.decode("action:APP"))
        assertEquals(Gesture.NONE, Gesture.decode("garbage"))
    }
}
