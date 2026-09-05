# Lokale deutsche Transkription für Smart Calls

Smart Calls transkribiert deutsche WAV-Aufnahmen kostenlos direkt auf dem Android-Gerät. Audiodateien werden dafür nicht an Supabase, Gemini oder andere Transkriptionsdienste übertragen.

## Verhalten in der App

- Unter **Agents → Smart Calls** wird das lokale Deutsch-Modell einmalig geladen.
- Seit Version 2.3 ist der Download ungefähr **60 MB** groß.
- Gespräche über 60 Sekunden werden nach dem Auflegen automatisch eingeplant.
- Bestehende WAV-Aufnahmen können manuell transkribiert werden.
- Während eines aktiven Smart Calls pausiert die Verarbeitung.
- Die Aufnahme wird in kurzen Abschnitten verarbeitet; Fortschritt und Teiltext werden nach jedem Abschnitt gespeichert.
- Wird Android oder der Worker beendet, kann die Verarbeitung am letzten Checkpoint weiterlaufen.

## Modell

Verwendet wird `ggml-base-q5_1.bin` aus `ggerganov/whisper.cpp`.

- Revision: `f281eb45af861ab5e5297d23694b7d46e090c02c`
- Dateigröße: `59,707,625` Bytes
- SHA-256: `422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898`
- Sprache: fest auf `de`
- Übersetzung: deaktiviert
- Spracherkennung: deaktiviert

PR #22 verwendete zuvor `ggml-small-q5_1.bin` mit ungefähr 190 MB und 30-Sekunden-Inferenzfenstern. Auf dem Smartphone konnte dadurch der erste native Whisper-Aufruf so lange dauern, dass die Oberfläche minutenlang bei `0 %` stehen blieb. Version 2.3 verwendet deshalb das deutlich kleinere Base-Modell, vier CPU-Threads und 10-Sekunden-Fenster. Das alte Small-Modell wird beim erneuten Einrichten entfernt.

## Native Bibliothek

`whisper.cpp` wird über JNI/CMake eingebunden. Der Build pinnt weiterhin den vorhandenen whisper.cpp-Stand und erzeugt `libstromruf_whisper.so`. GPU und OpenMP bleiben deaktiviert; der Linker nutzt 16-KB-Page-Size-Kompatibilität.

## Wichtige Dateien

| Datei | Zweck |
| --- | --- |
| `app/src/main/cpp/CMakeLists.txt` | Android-CMake-Build für whisper.cpp |
| `app/src/main/cpp/whisper_jni.cpp` | JNI-Brücke und Inferenzparameter |
| `app/src/main/java/com/example/transcription/offline/WhisperNative.kt` | Kotlin-Wrapper für JNI |
| `app/src/main/java/com/example/transcription/offline/PcmWave.kt` | WAV-Reader und 16-kHz-Resampling |
| `app/src/main/java/com/example/transcription/offline/LocalTranscripts.kt` | Modellstatus, Jobs, Queue und Checkpoints |
| `app/src/main/java/com/example/transcription/offline/WhisperWorkers.kt` | Modell-Download und Transkription |
| `app/src/main/java/com/example/ui/screens/OfflineTranscriptionUi.kt` | Download-, Status- und Transkript-UI |

## Datenschutz und Kosten

Die lokale Transkription verursacht nach dem Modelldownload keine API-Kosten. Aufnahme und vollständiges Transkript bleiben lokal. Die optionale Gemini-Analyse ist davon getrennt.

## PR #23 / Supabase

Die automatische Zusammenfassung und die Synchronisation einer kurzen Smart-Call-Notiz nach Supabase liegen in PR #23 und bauen auf einer erfolgreich abgeschlossenen lokalen Transkription auf. WAV-Datei und Volltranskript sollen weiterhin nicht nach Supabase hochgeladen werden.

## Verifikation

Der Fix hält die vorhandene SHA-256-Prüfung des Modells bei und ändert die Verarbeitung so, dass nach jedem kurzen Audiofenster ein Checkpoint geschrieben wird. Ein vollständiger APK-Lauf auf einem echten Android-Gerät ist weiterhin der abschließende End-to-End-Test.
