# 5D.5 correction — V stroke, strip lights and timed ring motion

Status: complete — accepted by player; occasional animation stutter remains known visual polish.

## Findings and design

The API tween parameter is speed, not duration. Prior 2.8/0.65 values therefore did not
specify seconds. Replace speed-based tweens with bounded 20Hz poses derived from monotonic
time, using quintic easing (zero endpoint speed/acceleration). Only changed active poses
send mutations; one loop per service, shared per gate, not per viewer.

The original CC0 OBJ defines Chevron_A (V) and Chevron_Light (stripes). Previous code moved
ChevronBlock, the wrong part. Extract original material groups with existing offline
triangulation; retain accepted frame/ring and world transform. Five active mesh resources,
196,107 triangles per gate. Static block remains neutral. V carries its strip mesh and moves
radially inward then returns home; only confirmed locks change the strip material.

Relay default step 5000ms, 35 seconds nominal. Add optional `dialProgress.payload.stepMs`
(1..5000) so animation follows overrides. Plugin fallback for older relay: 3500ms.
Older plugins ignore the added field. No protocol version/schema/settings migration.
Within each step: ring 0..62%, pause until 68%, V inward 68..80%, pause until 82%,
V outward 82..94%, home until confirmed lock. Ring sweep 24 degrees, alternates direction.
Late viewers sample current phase; interruption clears V displacement and freezes ring.
No animation callback can later light or move a cancelled dial. The 60s request deadline
covers default sequence + up to 20s lookup/reply budgets with normal scheduling margin.

## Checklist

- [x] Material extraction/hash/shape checks and direct inspection of original V/strip groups.
- [x] Time-based poses and lifecycle; no whole-chevron material changes.
- [x] Timing/progress compatibility, acceleration/stroke/lock/interrupt regression tests.
- [x] Plugin package/entrypoint, relay tests and isolated transfer smoke.
- [x] Backup, both test servers and relay activation, preserved placement data.
- [x] Player explicitly accepts the visual milestone; occasional chevron stutter is non-blocking.

Rollback: previous plugin JAR and relay image/source; old mesh files retained. No database
restoration needed for visual rollback. Explicit pose updates trade a bounded low-volume
stream for deterministic timing; native visual smoothness remains a player-test gate.
API reference: https://javadoc.rising-world.net/latest/net/risingworld/api/worldelements/GameObject.html

## Activation evidence

31 plugin tests/package, entrypoint and asset checks pass. Relay unit/contract tests and
isolated transfer smoke pass. Both test servers reloaded/network-ready; placement rows
retained exactly. Five successful native mesh loads with no new import errors observed.
The visual sequence remains subject to player acceptance.

## Follow-up — player feedback

Player reports the motion looks much better. Requested refinement: brighter color on OPEN
and the missing inner light surface of the fixed outer chevron. Split original
Chevron_Block_Light from Chevron_Block_Arm; both light surfaces share dark/locked/open
materials. Geometry, animation cadence and relay unchanged. Native lighting retest pending.

Lighting follow-up activated on both test servers: 31 tests/package, entrypoint and authored
material extraction checks pass. Original inner fixed-chevron surface now lights with the V
strips; both brighten on OPEN. Class/assets match and placement data retained. Focused native
lighting acceptance remains pending.

## Player acceptance — 2026-09-27

Player explicitly considers this milestone complete. Ring/V motion, authored light surfaces
and OPEN brightening accepted. Chevron animation occasionally stutters; recorded as known,
non-blocking visual polish, not fixed or diagnosed at this checkpoint. No additional native
priority/failure scenario is inferred from this acceptance. No runtime changes here.
Next planned milestone: wormhole/event-horizon visual effects, with separate player testing.
