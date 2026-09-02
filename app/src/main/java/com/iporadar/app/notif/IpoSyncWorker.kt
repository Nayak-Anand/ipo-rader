package com.iporadar.app.notif

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.iporadar.app.core.util.Fmt
import com.iporadar.app.data.local.NotificationSettings
import com.iporadar.app.data.model.Ipo
import com.iporadar.app.data.model.IpoStatus
import com.iporadar.app.di.ServiceLocator
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Periodic refresh + local alerts. Everything is evaluated on device against the
 * freshly fetched feed, so no push server (and no server bill) is involved.
 */
class IpoSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = ServiceLocator.repository
        val prefs = ServiceLocator.prefs

        repo.warmStart()
        val refreshed = repo.refresh()
        if (refreshed.isFailure) return Result.retry()

        val settings = prefs.settingsNow()
        val watchlist = prefs.watchlistNow()
        val today = LocalDate.now()

        val candidates = repo.state.value.ipos.filter { ipo ->
            !settings.watchlistOnly || ipo.id in watchlist
        }

        for (ipo in candidates) {
            for (alert in alertsFor(ipo, settings, today)) {
                val dedupeKey = "${ipo.id}:${alert.kind}:${today}"
                if (prefs.alreadyNotified(dedupeKey)) continue
                Notifier.show(
                    context = applicationContext,
                    id = dedupeKey.hashCode(),
                    title = alert.title,
                    body = alert.body,
                    ipoId = ipo.id
                )
                prefs.markNotified(dedupeKey)
            }
        }
        return Result.success()
    }

    private data class Alert(val kind: String, val title: String, val body: String)

    private fun alertsFor(
        ipo: Ipo,
        settings: NotificationSettings,
        today: LocalDate
    ): List<Alert> {
        val out = mutableListOf<Alert>()
        val gmpLine = ipo.gmp?.let { gmp ->
            val gain = gmp.estimatedGainPct(ipo.priceMax)
            "GMP ${Fmt.rupeesSigned(gmp.premium)}" + (gain?.let { " (${Fmt.pct(it, 1)})" } ?: "")
        }

        if (settings.ipoOpen && ipo.openDate == today) {
            out += Alert(
                kind = "open",
                title = "${ipo.name} IPO aaj khula hai",
                body = listOfNotNull(
                    "Price band ${ipo.priceBandLabel}",
                    ipo.lotSize?.let { "Lot $it shares" },
                    gmpLine
                ).joinToString(" · ")
            )
        }

        if (settings.closingSoon && ipo.status == IpoStatus.OPEN && ipo.closeDate != null) {
            val daysLeft = ChronoUnit.DAYS.between(today, ipo.closeDate)
            if (daysLeft in 0..1) {
                out += Alert(
                    kind = "closing",
                    title = if (daysLeft == 0L) "${ipo.name} — aaj last day"
                    else "${ipo.name} kal band ho raha hai",
                    body = listOfNotNull(
                        ipo.subscription?.total?.let { "Subscribed ${Fmt.times(it)}" },
                        gmpLine
                    ).joinToString(" · ").ifEmpty { "Apply karne ka aakhri mauka." }
                )
            }
        }

        if (settings.allotmentOut && ipo.allotmentDate == today) {
            out += Alert(
                kind = "allotment",
                title = "${ipo.name} allotment aaj",
                body = "Registrar site pe status check karein" +
                    (ipo.registrar?.let { " (${it.displayName})" } ?: "")
            )
        }

        if (settings.listingDay && ipo.listingDate == today) {
            out += Alert(
                kind = "listing",
                title = "${ipo.name} aaj list ho raha hai",
                body = listOfNotNull(
                    "Issue price ${Fmt.rupees(ipo.priceMax)}",
                    gmpLine
                ).joinToString(" · ")
            )
        }

        if (settings.gmpMoves && ipo.status != IpoStatus.LISTED) {
            val gmp = ipo.gmp
            val previous = gmp?.history?.lastOrNull()?.premium
            if (gmp != null && previous != null && previous != 0.0) {
                val movePct = (gmp.premium - previous) / kotlin.math.abs(previous) * 100.0
                if (kotlin.math.abs(movePct) >= GMP_MOVE_THRESHOLD_PCT) {
                    out += Alert(
                        kind = "gmp",
                        title = "${ipo.name} GMP ${if (movePct > 0) "chadha" else "gira"}",
                        body = "${Fmt.rupeesSigned(previous)} → ${Fmt.rupeesSigned(gmp.premium)} " +
                            "(${Fmt.pct(movePct, 0)})"
                    )
                }
            }
        }
        return out
    }

    companion object {
        private const val UNIQUE_NAME = "ipo_sync"
        private const val GMP_MOVE_THRESHOLD_PCT = 20.0

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<IpoSyncWorker>(3, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
