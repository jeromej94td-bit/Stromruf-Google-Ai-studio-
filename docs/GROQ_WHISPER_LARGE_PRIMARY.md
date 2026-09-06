# Smart Calls – Groq Whisper Large v3 als Haupttranskriptor

Smart Calls nutzt für neue Transkriptionen primär Groq `whisper-large-v3`.

Ablauf:
1. Nach dem Gespräch wird die vorhandene WAV-Aufnahme wie bisher automatisch zur Transkription eingeplant.
2. Ist ein Groq API-Key hinterlegt, wird die Aufnahme an `https://api.groq.com/openai/v1/audio/transcriptions` gesendet.
3. Modell: `whisper-large-v3`, Sprache fest `de`, Temperatur `0`.
4. Ein Stromruf-Domänenprompt hilft bei Begriffen wie Strom, Gas, Arbeitspreis, Marktlokationsnummer, Zählernummer, Abschlag und Preisgarantie.
5. Das fertige Transkript läuft anschließend durch die bestehende Notiz-/Gemma-/Supabase-/Wiedervorlagenlogik.
6. Falls kein Groq-Key vorhanden ist oder Groq nicht erreichbar ist, fällt die App automatisch auf das lokale Whisper-Small-Modell zurück.

Der Groq-Key wird verschlüsselt über `SecureIntegrationSettings` gespeichert. Alternativ kann `GROQ_API_KEY` als Build-Secret bereitgestellt werden. Ein echter Schlüssel darf niemals in GitHub eingecheckt werden.

Die SIP-/Linphone-Registrierung und Telefonie wurden für diese Änderung nicht verändert.
