package dev.mantikafasi.manticord.orbsshop

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.discord.utilities.images.CollectibleAssets
import com.linecorp.apng.ApngDrawable
import java.io.IOException
import java.io.InputStream
import kotlin.math.roundToInt
import kotlin.random.Random
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response

internal class EffectOverlay(
    context: Context,
    private val effect: EffectSpec,
    private val reduceMotion: Boolean,
) : FrameLayout(context) {
    private class Playback(val view: ImageView, val drawable: ApngDrawable, val layer: EffectLayer)

    private val main = Handler(Looper.getMainLooper())
    private val delivery = Handler(Looper.getMainLooper())
    private val calls = mutableSetOf<Call>()
    private val layers = mutableListOf<Playback>()
    private var generation = 0
    private var playing = false
    private var startedAt = 0L
    private var unfinished = 0
    private var playedLayer = false

    init {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isClickable = false
        isFocusable = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startPreview()
    }

    override fun onDetachedFromWindow() {
        clear()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE && isAttachedToWindow) startPreview()
        else if (visibility != VISIBLE && playing) clear()
    }

    fun replay() {
        clear()
        startPreview()
    }

    private fun startPreview() {
        if (playing || !isAttachedToWindow || windowVisibility != VISIBLE) return
        playing = true
        startedAt = SystemClock.uptimeMillis() + 160
        playedLayer = false
        val version = generation
        if (reduceMotion || effect.layers.isEmpty()) {
            showStill(version)
            return
        }
        val choices = effect.layers.map { it.alternatives.size }.filter { it > 0 }.minOrNull() ?: 0
        val alternative = if (choices > 0) Random.nextInt(choices) else 0
        unfinished = effect.layers.size
        effect.layers.forEach { layer ->
            val source = layer.alternatives.getOrNull(alternative) ?: layer.source
            // Defer decoding later layers to keep full-size APNGs off the heap until needed.
            main.postDelayed({
                fetchLayer(source, layer, version)
            }, (layer.start - DECODE_LEAD_MS).coerceAtLeast(0))
        }
    }

    private fun fetchLayer(url: String, layer: EffectLayer, version: Int) {
        fetch(url, decode = { ApngDrawable.decode(it.buffered()) }) { drawable ->
            if (version != generation) {
                drawable?.recycle()
                return@fetch
            }
            unfinished--
            if (drawable != null) {
                playedLayer = true
                val image = ImageView(context).apply {
                    scaleType = if (layer.fillsCanvas) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.FIT_XY
                    visibility = INVISIBLE
                    translationZ = layer.z.toFloat()
                    setImageDrawable(drawable)
                }
                val playback = Playback(image, drawable, layer)
                layers += playback
                addView(image, LayoutParams(-1, -1))
                position(image, layer)
                val remaining = (startedAt + layer.start - SystemClock.uptimeMillis()).coerceAtLeast(0)
                main.postDelayed({ play(playback, layer) }, remaining)
            }
            if (unfinished == 0 && !playedLayer) showStill(version)
        }
    }

    private fun play(playback: Playback, layer: EffectLayer) {
        val image = playback.view
        val drawable = playback.drawable
        drawable.seekTo(0)
        val duration = layer.duration.takeIf { it > 0 } ?: drawable.durationMillis.toLong().coerceAtLeast(1)
        drawable.loopCount = 1
        image.visibility = VISIBLE
        drawable.start()
        main.postDelayed({
            drawable.stop()
            image.visibility = INVISIBLE
            if (layer.looping) main.postDelayed({ play(playback, layer) }, layer.delay)
            else remove(playback)
        }, duration)
    }

    override fun onSizeChanged(width: Int, height: Int, previousWidth: Int, previousHeight: Int) {
        super.onSizeChanged(width, height, previousWidth, previousHeight)
        layers.forEach { playback ->
            position(playback.view, playback.layer)
        }
    }

    private fun position(view: ImageView, layer: EffectLayer) {
        if (layer.fillsCanvas || width == 0) return
        val scale = width.toDouble() / EffectSpec.CANVAS_WIDTH
        view.layoutParams = LayoutParams(
            (requireNotNull(layer.width) * scale).roundToInt().coerceAtLeast(1),
            (requireNotNull(layer.height) * scale).roundToInt().coerceAtLeast(1),
        ).apply {
            leftMargin = (layer.x * scale).roundToInt()
            topMargin = (layer.y * scale).roundToInt()
        }
    }

    private fun showStill(version: Int) {
        val still = effect.still ?: return
        fetch(still, decode = { BitmapFactory.decodeStream(it) }) { bitmap ->
            if (version != generation) {
                bitmap?.recycle()
                return@fetch
            }
            if (bitmap != null) {
                addView(ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageBitmap(bitmap)
                }, LayoutParams(-1, -1))
            }
        }
    }

    private fun <T> fetch(url: String, decode: (InputStream) -> T?, deliver: (T?) -> Unit) {
        if (!isDiscordArtwork(url)) {
            deliver(null)
            return
        }
        val request = Request.Builder().url(url).build()
        val call = CollectibleAssets.client.newCall(request)
        calls += call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = complete(call, null)
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use { if (it.isSuccessful) it.body?.byteStream()?.let(decode) else null }
                }.getOrNull()
                complete(call, result)
            }

            private fun complete(call: Call, result: T?) {
                delivery.post {
                    calls.remove(call)
                    deliver(result)
                }
            }
        })
    }

    private fun remove(playback: Playback) {
        removeView(playback.view)
        playback.view.setImageDrawable(null)
        playback.drawable.stop()
        playback.drawable.recycle()
        layers.remove(playback)
    }

    private fun clear() {
        generation++
        playing = false
        main.removeCallbacksAndMessages(null)
        calls.forEach(Call::cancel)
        calls.clear()
        layers.toList().forEach(::remove)
        removeAllViews()
    }

    private companion object {
        const val DECODE_LEAD_MS = 650L
    }
}
