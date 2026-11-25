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
}
