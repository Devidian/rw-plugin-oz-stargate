# Phase 5E.1 — event-horizon surface and opening effect

Status: current visual milestone accepted by the player on 2026-09-27; complete for this iteration.
The sections below retain implementation/test history; the final acceptance supersedes earlier pending notes.

## Objective / scope

Plugin-only visual water surface in the accepted gate opening, plus a short forward opening
surge. Network OPEN/ready is the only visibility authority. No new collider, damage, travel
permission, relay protocol or persistence change. Preserve accepted chevron motion/lighting.

Deterministic procedural OBJ meshes and PNG textures (repository-owned code, no external
art dependency). Circular surface overlaps the inner rim slightly; body centre remains
3.075 source metres above the feet, scale2. Front is -Z. Countermoving translucent ripple
layers over an opaque blue/silver base; a temporary textured dome extends toward the front.
Shared assets, existing animation loop/proximity/lifecycle. Opening time is recorded in the
network client so proximity/relog viewing never restarts an already completed surge.

## Checklist

- [x] Procedural assets, reproducibility/mesh validation and texture inspection.
- [x] Pure OPEN/age pose policy and regression tests for abort/offline/late viewing.
- [x] Native model/material integration with shared resource cleanup and existing rp repair.
- [x] Package/entrypoint checks, backup, scoped two-test-server activation and data verification.
- [x] Current opening/water/closure visuals accepted; see final acceptance for scope.

## Risks / rollback

Transparency sorting, illumination and visual smoothness require native inspection. Opening
surge is cosmetic; no new gameplay hazard or delay. Countermoving layers approximate water
without a custom shader. Restore prior JAR to roll back; extra assets are inert. No schema
migration. Do not continue beyond this milestone before player feedback.

## Technical completion

35 tests/package, entrypoint and procedural asset reproducibility/geometry checks pass.
Both test servers reloaded/network-ready; classes/assets match and five placement tables
remain unchanged. No native new mesh loads were observed in the client-log segment, so
visual rendering/import remains part of the player acceptance test.

## Player feedback / refinement

Player confirms the effect behaves as described, but appearance remains far from the original.
Current refinement: a normal closure collapses the surface toward its centre over0.8s; forward
surge peak increases from1.8 to2.8 source metres. Closing is a cosmetic tail only, never an
extension of travel permission. Offline/new dial cancels the tail immediately. No new assets.

Deferred at player's request until final visual review:
- Chevron sequence should be V inward -> light on -> V home (locked), rather than light after return.
- Original gate textures/material detail are still missing; neutral body material is current.
- Compare water/surge appearance with the original; present layered effect is not claimed faithful.

Collapse/extension refinement activated:39 tests/package and entrypoint checks pass. Both test
servers reloaded/network-ready, assets and placement rows preserved. Native shutdown/extent
retest pending. A pre-update Demo rendering error had only a null message; no repeat observed
after the current reload. Diagnose if it recurs; do not claim its cause fixed.

## Reference-loop refinement (2026-09-27)

Player supplied `S:/Stargate/SG_Puddle.png` and `PuddleLoop.mp4`: white caustics/bright
centre, dark blue rim and morphing water rather than rotating layers. The video is280frames
at24fps (11.6667s). Extract140frames at12fps, retaining its speed, into two2688x3840 atlases
(384px tiles, about20MB PNG download /79MiB raw RGBA before mipmaps). Native immutable
97-vertex disk meshes select UV tiles; both atlas models are present from initial attachment.
No repeated texture upload, dynamic mesh rebuild, custom shader or per-player timer.
Reference still supplies the opening dome too. Both sides show the same water.

Closing now takes600ms:150ms at unchanged radius while whitening, then450ms shrinking while
white. Shared immutable alpha materials and per-gate overlays avoid cross-gate color changes.
Offline/new dial still cancels immediately. Gate texturing and chevron timing remain deferred.

- [x] Inspect PNG/video and verify MeshAsset/MaterialAsset API.
- [x] Implement extraction with source/output hashes, bounded playback and closing policy.
- [x] Build/tests, native activation on both test servers and placement preservation.
- [x] Reference water and final irregular shutdown visually accepted.

Risks: first-loop native mesh loading, texture filtering/illumination and close-up384px detail
need player inspection; Java tests cannot validate those. Source media is user-provided;
no redistribution license inferred. Keep that provenance for a later public release review.
Rollback: restore previous JAR; new reference assets are inert for the previous implementation.

Reference refinement validation:41 tests/package, entrypoint check, diff check and exact
FFmpeg regeneration pass. Backup `/docker/apps/stargate-puddle-backup.8rmeb9qk/`.
Both class hashes `329ff40ff60bb6fa31c442fa24fd5aa3cc688834ce75235f006bb0491b1f6fae`,
all asset hashes match, SQLite integrity and all five placement tables preserved. Development
reload/network-ready20:06:41UTC; Demo20:06:46UTC. Client received both initial atlas mesh/material
assets (each disk4305bytes), without errors in the checked segment. Full animated-loop appearance
is unverified until player test. No server restart, relay/production change or publication.

## Irregular shutdown refinement

Player accepted the reference water as excellent; requested softer irregular outside-in
transparency instead of shrinking a circle. Keep accepted open-water assets unchanged.
New12-frame RGBA atlas at20fps bakes moving reference water, coherent spatial alpha erosion
and a sinusoidal white blend peaking at80% at300ms, decreasing afterwards. Total600ms.
Mesh radius stays fixed, including closure during growth. No separate white overlays remain;
thus the white effect cannot survive outside the dissolving water. Existing interruption and
travel authority remain intact. Closing uses its own short reference segment; a texture-phase
change at transition and native transparency/filtering are visual acceptance items.

41 tests/package, entrypoint/diff check and deterministic asset regeneration pass. Extracted
alpha validated: first disk opaque, coverage decreases monotonically, final frame transparent.
Backup `/docker/apps/stargate-dissolve-backup.t040_hhk/`; rollback previous JAR (new assets inert).
Player test pending: outside-in irregular wisps/transparency, restrained white peak at300ms,
no square borders, both sides, subsequent opening/rp and travel unchanged.

Deployment verified: Development reload20:19:31UTC/ready20:19:32; Demo reload/ready20:19:29. Both class hashes ecdcd8e5b17aa0de4327a546a2f4b52d5128253bb64800faece382e8ff3deefa. Asset hashes and five placement tables match, SQLite integrity OK. Stop for player acceptance.

## Kawoosh hold and rear return vortex

Player accepted the irregular shutdown. Current request: forward surge35% farther,400ms
hold at full extension, then a brief smaller vortex behind the gate before settling.
Front peak3.78 source metres (previous2.8), rise180–915ms, hold915–1315ms,
return1315–2050ms. Rear +Z vortex1850–2650ms, peak0.9m, radius at most1.3m.
Twisted UVs and an off-axis tapered mesh rotate270degrees over the short tail; existing
reference-water material. Pure OPEN-age policy, no callback/timer per gate, unknown/late
age settled. Both protrusions cancel on closure/offline; accepted dissolve unchanged.

- [x] Implementation and deterministic nondegenerate rear OBJ generation.
- [x] 43 tests/package; files and placement preservation verified on both test servers. Development reload/ready20:43:46–47UTC; Demo20:43:43.
- [x] Extended Kawoosh, hold and rear vortex visually accepted.
Risks: native rear silhouette/motion and overlap need player acceptance. Cosmetic only.
Rollback prior JAR; added rear mesh inert with the previous code. No schema or relay change.

## Player acceptance — 2026-09-27

User: "Sieht gut aus so, würde ich erstmal so abnehmen alles!"
Current water, irregular shutdown, extended Kawoosh/400ms hold and rear vortex are visually
accepted. Phase5E.1 is complete for this iteration. This general visual acceptance does not
claim separately measured timing or a newly itemized travel/rp regression run.
Deferred final polish remains: original gate textures/material details, Chevron order
(V inward -> light on -> V home), occasional Chevron stutter. No new runtime changes,
release or production deployment at this checkpoint.
