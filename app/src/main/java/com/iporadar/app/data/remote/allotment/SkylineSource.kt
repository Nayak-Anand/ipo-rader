package com.iporadar.app.data.remote.allotment

import com.iporadar.app.data.model.AllotmentResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * Skyline Financial Services.
 *
 * Their lookup is a plain three-step form: load the page for a session, POST the
 * company to reach the search form, then POST the PAN with that form's CSRF token.
 * The CSRF token is an ordinary same-session anti-forgery field — it is handed to
 * whoever loads the form — not a barrier to automation, and there is no captcha.
 */
class SkylineSource(
    private val client: OkHttpClient
) : AllotmentSource {

    override val registrarName = "Skyline Financial Services"

    /** normalised name -> option id, for matching. */
    private var companies: Map<String, String> = emptyMap()
    /** display label -> option id, for the picker. */
    private var labels: Map<String, String> = emptyMap()
    private var fetchedAt = 0L

    override suspend fun resolve(ipoName: String): String? = withContext(Dispatchers.IO) {
        matchCompany(ipoName, companies())
    }

    override suspend fun catalogue(): Map<String, String> = withContext(Dispatchers.IO) {
        companies()
        labels
    }

    override suspend fun check(companyKey: String, pan: String): AllotmentResult =
        withContext(Dispatchers.IO) {
            try {
                // Step 1 — session cookie.
                get(IPO_PAGE)

                // Step 2 — the search form for this company, which carries the token.
                val formHtml = post(
                    SEARCH_PAGE,
                    FormBody.Builder().add("company", companyKey).build()
                ) ?: return@withContext AllotmentResult.Failed("Registrar respond nahi kar raha")

                val token = Jsoup.parse(formHtml)
                    .selectFirst("input[name=csrf_token]")
                    ?.attr("value")
                    .orEmpty()

                // Step 3 — the actual lookup.
                val resultHtml = post(
                    SEARCH_PAGE,
                    FormBody.Builder()
                        .add("company", companyKey)
                        .add("csrf_token", token)
                        .add("action", "search")
                        .add("pan", pan)
                        .add("application_no", "")
                        .add("client_id", "")
                        .build()
                ) ?: return@withContext AllotmentResult.Failed("Registrar respond nahi kar raha")

                parse(resultHtml)
            } catch (e: java.net.UnknownHostException) {
                AllotmentResult.Failed("Internet nahi mil raha")
            } catch (e: Exception) {
                AllotmentResult.Failed(e.message ?: "Check nahi ho paya")
            }
        }

    private suspend fun companies(): Map<String, String> = withContext(Dispatchers.IO) {
        if (companies.isNotEmpty() && System.currentTimeMillis() - fetchedAt < TTL_MS) {
            return@withContext companies
        }
        val html = get(IPO_PAGE) ?: return@withContext companies

        val options = Jsoup.parse(html)
            .select("select[name=company] option")
            .mapNotNull { option ->
                val id = option.attr("value").trim()
                val label = option.text().trim()
                if (id.isEmpty() || label.isEmpty()) null else label to id
            }

        if (options.isNotEmpty()) {
            labels = options.toMap()
            companies = options.associate { (label, id) -> normaliseCompany(label) to id }
            fetchedAt = System.currentTimeMillis()
        }
        companies
    }

    /**
     * The result page renders either a "no record" notice or a table of the
     * application. Read it by label so a column reorder does not break the parse.
     */
    private fun parse(html: String): AllotmentResult {
        val doc = Jsoup.parse(html)
        val text = doc.body().text()

        if (NOT_FOUND.containsMatchIn(text)) return AllotmentResult.NoRecord

        val cells = mutableMapOf<String, String>()
        doc.select("table tr").forEach { row ->
            val tds = row.select("td, th")
            if (tds.size >= 2) {
                val label = tds[0].text().trim().lowercase()
                val value = tds[1].text().trim()
                if (label.isNotEmpty() && value.isNotEmpty()) cells[label] = value
            }
        }
        if (cells.isEmpty()) return AllotmentResult.NoRecord

        fun find(vararg needles: String): String? =
            cells.entries.firstOrNull { entry -> needles.any { entry.key.contains(it) } }?.value

        val shares = find("allot", "shares")?.filter { it.isDigit() }?.toIntOrNull()
        val applicationNo = find("application")
        val name = find("name")

        return when {
            shares != null && shares > 0 -> AllotmentResult.Allotted(shares, applicationNo, name)
            shares != null -> AllotmentResult.NotAllotted(applicationNo)
            applicationNo != null -> AllotmentResult.NotAllotted(applicationNo)
            else -> AllotmentResult.NoRecord
        }
    }

    private fun get(url: String): String? =
        client.newCall(Request.Builder().url(url).header("User-Agent", UA).build())
            .execute()
            .use { if (it.isSuccessful) it.body?.string() else null }

    private fun post(url: String, body: FormBody): String? =
        client.newCall(
            Request.Builder()
                .url(url)
                .post(body)
                .header("User-Agent", UA)
                .header("Referer", IPO_PAGE)
                .build()
        ).execute().use { if (it.isSuccessful) it.body?.string() else null }

    private companion object {
        const val IPO_PAGE = "https://www.skylinerta.com/ipo.php"
        const val SEARCH_PAGE = "https://www.skylinerta.com/display_application.php"
        const val TTL_MS = 30 * 60 * 1000L
        const val UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"
        val NOT_FOUND = Regex("(?i)no record found|could not find any application")
    }
}
