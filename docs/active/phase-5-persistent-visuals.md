# Phase 5D.2: persistent gate models

Status: completed and accepted by the player on 2026-09-27, including manual rp recovery after the client-presence correction. User accepted the corrected preview scale and requested continuation.

## Objective / ownership

OZ Stargate owns optional static Milky Way model placement per registered local gate. Admin `/sg placegate <ID>` stores current feet position and horizontal facing; `/sg removegatemodel <ID>` removes only that model. This milestone tests persistence and visibility before explicit travel alignment (5D.3) and animation. Relay and Tools need no changes.

## Decisions / constraints

- Additive `stargate_visuals` SQLite table and gate-deletion cleanup trigger. Existing gate arrival, DHD, horizon and custody records are untouched. Placement messages explain that travel alignment is a separate step.
- Fixed accepted model scale: two world units per source metre, approximately 6.14m tall. Feet pivot and front-facing convention match preview.
- Share lazy model/material/import assets between previews and persistent instances. Keep imported meshes readable for native combination. No colliders or effects.
- One model transform per local gate. Server-thread refresh once per second, nearest 16 models per spawned connected player; enter at 128 world units, retain until 160. Reconcile after spawn/reload, remove on disconnect/unload/deletion. Release unused model instances; keep one shared asset set until unload.
- Save before replacing the live transform. Missing local gate and non-finite/vertical direction must not create a record. No relay request or player inventory mutation from visual commands.
- Rollback previous JAR/i18n; optional table/trigger may remain inert. Back up test-server databases/artifacts before deployment, exclude active transfers. No server restart or release.

## Checklist

- [x] Extract shared accepted model factory; preserve preview behavior.
- [x] Persist validated placements and clean up after local gate deletion.
- [x] Add admin commands, localized help, bounded player visibility and lifecycle wiring.
- [x] Test reopen/replacement/removal/cascade, preserved gameplay rows, direction validation and visibility boundaries/cap.
- [x] Package/entrypoint/asset checks; deploy both designated test servers and verify reload/readiness.
- [x] Player acceptance checkpoint completed: user accepted the tested behavior and explicitly confirmed the final manual rp recovery. No separate multi-client observation is claimed beyond the user report.

## Risks / follow-up

Client rendering and performance require native acceptance. Rendering all visible static rings can be expensive; proximity and count limits bound instances per client. No collision is intentional. Model placement does not yet move arrival or passage; 5D.3 must explicitly align these with the displayed ring, handle rotation, and suppress accidental transit during editing. Earlier preview automatic expiry/orientation checks were not explicitly reported as completed.

Validation: 14 tests pass; entrypoint and asset/ZIP verification pass. Both installed class contents match. Additive table/trigger verified, existing gate/DHD/horizon rows exactly preserved. Development reload/network-ready 2026-09-26 23:59:13 UTC; Demo reload 23:59:24 and network-ready 23:59:25 UTC. Player acceptance pending.

## Reload visibility correction (2026-09-27)

Player accepted the tested behavior except rp: model disappears until relog. Client Player.log proves the sequence twice (02:03 and 02:04 local): Add game object / Loaded obj, then CLIENT RESET API, then RELOADED ALL PLUGINS. GateVisualService.start previously rendered synchronously inside onEnable and latched the viewer state before the engine reset. Start now enqueues the initial tick, using Plugin.enqueue documented next-tick behavior; subsequent refresh logic is unchanged. Closed guard prevents a stale queued tick after unload. No placement/database changes. Native rp retest pending. API reference: https://javadoc.rising-world.net/latest/net/risingworld/api/Plugin.html#enqueue(java.lang.Runnable).

Reload fix validated: Maven package/14 tests/entrypoint check pass. Both test servers activated on 2026-09-27 UTC (Development 00:13:19, Demo 00:13:17). Connected Development client Player.log now records CLIENT RESET API at line 188405, then Create/Add game object 240 at lines 188448–188449, then successful OBJ load at 188531, proving corrected native ordering without relog. Manual rp visual confirmation remains pending. Prior JARs: `/docker/apps/stargate-visual-reload-backup/`.

### Manual rp reset correction, second iteration

Player reported auto-reload succeeds but manual rp briefly restores then removes the model. Client logs for 02:14:01 and 02:14:26 local show the reset still arrives after the deferred model import. Next-tick scheduling alone is therefore insufficient. Added one readWorldPosition client-presence request per viewer (not per gate) every five seconds, with at most one outstanding request and an eight-second response deadline. Missing/wrong position or lost reply rebuilds only that viewer via remove-before-add. A new request token, viewer/model/placement identity guards and invalidation on removal/distance/replacement reject stale callbacks. No repeated blind model resend while presence checks succeed. Periodic monitoring also handles a reset after an earlier successful reply. Model placement data is unchanged.

17 tests/package/entrypoint checks pass, including request-rate bounds, lost reply and late callback rejection. API readWorldPosition requests the actual client position; source: https://javadoc.rising-world.net/latest/net/risingworld/api/worldelements/GameObject.html. Native verification pending. Prior JARs at `/docker/apps/stargate-presence-backup/`.

Presence fix activation: Development reload/network-ready 2026-09-27 00:19:52 UTC; Demo 00:19:50 UTC. Connected client successfully imported model 297 after API reset. No repeated recovery or rendering-suspended log entries observed during initial healthy-client monitoring; manual rp retest still pending. Allow approximately 15 seconds after the client reset for interval plus timeout/recovery.

Final acceptance (2026-09-27): player confirmed "ja geht! abgehakt" after the focused manual rp retest. Persistent placement milestone 5D.2 complete. Next planned milestone: explicit model/arrival/passage alignment (5D.3); no next deployment at this checkpoint.
