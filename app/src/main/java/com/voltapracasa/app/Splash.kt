package com.voltapracasa.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Abertura: luz de varanda acesa, marca no centro e assinatura no rodapé. */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(900))
        delay(1300)
        onFinished()
    }

    Box(Modifier.fillMaxSize().background(Brand.Ink)) {
        Box(
            Modifier.align(Alignment.Center).size(340.dp)
                .graphicsLayer { alpha = progress.value }
                .background(Brush.radialGradient(listOf(Brand.Porch.copy(alpha = 0.30f), Color.Transparent)))
        )

        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 32.dp).graphicsLayer {
                alpha = progress.value
                scaleX = 0.88f + 0.12f * progress.value
                scaleY = 0.88f + 0.12f * progress.value
            },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PawMark(92.dp, Brand.Porch)
            Spacer(Modifier.height(24.dp))
            Text("Volta Pra Casa", style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(
                "Todo pet merece voltar pra casa",
                style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.72f), textAlign = TextAlign.Center
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 44.dp).graphicsLayer { alpha = progress.value },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("From", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
            Spacer(Modifier.height(2.dp))
            Text(
                "André Campos",
                style = MaterialTheme.typography.titleMedium, color = Color.White, letterSpacing = 1.sp
            )
        }
    }
}
