package com.iporadar.app.ui.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iporadar.app.BuildConfig
import com.iporadar.app.data.local.DarkMode
import com.iporadar.app.data.local.NotificationField
import com.iporadar.app.data.repo.DataSource
import com.iporadar.app.ui.IpoViewModel
import com.iporadar.app.ui.components.SectionHeader

@Composable
fun SettingsScreen(vm: IpoViewModel) {
    val settings by vm.notificationSettings.collectAsStateWithLifecycle()
    val darkMode by vm.darkMode.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
        }

        item {
            SettingsCard {
                SectionHeader(title = "Notifications")
                Spacer(Modifier.height(6.dp))
                ToggleRow(
                    title = "IPO open alert",
                    subtitle = "Jis din IPO subscription ke liye khule",
                    checked = settings.ipoOpen,
                    onChange = { vm.setNotification(NotificationField.IPO_OPEN, it) }
                )
                ToggleRow(
                    title = "Closing reminder",
                    subtitle = "Band hone se ek din pehle aur last day",
                    checked = settings.closingSoon,
                    onChange = { vm.setNotification(NotificationField.CLOSING_SOON, it) }
                )
                ToggleRow(
                    title = "Allotment day",
                    subtitle = "Allotment finalise hone wale din",
                    checked = settings.allotmentOut,
                    onChange = { vm.setNotification(NotificationField.ALLOTMENT_OUT, it) }
                )
                ToggleRow(
                    title = "Listing day",
                    subtitle = "Jis din share exchange pe list ho",
                    checked = settings.listingDay,
                    onChange = { vm.setNotification(NotificationField.LISTING_DAY, it) }
                )
                ToggleRow(
                    title = "Badi GMP movement",
                    subtitle = "GMP 20% se zyada upar ya neeche jaye",
                    checked = settings.gmpMoves,
                    onChange = { vm.setNotification(NotificationField.GMP_MOVES, it) }
                )
                ToggleRow(
                    title = "Sirf watchlist ke liye",
                    subtitle = "Alerts sirf saved IPOs ke aayenge",
                    checked = settings.watchlistOnly,
                    onChange = { vm.setNotification(NotificationField.WATCHLIST_ONLY, it) },
                    last = true
                )
            }
        }

        item {
            SettingsCard {
                SectionHeader(title = "Theme")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DarkMode.entries.forEach { mode ->
                        FilterChip(
                            selected = darkMode == mode,
                            onClick = { vm.setDarkMode(mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        DarkMode.SYSTEM -> "System"
                                        DarkMode.LIGHT -> "Light"
                                        DarkMode.DARK -> "Dark"
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }

        item {
            SettingsCard(onClick = { vm.refresh() }) {
                SectionHeader(title = "Data")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Source: " + when (state.source) {
                        DataSource.NETWORK -> "Live feed + NSE"
                        DataSource.CACHE -> "Offline snapshot"
                        DataSource.BUNDLED -> "Bundled sample data"
                        DataSource.NONE -> "Abhi load nahi hua"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${state.ipos.size} IPOs loaded · tap karke refresh karein",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        item {
            SettingsCard {
                SectionHeader(title = "App ke baare me")
                Spacer(Modifier.height(8.dp))
                Text(
                    "IPO Radar v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Ye app sirf publicly available information dikhata hai. GMP grey market " +
                        "ka unofficial number hai — SEBI regulated nahi. Yahan kuch bhi investment " +
                        "advice nahi hai; apna research zaroor karein.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SettingsCard(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "IPO Radar — India ke IPOs, GMP aur allotment ek jagah."
                                )
                            }
                        )
                    }
                }
            ) {
                Text("App share karein", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    last: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
    if (!last) {
        androidx.compose.material3.HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun SettingsCard(
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}
