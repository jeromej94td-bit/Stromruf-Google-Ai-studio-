import sys

with open("app/src/main/java/com/example/viewmodel/StromrufViewModel.kt", "r") as f:
    content = f.read()

# Remove the incorrectly placed data class
data_class = """data class CustomerMessageDraftState(
    val rawNote: String = "",
    val transcript: String = "",
    val subject: String = "",
    val body: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)
"""
content = content.replace(data_class, "")

# Remove the incorrectly placed methods at the end
import re
methods_start = r"    private val _customerMessageDraft = MutableStateFlow\(CustomerMessageDraftState\(\)\)"
methods_match = re.search(methods_start, content)
if methods_match:
    content_before_methods = content[:methods_match.start()]
    methods_content = content[methods_match.start():]
    # The last brace of the file is at the end of methods_content
    # Let's extract the methods string properly.
else:
    print("Methods not found!")
    sys.exit(1)

# we just need to get the methods string and put it before the closing brace of StromrufViewModel.
# The class StromrufViewModel ends right before "class StromrufViewModelFactory"
# So let's find that.

class_factory_idx = content_before_methods.find("class StromrufViewModelFactory")
if class_factory_idx != -1:
    # Find the closing brace of StromrufViewModel before class_factory_idx
    closing_brace_idx = content_before_methods.rfind("}", 0, class_factory_idx)
    if closing_brace_idx != -1:
        # We want to insert the methods_content (minus the last '}') right before this closing brace.
        # But wait, methods_content has the last '}' that we added.
        methods_content_clean = methods_content.rstrip().rstrip("}")
        
        new_content = (
            data_class + "\n" +
            content_before_methods[:closing_brace_idx] +
            "\n" + methods_content_clean + "\n" +
            content_before_methods[closing_brace_idx:]
        )
        
        with open("app/src/main/java/com/example/viewmodel/StromrufViewModel.kt", "w") as f:
            f.write(new_content)
        print("Fixed!")
    else:
        print("Closing brace not found!")
else:
    print("Factory not found!")
