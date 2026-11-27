package com.asnidev.sysreadout.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class OledTest {
    @Test fun blackensOnlyTheBackground() {
        Presets.builtIn.forEach { p ->
            val t = p.theme.oled()
            assertEquals(p.name, Theme.OLED_BLACK, t.background)
            assertEquals(p.name, p.theme.copy(background = Theme.OLED_BLACK), t)
        }
    }

    @Test fun offLeavesTheThemeAlone() {
        val t = Presets.builtIn.first().theme
        assertSame(t, t.oled(on = false))
    }
}
