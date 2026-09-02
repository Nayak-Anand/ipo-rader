package com.iporadar.app.data.remote.allotment

import com.iporadar.app.data.model.AllotmentResult
import com.iporadar.app.data.remote.MaashitlaApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Maashitla Securities — a plain JSON lookup, unauthenticated and CORS-open, the
 * same call their own site makes. 200 carries a record, 404 means there is none.
 */
class MaashitlaSource(
    private val api: MaashitlaApi
) : AllotmentSource {

    override val registrarName = "Maashitla Securities"

    private var companies: Map<String, String> = emptyMap()
    private var fetchedAt = 0L

    override suspend fun resolve(ipoName: String): String? = withContext(Dispatchers.IO) {
        matchCompany(ipoName, companies())
    }

    override suspend fun catalogue(): Map<String, String> = withContext(Dispatchers.IO) {
        // The API is keyed by the company name itself, so label and key are the same.
        companies().values.associateWith { it }
    }

    override suspend fun check(companyKey: String, pan: String): AllotmentResult =
        withContext(Dispatchers.IO) {
            try {
                val response = api.search(companyName = companyKey, pan = pan)
                when {
                    response.code() == 404 -> AllotmentResult.NoRecord
                    !response.isSuccessful ->
                        AllotmentResult.Failed("Registrar ne error diya (${response.code()})")
                    else -> parse(response.body())
                }
            } catch (e: java.net.UnknownHostException) {
                AllotmentResult.Failed("Internet nahi mil raha")
            } catch (e: Exception) {
                AllotmentResult.Failed(e.message ?: "Check nahi ho paya")
            }
        }

    private suspend fun companies(): Map<String, String> {
        if (companies.isNotEmpty() && System.currentTimeMillis() - fetchedAt < TTL_MS) {
            return companies
        }
        return try {
            val parsed = api.companies()
                .mapNotNull { it.companyName?.trim() }
                .filter { it.isNotEmpty() }
                .associateBy { normaliseCompany(it) }
            if (parsed.isNotEmpty()) {
                companies = parsed
                fetchedAt = System.currentTimeMillis()
            }
            parsed
        } catch (_: Exception) {
            companies
        }
    }

    /** The success shape is undocumented, so every field is read by trying aliases. */
    private fun parse(body: JsonElement?): AllotmentResult {
        val row = when (body) {
            is JsonArray -> body.firstOrNull() as? JsonObject
            is JsonObject -> (body["data"] as? JsonObject)
                ?: ((body["data"] as? JsonArray)?.firstOrNull() as? JsonObject)
                ?: body
            else -> null
        } ?: return AllotmentResult.NoRecord

        val shares = row.int("alloted_shares", "allotted_shares", "shares_allotted", "shares", "qty")
        val applicationNo = row.str("application_no", "application_number", "app_no", "applicationNo")
        val holder = row.str("name", "applicant_name", "investor_name", "holder_name")

        return when {
            shares != null && shares > 0 -> AllotmentResult.Allotted(shares, applicationNo, holder)
            shares != null -> AllotmentResult.NotAllotted(applicationNo)
            applicationNo != null -> AllotmentResult.NotAllotted(applicationNo)
            else -> AllotmentResult.NoRecord
        }
    }

    private fun JsonObject.str(vararg keys: String): String? {
        for (key in keys) {
            val v = (this[key] as? JsonPrimitive)?.content?.trim()
            if (!v.isNullOrEmpty() && !v.equals("null", true)) return v
        }
        return null
    }

    private fun JsonObject.int(vararg keys: String): Int? =
        str(*keys)?.replace(",", "")?.toDoubleOrNull()?.toInt()

    private companion object {
        const val TTL_MS = 30 * 60 * 1000L
    }
}
