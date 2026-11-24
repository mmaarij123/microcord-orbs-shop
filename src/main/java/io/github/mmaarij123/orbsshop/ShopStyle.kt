package io.github.mmaarij123.orbsshop

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.discord.R as HostR
import com.discord.stores.StoreStream
import java.text.NumberFormat

internal class ShopStyle(context: Context) {
    private val dark = runCatching { StoreStream.getUserSettingsSystem().isDarkTheme() }.getOrElse {
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK !=
            Configuration.UI_MODE_NIGHT_NO
    }
    private val fallback = if (dark) 0xFF101114.toInt() else 0xFFF5F5F7.toInt()
    val ink = shopSurfaceColor(
        context.themeColour(HostR.attr.colorBackgroundMobilePrimary, Color.TRANSPARENT),
        context.themeColour(HostR.attr.colorBackgroundFloating, Color.TRANSPARENT),
        fallback,
    )
    val text = context.themeColour(HostR.attr.colorHeaderPrimary, if (dark) Color.WHITE else 0xFF202127.toInt())
    val muted = context.themeColour(HostR.attr.colorHeaderSecondary, blend(ink, text, 0.68f))
    val panel = blend(ink, text, 0.045f)
    val inset = blend(ink, text, 0.075f)
    val line = blend(ink, text, 0.17f)
    val accent = context.themeColour(HostR.attr.colorTextLink, if (dark) 0xFF65A8FF.toInt() else 0xFF2147A5.toInt())
    val action = context.themeColour(HostR.attr.color_brand, 0xFF5263DA.toInt()).let { colour ->
        if (Color.alpha(colour) == 255) colour else blend(ink, text, 0.26f)
    }
    val onAction = if (ColorUtils.calculateLuminance(action) > 0.32) 0xFF111318.toInt() else Color.WHITE
}

internal class ShopIcons(private val resources: Context) {
    fun drawable(id: Int, tint: Int) = requireNotNull(resources.getDrawable(id)).mutate().apply {
        setTint(tint)
    }

    fun button(ui: Context, id: Int, description: String, tint: Int, click: (() -> Unit)? = null) =
        ImageButton(ui).apply {
            setImageDrawable(drawable(id, tint))
            contentDescription = description
            background = ui.pressable(Color.TRANSPARENT, 12)
            scaleType = android.widget.ImageView.ScaleType.CENTER
            minimumWidth = ui.dp(48)
            minimumHeight = ui.dp(48)
            click?.let { setOnClickListener { _ -> it() } }
        }
}

private fun Context.themeColour(attr: Int, fallback: Int): Int {
    val value = TypedValue()
    if (!theme.resolveAttribute(attr, value, true)) return fallback
    return when {
        value.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT -> value.data
        value.resourceId != 0 -> runCatching { getColor(value.resourceId) }.getOrDefault(fallback)
        else -> fallback
    }
}

// Gradient themes can leave the page surface clear so the host wallpaper shows through.
internal fun shopSurfaceColor(page: Int, floating: Int, fallback: Int): Int = when {
    page ushr 24 == 255 -> page
    floating ushr 24 == 255 -> floating
    else -> fallback
}

internal fun blend(from: Int, to: Int, amount: Float) =
    ColorUtils.blendARGB(from, ColorUtils.compositeColors(to, from), amount)
internal fun Context.dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

internal fun Context.shape(fill: Int, radius: Int = 14, stroke: Int? = null) = GradientDrawable().apply {
    setColor(fill)
    cornerRadius = dp(radius).toFloat()
    stroke?.let { setStroke(dp(1), it) }
}

internal fun Context.pressable(fill: Int, radius: Int, stroke: Int? = null) = RippleDrawable(
    ColorStateList.valueOf(0x40888DAA),
    StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), shape(fill, radius, ShopStyle(this@pressable).accent))
        addState(intArrayOf(), shape(fill, radius, stroke))
    },
    shape(Color.WHITE, radius),
)

private fun Context.shopTypeface(bold: Boolean): Typeface {
    val value = TypedValue()
    val attr = if (bold) HostR.attr.font_primary_semibold else HostR.attr.font_primary_normal
    if (theme.resolveAttribute(attr, value, true) && value.resourceId != 0) {
        runCatching { ResourcesCompat.getFont(this, value.resourceId) }.getOrNull()?.let { return it }
    }
    return Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
}

internal fun Context.label(value: String, size: Int, colour: Int, bold: Boolean = false) =
    TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(colour)
        fontFeatureSettings = "'tnum'"
        typeface = shopTypeface(bold)
        includeFontPadding = false
    }

internal fun Context.button(label: String, style: ShopStyle, primary: Boolean = false, click: () -> Unit) =
    Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        typeface = shopTypeface(true)
        gravity = Gravity.CENTER
        setTextColor(if (primary) style.onAction else style.text)
        minHeight = dp(48)
        minimumHeight = dp(48)
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(12), dp(6), dp(12), dp(6))
        background = pressable(if (primary) style.action else style.panel, 13, if (primary) null else style.line)
        setOnClickListener { click() }
    }

internal fun TextView.orbIcon(icons: ShopIcons, colour: Int, size: Int = 15) {
    val icon = icons.drawable(R.drawable.ic_discord_orbs, colour)
    icon.setBounds(0, 0, context.dp(size), context.dp(size))
    setCompoundDrawablesRelative(icon, null, null, null)
    compoundDrawablePadding = context.dp(6)
}

internal fun Context.column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
internal fun Context.row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
internal fun View.pad(horizontal: Int, vertical: Int) =
    setPadding(context.dp(horizontal), context.dp(vertical), context.dp(horizontal), context.dp(vertical))

internal fun LinearLayout.addLine(view: View, space: Int = 0, height: Int = -2) {
    addView(view, LinearLayout.LayoutParams(-1, if (height < 0) height else context.dp(height)).apply {
        topMargin = context.dp(space)
    })
}

internal fun FrameLayout.place(
    view: View, width: Int, height: Int, at: Int, left: Int = 0, top: Int = 0, right: Int = 0,
) {
    val context = context
    addView(view, FrameLayout.LayoutParams(
        if (width < 0) width else context.dp(width),
        if (height < 0) height else context.dp(height),
        at,
    ).apply {
        marginStart = context.dp(left)
        topMargin = context.dp(top)
        marginEnd = context.dp(right)
    })
}

internal fun orbText(amount: Int) = "${NumberFormat.getIntegerInstance().format(amount)} Orbs"
