# Static gate asset build

## Milky Way AssetBundle candidate (Unity 6000.3.22f1)

This optional renderer combines a high-detail FBX body in LOD0, the previously
accepted textured body OBJ in LOD1, and moving ring/Chevron geometry from the
supplied `SG_MilkyWay.fbx` (SHA256
`277b775a102f19d17a5a8383b065a04d5472e43dd456371667ef5230cabb9e13`).
Keep the original FBX outside the plugin and outside Unity's `Assets/AssetBundles`
folder. The FBX importer already applies its authored 0.9 scale; the offline LOD
tool applies that factor once to the moving FBX mesh coordinates. See
`SOURCE-FBX-NOTICE.txt` for the CC0 source and authored animation/material notes.

Create a Unity **6000.3.22f1** High Definition 3D project with Windows, Linux and
Mac build support. Import the game's `Data/SDK/PluginSDK.unitypackage` and put
`scripts/assets/unity/*.cs` in `Assets/Editor`. Copy the FBX to
`Assets/StargateSource/SG_MilkyWay.fbx`. In batch mode, run
`StargateFbxExporter.Export` with `-exportPath <temporary-directory>`. It writes
four unique mesh groups (`body`, `ring`, `chevron-v`, `chevron-block`) with original
material boundaries; only one of nine identical Chevron meshes is exported.

With the pinned Python requirements, run
`python scripts/assets/build-fbx-lods.py <source.fbx> <export-directory> <lod-directory>`.
Copy its `LOD0`–`LOD3` folders and `manifest.json`, plus the existing
`gate-subtle-color.png`, into `Assets/AssetBundles/StargateMilkyway`. Run
`StargatePrefabBuilder.Build`, then the official
`AssetBundleBuilder.BuildAssetBundlesMenuItem`. The output is
`Build/stargatemilkyway.bundle`; copy it to
`src/assets/models/milkyway/stargatemilkyway.bundle` and package the plugin.
Do not leave empty directories or their `.meta` files under
`Assets/AssetBundles`: Unity recreates directories from metadata, and the SDK
builder then reports a missing bundle output.

The body is `textured-articulated-body.obj` (SHA256
`7b8636c2348cabafebf56c5f64707d02977334f8ca6f0b1d0866e7735e7de0c3`).
The first FBX-only bundle split the body at material seams and left visible
holes. The current experiment reduces the complete source body to about 220k
triangles for LOD0 while preserving boundaries and the passage opening. LOD1
uses the previously accepted body and all other former LOD0 meshes exactly.
LOD0 omits the body's duplicate rear Chevron housing and light groups because
the prefab supplies animated rear V meshes in the same positions. Removing
additional fine inner-ring groups caused invalid passage-spanning triangles
and was rejected by the geometry check.
LOD0 and LOD1 ring faces retain their original FBX material groups so glyphs do not
inherit neighboring stone or groove materials. Distant ring LODs simplify the
joined geometry and assign materials after reduction to avoid seam cracks.
The prefab contains 48 solid box colliders around the annulus, outside the LOD
group; the plugin sets its root to `Layer.DEFAULT`.
The source FBX has front-only Chevron V geometry. The prefab mirrors each V
mesh with reversed winding for the rear Chevron housings and keeps the rear
strip material and motion synchronized with the front. A broad rear annular
backing was rejected after a player check because it was visible from the
passage and did not close the remaining frame slits.
The four target budgets are about 375k, 275k, 65k and 22k triangles per gate,
including nine Chevron instances. The build manifest records actual totals.
`gate.assetMode` defaults to `obj`; set it to `bundle` in the world settings for
a Development comparison, then reload. A failed bundle load logs an error and
uses the OBJ renderer. The DHD and existing wormhole/collision assets remain
separate. Verify native appearance, animation, load time, transfer, frame time
and memory before making the bundle the default renderer.
The prefab retains the authored material groups, but the runtime currently
assigns the game's native HDRP materials to each mesh: the bundle's baked
materials appeared magenta in the client, and excluding it from the GPU
Resident Drawer alone did not resolve that result.

Offline tooling only; normal Maven builds use checked-in generated assets and do not need Python packages or the original archive.

Use Python 3.13 in an isolated venv, install the pinned requirements, then:

```
python scripts/assets/build-static-gate.py /path/to/SG_MW_OBJ.zip src/assets/models/milkyway --cache /tmp/stargate-selected-parts
```

The script verifies the known source SHA256, triangulates concave OBJ polygons with earcut (and vectorized quad splitting) before MeshLab, removes duplicate/degenerate geometry and reduces each part while preserving normal constraints and using existing vertex positions. Boundary preservation is enabled on the ring/chevrons, but disabled on the body to avoid sacrificing the smooth frame while retaining tiny border details, duplicates both chevron pieces nine times at 40-degree intervals, applies the author's 0.9 scale, and shifts the bottom to Y=0. The resulting OBJ uses source -Z as its front. It retains neither source UVs nor procedural LightWave materials; this preview uses generated normals and a neutral runtime material.

Per-part reduction caches preserve independent meshes for later animation; use a fresh cache when changing tooling or reduction settings. The production preview is the combined OBJ; no multi-million-polygon original is copied into the plugin. SOURCE-NOTICE.txt and build-report.json accompany it. The report records actual triangles, bounds, size and source/output hashes. Build fails above 250k triangles or 10MiB.

Tooling: [PyMeshLab](https://pymeshlab.readthedocs.io/en/latest/filter_list.html#meshing-decimation-quadric-edge-collapse) (OBJ import and constrained quadric reduction). These tools are not Rising World plugin dependencies. The earlier fast-simplification/clustering candidate was rejected after visual inspection and is not the shipped asset.

Run `python scripts/assets/verify-static-gate.py` after generation to independently check output hashes, indices, nondegenerate triangles, object count, size, ground origin and scale bounds. Native visual quality still requires player acceptance.

The initial 100k target was raised after offline visual inspection: aggressive reduction damaged silhouettes. Normal/boundary preservation takes priority for this single-model admin preview; native performance still needs testing. Direct MeshLab OBJ triangulation also filled the ring opening and was rejected. Correct polygon triangulation runs before MeshLab reduction; no rejected candidate is deployed.

Selected candidate: 156,395 triangles, 94,025 vertices, 5,903,813 bytes. The body target is 120k, inner ring 22k, and each of the two instanced chevron parts 800. The generator invokes the independent verifier; it checks nine sample points across the open passage in addition to structural/budget checks. Static offline shading is a geometry diagnostic, not a Rising World screenshot.

## Authored material reference / UV derivatives

`build-gate-materials.py /path/SG_MW_Texture_Reference.zip [--check]` adds dominant-axis UVs
to the accepted preview/body/ring/housings and extracts the original color JPG byte-for-byte.
Old mesh files remain untouched. Vertex positions, triangle order/count and source hashes are
verified; `gate-material-report.json` records derived assets. Reference tile4m maps to3.6
source metres after the original90% scale correction. Body/ring surfaces use original color
plus runtime scalar metallic/smoothness. Roughness/height maps are not misused as normal maps.
The UV-equipped admin preview is13.0MB (higher than the original10MiB preview limit); no extra
geometry is introduced. See phase-5-gate-materials for runtime limits and pending acceptance.

## Original DHD model

`/usr/bin/python3 scripts/assets/build-dhd-model.py [--check]` generates four standalone
OBJ parts (body, keys, markings and activation button) from repository-owned parametric
geometry. It uses no external model archive or Python packages. The report records hashes
and geometry counts; the build is deterministic. Runtime materials and a primitive box
collider live in `DhdModelAssets`. This is a compact first native visual milestone and
requires in-game shape/interactivity acceptance.

## User-supplied BlendSwap DHD conversion

Use the official portable Blender 3.3.15 offline, then run
`/usr/bin/python3 scripts/assets/rebuild-supplied-dhd.py /path/to/Stargate\ DHD.zip /path/to/blender [--check]`.
The wrapper verifies source hashes, calls `build-supplied-dhd.py`, copies the original
license notice verbatim, writes the source/change notice and verifies deterministic
assets in `src/assets/models/dhd`. Maven builds consume checked-in OBJ files and do not
require Blender or the original archive. Keep the original archive outside the plugin.
A CPU-rendered comparison candidate was inspected; native player acceptance is separate.
The derived body and accents have box-projected UVs for the existing gate stone texture.
Seven additional key and glyph OBJs each contain one complete source button and its glyph.
Unlit remainder OBJs hold the other buttons/glyphs without adding triangles to the live
model. The chosen buttons remain stable across reloads; one more lights per outgoing
chevron. The original combined key and symbol OBJs stay available for rollback.

## DHD AssetBundle comparison

For the optional DHD bundle, run `python scripts/assets/build-dhd-bundle-input.py
/path/to/Stargate\ DHD.zip /temporary/dhd-bundle-input` with Python 3.13. Its
`build-original-dhd-lod0.py` reader verifies the exact supplied Blender 2.71
archive and exports the unreduced control mesh with its authored base Mirror,
without subdivision. It discards seven degenerate triangles after coordinate
rounding, leaving 122,129 triangles for LOD0. LOD1 copies the current 43,863
triangle supplied OBJ groups. Both LODs partition the seven individually lit
keys and symbols. The original `.blend` is never copied into the plugin.

Copy the generated `LOD0`, `LOD1` and `manifest.json` plus the accepted
`gate-subtle-color.png` to `Assets/AssetBundles/StargateDhd` in the pinned
Unity 6000.3.22f1 HDRP project. Put `StargateDhdPrefabBuilder.cs` in
`Assets/Editor`, run `StargateDhdPrefabBuilder.Build`, then the SDK's
`AssetBundleBuilder.BuildAssetBundlesMenuItem`. The separate output
`Build/stargatedhd.bundle` belongs in `src/assets/models/dhd/`. In bundle
mode the plugin loads the prefab and uses native materials, while the existing
OBJ DHD remains the fallback. Compare both LODs, interaction, light nodes and
collision on a player client before choosing DHD bundled materials.

`build-lightwave-kawoosh.py` can make a temporary, Milky Way sized surge OBJ
from the user-supplied `KawooshMorph_20211124A.lwo` using the pinned PyMeshLab
environment. This is an offline comparison candidate and is not included in
the current plugin package. The source scene contains distinct start/end morphs;
the existing `WormholePose` remains the gameplay timing source.

## Wormhole AssetBundle comparison

Copy the accepted `puddle-loop-0.png`, `puddle-loop-1.png`,
`puddle-surge.png`, `puddle-dissolve.png`, `articulated-wormhole-surge.obj`
and `articulated-wormhole-rear-vortex.obj` into
`Assets/AssetBundles/StargateWormhole` in the pinned Unity project. Put
`StargateWormholePrefabBuilder.cs` in `Assets/Editor` and run its `Build`
method, then `AssetBundleBuilder.BuildAssetBundlesMenuItem`. The output is
`Build/stargatewormhole.bundle` and belongs in
`src/assets/models/milkyway/`. The prefab stores 140 accepted loop frames
for both faces, 12 closing frames, surge/rear meshes and two horizon lights.
The plugin keeps `WormholePose` as the timing source and overrides the
prefab's authored HDRP materials with its native water materials. If this
separate bundle cannot load, it uses the existing native mesh renderer.

This is a Development candidate. Unlike the current two mesh changes per
loop frame, switching prefab frame nodes needs four active-state changes;
measure network traffic and client frame time before accepting it. The
user-supplied film LightWave shape is not included in this bundle. Use the
[2026 reference notes](../../docs/reference/stargate-reference-information-2026.md)
for front, profile and rear comparisons, and compare the opening/closing
silhouette separately from the 12 fps water loop.

For a separate **Development-only Kawoosh comparison**, generate
`wormhole-surge-lightwave.obj` with `build-lightwave-kawoosh.py`, copy it into
`Assets/AssetBundles/StargateWormhole`, then run
`StargateWormholePrefabBuilder.BuildLightwaveComparison` and rebuild the
bundle. This replaces only the opening surge mesh; `WormholePose` still
animates its depth and timing, and the water loop is unchanged. The original
LightWave morph envelopes are not imported into Unity by this comparison.
Keep the classic bundle available for rollback and review the film demo's
distribution terms before any public release.

## Accessory Pack 1 platforms

Run `python scripts/assets/build-accessory-platforms.py
/mnt/s/Stargate/Stargate_2020_Accessory_Pack_1 /temporary/platform-input` with
the pinned PyMeshLab environment. The manifest records each source hash and
LOD/collider triangle count. The offline tool exports an OBJ geometry reference
for LOD0, a roughly 32% render LOD1, and a small collision mesh. Unity imports
the original FBX directly for the bundle's unreduced LOD0. The circular
platform retains its full 14,435-triangle collider because decimation altered
the central deck height.

Copy the eight generated LOD OBJ meshes to
`Assets/StargateSource/Platforms/Derived` and the four collision OBJs to
`Assets/AssetBundles/StargatePlatforms`. Copy the four supplied FBX files to
`Assets/StargateSource/Platforms` and the accepted `gate-subtle-color.png` as
`Assets/AssetBundles/StargatePlatforms/PlatformStone.png`. The original LOD0
OBJs are build references and must stay outside the bundle folder.
Put `StargatePlatformPrefabBuilder.cs` in `Assets/Editor`, run its `Build` method,
then `AssetBundleBuilder.BuildAssetBundlesMenuItem` in Unity 6000.3.22f1.
The output `Build/stargateplatforms.bundle` belongs in
`src/assets/models/milkyway/`. The bundle contains four prefabs, each with
unreduced geometry in LOD0 (the three FBX meshes and the complete circular
OBJ mesh), the OBJ-derived reduced LOD1, and a separate
non-convex mesh collider. The supplied FBX files contain material groups but no
image textures, and the two curved FBX meshes have no UV channel. The builder
generates planar UVs without changing triangle counts and assigns the existing
stone texture. Unity drops one 12-triangle circular stone on FBX import, so
the full 14,435-triangle source OBJ supplies that LOD0 instead. Each FBX
material group is a separate renderer so the plugin can assign a native
material to every surface; this avoids magenta secondary material slots.
The circular stone is tinted sandstone and the other platforms use darker
stone, inset and lamp variants based on the author's preview.

Gate placement uses the aimed ground point as the platform origin and puts
the gate on the central deck. The authored deck heights are 0.444 m
(circular), 1.333 m (both curved) and 0.44 m (Grace of God). Arrival heights
at the front platform surface are 1.10 m and 1.60 m for circular and Grace.
Test each
variant's collision, facing, terrain fit, arrival and LODs on a client before
considering the Development candidate accepted. Review the source pack's
distribution terms before public release.

The radial menu uses matching modern and classic PNG icons. The reviewed gate,
DHD and platform motif sources live in `assets/icon-sources/`; regenerate all
ten icon variants with `python3 scripts/generate-menu-icons.py` and ImageMagick.
Runtime loads the PNGs through the existing OZ icon manager.
