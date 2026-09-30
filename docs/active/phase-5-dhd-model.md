# Phase 5: DHD model and direct interaction

Status: supplied detailed DHD placement, collision, menu and visual lights accepted on the test servers; front event-horizon lighting accepted for this pass. Combined DHD/reload/travel regression accepted on Development and Demo (2026-09-29). Disposable-gate deletion and native OBJ importer investigation remain open; see root `docs/handoff/stargate-phase5-combined-regression.md`.

## Objective

A persistent, visible DHD console near each gate that a player can interact with directly to open the existing DHD menu. Admins place or remove it with `/sg placedhd <gate ID>` and `/sg removedhd <gate ID>`. Existing bound world objects remain compatible and are not silently replaced. No relay/travel protocol change.

## Constraints and asset decision

The supplied `Stargate DHD.zip` labels the creator's model CC0 and also warns that its Fan Art design must not be used commercially. The player states this plugin is used noncommercially; that warning alone does not rule out using the supplied model here. CC0 does not grant rights held by the owners of the underlying Stargate design. The first implementation conservatively used repository-generated parametric geometry instead of importing the blend; that was an implementation choice, not a conclusion that noncommercial use is prohibited. Reconsider the supplied source if a more accurate model is desired. No new runtime dependency. Place 2.5m in front of the admin. The model is decorative except for a simple interaction collider; menu/dial authority stays in DhdService.

## Checklist

- [x] Generate deterministic low-poly body/key/activation-button OBJ assets and source notice.
- [x] Add world-local placement store with gate-delete cleanup, admin commands, proximity rendering and direct GameObject interaction.
- [x] Reuse existing DHD modal with model-session validation; keep old bound-object workflow.
- [x] Placement persistence/cleanup test, 45 package tests, entrypoint check; native interaction accepted on Development.
- [x] Back up both test-server DBs/JAR; deploy Development/Demo, verify reload/network, hashes and unaffected data.
- [x] Development player: visible, direct DHD menu, `rp`, remove/re-place and no duplicate model.
- [x] Raise the supplied console half a game block without changing stored positions; add stone UVs and seven stable key circuits.
- [x] Add native point lights for DHD keys/activation and gate chevrons; switch by relay visual state.
- [x] Player: verify Demo DHD placement/facing, collision, direct menu, night key and Chevron lights; accept final front water illumination for this pass.
- [ ] Combined regression: normal-player/old-binding access, distance, idle/dial/open light state, relog/`rp`, travel/inventory and disposable-gate deletion. See root `docs/handoff/stargate-phase5-combined-regression.md`.

## Risks and rollback

Native client OBJ importer has occasional unexplained errors; verify actual model visibility in game. `PlayerGameObjectInteractionEvent` plus a primitive collider must be confirmed with a player test. Rollback JAR/assets only; additive table can remain. Preserve inventories, gates and relay state. Do not publish or deploy to Production at this milestone.

## Development/Demo deployment (2026-09-28)

Model builder replay exact; 2,362 triangles total, zero degenerate faces after OBJ rounding.
Maven package: 45 tests, entrypoint check and diff check passed. The admin placement is
2.5m in front to avoid trapping the player inside its solid interaction collider.
Backup `/docker/apps/stargate-dhd-model-backup.xm0ve972` contains prior JAR, i18n and
SQLite snapshots. Both servers loaded class digest
`db3516a77dabe677e64b5f8093dd1ea0daa63c251cf79a60770fa4911d7e7130`;
new assets/i18n match; five existing placement tables unchanged and SQLite integrity OK.
Development reload/network-ready 07:29:19 UTC, Demo 07:29:15 UTC.
`stargate_dhd_models` exists and has zero rows before player placement. The model is
not yet natively visible/tested; user invited to place Development model at B08AA181.
Intermittent OBJ importer error from gate milestone remains open. No relay/production changes.

## First native Development check

Player confirms model visible and direct interaction opens existing DHD menu.
Development SQLite has one placement for `B08AA181`, integrity OK. Screenshot
`local.res/stargate-assets/dhd-model-first-placement.png` shows the complete console
beside the accepted gate at a free ground position, with discernible radial keys and
red activation centre. The client log rotated after the saved offset; in the current
09:37 segment all four DHD OBJ parts report successful loads. One separate
ArgumentOutOfRangeException follows; the log does not name its failing asset.
Reload/replacement/legacy-DHD regression remains pending; requested from player.

## Development player acceptance (2026-09-28)

Player: model visible and menu opens. Follow-up `rp` showed exactly one model; removing
and placing it again removed/restored the model and interaction worked. Development
`stargate_dhd_models` has exactly one `B08AA181` row after replacement, SQLite integrity OK.
Network-ready logged after player `rp` at 07:55:58 UTC; no DHD render error in the checked
server segment. Demo remains deployed/empty (zero DHD models) and has not received a native
placement test. This acceptance does not cover normal-player access, distant interaction,
old object bindings, travel/inventory or client-importer reliability. Stop before combined
regression as requested after each player test. No release/production change.

## Supplied BlendSwap model candidate (2026-09-28)

Player explicitly requests trying the supplied model for this noncommercial plugin.
`Stargate DHD.zip` contains Blender 2.71 `DHD.blend` by blenderjunky, an archived license
HTML and preview. The exact source archive and Blend hashes are verified by
`scripts/assets/rebuild-supplied-dhd.py`; Blender 3.3.15 applies the authored base Mirror,
omits very dense Subsurf modifiers, uses constrained reduction on rigid pieces and a
deterministic planar dissolve on keys. Five material groups preserve body, accents, keys,
symbol faces and central activation dome. The result is 43,863 triangles and reproduces
byte-for-byte. Converted dimensions ~1.45m diameter and 1.12m tall, floor Y=0; front
and authored tilt face the placing player. Source archive/blend are not packaged. The
verbatim source license HTML and a source/changes notice accompany converted OBJ files.
The accepted parametric meshes remain present for JAR-only rollback.

The source labels the model CC0 and warns that the fan-art design must not be used
commercially. Player states this plugin is noncommercial. This does not settle any rights
held by the Stargate design owner. Do not treat this model as unrestricted for a later
commercial distribution; preserve notices. Native appearance and interaction acceptance
of this new candidate remain pending.

### Candidate activation and first native check

2026-09-28: Maven45 tests/package and entrypoint/diff checks passed; exact offline
regeneration passed twice. All OBJ indices/bounds validated; 43,863 triangles, no extra
runtime dependency. Backup `/docker/apps/stargate-supplied-dhd-backup.b6bob21c`
contains the accepted previous JAR/i18n and SQLite snapshots. Development and Demo
activated class digest `8f583d358de5931a6ed43ed39d51b22496985278cc2f66aaf84160ed09dc03be`
at 09:03:47/09:03:44 UTC. All eight derived assets match, six world placement tables
match backup on both and SQLite integrity is OK. No active transfer at upload.
Native screenshot `local.res/stargate-assets/dhd-supplied-first-native.png` confirms a
DHD shape at left of the gate, placed on ground; fog/distance prevent detailed material
judgment. New client segment has 14 successful OBJ loads and no load exception.
Player close-view, orientation and interaction acceptance requested; do not mark complete.

## DHD placement and real-light preview (2026-09-28)

Player's Demo screenshot from beneath the floor showed the supplied foot below the platform.
The runtime root now sits 0.5 game units higher; stored placement rows remain unchanged and
the presence check accounts for the offset. Existing gate stone texture is box-projected
onto the body and accents. Seven disjoint, deterministic key circuits cover 38 whole
authored buttons; successive outgoing chevrons activate them. Native point lights illuminate
active keys, the central activation dome when OPEN, and each lit gate chevron. Lights have
small ranges and no shadows; none are active while idle. DHD state refreshes once per second.

Offline source regeneration verified all 15 derived files byte-for-byte, with the same
43,863 source triangles; Maven package/45 tests, entrypoint and diff checks pass. Backup
`/docker/apps/stargate-dhd-light-backup.us2319kr` holds prior JAR/i18n/SQLite snapshots.
Development reloaded/network-ready at 09:38:16 UTC; Demo at 09:38:21 UTC. Both load the
same `AnimatedDhdModel.class` digest
`f422f1892cba3b862874d10be2f99541dade48863b42cc124c24285f1dec483f`;
all 15 derived files match by checksum and six world placement tables remain byte-for-byte
equivalent to the backups (SQLite integrity OK). Relay had zero active transfers before upload.
After reload the live screenshot was taken at night; it cannot establish floor clearance
or texture quality. A controlled player test must confirm dynamic illumination and that
idle lights switch off. No release or Production deployment.

The first circuit split used triangle centroids and could illuminate partial buttons.
It was replaced before player acceptance with connected-component grouping (38 complete
button shells, 5–6 per circuit). Exact offline replay passed again; derived files match on
both test servers. JAR reload/network-ready completed again at 09:45:21 UTC on each.

## Placement, collision and night lighting correction (2026-09-28)

Player removed and re-placed the DHD on Demo: its base now hovered about 0.55 game units
above the floor. The earlier apparent embedding came from replacing the provisional model
at an old placement. Remove the runtime 0.5-unit clearance and restore the direct stored
position without DB migration. Reverse the mistaken facing so looking toward the intended
console placement yields the keypad toward the player. Replace the cuboid collision with
a 24-sided convex hull approximating the column, wide rim and upper dome.

The earlier seven circuits each lit several buttons. The offline builder now chooses seven
spaced whole keys from 38 and assigns each key's source glyph components to the same
step. Other keys/glyphs remain in unlit meshes. One local native point light accompanies
each selected key. Increase DHD and chevron light intensity, and add two pale blue point
lights to the visible event horizon. These lights remain cosmetic; relay authority and
travel code are unchanged. The original combined key/symbol files remain for rollback.

Offline source replay verifies 24 generated files and 43,863 original triangles. Maven
package and all 45 tests pass. Backup `/docker/apps/stargate-dhd-placement-backup.g3k9_qx1`
includes the prior JAR, DHD assets and consistent SQLite snapshots on both test servers.
No active relay transfer before upload. Development reloaded/network-ready at 10:23:44 UTC.
Demo reloaded/network-ready at 10:26:12 UTC. Both test servers have matching 24 derived
assets and `AnimatedDhdModel.class` digest
`196014233bb11045959ae002cde6397263c3f00e9c6b0fceaf4989de553f5c07`.
SQLite integrity passes and six placement tables match the backup on both. Native player
acceptance of height, facing, rounded collision and night illumination remains pending.

## Demo collision regression and primitive fix (2026-09-28)

Player on Demo confirms renewed placement height and facing work, but the 24-sided
`HullCollider` gives no collision and no direct GameObject interaction. Server log shows
the raw mesh asset registered without a Java exception; it does not establish that client
physics accepted the hull. Remove this ineffective hull and its asset. Restore a compact
primitive `BoxCollider` on the central column (same supported API path as the previously
accepted model), then add three primitive `SphereCollider` children for the pedestal,
rim and dome. Interaction still walks each hit child's parent chain to the DHD root.
The primitive colliders are an approximation, not mesh-accurate collision.

Maven package/45 tests passed. Backup
`/docker/apps/stargate-dhd-collider-fix-backup.9gmfc2d2` contains current JARs and
consistent SQLite snapshots, including one DHD placement on each test server. Relay had
zero active transfers before update. Demo player must verify both physical blocking and
the DHD menu before this fix is accepted.
Demo reloaded/network-ready at 11:42:19/11:42:20 UTC and Development at 11:58:28 UTC.
Both JARs contain `AnimatedDhdModel.class` digest
`231d96ffcd3c80d468d26d0212eec81ff44ac38987c17f7e454ff8a55bd2ed82`.
SQLite integrity passes and all six placement tables match the immediately preceding
backup on each server. This proves deployment and data preservation, not client collision.

## Outer rim coverage (2026-09-28)

Player confirms DHD interaction works with primitive colliders, but can still walk through
the visible rim. The authored body's upper radius reaches 0.721 local metres between
Y=0.7 and 0.9. The prior upper sphere radius 0.69 was smaller even at its centre and
smaller still at the top/bottom of that band. Increase it to 0.80 and adjust the pedestal
sphere to radius 0.48 at Y=0.20. An offline vertex-envelope check finds no uncovered
upper-rim vertices (at least 0.079 local metres collision margin in Y=0.7–0.9).

Maven package/45 tests passed. Backup `/docker/apps/stargate-dhd-rim-backup.6xnguyhz`
contains the prior functioning-interaction JAR and consistent SQLite snapshots for both
test worlds. Relay had zero active transfers. Demo reloaded/network-ready 12:17:59 UTC,
Development 12:18:08 UTC. Both JARs have DHD class digest
`8b28f39484d0c105b40382c5a8a6ed7d941b67fb7826fe88a2e01c93d34ae214`; SQLite
integrity OK, one DHD placement per world. Player collision acceptance remains pending.

## Filled primitive collision correction (2026-09-28)

User places a construction block at the supposed collision limit, then stands inside the
DHD while looking down. Screenshots confirm real penetration of the visible console; the
working interaction alone did not prove body collision. Inspection of PluginAPI 0.9.3.2
bytecode shows `SphereCollider` constructors select `Collider.Type.Box` while serializing
only sphere data. The round primitive children therefore cannot be trusted; the narrow
central `BoxCollider` is the only known effective solid collider.

Remove the sphere children. Compose three solid circular cross-sections from 12
axis-aligned native `BoxCollider` strips each, at base/middle/rim heights. They fill the
interior as well as the edge and retain the central box for direct interaction. A local
envelope check against every body OBJ vertex finds 0 uncovered vertices across all height
bands. This is a geometry check; client physics still needs player acceptance.

Maven package/45 tests pass. Backup
`/docker/apps/stargate-dhd-filled-collision-backup.6rgqft3s` contains the previous JAR
and consistent SQLite snapshots for both test worlds; relay had zero active transfers.
The player moved outside before Demo reload. Development reloaded/network-ready at
13:04:22 UTC; Demo at 13:08:26 UTC. Both JARs have DHD class digest
`56a5001f8a983632c413013560e81ee636d398c58f621b96aea553c03b103c06`; SQLite
integrity is OK and one DHD placement remains in each world. Demo player confirms the
filled collider blocks at both rim and centre and direct DHD menu interaction still works.
This accepts the collision fix on Demo; night lighting and combined regression remain
pending. Stop at this player-test checkpoint.

## Night-light visual review (2026-09-28)

Player requested a live look at the DHD lighting. Screenshot
`local.res/stargate-assets/dhd-night-light-review-2026-09-28.png` shows an OPEN gate at
night: the red activation dome is clearly visible and several selected keys are warm
orange, as expected after seven chevrons. The light spills onto neighbouring key faces,
so the glyphs do not read as sharply isolated symbols. The outer console remains nearly
black. The event horizon is largely dark blue with one prominent warm bright patch; the
expected even blue illumination is not visible. This is visual evidence, not proof of
which light/material causes the patch. Review per-key light range/placement and horizon
material/lighting before accepting the night-light milestone. No code change in this review.

## DHD and chevron light alignment (2026-09-28)

The seven DHD lights now use the selected glyph mesh centroids, raised 4 cm along each
face normal. Their local range drops from 1.7 to 0.48 m and intensity rises from 2.4
to 3.2. Gate-chevron lights move toward the V strips and front surface; their range
rises from 2.4 to 2.8 m and intensity from 2 to 4. Relay-driven activation is unchanged.
Maven package passed all 45 tests; entry-point architecture and diff checks passed.
Zero active transfers before update. Backup
`/docker/apps/stargate-light-alignment-backup.w_a2x6_l` preserves both prior JARs and
consistent SQLite snapshots. Demo reloaded/network-ready 13:43:53 UTC; Development
13:45:58 UTC. Both JARs have matching DHD and gate class digests. SQLite integrity
is OK with one DHD placement per world. Player night-light acceptance remains pending.

Player visual test showed a mirrored DHD pattern (11 o'clock key lit at 1 o'clock)
and almost invisible gate chevrons. Live screenshot of the dial confirmed the gate
ring was dark. The imported OBJ flips X relative to native child light coordinates,
so the DHD light X values are now negated. Gate source meshes locate chevron strips
at Y=2.728-2.934 m relative to the assembly; the earlier light at Y=-0.18 m was
near the gate centre. It now sits at Y=2.88 m, Z=-0.3 m, with a tighter 1.8 m range
and intensity 4.5. This should also remove the misplaced warm light from the
wormhole centre; visual confirmation is pending. Maven package passed 45 tests;
entry-point and diff checks passed. Zero
active transfers before update. Backup
`/docker/apps/stargate-light-coordinate-backup.9l7t9xdt` preserves prior JARs and
consistent SQLite snapshots. Demo reloaded/network-ready 14:13:05 UTC, Development
14:15:44 UTC; both JAR class digests match. SQLite integrity OK, one DHD placement
per world. Player has closed the game temporarily; no more screenshots until asked.
Visual acceptance remains pending.

Player accepted the revised DHD and chevron lights but reported the event horizon
looks better from behind. Two attempted live screenshots showed a closed gate, so
the difference was not directly captured. `GateWormholeVisual` used a double-sided
surface but placed both blue lights only at local Z=-0.25 m. It now has a matching
pair at Z=+0.25 m. This is a focused lighting change; the water texture and motion
remain as accepted. Maven package passed 45 tests; architecture and diff checks
passed. Zero active transfers before update. Backup
`/docker/apps/stargate-horizon-bilateral-backup.ublyhp5q` contains prior JARs and
consistent SQLite snapshots. Demo reloaded/network-ready 16:42:33 UTC and
Development 16:43:15 UTC; both horizon class digests match. Front/back visual
acceptance remains pending.

Live comparison while OPEN confirmed the front was markedly darker than the rear:
the rear showed a bright white-blue centre, while the DHD-facing front stayed dark
blue. Matching light pairs on opposite sides did not fix it because `DoubleSided`
disables culling but keeps the original mesh normals. The water loop now renders
each frame twice, with the second model rotated 180 degrees around Y, and uses
single-sided water materials. A central blue point light on each side (local
Z=±0.35 m, range 5 m, intensity 5.5) follows the texture's white centre and
dark blue rim. The brief dissolve still uses its existing accepted material.
Maven package passed 45 tests; architecture and diff checks passed. No active
transfers before update. Backup
`/docker/apps/stargate-horizon-face-backup.w19bt1z5` contains prior JARs and
consistent SQLite snapshots. Demo reloaded/network-ready 17:10:39 UTC and
Development 17:11:24 UTC with matching water-class digests. Player visual
acceptance is pending after a fresh dial.

Fresh open-front and open-rear screenshots show the two water sides now much closer
in brightness, with a visible blue centre on each. Both still look dim at night.
Player also reported that active rear chevron clamps are unlit. Added a second
state-driven point light behind each chevron assembly at local Z=+0.3 m, matching
the front light's dial/open colours, range and intensity. Raised both central
horizon lights from intensity 5.5 to 8.5 and shifted their colour toward pale blue;
the accepted water texture and timing are unchanged. Maven package passed 45 tests,
architecture and diff checks passed, zero active transfers before update. Backup
`/docker/apps/stargate-rear-chevron-light-backup.gh5n6jc_` preserves prior JARs
and consistent SQLite snapshots. Demo reloaded/network-ready 17:24:54 UTC and
Development 17:25:46 UTC with matching gate/horizon class digests. Await player
night-time comparison of both sides and rear chevrons.

Player accepted rear chevron lights but still found the event horizon brighter from
behind. A fresh front screenshot confirmed the DHD-facing water retained a narrow
bright centre and very dark outer area. Offline atlas inspection found the loop's
centre luminance stable across 140 frames (mean RGB average 181-190), so frame
variation is unlikely to explain the persistent difference. The front (-Z) central
point light now has range 6 m and intensity 16; the accepted rear (+Z) light keeps
range 5 m and intensity 8.5. This is a targeted native-renderer compensation,
not a change to the texture, loop or chevrons. Maven package passed 45 tests;
entry-point and diff checks passed. Zero active transfers before update. Backup
`/docker/apps/stargate-horizon-front-light-backup.qv90y1fo` preserves prior JARs
and consistent SQLite snapshots. Demo reloaded/network-ready 17:48:20 UTC,
Development 17:49:18 UTC; horizon class digests match. Await player visual test.

Player signalled OPEN on the DHD side after this deployment. Live screenshot shows
the central white-blue water patch distinctly brighter than before, while the
outer area remains dark blue. A second screenshot after a player "hinten" signal
still showed the DHD at right foreground because the player had already returned
to the front by capture time. A later Kistenseite screenshot showed the correct
opposite location, but the gate had closed. User directly compared the two open
sides and reported the DHD-facing side remained darker. The stronger -Z light
therefore appears to illuminate the other visible face in the native renderer.
Both central lights now have range 6 m and intensity 16, preserving the brighter
rear while providing the same source to the front. Maven package passed 45 tests;
entry-point and diff checks passed, zero active transfers. Backup
`/docker/apps/stargate-horizon-balanced-backup.rckh1to7` holds prior JARs and
consistent SQLite snapshots. Demo reloaded/network-ready 18:22:15 UTC,
Development 18:23:04 UTC; horizon class digests match. Request a direct player
comparison; no further screenshot is necessary for this milestone.

Player supplied `local.res/screenshots/stargate/sg_vorne.jpg` and `sg_hinten.jpg`
from the open gate. They clearly show a much smaller, darker lit region on the
DHD-facing side even though that gate fills more of the image; viewing distance
does not explain the difference. The rear surface and rear chevrons look good.
Adjusted only the -Z/DHD-side central point light from range 6/intensity 16 to
range 7/intensity 40; rear remains range 6/intensity 16. This keeps the accepted
texture, animation and rear illumination. Maven package passed 45 tests;
entry-point and diff checks passed, zero active transfers before update. Backup
`/docker/apps/stargate-horizon-front-bright-backup.qbmq098i` holds prior JARs and
consistent SQLite snapshots. Demo reloaded/network-ready 18:34:23 UTC and
Development 18:36:37 UTC, with matching horizon class digests. Await direct
player front/rear comparison; avoid repeated timed screenshots.

Player supplied another matched pair from Steam (`20260928203901_1.jpg` from the
crate side, `20260928203910_1.jpg` from the DHD side). After increasing the
nominal front light to 40 while leaving the other at 16, the crate side was
brighter and the DHD side stayed dark. This suggests the native renderer's
effective light-to-face mapping is opposite the authored assumption. Player
asked whether point lights have direction and suggested a source far enough
in front to cover the full aperture. Point lights are omnidirectional; the
previous 7 m range covered the 2.31 m model radius geometrically but fell off
strongly at the rim. Both sources now sit at local Z=±1 m, range 12 m,
intensity 40, so each visible face receives a broad source regardless of that
mapping. The first equal-intensity/7 m candidate was built but not deployed;
the ±1 m/12 m candidate is the one on both test servers. Maven package passed
45 tests; entry-point and diff checks passed. Zero active transfers before
update. Backup `/docker/apps/stargate-horizon-wide-light-backup.yymg713g`
contains prior JARs and consistent SQLite snapshots. Demo reloaded/network-ready
18:53:20 UTC, Development 18:54:24 UTC; horizon class digests match. Await
player visual acceptance from both sides.

Two fresh Steam screenshots (`20260928210130_1.jpg` DHD side,
`20260928210143_1.jpg` crate side) show the ±1 m sources darkened both water
faces compared with the prior pair, especially the DHD side. The gate itself
is also darker on that side under the scene lighting. Moved both horizon
point lights back to local Z=±0.35 m while retaining range 12 m/intensity 40
to illuminate the water closer to its surface with broad coverage. Maven
package passed 45 tests; entry-point and diff checks passed. Zero active
transfers before upload. Backup
`/docker/apps/stargate-horizon-close-light-backup.cmy18vhh` contains both
prior JARs and consistent SQLite snapshots. Demo reloaded/network-ready at
19:06:06 UTC, Development at 19:06:58 UTC. Both classes have identical SHA256
`b769058fca620c9ec975710d00eb99dc01e2e560a1a0de33c8c9d362b3074873`;
both databases pass integrity check. Player front/rear visual comparison pending.

The new Steam pair (`20260928211026_1.jpg` DHD side,
`20260928211037_1.jpg` crate side) confirms a similarly bright white-blue
centre on both sides after moving the lights closer. Both water rims remain
dark blue; the DHD-side stone ring is also much darker under scene lighting.
Await player judgement on whether the remaining water difference is acceptable.

Player rejects the still-dark DHD side and supplied a Steam helmet-light
reference (`20260928211319_1.jpg`): the desired water is visibly blue nearly
to its edge while retaining a bright centre. Replaced the DHD-side central
point light with four lower-intensity sources at 30/70% on both aperture axes
(local X/Y=±0.924 m, Z=+0.35 m, intensity 30 each); kept the opposite central
source at Z=-0.35 m, intensity 40. Range stays 12 m. Maven package passed
45 tests; entry-point and diff checks passed. Zero active transfers before
upload. Backup `/docker/apps/stargate-horizon-four-light-backup.1j4bcwu0`
contains prior JARs and consistent SQLite snapshots. Demo reloaded/network-ready
19:22:21 UTC and Development 19:23:04 UTC. Both classes have SHA256
`e52d99a87d59a13dab8ef467ac2d96df79ee009452f04aa0496ffe5cbf1014fb`;
both DBs pass integrity check. Await player visual comparison, including possible
corner hotspots and whether the DHD side now meets the helmet-light reference.

Player's Steam pair (`20260928212531_1.jpg` DHD side,
`20260928212544_1.jpg` crate side) confirmed the four-source pattern appeared
on the crate side and did not brighten the DHD face. Swapped the local Z
positions of the four sources and the opposite central source; water meshes,
atlas, animation and light strengths are unchanged. This directly tests the
observed side mapping. Maven package passed 45 tests; entry-point and diff
checks passed, zero active transfers. Backup
`/docker/apps/stargate-horizon-flip-light-backup.uatchejf` contains prior JARs
and consistent SQLite snapshots. Demo reloaded/network-ready 19:29:38 UTC,
Development 19:30:24 UTC. Horizon class SHA256 on both servers:
`92f4dec850a3158a7f255ca9256debae7b1275f14a0c124b5e9877e8e472adfd`;
both SQLite databases pass integrity check. Await player visual test of both
sides; if the four spots remain objectionable, change the light distribution.

Player's next Steam pair (`20260928213217_1.jpg` DHD side,
`20260928213228_1.jpg` crate side) confirms the DHD side now receives the
four light spots, but the rear still looks better. For an explicit range
experiment, changed only the four DHD-side point lights from range 12 m to
36 m; the rear central light remains range 12 m and all intensities remain
unchanged. This tests whether wider falloff covers the aperture and may also
illuminate nearby terrain. Maven package passed 45 tests; entry-point and
diff checks passed, zero active transfers. Backup
`/docker/apps/stargate-horizon-triple-range-backup.uhkk_lcg` includes both
prior JARs and consistent SQLite snapshots. Demo reloaded/network-ready
19:35:05 UTC, Development 19:35:46 UTC. Horizon class SHA256 matches on both
servers (`c2e57e43645e232a4b16cc33b2b188b10397c6878ec15cb51df12adc1f0e28ca`);
SQLite integrity OK. Await player visual assessment before treating range 36
as a final value.

The next Steam pair (`20260928213649_1.jpg` crate side,
`20260928213701_1.jpg` DHD side) shows no clear difference from the range-12
pair. The earlier request used "radius"; implementation had changed `setRange`
instead of `setRadius`. Official PluginAPI docs distinguish maximum light reach
from the physical source radius, which makes light less punctual. Corrected
the experiment: restored all ranges to 12 m and set the four DHD-side source
radii from the API default 0.1 m to 0.3 m; rear source keeps default radius.
Maven package passed 45 tests; entry-point and diff checks passed, zero active
transfers. Backup `/docker/apps/stargate-horizon-source-radius-backup.quxwdlxk`
contains prior JARs and consistent SQLite snapshots. Demo reloaded/network-ready
19:40:09 UTC, Development 19:40:52 UTC. Horizon class SHA256 matches on both
servers (`1661ea768c5bc8fafcf78477d33257bfef82d0758b3043d6692f71dde1ad3e9a`);
SQLite integrity OK. Await player visual comparison; source radius may soften
the spots but may not make the overall horizon bright enough.

New Steam pair (`20260928214346_1.jpg` DHD side,
`20260928214356_1.jpg` crate side) confirms the larger source radius did not
improve the DHD face; the four patches look slightly dimmer. Player also
finds the rear dimmer than in the earliest Steam comparisons. Replaced the
four front point lights with a single broad spot from local Z=-2 m aimed
toward the aperture (+Z), 110° outer/90° inner angle, range 12 m and intensity
1000. The rear point at Z=+0.35 m/range 12/intensity 40 stays. This tests a
helmet-like broad beam instead of more near-surface point lights; visual
effect and possible spill onto nearby terrain are unverified. Maven package
passed 45 tests; entry-point and diff checks passed, zero active transfers.
Backup `/docker/apps/stargate-horizon-spot-light-backup.21iz59_n` contains
prior JARs and consistent SQLite snapshots. Demo reloaded/network-ready
19:49:15 UTC, Development 19:49:59 UTC. Horizon class SHA256 matches on both
servers (`026ed2663ea666325707a4b7d4cd4b80bdebe6fdb5c72c884f555f4d6be76b42`);
SQLite integrity OK. Await player comparison against the helmet-light image.

Player supplied Steam pair (`20260928215352_1.jpg` DHD side,
`20260928215405_1.jpg` crate side) and reports the front is better but the
illuminated radius remains too small. The front spot shows a single bright
centre with the outer water still dark. Expanded only its inner/outer cone
angles from 90°/110° to 115°/130°; source position, colour, intensity 1000,
range 12 and rear point light stay unchanged. At local Z=-2 m, the larger
inner cone covers the 2.31 m aperture radius geometrically; renderer falloff
still needs player confirmation. Maven package passed 45 tests; entry-point
and diff checks passed, zero active transfers. Backup
`/docker/apps/stargate-horizon-wide-spot-backup.okd7tyu1` contains prior
JARs and consistent SQLite snapshots. Demo reloaded/network-ready 19:56:46
UTC, Development 19:57:29 UTC. Matching horizon class SHA256:
`a94d6913e9243cec196769fc738266da94febe68ec0d991505bec6f03e51405c`;
SQLite integrity OK. Await front/rear visual acceptance, including spill.

Player supplied new Steam pair (`20260928220058_1.jpg` DHD side,
`20260928220107_1.jpg` crate side) and says the front lit area grew slightly;
one more small increase should be enough. Expanded only the front spot's
inner/outer cone angles by 5° to 120°/135°; all other light parameters stay
unchanged. Maven package passed 45 tests; entry-point and diff checks passed,
zero active transfers. Backup
`/docker/apps/stargate-horizon-spot-final-tune-backup.9654erud` contains
prior JARs and consistent SQLite snapshots. Demo reloaded/network-ready
20:03:50 UTC, Development 20:04:41 UTC. Horizon class SHA256 matches on both
servers (`da13d39aa3017cd52e46b36e23712e8c17ac614584ae1aebe964650a7c64bc80`);
SQLite integrity OK. Await player visual acceptance of this tuned cone.

Latest Steam pair (`20260928222643_1.jpg` DHD side,
`20260928222653_1.jpg` crate side) shows a stronger, wider front light;
player suggests moving the spot a little farther from the gate to enlarge
the footprint. Moved only the front spot from local Z=-2.0 to -2.3 m,
preserving 120°/135° cone angles, range 12 m and intensity 1000. This may
soften the centre while widening the footprint. Maven package passed 45
tests; entry-point and diff checks passed, zero active transfers. Backup
`/docker/apps/stargate-horizon-spot-distance-backup.n9_sb2sr` contains
prior JARs and consistent SQLite snapshots. Demo reloaded/network-ready
21:39:05 UTC, Development 21:39:49 UTC. Both JARs have matching horizon
class SHA256 `117a047836c0d8e27811453bea64ff768e4703f5963d7e5757c40acec6a1efec`;
SQLite integrity OK. Await player front/rear visual acceptance.

Player supplied final front Steam screenshot (`20260928234318_1.jpg`) after
the Z=-2.3 m update. The water still darkens before the inner rim, but the
player considers the current front lighting good enough. Treat this visual
milestone as accepted; retain the broad front spot and opposite point light.
No further tuning is planned for this pass.
