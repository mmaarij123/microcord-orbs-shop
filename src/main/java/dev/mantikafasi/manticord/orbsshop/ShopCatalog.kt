package dev.mantikafasi.manticord.orbsshop

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal enum class CosmeticKind(val wireType: Int, val singular: String, val filter: String) {
    AVATAR(0, "Avatar decoration", "Avatar Decorations"),
    EFFECT(1, "Profile effect", "Profile Effects"),
    FRAME(3, "Profile frame", "Profile Frames"),
    NAMEPLATE(2, "Nameplate", "Nameplates"),
}

internal data class Offer(
    val sku: Long,
    val name: String,
    val summary: String,
    val kind: CosmeticKind,
    val orbs: Int,
    val colour: String?,
    val swatch: String?,
    val product: JsonObject,
)

internal data class ProductFamily(val name: String, val variants: List<Offer>) {
    val kind = variants.first().kind
}

internal object ShopCatalog {
    data class CatalogProduct(val data: JsonObject, val parent: JsonObject? = null) {
        val sku = data.string("sku_id")?.toLongOrNull()?.takeIf { it > 0 }
        val family = data.string("base_variant_sku_id")?.toLongOrNull()?.takeIf { it > 0 }
            ?: parent?.string("sku_id")?.toLongOrNull()?.takeIf { it > 0 }
        val familyName = data.string("base_variant_name")?.takeIf(String::isNotBlank)
            ?: parent?.string("name")?.takeIf(String::isNotBlank)
    }

    fun products(payload: JsonElement): List<CatalogProduct> {
        val categories = when {
            payload.isJsonArray -> payload.asJsonArray
            payload.isJsonObject -> payload.asJsonObject.array("categories")
            else -> null
        } ?: error("Discord returned an unrecognized shop catalog.")

        return categories.objects().flatMap { category ->
            category.array("products").objects().flatMap { product ->
                val variants = product.array("variants").objects()
                if (variants.isEmpty()) listOf(CatalogProduct(product))
                else variants.map { CatalogProduct(it, product) }
            }
        }
    }

    fun merge(products: List<CatalogProduct>): List<ProductFamily> {
        // The same SKU can appear on the Orbs tab as a single tile and in the main catalog
        // inside its colour family. Use the family metadata from either response.
        val hints = products.filter { it.sku != null && it.family != null }
            .associateBy { it.sku }
        val names = products.mapNotNull { row ->
            val id = row.family ?: return@mapNotNull null
            row.familyName?.let { id to it }
        }.toMap()
        val families = linkedMapOf<Pair<CosmeticKind, Long>, NamedOffers>()
        val included = mutableSetOf<Long>()
        for (row in products) {
            val product = row.data
            val sku = row.sku ?: continue
            if (sku in included) continue
            val kind = CosmeticKind.entries.firstOrNull { it.wireType == product.int("type") } ?: continue
            if (product.array("items").objects().none { it.int("type") == kind.wireType }) continue
            val price = orbPrice(product) ?: continue
            val name = product.string("name")?.takeIf(String::isNotBlank) ?: continue
            val hint = hints[sku]
            val familySku = hint?.family ?: sku
            val family = families.getOrPut(kind to familySku) { NamedOffers(names[familySku] ?: name) }
            family.offers += Offer(
                sku, name, product.string("summary").orEmpty(), kind, price,
                product.string("variant_label") ?: hint?.data?.string("variant_label"),
                product.string("variant_value") ?: hint?.data?.string("variant_value"),
                product,
            )
            included += sku
        }
        return families.values.map { ProductFamily(it.name, it.offers) }
    }

    private fun orbPrice(product: JsonObject): Int? {
        // Discord uses the default country price to determine Orbs eligibility.
        val amounts = product.obj("prices")?.obj("0")?.obj("country_prices")?.array("prices").objects()
            .filter { it.string("currency") == "discord_orb" }
            .mapNotNull { it.int("amount")?.takeIf { amount -> amount > 0 } }
            .distinct()
        return amounts.singleOrNull()
    }

    private class NamedOffers(val name: String, val offers: MutableList<Offer> = mutableListOf())
}

internal fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
internal fun JsonObject.array(key: String): JsonArray? = get(key)?.takeIf { it.isJsonArray }?.asJsonArray
internal fun JsonArray?.objects(): List<JsonObject> =
    this?.filter { it.isJsonObject }?.map { it.asJsonObject }.orEmpty()

internal fun JsonObject.string(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString
internal fun JsonObject.int(key: String): Int? = string(key)?.toIntOrNull()
