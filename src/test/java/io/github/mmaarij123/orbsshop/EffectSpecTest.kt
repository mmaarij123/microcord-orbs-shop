package io.github.mmaarij123.orbsshop

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectSpecTest {
    @Test fun tallModernEffectFitsInsideACompleteProfile() {
        val effect = parse(
            """{"src":"intro","duration":4981,"start":0,"zIndex":110},
            {"src":"loop","duration":4980,"start":7981,"zIndex":100,"loop":true,"loopDelay":3000}""",
        )
        assertEquals(595, effect.canvasHeight)
        assertEquals(EffectSize(235, 460), effect.fit(320, 460))
        assertTrue(effect.layers.all { it.fillsCanvas })
        assertFalse(effect.layers.first().looping)
        assertTrue(effect.layers.last().looping)
        assertEquals(7981L, effect.layers.last().start)
        assertEquals(3000L, effect.layers.last().delay)
    }

    @Test fun positionedLegacyLayerExtendsTheProfileCanvas() {
        val effect = parse(
            """{"src":"positioned","position":{"x":-3,"y":190},"width":304,"height":260,
                "zIndex":12,"randomizedSources":[{"src":"alt-a"},{"src":"alt-b"}]}""",
        )
        assertEquals(450, effect.canvasHeight)
        assertEquals(EffectSize(304, 450), effect.fit(304, 520))
        val positioned = effect.layers.single()
        assertFalse(positioned.fillsCanvas)
        assertEquals(-3, positioned.x)
        assertEquals(190, positioned.y)
        assertEquals(listOf("alt-a", "alt-b"), positioned.alternatives)
        assertEquals("still", effect.still)
    }

    private fun parse(layers: String) = EffectSpec.fromProduct(
        JsonParser.parseString(
            """{"items":[{"type":1,"reducedMotionSrc":"still","effects":[$layers]}]}""",
        ).asJsonObject,
    )
}
