package com.iporadar.app.data.model

import kotlinx.serialization.Serializable
import java.util.Locale

enum class Relationship {
    SELF, SPOUSE, FATHER, MOTHER, SON, DAUGHTER, SIBLING, HUF, OTHER;

    val label: String
        get() = when (this) {
            SELF -> "Self"
            SPOUSE -> "Spouse"
            FATHER -> "Father"
            MOTHER -> "Mother"
            SON -> "Son"
            DAUGHTER -> "Daughter"
            SIBLING -> "Sibling"
            HUF -> "HUF"
            OTHER -> "Other"
        }
}

/**
 * One saved PAN in the vault.
 *
 * Families routinely apply from several demat accounts to improve allotment odds,
 * and every registrar page asks for the PAN by hand. Storing them here — on device
 * only, never uploaded — turns five lookups into five taps.
 */
@Serializable
data class PanEntry(
    val id: String,
    val pan: String,
    val holderName: String = "",
    val relationship: Relationship = Relationship.SELF
) {
    /** "ABCDE****F" — enough to recognise the row without exposing the whole number. */
    val masked: String
        get() = if (pan.length == 10) pan.take(5) + "****" + pan.last() else pan

    val displayName: String
        get() = holderName.ifBlank { relationship.label }

    val isValid: Boolean
        get() = PAN_PATTERN.matches(pan)

    companion object {
        /** Five letters, four digits, one letter — the format the Income Tax dept issues. */
        val PAN_PATTERN = Regex("^[A-Z]{5}[0-9]{4}[A-Z]$")

        fun normalise(raw: String): String =
            raw.trim().uppercase(Locale.ENGLISH).filter { it.isLetterOrDigit() }.take(10)
    }
}
