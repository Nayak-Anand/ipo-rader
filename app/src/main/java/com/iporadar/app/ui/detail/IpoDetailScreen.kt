package com.iporadar.app.ui.detail

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iporadar.app.core.util.Fmt
import com.iporadar.app.data.model.FinancialYear
import com.iporadar.app.data.model.Ipo
import com.iporadar.app.data.model.IpoStatus
import com.iporadar.app.data.model.Valuation
import com.iporadar.app.ui.IpoViewModel
import com.iporadar.app.ui.components.EmptyState
import com.iporadar.app.ui.components.GmpTrendChart
import com.iporadar.app.ui.components.LabeledValue
import com.iporadar.app.ui.components.NeutralChip
import com.iporadar.app.ui.components.SectionHeader
import com.iporadar.app.ui.components.StatusChip
import com.iporadar.app.ui.components.SubscriptionBars
import com.iporadar.app.ui.theme.LocalMarketColors
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IpoDetailScreen(
    ipoId: String,
    vm: IpoViewModel,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ipo = state.ipos.firstOrNull { it.id == ipoId }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = ipo?.name ?: "IPO",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                if (ipo != null) {
                    IconButton(onClick = {
                        val text = shareText(ipo)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(send, "Share IPO"))
                    }) {
                        Icon(Icons.Outlined.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = { vm.toggleWatch(ipo) }) {
                        Icon(
                            imageVector = if (ipo.id in state.watchlist) Icons.Filled.Bookmark
                            else Icons.Outlined.BookmarkBorder,
                            contentDescription = "Watchlist",
                            tint = if (ipo.id in state.watchlist) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            ),
            // The Scaffold above already consumed the status-bar inset; without this
            // the bar pads for it a second time and floats away from the top.
            windowInsets = WindowInsets(0, 0, 0, 0)
        )

        if (ipo == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "IPO nahi mila",
                    subtitle = "Ho sakta hai ye issue feed se hata diya gaya ho."
                )
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { HeaderRow(ipo) }
            item { GmpHeroCard(ipo) }
            item { KeyDetailsCard(ipo) }
            ipo.subscription?.let { subscription ->
                item {
                    DetailCard {
                        SectionHeader(
                            title = "Subscription",
                            trailing = subscription.updatedAt
                        )
                        Spacer(Modifier.height(12.dp))
                        SubscriptionBars(subscription)
                    }
                }
            }
            item { TimelineCard(ipo) }
            // Hoisted: a smart cast on ipo.gmp would not survive into the item lambda.
            val gmp = ipo.gmp
            if (gmp != null && gmp.history.isNotEmpty()) {
                item {
                    DetailCard {
                        SectionHeader(title = "GMP trend", trailing = gmp.updatedAt)
                        GmpTrendChart(points = gmp.history)
                    }
                }
            }
            val valuation = ipo.valuation
            if (valuation != null) {
                item { ValuationCard(valuation) }
            }
            if (ipo.financials.isNotEmpty()) {
                item { FinancialsCard(ipo.financials) }
            }
            if (ipo.strengths.isNotEmpty() || ipo.risks.isNotEmpty()) {
                item { StrengthsRisksCard(strengths = ipo.strengths, risks = ipo.risks) }
            }
            item { RegistrarCard(ipo) }
            val about = ipo.about
            if (!about.isNullOrBlank()) {
                item {
                    DetailCard {
                        SectionHeader(title = "Company ke baare me")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = about,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item { Disclaimer() }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun HeaderRow(ipo: Ipo) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusChip(ipo.stage())
        NeutralChip(ipo.board.label)
        ipo.exchange?.let { NeutralChip(it) }
        if (ipo.symbol.isNotBlank()) NeutralChip(ipo.symbol)
    }
}

@Composable
private fun GmpHeroCard(ipo: Ipo) {
    val market = LocalMarketColors.current
    val gmp = ipo.gmp
    val gain = gmp?.estimatedGainPct(ipo.priceMax)
    val accent = when {
        gain == null -> MaterialTheme.colorScheme.onSurfaceVariant
        gain >= 0 -> market.positive
        else -> market.negative
    }

    DetailCard {
        SectionHeader(
            title = if (ipo.status == IpoStatus.LISTED) "Listing performance" else "Grey Market Premium",
            trailing = gmp?.updatedAt
        )
        Spacer(Modifier.height(12.dp))

        if (ipo.status == IpoStatus.LISTED && ipo.listingPrice != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                LabeledValue("Issue price", Fmt.rupees(ipo.priceMax))
                LabeledValue("Listing price", Fmt.rupees(ipo.listingPrice))
                LabeledValue(
                    "Listing gain",
                    Fmt.pct(ipo.listingGainPct),
                    valueColor = if ((ipo.listingGainPct ?: 0.0) >= 0) market.positive else market.negative
                )
            }
            return@DetailCard
        }

        if (gmp == null) {
            Text(
                "Is IPO ke liye GMP abhi report nahi hua hai.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@DetailCard
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = Fmt.rupeesSigned(gmp.premium),
                style = MaterialTheme.typography.displaySmall,
                color = accent
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = Fmt.pct(gain),
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                modifier = Modifier.padding(bottom = 5.dp)
            )
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            LabeledValue(
                "Expected listing",
                Fmt.rupees(gmp.expectedListing(ipo.priceMax)),
                modifier = Modifier.weight(1f)
            )
            LabeledValue(
                "Profit / lot",
                Fmt.rupeesSigned(ipo.lotSize?.let { gmp.premium * it }),
                valueColor = accent,
                modifier = Modifier.weight(1f)
            )
            LabeledValue(
                "Issue price",
                Fmt.rupees(ipo.priceMax),
                modifier = Modifier.weight(1f)
            )
        }

        // The grey market also quotes a flat price per application (Kostak) and a
        // price paid only if the application actually gets allotment (Subject to Sauda).
        if (gmp.kostak != null || gmp.subjectToSauda != null) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                LabeledValue("Kostak", Fmt.rupees(gmp.kostak), Modifier.weight(1f))
                LabeledValue("Subject to Sauda", Fmt.rupees(gmp.subjectToSauda), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun KeyDetailsCard(ipo: Ipo) {
    DetailCard {
        SectionHeader(title = "Issue details")
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth()) {
                LabeledValue("Price band", ipo.priceBandLabel, Modifier.weight(1f))
                LabeledValue("Lot size", ipo.lotSize?.let { "$it shares" } ?: "—", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                LabeledValue("Min. investment", Fmt.rupees(ipo.minInvestment), Modifier.weight(1f))
                LabeledValue("Issue size", Fmt.crore(ipo.issueSizeCr), Modifier.weight(1f))
            }
            if (ipo.freshIssueCr != null || ipo.ofsCr != null) {
                Row(Modifier.fillMaxWidth()) {
                    LabeledValue("Fresh issue", Fmt.crore(ipo.freshIssueCr), Modifier.weight(1f))
                    LabeledValue("Offer for sale", Fmt.crore(ipo.ofsCr), Modifier.weight(1f))
                }
                val freshShare = ipo.freshIssueSharePct
                if (freshShare != null) {
                    Text(
                        text = freshShareNote(freshShare),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(Modifier.fillMaxWidth()) {
                LabeledValue(
                    "sHNI (2 lakh+)",
                    lotsFor(ipo, 200_000.0),
                    Modifier.weight(1f)
                )
                LabeledValue(
                    "bHNI (10 lakh+)",
                    lotsFor(ipo, 1_000_000.0),
                    Modifier.weight(1f)
                )
            }
        }
    }
}

/** Smallest application that clears the HNI threshold, in lots and rupees. */
private fun lotsFor(ipo: Ipo, threshold: Double): String {
    val perLot = ipo.minInvestment ?: return "—"
    if (perLot <= 0.0) return "—"
    val lots = Math.ceil(threshold / perLot).toInt().coerceAtLeast(1)
    return "$lots lots · ${Fmt.rupees(lots * perLot)}"
}

@Composable
private fun TimelineCard(ipo: Ipo) {
    val steps = listOf(
        "IPO opens" to ipo.openDate,
        "IPO closes" to ipo.closeDate,
        "Allotment" to ipo.allotmentDate,
        "Refund initiation" to ipo.refundDate,
        "Listing" to ipo.listingDate
    ).filter { it.second != null }

    if (steps.isEmpty()) return
    val today = LocalDate.now()
    val market = LocalMarketColors.current

    DetailCard {
        SectionHeader(title = "Timeline")
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            steps.forEachIndexed { index, (label, date) ->
                val done = date != null && !date.isAfter(today)
                val dotColor = if (done) market.positive else MaterialTheme.colorScheme.outline
                Row(verticalAlignment = Alignment.Top) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        if (index != steps.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(30.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.padding(bottom = if (index == steps.lastIndex) 0.dp else 14.dp)) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (done) FontWeight.Normal else FontWeight.SemiBold
                        )
                        Text(
                            text = Fmt.date(date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RegistrarCard(ipo: Ipo) {
    val context = LocalContext.current
    val registrar = ipo.registrar
    DetailCard {
        SectionHeader(title = "Allotment")
        Spacer(Modifier.height(8.dp))
        Text(
            text = registrar?.displayName ?: ipo.registrarName ?: "Registrar abhi announce nahi hua",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        if (registrar != null) {
            Button(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, registrar.allotmentUrl.toUri()))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Allotment status check karein")
            }
        } else {
            OutlinedButton(onClick = { }, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text("Registrar TBA")
            }
        }
    }
}

@Composable
private fun ValuationCard(valuation: Valuation) {
    val market = LocalMarketColors.current
    DetailCard {
        SectionHeader(title = "Valuation")
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth()) {
                LabeledValue("P/E ratio", decimal(valuation.peRatio), Modifier.weight(1f))
                LabeledValue("Industry P/E", decimal(valuation.industryPe), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                // EPS is a per-share figure — rounding it to whole rupees loses the point.
                LabeledValue("EPS", rupeeDecimal(valuation.eps), Modifier.weight(1f))
                LabeledValue("RoNW", percentPlain(valuation.ronwPct), Modifier.weight(1f))
            }
            if (valuation.marketCapCr != null) {
                LabeledValue("Market cap (post-issue)", Fmt.crore(valuation.marketCapCr))
            }
        }

        val verdict = valuation.versusIndustry
        if (verdict != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (verdict < 0) {
                    "Industry average se sasta priced hai."
                } else {
                    "Industry average se mehenga priced hai."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (verdict < 0) market.positive else market.warning
            )
        }
    }
}

/**
 * Metrics down the side, years across — the layout every prospectus summary uses.
 * Scrolls horizontally so four or five years never squeeze the labels.
 */
@Composable
private fun FinancialsCard(years: List<FinancialYear>) {
    val rows = financialRows.filter { row -> years.any { row.pick(it) != null } }
    if (rows.isEmpty()) return
    val market = LocalMarketColors.current

    DetailCard {
        SectionHeader(title = "Financials", trailing = "₹ crore")
        Spacer(Modifier.height(12.dp))

        Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Row {
                Spacer(Modifier.width(104.dp))
                years.forEach { year ->
                    Text(
                        text = year.period,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(84.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            rows.forEach { row ->
                Row(
                    modifier = Modifier.padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = row.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(104.dp)
                    )
                    years.forEach { year ->
                        val value = row.pick(year)
                        Text(
                            text = if (value == null) "—" else plainCrore(value),
                            style = MaterialTheme.typography.titleSmall,
                            color = when {
                                value == null || !row.signed -> MaterialTheme.colorScheme.onSurface
                                value >= 0 -> market.positive
                                else -> market.negative
                            },
                            modifier = Modifier.width(84.dp)
                        )
                    }
                }
            }
        }
    }
}

private class FinancialRow(
    val label: String,
    val signed: Boolean,
    val pick: (FinancialYear) -> Double?
)

/** Profit is the only row where the sign carries meaning, so only it gets colour. */
private val financialRows = listOf(
    FinancialRow("Revenue", false) { it.revenueCr },
    FinancialRow("Profit (PAT)", true) { it.patCr },
    FinancialRow("Net worth", false) { it.netWorthCr },
    FinancialRow("Borrowings", false) { it.borrowingsCr }
)

@Composable
private fun StrengthsRisksCard(strengths: List<String>, risks: List<String>) {
    val market = LocalMarketColors.current
    DetailCard {
        SectionHeader(title = "Strengths aur risks")
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (strengths.isNotEmpty()) {
                BulletGroup("Strengths", strengths, "+", market.positive)
            }
            if (risks.isNotEmpty()) {
                BulletGroup("Risk factors", risks, "!", market.negative)
            }
        }
    }
}

@Composable
private fun BulletGroup(
    heading: String,
    items: List<String>,
    marker: String,
    markerColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text = heading.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        items.forEach { line ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = marker,
                    style = MaterialTheme.typography.titleSmall,
                    color = markerColor,
                    modifier = Modifier.width(18.dp)
                )
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun decimal(value: Double?): String =
    value?.let { String.format(Locale.ENGLISH, "%.2f", it) } ?: "—"

private fun rupeeDecimal(value: Double?): String =
    value?.let { "₹" + String.format(Locale.ENGLISH, "%,.2f", it) } ?: "—"

private fun percentPlain(value: Double?): String =
    value?.let { String.format(Locale.ENGLISH, "%.1f", it) + "%" } ?: "—"

private fun plainCrore(value: Double): String =
    String.format(Locale.ENGLISH, "%,.0f", value)

private fun freshShareNote(pct: Double): String {
    val rounded = pct.toInt()
    return "$rounded% paisa company me jaata hai, baaki selling shareholders ko."
}

@Composable
private fun Disclaimer() {
    Text(
        text = "GMP grey market ka unofficial indicator hai — na SEBI regulated hai, na listing price ki " +
            "guarantee. Ye app sirf information ke liye hai, investment advice nahi.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun DetailCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

private fun shareText(ipo: Ipo): String = buildString {
    appendLine("${ipo.name} IPO")
    appendLine("Status: ${ipo.status.label} (${ipo.board.label})")
    appendLine("Price band: ${ipo.priceBandLabel}")
    ipo.lotSize?.let { appendLine("Lot size: $it shares") }
    ipo.minInvestment?.let { appendLine("Min investment: ${Fmt.rupees(it)}") }
    ipo.gmp?.let {
        appendLine("GMP: ${Fmt.rupeesSigned(it.premium)} (${Fmt.pct(it.estimatedGainPct(ipo.priceMax))})")
    }
    appendLine("Dates: ${Fmt.dateRange(ipo.openDate, ipo.closeDate)}")
    append("via IPO Radar")
}
