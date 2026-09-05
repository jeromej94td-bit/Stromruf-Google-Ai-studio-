# SIP-Anmeldung: Wiederherstellung

Smart Calls verwendet für Easybell TLS eine explizite Registrar-Adresse in der Form `sips:voip.easybell.de:5061;transport=tls`. Die Eingabe wird vor dem Aufbau bereinigt, sodass auch ein versehentlich eingegebenes `sip:` oder `sips:` nicht zu einer doppelten SIP-Adresse führt.

Wenn die Anmeldung vorübergehend scheitert, startet die App bis zu drei erneute Versuche nach 2, 6 und 15 Sekunden. Die sichtbare Fehlermeldung unterscheidet Zugangsdaten, TLS-Fehler und Nichterreichbarkeit.

Die SIP-Zugangsdaten und die TLS-Zertifikatsprüfung bleiben unverändert geschützt. Diese Änderung betrifft nur den Verbindungsaufbau und keine Audio-, Aufnahme- oder Supabase-Funktion.
