package com.promoapp.ui.post

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.promoapp.R
import com.promoapp.domain.model.DraftError

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PostDealScreen(
    onBack: () -> Unit,
    onPosted: (Long) -> Unit,
    viewModel: PostDealViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val errors = state.visibleErrors

    LaunchedEffect(Unit) {
        viewModel.postedDealId.collect { onPosted(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.post_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                labelRes = R.string.post_field_title,
                errorRes =
                    when {
                        DraftError.TITLE_TOO_SHORT in errors -> R.string.err_title_short
                        DraftError.TITLE_TOO_LONG in errors -> R.string.err_title_long
                        else -> null
                    },
                capitalization = KeyboardCapitalization.Sentences,
            )
            FormField(
                value = state.url,
                onValueChange = viewModel::onUrlChange,
                labelRes = R.string.post_field_url,
                errorRes = if (DraftError.INVALID_URL in errors) R.string.err_url else null,
                keyboardType = KeyboardType.Uri,
            )

            FormField(
                value = state.store,
                onValueChange = viewModel::onStoreChange,
                labelRes = R.string.post_field_store,
                errorRes = if (DraftError.MISSING_STORE in errors) R.string.err_store else null,
                capitalization = KeyboardCapitalization.Words,
            )
            state.options?.stores?.takeIf { it.isNotEmpty() }?.let { stores ->
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    stores.forEach { store ->
                        FilterChip(
                            selected = state.store.trim().equals(store, ignoreCase = true),
                            onClick = { viewModel.onStoreChange(store) },
                            label = { Text(store) },
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FormField(
                    value = state.priceText,
                    onValueChange = { viewModel.onPriceChange(it.onlyNumberChars()) },
                    labelRes = R.string.post_field_price,
                    errorRes = if (DraftError.INVALID_PRICE in errors) R.string.err_price else null,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                FormField(
                    value = state.originalPriceText,
                    onValueChange = { viewModel.onOriginalPriceChange(it.onlyNumberChars()) },
                    labelRes = R.string.post_field_original_price,
                    errorRes = if (DraftError.ORIGINAL_NOT_HIGHER in errors) R.string.err_original else null,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
            }

            FormField(
                value = state.imageUrl,
                onValueChange = viewModel::onImageUrlChange,
                labelRes = R.string.post_field_image,
                errorRes = if (DraftError.INVALID_IMAGE_URL in errors) R.string.err_image_url else null,
                keyboardType = KeyboardType.Uri,
            )
            FormField(
                value = state.coupon,
                onValueChange = viewModel::onCouponChange,
                labelRes = R.string.post_field_coupon,
                errorRes = null,
                capitalization = KeyboardCapitalization.Characters,
            )
            FilterChip(
                selected = state.freeShipping,
                onClick = { viewModel.onFreeShippingChange(!state.freeShipping) },
                label = { Text(stringResource(R.string.post_free_shipping)) },
            )

            state.options?.categories?.takeIf { it.isNotEmpty() }?.let { categories ->
                Text(stringResource(R.string.post_field_category), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { name ->
                        FilterChip(
                            selected = state.category == name,
                            onClick = { viewModel.onCategoryToggle(name) },
                            label = { Text(name) },
                        )
                    }
                }
            }

            FormField(
                value = state.author,
                onValueChange = viewModel::onAuthorChange,
                labelRes = R.string.post_field_author,
                errorRes = if (DraftError.MISSING_AUTHOR in errors) R.string.err_author else null,
                capitalization = KeyboardCapitalization.Words,
            )

            if (state.submitFailed) {
                Text(
                    text = stringResource(R.string.post_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Button(
                onClick = viewModel::submit,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.post_submitting), modifier = Modifier.padding(start = 8.dp))
                } else {
                    Text(stringResource(R.string.post_submit))
                }
            }
        }
    }
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes labelRes: Int,
    errorRes: Int?,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(labelRes)) },
        isError = errorRes != null,
        supportingText =
            if (errorRes != null) {
                { Text(stringResource(errorRes)) }
            } else {
                null
            },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
    )
}

private fun String.onlyNumberChars(): String = filter { it.isDigit() || it == ',' || it == '.' }
