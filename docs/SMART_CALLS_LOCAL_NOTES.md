# Smart Calls: automatische lokale Gesprächsnotizen

## Ablauf

1. Ein Smart Call mit mehr als 60 Sekunden wird nach dem Auflegen als WAV **nur auf dem Android-Gerät** gespeichert.
2. Der lokale Whisper-Worker transkribiert die Aufnahme auf Deutsch.
3. `GermanCallSummary` bildet daraus auf dem Gerät eine kurze Gesprächsnotiz. Dabei werden Hinweise wie Angebot, Interesse, Termin oder Rückruf hervorgehoben.
4. `SmartCallNoteWorker` speichert ausschließlich diese Zusammenfassung in `public.smartcall_notes` des Stromruf-Supabase-Projekts.
5. Die App zeigt die gespeicherten Notizen in **Agents → Smart Calls → Gespeicherte Smart-Call-Notizen** an und aktualisiert die Liste automatisch.

## Datenschutz

- Keine WAV-/Audiodatei wird hochgeladen.
- Das vollständige Transkript bleibt auf dem Android-Gerät.
- In Supabase stehen nur Rufnummer, Zeitpunkt, Gesprächsdauer, Dateiname und die kurze Zusammenfassung.

## Zuverlässigkeit

Die Speicherung hat eine eindeutige ID aus Benutzer und Dateiname. Ein erneuter Versuch erzeugt daher keine doppelten Notizen. Bei fehlendem Internet oder abgelaufener Anmeldung bleibt die Notiz vorgemerkt und WorkManager versucht die Synchronisierung erneut; beim nächsten Öffnen von Smart Calls werden offene Vorgänge ebenfalls wieder eingeplant.

## Noch nicht enthalten

Automatische Kalendereinträge oder Wiedervorlagen werden bewusst erst im nächsten Schritt ergänzt. Die gespeicherte Gesprächsnotiz ist die Grundlage dafür.
