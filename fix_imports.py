file_path = "app/src/main/java/com/example/ui/screens/AgentCallScreen.kt"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

imports = """
import android.content.Context
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import android.widget.Toast
"""

content = content.replace("package com.example.ui.screens", "package com.example.ui.screens\n" + imports)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

