import sys

with open("app/src/main/java/com/example/viewmodel/StromrufViewModel.kt", "r") as f:
    content = f.read()

data_class_lines = """data class CustomerMessageDraftState(
    val rawNote: String = "",
    val transcript: String = "",
    val subject: String = "",
    val body: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)
"""

if content.startswith("data class CustomerMessageDraftState"):
    # Remove it from the beginning
    content = content[len(data_class_lines):]

    # Find where to put it (after imports)
    import_idx = content.rfind("import ")
    end_of_imports = content.find("\n", import_idx) + 1
    
    new_content = content[:end_of_imports] + "\n" + data_class_lines + "\n" + content[end_of_imports:]
    
    with open("app/src/main/java/com/example/viewmodel/StromrufViewModel.kt", "w") as f:
        f.write(new_content)
    print("Fixed data class position!")
else:
    print("Data class not found at beginning!")
