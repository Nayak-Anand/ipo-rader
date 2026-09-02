package com.iporadar.app.data.model

/**
 * Result of checking one PAN against one IPO.
 *
 * Only some registrars expose a check that can be automated. Where they don't
 * (captcha or bot defence), we say so plainly rather than pretending — see
 * [NotSupported].
 */
sealed interface AllotmentResult {

    /** Not checked yet. */
    data object Idle : AllotmentResult

    data object Checking : AllotmentResult

    /** Shares were allotted. */
    data class Allotted(
        val shares: Int?,
        val applicationNo: String?,
        val holderName: String?
    ) : AllotmentResult

    /** A record exists for this PAN, but no shares were allotted. */
    data class NotAllotted(
        val applicationNo: String?
    ) : AllotmentResult

    /**
     * The registrar has no record for this PAN — usually means no application was
     * made. Registrars do not distinguish this from "applied but rejected", so the
     * wording stays neutral.
     */
    data object NoRecord : AllotmentResult

    /** This IPO's registrar blocks automated checks; the user must open its site. */
    data object NotSupported : AllotmentResult

    data class Failed(val message: String) : AllotmentResult

    val isTerminal: Boolean
        get() = this !is Idle && this !is Checking
}

/** One row on the allotment screen: a saved PAN plus its latest result. */
data class PanCheck(
    val pan: PanEntry,
    val result: AllotmentResult = AllotmentResult.Idle
)
