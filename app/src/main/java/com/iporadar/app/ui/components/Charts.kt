package com.iporadar.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.iporadar.app.core.util.Fmt
import com.iporadar.app.data.model.GmpPoint
import com.iporadar.app.data.model.Subscription
import com.iporadar.app.ui.theme.LocalMarketColors
import kotlin.math.max

/**
 * GMP trend line. Kept deliberately plain — no axes, no grid — because the
 * numbers around it already carry the precise values.
 */
@Composable
fun GmpTrendChart(
    points: List<GmpPoint>,
    modifier: Modifier = Modifier
) {
    val market = LocalMarketColors.current
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    if (points.size < 2) {
        Box(
            modifier = modifier.height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "GMP history abhi available nahi hai",
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVariant
            )
        }
        return
    }

    val values = points.map { it.premium }
    val minV = values.min()
    val maxV = values.max()
    val span = max(maxV - minV, 1.0)
    val rising = values.last() >= values.first()
    val lineColor = if (rising) market.positive else market.negative

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(vertical = 8.dp)
        ) {
            val w = size.width
            val h = size.height
            val stepX = if (points.size == 1) 0f else w / (points.size - 1)

            fun yOf(v: Double): Float =
                (h - ((v - minV) / span).toFloat() * h).coerceIn(0f, h)

            val linePath = Path()
            val fillPath = Path()
            points.forEachIndexed { index, point ->
                val x = stepX * index
                val y = yOf(point.premium)
                if (index == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, h)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo(w, h)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    listOf(lineColor.copy(alpha = 0.22f), Color.Transparent)
                )
            )
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 2.5.dp.toPx())
            )
            drawCircle(
                color = lineColor,
                radius = 4.dp.toPx(),
                center = Offset(w, yOf(values.last()))
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                points.first().date,
                style = MaterialTheme.typography.labelSmall,
                color = onSurfaceVariant
            )
            Text(
                "Low ${Fmt.rupeesSigned(minV)}  ·  High ${Fmt.rupeesSigned(maxV)}",
                style = MaterialTheme.typography.labelSmall,
                color = onSurfaceVariant
            )
            Text(
                points.last().date,
                style = MaterialTheme.typography.labelSmall,
                color = onSurfaceVariant
            )
        }
    }
}

/** Category-wise subscription bars, scaled against the most-subscribed category. */
@Composable
fun SubscriptionBars(
    subscription: Subscription,
    modifier: Modifier = Modifier
) {
    // Show the NII halves when the feed reports them; fall back to the combined figure.
    val niiRows = when {
        subscription.niiSmall != null || subscription.niiBig != null -> listOfNotNull(
            subscription.niiBig?.let { "bNII (₹10L+)" to it },
            subscription.niiSmall?.let { "sNII (₹2-10L)" to it }
        )
        subscription.nii != null -> listOf("NII / HNI" to subscription.nii)
        else -> emptyList()
    }

    val rows = listOfNotNull(subscription.qib?.let { "QIB" to it }) +
        niiRows +
        listOfNotNull(
            subscription.retail?.let { "Retail" to it },
            subscription.employee?.let { "Employee" to it },
            subscription.total?.let { "Total" to it }
        )
    if (rows.isEmpty()) return

    val peak = max(rows.maxOf { it.second }, 1.0)
    val market = LocalMarketColors.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { (label, times) ->
            val isTotal = label == "Total"
            val barColor = when {
                isTotal -> MaterialTheme.colorScheme.secondary
                times >= 1.0 -> market.positive
                else -> market.warning
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        Fmt.times(times),
                        style = MaterialTheme.typography.titleSmall,
                        color = barColor
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth((times / peak).toFloat().coerceIn(0.02f, 1f))
                            .clip(RoundedCornerShape(3.dp))
                            .background(barColor)
                    )
                }
            }
        }
    }
}

/** Small inline sparkline used inside the GMP list rows. */
@Composable
fun MiniSparkline(
    values: List<Double>,
    positive: Boolean,
    modifier: Modifier = Modifier
) {
    val market = LocalMarketColors.current
    val color = if (positive) market.positive else market.negative
    if (values.size < 2) {
        Spacer(modifier.width(52.dp))
        return
    }
    val minV = values.min()
    val span = max(values.max() - minV, 1.0)

    Canvas(modifier = modifier.width(52.dp).height(24.dp)) {
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = stepX * i
            val y = size.height - ((v - minV) / span).toFloat() * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(width = 1.8.dp.toPx()))
    }
}
