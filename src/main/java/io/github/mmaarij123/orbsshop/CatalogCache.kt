package io.github.mmaarij123.orbsshop

import android.util.AtomicFile
import com.google.gson.Gson
import java.io.File

internal data class CatalogKey(val userId: Long, val locale: String?, val country: String?)

internal data class CachedCatalog(
    val key: CatalogKey,
    val savedAt: Long,
    val products: List<ProductFamily>,
    val schema: Int = 1,
) {
    fun isFresh(now: Long) = now - savedAt in 0 until FRESH_CATALOG_MS
}

internal const val FRESH_CATALOG_MS = 15 * 60 * 1000L

/** Only catalog entries are persisted. Balance, purchases and sessions are kept in memory. */
internal class CatalogCache(directory: File) {
    private val file = AtomicFile(directory.resolve("catalog.json"))
    private val gson = Gson()

    fun read(key: CatalogKey, now: Long): CachedCatalog? = runCatching {
        file.openRead().bufferedReader().use { reader ->
            gson.fromJson(reader, CachedCatalog::class.java)
        }?.takeIf {
            it.schema == 1 && it.key == key && now - it.savedAt in 0 until MAX_AGE_MS
        }
    }.getOrNull()

    fun write(catalog: CachedCatalog) {
        runCatching {
            val data = gson.toJson(catalog).toByteArray(Charsets.UTF_8)
            val stream = file.startWrite()
            try {
                stream.write(data)
                file.finishWrite(stream)
            } catch (error: Exception) {
                file.failWrite(stream)
                throw error
            }
        }
    }

    private companion object {
        const val MAX_AGE_MS = 24 * 60 * 60 * 1000L
    }
}
