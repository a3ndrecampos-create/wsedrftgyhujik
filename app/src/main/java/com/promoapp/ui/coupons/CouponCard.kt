package com.promoapp.ui.coupons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.promoapp.R
import com.promoapp.domain.model.Coupon
import com.promoapp.domain.model.CouponStatus
import com.promoapp.ui.components.formatBrl
import com.promoapp.ui.theme.PromoAppTheme

@Composable
fun CouponCard(
    coupon: Coupon,
    nowMillis: Long,
    onCopy: (String) -> Unit,
    onOpenStore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = coupon.statusAt(nowMillis)
    val isExpired = status == CouponStatus.EXPIRED

    Card(modifier = modifier.fillMaxWidth().alpha(if (isExpired) 0.6f else 1f)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = coupon.storeName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(text = expiryText(coupon, status, nowMillis), status = status)
            }
            Text(
                text = coupon.discountLabel,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(text = coupon.description, style = MaterialTheme.typography.bodyMedium)
            coupon.minPurchase?.let {
                Text(
                    text = stringResource(R.string.coupon_min_purchase, formatBrl(it)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = coupon.code,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Button(onClick = { onCopy(coupon.code) }, enabled = !isExpired) {
                    Text(stringResource(R.string.coupon_copy))
                }
            }
            TextButton(onClick = onOpenStore, enabled = !isExpired) {
                Text(stringResource(R.string.go_to_store, coupon.storeName))
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, status: CouponStatus) {
    val (container, content) = when (status) {
        CouponStatus.EXPIRING_SOON ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        CouponStatus.EXPIRED ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        CouponStatus.ACTIVE ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(shape = RoundedCornerShape(50), color = container) {
        Text(
            text = text,
            color = content,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private val previewCoupon = Coupon(
    id = 1,
    code = "PROMO11",
    storeName = "Amazon",
    description = "Primeira compra pelo app",
    discountLabel = "R$ 50 OFF",
    minPurchase = 200.0,
    expiresAtMillis = System.currentTimeMillis() + 3 * 3_600_000L,
    url = "https://example.com",
)

@Preview(showBackground = true)
@Composable
private fun CouponCardPreview() {
    PromoAppTheme(dynamicColor = false) {
        CouponCard(coupon = previewCoupon, nowMillis = System.currentTimeMillis(), onCopy = {}, onOpenStore = {})
    }
}
