# ensure app_name is Stromruf in strings.xml
with open("app/src/main/res/values/strings.xml", "r") as f:
    content = f.read()

if "My Application" in content:
    content = content.replace("My Application", "Stromruf")
    with open("app/src/main/res/values/strings.xml", "w") as f:
        f.write(content)
