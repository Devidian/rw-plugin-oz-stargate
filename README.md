# OZ Stargate

Players can hide the Stargate shortcut in the plugin settings. It remains visible by default.

Command-based Rising World Stargate plugin adapted from `rw-plugin-maven-template`. The Tools UI, JSON settings, DE/EN i18n, Info/Status, optional bridges, and single-listener entry point remain in place.

## Inventory custody

Admins can use `/sg pack`, `/sg unpack`, and `/sg recover`. Packing serializes inventory **and worn clothes** into world-local SQLite before removing them. Unpacking requires empty inventory and clothes and consumes the stored set once. Interrupted states remain blocked until recovered. Legacy copy-only snapshots cannot be unpacked.

## Gate discovery (phase 2)

Admins can use `/sg registerGate` at their current position and `/sg unregisterGate <ID>`. All players can use `/sg gatelist` and `/sg dial <target ID> [source ID]`. The source ID is optional only when exactly one local gate exists. The relay owns globally visible gate IDs; world-local SQLite keeps coordinates and orientation. A target with an incoming dial is reserved for one minute. This phase does not transfer players or inventory.

`settings.<world>.json` contains `relay.url`, the required `relay.advertisedHost` (reachable IPv4 or DNS name), optional `networkCode.override`, `networkCode.trusted`, and `forbiddenActions.ChangeGameMode` (default `true`). The trusted code is written by the relay response and shown read-only in the admin workflow. The computed network code includes the forbidden-action flags; it is a routing value, not a secret. The default relay URL is `wss://sgn.omega-zirkel.de/ws`. Only use this build on Development/test servers.

Phase 3A records a player's UID, name, world play time, and permission group per server when the player connects, spawns, or changes group. Admins can inspect available observations with `/sg trust [UID]`. It does not calculate a trust score or move inventory. With `forbiddenActions.ChangeGameMode=true`, attempted game-mode changes are cancelled and show a localized native error dialog.

Phase 3B adds `/sg warp <target gate>` after a successful dial; phase 4 makes it available to all players. Inventory and clothes are secured in a durable SQLite journal, removed and saved before the server-change prompt. The target must claim the transfer before applying it at spawn. Declined or expired unclaimed travel restores source custody; ambiguous partial application requires review. The target's previous inventory/clothes are kept separately and restored on its next ordinary login after return travel. Health, hunger, thirst, and stamina travel within target-supported bounds; permissions and game mode remain local. Basic travel and controlled source-restart recovery passed player acceptance. Phase-4 non-admin travel, cancellation restoration and management/debug access denial also passed player acceptance.

## Development: local sector ownership

Every registered gate owns its world-local `(sectorX, sectorZ)` address. As an
admin, stand at the intended ring position and look away from its intended
front (toward the back of the gate), then run
`/sg placegate` without an ID. An available relay reserves a globally unique
ID, and the plugin commits the ID, sector, lowered model, aligned passage and
an arrival point about 1.2 m in front of the ring together. Arrival faces away
from the ring. `/sg placedhd <ID>` remains a separate optional step. The
existing `/sg registerGate` and `/sg placegate <ID>` commands still support
manual setup and moving an unaligned model. New model placements put the ring
about 1.2 m deeper in the ground; stored older models are unchanged. A second
gate in the same sector is refused. `network.enabled` is off by default and
controls cross-server travel; the relay stays connected for ID reservation
and transfer recovery. Local dialing and passage await native player acceptance.

## Installation

Requires OZ Tools and PluginAPI 0.9.3.2. Build with Java 20 and `mvn -B clean package`. Deploy `dist/OZStargate/` to `Plugins/OZStargate/`, preserving `settings.<world>.json` and `<world>.db`.

## Installation (0.1.0)

Requires OZ Tools 0.26.2 and the Rising World Unity API 0.9.3.2 baseline. Extract the release ZIP into Plugins, preserving world settings and SQLite databases during updates. Configure a private/trusted OZ Stargate Network 0.1.0 relay and a reachable advertised game-server address. Back up both player and plugin databases before rollback; never restore only one side of an in-flight transfer.

## 0.2.0 release candidate

One gate per sector supports local travel by default. An administrator can
place or move a gate with its DHD, place either separately, or remove both from
the radial menu. A placed gate receives a globally unique address, aligned
passage and outward-facing arrival point in one step. Players choose destinations
on the DHD and enter an open wormhole to travel. The models, collision, lighting
and wormhole animation are included in the plugin package.

Cross-server travel requires OZ Stargate Network 0.2.0 and an explicit
`network.enabled=true` setting on each participating server. With the default
`false`, the relay is still contacted to reserve globally unique gate IDs.
When a target still holds an earlier visitor inventory, a new cross-server
arrival is rejected before the source inventory is cleared. Back up native
Player.db and plugin SQLite databases together before upgrades. Do not change
plugin or relay versions while a transfer is active.

## Development: phase 5A dialing

With a phase-5A relay, dialing encodes seven chevrons (default 14 seconds total) before contacting the target. An incoming connection interrupts an unfinished outgoing sequence. Once final connection negotiation or a wormhole is active, both gates are reserved. The wormhole closes after 60 seconds or disconnect; travel remains outgoing-only. No inventory is removed during dialing. Upgrade plugin and relay together: both require dialSequenceVersion=1 for new dials; legacy transfer recovery stays compatible.

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

`/sg gatelist`, `/sg dial` and `/sg warp` are now admin debug commands. Ordinary players use the linked DHD and passage zone. `/sg help` explains the relevant workflow for the player's role; admins additionally see debug and zone-management commands.

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
