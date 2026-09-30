# Gate materials — native visual milestone

Status: visual milestone accepted by player on 2026-09-28; combined regression remains pending.

## Objective and scope

Replace neutral solid color with the author's supplied mottled surface reference on the
accepted model. Body, rotating ring and chevron housings use shared texture plus separate
metallic/smoothness values; accepted light strips, water, animations and collision unchanged.

Original `SG_MW_Texture_Reference.zip` contains Color_and_Metal, Roughness and Bump JPGs.
The author describes procedural layered gray material in SOURCE-NOTICE.txt. Optimized OBJ
models have no UVs. Generate dominant-face-axis box projection at3.6 source metres per
original4m reference tile (author's90% scale), retaining exact vertex coordinates, face
indices/order and pivots. Added UV seams must not alter physical placement or collision.
New textured derivatives preserve old assets for rollback. Original4096² color JPG copied
byte-for-byte; no new artwork or inferred packed material channels.

Public MaterialAsset API supports albedo/normal textures and scalar metallic/smoothness,
not separate roughness/metalness texture maps. Current milestone therefore uses original
color and scalar roughness approximation, not a full LightWave material reconstruction.
Bump is a height map, not a tangent normal map; it is not incorrectly assigned as normal.
Fine normal/bump conversion remains optional subsequent refinement after native appearance.

## Checklist

- [x] Inspect source notices, reference images, existing UVs and local material API.
- [x] Deterministic UV generation and original texture extraction; unchanged geometry assertions.
- [x] 44 tests/package, entrypoint/diff checks, asset hash/reproduction checks and scoped two-server deployment; placement tables unchanged.
- [x] Player accepts current gate appearance, surface brightness/mottling and glyph contrast (2026-09-28).
- [ ] Combined final regression: multiple viewing angles/distances, moving ring/V texture attachment,
  lights/water/collision/travel/rp; general visual acceptance is not an itemized regression result.

## Risks / rollback

Original reference is quite dark; native daylight/reflections determine usable brightness.
Box projection can show seams at axis changes; assess at normal player distance. Original
4096² JPG adds64MiB raw RGBA before mipmaps, ~2.2MB transfer. Import topology/vertex splits
change through UVs, but triangles/geometry remain identical. Existing intermittent native
OBJ importer exceptions remain an open issue; don't infer their repair from asset changes.
Restore prior JAR to roll back; new derivative assets remain inert. No database/relay change.

Asset budget: UV indices expand the optional combined admin preview from5.9MB to13.0MB
(textured body10.0MB, ring1.76MB). This exceeds the earlier10MiB untextured-preview target;
accepted triangle/vertex positions remain identical. Keep separate original files for rollback.
The increase is explicitly accepted here for texture coordinates, not extra geometry. Existing
shared parts/proximity loading remain bounded; native load/performance must be inspected.

Activation confirmed: Development reload/network-ready21:26:11UTC; Demo21:26:07UTC.
Client received all three authored-surface materials, loaded eight OBJ models successfully;
no new Exception/Could-not-load in the checked44853-byte segment. This observation does not
prove the earlier intermittent importer problem fixed. Native material appearance pending.

## Shade-readability correction

Player requested correction after native screenshot showed almost-black front despite daylight
from the left. Screenshot baseline local.res/stargate-assets/material-dark-before.png.
New Imagegen derivative `gate-shade-readable-color.png` lifts the reference to neutral gray;
original image preserved. This is an AI-edited derivative, not an exact numeric exposure
transform; provenance/dimensions/hash in gate-shade-readable-report.json (1254² output).
Metallic/smoothness now body.08/.18, ring.16/.26, housings.12/.22. No emission/artificial
lighting; existing scene lighting still determines shade. Geometry/UV/collision unchanged.
44 tests/package and entrypoint/diff checks pass. Backup
`/docker/apps/stargate-shade-backup.28v150_r/`; rollback prior JAR. Native comparison pending.

Shade correction deployed on both test servers: class hash
c485c672c103f684da69e3220691875fa9565eb56f29367143b309996578443f. All assets match,
placement rows unchanged and SQLite integrity OK. Development reload/ready21:34:22UTC,
Demo21:34:19UTC. Native screenshot material-shade-after.png shows the actual placed gate
on the RIGHT with visible gray mottling/relief; camera moved and a separate building preview
is on the left, so this is not an exact same-view/light A/B comparison. Native material
readability is improved visually, player acceptance pending.
Client log77373bytes: eight successful OBJ loads and one ArgumentOutOfRangeException at
23:34:26local. Intermittent importer issue remains open; no claim of a clean native run.

## Subtle surface refinement

Player accepts shade brightness but rejects strong mottling, requests only minimal darkening.
Imagegen edit `gate-subtle-color.png` reduces large blotches while retaining fine grain.
Measured mean RGB previous94.95/new108.75; stddev14.07/new9.47. Runtime tint.85 brings nominal
mean to92.44 (~2.6% below previous); actual lit appearance needs native comparison, not inferred
from pixel arithmetic. Metallic/smoothness, UVs, model and collision unchanged.
Provenance/metrics/hash in gate-subtle-report.json.44 tests/package/entrypoint/diff checks pass.
Backup `/docker/apps/stargate-subtle-backup.vlseene7/`; previous JAR rolls back without asset changes.
Build `/tmp/stargate-subtle-build.log`; verifier `/tmp/stargate-subtle-verify.py`.

Subtle refinement active: Development reload/ready21:47:51UTC, Demo21:47:47UTC.
Both class hashes d9e58a24a9eb8c2d24dd244c14f86598868ebae6a948c9882c2adf4b3a66f0fd;
all asset hashes match and five placement tables unchanged, SQLite integrity OK.
Client51270-byte segment: eight successful OBJ imports, one native ArgumentOutOfRangeException
at23:47:54local. Known importer issue remains open. Screenshot had inventory open and gate
outside view, so no native visual comparison could be made. Stop for player material test.

## Authored glyph contrast

Player screenshot with inventory closed confirms a readable subtle frame, but almost invisible
symbols. Ray-depth checks of all 2625 source glyph triangle centroids against the accepted
body/ring find no body occlusion; simplified ring differs by up to 9.1mm behind source faces.
`build-ring-glyphs.py` preserves all 682 original glyph-face polygons (2625 triangles), with
robust concave triangulation and the accepted floor/pivot transform. An additional 1mm -Z
surface offset avoids coplanar flicker. Separate matte neutral-gray material (.56/.57/.58,
metallic .08, smoothness .12), child of rotating ring. Other surfaces/collision unchanged.
Only animated/persisted models use this layer; the combined static preview is unchanged.
Builder replay exact; Maven44 tests/package, entrypoint and diff checks pass. Native importer
ArgumentOutOfRangeException seen before this patch remains an open, separate risk.
Backup `/docker/apps/stargate-glyph-backup.iprz_d_t/`; previous JAR rolls back this addition.
Deployment/native validation pending; build /tmp/stargate-glyph-build.log.

Glyph contrast activated: Development reload/ready21:58:25UTC, Demo21:58:22UTC.
Class SHA2569ebcc8543f615ffda2c2823d3afa68c83fb76028ec5265f55f22b203fd74a483.
All asset hashes match; five placement tables unchanged; SQLite integrity OK.
Native screenshot local.res/stargate-assets/material-glyph-contrast.png shows clearly readable
matte light-gray symbols on the rotated inner ring. View moved closer/low angle, not an exact
A/B comparison. Player acceptance, rotation/flicker at other angles still pending.
Client185308 new bytes: nine successful OBJ imports, one ArgumentOutOfRangeException at
23:58:29local; error remains open and is not assigned to a specific asset. No importer fix claimed.

## Player acceptance — gate materials and glyphs (2026-09-28)

User: "sieht gut aus, ist abgenommen".
Current surface appearance and glyph contrast are accepted. This completes the visual
material milestone for this iteration. No new runtime changes at this checkpoint.
The intermittent native OBJ importer exception remains unresolved; general visual acceptance
does not establish a separate multi-angle/flicker or full travel/reload regression result.
Next agreed milestone: actual DHD model, followed by combined final acceptance.
