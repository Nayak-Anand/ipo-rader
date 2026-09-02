package com.iporadar.app.data.model

import java.time.LocalDate

enum class IpoBoard { MAINBOARD, SME;
    val label: String get() = if (this == MAINBOARD) "Mainboard" else "SME"
}

/**
 * Finer-grained than [IpoStatus]: it splits "closed" into the two waits that follow,
 * so a just-closed IPO can keep its place in the list until it actually lists.
 */
enum class Stage {
    UPCOMING, OPEN, ALLOTMENT, LISTING, LISTED, CLOSED;

    val label: String
        get() = when (this) {
            UPCOMING -> "Upcoming"
            OPEN -> "Open"
            ALLOTMENT -> "Allotment"
            LISTING -> "Listing"
            LISTED -> "Listed"
            CLOSED -> "Closed"
        }
}

/** How long a closed issue with no published dates stays in the active list. */
private const val AWAITING_GRACE_DAYS = 10L

enum class IpoStatus { UPCOMING, OPEN, CLOSED, LISTED;
    val label: String
        get() = when (this) {
            UPCOMING -> "Upcoming"
            OPEN -> "Open"
            CLOSED -> "Closed"
            LISTED -> "Listed"
        }
}

/**
 * Grey market snapshot.
 *
 * Beyond the premium itself, the grey market quotes two other numbers:
 *  - Kostak: flat amount paid for an application, whatever the allotment.
 *  - Subject to Sauda: amount paid only if the application actually gets allotment.
 */
data class Gmp(
    val premium: Double,
    val kostak: Double? = null,
    val subjectToSauda: Double? = null,
    val updatedAt: String? = null,
    val history: List<GmpPoint> = emptyList()
) {
    /** Expected listing price = upper price band + GMP. */
    fun expectedListing(priceMax: Double?): Double? =
        priceMax?.let { it + premium }

    /** Estimated listing gain in %. */
    fun estimatedGainPct(priceMax: Double?): Double? {
        if (priceMax == null || priceMax <= 0.0) return null
        return premium / priceMax * 100.0
    }
}

data class GmpPoint(val date: String, val premium: Double)

/**
 * Category-wise subscription, in times subscribed.
 *
 * SEBI splits the non-institutional bucket at ₹10 lakh: sNII bids ₹2–10 lakh,
 * bNII bids above ₹10 lakh. [nii] is the combined figure when only that is known.
 */
data class Subscription(
    val qib: Double? = null,
    val nii: Double? = null,
    val niiSmall: Double? = null,
    val niiBig: Double? = null,
    val retail: Double? = null,
    val employee: Double? = null,
    val total: Double? = null,
    val updatedAt: String? = null
)

/** One financial year from the RHP. Amounts in ₹ crore. */
data class FinancialYear(
    val period: String,
    val revenueCr: Double? = null,
    val patCr: Double? = null,
    val netWorthCr: Double? = null,
    val borrowingsCr: Double? = null
)

/** Valuation metrics, post-issue unless the prospectus says otherwise. */
data class Valuation(
    val peRatio: Double? = null,
    val industryPe: Double? = null,
    val eps: Double? = null,
    val ronwPct: Double? = null,
    val marketCapCr: Double? = null
) {
    val isEmpty: Boolean
        get() = peRatio == null && industryPe == null && eps == null &&
            ronwPct == null && marketCapCr == null

    /** Cheap vs its peers, expensive, or neither. Null when we cannot compare. */
    val versusIndustry: Int?
        get() {
            if (peRatio == null || industryPe == null || industryPe <= 0.0) return null
            return peRatio.compareTo(industryPe)
        }
}

data class Ipo(
    val id: String,
    val name: String,
    val symbol: String,
    val board: IpoBoard = IpoBoard.MAINBOARD,
    val status: IpoStatus = IpoStatus.UPCOMING,
    val logoUrl: String? = null,
    val priceMin: Double? = null,
    val priceMax: Double? = null,
    val lotSize: Int? = null,
    val issueSizeCr: Double? = null,
    val freshIssueCr: Double? = null,
    val ofsCr: Double? = null,
    val openDate: LocalDate? = null,
    val closeDate: LocalDate? = null,
    val allotmentDate: LocalDate? = null,
    val refundDate: LocalDate? = null,
    val listingDate: LocalDate? = null,
    val registrar: Registrar? = null,
    val registrarName: String? = null,
    val exchange: String? = null,
    val about: String? = null,
    val strengths: List<String> = emptyList(),
    val risks: List<String> = emptyList(),
    val financials: List<FinancialYear> = emptyList(),
    val valuation: Valuation? = null,
    val gmp: Gmp? = null,
    val subscription: Subscription? = null,
    val listingPrice: Double? = null
) {
    /** Retail minimum investment for 1 lot at the cut-off (upper) price. */
    val minInvestment: Double?
        get() = if (priceMax != null && lotSize != null) priceMax * lotSize else null

    val priceBandLabel: String
        get() = when {
            priceMin != null && priceMax != null && priceMin != priceMax ->
                "₹${priceMin.toInt()} - ₹${priceMax.toInt()}"
            priceMax != null -> "₹${priceMax.toInt()}"
            else -> "TBA"
        }

    /** Actual listing gain %, only available once listed. */
    val listingGainPct: Double?
        get() {
            if (listingPrice == null || priceMax == null || priceMax <= 0.0) return null
            return (listingPrice - priceMax) / priceMax * 100.0
        }

    /**
     * Where a closed issue sits in the post-bidding journey. This is what the user
     * actually cares about after applying — an IPO that closed today is not "done",
     * it has an allotment and a listing still coming.
     */
    fun stage(today: LocalDate = LocalDate.now()): Stage = when {
        status == IpoStatus.LISTED -> Stage.LISTED
        status == IpoStatus.UPCOMING -> Stage.UPCOMING
        status == IpoStatus.OPEN -> Stage.OPEN
        allotmentDate != null && today.isBefore(allotmentDate) -> Stage.ALLOTMENT
        listingDate != null && !today.isAfter(listingDate) -> Stage.LISTING
        // Closed with no dates published yet — still recent enough to be worth tracking.
        allotmentDate == null && listingDate == null &&
            closeDate != null && closeDate.plusDays(AWAITING_GRACE_DAYS) >= today -> Stage.ALLOTMENT
        else -> Stage.CLOSED
    }

    /** Open, or closed with allotment / listing still ahead. */
    fun isActive(today: LocalDate = LocalDate.now()): Boolean =
        stage(today) in setOf(Stage.OPEN, Stage.ALLOTMENT, Stage.LISTING)

    /** Share of the issue that is fresh capital rather than promoters selling out. */
    val freshIssueSharePct: Double?
        get() {
            val fresh = freshIssueCr ?: return null
            val total = issueSizeCr ?: ((freshIssueCr) + (ofsCr ?: 0.0))
            if (total <= 0.0) return null
            return fresh / total * 100.0
        }
}

/** Registrars whose allotment-status pages we deep-link into. */
enum class Registrar(val displayName: String, val allotmentUrl: String) {
    // linkintime.co.in no longer resolves — the registrar moved to MUFG Intime.
    LINK_INTIME(
        "MUFG Intime (Link Intime)",
        "https://in.mpms.mufg.com/Initial_Offer/public-issues.html"
    ),
    KFINTECH(
        "KFin Technologies",
        "https://ris.kfintech.com/ipostatus/"
    ),
    BIGSHARE(
        "Bigshare Services",
        "https://ipo.bigshareonline.com/ipo_status.html"
    ),
    MAASHITLA(
        "Maashitla Securities",
        "https://maashitla.com/allotment-status/public-issues"
    ),
    SKYLINE(
        "Skyline Financial Services",
        "https://www.skylinerta.com/ipo.php"
    ),
    CAMEO(
        "Cameo Corporate Services",
        "https://ipo.cameoindia.com/"
    ),
    PURVA(
        "Purva Sharegistry",
        "https://www.purvashare.com/investor-service/ipo-query"
    );

    companion object {
        /** Best-effort match of a free-text registrar name coming from the feed. */
        fun match(raw: String?): Registrar? {
            val s = raw?.lowercase()?.trim().orEmpty()
            if (s.isEmpty()) return null
            return when {
                s.contains("link") || s.contains("intime") || s.contains("mufg") -> LINK_INTIME
                s.contains("kfin") || s.contains("karvy") -> KFINTECH
                s.contains("bigshare") -> BIGSHARE
                s.contains("maashitla") -> MAASHITLA
                s.contains("skyline") -> SKYLINE
                s.contains("cameo") -> CAMEO
                s.contains("purva") -> PURVA
                else -> null
            }
        }
    }
}
