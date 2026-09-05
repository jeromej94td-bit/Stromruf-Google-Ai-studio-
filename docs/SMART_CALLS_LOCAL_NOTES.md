# Smart Calls: automatische lokale Gesprächsnotizen

## Ablauf

1. Ein Smart Call mit mehr als 60 Sekunden wird nach dem Auflegen als WAV **nur auf dem Android-Gerät** gespeichert.
2. Der lokale Whisper-Worker transkribiert die Aufnahme auf Deutsch.
3. `GermanCallSummary` bildet daraus auf dem Gerät eine kurze Gesprächsnotiz. Dabei werden Hinweise wie Angebot, Interesse, Termin oder Rückruf hervorgehoben.
4. `SmartCallNoteWorker` speichert ausschließlich diese Zusammenfassung in `public.smartcall_notes` des Stromruf-Supabase-Projekts.
5. Erkennt der Text einen konkreten Rückruftermin, legt die App eine Wiedervorlage unter **Heute** an und plant die Android-Erinnerung.
6. Die App zeigt die gespeicherten Notizen in **Agents → Smart Calls → Gespeicherte Smart-Call-Notizen** an und aktualisiert die Liste automatisch.

## Datenschutz

- Keine WAV-/Audiodatei wird hochgeladen.
- Das vollständige Transkript bleibt auf dem Android-Gerät.
- In Supabase stehen nur Rufnummer, Zeitpunkt, Gesprächsdauer, Dateiname und die kurze Zusammenfassung.

## Zuverlässigkeit

Die Speicherung hat eine eindeutige ID aus Benutzer und Dateiname. Ein erneuter Versuch erzeugt daher keine doppelten Notizen. Bei fehlendem Internet oder abgelaufener Anmeldung bleibt die Notiz vorgemerkt und WorkManager versucht die Synchronisierung erneut; beim nächsten Öffnen von Smart Calls werden offene Vorgänge ebenfalls wieder eingeplant.

## Terminregeln

- „morgen um 13:20 Uhr“ erzeugt morgen um 13:20 Uhr eine Wiedervorlage.
- Ein Datum oder Wochentag wird übernommen; ohne Uhrzeit verwendet die App 10:00 Uhr.
- „nächste Woche“ erzeugt Montag um 10:00 Uhr.
- Ohne erkennbaren Rückruf- oder Gesprächswunsch wird kein Termin erstellt.

Die Planung läuft vollständig auf dem Android-Gerät und nutzt keine kostenpflichtige KI-API.
