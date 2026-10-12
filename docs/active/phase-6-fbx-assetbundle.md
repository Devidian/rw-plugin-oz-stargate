# FBX AssetBundle evaluation

## Objective and boundaries

Evaluate whether the supplied Milky Way FBX improves gate loading and visual quality when packaged as a Unity AssetBundle. Keep OBJ as the default and fallback until a player comparison is accepted. The Stargate plugin owns rendering and settings; the original FBX and Unity project remain outside the plugin. No release or production change is part of this evaluation.

## Implementation checklist

- [x] Confirm the source readme, FBX scale, material groups and original triangle counts.
- [x] Generate four LODs around 250k, 150k, 60k and 20k triangles per assembled gate; preserve the passage opening.
- [x] Build an HDRP prefab with material groups, emissive Chevron states, lights and a `LODGroup` using the game Unity version and SDK.
- [x] Add a settings selected bundle renderer with OBJ fallback; retain existing collision, wormhole and preview behavior.
- [x] Verify final geometry, bundle format, Java package and Development reload.
- [ ] Compare appearance, dial animation, gate loading, frame time and memory with a player client on Development.

## Risks and validation

Unity's prefab animation paths and HDRP lighting require a live client check. The bundle adds three platform payloads to the package and can increase first download time despite fewer triangles at distance. Record package size and client measurements before considering a default switch. Build and validation commands are in `scripts/assets/README.md`.

The first bundle candidate had LOD0 248,546, LOD1 149,789, LOD2 60,185 and LOD3 20,008 triangles per assembled gate. Its three-platform bundle was 50,293,590 bytes. The plugin default remains `obj`.

The first Development player check still used OBJ because `GateModelAssets` captured the mode before world settings loaded. Renderer selection now reads the loaded setting when a gate model is created. After the corrected Development reload, the server registered `stargatemilkyway/stargatemilkyway.prefab` from the AssetBundle and did not register the legacy gate body/ring/Chevron OBJ meshes. A fresh player comparison is still required.

The next player check showed a translucent magenta gate. The client received the prefab, so the issue is its bundled HDRP materials/shader variants. `DisallowGPUDrivenRendering` alone did not change the result. Reassigning every prefab mesh to the game's native HDRP `MaterialAsset` resolved the magenta: the player saw textures, animation, wormhole and light. The player also reported visible holes in the body (screenshot 20261009205719_1.jpg). Material-by-material FBX body simplification split shared seams. An attempted whole-FBX body reduction produced triangles over the passage and was rejected before deployment.

The new hybrid candidate retains the accepted, hole-free `textured-articulated-body.obj` for LOD0 body, with simplified versions for distant LODs. The moving ring and Chevrons still come from the supplied FBX. Manifest provenance records both source hashes. Offline geometry checks found zero degenerate triangles and zero body/ring triangle centroids within the central 2 m passage. LOD totals are 248,666, 149,780, 60,414 and 20,019 triangles per gate. Unity 6000.3.22f1 built the three-platform bundle (47,009,859 bytes). Development player acceptance of the hole fix is pending; keep OBJ as default and fallback.

The October 10 Development check confirmed that this hybrid bundle loads and that most body holes disappeared. Eight Steam screenshots from 23:58 through 00:01 show remaining broken symbol faces and patchy triangles on the moving inner ring. The player initially passed through the full frame, then reported working collision after another reload. A corrected runtime material map was uploaded after the first screenshots. A follow-up offline build uses the source FBX material groups directly for the LOD0 ring, retains joined simplification only for distant LODs, and bakes 48 solid annular box colliders into the prefab. The runtime now sets the prefab to `Layer.DEFAULT` and uses these baked colliders. Its LOD totals are 248,666, 149,780, 60,200 and 20,019 triangles; the three-platform bundle is 45,440,630 bytes (SHA256 `6c4f965d7a2eadbf9a4b97814cfac25a0d94636dd4b30d0ee483031210027a93`). The package ran 83 passing tests, and the versioned ZIP contains that exact bundle. Development reloaded it at 22:08:43 UTC October 10 and logged `REGISTER ASSET PREFAB ... FROM ASSETBUNDLE` plus `Using Stargate AssetBundle prefab`; no OBJ fallback was logged. This follow-up still needs a new Development player check; prior screenshots do not validate it.

The player checked that candidate on October 11: front symbols and frame collision are good; screenshots 20261011001000_1.jpg, 20261011001109_1.jpg and 20261011001116_1.jpg still show narrow rear openings. The next candidate added a rear-facing, 192-triangle annular backing mesh per LOD at local Z=-0.20 behind the rotating ring. Assembled LOD totals including the backing were 248,858, 149,972, 60,392 and 20,211 triangles. Unity produced a three-platform bundle of 45,463,055 bytes (SHA256 `e3f89069c6e3bc226cfef76961f2d84d5bff45f8dcfba4c5804b4e5f7255fa17`). The package ran 83 passing tests; Development logged `RELOADED ALL PLUGINS`, `REGISTER ASSET PREFAB ... FROM ASSETBUNDLE` and `Using Stargate AssetBundle prefab` at 22:18:55 UTC October 10. The player rejected this candidate: the holes remain, and the backing is visible from inside the gate. Screenshot 20261011002028_1.jpg also shows it protruding in profile. Do not retain this backing.

The source FBX has front-only Chevron V geometry (negative local Z), while the fixed Chevron block spans both sides. The next candidate removes the annular backing and mirrors each Chevron V mesh onto the rear side with reversed winding; rear V lights and motion follow the front V. This targets the nine large open Chevron pockets without introducing a surface across the ring. The remaining thin slits in the fixed body are a separate, unresolved mesh defect. Keep the `obj` default until Development player acceptance of both rear geometry and collision.

The accepted body OBJ has 22,991 boundary edges, 3,549 non-manifold edges and 3,622 connected boundary groups. PyMeshLab's `meshing_close_holes(maxholesize=20)` rejects it because the mesh is not edge-manifold. A broad automatic fill would also risk adding faces across the passage; repair the specific source regions only after identifying their coordinates and validating the result in a client.

Unity 6000.3.22f1 built the mirrored-Chevron candidate for Windows, Linux and macOS (45,955,480 bytes, SHA256 `9a5c8600399ce4149c929e48af865db970767a6cfc4d619e2db3b4eee95bfbdd`). It contains three `UnityFS` platform segments. With the nine mirrored V instances, assembled triangle totals are 273,776 / 159,662 / 63,800 / 21,279. The Java test suite passed 83 tests. The versioned ZIP and Development file have the same bundle hash. Development logged `RELOADED ALL PLUGINS`, `REGISTER ASSET PREFAB ... FROM ASSETBUNDLE` and `Using Stargate AssetBundle prefab` at 22:29:14 UTC October 10. Player acceptance of the rear geometry and collision is pending.

## October 11 LOD and light experiment

The player compared the bundle against OBJ and found rear holes in OBJ as well. The current bundle LOD0 is already preferable and its frame is stable. The current LOD1 has visible defects on the front. The V-element strips appear dark while only the Chevron head lights; reduce the head point-light intensity to 80% of its current value for the next trial.

- [x] Preserve every current LOD0 mesh and material assignment as the new LOD1; keep its threshold and animation paths consistent.
- [x] Generate a new LOD0 with about 100,000 more triangles than the current assembled LOD0 (~374,000 total). Spend the extra budget on the fixed body, where the remaining rear openings occur; retain the accepted ring, Chevron geometry and collision.
- [ ] Reject a candidate if it adds triangles to the clear passage, opens new front seams, changes the silhouette or loses material groups. Compare front/rear renders and geometry before packaging.
- [ ] Give the V strips their own local light contribution while preserving the native-material workaround; set the Chevron head point lights from 4.5 to 3.6 and verify both front and rear during dial/open animation.
- [x] Build with Unity 6000.3.22f1 for Windows, Linux and macOS, verify bundle and ZIP hashes, package tests and Development reload.
- [ ] Ask the player to compare near LOD0, the LOD0-to-LOD1 transition, V-strip/head light, rear holes, collision and client frame rate/memory against the current candidate. Keep `gate.assetMode=obj` as the shipped default until acceptance.

Risk: the source body is a multi-million-triangle, non-manifold mesh. A larger decimation budget alone may still bridge the passage or create rear seams. If a 220k body fails the geometry checks, keep the current LOD0 and isolate/repair the source regions before another Development upload.

The source body was reduced as one mesh with boundary preservation. LOD0 body has 219,981 triangles after filtering 19 rounded degenerates, compared with 119,999 in the accepted body. The assembled LOD totals including mirrored rear Vs are 373,758 / 273,776 / 63,800 / 21,279. LOD1's eight OBJ files are byte-identical to the former LOD0 files. The new LOD0 has no triangle centroid within 2.25 source metres of the gate centre, and its minimum vertex radius is 2.286 m. Unity rebuilt the prefab with 3.6-intensity head lights (from 4.5) and separate low-intensity V-strip lights; client appearance and performance remain unverified.

Unity 6000.3.22f1 built the three-platform trial bundle (60,596,559 bytes, SHA256 `4b2774310e3c9c9dd4dbdf9ebce7432fbc3855a113b41c71e7bb28745108686c`). Each platform segment begins with `UnityFS`; the prefab builder and bundle builder exited successfully. The 14.6 MB package increase versus the previous bundle and up to 36 active Chevron lights during dialing/opening make client frame-time and memory checks necessary before accepting this LOD arrangement.

The package passed 83 Java tests. The versioned ZIP, plugin checkout and Development file share the bundle SHA256 above. Development logged `RELOADED ALL PLUGINS` at 13:02:54 UTC October 11 and enabled OZStargate 0.6.0. Prefab registration was not yet observed in the next minute of logs, likely because no gate model was instantiated; player login/proximity and client appearance remain the next validation gate. Keep the prior bundle (`9a5c8600399ce4149c929e48af865db970767a6cfc4d619e2db3b4eee95bfbdd`) available for rollback if the trial regresses.

## October 11 LOD0 rear and Chevron follow-up

Player screenshots 20261011153020_1.jpg and 20261011153036_1.jpg show narrow openings through LOD0's rear/side body. Screenshot 20261011153236_1.jpg shows stone artefacts beside the illuminated rear Chevron head; the V strips still do not read as lit. The player accepts LOD1+ and the dialing head intensity, but wants the open-state head reduced slightly. Keep all LOD1+ mesh files unchanged.

The original FBX body includes a rear `Chevron` group with 21,276 triangles and a rear `Chevron Light` group with 3,888 triangles: exactly nine times the separate V housing (2,364) and V light (432) source counts. Their Z bounds overlap the mirrored animated rear Vs, explaining the stone-textured overlap. Remove these duplicate groups only from the new LOD0 body. The user permits dropping fine passage and middle-ring overlays to spend the same 220k body budget on structural/rear geometry; each exclusion still needs an aperture check because the first broad exclusion produced an invalid passage-spanning reduction and was rejected before bundling.

- [x] Produce an LOD0-only body with duplicate rear Vs removed; keep the passage free and LOD1+ meshes byte-identical. A second trial excluding `Lower Rim Pills` put 58,070 face centroids inside the passage and was rejected. Rear gap comparison remains a client check.
- [x] Give the V strip its own brighter native material and stronger local lights; lower only the open-state head intensity from 3.6 to 3.0.
- [x] Rebuild/test/deploy the Development candidate. Client comparison of rear gaps, V strips and open head remains open.

Unity 6000.3.22f1 built the three-platform bundle (60,612,610 bytes, SHA256 `578a4cf5fd8b56ca8073fd6acdecd554240fa0f94e6a12aabf81eafd2cbddd0b`). Its `RisingWorld` wrapper contains three `UnityFS` segments. The plugin package passed 83 tests; the versioned ZIP, plugin checkout and Development file have the same bundle SHA256. Development has `gate.assetMode=bundle` and logged `RELOADED ALL PLUGINS` at 14:02:11 UTC on October 11, followed by `REGISTER ASSET PREFAB ... FROM ASSETBUNDLE` for the updated bundle. Client visual acceptance is pending; the narrow exclusion does not prove closure of the remaining rear body gaps.

Player acceptance, October 11: rear V strips now look good, even without a visible independent light effect; head brightness is good. The player accepts this gate as the bundle version despite the previously reported small rear LOD0 gaps. Preserve this bundle as the accepted gate baseline while DHD and wormhole packaging are evaluated separately. Acceptance does not yet change the shipped `gate.assetMode=obj` default or authorize a public release.
