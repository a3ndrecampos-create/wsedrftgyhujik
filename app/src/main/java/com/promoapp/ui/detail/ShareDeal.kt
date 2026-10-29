package com.promoapp.ui.detail

import android.content.Context
import android.content.Intent
import com.promoapp.R
import com.promoapp.domain.model.Deal
import com.promoapp.ui.components.formatBrl

fun shareDeal(
    context: Context,
    deal: Deal,
) {
    val text = context.getString(R.string.share_text, deal.title, formatBrl(deal.price), deal.url)
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.share_chooser)))
}
