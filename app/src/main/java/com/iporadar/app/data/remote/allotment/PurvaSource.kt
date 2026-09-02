package com.iporadar.app.data.remote.allotment

import com.iporadar.app.data.model.AllotmentResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * Purva Sharegistry.
 *
 * A plain Django form: load the page for a session and CSRF token, then POST the
 * company id with the PAN. The page's stylesheet still carries leftover
 * `.captcha-refresh` rules, but there is no captcha input and no reCAPTCHA script —
 * the form is company, application number and PAN, nothing else.
 */
class PurvaSource(
    private val client: OkHttpClient
) : AllotmentSource {

    override val registrarName = "Purva Sharegistry"

    /** normalised name -> company_id */
    private var companies: Map<String, String> = emptyMap()
    /** display label -> company_id */
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
                val page = get(QUERY_PAGE)
                    ?: return@withContext AllotmentResult.Failed("Registrar respond nahi kar raha")
                val token = Jsoup.parse(page)
                    .selectFirst("input[name=csrfmiddlewaretoken]")
                    ?.attr("value")
                    .orEmpty()

                val result = post(
                    QUERY_PAGE,
                    FormBody.Builder()
                        .add("csrfmiddlewaretoken", token)
                        .add("company_id", companyKey)
                        .add("applicationNumber", "")
                        .add("panNumber", pan)
                        .build()
                ) ?: return@withContext AllotmentResult.Failed("Registrar respond nahi kar raha")

                parse(result)
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
        val html = get(QUERY_PAGE) ?: return companies

        val options = Jsoup.parse(html)
            .select("select[name=company_id] option")
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
        return companies
    }

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
                .header("Referer", QUERY_PAGE)
                .header("Origin", "https://www.purvashare.com")
                .build()
        ).execute().use { if (it.isSuccessful) it.body?.string() else null }

    private companion object {
        const val QUERY_PAGE = "https://www.purvashare.com/investor-service/ipo-query"
        const val TTL_MS = 30 * 60 * 1000L
        const val UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"
        val NOT_FOUND = Regex("(?i)no record found|re-check your application")
    }
}
