import sys

# Let's fix missing "}"
with open('/tmp/bad_service.kt', 'r') as f:
    lines = f.readlines()

# We need to find all `companion object {` blocks.
# And just re-structure the file manually.
# In `bad_service.kt`, what do we have?
# It's better to just pull down the original contents from a script!
