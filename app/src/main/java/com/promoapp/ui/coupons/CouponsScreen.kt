package com.promoapp.ui.coupons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.promoapp.R
import kotlinx.coroutines.launch

@Composable
fun CouponsScreen(viewModel: CouponsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.coupon_copied)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.statusBars,
    ) { padding ->
        when (val current = state) {
            CouponsUiState.Loading ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            CouponsUiState.Error ->
                Column(
                    Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.coupons_load_error))
                    Button(onClick = viewModel::load, modifier = Modifier.padding(top = 12.dp)) {
                        Text(stringResource(R.string.retry))
                    }
                }
            is CouponsUiState.Success -> {
                val now = remember(current) { System.currentTimeMillis() }
                Column(Modifier.padding(padding)) {
                    Text(
                        text = stringResource(R.string.coupons_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = current.selectedStore == null,
                                onClick = { viewModel.onStoreSelected(null) },
                                label = { Text(stringResource(R.string.coupons_all)) },
                            )
                        }
                        items(current.stores) { store ->
                            FilterChip(
                                selected = current.selectedStore == store,
                                onClick = { viewModel.onStoreSelected(store) },
                                label = { Text(store) },
                            )
                        }
                    }
                    if (current.coupons.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.coupons_empty))
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(current.coupons, key = { it.id }) { coupon ->
                                CouponCard(
                                    coupon = coupon,
                                    nowMillis = now,
                                    onCopy = { code ->
                                        clipboard.setText(AnnotatedString(code))
                                        scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                                    },
                                    onOpenStore = { uriHandler.openUri(coupon.url) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
