import sys

file_path = "app/src/main/java/com/example/MainActivity.kt"

with open(file_path, "r", encoding="utf-8") as f:
    lines = f.readlines()

split_idx = -1
for i, line in enumerate(lines):
    if "RECONSTRUCTED MISSING COMPONENTS" in line:
        split_idx = i - 1
        break

if split_idx == -1:
    print("Cannot find split_idx")
    sys.exit(1)

content = "".join(lines[:split_idx])

# Count brackets
open_b = content.count("{")
close_b = content.count("}")

print(f"Open: {open_b}, Close: {close_b}")

if open_b > close_b:
    diff = open_b - close_b
    closing_str = ("}\n" * diff)
elif close_b > open_b:
    print("Too many closing brackets!")
    closing_str = ""
else:
    closing_str = ""

# Rewrite file with exact brackets
with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)
    f.write(closing_str)
    # Write the remaining part (reconstructed functions)
    f.write("".join(lines[split_idx:]))

print("Fixed brackets!")
