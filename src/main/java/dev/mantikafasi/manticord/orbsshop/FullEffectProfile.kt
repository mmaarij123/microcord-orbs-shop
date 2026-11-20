package dev.mantikafasi.manticord.orbsshop

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.discord.api.user.UserProfile
import com.discord.models.user.MeUser
import com.discord.stores.StoreStream
import com.discord.utilities.SnowflakeUtils
import com.discord.utilities.icon.IconUtils
import java.text.DateFormat
import java.util.Date
import rx.Subscription

internal class FullEffectProfile(
    private val ui: Context,
    private val style: ShopStyle,
    private val me: MeUser,
    private val offer: Offer,
) : FrameLayout(ui) {
    private val spec = EffectSpec.fromProduct(offer.product)
    private val effectArtwork = EffectOverlay(ui, spec, StoreStream.getAccessibility().isReducedMotionEnabled())
    private val body = ui.column()
    private val card = FrameLayout(ui).apply {
        background = ui.shape(style.ink, 16, style.line)
        clipToOutline = true
        place(ScrollView(ui).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(body, -1, -2)
        }, -1, -1, Gravity.FILL)
        place(effectArtwork, -1, -1, Gravity.FILL)
    }
    private var profileUpdates: Subscription? = null

    init {
        background = ui.artBackground(style, offer, 18)
        place(card, -1, -1, Gravity.CENTER)
        render(null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val profiles = StoreStream.getUserProfiles()
        profileUpdates = profiles.observeUserProfile(me.id).subscribe({ profile ->
            post { if (isAttachedToWindow) render(profile) }
        }, {})
        profiles.fetchProfileIfMissing(me.id)
    }

    override fun onDetachedFromWindow() {
        profileUpdates?.unsubscribe()
        profileUpdates = null
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec) - ui.dp(16)
        val height = MeasureSpec.getSize(heightMeasureSpec) - ui.dp(16)
        val size = spec.fit(width, height)
        card.layoutParams.width = size.width
        card.layoutParams.height = size.height
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    fun replay() = effectArtwork.replay()

    private fun render(profile: UserProfile?) {
        val metadata = profile?.userProfile
        body.removeAllViews()
        val themeColours = metadata?.themeColors.orEmpty()
        val top = themeColours.getOrNull(0)?.let { it or Color.BLACK } ?: offer.tint(style.accent)
        val bottom = themeColours.getOrNull(1)?.let { it or Color.BLACK } ?: top
        card.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(blend(style.ink, top, 0.28f), blend(style.ink, bottom, 0.12f)),
        ).apply {
            cornerRadius = ui.dp(16).toFloat()
            setStroke(ui.dp(1), style.line)
        }
        val banner = ui.artwork(IconUtils.getForUserBanner(me.id, metadata?.banner ?: me.banner, 512, false)).apply {
            background = ui.shape(blend(style.panel, metadata?.accentColor?.let { it or Color.BLACK } ?: top, 0.47f), 0)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        val artwork = me.avatarDecoration?.imageUrls(animate = false)?.firstOrNull()
        body.addLine(FrameLayout(ui).apply {
            place(banner, -1, 80, Gravity.TOP)
            place(cosmeticAvatar(ui, style, me, artwork, 62), 80, 80, Gravity.TOP or Gravity.START, left = 8, top = 44)
        }, height = 126)
        body.addLine(ui.column().apply {
            pad(14, 10)
            addLine(ui.label(me.globalName?.takeIf(String::isNotBlank) ?: me.username, 20, style.text, true))
            val pronouns = metadata?.pronouns?.takeIf(String::isNotBlank)
            addLine(ui.label(listOfNotNull("@${me.username}", pronouns).joinToString(" · "), 13, style.muted), 4)
            val badges = profile?.badges.orEmpty()
            if (badges.isNotEmpty()) {
                addLine(ui.row().apply {
                    badges.take(8).forEach { badge ->
                        val image = ui.artwork("https://cdn.discordapp.com/badge-icons/${badge.icon}.png").apply {
                            contentDescription = badge.description
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                        }
                        addView(image, LinearLayout.LayoutParams(ui.dp(18), ui.dp(18)).apply {
                            marginEnd = ui.dp(4)
                        })
                    }
                }, 8)
            }
            val bio = metadata?.bio ?: me.bio
            if (!bio.isNullOrBlank()) addLine(section("About me", bio), 12)
            val joined = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(SnowflakeUtils.toTimestamp(me.id)))
            addLine(section("Discord member since", joined), 12)
            val connections = profile?.connectedAccounts.orEmpty().filter { it.visibility == 1 }
                .map { it.name }.filter(String::isNotBlank)
            if (connections.isNotEmpty()) addLine(section("Connections", connections.joinToString("\n")), 12)
        })
    }

    private fun section(title: String, text: String) = ui.column().apply {
        background = ui.shape(style.panel, 9)
        pad(10, 10)
        addLine(ui.label(title, 12, style.text, true))
        addLine(ui.label(text, 13, style.muted), 5)
    }
}
