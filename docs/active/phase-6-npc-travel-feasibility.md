# NPC travel through Stargates: feasibility (2026-09-30)

## Objective and current decision

Determine whether NPCs can be detected at an open event horizon and moved
locally or between servers. This is an investigation only; no NPC travel is
enabled in the current Development build. Player decision on 2026-09-30:
defer implementation until after the next Stargate release.

## API and plugin findings

- The bundled PluginAPI exposes NPC spawn, death, damage and transform events,
  but no position-change event for NPCs. `NpcTransformEvent` changes NPC type,
  not world position.
- `World.getAllNpcsInRange(position, range)` returns nearby NPCs;
  `Npc.getPosition()` and `Npc.getGlobalID()` provide polling identity and
  position. `Npc.setPosition()` and `setRotation()` support local teleportation.
- `World.spawnNpc(typeID, variant, position, rotation)` and `Npc.delete()`
  support destination creation and source removal across servers. The spawn
  receives a new global ID.
- Existing `HorizonService` tracks players via `PlayerChangePositionEvent` and
  `HorizonEntryTracker`. Its zone geometry and open outgoing gate checks can
  be shared with a dedicated NPC movement service, but the player event path
  cannot detect NPC crossings.
- NPC state has multiple exposed fields (health, age, group, taming, clothing,
  equipment, behavior). The API does not expose a generic full-NPC snapshot or
  an enumerable set of arbitrary plugin attributes. A cross-server copy would
  therefore require an explicitly versioned subset and documented exclusions.

Official API: [Npc](https://javadoc.rising-world.net/latest/net/risingworld/api/objects/Npc.html),
[World](https://javadoc.rising-world.net/latest/net/risingworld/api/World.html),
[NpcTransformEvent](https://javadoc.rising-world.net/latest/net/risingworld/api/events/npc/NpcTransformEvent.html).

## Proposed staged implementation

1. Poll only open outgoing horizons every 0.2 s with a small `getAllNpcsInRange`
   radius; track previous positions per NPC global ID. Require a geometric
   crossing from outside to inside, not simple presence at startup. Prune old
   IDs and guard arrival with a short cooldown. Skip NPCs with riders.
2. Test local travel first using `Npc.setPosition`/`setRotation` and the
   existing persisted destination arrival. Verify ordinary and tamed NPCs,
   open/close transitions, reload, gate deletion and no travel loops.
3. For network travel, define a bounded NPC snapshot and a durable relay
   handoff with idempotency. The destination must acknowledge a single spawn
   before source deletion is finalized; recovery must reconcile both sides
   after timeouts and restarts. Preserve only fields explicitly supported by
   the snapshot, and decide how plugin-owned attributes and NPC ownership are
   handled before implementation.

## Risks / validation

Polling cost grows with concurrently open gates; measure on Development with
many NPCs. Rapid movement can skip a narrow horizon between samples, so use
the previous-to-current position segment against the aperture. Cross-server
despawn/spawn can duplicate or lose NPCs without durable custody and recovery.
Local travel can be implemented independently after the first detection test.
