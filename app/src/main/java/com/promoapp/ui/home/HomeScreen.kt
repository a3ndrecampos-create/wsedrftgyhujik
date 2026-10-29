package com.promoapp.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.promoapp.R
import com.promoapp.domain.model.DealTab
import com.promoapp.ui.components.DealList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onDealClick: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val deals = viewModel.deals.collectAsLazyPagingItems()
    val refreshState = deals.loadState.refresh
    val hasItems = deals.itemCount > 0

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 16.dp) {
            DealTab.entries.forEach { t ->
                Tab(
                    selected = t == tab,
                    onClick = { viewModel.onTabSelected(t) },
                    text = { Text(stringResource(t.labelRes)) },
                )
            }
        }

        if (refreshState is LoadState.Error && hasItems) {
            OfflineBanner(onRetry = { deals.refresh() })
        }

        when {
            !hasItems && refreshState is LoadState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            !hasItems && refreshState is LoadState.Error ->
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.load_error))
                    Button(onClick = { deals.refresh() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text(stringResource(R.string.retry))
                    }
                }
            else ->
                PullToRefreshBox(
                    isRefreshing = hasItems && refreshState is LoadState.Loading,
                    onRefresh = { deals.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    DealList(
                        deals = deals,
                        favorites = favorites,
                        onDealClick = onDealClick,
                        onToggleFavorite = viewModel::onToggleFavorite,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
        }
    }
}

@Composable
private fun OfflineBanner(onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.offline_banner),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}
