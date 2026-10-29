package com.promoapp.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.promoapp.R
import com.promoapp.domain.model.Deal
import com.promoapp.ui.components.PriceRow

@Composable
fun ProfileScreen(
    onPostClick: () -> Unit,
    onDealClick: (Long) -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val name by viewModel.displayName.collectAsStateWithLifecycle()
    val myDeals by viewModel.myDeals.collectAsStateWithLifecycle()
    val dailyEnabled by viewModel.dailyDealsEnabled.collectAsStateWithLifecycle()

    // Recarrega "Minhas ofertas" sempre que a tela volta a ficar visível (por exemplo, depois de publicar).
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = if (name.isBlank()) stringResource(R.string.profile_guest) else stringResource(R.string.profile_hello, name),
            style = MaterialTheme.typography.titleLarge,
        )
        Button(onClick = onPostClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.profile_post))
        }
        DailyDealsToggle(enabled = dailyEnabled, onChange = viewModel::onDailyDealsChange)
        Text(stringResource(R.string.profile_my_deals), style = MaterialTheme.typography.titleMedium)

        when (val current = myDeals) {
            MyDealsState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            MyDealsState.Error -> Text(stringResource(R.string.profile_load_error))
            is MyDealsState.Success ->
                if (current.deals.isEmpty()) {
                    Text(stringResource(R.string.profile_my_deals_empty))
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(current.deals, key = { it.id }) { deal ->
                            MyDealCard(deal = deal, onClick = { onDealClick(deal.id) })
                        }
                    }
                }
        }
    }
}

@Composable
private fun MyDealCard(
    deal: Deal,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = deal.imageUrl,
                contentDescription = deal.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = deal.storeName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = deal.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                PriceRow(price = deal.price, originalPrice = deal.originalPrice, discountPercent = deal.discountPercent)
            }
        }
    }
}
