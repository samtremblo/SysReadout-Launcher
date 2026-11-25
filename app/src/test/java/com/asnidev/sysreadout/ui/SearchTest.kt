package com.asnidev.sysreadout.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchTest {

    @Test
    fun accentsAndCaseAreIgnored() {
        assertEquals("zoe dupre", Search.normalize("Zoé Dupré"))
        assertEquals(0, Search.rank("Zoé Dupré", "zoe"))
    }

    @Test
    fun prefixBeatsInitialsBeatsSubstring() {
        assertEquals(0, Search.rank("Google Maps", "goo"))
        assertEquals(1, Search.rank("Google Maps", "gm"))
        assertEquals(2, Search.rank("Google Maps", "maps"))
        assertNull(Search.rank("Google Maps", "xyz"))
    }

    @Test
    fun filterSortsByRankAndKeepsOrderWithinARank() {
        val names = listOf("Ann Mills", "Bob Amato", "Amy", "Anna")
        // "Amy" starts with it, "Ann Mills" has those initials, "Bob Amato" contains it.
        assertEquals(listOf("Amy", "Ann Mills", "Bob Amato"), Search.filter(names, "am") { it })
        assertEquals(listOf("Ann Mills", "Anna"), Search.filter(names, "ann") { it })
    }

    @Test
    fun emptyQueryKeepsEverything() {
        val names = listOf("b", "a")
        assertEquals(names, Search.filter(names, "") { it })
    }

    @Test
    fun historyOrdersWithinARankButNeverAcrossRanks() {
        val names = listOf("Maps", "Messages", "Gmail")
        val used = mapOf("Messages" to 5.0, "Gmail" to 50.0)
        // "Maps" and "Messages" both start with "m": the used one first. "Gmail" only contains it: still last.
        assertEquals(listOf("Messages", "Maps", "Gmail"), Search.filter(names, "m", { used[it] ?: 0.0 }) { it })
    }
}
