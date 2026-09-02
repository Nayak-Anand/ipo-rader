package com.iporadar.app.core.net

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

private const val UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Mobile Safari/537.36"

/** NSE blocks anything that does not look like a browser. */
class BrowserHeadersInterceptor(
    private val referer: String
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request().newBuilder()
            .header("User-Agent", UA)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Referer", referer)
            .header("Connection", "keep-alive")
            .build()
        return chain.proceed(req)
    }
}

/**
 * NSE's /api endpoints only answer once the caller holds a session cookie issued by
 * the main site. We lazily prime that session, and re-prime once if a call is rejected.
 */
class NseSessionInterceptor(
    private val clientProvider: () -> OkHttpClient
) : Interceptor {

    @Volatile
    private var primedAt: Long = 0L

    override fun intercept(chain: Interceptor.Chain): Response {
        if (System.currentTimeMillis() - primedAt > SESSION_TTL_MS) {
            prime()
        }
        val response = chain.proceed(chain.request())
        if (response.code in REJECT_CODES) {
            response.close()
            prime(force = true)
            return chain.proceed(chain.request().newBuilder().build())
        }
        return response
    }

    private fun prime(force: Boolean = false) {
        if (!force && System.currentTimeMillis() - primedAt <= SESSION_TTL_MS) return
        try {
            val warmUp = Request.Builder()
                .url("https://www.nseindia.com/market-data/all-upcoming-issues-ipo")
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            clientProvider().newCall(warmUp).execute().use { /* cookies land in the jar */ }
            primedAt = System.currentTimeMillis()
        } catch (_: IOException) {
            // Offline or blocked — the repository falls back to the feed / cache.
        }
    }

    private companion object {
        const val SESSION_TTL_MS = 5 * 60 * 1000L
        val REJECT_CODES = setOf(401, 403, 429)
    }
}
