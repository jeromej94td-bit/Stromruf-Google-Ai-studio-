import sys

# 1. DialerInCallService
with open('./app/src/main/java/com/example/service/DialerInCallService.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
skip = False
for l in lines:
    if 'var instance: DialerInCallService?' in l:
        # Keep only the first one
        if not any('var instance: DialerInCallService?' in x for x in new_lines):
            new_lines.append(l)
    elif 'fun updateBubble' in l:
        if not any('fun updateBubble' in x for x in new_lines):
            new_lines.append(l)
    else:
        new_lines.append(l)

with open('./app/src/main/java/com/example/service/DialerInCallService.kt', 'w') as f:
    f.writelines(new_lines)


# 2. DesignSystem.kt
with open('./app/src/main/java/com/example/ui/design/DesignSystem.kt', 'r') as f:
    ds_content = f.read()

ds_content = ds_content.replace('import androidx.compose.animation.core.rememberInfiniteTransition', 'import androidx.compose.animation.core.*\nimport androidx.compose.ui.graphics.graphicsLayer\nimport androidx.compose.animation.core.rememberInfiniteTransition')
with open('./app/src/main/java/com/example/ui/design/DesignSystem.kt', 'w') as f:
    f.write(ds_content)

# 3. MainActivity.kt
with open('./app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    main_content = f.read()

main_content = main_content.replace('fun CallScreen(', '@Composable\nfun CallScreen(')
# also remove duplicate @Composable if it already existed
main_content = main_content.replace('@Composable\n@Composable\nfun CallScreen(', '@Composable\nfun CallScreen(')

with open('./app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(main_content)

# 4. LeadsScreen.kt (Color, RoundedCornerShape, background)
with open('./app/src/main/java/com/example/ui/screens/LeadsScreen.kt', 'r') as f:
    ls_content = f.read()

ls_content = ls_content.replace('import androidx.compose.ui.graphics.Color', 'import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.unit.sp\nimport androidx.compose.foundation.background\nimport androidx.compose.foundation.shape.RoundedCornerShape')

with open('./app/src/main/java/com/example/ui/screens/LeadsScreen.kt', 'w') as f:
    f.write(ls_content)

