package com.promoapp.ui.home

import androidx.annotation.StringRes
import com.promoapp.R
import com.promoapp.domain.model.DealTab

@get:StringRes
val DealTab.labelRes: Int
    get() = when (this) {
        DealTab.FOR_YOU -> R.string.tab_for_you
        DealTab.TRENDING -> R.string.tab_trending
        DealTab.RECENT -> R.string.tab_recent
        DealTab.LOWEST_PRICE -> R.string.tab_lowest_price
    }
