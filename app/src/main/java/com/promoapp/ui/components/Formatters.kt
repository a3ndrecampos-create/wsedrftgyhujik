package com.promoapp.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.promoapp.R
import java.text.NumberFormat
import java.util.Locale

fun formatBrl(value: Double): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(value)

@Composable
fun relativeTimeText(millis: Long): String {
    val minutes = (System.currentTimeMillis() - millis) / 60_000
    return when {
        minutes < 1 -> stringResource(R.string.time_now)
        minutes < 60 -> stringResource(R.string.time_minutes_ago, minutes)
        minutes < 1_440 -> stringResource(R.string.time_hours_ago, minutes / 60)
        else -> stringResource(R.string.time_days_ago, minutes / 1_440)
    }
}
