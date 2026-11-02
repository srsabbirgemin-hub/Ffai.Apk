package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.remote.LiveCallState
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonRose
import com.example.ui.theme.NeonTeal

fun getOrbColors(colorName: String): Pair<Color, Color> {
    return when (colorName.uppercase()) {
        "TEAL" -> Pair(NeonTeal, NeonCyan)
        "CYAN" -> Pair(NeonCyan, Color(0xFF38BDF8))
        "ROSE" -> Pair(NeonRose, Color(0xFFFB7185))
        "AMBER" -> Pair(NeonAmber, Color(0xFFFBBF24))
        "EMERALD" -> Pair(NeonEmerald, Color(0xFF34D399))
        else -> Pair(NeonPurple, NeonTeal)
    }
}

@Composable
fun GlowingOrbComposable(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    colorName: String = "PURPLE",
    callState: LiveCallState = LiveCallState.IDLE,
    amplitude: Float = 0f,
    opacity: Float = 1.0f
) {
    val (primaryColor, secondaryColor) = getOrbColors(colorName)

    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_rotation"
    )

    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_breathing"
    )

    val pulseScale = remember { Animatable(1f) }
    LaunchedEffect(amplitude) {
        if (amplitude > 0.05f) {
            pulseScale.animateTo(
                targetValue = (1f + amplitude * 0.4f).coerceAtMost(1.4f),
                animationSpec = tween(durationMillis = 60)
            )
            pulseScale.animateTo(1f, animationSpec = tween(durationMillis = 140))
        }
    }

    val finalScale = when (callState) {
        LiveCallState.SPEAKING, LiveCallState.LISTENING -> pulseScale.value
        LiveCallState.THINKING -> breathingScale * 1.08f
        else -> breathingScale
    }

    val stateColor = when (callState) {
        LiveCallState.LISTENING -> NeonCyan
        LiveCallState.SPEAKING -> NeonTeal
        LiveCallState.THINKING -> NeonPurple
        LiveCallState.MUTED -> Color(0xFFEF4444)
        LiveCallState.ERROR -> Color(0xFFF87171)
        else -> primaryColor
    }

    Box(
        modifier = modifier
            .size(size)
            .scale(finalScale),
        contentAlignment = Alignment.Center
    ) {
        // Outer glowing halo canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerOffset = Offset(this.size.width / 2f, this.size.height / 2f)
            val maxRadius = this.size.minDimension / 2f

            // Outer soft glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        stateColor.copy(alpha = 0.6f * opacity),
                        secondaryColor.copy(alpha = 0.3f * opacity),
                        Color.Transparent
                    ),
                    center = centerOffset,
                    radius = maxRadius
                ),
                radius = maxRadius,
                center = centerOffset
            )
        }

        // Animated rotating energy ring
        Box(
            modifier = Modifier
                .fillMaxSize(0.85f)
                .rotate(rotation)
                .border(
                    width = 2.dp,
                    brush = Brush.sweepGradient(
                        listOf(
                            stateColor.copy(alpha = 0.9f * opacity),
                            secondaryColor.copy(alpha = 0.8f * opacity),
                            stateColor.copy(alpha = 0.2f * opacity),
                            stateColor.copy(alpha = 0.9f * opacity)
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Core Glowing Sphere
        Box(
            modifier = Modifier
                .fillMaxSize(0.72f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.9f * opacity),
                            stateColor.copy(alpha = 0.85f * opacity),
                            secondaryColor.copy(alpha = 0.7f * opacity),
                            Color(0xFF0A071E).copy(alpha = 0.9f * opacity)
                        )
                    )
                )
        )

        // Specular highlight on top-left for 3D holographic orb depth
        Box(
            modifier = Modifier
                .fillMaxSize(0.35f)
                .align(Alignment.TopStart)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.75f * opacity),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}
