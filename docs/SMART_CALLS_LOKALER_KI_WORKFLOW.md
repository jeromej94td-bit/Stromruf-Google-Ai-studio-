# Smart Calls: lokale Transkription, Notiz und Termin

## Ablauf

1. Smart Call endet und die WAV-Aufnahme wird geschlossen.
2. Bei Gesprächen über einer Minute startet die kostenlose lokale deutsche Whisper-Transkription automatisch.
3. Über den sichtbaren Button kann jedes Gespräch unabhängig von seiner Länge manuell transkribiert werden. Beim ersten Tippen wird der Modell-Download automatisch eingeplant; der Fortschritt steht direkt unter der Aufnahme.
4. Optional erstellt **Gemma 3n E2B** lokal eine kurze Gesprächsnotiz und den nächsten vereinbarten Schritt. Fehlt das Modell oder schlägt die Verarbeitung fehl, erzeugt die feste deutsche Regel-Logik die Notiz weiter.
5. Nur bei Gesprächen über einer Minute sendet `SmartCallNoteWorker` ausschließlich die Zusammenfassung an `public.smartcall_notes` im Stromruf-Supabase-Projekt. WAV und vollständiges Transkript bleiben auf dem Gerät.
6. Die gespeicherte Notiz erscheint automatisch unter **Agents → Smart Calls → Gespeicherte Smart-Call-Notizen**. Eindeutige deutsche Termine werden zusätzlich als lokale Wiedervorlage samt Alarm angelegt.

## Gemma 3n E2B installieren

Die App enthält die LiteRT-LM-Laufzeit. Das Modell wird nicht in die APK gepackt, weil es mehrere GB groß ist und vor dem Download einmal die Gemma-Lizenz akzeptiert werden muss. In der App:

1. **Modellseite öffnen**.
2. Lizenz akzeptieren und die Google-Datei `.litertlm` für `gemma-3n-E2B-it` herunterladen.
3. **Heruntergeladenes Modell installieren** wählen und die Datei auswählen.

Danach arbeitet die lokale KI vollständig offline. Sie ist eine Ergänzung zur Terminregel: Einen konkreten Termin legt die App nur an, wenn der Gesprächstext ihn eindeutig enthält; bei Angebots- oder Rückrufvereinbarungen ohne Datum nutzt sie die bestehende Wiedervorlage-Logik.
