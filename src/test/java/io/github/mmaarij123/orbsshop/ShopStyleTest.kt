package io.github.mmaarij123.orbsshop

import org.junit.Assert.assertEquals
import org.junit.Test

class ShopStyleTest {
    @Test fun keepsOpaquePageColorForSolidThemes() {
        val page = 0xFF232428.toInt()
        assertEquals(page, shopSurfaceColor(page, 0xFF303136.toInt(), 0xFF101114.toInt()))
    }

    @Test fun usesFloatingColorWhenGradientThemeClearsPageSurfaces() {
        val floating = 0xFF341A22.toInt()
        assertEquals(floating, shopSurfaceColor(0x00000000, floating, 0xFF101114.toInt()))
    }

    @Test fun usesLightThemeFallbackWhenBothSurfacesAreTranslucent() {
        val fallback = 0xFFF5F5F7.toInt()
        assertEquals(fallback, shopSurfaceColor(0x20302010, 0x80303030.toInt(), fallback))
    }
}
