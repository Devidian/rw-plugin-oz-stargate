# Gate asset assessment and next visual milestone

Status: optimized Milky Way preview deployed; native import, front appearance and corrected scale accepted on 2026-09-27. Persistent placement is the next milestone; formal performance measurement and final materials remain open.

## Provenance

Source archive set supplied by the user under /mnt/s/Stargate. Readmes identify David Gian-Cursio and explicitly dedicate the Milky Way, Pegasus and Movie models to CC0. Preserve those notices and credit the author with any derived model package. This records the supplied model license, not a separate franchise-rights assessment. Accessory pack and animation archive attribution needs to accompany their use; no standalone notice was found in their archive file lists. Local evidence (readmes and geometry counts) is retained under local.res/stargate-assets in the workspace.

| Archive | SHA256 |
| --- | --- |
| SG_MW_FBX.zip | 29277fb5271ec2b8541fd54291f16cf102957379fd4afef463696edc4546a6a2 |
| SG_MW_OBJ.zip | 3a976d4dfd234bc80024a11e3ade84710b4d917946decd9de6ec182bc7a5b641 |
| SG_Peg_FBX.zip | 46e4fcd1a04181ffd8aaa1ad6fc9504bddd01cfbb3b84fe0ea55e4124619452b |
| SG_Movie_FBX.zip | 50ad5bb6eacd1423ef95c98dd1ec7a65c04d27a6db6d7e33402031b9d5a2db8d |

The linked author showcase could not be retrieved with the web tool; licensing evidence here is the actual supplied readmes.

## Inventory and measured geometry

The collection includes FBX, OBJ/MTL, LightWave scenes/objects, texture-reference images, alternate inner rings, FBX animation templates and a LightWave Kawoosh demo. No Unity project or asset bundle was identified in the archive extensions. The accessory pack filenames describe bases/platforms. A standalone DHD console mesh was not identified by filename; DHD-named animation templates must be inspected before assuming they contain console geometry.

Milky Way OBJ counts, streamed from the archive without importing or modifying originals:

| Part | Vertices | Faces | Triangle estimate |
| --- | ---: | ---: | ---: |
| Main body | 2,223,647 | 1,957,719 | 4,293,636 |
| Earth/Giza inner ring | 39,211 | 30,239 | 74,635 |
| One lower chevron | 1,390 | 1,021 | 2,796 |
| One upper chevron block | 1,620 | 1,592 | 3,216 |

Triangle estimates sum n−2 for each polygon; they are not measured GPU triangles. Main OBJ alone is 463,625,657 bytes. Repeating both chevron parts nine times plus main/ring yields about 4.42 million triangles before optimization. This motivates mesh reduction and detail baking before a gameplay import; no frame-time benchmark has been performed.

The author specifies a 6.096m main-ring diameter and 90% scale for the OBJ model. Verify ring measurements visually rather than fitting the entire bounding box, which includes protrusions. The main OBJ bounds are approximately X ±3.417, Y −3.417..3.411 and Z ±0.264 in source units. Author-specified chevron translation: lower part 10.4cm down and 2.3cm inward; upper block 3.5cm up before scale adjustment. Texture-reference pictures do not establish ready-to-use UV/PBR maps; LightWave materials are described as procedural.

## Runtime path

[Official ModelAsset API](https://javadoc.rising-world.net/latest/net/risingworld/api/assets/ModelAsset.html) supports FBX and OBJ directly. Local PluginAPI 0.9.3.2 signatures also expose ModelAsset.loadFromFile, ModelImportSettings (including animation options), and PrefabAsset.loadFromFile. Thus Unity conversion is not automatically required merely because the source is FBX. Native hierarchy, materials and animation behavior still require a small import proof.

Start with an optimized Milky Way gate: static main body, independent inner ring, instanced chevron parts. Separate rigid parts can follow the accepted relay progress/state via GameObject transforms without trusting a monolithic pre-timed sequence. Evaluate imported prefab animation as an alternative only after its clip/control behavior is verified. Avoid changing the authoritative dialing or custody workflows for rendering.

## Proposed next milestone (after command-access acceptance)

- [x] Produce static candidate: 156,395 triangles and 5.6MiB. Initial 100k target relaxed after visual failures; explicit 250k/10MiB cap for this single-model preview. Offline shape/opening review passed; native performance pending.
- [ ] Establish scale, pivot, orientation and materials in an isolated import preview. Preserve original assets and license notices.
- [ ] Persist a visual transform linked to gate ID; explicitly align passage bounds and arrival position. Existing arbitrary zones/arrival points must not move silently.
- [ ] Show/remove visuals for nearby players with bounded asset reuse and unload cleanup; compare client frame time, memory and first-load transfer.
- [ ] Obtain player acceptance of static appearance/placement before ring/chevron animation and event-horizon effects.
- [ ] Later: relay-driven chevrons/ring, opening/closing effect, a real DHD model, then additional gate variants.

Rollback removes only optional rendering artifacts/configuration; gate, DHD, horizon and transfer data remain intact. The test deployment includes the optimized static model payload. Runtime compatibility/performance and visual quality remain open, not inferred from successful API compilation.

Phase 5D.1 adds only an admin-owned temporary preview before persistent alignment: see phase-5-static-preview.md. Generated model, source notice and machine-readable metrics reside in src/assets/models/milkyway; offline generator/validator in scripts/assets.

Active milestone: phase-5-persistent-visuals.md (5D.2), model persistence/visibility first. Explicit travel geometry alignment follows in 5D.3; animation and final materials remain later work.
