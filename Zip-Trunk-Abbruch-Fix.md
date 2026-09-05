# Zip Trunk Abbruch Fix

Suchbegriffe: **SIP-Trunk**, Easybell, Smart Calls, Android, Linphone, Abbruch nach 20 / 25 / 30 Sekunden.

## Ergebnis und entscheidende Änderung

Der Nutzer hat nach der Umstellung bestätigt: **„Es funktioniert wieder.“**
Diese Rückmeldung wird hier als erfolgreiche praktische Rückmeldung zum Fix festgehalten.
Eine genaue Gesprächsdauer, ein vollständiges Testprotokoll und eine unabhängig
geprüfte Zuordnung der installierten APK zum Commit wurden dabei nicht mitgeteilt.
Insbesondere ist damit kein protokollierter Ein-Stunden-Test behauptet.

Die entscheidende Änderung war der **Wechsel vom selbst geschriebenen SIP- und
RTP-Client zum Linphone Android SDK direkt in der App**, zusammen mit einem
Android-Telefoniedienst und einer von der Oberfläche unabhängigen Anruf-Lebensdauer.

Linphone übernimmt jetzt die Protokoll- und Medienverarbeitung. Die App kümmert
sich um Zugangsdaten, Bedienung, Anrufzustand und Aufnahme. Easybell bleibt der
SIP-Anbieter. Dafür wurde kein zusätzlicher Server eingerichtet oder gemietet.

## Dauerhaft auffindbarer Referenzstand

- Repository: `jeromej94td-bit/Stromruf-Google-Ai-studio-`.
- Implementierungs-PR: [#20 – Smart Calls 2.0: Linphone direkt auf Android](https://github.com/jeromej94td-bit/Stromruf-Google-Ai-studio-/pull/20).
- Fix-Commit: [`35264e511b82a67e35060edca5ebda4ce6b8a656`](https://github.com/jeromej94td-bit/Stromruf-Google-Ai-studio-/commit/35264e511b82a67e35060edca5ebda4ce6b8a656).
- Implementierungsbranch: `feature/smart-calls-linphone-android`.
- App-Version dieses Commits: **2.0**, `versionCode = 11`.
- SDK: **org.linphone:linphone-sdk-android:5.5.18**.
- Sichtbar in der Anrufmaske: **„Smart Calls · 2.0“** und
  **„Linphone · SIP-Telefonie direkt auf Android“**.

Die Code-Verweise in diesem Dokument zeigen absichtlich auf den festen Commit,
damit der funktionierende Ansatz auch nach späteren Änderungen auffindbar bleibt.

**GitHub-Stand beim Anlegen dieser Dokumentation:** PR #20 war noch offen und
nicht nach `main` gemergt. Diese separat auf `main` abgelegte Dokumentation
bedeutet daher nicht automatisch, dass ein Build von `main` bereits Linphone enthält.
Die Rückmeldung des Nutzers und der GitHub-Mergezustand sind getrennte Nachweise.
Den aktuellen Mergezustand zeigt der PR-Link.

## Ursprüngliches Fehlerbild

Ausgehende Easybell-Anrufe klingelten und wurden angenommen. Die tatsächliche
Verbindung brach anschließend wiederholt nach etwa 20–30 Sekunden ab, etwa nach
20, 22, 25 oder 26 Sekunden. Teilweise lief die lokale Anzeige „Im Gespräch“
weiter, obwohl die Gegenstelle schon getrennt war.

Zeitweise erschien:

> Verbindung unterbrochen: Software caused connection abort

Anrufe über Retell funktionierten nach Nutzerangabe mit dem Trunk durchgehend.
Das war ein Hinweis, den eigenen Android-Client genauer zu betrachten; es bewies
für sich allein noch keine bestimmte Fehlerursache.

## Was vorher versucht wurde und nicht ausreichend half

Im bisherigen Arbeitsverlauf wurden unter anderem Änderungen an ACK-Behandlung
und Wiederholungen, Dialog-Routing, Session-Timern, TLS-Keepalives, SIP-Stream-
Framing sowie SDP/SRTP und RTP-Verarbeitung vorgenommen. Der Nutzer meldete
anschließend weiterhin reale Abbrüche. Eine geringfügig längere Laufzeit war kein
Nachweis einer Behebung.

**Die einzelne ursprüngliche Protokollursache ist nicht abschließend bewiesen.**
Es liegt hier kein ausgewerteter End-to-End-SIP-/Medien-Trace vor, der etwa ein
fehlendes ACK, fehlerhaftes SRTP oder Android-Energiesparen eindeutig als alleinige
Ursache belegt. Auch die Socket-Fehlermeldung allein liefert diesen Nachweis nicht.

Gesichert dokumentiert sind die umgesetzte Architekturänderung und die danach
erhaltene positive Nutzer-Rückmeldung. Nicht nachträglich eine der früheren
Hypothesen zur bewiesenen Ursache erklären.

## Konkrete Umsetzung im Code

| Datei | Aufgabe im Fix |
| --- | --- |
| [LinphoneSipClient.kt](https://github.com/jeromej94td-bit/Stromruf-Google-Ai-studio-/blob/35264e511b82a67e35060edca5ebda4ce6b8a656/app/src/main/java/com/example/sip/LinphoneSipClient.kt) | App-weiter SDK-Adapter: Anmeldung, Wählen, Callbacks, Audio, Aufnahme und Auflegen |
| [SmartCallService.kt](https://github.com/jeromej94td-bit/Stromruf-Google-Ai-studio-/blob/35264e511b82a67e35060edca5ebda4ce6b8a656/app/src/main/java/com/example/sip/SmartCallService.kt) | Android-Foreground-Service, Anrufbenachrichtigung, Auflegen-Aktion und Wake Lock |
| [SmartCallsTab.kt](https://github.com/jeromej94td-bit/Stromruf-Google-Ai-studio-/blob/35264e511b82a67e35060edca5ebda4ce6b8a656/app/src/main/java/com/example/ui/screens/SmartCallsTab.kt) | Oberfläche an SDK-Adapter angeschlossen; kein Disconnect beim Verlassen des Reiters |
| [AndroidManifest.xml](https://github.com/jeromej94td-bit/Stromruf-Google-Ai-studio-/blob/35264e511b82a67e35060edca5ebda4ce6b8a656/app/src/main/AndroidManifest.xml) | Nicht exportierter Telefoniedienst mit Foreground-Typ `microphone` |
| [app/build.gradle.kts](https://github.com/jeromej94td-bit/Stromruf-Google-Ai-studio-/blob/35264e511b82a67e35060edca5ebda4ce6b8a656/app/build.gradle.kts) | SDK-Abhängigkeit und App-Version 2.0 / 11 |
| [settings.gradle.kts](https://github.com/jeromej94td-bit/Stromruf-Google-Ai-studio-/blob/35264e511b82a67e35060edca5ebda4ce6b8a656/settings.gradle.kts) | Offizielles Linphone-Maven-Repository für die Gruppe `org.linphone` |
| [SMART_CALLS_LINPHONE.md](https://github.com/jeromej94td-bit/Stromruf-Google-Ai-studio-/blob/35264e511b82a67e35060edca5ebda4ce6b8a656/docs/SMART_CALLS_LINPHONE.md) | Ursprüngliche technische Übergabe und Geräte-Abnahmeplan |

### 1. Eigenen Protokoll-Unterbau aus dem aktiven Anrufpfad nehmen

`SmartCallsTab` verwendet:

```kotlin
val sipClient = remember { LinphoneSipClient.getInstance(ctx) }
```

Der frühere `NativeSipClient` wird dort nicht mehr erzeugt. Seine eigenen
ACK-, Socket-, SDP-, RTP- und SRTP-Routinen sind kein Laufzeit-Fallback.
Die Datei blieb im Referenzstand unter anderem wegen der gemeinsam verwendeten
Typen `SipAccountConfig`, `SipState` und `SipTransportProtocol` erhalten.

### 2. Einen SDK-Core für die App verwenden

`LinphoneSipClient.getInstance()` verwendet den Application-Kontext.
`getCore()` erstellt den Linphone-Core; `onMain()` hält SDK-Zugriffe auf dessen
Android-Erstellungsthread. Die automatische SDK-Iteration ist aktiviert.

Wichtige Einstellungen aus dem Fix:

```kotlin
engine.setAutoIterateEnabled(true)
engine.setKeepAliveEnabled(true)
engine.verifyServerCertificates(true)
engine.verifyServerCn(true)
engine.inCallTimeout = 0
```

`inCallTimeout = 0` entfernt eine vom Core gesetzte maximale Gesprächsdauer.
Das ist nur ein Bestandteil dieser Integration und kein Beweis, dass ein
30-Sekunden-Timer die ursprüngliche Ursache war. Fehler oder Verbindungsverlust
werden weiterhin über SDK-Callbacks verarbeitet.

### 3. Vorhandenen Easybell-Zugang direkt anbinden

`register()` übernimmt SIP-Benutzer, optionalen Authentifizierungsbenutzer,
Passwort, Registrar, Port und Transport aus der vorhandenen Konfiguration.
Der Registrar dient auch als Outbound-Proxy.

Im bisherigen Anwendungsfall: `voip.easybell.de`, TLS, Port `5061`.
PCMA und PCMU werden aktiviert, Video deaktiviert. Bei TLS setzt
`startPendingCall()` die Medienverschlüsselung auf SRTP und macht sie verpflichtend.
Zertifikats- und Hostnamenprüfung bleiben aktiv.

SDK-Konfiguration und Authentifizierungsinformationen werden nicht zusätzlich
dauerhaft im SDK gespeichert. Die vorhandene Kontoeinstellungsoberfläche wird
weiterverwendet. Keine Zugangsdaten in diese Dokumentation kopieren.

### 4. Den Anruf an einen Telefoniedienst binden

`makeCall()` prüft die Mikrofonberechtigung und startet `SmartCallService`.
Erst nachdem der Dienst als Foreground-Service aktiv ist, ruft er
`startPendingCall()` auf, das `inviteAddressWithParams()` ausführt.

Der Dienst erweitert Linphones `CoreService` und verwendet
`FOREGROUND_SERVICE_TYPE_MICROPHONE`. Er zeigt eine Benachrichtigung mit
„Auflegen“ und hält während seiner Foreground-Laufzeit einen
`PARTIAL_WAKE_LOCK`. Beim Ende wird dieser wieder freigegeben.

Die Oberfläche ruft beim Verlassen des Reiters nicht mehr `disconnect()` auf.
Beim Wiederöffnen werden Sitzung, Stummschaltung und Lautsprecherzustand
übernommen; ein laufender Anruf wird nicht erneut registriert.

### 5. Gesprächszustand und Aufnahme an echte SDK-Ereignisse koppeln

`onCallStateChanged()` verarbeitet Aufbau, Klingeln, Verbindung, Fehler,
Ende und Freigabe. Die Dauer stammt aus `call.duration`.
`Error` und `End` beenden die lokale Gesprächs-/Aufnahmeverarbeitung.

Bei `StreamsRunning` startet `call.startRecording()` die native Aufnahme.
Die WAV-Datei liegt unter `filesDir/smart_calls_recordings`.
Erst bei `Released` wird die abgeschlossene Datei an die Oberfläche weitergegeben.
`finishMedia()` beendet Aufnahme und Daueraktualisierung.

Der vorhandene optionale SAF-Export und der Gemini-/Notizen-Ablauf bleiben angebunden.
Die Vorgabe bleibt: **Zusammenfassungen für Gespräche über 60 Sekunden nach
Supabase; keine Audiospuren nach Supabase.** Dieser Fix führte keinen neuen
Supabase-Audioupload ein.

## Was künftige Änderungen erhalten müssen

1. Smart Calls weiterhin über `LinphoneSipClient` betreiben.
2. SDK-Version bewusst aktualisieren und danach echte Anrufe prüfen.
3. Keine eigenen ACK-Wiederholungsjobs, RTP-Sender oder SRTP-Kontexte parallel
   in dieselbe SDK-Verbindung einbauen.
4. Telefoniedienst vor dem Wählen aktivieren; Anruf-Lebensdauer vom Reiter trennen.
5. Fehler und Gesprächsende aus dem SDK in die Oberfläche übernehmen.
6. Aufnahmen erst nach Abschluss weiterverarbeiten und die Speichergrenzen beibehalten.
7. Die TLS-Zertifikatsprüfung nicht als vermeintlichen Verbindungsfix abschalten.

## Prüfung und Wiederherstellung bei einem Rückfall

Bei der Implementierung wurden Adapter und Dienst erfolgreich gegen die echten
SDK-Klassen für JVM 11 kompiliert und das Manifest als XML geprüft. Dabei waren
die unveränderten gemeinsamen Modelltypen und die Speicherklasse Compile-Stubs.
Das war kein vollständiger APK- oder Gerätetest. Danach bestätigte der Nutzer
praktisch, dass die Telefonie wieder funktioniert.

Bei erneuten Abbrüchen zuerst installierte Version und tatsächlich verwendeten
Client prüfen und die oben verlinkten Dateien mit dem Fix-Commit vergleichen.
Fehlende Teile gezielt wiederherstellen; nicht pauschal das gesamte Repository
auf einen alten Stand zurücksetzen.

Für einen belastbaren Regressionstest:

- Echte Verbindung mit hörbarem Audio auf beiden Seiten über die frühere
  30-Sekunden-Grenze hinaus prüfen, anschließend einen längeren Test bis eine Stunde.
- Bildschirm ausschalten und den Reiter wechseln.
- Lokal, von der Gegenstelle und über die Benachrichtigung auflegen.
- Zweiten Anruf, Aufnahme und Zusammenfassungsverarbeitung kontrollieren.
- Bei einem Fehler SDK-Zustand, Dauer und SIP-Fehlercode aus dem Log-Tag
  `SmartCalls` sichern. Die bloß weiterlaufende UI-Uhr genügt nicht als Nachweis.

SDK-Quellen und Lizenzhinweise stehen in der verlinkten technischen Übergabe.
Die Lösung wurde für die ausdrücklich gewünschte persönliche Android-Nutzung umgesetzt.
