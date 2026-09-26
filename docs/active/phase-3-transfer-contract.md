# Phase 3 transfer contract and safety boundary

## Objective

Move one player between two open gates without duplicating or losing inventory and worn clothes. Phase 3A first records player observations and enforces the configured local game-mode rule; phase 3B adds transport only after the 3A player test.

## Verified PluginAPI 0.9.3.2 fields

| Value | Source and target API | Transfer decision |
| --- | --- | --- |
| Identity | `Player.getUID()` | Global player key; never use world-local `getDbID()`. |
| Name | `getName()` | Observation and chat only; target's own name remains authoritative. |
| Play time | `getTotalPlayTime()` | Seconds spent on the current world; observation only. |
| Permission group | `getPermissionGroup()` / `setPermissionGroup(String)` | Record per server. Do not grant a source server's group on the target without a trust policy. |
| Inventory | `Inventory.serialize()`, `deserialize(byte[])`, `clear()`, `syncWithClient()` | Binary custody with durable single-use state. |
| Worn clothes | `Clothes.serialize()`, `deserialize(byte[])`, `removeAll()` | Separate binary custody; inventory serialization does not include worn clothes. |
| Health | `getHealth()`, `setHealth(int)`, `getMaxHealth()`, `setMaxHealth(int)` | Candidate transfer fields; clamp to target-supported range. |
| Hunger/thirst/stamina | `getHunger()/setHunger(int)`, `getThirst()/setThirst(int)`, `getStamina()/setStamina(int)` | Candidate transfer fields; clamp to valid ranges. |
| Game mode | `isCreativeModeEnabled()` / `setCreativeModeEnabled(boolean)` | Only the creative flag is exposed on Player; there is no full current `GameMode` getter. Follow target world rules unless a safe mapping is defined. |
| Arrival position | `Player.setPosition(Vector3f)`, `setRotation(Quaternion)` | Use destination gate's world-local SQLite transform after `PlayerSpawnEvent`. |
| Connect consent | `connectToOtherServer(target, password, Callback<Boolean>)` | Callback reports acceptance/decline; acceptance is not proof that target joined. |

`PlayerSpawnEvent` fires after world loading. Phase 3B pairs the incoming transfer with this event by UID and a unique transfer ID. The WebSocket envelope limit is 1 MiB; each base64 inventory/clothes field is bounded at 650,000 characters. Oversized transfers are rejected before source inventory is removed.

## Durable state outline for phase 3B

1. The origin persists a UUID transfer ID and source inventory/clothes before sending `transferStart`. A unique relay index permits only one active transfer per network/player. The source must own an unexpired dial window and both gates must still exist.
2. The relay persists `PENDING` and routes `incomingTransfer`. The target persists a local `PREPARED` row before acknowledging. It rejects an already connected target player, conflicting local custody, or a missing gate.
3. The relay moves to `ACCEPTED`. The origin compares its live inventory/clothes with its snapshot, persists `CLEARING`, clears both, calls `Server.savePlayers()`, persists `CLEARED`, and sends `transferReleased`. Only the relay's durable `RELEASED` acknowledgment permits the native connection prompt.
4. Decline requests an abort. Only an authoritative `ABORTED` result permits source restoration. Acceptance starts connection and is not proof of arrival. The origin retains its journal until target completion.
5. At target spawn, `transferClaim` atomically changes `RELEASED` to `CLAIMED`. A claim cannot be aborted or expired automatically. The target saves its prior inventory/clothes in separate visitor custody, persists `APPLYING`, applies incoming inventory and supported vital stats, teleports to the local gate transform, forces player saving, and persists `APPLIED`. It then sends `transferDone`; the relay records `DONE` and removes its binary payload.
6. Before claim, cancellation and the one-minute transfer deadline can atomically move the relay to `ABORTED`. After claim, uncertainty stays blocked. On restart an `APPLYING` target only confirms completion when its live serialized inventory/clothes exactly match the incoming snapshot. A partial mismatch requires manual review; it does not trigger another application.
7. On return, outgoing completion and the visitor-base change to `RESTORE_PENDING` are one SQLite transaction. The previous destination inventory/clothes are restored on the player's next ordinary login to that world. A new incoming visit keeps that base in custody. No inventories are merged.

Relay states: `PENDING → ACCEPTED → RELEASED → CLAIMED → DONE`; `PENDING`, `ACCEPTED`, or `RELEASED` may become `ABORTED`. Source and target keep local diagnostic journal rows during the experiment. `Server.savePlayers()` is the native save boundary; SQLite and the game's player database are separate stores, so ambiguous interrupted mutations are quarantined rather than guessed.

## Validation status

- Maven compilation and five SQLite tests passed, including journal reopen, preserved target inventory, conditional state transitions, and single-use restoration.
- An isolated relay integration test passed source release before claim, ownership/network isolation, one active transfer per player, duplicate completion, late claim after abort, expiry, and a forced relay crash/restart after claim.
- The player accepted native declined connection, outbound spawn application with inventory/clothes, return travel, and restoration of the prior destination inventory. Arrival orientation looked plausible but was not precisely checked. Controlled source restart during the confirmation, unclaimed expiry, complete restoration and repeat login without duplicates were also accepted (transfer 6c4c700d-d669-4a65-917c-cc558f6e2981). A forced target game-process crash after claim remains unverified. Deployment remains limited to the two designated test servers.

## Rollback and validation

Phase 3A changes no inventory and can be rolled back by restoring the previous plugin/relay binaries while retaining MongoDB observations. Phase 3B adds SQLite journal and visitor-base tables and a MongoDB transfer collection/indexes without modifying old custody tables. Never roll back while a transfer or visitor base is unresolved. First reconcile it; then restore previous binaries while retaining the new journals for review. Consistent pre-deployment Player.db and Stargate backups exist on both test servers. Use only Development test inventories. No public or production transfer deployment is allowed without authenticated server identity and explicit acceptance.
