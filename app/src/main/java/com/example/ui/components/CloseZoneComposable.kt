package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CloseZoneGlow
import com.example.ui.theme.CloseZoneRed

@Composable
fun CloseZoneComposable(
    isHovered: Boolean,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.25f else 1.0f,
        animationSpec = tween(durationMillis = 180),
        label = "close_zone_scale"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            if (isHovered) CloseZoneRed else CloseZoneRed.copy(alpha = 0.8f),
                            if (isHovered) CloseZoneGlow else Color(0x33EF4444),
                            Color.Transparent
                        )
                    )
                )
                .border(
                    width = if (isHovered) 2.5.dp else 1.5.dp,
                    color = if (isHovered) Color.White else CloseZoneRed,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isHovered) Icons.Default.Delete else Icons.Default.Close,
                contentDescription = "Close Floating Orb",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isHovered) "Release to Close" else "Drag here to Close",
            color = if (isHovered) Color.White else Color(0xFFFCA5A5),
            fontSize = 11.sp
        )
    }
}
