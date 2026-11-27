package com.asnidev.sysreadout.ui

import com.asnidev.sysreadout.ui.Search.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {

    private fun tier(label: String, q: String, number: String? = null) =
        Search.match(Searchable(label, number), Search.normalize(q))

    @Test
    fun accentsAndCaseAreIgnored() {
        assertEquals("zoe dupre", Search.normalize("Zoé Dupré"))
        assertEquals(Tier.PREFIX, tier("Zoé Dupré", "ZOE"))
    }

    @Test
    fun wordsSplitAtSpacesPunctuationAndCamelCase() {
        assertEquals(listOf("whats", "app", "business"), Search.words("WhatsApp Business"))
        assertEquals(listOf("you", "tube"), Search.words("YouTube"))
        assertEquals(listOf("k", "9", "mail"), Search.words("K-9 Mail"))
    }

    @Test
    fun tiers() {
        assertEquals(Tier.EXACT, tier("Maps", "maps"))
        assertEquals(Tier.EXACT, tier("WhatsApp", "whats app"))
        assertEquals(Tier.PREFIX, tier("Instagram", "ins"))
        assertEquals(Tier.PREFIX, tier("WhatsApp", "whats ap"))
        assertEquals(Tier.WORD_PREFIX, tier("Google Maps", "maps"))
        assertEquals(Tier.INITIALS, tier("Google Maps", "gm"))
        assertEquals(Tier.INITIALS, tier("WhatsApp", "wa"))
        assertEquals(Tier.INITIALS, tier("YouTube", "yt"))
        assertEquals(Tier.WORDS, tier("Google Play Store", "goo st"))
        assertEquals(Tier.SUBSTRING, tier("Instagram", "gram"))
        assertEquals(Tier.SUBSEQUENCE, tier("YouTube", "ytb"))
        assertEquals(Tier.SUBSEQUENCE, tier("Instagram", "instgram")) // a missed letter: still in order
        assertEquals(Tier.TYPO, tier("Instagram", "instagrem")) // a wrong letter
        assertEquals(Tier.TYPO, tier("Instagram", "isntagram")) // swapped letters
        assertEquals(Tier.TYPO, tier("Firefox", "firrfox")) // an extra letter
        assertNull(tier("Google Maps", "xyz"))
    }

    @Test
    fun wordsMustComeInOrder() {
        assertNull(tier("Google Play Store", "st goo"))
    }

    @Test
    fun contactsMatchByNumber() {
        assertEquals(Tier.NUMBER, tier("Ann Mills", "555-12", "+1 (514) 555-1234"))
        assertNull(tier("Ann Mills", "99", "+1 (514) 555-1234")) // too few digits to mean a number
        assertNull(tier("Ann Mills", "777", "+1 (514) 555-1234"))
    }

    @Test
    fun fuzzyNeedsEnoughLetters() {
        assertNull(tier("YouTube", "yb")) // 2 letters: no subsequence
        assertEquals(Tier.SUBSEQUENCE, tier("Maps", "mps")) // 3 letters in order from a word start
        assertNull(tier("Maps", "xap")) // 3 letters: too short for typos
        assertEquals(Tier.TYPO, tier("Maps", "mpas")) // 4 letters: a swap is forgiven
    }

    @Test
    fun orderIsTierThenHistoryThenShorterName() {
        val names = listOf("Chrome Beta", "Chrome", "Bluetooth Chrome", "Calculator")
        assertEquals(listOf("Chrome", "Chrome Beta", "Bluetooth Chrome"), Search.filter(names, "chrome") { it })
        val used = mapOf("Chrome Beta" to 9.0)
        assertEquals(listOf("Chrome", "Chrome Beta", "Bluetooth Chrome"), Search.filter(names, "chrome", { used[it] ?: 0.0 }) { it })
        assertEquals(listOf("Chrome Beta", "Chrome", "Bluetooth Chrome"), Search.filter(names, "chr", { used[it] ?: 0.0 }) { it })
    }

    @Test
    fun fuzzyOnlyWhenNothingMatchesProperly() {
        val names = listOf("Instagram", "Messages")
        val proper = Search.find(names, "mess") { Searchable(it) }
        assertEquals(listOf("Messages"), proper.items)
        assertFalse(proper.fuzzy)
        val guess = Search.find(names, "instgram") { Searchable(it) }
        assertEquals(listOf("Instagram"), guess.items)
        assertTrue(guess.fuzzy)
    }

    @Test
    fun emptyQueryKeepsEverything() {
        val names = listOf("b", "a")
        assertEquals(names, Search.filter(names, "") { it })
    }

    @Test
    fun distanceCountsASwapAsOne() {
        assertEquals(1, Search.distance("teh", "the"))
        assertEquals(1, Search.distance("firfox", "firefox"))
        assertEquals(2, Search.distance("abcd", "badc"))
    }

    @Test
    fun completionIsTheRestOfAPrefixMatch() {
        assertEquals("tagram", Search.completion("Instagram", "ins"))
        assertEquals("tagram", Search.completion("Instagram", "INS"))
        assertEquals(" Dupré", Search.completion("Zoé Dupré", "zoe"))
        assertEquals("", Search.completion("Maps", "maps"))
        assertNull(Search.completion("Google Maps", "gm"))
        assertNull(Search.completion("Maps", "mapss"))
        assertNull(Search.completion("Maps", ""))
    }
}
