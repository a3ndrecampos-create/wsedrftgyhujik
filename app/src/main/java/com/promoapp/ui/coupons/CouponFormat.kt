package com.promoapp.ui.coupons

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.promoapp.R
import com.promoapp.domain.model.Coupon
import com.promoapp.domain.model.CouponStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val shortDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM", Locale.forLanguageTag("pt-BR"))

fun formatShortDate(millis: Long): String =
    shortDateFormatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

@Composable
fun expiryText(coupon: Coupon, status: CouponStatus, nowMillis: Long): String {
    val expiresAt = coupon.expiresAtMillis ?: return stringResource(R.string.coupon_no_expiry)
    return when (status) {
        CouponStatus.EXPIRED -> stringResource(R.string.coupon_expired)
        CouponStatus.EXPIRING_SOON -> {
            val minutes = (expiresAt - nowMillis) / 60_000
            if (minutes < 60) {
                stringResource(R.string.coupon_expires_in_minutes, minutes.coerceAtLeast(1))
            } else {
                stringResource(R.string.coupon_expires_in_hours, minutes / 60)
            }
        }
        CouponStatus.ACTIVE -> stringResource(R.string.coupon_valid_until, formatShortDate(expiresAt))
    }
}
