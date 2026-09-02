package com.iporadar.app.data.remote

import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET
import retrofit2.http.Query

/** Our own free, static JSON feed (GitHub Pages / raw.githubusercontent / Cloudflare Pages). */
interface FeedApi {
    @GET("ipos.json")
    suspend fun ipos(): FeedEnvelope
}

/**
 * NSE's public market-data endpoints. Responses are read as raw JSON because NSE
 * changes field names and types (string vs number) without notice.
 */
interface NseApi {
    @GET("api/all-upcoming-issues")
    suspend fun upcoming(@Query("category") category: String = "ipo"): JsonElement

    @GET("api/ipo-current-issue")
    suspend fun currentIssues(): JsonElement

    @GET("api/public-past-issues")
    suspend fun pastIssues(): JsonElement

    /**
     * Per-symbol detail. This is the only NSE endpoint that carries the bid lot,
     * the registrar name and the category-wise subscription split, so it is worth
     * the extra request per live issue.
     */
    @GET("api/ipo-detail")
    suspend fun ipoDetail(
        @Query("symbol") symbol: String,
        @Query("series") series: String = "EQ"
    ): JsonElement
}
