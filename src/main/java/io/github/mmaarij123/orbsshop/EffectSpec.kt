package io.github.mmaarij123.orbsshop

import com.google.gson.JsonObject
import kotlin.math.ceil
import kotlin.math.roundToInt

internal data class EffectLayer(
    val source: String,
    val z: Int,
    val start: Long,
    val duration: Long,
    val looping: Boolean,
    val delay: Long,
    val x: Int,
    val y: Int,
    val width: Int?,
    val height: Int?,
    val alternatives: List<String>,
) {
    val fillsCanvas get() = width == null || height == null
}

internal data class EffectSpec(val still: String?, val layers: List<EffectLayer>) {
    val canvasHeight: Int
        get() {
            val tallest = layers.maxOfOrNull { it.y + (it.height ?: 0) } ?: 0
            val standard = if (layers.isEmpty() || layers.any { it.fillsCanvas }) TALL_CANVAS else 432
            return maxOf(standard, tallest)
        }

    fun fit(availableWidth: Int, availableHeight: Int): EffectSize {
        val width = minOf(availableWidth, availableHeight * CANVAS_WIDTH / canvasHeight).coerceAtLeast(1)
        return EffectSize(width, (width.toDouble() * canvasHeight / CANVAS_WIDTH).roundToInt().coerceAtLeast(1))
    }

    companion object {
        const val CANVAS_WIDTH = 304
        // Genuine modern full-profile APNG canvases (including Odette) use 450 × 880.
        private val TALL_CANVAS = ceil(CANVAS_WIDTH * 880.0 / 450).toInt()

        fun fromProduct(product: JsonObject): EffectSpec {
            val item = product.array("items").objects().firstOrNull { it.int("type") == CosmeticKind.EFFECT.wireType }
            val still = item?.string("staticFrameSrc") ?: item?.string("reducedMotionSrc")
                ?: item?.string("static_frame_src") ?: item?.string("reduced_motion_src")
                ?: item?.string("thumbnailPreviewSrc") ?: item?.string("thumbnail_preview_src")
            val layers = item?.array("effects").objects().mapNotNull { layer ->
                val source = layer.string("src") ?: return@mapNotNull null
                val position = layer.obj("position")
                EffectLayer(
                    source = source,
                    z = layer.int("zIndex") ?: layer.int("z_index") ?: 0,
                    start = layer.millis("start"),
                    duration = layer.millis("duration"),
                    looping = layer.string("loop") == "true",
                    delay = layer.millis("loopDelay", "loop_delay"),
                    x = position?.int("x") ?: 0,
                    y = position?.int("y") ?: 0,
                    width = layer.int("width")?.takeIf { it > 0 },
                    height = layer.int("height")?.takeIf { it > 0 },
                    alternatives = (layer.array("randomizedSources") ?: layer.array("randomized_sources"))
                        .objects().mapNotNull { it.string("src") },
                )
            }
            return EffectSpec(still, layers)
        }

        private fun JsonObject.millis(key: String, alternate: String = key): Long =
            (string(key) ?: string(alternate))?.toLongOrNull()?.coerceIn(0, 120_000) ?: 0
    }
}

internal data class EffectSize(val width: Int, val height: Int)
