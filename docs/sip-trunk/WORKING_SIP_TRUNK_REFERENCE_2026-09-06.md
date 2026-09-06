# Stromruf SIP-Trunk – eingefrorene funktionierende Referenz

**Stand:** 06.09.2026 09:52 Europe/Berlin  
**Status:** FUNKTIONIEREND / NICHT VERÄNDERN  
**Referenz-Commit:** `b41583d1f54c67f3efb867377b82e1cff17b7098`  
**Backup-Branch:** `backup/sip-trunk-working-2026-09-06`

Diese Dokumentation beschreibt den funktionierenden SIP-Trunk auf dem Stromruf-Hauptbildschirm. Sie ist als Wiederherstellungsanker gedacht. Die produktive SIP-Telefonie soll nicht "optimiert", auf eine ältere Eigenimplementierung zurückgebaut oder in ihren Transport-/Media-Einstellungen verändert werden, solange kein bewusst freigegebener Ersatz vorliegt.

## 1. Architektur

Die aktuelle Home-SIP-Telefonie ist bewusst als eigene Implementierung unter `com.example.homesip` getrennt. Sie verwendet **Linphone SDK** und überlässt dem SDK SIP, TLS, SDP, RTP, SRTP, Dialog-Routing und Session-Handling.

Authoritative Dateien im funktionierenden Commit:

- `app/src/main/java/com/example/homesip/HomeSipTrunk.kt` – SIP-Core, Registrierung, Anruf, Foreground-Service
- `app/src/main/java/com/example/ui/screens/HomeSipTrunkCard.kt` – UI, Zugangsdaten, Rufnummer, Mikrofonfreigabe
- `app/src/main/java/com/example/ui/screens/HeuteScreen.kt` – Einbindung über `item { HomeSipTrunkCard() }`
- `app/src/main/AndroidManifest.xml` – Berechtigungen + `HomeSipCallService`
- `app/build.gradle.kts` – Linphone-Abhängigkeit

Bytegenaue Sicherung dieser Dateien liegt unter `docs/sip-trunk/snapshot-2026-09-06/`.

## 2. Exakte Git-Referenzen

| Datei | Blob SHA im funktionierenden Stand |
|---|---|
| `HomeSipTrunk.kt` | `1519e07184b8794929792fa89827eb7b305fd74d` |
| `HomeSipTrunkCard.kt` | `4dde60d63f953f68f13603aca515f749a6d850f3` |
| `HeuteScreen.kt` | `a2b7fee4b0e9433ceaa3bc8fdccccfc5e1984609` |
| `AndroidManifest.xml` | `e5f804766acc520f630dcc83710c4bad9a693394` |
| `app/build.gradle.kts` | `1b05394770465ca73b1b3ec6b6115b940448bec2` |

## 3. Linphone-Version

In `app/build.gradle.kts` ist fest eingebunden:

```kotlin
implementation("org.linphone:linphone-sdk-android:5.5.18")
```

Der dokumentierte funktionierende App-Stand ist VersionCode `19`, VersionName `2.8`.

## 4. Easybell-Konfiguration

Standardwerte der aktuellen Implementierung:

- Registrar: `secure.sip.easybell.de`
- Port: `5061`
- SIP-Signalisierung: TLS
- Server-URI: `sips:<registrar>:<port>;transport=tls`
- Identity: `sip:<user>@<registrar>`
- Ziel-URI: `sip:<zielrufnummer>@<registrar>`
- Registrierung: aktiv
- Outbound Proxy: aktiv
- Medienverschlüsselung: SRTP, verpflichtend

Der Auth-Benutzer ist optional. Ist er leer, verwendet die App den SIP-Benutzer als Auth-Benutzer.

**Wichtig:** Echte SIP-Benutzernamen und Passwörter gehören absichtlich NICHT in GitHub. Sie werden auf dem Gerät gespeichert.

## 5. Speicherung der Zugangsdaten

`HomeSipSettingsStore` verwendet bevorzugt:

- AndroidX `EncryptedSharedPreferences`
- `MasterKey` mit `AES256_GCM`
- Key-Verschlüsselung `AES256_SIV`
- Value-Verschlüsselung `AES256_GCM`
- Preference-Name: `home_sip_trunk`

Gespeicherte Keys:

- `user`
- `auth_user`
- `password`
- `registrar`
- `port`

Falls die verschlüsselte Preference-Erstellung auf einem Gerät fehlschlägt, existiert im aktuellen Code ein Fallback auf `home_sip_trunk_fallback`. Diese Dokumentation beschreibt das nur; sie verändert dieses Verhalten nicht.

## 6. Linphone-Core – Einstellungen, die den funktionierenden Stand ausmachen

Der Core wird aus folgender SIP-Konfiguration erzeugt:

```text
[sip]
store_auth_info=0
```

Danach setzt die App:

```kotlin
created.setAutoIterateEnabled(true)
created.setKeepAliveEnabled(true)
created.verifyServerCertificates(true)
created.verifyServerCn(true)
created.setVideoCaptureEnabled(false)
created.setVideoDisplayEnabled(false)
created.inCallTimeout = 0
```

Das bedeutet insbesondere:

- Linphone iteriert den Core automatisch.
- SIP Keep-Alive ist aktiv.
- Server-Zertifikate werden geprüft.
- Der Zertifikats-CN wird geprüft.
- Video ist deaktiviert.
- Kein künstliches In-Call-Timeout.

## 7. Registrierung

Vor einer neuen Verbindung räumt die Home-SIP-Implementierung ihre Linphone-Konten/Auth-Infos auf:

```kotlin
engine.clearAccounts()
engine.clearAllAuthInfo()
```

Der Registrar wird bereinigt: `sip:`/`sips:` werden entfernt, Pfad und Port aus einer versehentlich vollständig eingegebenen Adresse werden abgeschnitten und der Host wird kleingeschrieben.

Anschließend:

```kotlin
val server = factory.createAddress("sips:${clean.registrar}:${clean.port};transport=tls")
server.transport = TransportType.Tls
val identity = factory.createAddress("sip:${clean.user}@${clean.registrar}")
```

Auth-Info:

```kotlin
factory.createAuthInfo(
    clean.user,
    clean.authUser.ifBlank { clean.user },
    clean.password,
    null,
    null,
    clean.registrar
)
```

Account-Parameter:

```kotlin
params.identityAddress = identity
params.serverAddress = server
params.setRegisterEnabled(true)
params.setOutboundProxyEnabled(true)
```

Das erzeugte Konto wird anschließend als `defaultAccount` gesetzt.

## 8. Ausgehender Anruf

Die UI fordert vor dem Start `RECORD_AUDIO` an. Eine Zielnummer wird auf Ziffern und optional `+` normalisiert.

Der tatsächliche Anruf wird über einen Foreground-Service gestartet. Das verhindert, dass der aktive SIP-Anruf beim Wechsel des App-Zustands leicht abgeräumt wird.

Destination:

```kotlin
sip:<zielrufnummer>@<registrar>
```

Medienparameter:

```kotlin
params.mediaEncryption = MediaEncryption.SRTP
engine.setMediaEncryptionMandatory(true)
```

Dann:

```kotlin
engine.inviteAddressWithParams(destination, params)
```

**TLS + SRTP sind im funktionierenden Stand fest aktiv.**

## 9. Eingehende Anrufe

Diese Home-SIP-Komponente ist derzeit auf ausgehende Telefonie ausgelegt. `IncomingReceived` wird mit `Reason.Busy` abgelehnt. Auch das ist Teil des eingefrorenen funktionierenden Verhaltens und soll bei einer Wiederherstellung nicht versehentlich geändert werden.

## 10. Foreground-Service / Stabilität

`HomeSipCallService` ist im Manifest registriert als:

```xml
<service
    android:name=".homesip.HomeSipCallService"
    android:exported="false"
    android:stopWithTask="false"
    android:foregroundServiceType="microphone" />
```

Der Service:

- läuft mit einer dauerhaften Low-Importance-Benachrichtigung während des SIP-Anrufs,
- verwendet `FOREGROUND_SERVICE_TYPE_MICROPHONE`,
- hält während des Anrufs einen `PARTIAL_WAKE_LOCK`,
- startet den ausstehenden Linphone-Anruf,
- gibt den WakeLock in `onDestroy()` wieder frei,
- verwendet `START_NOT_STICKY`.

Notification Channel: `home_sip_active_call`  
Notification ID: `7321`

## 11. Relevante Manifest-Berechtigungen

Der funktionierende Stand enthält unter anderem:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.USE_SIP" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

## 12. UI-Verhalten

Auf `HeuteScreen` folgt auf den normalen `DialerCard` direkt:

```kotlin
item { HomeSipTrunkCard() }
```

Die SIP-Karte:

- lädt lokal gespeicherte SIP-Werte,
- hat einen ausklappbaren Bereich `Zugangsdaten`,
- kann Registrar/Port per Easybell-Chip auf `secure.sip.easybell.de:5061` setzen,
- speichert Benutzer/Auth-Benutzer/Passwort/Registrar/Port lokal,
- verbindet erst nach `SIP-Trunk verbinden`,
- zeigt Status `OFFLINE`, `CONNECTING`, `READY`, `DIALING`, `RINGING`, `IN_CALL`, `ERROR`,
- startet den Anruf erst nach Mikrofonfreigabe,
- bietet während Aufbau/Klingeln/Gespräch `Auflegen`.

## 13. Fehlerabbildung

Der aktuelle Stand unterscheidet sichtbar:

- SIP 401/403 -> Benutzer oder Passwort prüfen
- Certificate/TLS -> TLS-Zertifikat konnte nicht geprüft werden
- Timeout/Unreachable -> SIP-Server nicht erreichbar
- sonst -> allgemeine SIP-Anmeldung fehlgeschlagen

Bei Call-Fehlern wird zusätzlich der SIP-Protokollcode aus `call.errorInfo.protocolCode` angezeigt.

## 14. Wiederherstellung – schnellster und sicherster Weg

Wenn die SIP-Telefonie später kaputtgeht, **nicht neu erfinden**.

### Vollständig auf den bekannten funktionierenden Stand zurück

Verwende den Backup-Branch:

```text
backup/sip-trunk-working-2026-09-06
```

oder den Referenz-Commit:

```text
b41583d1f54c67f3efb867377b82e1cff17b7098
```

### Nur SIP wiederherstellen

Kopiere aus `docs/sip-trunk/snapshot-2026-09-06/` exakt zurück:

1. `app/src/main/java/com/example/homesip/HomeSipTrunk.kt`
2. `app/src/main/java/com/example/ui/screens/HomeSipTrunkCard.kt`
3. die Linphone-Abhängigkeit aus `app/build.gradle.kts`
4. Service + benötigte Permissions aus `AndroidManifest.xml`
5. die Einbindung `item { HomeSipTrunkCard() }` aus `HeuteScreen.kt`

Die Snapshot-Dateien sind direkte Kopien der Git-Blobs aus dem funktionierenden Commit.

## 15. Was ausdrücklich NICHT gemacht werden soll

Bei einer Reparatur dieses funktionierenden Home-SIP-Trunks nicht ohne bewusste Freigabe:

- Linphone durch den alten manuellen SIP-Stack ersetzen,
- Registrar von `secure.sip.easybell.de` wegändern,
- TLS entfernen oder auf UDP/TCP umstellen,
- SRTP optional machen oder deaktivieren,
- Zertifikatsprüfung abschalten,
- Keep-Alive deaktivieren,
- Foreground-Service/WakeLock entfernen,
- die SIP-Core-Lifecycle-Logik mit anderen Telefoniekomponenten vermischen,
- Zugangsdaten in GitHub eintragen.

## 16. Historischer Hinweis

`docs/SIP_ANMELDUNG_REPARATUR.md` beschrieb einen älteren Ansatz und ist nicht mehr die maßgebliche Quelle. Für den seit 06.09.2026 funktionierenden Linphone/Easybell-Home-SIP-Trunk gilt ausschließlich diese eingefrorene Referenz und der oben genannte Commit.
