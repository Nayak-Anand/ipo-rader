package com.iporadar.app.data.repo

import com.iporadar.app.data.model.AllotmentResult
import com.iporadar.app.data.model.Ipo
import com.iporadar.app.data.model.PanEntry
import com.iporadar.app.data.remote.allotment.AllotmentSource

/** A company a registrar will answer for, offered when our own list has no match. */
data class CheckableCompany(
    val label: String,
    val registrarName: String,
    val key: String
)

/**
 * Checks allotment against whichever registrar actually holds the issue.
 *
 * Sources are asked in order whether they hold the company; the first that says yes
 * performs the lookup. Registrars that put a human check in front of a lookup are
 * deliberately absent — Bigshare, MUFG and Cameo all verify a captcha server-side
 * (Bigshare's own page says so, and Cameo answers differently to a wrong one). For
 * those the UI hands off to the registrar's own page instead of pretending.
 */
class AllotmentRepository(
    private val sources: List<AllotmentSource>
) {

    /** Cache of which source handles which IPO, so we ask once per IPO, not per PAN. */
    private val resolved = mutableMapOf<String, Pair<AllotmentSource, String>?>()

    suspend fun supports(ipo: Ipo): Boolean = sourceFor(ipo) != null

    /** Name of the registrar that will answer, when one will. */
    suspend fun registrarFor(ipo: Ipo): String? = sourceFor(ipo)?.first?.registrarName

    suspend fun check(ipo: Ipo, pan: PanEntry): AllotmentResult {
        val (source, companyKey) = sourceFor(ipo) ?: return AllotmentResult.NotSupported
        return source.check(companyKey, pan.pan)
    }

    /**
     * Every company these registrars can answer for, whether or not it is in our IPO
     * list. NSE plus a GMP report does not cover the whole SME market, so without
     * this the check would sit unusable for issues we simply never saw.
     */
    suspend fun registrarCatalogue(): List<CheckableCompany> = sources.flatMap { source ->
        runCatching { source.catalogue() }.getOrDefault(emptyMap())
            .map { (label, key) -> CheckableCompany(label, source.registrarName, key) }
    }.sortedBy { it.label }

    suspend fun checkDirect(company: CheckableCompany, pan: PanEntry): AllotmentResult {
        val source = sources.firstOrNull { it.registrarName == company.registrarName }
            ?: return AllotmentResult.NotSupported
        return source.check(company.key, pan.pan)
    }

    private suspend fun sourceFor(ipo: Ipo): Pair<AllotmentSource, String>? {
        resolved[ipo.id]?.let { return it }
        if (resolved.containsKey(ipo.id)) return null

        for (source in sources) {
            val key = runCatching { source.resolve(ipo.name) }.getOrNull()
            if (key != null) {
                val hit = source to key
                resolved[ipo.id] = hit
                return hit
            }
        }
        // Remember the miss too — otherwise every PAN re-asks every registrar.
        resolved[ipo.id] = null
        return null
    }
}
