import sys

with open('./app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

# Replace futuristic3DBackground
new_modifier = '''@Composable
fun Modifier.futuristic3DBackground(style: String): Modifier {
    val config = com.example.ui.theme.getThemeStyleConfig(style)
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
    
    // Smooth animation for light positions
    val animOffset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(15000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        )
    )
    val animOffset2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(18000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        )
    )

    return this.then(
        Modifier.drawBehind {
            drawRect(color = config.baseBackground)
            
            // Pulsating organic gradient (Mesh-like)
            drawRect(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(config.glowColor1.copy(alpha = 0.35f), androidx.compose.ui.graphics.Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * (0.2f + 0.6f * animOffset1), size.height * (0.1f + 0.3f * animOffset2)),
                    radius = size.maxDimension * 0.8f
                )
            )
            
            drawRect(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(config.glowColor2.copy(alpha = 0.30f), androidx.compose.ui.graphics.Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * (0.8f - 0.4f * animOffset2), size.height * (0.7f + 0.2f * animOffset1)),
                    radius = size.maxDimension * 0.9f
                )
            )
            
            // Add a subtle tech grid but no hard circles
            val gridSpacing = 44.dp.toPx()
            for (x in 0..size.width.toInt() step gridSpacing.toInt()) {
                drawLine(
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.015f),
                    start = androidx.compose.ui.geometry.Offset(x.toFloat(), 0f),
                    end = androidx.compose.ui.geometry.Offset(x.toFloat(), size.height),
                    strokeWidth = 1f
                )
            }
            for (y in 0..size.height.toInt() step gridSpacing.toInt()) {
                drawLine(
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.015f),
                    start = androidx.compose.ui.geometry.Offset(0f, y.toFloat()),
                    end = androidx.compose.ui.geometry.Offset(size.width, y.toFloat()),
                    strokeWidth = 1f
                )
            }
        }
    )
}
'''

import re
# We need to remove the old fun Modifier.futuristic3DBackground(style: String) = drawBehind { ... }
# which is around 646 to 684. We can use a regex to replace it.
content = re.sub(r'fun Modifier\.futuristic3DBackground\(style: String\) = drawBehind \{.*?\n\}\n', new_modifier, content, flags=re.DOTALL)

with open('./app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
