package com.iporadar.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Schema of the free JSON feed this app reads (see server/README.md).
 * Everything is optional so a partially-filled feed never crashes the app.
 */
@Serializable
data class FeedEnvelope(
    @SerialName("updatedAt") val updatedAt: String? = null,
    @SerialName("ipos") val ipos: List<IpoDto> = emptyList()
)

@Serializable
data class IpoDto(
    val id: String? = null,
    val name: String? = null,
    val symbol: String? = null,
    val board: String? = null,
    val status: String? = null,
    val logoUrl: String? = null,
    val priceMin: Double? = null,
    val priceMax: Double? = null,
    val lotSize: Int? = null,
    val issueSizeCr: Double? = null,
    val freshIssueCr: Double? = null,
    val ofsCr: Double? = null,
    val openDate: String? = null,
    val closeDate: String? = null,
    val allotmentDate: String? = null,
    val refundDate: String? = null,
    val listingDate: String? = null,
    val registrar: String? = null,
    val exchange: String? = null,
    val about: String? = null,
    val strengths: List<String> = emptyList(),
    val risks: List<String> = emptyList(),
    val financials: List<FinancialYearDto> = emptyList(),
    val valuation: ValuationDto? = null,
    val listingPrice: Double? = null,
    val gmp: GmpDto? = null,
    val subscription: SubscriptionDto? = null
)

@Serializable
data class GmpDto(
    val premium: Double? = null,
    val kostak: Double? = null,
    val subjectToSauda: Double? = null,
    val updatedAt: String? = null,
    val history: List<GmpPointDto> = emptyList()
)

@Serializable
data class GmpPointDto(
    val date: String? = null,
    val premium: Double? = null
)

@Serializable
data class SubscriptionDto(
    val qib: Double? = null,
    val nii: Double? = null,
    val niiSmall: Double? = null,
    val niiBig: Double? = null,
    val retail: Double? = null,
    val employee: Double? = null,
    val total: Double? = null,
    val updatedAt: String? = null
)

@Serializable
data class FinancialYearDto(
    val period: String? = null,
    val revenueCr: Double? = null,
    val patCr: Double? = null,
    val netWorthCr: Double? = null,
    val borrowingsCr: Double? = null
)

@Serializable
data class ValuationDto(
    val peRatio: Double? = null,
    val industryPe: Double? = null,
    val eps: Double? = null,
    val ronwPct: Double? = null,
    val marketCapCr: Double? = null
)
