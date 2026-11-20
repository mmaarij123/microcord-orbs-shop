package dev.mantikafasi.manticord.orbsshop

import android.content.Context
import com.discord.restapi.ClientMetadataInterceptor
import com.discord.restapi.RequiredHeadersInterceptor
import com.discord.restapi.RestAPIBuilder
import com.discord.restapi.cookie.CookieJars
import com.discord.stores.StoreStream
import com.google.gson.JsonParser
import java.io.IOException
import java.util.Locale
import java.util.UUID
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// The plugin SDK predates support for custom Microcord backends. Older hosts only use Discord.
private fun isDiscordBackend(): Boolean {
    val type = try {
        Class.forName("com.discord.utilities.rest.ClientBackend")
    } catch (_: ClassNotFoundException) {
        return true
    }
    val instance = type.getField("INSTANCE").get(null)
    return type.getMethod("isDiscord").invoke(instance) == true
}

internal class ShopApi(context: Context) {
    private val application = context.applicationContext

    fun catalogKey(userId: Long): CatalogKey {
        val me = StoreStream.getUsers().getMe()
        check(userId > 0 && me.id == userId && isDiscordBackend())
        return CatalogKey(userId, StoreStream.getUserSettingsSystem().getLocale(), me.storeCountry?.country)
    }

    fun open(userId: Long): Connection {
        check(isDiscordBackend()) { "Orbs Shop is available on Discord accounts." }
        val auth = StoreStream.getAuthentication()
        val token = auth.getAuthState()?.token ?: error("Sign in to Discord to open the shop.")
        check(userId > 0 && StoreStream.getUsers().getMe().id == userId) {
            "Sign in to Discord to open the shop."
        }
        val headers = RequiredHeadersInterceptor(object : RequiredHeadersInterceptor.HeadersProvider {
            override fun getAuthToken(): String {
                check(auth.getAuthState()?.token == token && StoreStream.getUsers().getMe().id == userId) {
                    "Your Discord account changed. Reopen the shop."
                }
                return token
            }

            override fun getFingerprint() = auth.getFingerprint()
            override fun getLocale() = StoreStream.getUserSettingsSystem().getLocale()
            override fun getAcceptLanguages() = Locale.getDefault().toLanguageTag()
            override fun getSpotifyToken(): String? = null
        })
        val routes = RestAPIBuilder("https://discord.com/api/", CookieJars.persistent(application))
            .build(ShopRoutes::class.java, interceptors = listOf(headers, ClientMetadataInterceptor))
        return Connection(routes, StoreStream.getUsers().getMe().storeCountry?.country)
    }

    class Connection internal constructor(
        private val routes: ShopRoutes,
        private val country: String?,
    ) {
        fun catalog(): CatalogLoad {
            val sources = listOf(
                runCatching { ShopCatalog.products(JsonParser.parseString(routes.orbsCatalog(country).read())) },
                runCatching { ShopCatalog.products(JsonParser.parseString(mainCatalog())) },
            )
            val available = sources.mapNotNull { it.getOrNull() }
            if (available.isEmpty()) throw requireNotNull(sources.first().exceptionOrNull())
            return CatalogLoad(ShopCatalog.merge(available.flatten()), available.size < sources.size)
        }

        private fun mainCatalog(): String = try {
            routes.mainCatalog(country).read()
        } catch (error: ShopHttpError) {
            if (error.status != 404 && error.status != 405) throw error
            routes.mainCatalogShopTab(country).read()
        }

        fun balance(): Int {
            return JsonParser.parseString(routes.balance().read()).asJsonObject.int("balance")
                ?.takeIf { it >= 0 } ?: error("Discord returned an invalid Orbs balance.")
        }

        fun owned(): Set<Long> {
            val data = JsonParser.parseString(routes.purchases().read()).asJsonArray
            return data.objects().mapNotNullTo(mutableSetOf()) { it.string("sku_id")?.toLongOrNull() }
        }

        fun redeem(sku: Long) {
            val data = JsonParser.parseString(
                routes.redeem(sku, mapOf("checkout_session_id" to UUID.randomUUID().toString())).read(),
            )
            check(data.isJsonArray && !data.asJsonArray.isEmpty) { "Discord did not confirm the redemption." }
        }
    }
}

internal interface ShopRoutes {
    @GET("collectibles-shop?tab=orbs&include_bundles=false&variants_return_style=2")
    fun orbsCatalog(@Query("country_code") country: String?): Call<ResponseBody>

    @GET("collectibles-categories/v2?tab=catalog&variants_return_style=2")
    fun mainCatalog(@Query("country_code") country: String?): Call<ResponseBody>

    @GET("collectibles-shop?tab=catalog&variants_return_style=2")
    fun mainCatalogShopTab(@Query("country_code") country: String?): Call<ResponseBody>

    @GET("users/@me/virtual-currency/balance")
    fun balance(): Call<ResponseBody>

    @GET("users/@me/collectibles-purchases")
    fun purchases(): Call<ResponseBody>

    @POST("virtual-currency/skus/{sku}/redeem")
    fun redeem(@Path("sku") sku: Long, @Body request: Map<String, String>): Call<ResponseBody>
}

internal data class CatalogLoad(val products: List<ProductFamily>, val partial: Boolean)

internal class ShopHttpError(val status: Int) : IOException("Discord returned HTTP $status.")

private fun Call<ResponseBody>.read(): String {
    val response = execute()
    if (!response.isSuccessful) {
        response.errorBody()?.close()
        throw ShopHttpError(response.code())
    }
    return response.body()?.use(ResponseBody::string) ?: error("Discord returned an empty response.")
}
