# OZ Stargate

Command-based Rising World Stargate plugin adapted from `rw-plugin-maven-template`. The Tools UI, JSON settings, DE/EN i18n, Info/Status, optional bridges, and single-listener entry point remain in place.

## Inventory custody

Admins can use `/sg pack`, `/sg unpack`, and `/sg recover`. Packing serializes inventory **and worn clothes** into world-local SQLite before removing them. Unpacking requires empty inventory and clothes and consumes the stored set once. Interrupted states remain blocked until recovered. Legacy copy-only snapshots cannot be unpacked.

## Gate discovery (phase 2)

Admins can use `/sg registerGate` at their current position and `/sg unregisterGate <ID>`. All players can use `/sg gatelist` and `/sg dial <target ID> [source ID]`. The source ID is optional only when exactly one local gate exists. The relay owns globally visible gate IDs; world-local SQLite keeps coordinates and orientation. A target with an incoming dial is reserved for one minute. This phase does not transfer players or inventory.

`settings.<world>.json` contains `relay.url`, the required `relay.advertisedHost` (reachable IPv4 or DNS name), optional `networkCode.override`, `networkCode.trusted`, and `forbiddenActions.ChangeGameMode` (default `true`). The trusted code is written by the relay response and shown read-only in the admin workflow. The computed network code includes the forbidden-action flags; it is a routing value, not a secret. The default relay URL is `wss://sgn.omega-zirkel.de/ws`. Only use this build on Development/test servers.

Phase 3A records a player's UID, name, world play time, and permission group per server when the player connects, spawns, or changes group. Admins can inspect available observations with `/sg trust [UID]`. It does not calculate a trust score or move inventory. With `forbiddenActions.ChangeGameMode=true`, attempted game-mode changes are cancelled and show a localized native error dialog.

Phase 3B adds `/sg warp <target gate>` after a successful dial; phase 4 makes it available to all players. Inventory and clothes are secured in a durable SQLite journal, removed and saved before the server-change prompt. The target must claim the transfer before applying it at spawn. Declined or expired unclaimed travel restores source custody; ambiguous partial application requires review. The target's previous inventory/clothes are kept separately and restored on its next ordinary login after return travel. Health, hunger, thirst, and stamina travel within target-supported bounds; permissions and game mode remain local. Basic travel and controlled source-restart recovery passed player acceptance. Phase-4 non-admin travel, cancellation restoration and management/debug access denial also passed player acceptance.

## Installation

Requires OZ Tools and PluginAPI 0.9.3.2. Build with Java 20 and `mvn -B clean package`. Deploy `dist/OZStargate/` to `Plugins/OZStargate/`, preserving `settings.<world>.json` and `<world>.db`. No public release has been made.

## Installation (0.1.0)

Requires OZ Tools 0.26.2 and the Rising World Unity API 0.9.3.2 baseline. Extract the release ZIP into Plugins, preserving world settings and SQLite databases during updates. Configure a private/trusted OZ Stargate Network 0.1.0 relay and a reachable advertised game-server address. Back up both player and plugin databases before rollback; never restore only one side of an in-flight transfer.
