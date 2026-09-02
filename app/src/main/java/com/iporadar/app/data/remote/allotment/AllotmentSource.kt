package com.iporadar.app.data.remote.allotment

import com.iporadar.app.data.model.AllotmentResult
import java.util.Locale

/**
 * One registrar we can query directly.
 *
 * A source is only added here if the registrar serves allotment lookups with no
 * human check in the way at all. Where a registrar shows a captcha or runs bot
 * defence — Bigshare, MUFG and Cameo, all of which verify a captcha server-side —
 * the intent is that a person completes each lookup, so those
 * are deliberately not implemented. Whether a given server currently enforces its
 * captcha strictly is beside the point; submitting a blank one to get past it is the
 * same act either way. The app links out to their own pages instead.
 */
interface AllotmentSource {

    /** Shown in the UI so the user knows who answered. */
    val registrarName: String

    /**
     * The registrar's own handle for this company, or null if they do not hold it.
     * Asking the registrar is more reliable than trusting a registrar field we may
     * not have — most SME issues reach us without one.
     */
    suspend fun resolve(ipoName: String): String?

    /**
     * Everything this registrar can answer for, as display-name to internal key.
     *
     * Our IPO list is built from NSE and a GMP report, and neither covers every SME
     * issue a registrar handles — so when nothing matches, the user can still pick
     * straight from the registrar's own list rather than hitting a dead end.
     */
    suspend fun catalogue(): Map<String, String>

    suspend fun check(companyKey: String, pan: String): AllotmentResult
}

/** Company names differ by suffix and punctuation across every source. */
internal fun normaliseCompany(name: String): String = name
    .lowercase(Locale.ENGLISH)
    .replace(Regex("\\b(limited|ltd|private|pvt|india|ipo|sme|the)\\b"), "")
    .replace(Regex("[^a-z0-9]"), "")

/** Loose match so "Foo Ltd" finds "FOO LIMITED" without matching unrelated names. */
internal fun matchCompany(target: String, candidates: Map<String, String>): String? {
    val key = normaliseCompany(target)
    if (key.length < 4) return null

    candidates[key]?.let { return it }

    val head = key.take(14)
    if (head.length < 6) return null
    return candidates.entries
        .firstOrNull { it.key.startsWith(head) || key.startsWith(it.key.take(14)) }
        ?.value
}
