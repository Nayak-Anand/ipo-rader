package com.iporadar.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Maashitla Securities' public allotment API — the same one their own website calls.
 *
 * It is unauthenticated and CORS-open, and it is the only major registrar that does
 * not put a captcha or bot-defence in front of an allotment lookup. The rest
 * (KFin, Bigshare, MUFG, Purva) deliberately gate automation, and this app respects
 * that by deep-linking to their sites instead.
 */
interface MaashitlaApi {

    /** IPOs Maashitla is currently the registrar for. */
    @GET("public-issue/companies")
    suspend fun companies(): List<MaashitlaCompanyDto>

    /**
     * 200 with a body when a record exists, 404 when it does not — so the raw
     * [Response] is needed rather than just the body.
     */
    @GET("public-issue/search")
    suspend fun search(
        @Query("company_name") companyName: String,
        @Query("pan") pan: String
    ): Response<JsonElement>
}

@Serializable
data class MaashitlaCompanyDto(
    @SerialName("company_id") val companyId: String? = null,
    @SerialName("company_name") val companyName: String? = null
)
