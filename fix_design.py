import sys

with open('./app/src/main/java/com/example/ui/design/DesignSystem.kt', 'r') as f:
    content = f.read()

# I used:
# animateFloat -> androidx.compose.animation.core.animateFloat
# toPx -> .toPx() from LocalDensity
# EmeraldDim -> com.example.ui.theme.EmeraldDim
# graphicsLayer -> androidx.compose.ui.graphics.graphicsLayer

# Let's fix pulsatingAura in DesignSystem.kt

target_pulsating = '''@Composable
fun Modifier.pulsatingAura(color: androidx.compose.ui.graphics.Color): Modifier {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        )
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        )
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.drawBehind {
        drawRoundRect(
            color = color.copy(alpha = alpha),
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx(), 8.dp.toPx())
        )
    }
}'''

replacement_pulsating = '''@Composable
fun Modifier.pulsatingAura(color: androidx.compose.ui.graphics.Color): Modifier {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
    val alpha by androidx.compose.animation.core.animateFloat(
        infiniteTransition,
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "alpha"
    )
    val scale by androidx.compose.animation.core.animateFloat(
        infiniteTransition,
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "scale"
    )
    return this.androidx.compose.ui.graphics.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.drawBehind {
        drawRoundRect(
            color = color.copy(alpha = alpha),
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx(), 8.dp.toPx())
        )
    }
}'''
content = content.replace(target_pulsating, replacement_pulsating)

# And `EmeraldDim`
content = content.replace('EmeraldDim', 'com.example.ui.theme.Emerald.copy(alpha=0.2f)')

with open('./app/src/main/java/com/example/ui/design/DesignSystem.kt', 'w') as f:
    f.write(content)

# And missing CreateNewFolder in LeadsScreen.kt
with open('./app/src/main/java/com/example/ui/screens/LeadsScreen.kt', 'r') as f:
    content_leads = f.read()

content_leads = content_leads.replace('Icons.Default.CreateNewFolder', 'Icons.Default.PersonAdd')

with open('./app/src/main/java/com/example/ui/screens/LeadsScreen.kt', 'w') as f:
    f.write(content_leads)
