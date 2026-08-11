import sys

# 1. Fix DesignSystem.kt imports
with open('./app/src/main/java/com/example/ui/design/DesignSystem.kt', 'r') as f:
    ds_content = f.read()

ds_content = ds_content.replace('import com.example.ui.theme.com.example.ui.theme.Emerald.copy(alpha=0.2f)', 'import com.example.ui.theme.Emerald')

# Change pulsatingAura back to normal imports
pulsating_bad = '''@Composable
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

pulsating_good = '''@Composable
fun Modifier.pulsatingAura(color: androidx.compose.ui.graphics.Color): Modifier {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "alpha"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "scale"
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
ds_content = ds_content.replace(pulsating_bad, pulsating_good)

# Add missing imports to DesignSystem.kt
imports = '''import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
'''
ds_content = ds_content.replace('import androidx.compose.animation.core.rememberInfiniteTransition', imports + 'import androidx.compose.animation.core.rememberInfiniteTransition')

with open('./app/src/main/java/com/example/ui/design/DesignSystem.kt', 'w') as f:
    f.write(ds_content)

# 2. Fix MainActivity.kt
with open('./app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    main_content = f.read()

# Add missing imports to MainActivity
main_imports = '''import com.example.ui.design.pulsatingAura
'''
main_content = main_content.replace('import com.example.ui.theme.MyApplicationTheme', main_imports + 'import com.example.ui.theme.MyApplicationTheme')

# Add @Composable to CallScreen if it's missing (it shouldn't be, but maybe it is)
if '@Composable\nfun CallScreen' not in main_content:
    main_content = main_content.replace('fun CallScreen(', '@Composable\nfun CallScreen(')

with open('./app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(main_content)

# 3. Fix HeuteScreen.kt imports
with open('./app/src/main/java/com/example/ui/screens/HeuteScreen.kt', 'r') as f:
    heute_content = f.read()

heute_imports = '''import com.example.ui.design.pulsatingAura
import com.example.ui.theme.EmeraldDim
'''
if 'pulsatingAura' not in heute_content[:500]:
    heute_content = heute_content.replace('import com.example.ui.theme.Emerald', heute_imports + 'import com.example.ui.theme.Emerald')

with open('./app/src/main/java/com/example/ui/screens/HeuteScreen.kt', 'w') as f:
    f.write(heute_content)

# 4. Fix LeadsScreen.kt imports
with open('./app/src/main/java/com/example/ui/screens/LeadsScreen.kt', 'r') as f:
    leads_content = f.read()

leads_imports = '''import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
'''
if 'RoundedCornerShape' not in leads_content[:500]:
    leads_content = leads_content.replace('import androidx.compose.ui.graphics.Color', leads_imports + 'import androidx.compose.ui.graphics.Color')

with open('./app/src/main/java/com/example/ui/screens/LeadsScreen.kt', 'w') as f:
    f.write(leads_content)

