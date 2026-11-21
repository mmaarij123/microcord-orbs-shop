package io.github.mmaarij123.orbsshop

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopCatalogTest {
    @Test fun groupedColoursAreIndividuallyEligibleAndKeepTheirOwnPrice() {
        val moon = """{"sku_id":"100","type":2000,"name":"Moon","variants":[
            ${product(101, 0, "Red", prices(4100, includeCash = true))},
            ${product(102, 0, "Gold", prices(null, includeCash = true))},
            ${product(103, 0, "Blue", prices(3000))}
        ]}"""
        val catalog = catalog(
            moon, moon,
            product(201, 1, "Comet", prices(4100)),
            product(301, 3, "Arc", prices(5000)),
            product(401, 2, "Cloud", prices(3300)),
        )
        assertEquals(CosmeticKind.entries.toSet(), catalog.map { it.kind }.toSet())
        val variants = catalog.first().variants
        assertEquals("Moon", catalog.first().name)
        assertEquals(listOf(101L, 103L), variants.map { it.sku })
        assertEquals(listOf("Red", "Blue"), variants.map { it.colour })
        assertEquals(listOf(4100, 3000), variants.map { it.orbs })
    }

    @Test fun giftPricesCashPricesAndUnrelatedProductsNeverAppear() {
        val giftOnly = prices(4000).replace("\"0\"", "\"1\"")
        val memberOnly = prices(4000).replace("\"0\"", "\"4\"")
        assertTrue(
            catalog(
                product(1, 0, "Gift only", giftOnly),
                product(2, 0, "Cash only", prices(null, includeCash = true)),
                product(3, 0, "Membership", memberOnly),
                product(4, 1000, "Bundle", prices(4000)),
                product(5, 0, "Zero", prices(0)),
            ).isEmpty(),
        )
    }

    @Test fun mergesDualCurrencyItemsFromTheMainCatalogWithoutDuplicatingOrbsTiles() {
        val red = product(100, 0, "Red", prices(4100, includeCash = true))
        val orbTab = payload(red, product(301, 3, "Orb exclusive", prices(5000)))
        val main = payload(
            """{"sku_id":"100","type":2000,"name":"Moon","variants":[
                $red,
                ${product(101, 0, "Blue", prices(3600, includeCash = true))},
                ${product(102, 0, "Cash only", prices(null, includeCash = true))}
            ]}""",
            product(201, 1, "Dual-currency effect", prices(4100, includeCash = true)),
        )
        val merged = ShopCatalog.merge(orbTab + main)
        assertEquals(listOf("Moon", "Orb exclusive", "Dual-currency effect"), merged.map { it.name })
        assertEquals(listOf(100L, 101L), merged.first().variants.map { it.sku })
        assertEquals(listOf(4100, 3600), merged.first().variants.map { it.orbs })
        assertEquals(201L, merged.last().variants.single().sku)
    }

    private fun catalog(vararg products: String) = ShopCatalog.merge(payload(*products))

    private fun payload(vararg products: String) = ShopCatalog.products(
        JsonParser.parseString("""{"categories":[{"products":[${products.joinToString(",")}]}],"shop_blocks":[]}"""),
    )

    private fun product(sku: Long, type: Int, name: String, prices: String) =
        """{"sku_id":"$sku","type":$type,"name":"$name","variant_label":"$name",
            "variant_value":"#8caaff","items":[{"type":$type,"sku_id":"$sku"}],"prices":$prices}"""

    private fun prices(orbs: Int?, includeCash: Boolean = false): String {
        val values = listOfNotNull(
            orbs?.let { """{"amount":$it,"currency":"discord_orb","exponent":0}""" },
            """{"amount":499,"currency":"usd","exponent":2}""".takeIf { includeCash },
        )
        return """{"0":{"country_prices":{"prices":[${values.joinToString(",")}]}}}"""
    }
}
