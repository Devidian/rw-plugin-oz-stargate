# Phase 5D.5 — slower dialing and mechanical chevrons

Status: technically validated and active on both test servers; player acceptance pending.

## Objective / ownership

Plugin: split fixed chevron bodies from movable blocks, animate inward engagement and
outward release, reduce ring angular speed. Relay: default step cadence 3.5 seconds
(previously 2), total nominal seven-step sequence 24.5 seconds (previously 14).
Existing DIAL_STEP_MS overrides remain authoritative; no protocol or schema change.

## Checklist

- [x] Lossless 20-part split with separate chevron blocks, geometry check.
- [x] Ring 60 degrees / 2.8 seconds instead of 120 / 1.6; blocks engage over 0.65 seconds.
- [x] Mechanical positions follow current state, including incoming priority/offline/reset/late viewer.
- [x] Relay default 3500ms; verify seven-step timing and existing priority/deadline tests.
- [x] Package, API/entrypoint checks, relay tests and isolated transfer smoke.
- [x] Test-server backup/deployment, idle transfer guard, reload/readiness, data preservation.
- [ ] Player acceptance before wormhole effects.

## Risks / rollback

The 60-second plugin dial deadline covers 24.5 seconds plus lookup/reply budgets (up to
20 seconds) with margin under normal scheduling. Existing relay step overrides stay supported.
Visual timing is cosmetic and cannot open a connection. No delayed animation callbacks:
state changes directly retarget native movement, avoiding stale locks after cancellation.
Old paired OBJ assets remain for rollback to the prior JAR. Restore prior relay image/config
and JAR if needed; databases/settings are not migrated. Native appearance still needs testing.

## Evidence

28 plugin tests/package, entrypoint and geometry checks pass. Relay yarn/Docker checks
and isolated transfer smoke pass. Both test servers reloaded/network-ready, relay healthy;
all assets/classes match and five placement tables remain unchanged. Native motion and
speed acceptance remain pending; no successful new native import is claimed from this run.

## Player rejection / correction

The player reported the ring still excessively fast, the wrong chevron part moving and
whole elements lighting. The API parameter was incorrectly treated as a duration, although
it is speed. Prior visual timing statements were therefore not native timing evidence.
Superseded by `phase-5-v-chevron-correction.md`: timed poses, original V/stripe material
groups, inward/return stroke before lock and optional relay step duration metadata.

2026-09-27: corrected implementation and lighting follow-up explicitly accepted. Milestone complete; occasional chevron stutter remains non-blocking polish. See `phase-5-v-chevron-correction.md`.
