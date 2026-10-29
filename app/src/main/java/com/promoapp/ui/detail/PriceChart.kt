package com.promoapp.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.promoapp.R
import com.promoapp.domain.model.PricePoint
import com.promoapp.ui.components.formatBrl

@Composable
fun PriceChart(
    points: List<PricePoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (points.size < 2) return

    val minPrice = points.minOf { it.price }
    val maxPrice = points.maxOf { it.price }
    val description = stringResource(R.string.price_chart_description, formatBrl(minPrice), formatBrl(maxPrice))
    val range = (maxPrice - minPrice).takeIf { it > 0 } ?: 1.0

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .semantics { contentDescription = description },
    ) {
        val stepX = size.width / (points.size - 1)
        val verticalPadding = size.height * 0.1f
        val usableHeight = size.height - 2 * verticalPadding

        fun yFor(price: Double): Float = size.height - verticalPadding - ((price - minPrice) / range).toFloat() * usableHeight

        val line = Path()
        val area = Path()
        points.forEachIndexed { index, point ->
            val x = index * stepX
            val y = yFor(point.price)
            if (index == 0) {
                line.moveTo(x, y)
                area.moveTo(x, size.height)
                area.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                area.lineTo(x, y)
            }
        }
        area.lineTo(size.width, size.height)
        area.close()

        drawPath(area, color = lineColor.copy(alpha = 0.15f))
        drawPath(
            path = line,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(
            color = lineColor,
            radius = 5.dp.toPx(),
            center = Offset(size.width, yFor(points.last().price)),
        )
    }
}
