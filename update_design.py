import sys

with open('./app/src/main/java/com/example/ui/design/DesignSystem.kt', 'r') as f:
    content = f.read()

# Let's add a pulsating aura modifier
pulsating_modifier = '''
@Composable
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
}
'''

# Wait, we need graphicsLayer to be imported, let's just use FQDNs.
# Let's insert it before SelectChip
import re
content = content.replace('@Composable\nfun SelectChip(', pulsating_modifier + '\n@Composable\nfun SelectChip(')

# Now, update SegmentedControl to use pulsatingAura on the selected segment
segment_replace = '''                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        color = if (isSelected) EmeraldDim else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )'''
segment_new = '''                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .then(if (isSelected) Modifier.pulsatingAura(EmeraldDim) else Modifier)
                                    .background(
                                        color = if (isSelected) EmeraldDim else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )'''
content = content.replace(segment_replace, segment_new)

# Make AppCard more 3D and metallic
appcard_replace = '''fun AppCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dim.cardRadius),
        colors = CardDefaults.cardColors(containerColor = SlateElevated),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {'''

appcard_new = '''fun AppCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dim.cardRadius),
        colors = CardDefaults.cardColors(containerColor = SlateElevated),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Brush.linearGradient(
            colors = listOf(Color.White.copy(alpha = 0.3f), Color.Transparent, Color.Black.copy(alpha = 0.5f))
        ))
    ) {'''
content = content.replace(appcard_replace, appcard_new)

with open('./app/src/main/java/com/example/ui/design/DesignSystem.kt', 'w') as f:
    f.write(content)
