package com.iporadar.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iporadar.app.data.model.Ipo
import com.iporadar.app.ui.BoardFilter
import com.iporadar.app.ui.HomeTab
import com.iporadar.app.ui.IpoViewModel
import com.iporadar.app.ui.components.EmptyState
import com.iporadar.app.ui.components.ErrorState
import com.iporadar.app.ui.components.IpoCard

private val tabs = HomeTab.entries

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: IpoViewModel,
    onOpenIpo: (Ipo) -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val keyboard = LocalSoftwareKeyboardController.current

    // Land the user on whichever tab actually has something to show.
    val firstNonEmpty = remember(state.ipos, state.query, state.boardFilter, state.watchlistOnly) {
        tabs.indexOfFirst { state.forTab(it).isNotEmpty() }.takeIf { it >= 0 } ?: 0
    }
    val effectiveTab = if (state.forTab(tabs[selectedTab]).isEmpty() && state.query.isBlank()) {
        firstNonEmpty
    } else {
        selectedTab
    }

    Column(modifier = Modifier.fillMaxSize()) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("IPO Radar", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = subtitleFor(state.ipos.size, state.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { vm.toggleWatchlistOnly() }) {
                Icon(
                    imageVector = if (state.watchlistOnly) Icons.Filled.Bookmark
                    else Icons.Outlined.BookmarkBorder,
                    contentDescription = "Sirf watchlist dikhayein",
                    tint = if (state.watchlistOnly) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Company ya symbol search karein") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { vm.setQuery("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BoardFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.boardFilter == filter,
                    onClick = { vm.setBoardFilter(filter) },
                    label = { Text(filter.label) }
                )
            }
        }

        ScrollableTabRow(
            selectedTabIndex = effectiveTab,
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.background,
            divider = {}
        ) {
            tabs.forEachIndexed { index, tab ->
                val count = state.forTab(tab).size
                Tab(
                    selected = effectiveTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = if (count > 0) "${tab.label} ($count)" else tab.label,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                )
            }
        }

        val listed = state.forTab(tabs[effectiveTab])

        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                state.error != null && state.ipos.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ErrorState(message = state.error!!, onRetry = { vm.refresh() })
                    }
                }

                listed.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            title = emptyTitle(state.watchlistOnly, state.query),
                            subtitle = "Neeche kheench kar refresh karein."
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(listed, key = { it.id }) { ipo ->
                            IpoCard(
                                ipo = ipo,
                                watched = ipo.id in state.watchlist,
                                onClick = { onOpenIpo(ipo) },
                                onToggleWatch = { vm.toggleWatch(ipo) }
                            )
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
}

private fun emptyTitle(watchlistOnly: Boolean, query: String): String = when {
    query.isNotBlank() -> "\"$query\" se koi IPO nahi mila"
    watchlistOnly -> "Watchlist khaali hai"
    else -> "Is category me abhi koi IPO nahi hai"
}

private fun subtitleFor(count: Int, updatedAt: Long): String {
    if (updatedAt == 0L) return "$count IPOs"
    val minutes = (System.currentTimeMillis() - updatedAt) / 60_000
    val freshness = when {
        minutes < 1 -> "abhi update hua"
        minutes < 60 -> "$minutes min pehle"
        else -> "${minutes / 60} ghante pehle"
    }
    return "$count IPOs  ·  $freshness"
}
