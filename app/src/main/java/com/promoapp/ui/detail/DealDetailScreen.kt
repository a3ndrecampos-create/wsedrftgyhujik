package com.promoapp.ui.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.promoapp.R
import com.promoapp.domain.model.DealDetail
import com.promoapp.domain.model.PriceAlert
import com.promoapp.domain.model.PriceVerdict
import com.promoapp.ui.components.PriceRow
import com.promoapp.ui.components.formatBrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealDetailScreen(
    onBack: () -> Unit,
    viewModel: DealDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val alert by viewModel.alert.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    (state as? DealDetailUiState.Success)?.let { success ->
                        IconButton(onClick = { shareDeal(context, success.detail.deal) }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share))
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (val current = state) {
            DealDetailUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            DealDetailUiState.Error -> Column(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.load_error))
                Button(onClick = viewModel::load, modifier = Modifier.padding(top = 12.dp)) {
                    Text(stringResource(R.string.retry))
                }
            }
            is DealDetailUiState.Success -> DealDetailContent(
                detail = current.detail,
                alert = alert,
                onOpenStore = { uriHandler.openUri(current.detail.deal.url) },
                onCreateAlert = viewModel::onCreateAlert,
                onRemoveAlert = viewModel::onRemoveAlert,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun DealDetailContent(
    detail: DealDetail,
    alert: PriceAlert?,
    onOpenStore: () -> Unit,
    onCreateAlert: (Double) -> Unit,
    onRemoveAlert: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val deal = detail.deal
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = deal.imageUrl,
            contentDescription = deal.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.5f)
                .clip(RoundedCornerShape(16.dp)),
        )
        Text(
            text = deal.storeName,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = deal.title, style = MaterialTheme.typography.headlineSmall)
        PriceRow(
            price = deal.price,
            originalPrice = deal.originalPrice,
            discountPercent = deal.discountPercent,
            priceStyle = MaterialTheme.typography.headlineMedium,
        )
        detail.verdict?.let { VerdictBadge(it) }
        if (deal.tags.isNotEmpty()) {
            Text(
                text = deal.tags.joinToString(" • "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        if (detail.history.points.isNotEmpty()) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.price_history_title), style = MaterialTheme.typography.titleSmall)
                PriceChart(points = detail.history.points)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    PriceStat(R.string.price_lowest, detail.history.lowest)
                    PriceStat(R.string.price_average, detail.history.average)
                    PriceStat(R.string.price_highest, detail.history.highest)
                }
            }
        }
        }

        PriceAlertSection(
            currentPrice = deal.price,
            alert = alert,
            onCreate = onCreateAlert,
            onRemove = onRemoveAlert,
        )

        Button(onClick = onOpenStore, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.go_to_store, deal.storeName))
        }
    }
}

@Composable
private fun PriceStat(@StringRes labelRes: Int, value: Double?) {
    Column {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value?.let(::formatBrl).orEmpty(),
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
private fun VerdictBadge(verdict: PriceVerdict) {
    val (labelRes, container, content) = when (verdict) {
        PriceVerdict.LOWEST_EVER -> Triple(
            R.string.verdict_lowest_ever,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        PriceVerdict.GOOD_PRICE -> Triple(
            R.string.verdict_good_price,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        PriceVerdict.FAIR_PRICE -> Triple(
            R.string.verdict_fair_price,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
        PriceVerdict.ABOVE_AVERAGE -> Triple(
            R.string.verdict_above_average,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
    }
    Surface(shape = RoundedCornerShape(50), color = container) {
        Text(
            text = stringResource(labelRes),
            color = content,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
