import sys

file_path = "app/src/main/java/com/example/MainActivity.kt"
out_path = "app/src/main/java/com/example/MainActivity_fixed.kt"

try:
    with open(file_path, "r", encoding="utf-8") as f:
        corrupted_str = f.read()
    
    # Try to reverse the latin-1 to utf-8 mojibake
    original_bytes = corrupted_str.encode("latin-1")
    original_str = original_bytes.decode("utf-8")
    
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(original_str)
    
    print("Successfully reversed encoding corruption!")
except Exception as e:
    print(f"Failed: {e}")

