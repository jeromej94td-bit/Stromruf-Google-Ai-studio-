import sys

# 1. DialerInCallService.kt
with open('./app/src/main/java/com/example/service/DialerInCallService.kt', 'r') as f:
    ds = f.read()

ds = ds.replace('            updateBubble()\n            activeCallNumber.value = ""\n            activeCallName.value = ""', '            instance?.updateBubble()\n            activeCallNumber.value = ""\n            activeCallName.value = ""')

with open('./app/src/main/java/com/example/service/DialerInCallService.kt', 'w') as f:
    f.write(ds)

# 2. DesignSystem.kt
with open('./app/src/main/java/com/example/ui/design/DesignSystem.kt', 'r') as f:
    ds = f.read()

ds = ds.replace('size = size,', 'size = this.size,')

with open('./app/src/main/java/com/example/ui/design/DesignSystem.kt', 'w') as f:
    f.write(ds)
