import sys

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

# 1. Add viewModel parameter
content = content.replace(
    "fun WrapUpDialog(\n    data: WrapUpData",
    "fun WrapUpDialog(\n    viewModel: com.example.viewmodel.StromrufViewModel,\n    data: WrapUpData"
)

# 2. Update usages (there might be a few usages, usually one)
content = content.replace(
    "WrapUpDialog(\n                data = wrapUpData,",
    "WrapUpDialog(\n                viewModel = viewModel,\n                data = wrapUpData,"
)

# 3. Fix the Button action
content = content.replace(
    "(LocalContext.current as? MainActivity)?.viewModel ?: return@Dialog",
    "viewModel"
)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)
print("Added viewModel parameter to WrapUpDialog")
