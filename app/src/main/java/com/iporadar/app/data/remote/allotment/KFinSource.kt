package com.iporadar.app.data.remote.allotment

import com.iporadar.app.data.model.AllotmentResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * KFin Technologies.
 *
 * Their allotment app at ipostatus.kfintech.com is a plain SPA calling a public API
 * gateway — no captcha, no bot defence, and the response carries
 * `Access-Control-Allow-Origin: *`. (Their *corporate* site, ris.kfintech.com, does
 * sit behind bot defence, but that is a different host and not what serves lookups.)
 *
 * The company list is not an endpoint: it is baked into the SPA bundle as a JSON
 * literal, so we read it from there. The bundle filename carries a content hash that
 * changes on every deploy, which is why the index page is fetched first to find it.
 */
class KFinSource(
    private val client: OkHttpClient,
    private val json: Json
) : AllotmentSource {

    override val registrarName = "KFin Technologies"

    /** normalised name -> clientId */
    private var companies: Map<String, String> = emptyMap()
    /** display label -> clientId */
    private var labels: Map<String, String> = emptyMap()
    private var fetchedAt = 0L

    override suspend fun resolve(ipoName: String): String? = withContext(Dispatchers.IO) {
        matchCompany(ipoName, load())
    }

    override suspend fun catalogue(): Map<String, String> = withContext(Dispatchers.IO) {
        load()
        labels
    }

    override suspend fun check(companyKey: String, pan: String): AllotmentResult =
        withContext(Dispatchers.IO) {
            try {
                val body = get(
                    url = "$API_BASE?type=pan",
                    headers = mapOf(
                        "reqparam" to pan,
                        "client_id" to companyKey,
                        "Origin" to ORIGIN,
                        "Referer" to "$ORIGIN/"
                    )
                ) ?: return@withContext AllotmentResult.Failed("Registrar respond nahi kar raha")

                parse(body)
            } catch (e: java.net.UnknownHostException) {
                AllotmentResult.Failed("Internet nahi mil raha")
            } catch (e: Exception) {
                AllotmentResult.Failed(e.message ?: "Check nahi ho paya")
            }
        }

    private fun load(): Map<String, String> {
        if (companies.isNotEmpty() && System.currentTimeMillis() - fetchedAt < TTL_MS) {
            return companies
        }
        return try {
            val index = get(ORIGIN) ?: return companies
            val scriptPath = SCRIPT.find(index)?.groupValues?.get(1) ?: return companies
            val bundle = get(ORIGIN + scriptPath) ?: return companies
            val literal = COMPANY_LIST.find(bundle)?.groupValues?.get(1) ?: return companies

            val parsed = json.decodeFromString(ListSerializer(KFinCompany.serializer()), literal)
                .mapNotNull { entry ->
                    val name = entry.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    val id = entry.clientId?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    name to id
                }

            if (parsed.isNotEmpty()) {
                labels = parsed.toMap()
                companies = parsed.associate { (name, id) -> normaliseCompany(name) to id }
                fetchedAt = System.currentTimeMillis()
            }
            companies
        } catch (_: Exception) {
            companies
        }
    }

    /**
     * `{"data":[{"All_Shares":"0","App_Shares":"50","Appln_No":"...","Name":"..."}]}`
     * — All_Shares is what was allotted, App_Shares what was applied for.
     */
    private fun parse(body: String): AllotmentResult {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
            ?: return AllotmentResult.NoRecord
        val row = (root["data"] as? JsonArray)?.firstOrNull() as? JsonObject
            ?: return AllotmentResult.NoRecord

        fun str(key: String): String? =
            (row[key] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }

        val allotted = str("All_Shares")?.toDoubleOrNull()?.toInt()
        val applied = str("App_Shares")?.toDoubleOrNull()?.toInt()
        val applicationNo = str("Appln_No")
        val name = str("Name")

        return when {
            allotted != null && allotted > 0 -> AllotmentResult.Allotted(allotted, applicationNo, name)
            allotted != null || applied != null -> AllotmentResult.NotAllotted(applicationNo)
            else -> AllotmentResult.NoRecord
        }
    }

    private fun get(url: String, headers: Map<String, String> = emptyMap()): String? {
        val builder = Request.Builder().url(url).header("User-Agent", UA)
        headers.forEach { (k, v) -> builder.header(k, v) }
        return client.newCall(builder.build()).execute()
            .use { if (it.isSuccessful) it.body?.string() else null }
    }

    @Serializable
    private data class KFinCompany(
        @SerialName("clientId") val clientId: String? = null,
        @SerialName("name") val name: String? = null
    )

    private companion object {
        const val ORIGIN = "https://ipostatus.kfintech.com"
        const val API_BASE =
            "https://0uz601ms56.execute-api.ap-south-1.amazonaws.com/prod/api/query"
        const val TTL_MS = 60 * 60 * 1000L
        const val UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"

        /** The bundle name carries a content hash, so read it off the index page. */
        val SCRIPT = Regex("""src="\.?(/static/js/main\.[A-Za-z0-9]+\.js)"""")
        val COMPANY_LIST = Regex("""JSON\.parse\('(\[\{"clientId".*?\}])'\)""")
    }
}
