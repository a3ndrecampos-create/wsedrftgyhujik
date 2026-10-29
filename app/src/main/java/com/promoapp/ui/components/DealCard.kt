package com.promoapp.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.promoapp.R
import com.promoapp.domain.model.Deal
import com.promoapp.ui.theme.PromoAppTheme

@Composable
fun DealCard(
    deal: Deal,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp)) {
            AsyncImage(
                model = deal.imageUrl,
                contentDescription = deal.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${deal.storeName} · ${relativeTimeText(deal.postedAtMillis)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = deal.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                PriceRow(
                    price = deal.price,
                    originalPrice = deal.originalPrice,
                    discountPercent = deal.discountPercent,
                )
                if (deal.tags.isNotEmpty()) {
                    Text(
                        text = deal.tags.joinToString(" • "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔥 ${deal.temperature}", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(12.dp))
                    Text("💬 ${deal.commentCount}", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = stringResource(
                                if (isFavorite) R.string.wishlist_remove else R.string.wishlist_add,
                            ),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

private val previewDeal = Deal(
    id = 1,
    title = "Fone Bluetooth ANC com cancelamento de ruído",
    imageUrl = "",
    storeName = "Amazon",
    price = 299.90,
    originalPrice = 599.0,
    authorName = "Marcos",
    postedAtMillis = System.currentTimeMillis() - 5 * 60_000,
    temperature = 150,
    commentCount = 4,
    tags = listOf("Frete Grátis", "Cupom"),
    url = "https://example.com",
)

@Preview(showBackground = true)
@Composable
private fun DealCardPreview() {
    PromoAppTheme(dynamicColor = false) {
        DealCard(deal = previewDeal, isFavorite = true, onClick = {}, onToggleFavorite = {})
    }
}
