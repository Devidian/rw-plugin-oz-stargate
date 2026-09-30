# Phase 6 asset registration audit (2026-09-30)

## Objective

Check the reported hundreds of duplicate OBJ requests and client freeze before
the next Stargate release. Preserve the accepted gate and DHD visuals.

## Evidence

- The Development container log contains four complete plugin-load segments.
  Each segment has 33 Stargate `FROM FILE` asset registrations and 33 unique
  Stargate file paths. The count does not grow with the four current gates.
- Per segment the whole server logs about 28 model, 152 mesh, and 1546 texture
  registrations. The 152 meshes correspond to the shared procedural wormhole
  frame arrays (140 loop + 12 closing); they are created once per Stargate
  runtime, not once per gate. The large texture total spans all plugins.
- `GateModelAssets` caches each `ModelAsset` by part name and retains one
  `ModelImportSettings` per part. `DhdModelAssets` caches each DHD model path.
  The lifecycle disposes these resources at plugin shutdown/reload.
- The server log does not include individual client `REQUEST ASSET` lines.
  The developer's client-side request count and freeze are therefore not
  directly measurable from the available server log.

## Decision / remaining risk

No additional per-gate OBJ loading was found, so changing asset ownership now
would add risk without an evidenced reduction. Native OBJ importer stalls and
occasional Chevron animation stutter remain release notes/known limitations.
If the load pause becomes a release blocker, capture client asset IDs and
paths for one clean login and one plugin reload, compare repeated IDs versus
distinct settings IDs, then optimize the measured duplicate source. Avoid
replacing the procedural mesh arrays without a separate visual regression.
