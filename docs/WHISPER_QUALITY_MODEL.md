# Smart Calls – Whisper Qualitätsmodell

Dieser Stand wechselt die lokale Smart-Calls-Transkription vom sehr kleinen Tiny-q5_1-Modell zurück auf das deutlich größere mehrsprachige Whisper Small-q5_1-Modell.

Ziel ist bessere Erkennung deutscher Telefonate, ohne den früheren 0-%-Stillstand wieder einzuführen.

Technische Leitplanken:
- Modell: `ggml-small-q5_1.bin` (~190 MB), gepinnte whisper.cpp-Datei mit SHA-256-Prüfung.
- Verarbeitung bleibt vollständig lokal auf dem Android-Gerät.
- Audiofenster: 6 Sekunden mit 1 Sekunde Überlappung statt der früheren 30-Sekunden-Blöcke.
- Jeder Abschnitt hat ein 45-Sekunden-Abbruchlimit; ein einzelner schwieriger Abschnitt kann die gesamte Warteschlange deshalb nicht dauerhaft blockieren.
- 6 CPU-Threads für aktuelle High-End-Android-Geräte.
- Während eines aktiven SIP-Anrufs pausiert die Transkription weiterhin.
- SIP/Linphone-Code wird von diesem Qualitätswechsel nicht verändert.

Der frühere Tiny- und Base-Modellbestand wird beim nächsten Modelldownload entfernt. Bereits abgeschlossene Transkripte bleiben unverändert; für eine bessere Fassung muss die jeweilige Aufnahme erneut zur Transkription gestartet werden.
