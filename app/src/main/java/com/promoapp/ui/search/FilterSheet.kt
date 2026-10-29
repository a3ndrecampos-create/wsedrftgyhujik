package com.promoapp.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.promoapp.R
import com.promoapp.domain.model.FilterOptions
import com.promoapp.domain.model.SearchFilters
import com.promoapp.domain.model.SearchSort

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    initial: SearchFilters,
    options: FilterOptions?,
    onDismiss: () -> Unit,
    onApply: (SearchFilters) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sort by remember { mutableStateOf(initial.sort) }
    var stores by remember { mutableStateOf(initial.stores) }
    var category by remember { mutableStateOf(initial.category) }
    var minPrice by remember { mutableStateOf(initial.minPrice?.let(::formatPriceInput).orEmpty()) }
    var maxPrice by remember { mutableStateOf(initial.maxPrice?.let(::formatPriceInput).orEmpty()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.filters_title), style = MaterialTheme.typography.titleLarge)

            SectionTitle(R.string.filter_sort)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchSort.entries.forEach { option ->
                    FilterChip(
                        selected = sort == option,
                        onClick = { sort = option },
                        label = { Text(stringResource(option.labelRes)) },
                    )
                }
            }

            if (options != null && options.stores.isNotEmpty()) {
                SectionTitle(R.string.filter_stores)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.stores.forEach { store ->
                        FilterChip(
                            selected = store in stores,
                            onClick = { stores = if (store in stores) stores - store else stores + store },
                            label = { Text(store) },
                        )
                    }
                }
            }

            if (options != null && options.categories.isNotEmpty()) {
                SectionTitle(R.string.filter_category)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.categories.forEach { name ->
                        FilterChip(
                            selected = category == name,
                            onClick = { category = if (category == name) null else name },
                            label = { Text(name) },
                        )
                    }
                }
            }

            SectionTitle(R.string.filter_price)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PriceField(
                    value = minPrice,
                    onValueChange = { minPrice = it },
                    labelRes = R.string.filter_min,
                    modifier = Modifier.weight(1f),
                )
                PriceField(
                    value = maxPrice,
                    onValueChange = { maxPrice = it },
                    labelRes = R.string.filter_max,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        sort = SearchSort.RELEVANCE
                        stores = emptySet()
                        category = null
                        minPrice = ""
                        maxPrice = ""
                    },
                ) { Text(stringResource(R.string.filters_clear)) }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onApply(
                            SearchFilters(
                                stores = stores,
                                category = category,
                                minPrice = parsePrice(minPrice),
                                maxPrice = parsePrice(maxPrice),
                                sort = sort,
                            ).normalized(),
                        )
                    },
                ) { Text(stringResource(R.string.filters_apply)) }
            }
        }
    }
}

@Composable
private fun SectionTitle(textRes: Int) {
    Text(stringResource(textRes), style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun PriceField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter { it.isDigit() || it == ',' || it == '.' }) },
        modifier = modifier,
        singleLine = true,
        label = { Text(stringResource(labelRes)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}
