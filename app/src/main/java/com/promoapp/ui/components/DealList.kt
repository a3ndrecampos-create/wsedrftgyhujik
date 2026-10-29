package com.promoapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.promoapp.R
import com.promoapp.domain.model.Deal

/** Lista paginada de ofertas, compartilhada entre Home e Busca. */
@Composable
fun DealList(
    deals: LazyPagingItems<Deal>,
    favorites: Set<Long>,
    onDealClick: (Long) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(count = deals.itemCount, key = deals.itemKey { it.id }) { index ->
            deals[index]?.let { deal ->
                DealCard(
                    deal = deal,
                    isFavorite = deal.id in favorites,
                    onClick = { onDealClick(deal.id) },
                    onToggleFavorite = { onToggleFavorite(deal.id) },
                )
            }
        }
        when (deals.loadState.append) {
            is LoadState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is LoadState.Error -> item {
                TextButton(onClick = { deals.retry() }) { Text(stringResource(R.string.load_more_error)) }
            }
            else -> Unit
        }
    }
}
