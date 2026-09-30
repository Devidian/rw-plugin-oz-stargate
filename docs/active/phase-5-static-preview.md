# Phase 5D.1: optimized static gate preview

Status: implemented and packaged; Development and Demo activated; Demo recovered through the user-executed rp command. Native preview acceptance pending. Command-access milestone accepted by the player on 2026-09-27.

## Scope

First prove geometry, scale, orientation and native import before persistent visual placement or animation. Admin-only /sg previewgate creates a temporary static Milky Way gate at the admin's current feet and facing direction; /sg clearpreview removes it. Visible only to that admin, one preview per player, auto-removal after two minutes, cleanup on disconnect/unload. No colliders, registration or travel triggers are attached. Existing gates, arrival points, DHDs and passage zones are not moved. Persistent placement/alignment follows visual acceptance.

## Implementation / validation

- [x] Reproducibly reduce the provided CC0 OBJ meshes into a candidate <=250k triangles and <=10MiB; retain author notice and source hashes.
- [x] Preserve separate source-part outputs for later animation; compose nine chevrons into a static preview.
- [x] Inspect offline geometry, winding, finite bounds, open centre and model size before packaging.
- [x] Add a focused preview service and localized admin commands/help; sole Listener keeps delegation only.
- [x] Package, API/entrypoint checks, bounded assets/cleanup review.
- [x] Back up artifacts, exclude active transfers, deploy only both test servers, verify reload/network readiness.
- [ ] Stop for native visual acceptance: front/back, glyphs/chevrons, scale, facing, ground contact, removal and unchanged gameplay.

## Risks / limits

Strong reduction may remove surface detail; the initial material is a neutral preview material rather than baked final PBR textures. Client import and appearance cannot be proven by server startup. No performance benchmark or animation claim before native preview. Asset tooling is build-time only in a temporary environment; no runtime dependency is added. Rollback JAR/i18n while leaving model files unused; no database migration.

Offline validation rejected both the first aggressive/clustering reduction (damaged silhouette) and direct MeshLab OBJ loading (concave polygons incorrectly spanned the opening). Final pipeline uses explicit concave-polygon triangulation before boundary/normal-preserving reduction. The initial 100k target is relaxed to 250k for shape quality; payload remains capped at 10MiB. Native performance is still pending.

Selected asset: 156,395 triangles, 94,025 vertices, 5,903,813 bytes. Body target 120k without boundary locking, other parts retain boundary constraints; all use normal preservation and existing vertex positions. Correct triangulation runs before reduction. Topology-preserving and strict-boundary variants were evaluated; the selected variant passed the opening check and offline visual review with a cleaner contour. Degenerate faces after decimal export are removed. This supersedes rejected candidate metrics.

Verification: independent asset checks cover digest, object count, finite coordinates, indices, nonzero triangle area, bounds/ground origin and nine points in the open passage. Packaged ZIP contains matching model/report/source notice. Maven package and ten existing tests plus entrypoint checks pass. POM asset copying now includes nested model directories. A standalone orientation probe could not run outside the native API runtime; orientation remains a player-test item.

Deployment note: a transient directory-access error stopped the existing Tools file watcher on demo during first model-directory upload. Final files and permissions are correct; the user executed rp because the container has no console stdin/RCON channel. Demo plugin enable, network readiness and restarted Tools watcher verified at 2026-09-26 23:19:32 UTC; Development activation was verified at 23:14:29 UTC. Future first-time asset uploads should pre-create readable directories. No game-server restart or Tools modification performed.

### First native preview correction (2026-09-27)

Player reported a brief import stall but no visible gate. Local Windows client Player.log confirmed successful OBJ loading followed by `Cannot combine mesh that does not allow access` for all twenty parts. Added `KeepMeshesReadable` alongside `MergeToSingleObject` so the client can combine imported meshes. Geometry/material/placement are unchanged. Package, ten tests and entrypoint check passed; native visibility retest pending. Previous test-server JARs: `/docker/apps/stargate-preview-readable-backup/{development,demo}.jar`.

Fix activation verified: Development `RELOADED ALL PLUGINS` and network-ready at 2026-09-26 23:29:19 UTC; Demo both at 23:29:17 UTC. Waiting for player visibility retest.

Player feedback (2026-09-27) after readable-mesh fix: gate is visible and looks good while standing in front of it. Native import/visibility and initial front appearance confirmed. Full orientation, back/detail inspection, cleanup/expiry and performance acceptance remain pending; initial short loading stall was reported as tolerable.

### Native scale correction (2026-09-27)

Initial screenshot (subsequently replaced by a new capture) showed the gate only about 1.5 times the adjacent door height. Player confirmed an unscaled standard door and two blocks per metre. The OBJ has metre dimensions but the preview had used them directly as world units. Added a uniform Model transform scale of 2 (12.28626 world units high, approximately 6.14 metres). Source geometry and feet pivot remain unchanged. Native scale acceptance pending.

Scale correction validation: package, ten tests and entrypoint check passed. Both test servers automatically reloaded and rejoined SGN-DEV-250926: Development 2026-09-26 23:45:51 UTC, Demo 23:45:49 UTC. Previous JARs retained in `/docker/apps/stargate-preview-scale-backup/`. Await player scale acceptance.

Player scale feedback (2026-09-27): "deutlich besser". Follow-up native screenshot saved as `local.res/stargate-assets/native-scale-corrected.png`; front ring, open passage, glyphs and chevrons visible with substantially improved proportions against the standard door. Scale correction accepted visually; screenshot alone does not establish exact physical dimensions or remaining cleanup/orientation checks.
