package com.promoapp.ui.search

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.promoapp.R
import com.promoapp.ui.components.DealList

@Composable
fun SearchScreen(
    onDealClick: (Long) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val options by viewModel.options.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val hasCriteria by viewModel.hasCriteria.collectAsStateWithLifecycle()
    val results = viewModel.results.collectAsLazyPagingItems()
    var showFilters by rememberSaveable { mutableStateOf(false) }

    val refreshState = results.loadState.refresh
    val isEmpty = results.itemCount == 0

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.search_clear))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { showFilters = true }) {
                Text(
                    if (filters.activeFilterCount > 0) {
                        stringResource(R.string.filters_count, filters.activeFilterCount)
                    } else {
                        stringResource(R.string.filters)
                    },
                )
            }
        }

        when {
            !hasCriteria -> CenteredMessage(R.string.search_prompt)
            isEmpty && refreshState is LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            isEmpty && refreshState is LoadState.Error -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.load_error))
                Button(onClick = { results.refresh() }, modifier = Modifier.padding(top = 12.dp)) {
                    Text(stringResource(R.string.retry))
                }
            }
            isEmpty && refreshState is LoadState.NotLoading -> CenteredMessage(R.string.search_empty)
            else -> DealList(
                deals = results,
                favorites = favorites,
                onDealClick = onDealClick,
                onToggleFavorite = viewModel::onToggleFavorite,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (showFilters) {
        FilterSheet(
            initial = filters,
            options = options,
            onDismiss = { showFilters = false },
            onApply = {
                viewModel.onFiltersApplied(it)
                showFilters = false
            },
        )
    }
}

@Composable
private fun CenteredMessage(@StringRes textRes: Int) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(textRes))
    }
}
