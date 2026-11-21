package io.github.mmaarij123.orbsshop

import android.app.Application
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import dev.mantikafasi.manticord.plugins.api.ManticordPlugin
import dev.mantikafasi.manticord.plugins.api.PluginScope
import dev.mantikafasi.manticord.plugins.api.pluginSettings
import rx.Completable

class OrbsShopPlugin : ManticordPlugin {
    private lateinit var scope: PluginScope
    private lateinit var shop: ShopSession
    private lateinit var icons: ShopIcons
    private var settingsShortcut: SettingsShopShortcut? = null

    override val settingsSchema = pluginSettings {
        action("open_shop", "Open Orbs Shop") { openShop() }
    }

    override fun start(scope: PluginScope): Completable = Completable.fromAction {
        this.scope = scope
        shop = ShopSession(ShopApi(scope.applicationContext), CatalogCache(scope.storage.cacheDir))
        icons = ShopIcons(requireNotNull(scope.resources.context))
        scope.runOnMainThread {
            val application = scope.applicationContext as? Application
            if (application != null) {
                settingsShortcut = SettingsShopShortcut(application, icons, ::openShop).also { it.start() }
            }
        }
    }

    private fun openShop() {
        scope.dialogs.page("Orbs Shop") { context -> ShopView(context, shop, icons) }
    }

    override fun stop(): Completable = Completable.fromAction {
        if (::shop.isInitialized) shop.stop()
        // The host closes the plugin scope before calling stop.
        Handler(Looper.getMainLooper()).post {
            settingsShortcut?.stop()
            settingsShortcut = null
        }
    }
}

class OrbsShopDiscoveryService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
