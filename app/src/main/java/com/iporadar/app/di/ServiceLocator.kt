package com.iporadar.app.di

import android.content.Context
import com.iporadar.app.BuildConfig
import com.iporadar.app.core.net.BrowserHeadersInterceptor
import com.iporadar.app.core.net.InMemoryCookieJar
import com.iporadar.app.core.net.NseSessionInterceptor
import com.iporadar.app.data.local.AppPrefs
import com.iporadar.app.data.remote.FeedApi
import com.iporadar.app.data.remote.MaashitlaApi
import com.iporadar.app.data.remote.NseApi
import com.iporadar.app.data.remote.allotment.KFinSource
import com.iporadar.app.data.remote.allotment.MaashitlaSource
import com.iporadar.app.data.remote.allotment.PurvaSource
import com.iporadar.app.data.remote.allotment.SkylineSource
import com.iporadar.app.data.repo.AllotmentRepository
import com.iporadar.app.data.repo.IpoRepository
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Hand-rolled DI. The graph is small enough that a code-generating framework
 * would cost more build time than it saves.
 */
object ServiceLocator {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val json: Json by lazy {
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
            explicitNulls = false
        }
    }

    private val contentType = "application/json".toMediaType()

    private val cookieJar by lazy { InMemoryCookieJar() }

    private val logging by lazy {
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
    }

    /** No session interceptor here — this is what primes the NSE cookie, so it must not recurse. */
    private val bareClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    private val nseClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(BrowserHeadersInterceptor("https://www.nseindia.com/market-data/all-upcoming-issues-ipo"))
            .addInterceptor(NseSessionInterceptor { bareClient })
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    private val feedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cache(Cache(appContext.cacheDir.resolve("http"), 8L * 1024 * 1024))
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    val nseApi: NseApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.nseindia.com/")
            .client(nseClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(NseApi::class.java)
    }

    val feedApi: FeedApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.FEED_BASE_URL)
            .client(feedClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(FeedApi::class.java)
    }

    /** Allotment lookups must never be served from cache — the answer changes. */
    private val plainClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    val maashitlaApi: MaashitlaApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.maashitla.com/api/")
            .client(plainClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(MaashitlaApi::class.java)
    }

    /** Form-based registrars are cookie-driven (session, then CSRF token). */
    private val formClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(InMemoryCookieJar())
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    val allotmentRepository: AllotmentRepository by lazy {
        AllotmentRepository(
            listOf(
                MaashitlaSource(maashitlaApi),
                KFinSource(plainClient, json),
                SkylineSource(formClient),
                PurvaSource(formClient)
            )
        )
    }

    val prefs: AppPrefs by lazy { AppPrefs(appContext) }

    val repository: IpoRepository by lazy {
        IpoRepository(
            context = appContext,
            feedApi = feedApi,
            nseApi = nseApi,
            prefs = prefs,
            json = json
        )
    }
}
