# 🤖 STROMRUF KI-Agent Integration

## 🎯 Überblick

Dieses Paket ermöglicht eine **vollständige KI-Kontrolle über STROMRUF** mittels Anthropic Claude.

**Wer kontrolliert was?**
- 👤 User gibt natürlichsprachliche Anweisungen in der App (z.B. "Erstelle Kontakt Max Müller, 0176123456").
- 🤖 Claude führt alle Operationen automatisch aus.
- 📊 Volle Daten-Lesezugriff und Schreibzugriff auf Kontakte, Anrufe und Wiedervorlagen.

---

## 🏗️ Architektur

```
Android App (AiAgentClient + AiAgentScreen)
           ↓ (HTTP POST + Bearer Token)
Supabase Edge Function (ai-agent/index.ts)
           ↓
Claude / Anthropic API (Tool Calling Loop)
           ↓
Tool Handlers (contacts, followups, call_logs)
           ↓
Supabase Database (PostgreSQL)
```

---

## 🚀 Schnellstart

1. Setze deine Secrets in Supabase:
   ```bash
   supabase secrets set ANTHROPIC_API_KEY="sk-ant-..."
   supabase secrets set AI_AGENT_API_KEY="geheim123"
   ```
2. Deploye die Edge Function:
   ```bash
   supabase functions deploy ai-agent --no-verify
   ```
3. Starte die Android-App, wechsle zum **AI Agent** Tab und probiere es aus!
