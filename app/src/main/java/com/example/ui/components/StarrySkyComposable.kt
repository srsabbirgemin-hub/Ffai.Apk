package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

private data class Star(
    val xRatio: Float,
    val yRatio: Float,
    val radius: Float,
    val baseAlpha: Float,
    val speedFactor: Float
)

@Composable
fun StarrySkyComposable(
    modifier: Modifier = Modifier,
    starCount: Int = 45
) {
    val stars = remember {
        val rand = Random(42)
        List(starCount) {
            Star(
                xRatio = rand.nextFloat(),
                yRatio = rand.nextFloat(),
                radius = rand.nextFloat() * 1.8f + 0.6f,
                baseAlpha = rand.nextFloat() * 0.6f + 0.2f,
                speedFactor = rand.nextFloat() * 0.8f + 0.5f
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "star_twinkle")
    val twinkle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "twinkle_alpha"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        stars.forEach { star ->
            val dynamicAlpha = (star.baseAlpha + (twinkle * 0.4f * star.speedFactor)).coerceIn(0.1f, 1f)
            val starColor = if (star.speedFactor > 0.9f) {
                Color(0xFF67E8F9).copy(alpha = dynamicAlpha) // Cyan star
            } else if (star.speedFactor < 0.7f) {
                Color(0xFFE9D5FF).copy(alpha = dynamicAlpha) // Purple star
            } else {
                Color.White.copy(alpha = dynamicAlpha)
            }

            drawCircle(
                color = starColor,
                radius = star.radius,
                center = Offset(star.xRatio * width, star.yRatio * height)
            )
        }
    }
}
