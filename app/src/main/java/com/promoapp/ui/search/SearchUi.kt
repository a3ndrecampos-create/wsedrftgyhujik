package com.promoapp.ui.search

import androidx.annotation.StringRes
import com.promoapp.R
import com.promoapp.domain.model.SearchSort

@get:StringRes
val SearchSort.labelRes: Int
    get() = when (this) {
        SearchSort.RELEVANCE -> R.string.sort_relevance
        SearchSort.LOWEST_PRICE -> R.string.sort_lowest_price
        SearchSort.BIGGEST_DISCOUNT -> R.string.sort_biggest_discount
        SearchSort.NEWEST -> R.string.sort_newest
    }
