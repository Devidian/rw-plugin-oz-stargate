# Static gate asset build

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
