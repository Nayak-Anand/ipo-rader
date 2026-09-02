package com.iporadar.app.core.net

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * Minimal in-memory cookie jar. NSE hands out a short-lived session cookie on the
 * first HTML page load and refuses every /api/ call that arrives without it.
 */
class InMemoryCookieJar : CookieJar {

    private val store = ConcurrentHashMap<String, MutableMap<String, Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.topPrivateDomain() ?: url.host
        val bucket = store.getOrPut(host) { ConcurrentHashMap() }
        cookies.forEach { bucket[it.name] = it }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.topPrivateDomain() ?: url.host
        val bucket = store[host] ?: return emptyList()
        val now = System.currentTimeMillis()
        val expired = bucket.values.filter { it.expiresAt < now }
        expired.forEach { bucket.remove(it.name) }
        return bucket.values.toList()
    }

    fun clear() = store.clear()
}
