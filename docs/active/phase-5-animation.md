# Phase 5D.4 — visual state and dialing animation

Status: functional milestone accepted by the player; visual refinement remains planned.

## Objective and constraints

Animate the accepted persistent Milky Way model using authoritative network state.
Preserve placement, alignment, travel, DHD updates and manual reload recovery.
No protocol/schema change. Preview remains static. No event horizon effect yet.

## Implementation / validation

- [x] Lossless split of accepted body, rotating ring and nine paired chevrons; same CC0 attribution.
- [x] State policy: idle/offline dark; outgoing progressive seven locks; incoming/open seven active.
- [x] Cosmetic ring movement per confirmed step; interruption immediately stops/resets outgoing animation.
- [x] Separate visual observer; DHD observer retained; client presence recovery retains hierarchy.
- [x] Pure state regression tests, asset geometry equivalence, package and entrypoint check.
- [x] Both test servers: no active transfers, backup, reload/readiness, preserved placement rows.
- [x] Player reports all tested functionality working. Separate incoming-priority/failure scenarios are not individually confirmed.

Ring offsets are cosmetic, not a glyph encoding of the opaque relay addresses. Seven active
chevrons exclude the bottom pair and finish at the top. Amber materials indicate locks;
this API has no dedicated emission setter. Native tween messages occur only on state/step
changes, not every frame or per viewer. Incoming status is shown when the relay reports it;
no synthetic incoming sequence delays the real connection.

Rollback: restore previous JAR; static preview asset remains available. Additional OBJ files
are inert with the old JAR. No settings/database migration. Native hierarchy import/animation
and visual legibility remain player-test risks.

## Technical completion

27 tests and package/entrypoint/asset checks pass. Both test servers reloaded and network-ready;
placement/alignment rows retained exactly. Initial simultaneous imports with shared settings emitted
native TriLib errors. Each part now owns import settings; verification in the connected client recorded
11 successful OBJ loads and no new errors. Full native animation/reload acceptance remains pending.

## Player feedback — 2026-09-27

Player: all functionality works. Dialing feels too fast, particularly ring rotation, but is
acceptable for this milestone. Mechanical chevron movement is still missing, as scoped above.

Next visual refinement before wormhole effects:
- [ ] Slow ring rotation and tune overall dialing cadence while retaining authoritative relay timing.
- [ ] Split movable chevron parts and animate engagement/release; reset on interruption/closure.
- [ ] Validate and request a separate player acceptance checkpoint.

No runtime changes at this acceptance checkpoint.
