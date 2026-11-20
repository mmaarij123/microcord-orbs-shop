package dev.mantikafasi.manticord.orbsshop

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.discord.R as HostR
import com.discord.app.AppActivity
import com.discord.utilities.lifecycle.ActivityProvider
import com.discord.widgets.settings.WidgetSettings
import java.util.WeakHashMap

/** The plugin SDK's header contribution is for chats. Settings owns a separate toolbar. */
internal class SettingsShopShortcut(
    private val application: Application,
    private val icons: ShopIcons,
    private val openShop: () -> Unit,
) : Application.ActivityLifecycleCallbacks {
    private val itemId = View.generateViewId()
    private val managers = mutableSetOf<FragmentManager>()
    private val toolbars = WeakHashMap<Toolbar, Unit>()
    private val fragments = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentViewCreated(
            fm: FragmentManager,
            fragment: Fragment,
            view: View,
            savedInstanceState: Bundle?,
        ) {
            attachShortcut(fragment, view)
        }

        override fun onFragmentResumed(fm: FragmentManager, fragment: Fragment) {
            fragment.view?.let { attachShortcut(fragment, it) }
        }
    }

    fun start() {
        application.registerActivityLifecycleCallbacks(this)
        ActivityProvider.getActivity()?.let(::watch)
    }

    fun stop() {
        application.unregisterActivityLifecycleCallbacks(this)
        managers.forEach { it.unregisterFragmentLifecycleCallbacks(fragments) }
        managers.clear()
        toolbars.keys.forEach { it.menu.removeItem(itemId) }
        toolbars.clear()
    }

    private fun watch(activity: Activity) {
        val manager = (activity as? AppActivity)?.supportFragmentManager ?: return
        if (managers.add(manager)) {
            manager.registerFragmentLifecycleCallbacks(fragments, true)
            visitSettings(manager)
        }
    }

    private fun visitSettings(manager: FragmentManager) {
        manager.fragments.forEach { fragment ->
            fragment.view?.let { attachShortcut(fragment, it) }
            if (fragment.isAdded) visitSettings(fragment.childFragmentManager)
        }
    }

    private fun attachShortcut(fragment: Fragment, root: View) {
        if (fragment !is WidgetSettings) return
        val toolbar = root.findViewById<Toolbar>(HostR.id.action_bar_toolbar) ?: return
        if (toolbar.menu.findItem(itemId) != null) return
        val colour = ShopStyle(toolbar.context).muted
        toolbar.menu.add(Menu.NONE, itemId, Menu.NONE, "Orbs Shop")
            .setIcon(icons.drawable(R.drawable.ic_discord_shop, colour))
            .setOnMenuItemClickListener { openShop(); true }
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        toolbars[toolbar] = Unit
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = watch(activity)
    override fun onActivityStarted(activity: Activity) = watch(activity)
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) {
        val manager = (activity as? AppActivity)?.supportFragmentManager ?: return
        if (managers.remove(manager)) manager.unregisterFragmentLifecycleCallbacks(fragments)
    }
}
