# Phase 5C: event horizon

Status: implemented, activated on both designated test servers and accepted by the player on 2026-09-27, including the relay retry fix.

## Objective / ownership

Allow a player to enter a gate's configured passage zone to initiate the existing cross-server transfer. The DHD and passage zone share the local gate ID. This plugin owns geometry, persistence and movement handling; the relay remains authoritative for gate direction, open connection and expiry. Reuse TransferService for inventory custody, confirmation, cancellation and arrival.

## Constraints / decisions

- Trigger only on entry into an OPEN, OUTGOING gate with a matching live source/target window. Incoming, dialing, idle, expired or disconnected gates cannot initiate travel.
- Require exit and re-entry after cancellation or a failed attempt. Standing inside a zone when a connection opens must not initiate travel. Arrival, spawn and plugin reload must establish a baseline without initiating travel.
- Keep movement processing lightweight: cache geometry, avoid database/network work for ordinary movement, and guard deferred attempts against duplicates and stale player/connection state.
- Keep the plugin entry class limited to event delegation. Respect cancelled movement and recheck actual position before initiating travel.
- Do not register permission areas that could interfere with land claims. Verify native movement and visual-area APIs before choosing the final zone setup and visualization.
- Keep player warp commands through this acceptance milestone. Returning them to admin-only debug use follows acceptance of the complete DHD/horizon workflow.
- No new dependency, relay protocol change, model import or production deployment is planned.

## Implementation checklist

- [x] Setup: /sg sethorizon <gate> [X Y Z] at admin feet, default 3x3x1m, world-axis bounds (not view-relative), each dimension 0.5–8m; require distance <=16m to gate. Reject overlapping/touching other zones. /sg showhorizon previews a translucent blue Area3D for this admin for 30s; /sg removehorizon removes it. No permission-area registration. Foot tolerance 0.2m below setup point.
- [x] Persist a gate-owned zone with deletion cleanup and load a cached geometry snapshot.
- [x] Add guarded movement entry handling and spawn/disconnect/reload cleanup.
- [x] Reuse TransferService.warp after checking the exact outgoing gate window.
- [x] Add localized setup/validation feedback and player-test instructions.
- [x] Test geometry boundaries, entry latching, overlap handling, persistence/reopen and gate deletion.
- [x] Package and verify PluginAPI/entrypoint architecture.
- [x] No active transfers before rollout; SQLite/artifact backups verified. Both designated test servers reloaded and rejoined the network on 2026-09-26. Identical deployed classes match the local tested package; additive table exists, existing gates/DHDs retained.
- [x] Player acceptance of tested horizon behavior, including the focused cancellation/re-entry retest on 2026-09-27. A separately coordinated native reload-persistence test was not reported; persistence/reopen is covered by automated tests.

## Migration / rollback

Use additive world-local storage; preserve existing gate, DHD, inventory and transfer records. Back up SQLite consistently before deployment. Rollback plugin artifacts while retaining additive data; never restore an old inventory database over subsequent player activity.

## Risks / remaining decisions

Fast movement, overlapping zones, other plugins cancelling movement, and spawn inside a zone can cause unintended or repeated activation. Geometry and latching tests must cover these cases, with native gameplay acceptance for actual event order and confirmation behavior. Movement is sampled at the actual position on the next server tick. A complete jump/teleport across the thin zone with no sampled position inside deliberately does not initiate travel; walk into the zone for acceptance. Admin preview is temporary; permanent gate artwork is a later milestone.

Player acceptance: all tested behavior passed except retry after declining travel. Root cause is the relay consuming an otherwise open gate window. Relay-only fix and isolated regression test completed; native decline/exit/re-entry retest pending. No plugin code change required.

Final acceptance: player confirmed the retry fix works on 2026-09-27. Phase 5C is complete. Hold at this milestone; command-access changes and gate artwork remain separate work.
