package com.iporadar.app.data.remote

import com.iporadar.app.core.util.Fmt
import com.iporadar.app.data.model.FinancialYear
import com.iporadar.app.data.model.Gmp
import com.iporadar.app.data.model.GmpPoint
import com.iporadar.app.data.model.Ipo
import com.iporadar.app.data.model.IpoBoard
import com.iporadar.app.data.model.IpoStatus
import com.iporadar.app.data.model.Registrar
import com.iporadar.app.data.model.Subscription
import com.iporadar.app.data.model.Valuation
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.util.Locale

/* ---------------------------------------------------------------- feed -> domain */

fun IpoDto.toDomain(): Ipo? {
    val displayName = name?.trim()?.takeIf { it.isNotEmpty() }
        ?: symbol?.trim()?.takeIf { it.isNotEmpty() }
        ?: return null
    val sym = symbol?.trim()?.uppercase(Locale.ENGLISH).orEmpty()
    val open = Fmt.parseDate(openDate)
    val close = Fmt.parseDate(closeDate)
    val listing = Fmt.parseDate(listingDate)

    return Ipo(
        id = id?.takeIf { it.isNotBlank() } ?: slugKey(displayName, sym),
        name = displayName,
        symbol = sym,
        board = parseBoard(board),
        status = parseStatus(status) ?: deriveStatus(open, close, listing),
        logoUrl = logoUrl?.takeIf { it.isNotBlank() },
        priceMin = priceMin,
        priceMax = priceMax,
        lotSize = lotSize,
        issueSizeCr = issueSizeCr,
        freshIssueCr = freshIssueCr,
        ofsCr = ofsCr,
        openDate = open,
        closeDate = close,
        allotmentDate = Fmt.parseDate(allotmentDate),
        refundDate = Fmt.parseDate(refundDate),
        listingDate = listing,
        registrar = Registrar.match(registrar),
        registrarName = registrar?.takeIf { it.isNotBlank() },
        exchange = exchange?.takeIf { it.isNotBlank() },
        about = about?.takeIf { it.isNotBlank() },
        strengths = strengths.map { it.trim() }.filter { it.isNotEmpty() },
        risks = risks.map { it.trim() }.filter { it.isNotEmpty() },
        financials = financials.mapNotNull { it.toDomain() },
        valuation = valuation?.toDomain(),
        listingPrice = listingPrice,
        gmp = gmp?.toDomain(),
        subscription = subscription?.toDomain()
    )
}

fun GmpDto.toDomain(): Gmp? {
    val p = premium ?: return null
    return Gmp(
        premium = p,
        kostak = kostak,
        subjectToSauda = subjectToSauda,
        updatedAt = updatedAt,
        history = history.mapNotNull { pt ->
            val d = pt.date ?: return@mapNotNull null
            val v = pt.premium ?: return@mapNotNull null
            GmpPoint(d, v)
        }
    )
}

fun SubscriptionDto.toDomain(): Subscription? {
    val anyValue = listOfNotNull(qib, nii, niiSmall, niiBig, retail, employee, total)
    if (anyValue.isEmpty()) return null
    return Subscription(
        qib = qib,
        // If only the two NII halves are reported, show their combined figure too.
        nii = nii ?: averageOrNull(niiSmall, niiBig),
        niiSmall = niiSmall,
        niiBig = niiBig,
        retail = retail,
        employee = employee,
        total = total,
        updatedAt = updatedAt
    )
}

private fun averageOrNull(vararg values: Double?): Double? {
    val present = values.filterNotNull()
    if (present.isEmpty()) return null
    return present.sum() / present.size
}

fun FinancialYearDto.toDomain(): FinancialYear? {
    val label = period?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (revenueCr == null && patCr == null && netWorthCr == null && borrowingsCr == null) return null
    return FinancialYear(label, revenueCr, patCr, netWorthCr, borrowingsCr)
}

fun ValuationDto.toDomain(): Valuation? {
    val v = Valuation(peRatio, industryPe, eps, ronwPct, marketCapCr)
    return if (v.isEmpty) null else v
}

/* ----------------------------------------------------------------- NSE -> domain */

/**
 * NSE ships arrays of loosely-typed objects and renames fields between releases,
 * so every read here goes through alias lists and stays null-tolerant.
 */
fun JsonElement.nseToIpos(forcedStatus: IpoStatus? = null): List<Ipo> {
    val array = asArrayOrNull() ?: return emptyList()
    return array.mapNotNull { element ->
        val o = element as? JsonObject ?: return@mapNotNull null
        val name = o.str("companyName", "company", "issuerName", "name")
            ?: o.str("symbol") ?: return@mapNotNull null
        val symbol = o.str("symbol", "series_symbol").orEmpty().uppercase(Locale.ENGLISH)

        // public-past-issues uses ipoStartDate/ipoEndDate; the live endpoints use issue*.
        val open = Fmt.parseDate(o.str("issueStartDate", "ipoStartDate", "biddingStartDate", "startDate"))
        val close = Fmt.parseDate(o.str("issueEndDate", "ipoEndDate", "biddingEndDate", "endDate"))
        val listing = Fmt.parseDate(o.str("listingDate", "dateOfListing"))

        val priceRaw = o.str("issuePrice", "priceRange", "priceBand", "price")
        val prices = Fmt.numbers(priceRaw)

        // NSE spells the bid field with a lowercase s ("noOfsharesBid") and reports
        // the multiple as "noOfTime" — both differ from every other endpoint.
        val subscriptionTimes = o.num("noOfTime", "noOfTimesIssueSubscribed", "timesSubscribed")
        val sharesOffered = o.num("noOfSharesOffered", "noOfShareOffered")
        val sharesBid = o.num("noOfsharesBid", "noOfSharesBid")
        val derivedTimes = subscriptionTimes
            ?: if (sharesOffered != null && sharesOffered > 0 && sharesBid != null) {
                sharesBid / sharesOffered
            } else null

        Ipo(
            id = slugKey(name, symbol),
            name = name.trim(),
            symbol = symbol,
            board = if (o.str("series", "category")?.contains("SME", true) == true ||
                o.str("issueType")?.contains("SME", true) == true
            ) IpoBoard.SME else IpoBoard.MAINBOARD,
            status = forcedStatus
                ?: parseStatus(o.str("status"))
                ?: deriveStatus(open, close, listing),
            priceMin = prices.firstOrNull(),
            priceMax = prices.lastOrNull(),
            lotSize = o.num("lotSize", "marketLot", "minBidQuantity")?.toInt(),
            // On these endpoints "issueSize" is a share count, not rupees — convert
            // it with the cut-off price, and only then to crore.
            issueSizeCr = o.num("issueSize")?.let { size ->
                val cutOff = prices.lastOrNull()
                when {
                    cutOff != null && cutOff > 0 -> size * cutOff / 1e7
                    size > 100_000 -> size / 1e7
                    else -> size
                }
            },
            openDate = open,
            closeDate = close,
            listingDate = listing,
            exchange = when (o.str("isBse")) {
                "1" -> "BSE, NSE"
                else -> "NSE"
            },
            subscription = derivedTimes?.let { Subscription(total = it) }
        )
    }
}

/**
 * What `/api/ipo-detail` adds on top of the list endpoints. Every field is optional
 * because NSE fills this page in gradually as an issue approaches its open date.
 */
data class NseIpoDetail(
    val lotSize: Int? = null,
    val registrarName: String? = null,
    val priceMin: Double? = null,
    val priceMax: Double? = null,
    val faceValue: Double? = null,
    val subscription: Subscription? = null
) {
    val isEmpty: Boolean
        get() = lotSize == null && registrarName == null && priceMax == null && subscription == null
}

/**
 * `issueInfo.dataList` is a flat list of {title, value} rows — the same table NSE
 * renders on the web page — so we look values up by their label.
 * `activeCat.dataList` carries the category-wise bid table, keyed by Sr.No.
 */
fun JsonElement.nseToDetail(): NseIpoDetail {
    val root = this as? JsonObject ?: return NseIpoDetail()

    val info = (root["issueInfo"] as? JsonObject)?.get("dataList")?.asArrayOrNull()
        ?.mapNotNull { row ->
            val o = row as? JsonObject ?: return@mapNotNull null
            val title = (o["title"] as? JsonPrimitive)?.content?.trim() ?: return@mapNotNull null
            val value = (o["value"] as? JsonPrimitive)?.content?.trim().orEmpty()
            title to value
        }
        ?.toMap()
        .orEmpty()

    fun info(vararg labels: String): String? {
        for (label in labels) {
            val hit = info.entries.firstOrNull { it.key.equals(label, ignoreCase = true) }
                ?: info.entries.firstOrNull { it.key.contains(label, ignoreCase = true) }
            val v = hit?.value?.trim()?.trim('"')
            if (!v.isNullOrBlank() && !v.equals("Not applicable", true)) return v
        }
        return null
    }

    val prices = Fmt.numbers(info("Price Range", "Price Band"))

    return NseIpoDetail(
        lotSize = Fmt.firstNumber(info("Bid Lot", "Minimum Order Quantity", "Market Lot"))?.toInt(),
        registrarName = info("Name of the Registrar", "Registrar"),
        priceMin = prices.firstOrNull(),
        priceMax = prices.lastOrNull(),
        faceValue = Fmt.firstNumber(info("Face Value")),
        subscription = (root["activeCat"] as? JsonObject)?.get("dataList")?.asArrayOrNull()
            ?.toSubscription()
    )
}

private fun JsonArray.toSubscription(): Subscription? {
    var qib: Double? = null
    var nii: Double? = null
    var niiBig: Double? = null
    var niiSmall: Double? = null
    var retail: Double? = null
    var employee: Double? = null
    var total: Double? = null

    for (element in this) {
        val row = element as? JsonObject ?: continue
        val srNo = (row["srNo"] as? JsonPrimitive)?.content?.trim()
        val category = (row["category"] as? JsonPrimitive)?.content?.trim().orEmpty()
        // The first row repeats the column headings.
        if (srNo == "Sr.No." || category.equals("Category", true)) continue

        val times = (row["noOfTotalMeant"] as? JsonPrimitive)?.content
            ?.trim()?.toDoubleOrNull()

        when {
            category.equals("Total", true) -> total = times
            srNo == "1" -> qib = times
            srNo == "2" -> nii = times
            srNo == "2.1" -> niiBig = times
            srNo == "2.2" -> niiSmall = times
            srNo == "3" -> retail = times
            category.contains("Employee", true) -> employee = times
        }
    }

    if (listOfNotNull(qib, nii, niiBig, niiSmall, retail, employee, total).isEmpty()) return null
    return Subscription(
        qib = qib,
        nii = nii,
        niiSmall = niiSmall,
        niiBig = niiBig,
        retail = retail,
        employee = employee,
        total = total
    )
}

private fun JsonElement.asArrayOrNull(): JsonArray? = when (this) {
    is JsonArray -> this
    is JsonObject -> listOf("data", "results", "activeIssues", "upcomingIssues")
        .firstNotNullOfOrNull { this[it] as? JsonArray }
    else -> null
}

private fun JsonObject.str(vararg keys: String): String? {
    for (k in keys) {
        val prim = this[k] as? JsonPrimitive ?: continue
        val v = prim.content.trim()
        if (v.isNotEmpty() && !v.equals("null", true) && v != "-") return v
    }
    return null
}

private fun JsonObject.num(vararg keys: String): Double? {
    val raw = str(*keys) ?: return null
    return raw.replace(",", "").replace("₹", "").trim().toDoubleOrNull()
        ?: Fmt.firstNumber(raw)
}

/* ------------------------------------------------------------------- shared bits */

internal fun slugKey(name: String, symbol: String): String {
    val base = symbol.ifBlank { name }
    return base.lowercase(Locale.ENGLISH).replace(Regex("[^a-z0-9]+"), "-").trim('-')
}

internal fun parseBoard(raw: String?): IpoBoard =
    if (raw?.contains("sme", true) == true) IpoBoard.SME else IpoBoard.MAINBOARD

internal fun parseStatus(raw: String?): IpoStatus? {
    val s = raw?.trim()?.lowercase(Locale.ENGLISH) ?: return null
    return when {
        s.contains("upcoming") || s.contains("forthcoming") -> IpoStatus.UPCOMING
        s.contains("open") || s.contains("active") || s.contains("current") -> IpoStatus.OPEN
        s.contains("listed") -> IpoStatus.LISTED
        s.contains("closed") || s.contains("allot") -> IpoStatus.CLOSED
        else -> null
    }
}

/** Falls back to the calendar when the source does not carry a usable status. */
internal fun deriveStatus(
    open: LocalDate?,
    close: LocalDate?,
    listing: LocalDate?,
    today: LocalDate = LocalDate.now()
): IpoStatus = when {
    listing != null && !today.isBefore(listing) -> IpoStatus.LISTED
    close != null && today.isAfter(close) -> IpoStatus.CLOSED
    open != null && today.isBefore(open) -> IpoStatus.UPCOMING
    open != null && close != null -> IpoStatus.OPEN
    open != null && !today.isBefore(open) -> IpoStatus.OPEN
    else -> IpoStatus.UPCOMING
}
