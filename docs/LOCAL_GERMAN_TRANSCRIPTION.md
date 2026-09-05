# Lokale deutsche Transkription für Smart Calls

Diese Änderung ergänzt Smart Calls um eine kostenlose Offline-Transkription direkt auf dem Android-Gerät. Sie ist für deutsche Kundengespräche ausgelegt und nutzt keine kostenpflichtige API. Audiodateien werden nicht an Supabase, Gemini oder einen externen Dienst übertragen.

## Ziel

Smart-Calls-Aufnahmen sollen nach dem Gespräch automatisch als deutscher Text verfügbar sein. Daraus können später Gesprächsnotizen, Wiedervorlagen und Termine abgeleitet werden. In diesem Schritt wird zuerst die verlässliche lokale Transkription geschaffen; Zusammenfassung, Supabase-Notiz und Terminlogik bauen darauf auf.

## Verhalten in der App

- Im Reiter **Agents → Smart Calls** gibt es einen Bereich für **lokale deutsche Transkription**.
- Beim ersten Start muss das Whisper-Modell einmalig geladen werden. Der Download ist ungefähr 190 MB groß.
- Neue Smart-Calls-Aufnahmen werden automatisch zur Transkription eingeplant, wenn das Gespräch länger als 60 Sekunden dauerte.
- Kürzere Aufnahmen werden nicht automatisch verarbeitet.
- Bestehende WAV-Aufnahmen können in der Aufnahmenliste manuell transkribiert werden.
- Die Transkription läuft als Hintergrundarbeit über WorkManager.
- Während eines aktiven Telefonats pausiert die Transkription, damit Telefonie und Audioaufnahme Vorrang haben.
- Teilfortschritt wird gespeichert. Wenn Android den Job beendet, kann er später fortgesetzt werden.

## Modell

Verwendet wird das quantisierte Whisper-Modell:

- Datei: `ggml-small-q5_1.bin`
- Quelle: Hugging Face Repository `ggerganov/whisper.cpp`
- Revision: `5359861c739e955e79d9a303bcbc70fb988958b1`
- SHA-256: `ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb`
- Sprache: fest auf `de` gesetzt
- Übersetzung: deaktiviert
- automatische Spracherkennung: deaktiviert

Das Modell ist multilingual, wird in der App aber fest für Deutsch genutzt. Es gibt keine Modellauswahl, weil die Smart-Calls im aktuellen Einsatz nur deutsche Kundengespräche betreffen.

## Native Bibliothek

Die Android-App bindet `whisper.cpp` über JNI ein.

- `app/src/main/cpp/CMakeLists.txt` lädt `whisper.cpp` in Version `v1.8.3` über einen fest gepinnten Commit.
- Commit: `2eeeba56e9edd762b4b38467bab96c2517163158`
- Archiv-SHA-256: `089b898aa83b24a8321e0fd554eeb0967fb03dd687e27f6374c72d3363b5b429`
- Die App baut daraus `libstromruf_whisper.so`.
- GPU, OpenMP und native CPU-Spezialoptimierungen sind deaktiviert, damit der Build auf Android stabil bleibt.
- Der Linker bekommt `-Wl,-z,max-page-size=16384`, damit neuere Android-Geräte mit 16-KB-Page-Size unterstützt werden.

Die große Modelldatei wird nicht ins GitHub-Repository gelegt. Sie wird auf dem Gerät beim ersten Einrichten geladen und anhand der SHA-256-Prüfsumme geprüft.

## Wichtige Dateien

| Datei | Zweck |
| --- | --- |
| `app/src/main/cpp/CMakeLists.txt` | Android-CMake-Build für whisper.cpp |
| `app/src/main/cpp/whisper_jni.cpp` | JNI-Brücke zwischen Kotlin und whisper.cpp |
| `app/src/main/java/com/example/transcription/offline/WhisperNative.kt` | Kotlin-Wrapper für die native Bibliothek |
| `app/src/main/java/com/example/transcription/offline/PcmWave.kt` | WAV-Reader und Resampling auf 16 kHz Mono |
| `app/src/main/java/com/example/transcription/offline/LocalTranscripts.kt` | Jobstatus, Modellstatus, Queue und Cache |
| `app/src/main/java/com/example/transcription/offline/WhisperWorkers.kt` | Modell-Download und Transkriptions-Worker |
| `app/src/main/java/com/example/ui/screens/OfflineTranscriptionUi.kt` | UI-Karten für Download, Status und Transkriptanzeige |
| `app/src/main/java/com/example/sip/LinphoneSipClient.kt` | plant automatische Transkription nach Aufnahmen über 60 Sekunden ein |
| `app/src/main/java/com/example/ui/screens/SmartCallsTab.kt` | zeigt lokale Transkription in der Smart-Calls-Oberfläche |

## Datenschutz und Kosten

Die lokale Transkription ist kostenlos im laufenden Betrieb, sobald das Modell geladen ist. Es fallen keine API-Kosten pro Gespräch an. Die Audiodatei bleibt auf dem Gerät. Die App lädt nur das Whisper-Modell herunter.

Die bestehende optionale Gemini-Analyse bleibt bewusst ein manueller Schritt. Sie ist in der UI als API-Funktion gekennzeichnet und wird nicht automatisch gestartet.

## Grenzen dieses Schritts

- Es wird nur transkribiert, noch nicht automatisch zusammengefasst.
- Es werden noch keine Gesprächsnotizen nach Supabase geschrieben.
- Es werden noch keine Termine automatisch erzeugt.
- Sprechertrennung ist nicht enthalten.
- Zeitstempel werden pro verarbeitetem Abschnitt gespeichert und sind ungefähr, nicht wortgenau.

Diese Punkte sind die nächsten sinnvollen Schritte, nachdem die lokale Transkription auf dem Gerät stabil läuft.

## Verifikation

Der Stand wurde mit gezielten Checks geprüft:

- Kotlin-Klassen für Offline-Transkription kompilieren gegen die verwendeten Android-, WorkManager-, OkHttp- und Linphone-Abhängigkeiten.
- WAV-Verarbeitung ist per JUnit geprüft:
  - 8-kHz-Telefonieaudio wird auf 16 kHz gebracht.
  - 48-kHz-Stereo-WAV wird korrekt gelesen und gemischt.
  - unvollständige WAV-Dateien werden abgelehnt.
- Modell-Download wurde lokal mit der erwarteten SHA-256-Prüfsumme geprüft.
- Native JNI-Brücke wurde mit Host-Build getestet:
  - Modell laden
  - kurze Inferenz starten
  - Abbruchpfad aus Kotlin/JNI prüfen
  - native Ressourcen schließen

Ein vollständiger Android-APK-Build wurde in dieser Umgebung nicht durchgeführt, weil hier kein vollständiges Android-SDK/NDK-Projektsetup mit funktionierendem Gradle-Wrapper vorhanden ist.
