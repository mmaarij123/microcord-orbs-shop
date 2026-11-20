package dev.mantikafasi.manticord.orbsshop

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogCacheTest {
    @Test fun restartsCanRestoreAnAccountScopedCatalogWithItsVariantPrices() {
        val key = CatalogKey(123L, "en-US", "US")
        val product = JsonObject().apply { addProperty("name", "Test cosmetic") }
        val offer = Offer(42L, "Test cosmetic", "", CosmeticKind.EFFECT, 4100, "Blue", "#6699AA", product)
        val cache = CachedCatalog(key, 2_000_000, listOf(ProductFamily("Test cosmetic", listOf(offer))))
        val gson = Gson()
        val decoded = gson.fromJson(gson.toJson(cache), CachedCatalog::class.java)
        assertEquals(cache, decoded)
        assertEquals(CosmeticKind.EFFECT, decoded.products.single().kind)
        assertTrue(decoded.isFresh(cache.savedAt + FRESH_CATALOG_MS - 1))
        assertFalse(decoded.isFresh(cache.savedAt + FRESH_CATALOG_MS))
        assertFalse(decoded.isFresh(cache.savedAt - 1))
    }
}
