# Phase 5D.3: explicit travel alignment

Status: completed and accepted by the player on 2026-09-27. 5D.2 accepted.

## Goal / decisions

Admin `/sg aligngate <ID>` aligns an existing persistent model with a circular, rotated passage and a deliberately chosen arrival point. Stand on the intended walkable arrival position 2–8 world units (1–4m) in front of the ring; its projected body centre must lie within the opening. Arrival facing is outward from the authored front (-model forward). Admin chooses terrain/platform height; no terrain is modified or assumed safe from model coordinates alone.

- Source model bounds/pivot and accepted scale stay unchanged. Aperture centre is 6.15 world units above ring feet, conservative radius 4.4 units, half-depth 0.6. The trigger samples player body centre 1.8 units above feet. Legacy axis-aligned zones retain their existing feet-point behavior.
- New optional `stargate_aligned_horizons` table. Transaction updates arrival, removes the old legacy zone and inserts the oriented zone. No automatic migration of existing placement; no relay/custody/DHD changes. Existing behavior continues until the admin explicitly aligns.
- Gate must be network-ready and IDLE, with no active local transfer for that gate. Reject overlap conservatively using world bounding boxes. Rebaseline movement after edits so standing in a new zone cannot initiate travel.
- `/sg showhorizon <ID>` shows the rotated passage bounding box (not a wormhole effect). `/sg removehorizon <ID>` explicitly clears either geometry. Aligned model move/removal is locked until the horizon is removed, so model and travel cannot silently diverge. Existing arrival is retained until next alignment.
- Gate deletion cleans both geometries. Old plugin rollback cannot read aligned geometry and therefore has no passage for aligned gates (fail closed). Restore only affected arrival/zone rows from backup if reverting alignment; never overwrite current custody data.

## Checklist

- [x] Pure rotated aperture/arrival validation and conservative overlap bounds, tests for diagonal gates/front/back/floor limits.
- [x] Additive storage and atomic arrival/zone update; tests for reopen, failure rollback, missing gate/model, deletion and unchanged DHD.
- [x] Localized setup/lock commands and correctly rotated debug preview; lifecycle baselines.
- [x] Package and deployment on both test servers; preserve old rows, no automatic alignment.
- [x] Native acceptance checkpoint completed: user confirmed "läuft!" after the requested setup/travel/arrival/reload test. No additional individual observations are inferred beyond that overall acceptance.

## Risks / validation

Native coordinate/quaternion convention and passage height need player validation. Body-centre testing is intentionally conservative and does not implement continuous swept collision or physical gate collision. Admin must provide walkable ground/platform at arrival and through the ring. Existing debug warp remains admin-only. No animation or final material work in this milestone.

Validation: 23 local tests passed, including forced transaction rollback and float direction roundtrip. Pending gate requests and unexpired local dial windows also block edits before the next relay state update. Native acceptance pending.

Activation verified 2026-09-27 UTC: final Development reload/network-ready 00:44:55; Demo 00:44:54. Both class-content hashes match `d8e96c6bb59a7d03a1c9119134b1e83447c0eae378c698b57d425c9a559529de`. Both SQLite integrity checks pass; gate/DHD/legacy horizon/model rows exactly equal pre-deploy backup; new aligned tables are empty and cleanup triggers exist. First Demo table check ran before automatic reload and was repeated successfully after activation. Await player setup/acceptance.

Final acceptance (2026-09-27): player confirmed "läuft!". Phase 5D.3 complete. Next planned milestone: visual gate states and relay-driven chevron/ring animation, followed by wormhole effects. No further runtime changes at this checkpoint.
