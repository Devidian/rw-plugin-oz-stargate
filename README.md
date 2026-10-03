# OZ Stargate

Players can hide the Stargate shortcut in the plugin settings. It remains visible by default.

Rising World Stargate plugin adapted from `rw-plugin-maven-template`. Players travel through local gates or, when administrators enable it, between trusted servers. The Tools UI, JSON settings, DE/EN i18n, Info/Status, optional bridges, and single-listener entry point remain in place.

## Inventory custody

Admins can use `/sg pack`, `/sg unpack`, and `/sg recover`. Packing serializes inventory **and worn clothes** into world-local SQLite before removing them. Unpacking requires empty inventory and clothes and consumes the stored set once. Interrupted states remain blocked until recovered. Legacy copy-only snapshots cannot be unpacked.

## Gate discovery (phase 2)

Admins can use `/sg registerGate` at their current position and `/sg unregisterGate <ID>`. All players can use `/sg gatelist` and `/sg dial <target ID> [source ID]`. The source ID is optional only when exactly one local gate exists. The relay owns globally visible gate IDs; world-local SQLite keeps coordinates and orientation. An established wormhole has a one-minute travel window.

`settings.<world>.json` contains `relay.url`, the required `relay.advertisedHost` (reachable IPv4 or DNS name), optional `networkCode.override`, `networkCode.trusted`, and `forbiddenActions.ChangeGameMode` (default `true`). The trusted code is written by the relay response and shown read-only in the admin workflow. The computed network code includes the forbidden-action flags; it is a routing value, not a secret. The default relay URL is `wss://sgn.omega-zirkel.de/ws`. Use the relay only with a controlled group of trusted servers behind TLS.

Phase 3A records a player's UID, name, world play time, and permission group per server when the player connects, spawns, or changes group. Admins can inspect available observations with `/sg trust [UID]`. It does not calculate a trust score or move inventory. With `forbiddenActions.ChangeGameMode=true`, attempted game-mode changes are cancelled and show a localized native error dialog.

Phase 3B adds `/sg warp <target gate>` after a successful dial; phase 4 makes it available to all players. Inventory and clothes are secured in a durable SQLite journal, removed and saved before the server-change prompt. The target must claim the transfer before applying it at spawn. Declined or expired unclaimed travel restores source custody; ambiguous partial application requires review. The target's previous inventory/clothes are kept separately and restored on its next ordinary login after return travel. Health, hunger, thirst, and stamina travel within target-supported bounds; permissions and game mode remain local. Basic travel and controlled source-restart recovery passed player acceptance. Phase-4 non-admin travel, cancellation restoration and management/debug access denial also passed player acceptance.

## Development: local sector ownership

Every registered gate owns its world-local `(sectorX, sectorZ)` address. As an
admin, stand at the intended ring position and look away from its intended
front (toward the back of the gate), then run
`/sg placegate` without an ID. With `network.enabled=true`, the relay reserves
a globally unique ID. In solo/offline mode (`network.enabled=false`), the
plugin creates a 16-character `LOCAL` address usable only within this world.
The plugin commits the ID, sector, lowered model, aligned passage and
an arrival point about 1.2 m in front of the ring together. Arrival faces away
from the ring. `/sg placedhd <ID>` remains a separate optional step. The
existing `/sg registerGate` and `/sg placegate <ID>` commands still support
manual setup and moving an unaligned model. New model placements put the ring
about 1.2 m deeper in the ground; stored older models are unchanged. A second
gate in the same sector is refused. `network.enabled` is off by default, so
local gates and local travel work without a relay. Existing global gates are
kept locally, but require the network to be enabled before removal. `LOCAL`
addresses stay local if the network is enabled later.

## Installation

Requires OZ Tools and PluginAPI 0.9.3.2. Build with Java 20 and `mvn -B clean package`. Deploy `dist/OZStargate/` to `Plugins/OZStargate/`, preserving `settings.<world>.json` and `<world>.db`.

### Sound themes

The package contains only `audio/silent/` with 17 silent Ogg Vorbis files. Their
durations match the privately supplied reference list; the reference recordings
are not part of the repository or release. Admins can add `audio/<theme>/` with
the same filenames and choose the folder name in PluginSettings. Reopen the
admin settings to see newly added folders. Missing files in a selected theme
fall back to `silent`. Avoid symbolic links and use letters, digits, `_` or `-`
for folder names. `audio.volume` accepts 0–100% (default 70%), and each player
can switch Stargate sounds off in personal plugin settings. Added theme files
survive normal plugin updates when their names do not collide with packaged files.

Required filenames: `dhd_1.ogg`, `dhd_2.ogg`, `gate_roll_b.ogg`,
`gate_roll_c.ogg`, `chevron_out_1.ogg`, `gate_open.ogg`, `open_loop.ogg`,
`shutdown_b.ogg`, `go_trough.ogg`, `dial_fail.ogg`, and `chevron_1.ogg`
through `chevron_7.ogg`. Use Ogg Vorbis mono files. Custom sounds are
administrator supplied content; verify rights before distribution.

## Installation and compatibility

Requires OZ Tools 0.26.2 and the Rising World Unity API 0.9.3.2 baseline. Extract the release ZIP into Plugins, preserving world settings and SQLite databases during updates. Configure a private/trusted OZ Stargate Network 0.4.0 relay and a reachable advertised game-server address. Back up both player and plugin databases before rollback; never restore only one side of an in-flight transfer.

On a fresh installation with no gates, the plugin searches the sector of the
server's global default spawn for one suitable site and creates a gate with a
DHD. It uses the same terrain and clearance checks as Discovery, and retries
in bounded background batches if no site or relay connection is available.
Existing installations are never seeded later after their gates are removed.
The new address is learned only when a player reaches its DHD.

## Gate travel

One gate per sector supports local travel by default. An administrator can
place or move a gate with its DHD, place either separately, or remove both from
the radial menu. A networked gate receives a globally unique address, aligned
passage and outward-facing arrival point in one step. Players choose destinations
on the DHD and enter an open wormhole to travel. The models, collision, lighting
and wormhole animation are included in the plugin package.

Cross-server travel requires OZ Stargate Network 0.4.0 and an explicit
`network.enabled=true` setting on each participating server. With the default
`false`, the plugin does not contact the relay and creates local addresses.
If the advertised host is empty, the plugin asks the relay for the public IPv4
observed at its WebSocket proxy and saves it after registration. A DNS name
or a different reachable address can be set manually in the world settings.
If no public IP is available, gate placement reports this in chat.
When a target still holds an earlier visitor inventory, a new cross-server
arrival is rejected before the source inventory is cleared. Back up native
Player.db and plugin SQLite databases together before upgrades. Do not change
plugin or relay versions while a transfer is active.
An empty target inventory and empty clothing are not kept as a visitor base.
If a player manually switches servers while a nonempty visitor inventory is
still present on the previous server, a later arrival there remains blocked
until that visit is resolved through gate travel. Do not clear visitor-base
rows manually; they protect the previous local inventory.
Only incoming wormholes are announced in chat by default, and only to players
in the gate's sector. Players can enable other Stargate chat messages in their
personal plugin settings for debugging.

## Development: phase 5A dialing

Dialing encodes seven chevrons (default 49 seconds) before contacting the target. After target approval, the source gate opens immediately while the target lights seven chevrons at 400 ms intervals (2.8 seconds total). Travel becomes available only after the target activation ends. A local target is checked only at the seventh source lock; an incoming connection interrupts an unfinished outgoing sequence and resets its lights. Once final connection negotiation or a wormhole is active, both gates are reserved. The wormhole closes 60 seconds after travel becomes available or on disconnect; travel remains outgoing-only. No inventory is removed during dialing. Upgrade plugin and relay together: both require dialSequenceVersion=1 for new dials; legacy transfer recovery stays compatible.

## Development: phase 5B DHD console

As admin, run /sg binddhd <local gate ID>, then interact within 60 seconds with a placed usable object (for example an empty chest). The console must be within 16 metres of the gate. Interact again to open its DHD as any player; choose a remote address and press Dial. Gate status and seven chevrons update during dialing. Existing /sg warp remains available until the event horizon milestone.

/sg unbinddhd arms the next object interaction to remove its link; /sg canceldhd cancels selection. Using/clicking a console requires distance <=4 metres and a matching object identity. Links are stored in the world plugin SQLite database; deleting a gate removes its links. Rollback can leave the additive stargate_dhds table intact.

## Development: persistent DHD model

As admin, stand within 16m of a local gate and run `/sg placedhd <gate ID>`. One original
radial DHD model appears 2.5m in front of you. Players within 2m interact directly with its console
to open the existing DHD destination menu; the model does not bypass dialing or travel
checks. `/sg removedhd <gate ID>` removes only that model. Existing `/sg binddhd` world-object
consoles continue to work. Positions live in the additive `stargate_dhd_models` SQLite
table and are removed when their gate is deleted. The model's simple collision is independent
of its asynchronously imported mesh. The supplied Blend Swap DHD archive is not packaged;
the first implementation used original parametric geometry. The archive labels the creator's
model CC0 and its Stargate design as noncommercial Fan Art. Noncommercial use is not ruled out
by that warning, while rights in the underlying design remain separate.

### Supplied DHD visual candidate

The active Development/Demo DHD visual is now a reduced derivative of the user-supplied
`Stargate DHD.zip` model by blenderjunky. It preserves the authored symbols, keys and
activation dome. The previous parametric mesh remains in the asset folder for rollback.
`assets/models/dhd/supplied-dhd-SOURCE-NOTICE.txt` and
`supplied-dhd-LICENSE.html` describe the original CC0/Fan Art notice and changes.
This plugin is used noncommercially; rights in the underlying Stargate design are separate.
The direct interaction, collision and persisted placement workflow are unchanged.

## Development: phase 5C passage zone

As admin, stand at the centre of the intended passage, within 16m of the local gate, and run `/sg sethorizon <local gate ID>`. This stores a world-axis box, default X width 3m, Y height 3m and Z depth 1m, extending up from your feet with 0.2m floor tolerance. For another size use `/sg sethorizon <gate> <X> <Y> <Z>` (each 0.5–8m); for an east/west passage swap X and Z. This is not relative to your look direction. Other gate zones cannot touch or overlap.

`/sg showhorizon <gate>` shows a blue preview to the admin for 30 seconds; setup shows it too. `/sg removehorizon <gate>` removes only the zone. These commands are admin-only; passage works for all players. Permanent gate art is a later milestone.

Leave the zone, dial through its DHD, close the UI, then walk into the zone after the outgoing wormhole opens. The existing travel confirmation and inventory custody workflow starts. Incoming/idle/dialing/expired/offline gates do not initiate travel. After declining, leave and re-enter to try again. Standing inside while a wormhole opens, spawning or arriving there does not initiate travel. Movement uses actual positions sampled after movement events; jumping/teleporting completely across a zone between samples intentionally does not count as entering it.

Bounds persist in the additive `stargate_horizons` table and are removed when the gate is deleted. Existing DHD links and arrival positions stay as registered. Rollback artifacts may leave this table intact; do not roll inventory databases back over later gameplay. Player chat travel remains available through horizon acceptance.

## Development: travel command access after phase 5C

`/sg gatelist` shows each player's discovered addresses. `/sg dial` and `/sg warp` remain admin debug commands. Ordinary players use the linked DHD and passage zone. `/sg help` explains the relevant workflow for the player's role; admins additionally see debug and zone-management commands.

## Development: discovered address book

Each player's address book starts empty and is scoped to the relay network code.
Entering a chunk with a valid linked DHD or placed DHD model discovers its gate
and announces the address in chat. The DHD lists known addresses and offers a
native dialog for a 16-character gate ID. A manually entered address is learned
only when its dial opens a connection. `/sg gatelist` shows the same known IDs.
SQLite caches addresses by network and UID; locally known gates remain dialable
if the relay disconnects. Pending local discoveries synchronize on reconnect.
The relay removes deleted gates from all books and broadcasts the removal;
reconnecting servers replace their cache with the authoritative book. Existing
books are not populated from the old unrestricted gate list.

## Development: static Milky Way preview (5D.1)

Admins can run `/sg previewgate` on level ground while looking horizontally. A temporary, non-colliding gate appears at their feet, upright and aligned with the horizontal view direction. Walk backwards to inspect its front. The model is visible only to that admin, lasts two minutes, and is replaced by another preview command. `/sg clearpreview` removes it immediately; disconnect and plugin unload also remove it.

This is a static shape/scale/import test with a neutral material. It has no travel function and does not move registered gates, DHDs, zones or arrival points. Permanent placement, materials and animation follow visual acceptance. Source model by David Gian-Cursio (CC0); notice and build report ship beside the optimized asset. The offline build script is documented in `scripts/assets/README.md`.

### Persistent static gate models (admin setup)

- `/sg placegate <ID>` saves/replaces the Milky Way model for a local registered gate at your horizontal position, about 1.2 m below your feet, upright in your horizontal viewing direction. Step backwards to see the front. Height is about 6.1 m.
- `/sg removegatemodel <ID>` removes only its visible model. It retains the gate registration and existing travel configuration.
- Placements survive reconnects and plugin/server reloads. Nearby players see them automatically; each client receives at most the nearest 16 models. Models appear within 128 world units (about 64 m) and disappear beyond 160 (about 80 m).
- This static placement milestone has no collision or animation. Arrival points, DHD bindings and passage zones keep their existing positions. Use the explicit alignment setup below to bind travel geometry to the model.

### Align passage and arrival with the model

1. Place the gate model while idle. Provide a walkable platform through the opening and in front of the ring.
2. Stand at the desired arrival point, centred 1–4m in front of the model, then `/sg aligngate <ID>`. Your standing height is preserved; arrivals face outward from the gate front.
3. The old passage is replaced by a thin circular zone rotated with the model. Entry checks the player's body centre. `/sg showhorizon <ID>` shows its bounding frame for 30 seconds; frame corners are outside the circular trigger.
4. Setup changes require an online idle gate without active transfers. To move an aligned model, explicitly `/sg removehorizon <ID>`, move the model, then align again. Removing a zone retains the previous arrival until it is deliberately replaced.

Alignment is opt-in and saved atomically. Existing gates retain their earlier placement until configured. Final collision and wormhole effects are separate work.

### Visual dialing (development milestone 5D.4)

Persisted gate models now show network state automatically: the inner ring turns during
outgoing dialing, seven chevrons change to amber with confirmed progress (top last),
and an open connection uses a brighter amber. Incoming connections stop any outgoing
visual sequence. Idle, closed and disconnected gates return to their neutral appearance.
The two bottom chevrons remain unused by seven-symbol dialing. Ring angles are cosmetic;
relay gate IDs are not encoded as model glyph addresses. The temporary `previewgate`
remains static. Event horizon effects are a later milestone.

The next development refinement uses a slower ring sweep (60 degrees over 2.8 seconds)
and separate moving chevron blocks. Confirmed locks move each block radially inward;
closure/offline releases it again over 0.65 seconds. The companion relay defaults to
3.5 seconds per step (nominally 24.5 seconds for seven steps). Existing relay cadence
overrides remain supported; they do not change transfer authorization.

**Correction to the preceding development refinement:** native movement parameters are
speed values, not seconds. The current implementation uses timed eased poses: ring
acceleration/deceleration, stop, V inward and back, then confirmed strip lighting. Fixed
blocks and V housings remain neutral. The authored V strip surfaces are separate meshes.
Default relay cadence is now 5 seconds per symbol (35 seconds nominal for seven symbols).

The authored inner light surface of the fixed chevron now lights together with the V
strips. Both use brighter amber once the network reports OPEN; housings remain neutral.

### Wormhole visuals (development milestone 5E.1)

An established connection displays a blue/silver water surface inside the gate and a short
forward opening surge. The supplied reference video provides an 11.67-second water loop with bright central caustics and a dark blue rim.
Both sides render the surface. Normal closure dissolves irregularly from the rim inward, with a maximum 80% white blend at 0.3 seconds (0.6 seconds total); network loss hides
it immediately. The forward opening surge reaches 3.78 source metres, holds for 0.4 seconds, then retracts with a brief smaller rear vortex. Later proximity viewers see the current phase, without replaying a
completed opening surge. Effects are cosmetic and use no collision/damage/travel logic.

### Fixed gate collision (development)

Placed gates use overlapping fixed collision segments around the ring, preserving the open aperture.
Decorative protrusions have no separate collision; water/opening effects remain cosmetic.
Chevron strips and fixed inner lights illuminate at the inward stop before the V returns home.

### Authored gate surface (development visual test)

Frame, ring and Chevron housings use a shade-readable derivative of the supplied author's mottled color reference with
UV coordinates and separate metallic/smoothness values. Light strips retain accepted colors.
This is an approximation of the source procedural material; fine bump mapping is not included.
