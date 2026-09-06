# Automatische Verarbeitung nach SIP-Anrufen

Stand: 06.09.2026, Basis main `96fd9b9`.

## Ursache und Änderung

Die Gesprächsliste rief alle zwei Sekunden `scanExisting()` auf. Dieses ersetzte
laufende Primary-WorkManager-Aufträge mit `REPLACE`. Außerdem wurden lokale
Fallback-Aufträge beim Wiederaufnehmen wieder zum Primary geschickt.

- Pro Dateiname gibt es nun jeweils einen eindeutigen Primary-, Fallback- und
  Notiz-Auftrag mit `KEEP`. Ein Listenneuaufbau ersetzt keinen laufenden Auftrag.
- Nach dem Auflegen und Abschluss der WAV-Datei wird ein dauerhafter Auftrag
  mit Startzeit `callEndedAt + 90 Sekunden` angelegt. Android darf ihn wegen
  Energiesparregeln später ausführen. Weitere Anrufe verschieben diesen Zeitpunkt
  nicht und starten abgeschlossene Aufnahmen nicht erneut.
- Aktive Aufnahmen werden beim Scannen und in der Aufnahmeliste ausgelassen.
  Alte, bisher nicht erfasste Dateien werden erst nach 90 Sekunden ohne Änderung
  übernommen. Fehlerjobs bleiben sichtbar und können ausdrücklich erneut
  gestartet werden.
- Groq `whisper-large-v3` bleibt Primary. Fehlender Schlüssel, fehlendes Netz,
  HTTP-Fehler und das 90-Sekunden-Anfragelimit führen zum lokalen Base-q5_1-Fallback.
  Dateien über 25 MB werden lokal verarbeitet. Der vorhandene verschlüsselte
  Schlüsselmechanismus bleibt erhalten.
- Ein erfolgreiches Groq-Transkript wird vor Gemma gespeichert. Gemma hat ein
  separates 90-Sekunden-Limit; ohne Ergebnis greift die regelbasierte Analyse.
  Die vorhandenen editierbaren Gemma-Regeln und die Kundenfassung bleiben erhalten.
- Lokales Whisper arbeitet jeweils nur an einer Datei und setzt lange Jobs per
  WorkManager-Retry fort. Ein langsamer Abschnitt wird nicht mehr still übersprungen.

## Zuordnung, Audio und Speicherort

Kundennummern werden über normalisierte Telefonnummern aus Neukunden, heißen
Angeboten und zugesagten Annahmen aufgelöst. Mehrdeutige oder fehlende Nummern
bleiben ausdrücklich unzugeordnet. Neue fertige Aufnahmen mit eindeutiger Nummer
heißen `Call_KD_<Kundennummer>_Tel_<Telefon>_<Zeitstempel>.wav`.
Metadaten enthalten zusätzlich Kontaktbezug und Gesprächszeit. Gemma erhält diese
Zuordnung; die Notiz enthält sie auch beim regelbasierten Fallback.

Smart Calls bietet Anhören/Stoppen, Teilen/Drive, Ordnerauswahl, automatischen
Export sowie manuellen Export bestehender Aufnahmen. Die lokale Datei bleibt
für Wiedergabe und Verarbeitung erhalten. Ein Zielordner muss dauerhafte
Schreibrechte gewähren. Wenn Drive keine Ordnerauswahl anbietet, ist es über
das Android-Teilen-Menü nutzbar.

## Follow-ups und Datenschutz

Follow-ups werden zuerst über das Repository lokal angelegt und die Erinnerung
wird geplant. Cloud-Synchronisation wird separat erneut versucht. Stabile IDs
verhindern doppelte Termine nach einem Neustart. Ausdrücklich vereinbarte Zeiten
werden nicht durch die allgemeine Kollisionsverschiebung verändert. Relative
Termine beziehen sich auf den Gesprächstag, nicht auf den späteren Verarbeitungstag.
„Nächste Woche“ allein enthält keine eindeutige Uhrzeit und erzeugt deshalb
keinen erfundenen Termin.

Supabase verwendet weiterhin `smartcall_notes` und `followups`. Dorthin gehen nur
Notiz, Zuordnung und Metadaten, weder Audio noch vollständiges Transkript.
Audio verlässt das Gerät nur für Groq bzw. den gewählten Export.

## Prüfung und Grenzen

- Drei JUnit-Tests des tatsächlichen Kotlin-Terminplaners lokal bestanden,
  einschließlich morgen 18:00, Dienstag 14:30, Freitag 10:00, 12.09. 15:00 und
  nächster Montag mit Uhrzeit; ohne Uhrzeit wird kein Termin erfunden.
- Codeprüfung: Primary-vor-Fallback, unveränderter Base-Modellhash/-umfang,
  feste Verzögerung, KEEP-Policies, lokale Speicherung vor Cloud-Sync,
  Player-Freigabe und vorhandener FileProvider für das Teilen.
- `git diff --check` ohne Fehler. Keine GitHub-Actions-Langläufe gestartet.
- `HomeSipTrunk.kt` ist gegenüber dem aktuellen main bytegleich; Registrar,
  5061/TLS, SRTP, Zertifikatsprüfung, Outbound Proxy, Registrierung und
  Zugangsdatenhandling wurden nicht verändert. Bereits in main vorhandene
  Unterschiede zur ursprünglichen Gold-Master-Referenz bleiben erhalten.
- Kein vollständiger APK-Build: Im Checkout fehlen Gradle-Wrapper-JAR und
  Wrapper-Konfiguration. Kein Android-Gerät und kein Live-Groq-/Supabase-Test.
  Ein echter Anruf mit anschließendem Hintergrundlauf und die Wiedergabe auf
  dem Zielgerät bleiben als Geräteprüfung offen.
