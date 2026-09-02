package com.iporadar.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iporadar.app.core.util.Fmt
import com.iporadar.app.data.model.Ipo
import com.iporadar.app.data.model.Stage
import com.iporadar.app.ui.theme.LocalMarketColors
import com.iporadar.app.ui.theme.MarketColors

@Composable
fun IpoCard(
    ipo: Ipo,
    watched: Boolean,
    onClick: () -> Unit,
    onToggleWatch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val market = LocalMarketColors.current
    val gain = ipo.gmp?.estimatedGainPct(ipo.priceMax) ?: ipo.listingGainPct
    val gainColor = when {
        gain == null -> MaterialTheme.colorScheme.onSurfaceVariant
        gain >= 0 -> market.positive
        else -> market.negative
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            Row(verticalAlignment = Alignment.Top) {
                CompanyAvatar(ipo)
                Spacer(Modifier.width(11.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ipo.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(5.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        StatusChip(ipo.stage())
                        NeutralChip(ipo.board.label)
                    }
                }

                IconButton(onClick = onToggleWatch, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = if (watched) Icons.Filled.Bookmark
                        else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (watched) "Watchlist se hatayein" else "Watchlist me daalein",
                        tint = if (watched) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(11.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LabeledValue(
                    label = "Price band",
                    value = ipo.priceBandLabel,
                    modifier = Modifier.weight(1.1f)
                )
                LabeledValue(
                    label = if (ipo.stage() == Stage.LISTED) "Listing gain" else "GMP",
                    // Rupees and percent read as one figure, so keep them on one line.
                    value = gmpLabel(ipo),
                    valueColor = gainColor,
                    modifier = Modifier.weight(1.15f)
                )
                LabeledValue(
                    label = "Min. invest",
                    value = Fmt.rupees(ipo.minInvestment),
                    modifier = Modifier.weight(1f)
                )
            }

            // Everything the standalone GMP screen used to show, in place.
            val gmp = ipo.gmp
            if (gmp != null && ipo.stage() != Stage.LISTED) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LabeledValue(
                        label = "Expected listing",
                        value = Fmt.rupees(gmp.expectedListing(ipo.priceMax)),
                        modifier = Modifier.weight(1.1f)
                    )
                    LabeledValue(
                        label = "Profit / lot",
                        value = Fmt.rupeesSigned(ipo.lotSize?.let { gmp.premium * it }),
                        valueColor = gainColor,
                        modifier = Modifier.weight(1f)
                    )
                    MiniSparkline(
                        values = gmp.history.map { it.premium }.takeLast(14),
                        positive = (gain ?: 0.0) >= 0
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(9.dp))

            // Dates on the left, the live signal (countdown / subscription) on the right.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateLine(ipo),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val signal = signalLine(ipo)
                if (signal != null) {
                    Text(
                        text = signal,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = signalColor(ipo, market)
                            .takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant }
                    )
                }
            }
        }
    }
}

/** "+₹32 (+18.1%)" — the premium with the gain it works out to, on one line. */
private fun gmpLabel(ipo: Ipo): String {
    if (ipo.stage() == Stage.LISTED) {
        return ipo.listingGainPct?.let { Fmt.pct(it) } ?: "—"
    }
    val gmp = ipo.gmp ?: return "—"
    val rupees = Fmt.rupeesSigned(gmp.premium)
    val pct = gmp.estimatedGainPct(ipo.priceMax) ?: return rupees
    return "$rupees (${Fmt.pct(pct, 1)})"
}

/** The bidding window, always visible — this is what people scan the list for. */
private fun dateLine(ipo: Ipo): String = when (ipo.stage()) {
    Stage.LISTED -> "Listed ${Fmt.date(ipo.listingDate)}"
    else -> Fmt.dateRange(ipo.openDate, ipo.closeDate)
}

/** Whatever the user is waiting on right now. */
private fun signalLine(ipo: Ipo): String? = when (ipo.stage()) {
    Stage.OPEN, Stage.UPCOMING ->
        Fmt.countdown(ipo.openDate, ipo.closeDate)
            ?: ipo.subscription?.total?.let { "Subscribed ${Fmt.times(it)}" }
    Stage.ALLOTMENT -> ipo.allotmentDate?.let { "Allotment ${Fmt.date(it)}" }
        ?: "Allotment awaited"
    Stage.LISTING -> ipo.listingDate?.let { "Lists ${Fmt.date(it)}" }
    Stage.LISTED, Stage.CLOSED ->
        ipo.subscription?.total?.let { "Subscribed ${Fmt.times(it)}" }
}

private fun signalColor(ipo: Ipo, market: MarketColors): Color = when (ipo.stage()) {
    // Last day / closing tomorrow deserves to stand out.
    Stage.OPEN -> if (Fmt.countdown(ipo.openDate, ipo.closeDate)?.contains("Closes") == true) {
        market.warning
    } else {
        Color.Unspecified
    }
    Stage.ALLOTMENT, Stage.LISTING -> Color.Unspecified
    else -> Color.Unspecified
}

@Composable
private fun CompanyAvatar(ipo: Ipo) {
    val initials = ipo.name
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
