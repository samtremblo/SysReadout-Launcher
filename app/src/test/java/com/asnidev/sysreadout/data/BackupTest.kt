package com.asnidev.sysreadout.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64

class BackupTest {

    // android.util.Base64 is a stub in unit tests; java.util's does the same job here.
    private val enc: (ByteArray) -> String = { Base64.getEncoder().encodeToString(it) }
    private val dec: (String) -> ByteArray = { Base64.getDecoder().decode(it) }

    @Test
    fun roundTripKeepsEveryTypeAndFont() {
        val b = Backup(
            settings = mapOf(
                "show_clock" to false,
                "log_interval" to 5,
                "big" to 1_099_511_627_776L,
                "ratio" to 0.5f,
                "banner" to "MY PHONE\n{date}",
                "hidden" to setOf("a/b#0", "c/d#10"),
            ),
            fonts = mapOf("Mine.ttf" to byteArrayOf(1, 2, 3)),
            appVersion = "0.2.5",
        )
        assertEquals(b, Backup.fromJson(b.toJson(enc), dec))
    }

    @Test
    fun otherFilesAreRefused() {
        for (text in listOf("not json", "{}", """{"format":"something-else"}""")) {
            try {
                Backup.fromJson(text, dec)
                fail("accepted $text")
            } catch (_: Backup.InvalidBackup) {
            }
        }
    }

    @Test
    fun aNewerFormatIsRefused() {
        try {
            Backup.fromJson("""{"format":"sysreadout-backup","version":99}""", dec)
            fail()
        } catch (e: Backup.InvalidBackup) {
            assertTrue(e.message!!.contains("newer"))
        }
    }

    @Test
    fun unknownTypesAndUnsafeFontNamesAreSkipped() {
        val text = """{"format":"sysreadout-backup","version":1,
            "settings":{"ok":{"t":"bool","v":true},"future":{"t":"blob","v":"x"}},
            "fonts":{"../../evil.ttf":"AQI=","notafont.sh":"AQI="}}"""
        val b = Backup.fromJson(text, dec)
        assertEquals(mapOf("ok" to true), b.settings)
        assertEquals(setOf("_.._evil.ttf"), b.fonts.keys)
    }

    @Test
    fun fontNames() {
        assertEquals("My_Font.otf", Backup.fontName("My Font.otf"))
        assertNull(Backup.fontName("..."))
        assertNull(Backup.fontName("x.exe"))
    }
}
