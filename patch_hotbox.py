import sys

with open('./app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

# Replace "modifier = Modifier.height(44.dp)" for the Hotbox button with pulsatingAura
import re
target = r'modifier = Modifier\.height\(44\.dp\)\n\s*\) \{\n\s*Row\(\n\s*verticalAlignment = Alignment\.CenterVertically,\n\s*horizontalArrangement = Arrangement\.spacedBy\(8\.dp\)\n\s*\) \{\n\s*Icon\(\n\s*imageVector = Icons\.Default\.Star,\n\s*contentDescription = "In Hotbox"'
replacement = r'modifier = Modifier.height(44.dp).then(if (isHot) Modifier.pulsatingAura(Color(0xFFEF4444).copy(alpha=0.3f)) else Modifier.pulsatingAura(Color(0xFF00FF87).copy(alpha=0.3f)))\n                            ) {\n                                Row(\n                                    verticalAlignment = Alignment.CenterVertically,\n                                    horizontalArrangement = Arrangement.spacedBy(8.dp)\n                                ) {\n                                    Icon(\n                                        imageVector = Icons.Default.Star,\n                                        contentDescription = "In Hotbox"'
content = re.sub(target, replacement, content)

with open('./app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
