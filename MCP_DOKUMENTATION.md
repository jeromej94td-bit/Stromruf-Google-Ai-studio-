# Stromruf CRM - Model Context Protocol (MCP) Dokumentation 🚀

Diese Dokumentation erklärt, wie du den integrierten **MCP-Server** (`stromruf_mcp.py`) nutzt, um dein Stromruf-CRM (Leads, Kontakte, Anrufprotokolle, Wiedervorlagen) mit einer anderen KI wie **Claude Desktop**, **Windsurf** oder **Cursor** zu verbinden. 

Dadurch kann die externe KI direkt auf deine Stromruf-Datenbank in Supabase zugreifen, neue Kontakte anlegen, Anrufe protokollieren, heiße Angebote pflegen und Wiedervorlagen organisieren!

---

## 📋 Voraussetzungen

Der MCP-Server ist in **reinem Python 3** geschrieben und benötigt **keine externen Bibliotheken** (kein `pip install` nötig!). Er läuft out-of-the-box auf jedem System mit installiertem Python.

- **Python 3.x** auf deinem Computer installiert.
- **Anmeldemethode**:
  - **Option A (Standard E-Mail/Passwort)**: Deine Stromruf-Zugangsdaten.
  - **Option B (Gmail / Google OAuth / Passwortlos)**: Wenn du mit Google/Gmail angemeldet bist, öffnest du einfach in deiner Stromruf-App die **Einstellungen (Zahnrad-Symbol)**, scrollst nach unten zu **"MCP-Kopplung"** und klickst auf **"Sitzungs-Token kopieren"**. Diesen Token kannst du dann direkt verwenden.

---

## 🛠️ Installation & Setup (Lokal)

1. **Datei kopieren**: Kopiere die Datei `stromruf_mcp.py` aus diesem Repository auf deinen lokalen Computer (z. B. nach `C:\stromruf\stromruf_mcp.py` oder `/Users/deinname/stromruf/stromruf_mcp.py`).
2. **Ausführbar machen (Mac/Linux)**:
   ```bash
   chmod +x /Users/deinname/stromruf/stromruf_mcp.py
   ```

---

## ⚙️ Integration in externe KIs

Hier sind die fertigen Konfigurationen für die beliebtesten KI-Tools:

### 1. Claude Desktop (Empfohlen)
Öffne die Konfigurationsdatei von Claude Desktop. Du findest sie hier:
- **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`
- **macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`

Füge unter `mcpServers` folgenden Eintrag hinzu:

#### Bei Anmeldung per Gmail / Google OAuth (Empfohlen):
```json
{
  "mcpServers": {
    "stromruf-crm": {
      "command": "python3",
      "args": [
        "/Users/deinname/stromruf/stromruf_mcp.py"
      ],
      "env": {
        "STROMRUF_ACCESS_TOKEN": "DEIN_KOPIERTER_SITZUNGS_TOKEN_AUS_DER_APP"
      }
    }
  }
}
```

#### Bei Anmeldung per klassischem Passwort:
```json
{
  "mcpServers": {
    "stromruf-crm": {
      "command": "python3",
      "args": [
        "/Users/deinname/stromruf/stromruf_mcp.py"
      ],
      "env": {
        "SUPABASE_EMAIL": "deine-email@example.com",
        "SUPABASE_PASSWORD": "dein-passwort-hier"
      }
    }
  }
}
```
*(Ersetze `/Users/deinname/stromruf/stromruf_mcp.py` mit dem tatsächlichen absoluten Pfad zu deiner Datei. Windows-Nutzer verwenden `python` anstelle von `python3`.)*

### 2. Windsurf (Code Editor)
Erstelle oder öffne deine globale Windsurf-MCP-Konfigurationsdatei (`~/.codeium/windsurf/mcp_config.json`):

#### Bei Gmail / Google OAuth:
```json
{
  "mcpServers": {
    "stromruf-crm": {
      "command": "python3",
      "args": [
        "/absolute/path/to/stromruf_mcp.py"
      ],
      "env": {
        "STROMRUF_ACCESS_TOKEN": "DEIN_KOPIERTER_SITZUNGS_TOKEN_AUS_DER_APP"
      }
    }
  }
}
```

#### Bei Passwort:
```json
{
  "mcpServers": {
    "stromruf-crm": {
      "command": "python3",
      "args": [
        "/absolute/path/to/stromruf_mcp.py"
      ],
      "env": {
        "SUPABASE_EMAIL": "deine-email@example.com",
        "SUPABASE_PASSWORD": "dein-passwort"
      }
    }
  }
}
```

### 3. Cursor
1. Öffne die Cursor-Einstellungen (**Settings** -> **Features** -> **MCP**).
2. Klicke auf **+ Add New MCP Server**.
3. Gib folgende Werte ein:
   - **Name**: `stromruf-crm`
   - **Type**: `command`
   - **Command**: `python3 /absoluter/pfad/zu/stromruf_mcp.py` (bzw. unter Windows `python C:\pfad\zu\stromruf_mcp.py`)
4. Füge die entsprechende Umgebungsvariable (Environment Variable) hinzu:
   - **Wenn Gmail**:
     - Key: `STROMRUF_ACCESS_TOKEN` -> Value: `DEIN_KOPIERTER_SITZUNGS_TOKEN`
   - **Wenn Passwort**:
     - Key: `SUPABASE_EMAIL` -> Value: `deine-email@example.com`
     - Key: `SUPABASE_PASSWORD` -> Value: `dein-passwort`

---

## 🤖 Wie gebe ich das einer anderen KI? (System Prompt)

Wenn du ein neues Chat-Fenster in Claude, Windsurf oder Cursor öffnest, kannst du der KI einfach folgenden Prompt senden. Kopiere diesen Text und füge ihn ein:

> **PROMPT FÜR DIE ANDERE KI:**
> 
> "Hallo! Du hast über das Model Context Protocol (MCP) Zugriff auf mein Stromruf-CRM-System ('stromruf-crm'). Dieses System verwaltet meine Leads, Kontakte, heiße Angebote, Anrufprotokolle und Wiedervorlagen.
> 
> Bitte unterstütze mich aktiv bei der Organisation meines Vertriebstags. Du hast Zugriff auf folgende Werkzeuge:
> - `list_contacts`: Holt meine CRM-Kontakte (Suche nach Name oder Telefonnummer möglich).
> - `upsert_contact`: Erstellt neue Kontakte oder aktualisiert bestehende (z. B. Hotbox-Status oder Anrufgrund ändern).
> - `list_followups` / `upsert_followup`: Verwaltet meine Wiedervorlage-Termine.
> - `list_call_logs` / `insert_call_log`: Zeigt meine getätigten Telefonate oder trägt neue Gesprächsergebnisse ein.
> - `list_neukunden` / `upsert_neukunde`: Holt oder erstellt frische Leads.
> - `list_customer_messages`: Zeigt eingegangene Kundennachrichten und Transkripte.
> 
> Bitte beginne damit, meine aktuellen Wiedervorlagen (`list_followups`) und meine neuesten Leads (`list_neukunden`) zu laden, damit wir sehen, wer heute angerufen werden muss. Schlage mir anschließend die nächsten 3 besten Anrufe vor!"

---

## 🛠️ Liste der verfügbaren MCP-Werkzeuge (Tools)

Sobald verbunden, verfügt die KI über diese mächtigen Funktionen:

| Werkzeug | Beschreibung | Parameter |
| :--- | :--- | :--- |
| `login` | Meldet den Server manuell an, falls keine Umgebungsvariablen gesetzt sind. | `email`, `password` |
| `list_contacts` | Holt Kontaktdaten aus der Datenbank. | `limit` (Standard 50), `search` (Suche) |
| `upsert_contact` | Legt Kontakt an oder ändert diesen (Hotbox-Zuweisung, Anrufgrund, etc.). | `id`, `name`, `phone`, `company`, `email`, `is_hot_box`, `call_reason` |
| `delete_contact` | Löscht einen Kontakt dauerhaft. | `id` |
| `list_followups` | Zeigt geplante Wiedervorlagen. | `limit` |
| `upsert_followup` | Erstellt/Verschiebt Wiedervorlage-Termine. | `id`, `contact_id`, `contact_name`, `contact_phone`, `due_at_ms`, `notes` |
| `delete_followup` | Löscht eine Wiedervorlage. | `id` |
| `list_call_logs` | Ruft Gesprächshistorie ab. | `limit` |
| `insert_call_log` | Protokolliert ein geführtes Gespräch (Dauer, Ergebnis, Notiz). | `phone`, `contact_name`, `duration_seconds`, `outcome`, `call_type`, `notes` |
| `list_neukunden` | Zeigt alle frisch importierten Neukunden/Leads. | `limit` |
| `upsert_neukunde` | Erstellt oder aktualisiert einen Lead-Status. | `id`, `name`, `phone`, `email`, `company`, `status` |
| `list_customer_messages`| Lädt eingegangene Anruf-Transkripte oder SMS/Nachrichten. | `limit` |

Viel Erfolg bei der Vertriebs-Automatisierung mit Stromruf und deiner Lieblings-KI! 🚀
