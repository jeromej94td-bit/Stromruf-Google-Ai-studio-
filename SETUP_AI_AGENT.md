# 🤖 STROMRUF KI-Agent Setup-Handbuch

## Inhaltsverzeichnis
1. [Schnellstart](#schnellstart)
2. [Detaillierte Installation](#detaillierte-installation)
3. [Sicherheit & Best Practices](#sicherheit--best-practices)
4. [Troubleshooting](#troubleshooting)
5. [API-Referenz](#api-referenz)

---

## Schnellstart

### Was wird benötigt?
- ✅ Supabase-Projekt (bereits vorhanden: `yepluyipizbbrgoffqdq`)
- ✅ Anthropic API-Key (Claude)
- ✅ Android Studio / Kotlin
- ⏱️ ~15 Minuten Setup-Zeit

---

## Detaillierte Installation

### Phase 1: Anthropic-Vorbereitung

#### Step 1.1: API-Key erstellen

1. Gehe zu [Anthropic Console](https://console.anthropic.com)
2. Login / Registrierung
3. Gehe zu "API Keys"
4. Klicke "Create Key"
5. Kopiere den Key (Format: `sk-ant-...`)

---

### Phase 2: Supabase Edge Function

#### Step 2.1: Verzeichnis vorbereiten

Die Edge-Function-Dateien befinden sich bereits in deinem Projekt unter:
- `supabase/functions/ai-agent/index.ts`
- `supabase/functions/ai-agent/deno.json`

#### Step 2.2: Secrets setzen

Führe diese Befehle im Terminal aus, um die Secrets sicher in Supabase zu speichern:

```bash
# Terminal Login
supabase login

# Secrets setzen
supabase secrets set \
  ANTHROPIC_API_KEY="sk-ant-dein-key-hier" \
  AI_AGENT_API_KEY="geheim123"
```

#### Step 2.3: Deployen

```bash
supabase functions deploy ai-agent --no-verify
```

---

### Phase 3: Android App Integration

Die Android-App wurde bereits voll integriert! 

Folgende Dateien wurden angelegt und konfiguriert:
- `app/src/main/java/com/example/util/AiAgentClient.kt` (HTTP Client)
- `app/src/main/java/com/example/ui/AiAgentScreen.kt` (Compose Chat UI)
- `app/src/main/java/com/example/MainActivity.kt` (Navigationsintegration)

---

## Sicherheit & Best Practices

### 🔒 API-Keys Management
Der Android-Client liest den `AI_AGENT_API_KEY` sicher über Reflection aus der `BuildConfig` aus, sodass es zu keinen Kompilierungsfehlern kommt, wenn die Variable noch nicht in der lokalen `.env`-Datei eingetragen ist.

Um deine eigene, sichere API-Key-Schnittstelle zu konfigurieren, füge in deiner lokalen `.env` einfach folgende Zeile hinzu:
```env
AI_AGENT_API_KEY="dein-sicheres-passwort-hier"
```

---

## Troubleshooting

### Problem: "Unauthorized: Invalid API key"
**Lösung:**
1. Stelle sicher, dass der in deiner Edge-Function gesetzte `AI_AGENT_API_KEY` mit dem in der `.env`-Datei deines Android-Clients übereinstimmt.

### Problem: "Verbindungsfehler"
**Lösung:**
1. Überprüfe die Internetverbindung.
2. Prüfe, ob die Edge Function unter `yepluyipizbbrgoffqdq.supabase.co` erfolgreich deployt ist.
