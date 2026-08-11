import sys

with open('./app/src/main/java/com/example/ui/screens/DialerCard.kt', 'r') as f:
    content = f.read()

content = content.replace('import com.example.model.ContactEntity\nimport com.example.model.CallLogEntity', 'import com.example.database.ContactEntity\nimport com.example.database.CallLogEntity\nimport com.example.ui.design.*')

content = content.replace('log.number', 'log.phoneNumber')
content = content.replace('CardBg', 'SlateElevated')
content = content.replace('Cobalt', 'Cyan')

with open('./app/src/main/java/com/example/ui/screens/DialerCard.kt', 'w') as f:
    f.write(content)

with open('./app/src/main/java/com/example/ui/screens/StatistikenScreen.kt', 'r') as f:
    content = f.read()

content = content.replace('import com.example.ui.theme.*', 'import com.example.ui.theme.*\nimport com.example.ui.design.*')
content = content.replace('Cobalt', 'Cyan')

with open('./app/src/main/java/com/example/ui/screens/StatistikenScreen.kt', 'w') as f:
    f.write(content)
