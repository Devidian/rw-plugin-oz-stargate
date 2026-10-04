# DHD-Adressen, Startgates und Discord-Ereignisse

- Spieler können im DHD Aliase, lokale oder Netzwerkadressen anzeigen; lokale Adressen bleiben als Fallback verfügbar. Admins ändern einen Toralias über den nativen Eingabedialog.
- DHD-Schaltflächen und Chevrons wurden neu angeordnet. Doppelte Interaktionen mit demselben Tor ersetzen ein sichtbares DHD nicht mehr.
- Admins markieren Startgates im DHD nach nativer Bestätigung. Die Markierung bleibt in der lokalen SQLite-Datenbank. Eine Welteinstellung schaltet die zufällige Erstankunft aus; Sektor 0,0 bleibt Fallback. Transfers sind davon ausgenommen.
- Entdeckungs-Abklingzeit und Admin-Ausnahme sind einstellbar. Optional konfigurierbare Discord-Kanäle melden interne und externe Reisen, Entdeckungen und Netzwerkstatus ohne den Netzwerkcode. Alle neuen Spieler- und Admintexte haben DE/EN-Übersetzungen.
- Relay-Adressbuch und lokale Adressmigration wurden erweitert. Bestehende Weltsettings und Gate-IDs bleiben erhalten; OZ Stargate Network 0.5.0 wird für Netzwerkadressen und Alias-Abgleich benötigt.
