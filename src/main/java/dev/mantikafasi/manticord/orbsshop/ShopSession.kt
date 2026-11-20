package dev.mantikafasi.manticord.orbsshop

import android.os.Handler
import android.os.Looper
import com.discord.stores.StoreStream
import java.util.concurrent.Executors
import rx.subjects.BehaviorSubject

internal data class ShopState(
    val userId: Long = 0,
    val products: List<ProductFamily>? = null,
    val balance: Int? = null,
    val partialCatalog: Boolean = false,
    val loadedAt: Long = 0,
    val owned: Set<Long>? = null,
    val unconfirmed: Set<Long> = emptySet(),
    val busy: Boolean = false,
    val notice: String? = null,
)

internal class ShopSession(private val api: ShopApi, private val cache: CatalogCache) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val updates = BehaviorSubject.create(ShopState())
    val states get() = updates.asObservable()

    @Volatile private var stopped = false
    private var requestVersion = 0L

    fun load(userId: Long, force: Boolean = false) {
        if (stopped) return
        val current = updates.value
        val now = System.currentTimeMillis()
        val retention = if (current.partialCatalog || current.balance == null || current.owned == null) {
            RETRY_DELAY_MS
        } else FRESH_CATALOG_MS
        if (current.userId == userId && (current.busy ||
                (!force && current.products != null && now - current.loadedAt in 0 until retention))
        ) return
        val version = ++requestVersion
        val loading = current.takeIf { it.userId == userId } ?: ShopState(userId = userId)
        updates.onNext(loading.copy(busy = true, notice = null))
        worker.execute {
            var fallback = loading
            val result = runCatching {
                val key = api.catalogKey(userId)
                val cached = cache.read(key, now)
                if (cached != null && loading.products == null) {
                    fallback = loading.copy(products = cached.products)
                    val cachedView = fallback.copy(busy = true, notice = "Checking your Orbs balance…")
                    publish(userId, version) { cachedView }
                }
                val connection = api.open(userId)
                val refreshed = if (!force && cached != null && cached.isFresh(now)) {
                    wallet(fallback.copy(products = cached.products, loadedAt = cached.savedAt), connection)
                } else {
                    snapshot(userId, connection, key)
                }
                refreshed.copy(unconfirmed = loading.unconfirmed - refreshed.owned.orEmpty())
            }
            publish(userId, version) {
                result.getOrElse {
                    fallback.copy(
                        balance = null,
                        owned = null,
                        loadedAt = now,
                        notice = when {
                            userId <= 0 -> "Sign in to Discord to open the shop."
                            fallback.products != null -> "Showing the saved catalog. Refresh to reconnect."
                            else -> "Couldn’t refresh the shop. Try again."
                        },
                    )
                }.copy(busy = false)
            }
        }
    }

    fun redeem(userId: Long, selected: Offer) {
        val current = updates.value
        if (stopped || current.userId != userId || current.busy || current.owned == null ||
            selected.sku in current.owned || selected.sku in current.unconfirmed || (current.balance ?: -1) < selected.orbs
        ) return
        val version = ++requestVersion
        updates.onNext(current.copy(busy = true, notice = null))
        worker.execute {
            var requestStarted = false
            val result = runCatching {
                val connection = api.open(userId)
                val fresh = snapshot(userId, connection).copy(unconfirmed = current.unconfirmed)
                val currentOffer = fresh.products.orEmpty()
                    .firstNotNullOfOrNull { family -> family.variants.firstOrNull { it.sku == selected.sku } }
                when {
                    currentOffer?.orbs != selected.orbs -> fresh.copy(
                        notice = "This offer changed. Open it again to review the current price.",
                    )
                    fresh.owned == null || fresh.balance == null -> fresh.copy(
                        notice = "Couldn’t check your balance or collection. Refresh and try again.",
                    )
                    selected.sku in fresh.owned -> fresh.copy(notice = "This colour is already in your collection.")
                    fresh.balance < selected.orbs -> fresh.copy(notice = "You don’t have enough Orbs.")
                    else -> {
                        check(!stopped)
                        requestStarted = true
                        connection.redeem(selected.sku)
                        StoreStream.getCollectibles().fetchPurchases(force = true)
                        fresh.copy(
                            balance = runCatching { connection.balance() }.getOrNull(),
                            owned = fresh.owned + selected.sku,
                            notice = "${selected.name} added to your collection.",
                        )
                    }
                }
            }.getOrElse { failure ->
                // An interrupted response can arrive after Discord has already granted the item.
                // Recheck instead of repeating the POST.
                val refreshed = runCatching { snapshot(userId) }
                    .getOrElse { current.copy(balance = null, owned = null) }
                val unconfirmed = current.unconfirmed - refreshed.owned.orEmpty()
                val uncertain = requestStarted && (failure !is ShopHttpError || failure.status >= 500) &&
                    refreshed.owned?.contains(selected.sku) != true
                val message = when {
                    requestStarted && refreshed.owned?.contains(selected.sku) == true ->
                        "${selected.name} is in your collection."
                    uncertain -> "Discord could not confirm this redemption. Check your collection and balance."
                    requestStarted -> "Discord declined this redemption. Check your balance and refresh the shop."
                    else -> "Couldn’t verify the offer. Refresh and try again."
                }
                refreshed.copy(
                    notice = message,
                    unconfirmed = if (uncertain) unconfirmed + selected.sku else unconfirmed,
                )
            }
            publish(userId, version) { result.copy(busy = false) }
        }
    }

    private fun snapshot(
        userId: Long,
        connection: ShopApi.Connection = api.open(userId),
        key: CatalogKey = api.catalogKey(userId),
    ): ShopState {
        val catalog = connection.catalog()
        val now = System.currentTimeMillis()
        if (!catalog.partial) cache.write(CachedCatalog(key, now, catalog.products))
        return wallet(
            ShopState(
                userId = userId,
                products = catalog.products,
                partialCatalog = catalog.partial,
                loadedAt = now,
            ),
            connection,
        )
    }

    private fun wallet(state: ShopState, connection: ShopApi.Connection) = state.copy(
        balance = runCatching { connection.balance() }.getOrNull(),
        owned = runCatching { connection.owned() }.getOrNull(),
    )

    private fun publish(userId: Long, version: Long, state: () -> ShopState) {
        main.post {
            if (!stopped && requestVersion == version && updates.value.userId == userId) updates.onNext(state())
        }
    }

    private companion object {
        const val RETRY_DELAY_MS = 60_000L
    }

    fun stop() {
        stopped = true
        main.post {
            worker.shutdown()
            updates.onCompleted()
        }
    }
}
