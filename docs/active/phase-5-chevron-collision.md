# Chevron finish and fixed model collision

Status: Chevron order and frame collision accepted by the player on 2026-09-27.
Historical implementation/test notes follow; remaining regression/import issues are retained below.

## Scope / decisions

- First final-polish milestone, approved2026-09-27. Then gate materials, real DHD model,
  combined final test; local sector travel/additional variants remain later work.
- Cosmetic V light turns on at phase0.80 (inner stop), stays lit on return and after relay
  confirmation. No timer confirms a lock or grants travel; offline/idle/preemption overrides
  old outgoing pose. Both V strips and fixed inner light retain brighter OPEN material.
- Light selection is evaluated with motion every animation sample; cached material identity
  prevents redundant sends. Shared service sampling increases20->30Hz. This reduces pose
  spacing; occasional native stutter has no proven root cause and is not claimed fixed.
- One MeshCollider uses the existing combined static gate mesh at the placed root. It follows
  the same scale/orientation/feet pivot and retains the aperture. Animated fittings use their
  home-position collision shape; no moving collider cook and no water/surge collider.
- Existing proximity/presence repair and asset disposal apply. Preview remains non-colliding.
  No database/protocol/travel-state changes.

## Validation / risks

- [x] Local PluginAPI signatures and official MeshCollider documentation verified.
- [x] 43 tests/package and entrypoint/diff checks pass; stroke tests cover light before return,
  next symbol, offline and interruption. Source geometry rays pass four opening probes and
  hit both side frame probes. This is not native player-physics acceptance.
- [x] Both test servers activated, class/assets/placement data verified; corrected raw collider run recorded below.
- [ ] Player test: V in/light/home, stable OPEN/abort lights, frame blocking and clear passage,
  travel/decline/re-entry, manual rp/relog, first-load hitch and animation smoothness.

Uses the existing156k-triangle mesh as a fixed collision surface: native cook cost and actual
player movement require inspection. Keep the through-path clear; no broad hull or bounding box.
Rollback previous JAR, no migration or new assets. Stop at player test before materials/DHD.

API: https://javadoc.rising-world.net/latest/net/risingworld/api/collider/MeshCollider.html

## Native-import correction (supersedes full-model collider above)

First activation emitted three native TriLib OBJ NullReferenceExceptions in the client after
adding the combined static ModelAsset as collider. The messages did not identify individual
failed assets; no exact root cause is claimed. Replace that additional OBJ import with a raw
MeshAsset annular prism:384vertices/768triangles, inner radius2.30m, outer3.075m, depth0.48m,
centreY3.075m. This approximates the solid ring; decorative protrusions have no separate
collision. No face spans the aperture. Shared native lifetime/placement scale still apply.
44 tests/package now include closed-manifold/ring-aperture and feet-pivot checks.

Corrected build class SHA256 on both test servers:627cd5f30234f987098edf3074aa5f10c86134df3c81a0ce50c22498fa80fc4d. All asset hashes and placement rows unchanged; SQLite integrity OK. Previous hash above belongs to the superseded OBJ-collider attempt.

Corrected activation confirmed: Development reload/network-ready21:11:10UTC, Demo21:11:07UTC.
Client received raw ring collision mesh (13873bytes), loaded eight OBJ models successfully;
no new Could-not-load/Exception messages in the checked33303-byte segment. This verifies
asset delivery, not physical player collision. Player acceptance pending; stop here.

## Player feedback / primitive-collider correction

Chevron sequence accepted; player reports no physical collision. Thus raw mesh receipt was
not sufficient. Layer docs and local API confirm collision depends on layer, but the exact
native cause of the failed mesh collider is unproven. Replace it with48 overlapping BoxCollider
children explicitly on Layer.OBJECT, isTrigger=false. Children inherit gate placement/scale,
no dependency on an imported/raw collider mesh. Radius2.30..3.075m, axial thickness0.48m;
small decorative protrusions remain excluded. Aperture remains clear. Chevron code unchanged.
Remove unused raw collision mesh allocation/disposal. Tests sample720angles at three frame
radii plus inner/outer clear radii, validate overlap, feet pivot and depth. Both-sided player
blocking, travel and rp still need native acceptance.

Client log also recorded later OBJ NullReferenceExceptions at23:14:56local during a subsequent
reload, after the previous clean observation. Do not claim the generic native importer issue
fully fixed. Primitive colliders remove mesh-loading dependence for collision only.

Primitive correction validation:44 tests/package, entrypoint/diff checks pass. Both class
hashes c81b57a7d4b69a219af61bd1147f83c18b191a7ef5fbdb03cc70422080ea65e2; all asset hashes
match and all five placement tables unchanged, SQLite integrity OK.
Backup `/docker/apps/stargate-collision-box-backup.qexmevyl/`; previous JAR rollback.
Evidence `/tmp/stargate-collision-box-build.log`, `/tmp/stargate-collision-box-verify.py`.
No active transfer before upload. Native acceptance must specifically verify solid frame
blocking from both sides and centre travel/decline/re-entry, then rp. No materials work yet.

Primitive correction activated: Development reload/ready21:18:54UTC; Demo21:18:50UTC.
Client segment185177bytes includes eight successful OBJ imports AND three TriLib
NullReferenceExceptions at23:18:58local. The intermittent native model-import issue remains
open; do not claim a clean native run. Collision segments no longer use the importer or a
mesh asset, but physical blocking still needs the player's test. Preserve this limitation.

## Player acceptance — collision correction

2026-09-27: user confirms "ring ist jetzt kollidierend mitte frei zugänglich, super!"
Together with prior acceptance of the Chevron sequence, the Chevron-order/frame-collision
slice is accepted. Physical frame blocking and clear centre are confirmed. This feedback
does not establish a fresh travel/rp regression result or resolution of occasional animation
stutter/native OBJ import errors; retain those as open final-validation issues.
Next approved milestone: gate materials/textures, then actual DHD model and combined tests.
No runtime changes at this acceptance checkpoint.
