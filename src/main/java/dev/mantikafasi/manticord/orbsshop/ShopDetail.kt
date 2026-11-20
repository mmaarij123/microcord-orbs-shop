package dev.mantikafasi.manticord.orbsshop

import androidx.appcompat.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.discord.api.collectibles.CollectibleProduct
import com.discord.models.user.MeUser
import com.discord.stores.StoreStream
import com.discord.utilities.icon.IconUtils
import com.discord.views.NameplateView
import com.discord.views.VitoImageView
import com.discord.widgets.user.profile.ProfileFrameView
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import java.net.URI

internal class ShopDetail(
    private val ui: Context,
    private val family: ProductFamily,
    initial: ShopState,
    private val session: ShopSession,
    private val icons: ShopIcons,
) {
    private val style = ShopStyle(ui)
    private val dialog = Dialog(ui)
    private var confirmation: AlertDialog? = null
    private val me = StoreStream.getUsers().getMe()
    private val heading = ui.label(family.name, 24, style.text, bold = true)
    private val summary = ui.label("", 14, style.muted)
    private val stage = FrameLayout(ui)
    private var effectPreview: FullEffectProfile? = null
    private val feedback = ui.label("", 13, style.muted).apply {
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val confirm = ui.button("", style, primary = true) { confirmRedemption() }
    private val choices = linkedMapOf<Offer, TextView>()
    private var selected = family.variants.first()
    private var state = initial

    init {
        val content = ui.column().apply {
            addLine(ui.row().apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(ui.label("Preview on your profile", 13, style.muted, true),
                    LinearLayout.LayoutParams(0, -2, 1f))
                if (family.kind == CosmeticKind.EFFECT && !StoreStream.getAccessibility().isReducedMotionEnabled()) {
                    addView(ui.button("Replay", style) { effectPreview?.replay() },
                        LinearLayout.LayoutParams(-2, -2))
                }
            }, 8)
            val previewHeight = if (family.kind == CosmeticKind.EFFECT) {
                (ui.resources.configuration.screenHeightDp - 248).coerceIn(240, 570)
            } else 300
            addLine(stage, 10, previewHeight)
            addLine(summary, 12)
            if (family.variants.size > 1) {
                addLine(ui.label("Colour", 15, style.text, true), 16)
                addLine(colourPicker(), 8)
            }
            setPadding(ui.dp(16), 0, ui.dp(16), ui.dp(12))
        }
        dialog.setContentView(ui.column().apply {
            background = ui.shape(style.ink, 24, style.line)
            addLine(ui.row().apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ui.dp(18), ui.dp(10), ui.dp(8), ui.dp(6))
                val copy = ui.column().apply {
                    addLine(ui.label(family.kind.singular, 12, style.muted, true))
                    addLine(heading, 4)
                }
                addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
                addView(icons.button(ui, R.drawable.ic_shop_close, "Close preview", style.text) { dismiss() },
                    LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
            })
            addView(ScrollView(ui).apply {
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                addView(content, -1, -2)
            }, LinearLayout.LayoutParams(-1, 0, 1f))
            addLine(ui.column().apply {
                pad(16, 12)
                background = ui.shape(style.panel, 18)
                addLine(confirm)
                addLine(feedback, 8)
            })
        })
    }

    fun show(onDismiss: () -> Unit) {
        dialog.setOnDismissListener {
            confirmation?.dismiss()
            onDismiss()
        }
        showVariant()
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = ui.resources.displayMetrics.widthPixels - ui.dp(20)
            val height = ui.resources.displayMetrics.heightPixels - ui.dp(84)
            val maxHeight = if (family.kind == CosmeticKind.EFFECT) 750 else 610
            setLayout(minOf(width, ui.dp(510)), minOf(height, ui.dp(maxHeight)))
        }
    }

    fun dismiss() = dialog.dismiss()

    fun update(newState: ShopState) {
        state = newState
        val current = state.products.orEmpty().asSequence().flatMap { it.variants.asSequence() }
            .firstOrNull { it.sku == selected.sku }
        val owned = state.owned?.contains(selected.sku) == true
        val eligible = current?.orbs == selected.orbs
        val disputed = selected.sku in state.unconfirmed
        val balance = state.balance
        confirm.text = when {
            state.busy -> "Checking with Discord…"
            owned -> "In your collection"
            disputed -> "Redemption unconfirmed"
            !eligible -> "Offer changed"
            balance == null || state.owned == null -> "Orbs checkout unavailable"
            balance < selected.orbs -> "Not enough Orbs"
            else -> "Redeem · ${orbText(selected.orbs)}"
        }
        confirm.isEnabled = !state.busy && !owned && !disputed && eligible && state.owned != null &&
            balance != null && balance >= selected.orbs
        confirm.alpha = if (confirm.isEnabled) 1f else 0.62f
        if (confirm.isEnabled) confirm.orbIcon(icons, style.onAction)
        else confirm.setCompoundDrawablesRelative(null, null, null, null)
        val message = state.notice ?: when {
            disputed -> "Check your Discord collection and balance before trying again."
            !eligible -> "Close this preview to see the current offer."
            balance == null || state.owned == null -> "Close this preview and refresh your balance and collection."
            else -> ""
        }
        feedback.text = message
        feedback.visibility = if (message.isBlank()) View.GONE else View.VISIBLE
    }

    private fun colourPicker(): View = HorizontalScrollView(ui).apply {
        isHorizontalScrollBarEnabled = false
        addView(ui.row().apply {
            family.variants.forEachIndexed { index, offer ->
                val label = offer.colour?.takeIf(String::isNotBlank) ?: offer.name
                val choice = ui.button(label, style) { selected = offer; showVariant() }
                val dot = ui.shape(offer.tint(style.accent), 10, style.line).apply {
                    setBounds(0, 0, ui.dp(16), ui.dp(16))
                }
                choice.setCompoundDrawablesRelative(dot, null, null, null)
                choice.compoundDrawablePadding = ui.dp(7)
                addView(choice, LinearLayout.LayoutParams(-2, -2).apply {
                    if (index > 0) marginStart = ui.dp(6)
                })
                choices[offer] = choice
            }
        })
    }

    private fun showVariant() {
        summary.text = selected.summary
        summary.visibility = if (selected.summary.isBlank()) View.GONE else View.VISIBLE
        choices.forEach { (offer, choice) ->
            val active = offer == selected
            choice.isSelected = active
            choice.background = ui.pressable(
                if (active) blend(style.ink, style.accent, 0.13f) else style.panel,
                12,
                if (active) style.accent else style.line,
            )
            choice.setTextColor(if (active) style.accent else style.text)
        }
        stage.removeAllViews()
        val preview = if (family.kind == CosmeticKind.EFFECT) {
            FullEffectProfile(ui, style, me, selected).also { effectPreview = it }
        } else {
            profilePreview(ui, style, me, selected) { requiredHeight ->
                val height = stage.layoutParams
                if (height.height != requiredHeight) {
                    height.height = requiredHeight
                    stage.layoutParams = height
                }
            }
        }
        stage.addView(preview, -1, -1)
        update(state)
    }

    private fun confirmRedemption() {
        val offer = selected
        val name = offer.colour?.let { "${family.name} · $it" } ?: offer.name
        confirmation = AlertDialog.Builder(ui)
            .setTitle("Redeem $name?")
            .setMessage("Spend ${orbText(offer.orbs)} from your Discord balance.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Redeem") { _, _ -> session.redeem(me.id, offer) }
            .show()
    }
}

private val collectibleJson = GsonBuilder()
    .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
    .create()

internal fun productArt(ui: Context, style: ShopStyle, offer: Offer): View = FrameLayout(ui).apply {
    background = ui.artBackground(style, offer, 0)
    val product = offer.model()
    val art = when (offer.kind) {
        CosmeticKind.AVATAR -> product?.avatarDecoration()?.imageUrls(animate = false)?.firstOrNull()
        CosmeticKind.EFFECT -> product?.profileEffectThumbnail()
        CosmeticKind.NAMEPLATE -> product?.nameplate()?.staticImageUrl()
        CosmeticKind.FRAME -> product?.profileFrame()?.topStaples()?.firstOrNull()?.assetUrl(offer.sku)
    } ?: offer.product.obj("preview_assets")?.string("fg_static")
    place(ui.artwork(art), if (offer.kind == CosmeticKind.AVATAR) 96 else -1, -1, Gravity.CENTER)
}

private fun profilePreview(
    ui: Context,
    style: ShopStyle,
    me: MeUser,
    offer: Offer,
    resize: (Int) -> Unit,
): View = FrameLayout(ui).apply {
    background = ui.artBackground(style, offer, 18)
    clipToOutline = true
    val product = offer.model()
    val decoration = if (offer.kind == CosmeticKind.AVATAR) product?.avatarDecoration() else me.avatarDecoration
    val frame = product?.profileFrame()
    val back = ProfileFrameView(ui).apply { half = ProfileFrameView.Half.BACK }
    val front = ProfileFrameView(ui).apply { half = ProfileFrameView.Half.FRONT }
    val avatar = cosmeticAvatar(ui, style, me, decoration?.imageUrls(animate = false)?.firstOrNull(), 66)
    val profile = FrameLayout(ui).apply {
        background = ui.shape(style.ink, 14, style.line)
        clipToOutline = true
        val banner = ui.artwork(me.banner?.let { IconUtils.getForUserBanner(me.id, it, 480, false) }).apply {
            background = ColorDrawable(blend(style.panel, offer.tint(style.accent), 0.5f))
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        }
        place(banner, -1, 76, Gravity.TOP)
        place(avatar, 88, 88, Gravity.TOP or Gravity.START, left = 11, top = 37)
        val profileName = me.globalName?.takeIf(String::isNotBlank) ?: me.username
        val profileText = ui.column().apply {
            addLine(ui.label(profileName, 20, style.text, bold = true))
            addLine(ui.label("@${me.username}", 13, style.muted), 2)
        }
        place(profileText, -1, -2, Gravity.TOP or Gravity.START, left = 22, top = 124, right = 12)
        if (offer.kind == CosmeticKind.AVATAR && !me.bio.isNullOrBlank()) {
            place(ui.label(me.bio.orEmpty(), 13, style.muted).apply {
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, -1, -2, Gravity.TOP, left = 22, top = 195, right = 16)
        }
        if (offer.kind == CosmeticKind.NAMEPLATE) {
            val plate = FrameLayout(ui).apply {
                background = ui.shape(style.panel, 8)
                place(NameplateView(ui).apply {
                    bind(product?.nameplate(), NameplateView.Placement.MEMBER_LIST, selected = true)
                }, -1, -1, Gravity.CENTER)
                place(ui.label(profileName, 14, style.text, bold = true), -2, -2, Gravity.CENTER_VERTICAL or Gravity.START, left = 10)
            }
            place(plate, -1, 42, Gravity.TOP, left = 18, top = 195, right = 18)
        }
    }
    place(back, -1, -1, Gravity.FILL)
    place(profile, -1, 244, Gravity.TOP, left = 20, top = 30, right = 20)
    place(front, -1, -1, Gravity.FILL)
    if (frame != null) {
        addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            val available = right - left
            if (available <= 0 || available == oldRight - oldLeft || frame.innerWidth <= 0) {
                return@addOnLayoutChangeListener
            }
            val horizontalOverflow = frame.overflowHorizontal.coerceAtLeast(0)
            val scaledMargin = horizontalOverflow.toFloat() / (frame.innerWidth + horizontalOverflow * 2)
            val margin = maxOf(ui.dp(20), (available * scaledMargin).toInt() + ui.dp(4))
            val scale = (available - 2 * margin).coerceAtLeast(0).toFloat() / frame.innerWidth
            val top = maxOf(ui.dp(30), (frame.overflowTop * scale).toInt() + ui.dp(8))
            val bottom = maxOf(ui.dp(18), (frame.overflowBottom * scale).toInt() + ui.dp(8))
            post {
                if (isAttachedToWindow) {
                    profile.layoutParams = (profile.layoutParams as FrameLayout.LayoutParams).apply {
                        leftMargin = margin
                        rightMargin = margin
                        topMargin = top
                    }
                    resize(top + ui.dp(244) + bottom)
                }
            }
        }
        profile.addOnLayoutChangeListener { _, left, top, right, _, _, _, _, _ ->
            back.anchorTo(left, top, right - left)
            front.anchorTo(left, top, right - left)
        }
        back.bind(frame)
        front.bind(frame)
    }

}

private fun Offer.model() = runCatching {
    collectibleJson.fromJson(product, CollectibleProduct::class.java)
}.getOrNull()

internal fun Context.artwork(url: String?) = VitoImageView(this).apply {
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
    if (url != null && isDiscordArtwork(url)) setImageURI(url)
}

internal fun isDiscordArtwork(value: String): Boolean {
    val url = runCatching { URI(value) }.getOrNull() ?: return false
    return url.scheme == "https" && url.host in setOf("cdn.discordapp.com", "media.discordapp.net", "cdn.discordapp.net")
}

internal fun Offer.tint(fallback: Int): Int {
    val colour = swatch?.let {
        runCatching { Color.parseColor(if (it.startsWith("#")) it else "#$it") }.getOrNull()
    }
    val artColour = product.obj("styles")?.array("background_colors")
        ?.firstOrNull()?.takeIf { it.isJsonPrimitive }?.asString?.toIntOrNull()
    return colour ?: artColour?.let { it or 0xFF000000.toInt() } ?: fallback
}

internal fun Context.artBackground(style: ShopStyle, offer: Offer, radius: Int) = GradientDrawable(
    GradientDrawable.Orientation.TL_BR,
    intArrayOf(
        blend(style.ink, offer.tint(style.accent), 0.22f),
        blend(style.ink, offer.tint(style.accent), 0.06f),
    ),
).apply { cornerRadius = dp(radius).toFloat() }

internal fun avatarShowcase(ui: Context, style: ShopStyle, me: MeUser, offer: Offer): View =
    FrameLayout(ui).apply {
        val tint = offer.tint(style.accent)
        place(View(ui).apply { background = ui.shape(blend(style.ink, tint, 0.10f), 60, blend(style.line, tint, 0.35f)) },
            112, 112, Gravity.CENTER)
        val art = offer.model()?.avatarDecoration()?.imageUrls(animate = false)?.firstOrNull()
        place(cosmeticAvatar(ui, style, me, art, 80), -1, -1, Gravity.FILL)
    }

internal fun cosmeticAvatar(ui: Context, style: ShopStyle, me: MeUser, art: String?, size: Int): View =
    FrameLayout(ui).apply {
        val avatar = ui.artwork(null).apply {
            setImageURI(IconUtils.getForUser(me, size = 128))
            background = ui.shape(style.panel, size)
            clipToOutline = true
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        }
        place(avatar, size, size, Gravity.CENTER)
        val overlay = (size * 1.2f).toInt()
        place(ui.artwork(art), overlay, overlay, Gravity.CENTER)
    }
