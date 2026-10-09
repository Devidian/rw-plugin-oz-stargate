# OZ Stargate plan

2026-10-08: local faction sharing, personal address names, server-scoped cache migration and DHD hover fixes are implemented and accepted on Development. The Development plugin was uploaded after SQLite and relay backups; build and automated tests pass. Public 0.6.0 release preparation is under way; production deployment remains separate.

- [ ] Validate the next-300926 change on Development with a controlled player check.

The implementation plan is maintained at `../docs/active/stargate-network-plan.md` in the root workspace. This repository owns the Rising World plugin, its settings, UI, local persistence, and network client. The standalone relay lives in `../rw-stargate-network`.

Phase 1 inventory custody, phase 2 gate discovery/dialing, and phase 3A observations/game-mode restriction passed Development player tests. Phase 3B decline, outbound travel, return travel, and restoration of the prior destination inventory are also accepted. Exact arrival orientation remains unconfirmed. Controlled source restart during the confirmation dialog, restoration and repeat login without duplicates are now accepted. Phase 4 opens gatelist/dial/warp to players while retaining admin-only management and debug commands; non-admin runtime acceptance passed for travel, cancellation restoration and all restricted commands. Prepare the originally requested 0.1.0 release; the user supplied Devidian GitHub repositories for plugin and relay. A forced target game-process crash after claim remains unverified. See RUNTIME_TESTING.md and docs/active/phase-3-transfer-contract.md.

Both 0.1.0 releases are complete. Active work: phase 5A seven-chevron dialing and incoming priority; validate on the two designated test servers and wait for player acceptance before DHD/UI.

Phase 5A accepted by the player. Active milestone 5B: placed-object DHD bindings and target/status UI; see docs/active/phase-5-dhd.md.

Phase 5B including corrected DHD layout is accepted. Active milestone 5C: persisted gate passage zones and guarded movement-triggered travel; see docs/active/phase-5-horizon.md. Implementation/package and ten tests pass; native player acceptance completed on 2026-09-27, including the relay retry correction. Travel chat commands are now restricted to admin debug use; role-specific help is implemented. See docs/active/phase-5-command-access.md for the pending native acceptance. Asset assessment and next visual milestone: docs/active/phase-5-gate-assets.md.

Command-access acceptance passed on 2026-09-27. Active milestone 5D.1: optimized static Milky Way preview for admins, before persistent visual placement or animation; see docs/active/phase-5-static-preview.md. Asset and package checks pass; native preview acceptance pending.

Preview visibility and corrected scale accepted on 2026-09-27; user requested continuation. Active milestone 5D.2: persistent model placement and proximity visibility, followed separately by explicit arrival/passage alignment (5D.3). See docs/active/phase-5-persistent-visuals.md. Earlier preview cleanup/orientation checks are not claimed as completed.

2026-09-27 acceptance: user confirmed manual rp recovery ("ja geht! abgehakt") after previously accepting the other tested persistent-model behavior. Phase 5D.2 is complete. Next planned work: explicit model/arrival/passage alignment (5D.3). No further runtime changes at this acceptance checkpoint.

Active 5D.3: explicit model/passage/arrival alignment; see docs/active/phase-5-alignment.md. No existing placement changes until an admin runs aligngate.

2026-09-27 acceptance: player confirmed "läuft!" for the phase 5D.3 alignment test stand. Model/passage/arrival alignment is accepted; no additional detailed measurements are claimed. Next planned work: visual gate states and relay-driven chevron/ring animation, then wormhole effects. Stop at this acceptance checkpoint.

- 2026-09-27: phase 5D.4 visual dialing implemented; 27 tests, package, entrypoint and
  lossless articulated-asset checks pass. Test-server activation/native acceptance tracked
  in `docs/active/phase-5-animation.md`. No protocol or persistence changes.

- 2026-09-27: phase 5D.5 slows the ring and separates moving chevron blocks. Companion
  relay defaults to 3500ms per symbol; no persistence/protocol changes. See
  `docs/active/phase-5-chevron-motion.md`; native acceptance is a separate checkpoint.

- 2026-09-27: 5D.5 rejected visuals corrected with timed poses and authored V/stripe groups; 31 tests pass. Native retest pending; see `docs/active/phase-5-v-chevron-correction.md`.

- 2026-09-27: corrected ring/V animation and lighting accepted as complete. Occasional chevron stutter remains known polish. Next: wormhole/event-horizon visual effects.

- 2026-09-27: phase 5E.1 adds cosmetic water surface/opening surge using shared procedural
  assets and authoritative OPEN timing. No travel/protocol/schema changes. Native test pending;
  see `docs/active/phase-5-wormhole.md`.

## Player acceptance — 2026-09-27

User: "Sieht gut aus so, würde ich erstmal so abnehmen alles!"
Current water, irregular shutdown, extended Kawoosh/400ms hold and rear vortex are visually
accepted. Phase5E.1 is complete for this iteration. This general visual acceptance does not
claim separately measured timing or a newly itemized travel/rp regression run.
Deferred final polish remains: original gate textures/material details, Chevron order
(V inward -> light on -> V home), occasional Chevron stutter. No new runtime changes,
release or production deployment at this checkpoint.

## Player acceptance — collision correction

2026-09-27: user confirms "ring ist jetzt kollidierend mitte frei zugänglich, super!"
Together with prior acceptance of the Chevron sequence, the Chevron-order/frame-collision
slice is accepted. Physical frame blocking and clear centre are confirmed. This feedback
does not establish a fresh travel/rp regression result or resolution of occasional animation
stutter/native OBJ import errors; retain those as open final-validation issues.
Next approved milestone: gate materials/textures, then actual DHD model and combined tests.
No runtime changes at this acceptance checkpoint.

## Current checkpoint — phase 5 visuals (2026-09-28)

Supplied DHD placement, collision and interaction; gate materials, Chevron
lights, water and broad front spotlight are accepted on the two test servers.
The front water still darkens before the inner rim, which the player accepts
for this pass. Next: combined player regression of DHD state/reload and
inventory-safe two-server travel. Use root
`docs/handoff/stargate-phase5-combined-regression.md` and stop after each
player-test group. Intermittent native OBJ importer errors and occasional
Chevron stutter remain recorded limits; no release/Production deployment is
part of this checkpoint.
