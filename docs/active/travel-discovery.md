# Reisebildschirm, Erstankunft, Adressbuch und Auto-Discovery

## Ziel und Grenzen

Vier Development-Phasen in dieser Reihenfolge: Reisebildschirm, Erstankunft,
Adressbuch, Auto-Discovery. Nach jeder Phase Build, gezielter Upload und
Spielertest mit Rückmeldung. Produktivserver bleiben unverändert. Der Nutzer
gab am 2026-10-03 nach Abnahme aller Phasen einen öffentlichen Release frei;
dieser umfasst das Relay vor dem Plugin, aber kein Produktivserver-Deployment.

## Betroffene Systeme

- `rw-plugin-oz-stargate`: UI, Eintritt/Transfer, lokale SQLite-Daten und DHD.
- `rw-stargate-network`: ab Phase 3 Relay-Protokoll und MongoDB-Adressbuch.
- `rw-server-dev`: nur Development-Upload und Laufzeitprüfung.

## Checkliste

- [x] Reisebildschirm als eigenes Bild, je zehn Sekunden am Quell- und Zielserver bei Serverreisen, persönliche DE/EN-Einstellung und Aufräumen bei Abbruch/Disconnect/Reload implementieren.
- [x] Phase 1 lokal bauen, Tests und ZIP-Inhalt prüfen.
- [x] Phase 1 auf Development hochladen und Reload prüfen.
- [x] Demoserver als zweiten Testserver mit gleichem Build aktualisieren und Reload prüfen.
- [x] Phase 1 auf Development und Demo im Spiel prüfen und Rückmeldung abwarten.
- [x] Erstankunft nur beim ersten regulären Beitritt dieses Servers; freies oder eingehend offenes Tor, sonst normaler Spawn.
- [x] Phase 2 auf Development und Demo2 testen; Spielerabnahme am 2026-10-02 erhalten.
- [x] Globales Adressbuch mit Relay-Sammlung, lokalem Cache, manueller Wahl und Löschsynchronisierung implementieren.
- [x] Vor Phase 3 Relay und Plugin-Datenbanken sichern; Relay vor Plugin auf Development und Demo2 aktivieren.
- [x] Phase 3 nach der gewünschten Farbunterscheidung im DHD abnehmen (2026-10-03).
- [x] `World.getChunk()` und `Chunk.getLODSurfaceLevel()` auf Development für sichere Platzierung prüfen.
- [x] Auto-Discovery mit begrenzter LOD-/Freiraumprüfung, Hintergrundpool und DHD-Anwahl auf Development implementieren.
- [x] Auto-Discovery nach dem Anwahl- und i18n-Update im Spiel auf Development abnehmen.
- [x] Bei frischer Installation ohne Tore einmalig ein Tor mit DHD im globalen Startsektor nach Discovery-Platzierungsregeln erzeugen; Adresse bleibt unentdeckt. Spieltest auf Demo und Reise von Development am 2026-10-03 abgenommen.

## Risiken und Prüfung

Der Transfer transportiert die Quell-Einstellung im vorhandenen Datenobjekt.
Alte Transfers ohne diese Einstellung zeigen keinen Reisebildschirm. Die
Zielanzeige beginnt bei der Ankunft neu; Verbindungszeit zählt nicht dazu.
Der Development-Spielertest muss lokale und Serverreise sowie Abbruch,
Opt-out und Reload mit sichtbarer UI prüfen. Phase 4 bleibt von einer
serverseitig belegten Prüfung des Geländes abhängig.

## Phase 1: Development-Stand (2026-10-01)

`mvn -o -q clean package`: 62 Tests bestanden. ZIP enthält JAR, DE/EN-JSON
und `assets/ui/travel-tunnel.png`. Die erste Development-Aktivierung zeigte
einen API-Ladefehler, weil `TextureAsset.loadFromPlugin` nur im JAR sucht.
Nach Korrektur auf `loadFromFile` wurde das JAR erneut gebaut und gezielt
hochgeladen. Um 21:05:00 UTC meldete der Server `RELOADED ALL PLUGINS` und
`Stargate network ready`; die Bildtextur wurde zuvor aus der Datei registriert.
Der JAR-Hash auf Development stimmt mit dem lokalen Build überein
(`da49a772890a94fd09811a77699dbab5aee72a41140ec06acc0ef02c70f2b036`).
Die Welteinstellung ist bytegleich zur Sicherung; SQLite-Integrität ist `ok`
und es gibt null aktive Transfers. Sicherung von altem JAR, Welteinstellung
und konsistenter SQLite-Datenbank:
`/appdata/rising-world/development-server/.stargate-test-backup/travel-screen-20261001`.

Spielertest: lokalen Tordurchgang mit aktivierter und deaktivierter Anzeige,
zehn Sekunden Dauer, Disconnect/Reload und Abbruch prüfen. Danach in
beide Richtungen zwischen Development und Demo reisen: Bild am Quellserver
sofort beim Durchgang, am Zielserver für neue zehn Sekunden. Den
persönlichen Schalter auf dem Quellserver deaktivieren und prüfen, dass für
die laufende Reise auch am Zielserver kein Bild erscheint.

Der Nutzer hat `rw-demo` als zweiten Testserver für diese Phase freigegeben.
Dort wurden vor dem Upload null aktive Transfers und `integrity_check=ok`
festgestellt. JAR, DE/EN-Dateien, Welteinstellung und konsistente SQLite-
Datenbank liegen unter
`/appdata/rising-world/demo-server/.stargate-test-backup/travel-screen-20261001`.
Nur JAR, DE/EN-Dateien und Tunnelbild wurden hochgeladen. Um 21:18:19 UTC
meldete Demo `RELOADED ALL PLUGINS` und `Stargate network ready`; die Textur
wurde aus der Datei registriert. JAR- und Bild-Hashes sind auf Demo und
Development identisch. Demo-Welteinstellung blieb bytegleich zur Sicherung;
SQLite-Integrität ist `ok` und es gibt null aktive Transfers.

## Phase 1: Ankunftskorrektur nach Spielerrückmeldung

Das Tunnelbild wurde im Spiel positiv bewertet. Der eigene Durchgangssound
und die Sichtbarkeit wurden jedoch vor Ende des Bildes freigegeben; ein
einloggender Spieler im Servertransfer war vor dem Setzen der Zielposition
kurz sichtbar. Die Korrektur trennt Durchgangssound für Umstehende vom
persönlichen Ankunftssound. Nur der eigene Sound und die Sichtbarkeit folgen
dem Ende der zehn Sekunden; bei deaktiviertem Bild folgen sie unmittelbar
nach dem Setzen der Zielposition. `PlayerConnectEvent` und `PlayerSpawnEvent`
verbergen eingehende Transfers früh. Ein SQLite-Marker hält eine noch nicht
freigegebene Serverankunft über Relog und Reload nachverfolgbar.

`mvn -o -q clean package`: 63 Tests bestanden, darunter ein SQLite-Reopen-
Test für den Ankunftsmarker. Vor dem Upload waren beide Datenbanken intakt
und hatten null laufende Transfers. Die vorigen JARs, Welteinstellungen und
konsistente Datenbanken wurden je Server unter
`.stargate-test-backup/arrival-timing-20261001` gesichert. Development
meldete um 21:49:11 UTC und Demo um 21:51:49 UTC `RELOADED ALL PLUGINS`
und `Stargate network ready`. Beide verwenden denselben JAR-Hash
`07008175a2fa96db959bf835f2ae6d496098a9d57806ab31f416a51041a3fa7f`.
Die Welteinstellungen blieben bytegleich; SQLite-Integrität ist auf beiden
Servern `ok`, die neue Tabelle existiert und enthält derzeit null Marker.

Erneuter Spielertest: Beim lokalen Durchgang und bei Reisen in beide
Richtungen muss der eigene Durchgangssound erst nach Ende des Bildes ertönen.
Ein zweiter Spieler prüft, dass der Reisende während des Bildes verborgen
bleibt und am Ziel erst nach dessen Ende sichtbar wird. Bei deaktiviertem
Bild erfolgen eigener Sound und Sichtbarkeit sofort nach Ankunft. Einen
Relog während des eingehenden Servertransfers und einen Reload während einer
bereits erreichten, noch verdeckten Ankunft gesondert prüfen.

## Phase 1: Zielserverbildschirm nach zweiter Spielerrückmeldung

Der Spieler bestätigte den lokalen Sichtbarkeitsablauf, sah bei Serverreisen
aber den Ankommenden nackt im Tor und erhielt am Ziel keinen Bildschirm.
Die letzten abgeschlossenen Testtransfers brauchten vom Quell-Durchgang bis
zum abgeschlossenen Zieltransfer etwa 18–21 Sekunden. Damit war die zuvor
quellseitig gestartete Zehn-Sekunden-Frist schon vor der Zielankunft abgelaufen.
Auf ausdrückliche Rückfrage wurde eine neue zehnsekündige Anzeige auf dem
Zielserver gewählt. Ihr Start wird beim Ziel-Spawn beziehungsweise der ersten
Zielverarbeitung in SQLite gespeichert; wiederholte Relay-Meldungen und Relog
setzen sie nicht zurück. Die Quell-Einstellung bleibt für die Reise bindend.
Die Zielroutine setzt Unsichtbarkeit erneut nach Kleiderübernahme und finaler
Positionierung, bevor sie das Ende der Zielanzeige abwartet.

Der lokale Build bestand 63 Tests. Vor dem erneuten Upload waren beide
Testdatenbanken intakt, ohne aktive Transfers und ohne ausstehende Ankünfte.
JAR, Welteinstellung und konsistente SQLite-Datenbank wurden auf beiden
Testservern unter `.stargate-test-backup/target-screen-20261002` gesichert.
Development meldete um 06:02:15 UTC und Demo um 06:04:14 UTC
`RELOADED ALL PLUGINS` und `Stargate network ready`. Beide verwenden den
lokalen JAR-Hash
`a37333f75c964c5aed3e7bf345f5cecb872acdd55f867acbeb87cd43a438f037`.
Die Zielbildschirm-Tabelle existiert auf beiden Servern, SQLite-Integrität
ist `ok`, und beide Welteinstellungen blieben bytegleich zur Sicherung.
Erforderlicher Spielertest: Serverreise in beide Richtungen mit zweitem
Beobachter; am Ziel zehn Sekunden Bild ab Spawn, Reisender bis zum Ende
unsichtbar und danach bekleidet sichtbar, eigener Durchgangssound erst dann.
Bei deaktivierter Quell-Einstellung kein Bild und Freigabe nach finaler
Positionierung. Relog während der Zielanzeige darf die Frist nicht verlängern.

## Phase 1: Timer-Fix und Abnahme (2026-10-02)

Der Spieler bestätigte Bild, Sichtbarkeit und Ton, meldete jedoch beim
Verlassen des Quellservers eine Timer-Exception. Die Development-Logs zeigen
`Player not found` in `TravelScreenService.lambda$show$0` um 06:44:20 UTC;
auf Demo trat derselbe Fehler um 06:46:22 UTC auf. Beide Timer verwenden nun
die beim Start erfasste UID statt `Player.getUID()` nach Disconnect. Der
Disconnect verwirft laufende UI-Callbacks, lässt aber den persistenten Marker
einer noch nicht freigegebenen Zielankunft für den nächsten Login bestehen.

`mvn -o -q clean package` bestand mit 63 Tests. Die vorherigen JARs wurden
unter `.stargate-test-backup/timer-fix-20261002` gesichert. Development
meldete um 06:52:44 UTC, Demo um 06:53:45 UTC `RELOADED ALL PLUGINS` und
`Stargate network ready`. Beide JARs stimmen mit dem lokalen SHA-256
`4d2f81892d44e342a9461c5b8570cac5310cd8fec0875d1dc87f45678551056a`
überein. Der Nutzer hatte Phase 1 mit Behebung dieses Fehlers abgenommen.
Ein erneuter Serverwechsel mit dem korrigierten Build ist noch nicht im Spiel
beobachtet worden.

## Phase 2: Erstankunft auf Development (2026-10-02)

`PlayerConnectEvent.isNewPlayer()` unterscheidet neue Spieler von bestehenden
Bewohnern. Eine eigene SQLite-Tabelle speichert den Status des ersten regulären
Besuchs; ein erster Beitritt per Stargate-Transfer wird dort nur vorgemerkt.
Beim ersten regulären Beitritt bleibt der Spieler zunächst verborgen. Ein
zufällig gewähltes freies Tor wird während sieben eingehender Chevrons im
400-ms-Takt reserviert, öffnet, setzt den Spieler an den gespeicherten
Ankunftspunkt und schließt nach zehn Sekunden. Danach wird dessen ursprüngliche
Sichtbarkeit wiederhergestellt. Ist kein Tor frei, wird ein bereits eingehend
offenes Tor ohne neue Animation verwendet. Andernfalls gilt der normale Spawn.
Lokale und Relay-Wahlversuche gegen ein reserviertes Tor werden abgewiesen.
Ein Disconnect verwirft nur die laufende Animation; der Besuch wird beim
nächsten regulären Beitritt erneut versucht.

`mvn -o -q clean package` bestand mit 65 Tests. Vor dem Upload meldete die
Development-Datenbank `integrity_check=ok`, null aktive Transfers und null
ausstehende Ankünfte. JAR, Welteinstellung und konsistente SQLite-Datenbank
liegen unter `.stargate-test-backup/first-arrival-20261002`. Nur das JAR wurde
hochgeladen. Development meldete um 07:14:17 UTC `RELOADED ALL PLUGINS`, um
07:14:18 UTC `Stargate network ready`. Das JAR entspricht lokal und auf dem
Server dem SHA-256
`1e061de43dc583e1916aa272ce27b97a9ae47ae7ee554d4dc666050623998347`.
Die Welteinstellung blieb bytegleich, SQLite-Integrität ist `ok`, und die neue
Tabelle `stargate_first_visits` ist vorhanden. Demo bleibt auf dem abgenommenen
Phase-1-Build.

Spielertest: Mit einem neuen Testspieler auf Development bei freiem Tor
beitreten, Chevrons und zehn Sekunden Öffnung sowie die durchgehende
Unsichtbarkeit bis zum Ende beobachten. Danach reloggen: kein zweiter
Toreintritt. Bei nur eingehend offenem Tor und bei keinem verfügbaren Tor
jeweils einen weiteren neuen Testspieler verwenden. Einen neuen Spieler zuerst
von Demo per Stargate transferieren und erst anschließend regulär auf
Development beitreten lassen; nur dieser reguläre Beitritt soll die
Erstankunft auslösen. Während einer künstlichen Öffnung eine Wahl auf das
reservierte Tor versuchen und dessen Ablehnung prüfen.

## Phase 2: Demo2 und Spielerkorrektur (2026-10-02)

Der Nutzer legte `PluginDemo2` an, weil der native `deleteplayer`-Befehl den
Testspieler nicht zuverlässig entfernt. Beim ersten Spielertest wurde der
Spieler aus Beobachtersicht korrekt zum Tor gebracht. In seiner eigenen
Ansicht war zunächst der Standardspawn sichtbar. Die Erstankunft zeigt nun
ab Spawn den Reisebildschirm und entfernt ihn erst nach dem Setzen der
Torposition. Die ursprüngliche Unsichtbarkeit wird beim Spawn nochmals
erzwungen. Der persönliche Schalter für den Reisebildschirm gilt auch hier.

Für einen leeren `relay.advertisedHost` fragt das Plugin die öffentliche IP
über `resolveHost` beim Relay an und speichert sie erst nach erfolgreicher
Netzwerkanmeldung. Der Versuch mit `Server.getPublicIP()` ergab bei Demo2
zur Plugin-Laufzeit einen leeren Wert; die gesicherte Welteinstellung wurde
daraufhin sofort wiederhergestellt. Der Relay-Test zeigt nach Ergänzung des
Proxy-Headers `X-Real-IP` und des Host-Fallbacks für die gemeinsamen Docker-
Spielserver die erfolgreiche automatische Wiederherstellung von
`82.165.51.138` in `settings.PluginDemo2.json`. Ein manueller Host bleibt
als Überschreibung möglich. Bei fehlender erkennbarer IP erscheint nun eine
DE/EN-Spielermeldung statt nur eines Serverlogs.

Bei `network.enabled=false` startet das Plugin keine Relay-Verbindung mehr.
Es erzeugt lokale 16-stellige `LOCAL`-Adressen und lässt lokale DHD-Reisen
ohne Relay zu. Solche Tore werden später nicht automatisch zu globalen Toren;
die Cross-Server-Wahl von ihnen wird ausdrücklich abgewiesen. Dieser Modus
ist automatisch getestet, aber noch nicht im Spiel abgenommen.

Das Relay bestand `yarn test` lokal und im Docker-Build. Vor seiner
Testaktivierung waren null Transfers aktiv; MongoDB, Relay-Quellen,
Compose-Datei und der scoped Nginx-Vhost wurden gesichert unter
`/docker/apps/stargate-network/.stargate-test-backup/auto-host-20261002`.
Der isolierte Relay-Container läuft mit Image-ID
`10308d88a104929bd05e3bb0cd30d08e7e268918bffd8a750cb063caebe49b56`
und meldet `healthy`. Produktive Spielserver blieben unverändert.

Das Plugin bestand 67 Tests im isolierten Maven-Cache. `PluginDemo2` hatte
vor dem Upload null aktive Transfers und eine intakte SQLite-Datenbank.
JAR, DE/EN-Dateien, Welteinstellung und konsistente Datenbank wurden unter
`.stargate-test-backup/phase2-demo2-20261002` sowie vor der letzten
Bildschirmkorrektur unter `.stargate-test-backup/arrival-screen-20261002`
gesichert. Der finale Demo-JAR-Hash ist
`4375fa497f33fc418dc6e22f0de0aeb4d45f45007558103fc02bdff2d0c3430a`;
um 09:14:33 UTC meldete Demo `RELOADED ALL PLUGINS` und
`Stargate network ready`. SQLite-Integrität ist `ok`.

Die UID `76561198035762372` wurde ausschließlich in der Stargate-Tabelle
`stargate_first_visits` von `DONE` auf `PENDING` zurückgesetzt. Native
Spielerdaten blieben unberührt. Der nächste reguläre Demo2-Login dieses
Spielers wiederholt die Erstankunft genau einmal. Benötigter Spielertest:
Aus eigener Sicht muss der Bildschirm den Standardspawn bis zur Torposition
verdecken; danach gelten die zehn Sekunden Toröffnung und Sichtbarkeit erst
am Ende. Anschließend erneut reloggen und prüfen, dass keine zweite
Erstankunft erfolgt.

## Phase 2: Abnahme und visueller Restpunkt (2026-10-02)

Der Nutzer bestätigte die Erstankunft aus Sicht des ankommenden Spielers und
nahm Phase 2 ab. Beim Übergang vom Ladebildschirm zum Reisebildschirm ist der
Standardspawn noch ganz kurz sichtbar. Dieser visuelle Restpunkt wird für eine
spätere Untersuchung vorgemerkt; er hält die Abnahme und Phase 3 nicht auf.
Die Wirkung des Offline-Modus wurde bislang nur automatisch geprüft.

## Phase 3: Adressbuch auf Development und Demo2 (2026-10-02)

Das Relay speichert gelernte Tor-IDs in `address_books` mit eindeutigem
Schlüssel `(networkCode,uid,gateId)`. `syncAddressBook` nimmt bis zu 256
ausstehende IDs pro Anfrage idempotent an, bestätigt nur noch existierende
Tore und liefert die vollständige Liste des Spielers zurück. Beim Löschen
eines Tors entfernt es die ID aus allen Büchern und meldet `addressRemoved`
an verbundene Server. Beim Wiederverbinden synchronisiert das Plugin zuerst
ausstehende lokale Entdeckungen und ersetzt dann seinen Cache durch die
Relay-Liste. Bereits vorhandene Spielerbücher wurden nicht vorbefüllt.

Die SQLite-Tabelle `stargate_address_book` trennt Cache und ausstehende
Entdeckungen nach Netzwerkcode und UID. Auch vor der ersten erfolgreichen
Relay-Verbindung können lokale Entdeckungen unter `UNASSIGNED` vorgemerkt
werden. Beim Betreten eines Chunks mit einem gültig gebundenen Objekt-DHD
oder platzierten DHD-Modell wird dessen Tor gelernt und im Chat gemeldet.
Während Erstankunft und aktivem Transfer wird der kurz sichtbare
Zwischenspawn nicht für Entdeckungen gewertet. Spawn, Teleport und normale
Bewegung werden über einen Ein-Sekunden-Check erfasst. Das DHD und
`/sg gatelist` zeigen nur bekannte IDs; `/sg gatelist` ist für alle Spieler
verfügbar. Ein neuer DHD-Button öffnet den nativen 16-stelligen Eingabedialog.
Die eingegebene ID wird erst beim Öffnen einer lokalen oder entfernten
Verbindung gelernt. Bekannte lokale Ziele bleiben bei Relay-Ausfall wählbar.
Die ältere Relay-Abfrage `getAddressList` bleibt nur für alte Clients erhalten.

Der Plugin-Build bestand 69 Tests einschließlich Cache-Scope, ausstehender
Entdeckungen und Löschung. DE/EN haben je 241 gleiche Schlüssel; die JSONs
sind gültig. Das ZIP enthält JAR, beide Sprachen und Tunnelbild. Relay
`yarn test`, Docker-Build und der isolierte MongoDB-Transfer-Smoke bestanden;
der Smoke prüft zusätzlich leeres Buch, idempotentes Lernen, UID-Trennung,
serverübergreifende Abfrage und Löschung. Die Testdatenbank wurde vom Smoke
entfernt.

Vor dem Upload waren null Transfers aktiv. MongoDB, Relay-Quellen, Compose
und beide Plugin-JARs, Welteinstellungen, DE/EN-Texte und konsistente SQLite-
Datenbanken wurden gesichert unter
`/docker/apps/stargate-network/.stargate-test-backup/addressbook-20261002`
sowie jeweils `.stargate-test-backup/addressbook-20261002` in den beiden
Spielserver-Verzeichnissen. Das Test-Relay wurde zuerst aktualisiert; sein
Image ist `40220fe42fd4c10823a75e8548f6e875fc94e4ef7143c46268030c53e758a4b4`
und meldet `healthy`. Danach meldeten Development um 11:23:23 UTC und Demo2
um 11:24:07 UTC `RELOADED ALL PLUGINS` und `Stargate network ready`. Eine
kleine Korrektur wiederholt die Chunk-Prüfung beim Netzwerkcode-Wechsel und
nach einem SQLite-Fehler. Der erneute Build bestand wieder 69 Tests; die
beiden Server meldeten um 11:28:25 beziehungsweise 11:28:30 UTC Reload und
Netzwerkbereitschaft. Beide
verwenden JAR-SHA-256
`886212fda459534ff141a8a3a3f8c7a7cbb0d0708137a9594d204942469e9b89`.
Die SQLite-Integrität ist auf beiden Servern `ok`; unmittelbar nach dem
Upload waren Relay- und lokale Adressbücher leer und es gab null aktive
Transfers. Produktive Spielserver blieben unverändert.

Spielertest: Mit Spieler A einen DHD-Chunk auf Development betreten (auch
Spawn oder Teleport) und Chatmeldung, DHD und `/sg gatelist` prüfen. Mit
Spieler B ohne Besuch muss die Liste leer bleiben. Mit A auf Demo2 anmelden
und das serverübergreifend bekannte Tor prüfen. Eine noch unbekannte gültige
ID manuell anwählen: erst nach offener Verbindung muss sie erscheinen;
ungültige oder blockierte Wahl darf nichts lernen. Ein bekanntes lokales Tor
bei Relay-Ausfall wählen, danach Wiederverbindung und Sync prüfen. Ein
Testtor löschen und die Entfernung auf beiden Servern, auch nach Offline-
Wiederverbindung, prüfen. Nach Spielerrückmeldung Phase 3 abnehmen, erst
dann mit der Machbarkeitsprüfung für Phase 4 beginnen.

## Phase 3: Farbunterscheidung und Abnahme (2026-10-03)

Der Nutzer nahm Phase 3 unter der Bedingung ab, dass Buttons bekannter
Adressen lokale und externe Tore farblich unterscheiden. DHD-Ziele erhalten
nun einen zwei Pixel breiten dunkelgrünen Rand, wenn ihre Tor-ID in der
lokalen Tor-Datenbank existiert, und einen orangefarbenen Rand für externe
IDs. Die Zuordnung wird bei jeder Adressbuch-Aktualisierung und beim
Seitenwechsel neu angezeigt. Die konkrete Clientdarstellung der Farben
wurde noch nicht separat im Spiel beobachtet.

Der korrigierte Build bestand 69 Tests; `git diff --check` war sauber.
Vor dem Upload wurden null aktive Transfers und ein gesundes Test-Relay
bestätigt. Die aktuellen JARs, Einstellungen, DE/EN-Dateien und konsistenten
SQLite-Datenbanken liegen je Server unter
`.stargate-test-backup/addressbook-border-20261003`. Development meldete um
08:36:23 UTC, Demo2 um 08:36:26 UTC `RELOADED ALL PLUGINS` und
`Stargate network ready`. Beide nutzen JAR-SHA-256
`3fd870075b18c51fd5e8a8480c33872afba4de537a44f016658acb5ab3513ff1`;
SQLite-Integrität ist auf beiden Servern `ok`. Es erfolgte kein Release.

## Phase 4: Machbarkeitstest angehalten (2026-10-03)

Eine zeitweilige, nur auf Development geladene Probe las über `World.getChunk`
drei Chunks: einen bereits genutzten Referenz-Chunk `(111,33)` sowie die
entfernten Chunks `(384,128)` und `(640,384)` in freien Sektoren. Die
serverseitigen LOD-Oberflächenhöhen waren gültig (`201.0`, `156.3125`,
`105.5`), ebenso die Wasser- und Nachbarhöhen. Die entfernten Sektoren
`(1,0)` und `(2,1)` wurden beim ersten Lesen von der Spielwelt generiert;
es wurden keine Tore oder DHDs angelegt.

`Chunk.getChunkPart()` lieferte an der Oberflächenhöhe keinen gültigen
Terrain-Teil. Die direkte Abfrage über `World.getChunkPart()` lieferte auch
für den darunterliegenden Teil an allen drei Punkten keinen gültigen Teil.
Damit konnten tatsächlicher Terrain-Freiraum, DHD-Standort und sicherer
Ankunftspunkt nicht verlässlich gegen Voxel und Kollisionsgeometrie geprüft
werden. Objekt-, Bau- und Pflanzenlisten waren zwar lesbar, ersetzen diese
Prüfung nicht. Nach der vereinbarten technischen Grenze wird Phase 4 nicht
aktiviert. Die Beobachtung passt zur API-Dokumentation: LOD liefert eine
Höhenrepräsentation; die eigentlichen 3D-Terrainvoxel liegen in `ChunkPart`.

Die Probe wurde aus dem Quellcode entfernt. Development wurde um 08:44:02 UTC
auf den gesicherten Phase-3-JAR-Hash
`3fd870075b18c51fd5e8a8480c33872afba4de537a44f016658acb5ab3513ff1`
zurückgesetzt und meldete `Stargate network ready`. Demo2 blieb auf
demselben Phase-3-Build. Der Rückbau-Build bestand 69 Tests;
Development-SQLite-Integrität ist `ok`.

### Nachprobe: Generierungszeit und Spielerpräsenz (2026-10-03)

Eine zweite temporäre Development-Probe las dieselben bereits generierten
Chunks nach fünf und nach 60 Sekunden erneut. Beide entfernten Chunks und
der Referenz-Chunk lieferten jeweils dieselben LOD-Höhen, aber in vier
vertikalen Chunk-Teilen um die Oberfläche weder über `Chunk.getChunkPart()`
noch über `World.getChunkPart()` einen gültigen Terrain-Teil. Seit der ersten
Generierung der entfernten Chunks waren zu diesem Zeitpunkt mehr als zehn
Minuten vergangen. Bei beiden Messungen waren null Spieler online. Eine
bloße Wartezeit erklärt den fehlenden Terrain-Teil daher nicht; ob ein
Spieler in der Nähe das Ergebnis ändert, bleibt offen und braucht einen
späteren Spielertest. Die Probe wurde aus dem Quellcode entfernt und das
gesicherte Phase-3-JAR auf Development wiederhergestellt. Phase 4 bleibt
bis zu einer verlässlichen serverseitigen Freiraumprüfung angehalten.
Development meldete um 08:53:54 UTC `RELOADED ALL PLUGINS` und
`Stargate network ready`; der JAR-Hash ist wieder
`3fd870075b18c51fd5e8a8480c33872afba4de537a44f016658acb5ab3513ff1`.

### Nachprobe mit Spieler im Chunk (2026-10-03)

Der Nutzer hat die Farben der bekannten Adressen im DHD abgenommen und war
für die zweite Terrainprobe auf Development online. Um 09:00:44 UTC meldete
die temporäre Probe einen Spieler. Sein geladener Chunk `(111,34)` lieferte
gültige `ChunkPart`-Daten sowohl über `Chunk.getChunkPart()` als auch über
`World.getChunkPart()` für vier vertikale Teile um die Oberfläche. Die
entfernten, bereits länger generierten Chunks `(384,128)` und `(640,384)`
lieferten gleichzeitig weiterhin gültige LOD-Höhen, jedoch keine gültigen
`ChunkPart`-Daten. Damit ist die Abhängigkeit von der geladenen
Spielerumgebung für diesen Test bestätigt. Eine sichere automatische
Platzierung in einem entfernten, nicht geladenen Sektor ist damit weiterhin
nicht nachgewiesen; Phase 4 bleibt entsprechend der technischen Grenze
angehalten. Die Probe wurde aus dem Quellcode entfernt und das gesicherte
Phase-3-JAR auf Development wiederhergestellt.
Um 09:01:39 UTC meldete Development erneut `RELOADED ALL PLUGINS` und
`Stargate network ready`; der JAR-Hash ist wieder
`3fd870075b18c51fd5e8a8480c33872afba4de537a44f016658acb5ab3513ff1`.

### LOD-Platzierungsversuch auf Development (2026-10-03)

Auf Nutzerwunsch wird die bisherige strenge `ChunkPart`-Grenze für einen
kontrollierten Development-Versuch gelockert. Die 32×32 LOD-Höhen eines
Chunks müssen vollständig endlich sein, die niedrigste Zelle muss strikt
über Meereshöhe `90` liegen, und der Unterschied zwischen höchster und
niedrigster Zelle darf höchstens vier Welt-Einheiten (zwei Meter) betragen.
Wasser, vorhandene Objekte und Bauelemente schließen den Testchunk aus.
Eine der vier Himmelsrichtungen wird zufällig aus den Richtungen gewählt,
bei denen Tor, DHD und Ankunftspunkt innerhalb des Chunks liegen und die
bekannten Pflanzen ausreichend entfernt sind. LOD-Höhen sind nur eine
Näherung; der tatsächliche Freiraum wird damit nicht garantiert.

Die Development-Probe fand im Chunk `(640,384)` Höhen von `104.5` bis
`105.9375` Welt-Einheiten, keine Wasserfläche, Objekte oder Bauelemente
und zwei Pflanzen. Die Pflanzen schließen zwei Himmelsrichtungen aus;
für die anderen beiden sind die Stellpunkte frei. Die jeweiligen Höhen
von Tor, DHD und Ankunftspunkt wurden separat gelesen. Für einen gezielten
Spielertest ist ausschließlich auf Development der Admin-Befehl
`/sg testdiscovery` aktiviert. Er versucht einmalig die koordinierte
Registrierung eines Tors mit DHD in diesem Chunk; ist der Sektor schon
belegt, legt er nichts an. Vor dem Upload wurden JAR und eine konsistente
SQLite-Datenbank unter `.stargate-test-backup/discovery-placement-20261003`
gesichert; Datenbankintegrität war `ok`, der Zielsektor frei und es gab
null aktive Transfers. Der Build bestand 69 Tests. Development meldete um
09:16:44 UTC `RELOADED ALL PLUGINS` und `Stargate network ready`; der
hochgeladene JAR-Hash ist
`88aca077336e403a66dc35bca808adc398471778bd01273f693c5742e0dbc1e2`.
Der Ingame-Befehl und die physische Platzierung stehen noch zur Prüfung an.

Der Admin hat `/sg testdiscovery` ausgeführt. Um 09:17:12 UTC wurde Tor
`3C5FA80F70294306` im Chunk `(640,384)` mit Ostausrichtung registriert.
Torposition `(20504,105.46875,12304)`, DHD-Position
`(20488,105.28125,12300)`; der Ankunftspunkt wurde gemäß bestehender
Platzierung 2.4 Welt-Einheiten hinter dem Tor gespeichert. SQLite-
Integrität ist weiterhin `ok`; Sektorindex, Tormodell, Durchgang und DHD
haben jeweils genau einen Datensatz, und das Relay kennt die Tor-ID. Der
Spieler bestätigte im Sicht- und Reisetest, dass Tor, DHD und Ankunftspunkt
frei auf dem Boden stehen.

Für die eigentliche Auto-Discovery wurde danach ein vorbereiteter Pool pro
Ausgangstor festgelegt: fünf zufällige freie Sektoren im eingestellten
Kreisradius, jeweils bis zu fünf passende Chunks. Ein Hintergrundscan
beginnt ringförmig in der Sektormitte. Die Kandidaten werden in SQLite
gehalten; mehrere Ausgangstore dürfen denselben Zielsektor im Pool haben.
Nach einer Entdeckung werden belegte Zielsektoren aus allen Pools entfernt
und die Pools der bestehenden sowie des neuen Tors nachgefüllt. Die
Erfolgswahrscheinlichkeit wird nach der gültigen DHD-Anwahl geprüft.

Die Pool-Variante ist nun lokal implementiert. `stargate_discovery_candidates`
speichert Chunks pro Ausgangstor, Sektor und Einstellungsradius;
`stargate_discovery_cooldowns` speichert die nächste erlaubte Versuchszeit
pro Spieler. Der Scanner liest je Schritt höchstens einen Chunk und prüft
höchstens 25 zentrale Ringpositionen pro zufällig gewähltem Sektor. Maximal
fünf Sektoren mit jeweils maximal fünf passenden Chunks werden gehalten.
Ein DHD-Button startet den Versuch; Wahrscheinlichkeit und Kreisradius
stehen in den Admin-Einstellungen (Standard 75 Prozent und 10 Sektoren,
gültig 0–100 Prozent beziehungsweise 2–50 Sektoren). Der Cooldown beträgt
fünf Minuten auch nach Fehlschlag oder fehlendem Standort. Die neue Adresse
lernt nur der auslösende Spieler. Der temporäre Admin-Testbefehl wurde aus
dem Code entfernt. 72 lokale Tests, DE/EN-JSON und das ZIP mit JAR,
Einstellungen und Sprachdateien wurden geprüft.

Vor dem Development-Upload gab es null aktive Transfers; JAR, DE/EN-Texte,
Standardeinstellungen und konsistente SQLite-Datenbank wurden unter
`.stargate-test-backup/discovery-pool-20261003` gesichert. Development
meldete um 09:35:57 UTC `RELOADED ALL PLUGINS` und `Stargate network ready`.
Eine Verkleinerung der zentralen Ringsuche auf 25 Chunks wurde um 09:39:54
UTC ebenfalls geladen. Die Pool-Tabelle füllt sich im Hintergrund; um
09:40 UTC hatten vier der fünf lokalen Tore bereits Kandidaten und ein Tor
fünf Zielsektoren. Ingame-Prüfung von Button, Erfolg, Fehlschlag, Cooldown,
Nachfüllen und realer Ankunft steht noch aus. LOD-Höhen bleiben eine
Näherung und ersetzen keine Kollisionserkennung für Höhlen oder Überhänge.

Die Sektorauswahl wurde für schnellere Poolbildung auf zufällige nahe
Sektoren (bis drei Sektoren Entfernung) fokussiert; wenn dort keine freien
Kandidaten mehr vorhanden sind, wird der volle konfigurierte Radius genutzt.
Die Grenzen und Zufallsauswahl bleiben erhalten. Um 09:48:25 UTC meldete
Development erneut `RELOADED ALL PLUGINS` und `Stargate network ready`.
Danach erreichten alle fünf lokalen Ausgangstore jeweils fünf gespeicherte
Zielsektoren; die Chunksummen pro Tor betrugen 18, 12, 14, 21 und 14,
jeweils höchstens fünf pro Sektor. SQLite-Integrität ist `ok`, der aktive
JAR-Hash `c215b75a13e4639c5d68c6e7b0e3a4f2e2d5f182cf28b696bc9ba6ffa08f5045`.
Der Spieler war beim anschließenden Klicktest nicht mehr online; die
Cooldown-Tabelle ist noch leer. Dieser Spielertest bleibt offen.
Nach einer DE/EN-Textkorrektur meldete Development um 09:51:25 UTC erneut
`RELOADED ALL PLUGINS` und `Stargate network ready`; alle fünf Pools
enthielten weiterhin jeweils fünf Zielsektoren, SQLite-Integrität `ok`.

## Phase 4: Anwahl nach Spielerrückmeldung (2026-10-03)

Der Spieler bestätigte die Platzierung des erzeugten Tors und DHDs. Die
sofortige Erfolgsnachricht und Adressvergabe beim DHD-Klick wurden geändert:
eine Discovery reserviert jetzt das Ausgangstor, spielt sieben ausgehende
Chevrons mit der bestehenden 7-Sekunden-Schrittzeit und entscheidet erst
danach anhand der Erfolgswahrscheinlichkeit. Ein Fehlschlag setzt das Tor
zurück und löst den bestehenden Fehlwahlton aus. Bei Erfolg wird ein
Standort aus dem vorbereiteten Pool validiert, das Tor angelegt und direkt
eine lokale Verbindung vom Ausgangstor dorthin eröffnet. Die Adresse wird
erst nach erfolgreichem Verbindungsbeginn für den auslösenden Spieler
gespeichert. Disconnect und Plugin-Reload geben reservierte Animationen
frei. Die Admin-Kategorien `audio` und `discovery` haben nun DE/EN-Schlüssel
für die spielerbezogene Übersetzung im OZ-Tools-Panel.

Lokal: 72 Tests bestanden, DE/EN-JSON parsebar, ZIP enthält JAR und beide
Sprachdateien. Vor dem Upload gab es null aktive Relay-Transfers. Der vorige
JAR, beide Sprachdateien, Welteinstellungen und ein konsistenter SQLite-
Snapshot liegen unter `.stargate-test-backup/discovery-dial-20261003`.
Development lud den neuen JAR-Hash
`ac942bf7424f1600dc3719c8ccedb5e04943c736a74050ae7cb76d90f012f372`;
um 15:25:48 Serverzeit folgten `RELOADED ALL PLUGINS` und
`Stargate network ready`. SQLite-Integrität ist `ok`, der Pool enthält 93
Chunk-Kandidaten. Der erneute Spielertest der sichtbaren Anwahl, der
Fehlwahl, der automatischen Verbindung und der deutschen Kategorien steht
noch aus. Die LOD-Prüfung bleibt eine Näherung für unbekanntes Gelände.

Eine abschließende Prüfung des Ausgangstors vor der Standortregistrierung
wurde ergänzt. Der endgültige Development-JAR hat den Hash
`5a02d5a1458ec0fe371f7b9d0dfcd11af3eb088e425caeb05bdd6661ea19fab0`.
Er wurde um 15:27:58 Serverzeit mit `RELOADED ALL PLUGINS` und
`Stargate network ready` aktiviert; erneut bestanden 72 lokale Tests.

Der anschließende Spielerbericht bestätigte die deutschen Kategorien,
meldete aber englische Feldnamen und Beschreibungen innerhalb von `audio`
und `discovery`. OZ Tools sucht hierfür Schlüssel nach dem Einstellungsnamen
(`tc.setting.audio.theme`, `tc.setting.audio.volume`,
`tc.setting.discovery.successpercent`, `tc.setting.discovery.radiussectors`).
Diese DE/EN-Schlüssel wurden ergänzt. Alle acht Labels/Beschreibungen sind
in beiden JSON-Dateien vorhanden und parsebar; der erneute Build bestand
72 Tests. Development lud die Texte und den JAR-Hash
`5a54036067d7a771bcf88dbb1a264a1db82b31f36128ac98e1e07edc19866242`;
um 15:35:40 Serverzeit folgten `RELOADED ALL PLUGINS` und
`Stargate network ready`. Die Anzeige der Feldtexte und die Discovery-
Anwahl benötigen noch den erneuten Spielertest.

## Nachtrag: Erstes Tor im Startsektor

Nach Abnahme der Discovery-Fehlwahl wünscht der Spieler ein erstes Tor samt
DHD bei neuer Plugin-Installation, sofern kein Tor vorhanden ist. Der
Startsektor ist der Sektor des globalen Standardspawns. Die Platzierung
verwendet dieselbe Chunk- und Freiraumprüfung wie Auto-Discovery; nur
geeignete Chunks innerhalb dieses Sektors kommen infrage. Die Adresse
wird keinem Spieler vorab gelernt. Ein SQLite-Marker unterscheidet eine
neue Installation von einer bestehenden Installation, deren Tore später
gelöscht wurden. Bei fehlendem Standort oder Relay-Verbindung bleibt der
Bootstrap ausstehend und wird begrenzt erneut geprüft. Der Spieltest in
einer frischen Welt erfolgt auf Demo; die bestehende Dev-Welt darf kein Tor erhalten.

Die Implementierung prüft vor dem ersten Anlegen der `stargates`-Tabelle,
ob bereits eine Stargate-Installation vorliegt. Der neue SQLite-Marker
bleibt bei einer frischen Installation bis zur erfolgreichen Platzierung
`PENDING`; ältere Datenbanken starten mit `DONE`. Der Scanner prüft jeweils
25 Chunks in kleinen Hintergrundschritten, zuerst ringförmig um die
Sektormitte, dann um den Standardspawn und anschließend weiter im
Startsektor. Ohne Relay wartet er bei aktivierter Netzwerkfunktion; im
lokalen Offline-Modus kann er eine lokale Tor-ID anlegen.

Lokal: 74 Tests bestanden, darunter Reopen und Bestandserkennung des
Bootstrap-Markers. Vor dem Development-Upload waren acht Tore vorhanden,
SQLite-Integrität `ok` und null aktive Relay-Transfers. JAR, Welteinstellung
und konsistenter SQLite-Snapshot wurden unter
`.stargate-test-backup/initial-gate-20261003` gesichert. Development lud
JAR-Hash `30731fd15d991438c5434e34f543e165010eb1299447d08162e6ed982590cc43`;
um 15:48:56 UTC meldete der Server `RELOADED ALL PLUGINS`, um 15:48:57
`Stargate network ready`. Der Marker steht auf `DONE`, die Toranzahl blieb
acht und SQLite-Integrität ist `ok`. Der positive Test benötigt eine frische
Plugin-Datenbank/Welt.

Der Nutzer gab den Demoserver für den positiven Test frei. Dessen bisherige
Welt `PluginDemo2` und Plugin-DB blieben erhalten; vor dem Test wurden
`server.properties`, JAR, Sprachdateien, Einstellungen und eine konsistente
Kopie von `PluginDemo2.db` unter
`/appdata/rising-world/demo-server/.stargate-test-backup/initial-gate-demo-20261003`
gesichert. Die neue Superflat-Welt auf Höhe 100 dient nur der kontrollierten
Bootstrap-Prüfung. Ein erster Durchlauf in `StargateBootstrapTest` erzeugte
ein vollständiges lokales Tor, während die neue Einstellungsdatei wegen
Dateibesitz zunächst nicht lesbar war. Der Besitz wurde korrigiert; dieser
Durchlauf ist deshalb kein Nachweis für die Relay-Registrierung.

Im zweiten frischen Durchlauf `SGStartTest2` waren die Netzwerkeinstellungen
vor dem Start lesbar. Um 15:58:54 UTC wurde das Netzwerk bereit, um 15:58:56
UTC entstand die globale Tor-ID `F09288789F424C93` im Startsektor `(0, 0)`.
Das Relay kennt diese ID im Development-Netz. SQLite enthält genau je einen
Datensatz für Tor, Sektor, Modell, DHD und ausgerichteten Durchgang; das
Adressbuch enthält null Einträge und `integrity_check` ist `ok`. Nach einem
Plugin-Reload um 16:00:40 UTC blieben genau ein Tor, Marker `DONE` und leeres
Adressbuch erhalten. Der Spieler wurde beim ersten Betreten zum neuen Tor
gebracht und konnte das Tor samt DHD nutzen. Nach dem Wechsel auf Development
erschien die neue Adresse in seiner Liste; die Anwahl und Ankunft am neuen
Demo-Tor funktionierten. Der Spieler nahm die Funktion am 2026-10-03 ab.
Bei der ersten Ankunft brach er sich die Beine; außerdem war das DHD leicht
im Boden versenkt, blieb aber bedienbar. Beides ist als akzeptierte
Beobachtung für eine spätere Prüfung festgehalten.

Nach der Rückkehr des Spielers auf Development wurde der akzeptierte
`SGStartTest2`-Datenbankstand zusätzlich gesichert. Das Testtor
`F09288789F424C93` wurde über `unregisterGate` aus dem Relay entfernt;
Relay-Tor und Adressbucheinträge stehen auf null, ebenso der lokale
Development-Adressbucheintrag. Die temporäre Welt-Datenbank wurde nach der
Sicherung um dieses Tor bereinigt (`integrity_check: ok`). Die gesicherte
`server.properties` aktiviert wieder `PluginDemo2` mit Weltart `Default`.
Beim Neustart lud Demo `PluginDemo2.db`, meldete `Stargate network ready` und
behielt genau sein vorheriges Tor (`integrity_check: ok`). Der JAR-Hash blieb
`30731fd15d991438c5434e34f543e165010eb1299447d08162e6ed982590cc43`.
Die Testwelten und Sicherungen bleiben für die spätere Fehlersuche erhalten.
