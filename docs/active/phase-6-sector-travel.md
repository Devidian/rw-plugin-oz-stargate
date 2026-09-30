# Phase 6: Local sector travel and optional cross-server network

## Objective

Every gate is a local gate. Each Rising World sector `(sectorX, sectorZ)` may
contain at most one gate in a world. A gate may dial another sector on its own
server without the relay. The relay still allocates each new gate's globally
unique ID at registration time. An admin setting enables cross-server travel;
its default is off, independent of relay address reservation.

## Constraints and ownership

- Existing gate IDs, DHDs, models, horizons, arrival transforms, transfer
  journals and player inventory must remain intact.
- An existing gate is migrated into the mandatory local sector index at startup.
  Migration is transactional. A legacy sector conflict stops the new gate
  registry from claiming a representative arbitrarily; inspect and resolve
  the conflicting records before enabling this version on that world.
- New registration checks the world-local sector before contacting the relay,
  then commits gate and sector ownership atomically after the relay responds.
  If the local commit fails, release the freshly allocated relay ID.
- The relay connection remains available for identity reservation and recovery
  even when cross-server travel is disabled. Network discovery/dialing and
  incoming connections must respect the travel setting on both peers.
- A local dial must reject a gate busy with either local or cross-server travel.
  An incoming connection interrupts outgoing dialing. Teleport only after an
  outgoing local wormhole opens and the player enters its passage. Reset entry
  tracking after arrival; leave inventory, clothing and permissions untouched.
- Compute sector coordinates with the native game chunk conversion and the
  existing GPS convention of 256 chunks per sector, including negative values.

## Checklist

- [x] Confirm shared gates, mandatory local ownership and immediate relay ID
  reservation with the player.
- [x] Replace optional `/sg locallink` enrollment with mandatory transactional
  migration and one-gate-per-sector registration. Remove obsolete commands/UI.
- [x] Add `network.enabled=false` to template PluginSettings/defaults/admin UI
  while keeping relay ID reservation and transfer recovery available.
- [x] Enforce the cross-server switch on both plugin and relay routing, with
  compatible behavior for already registered gate IDs. Isolated live relay
  transfer/toggle regression passed.
- [x] Add independent local dial state and timing, incoming priority,
  closure/expiry and restart behavior. Native behavior is not yet accepted.
- [x] Add local destination selection to DHD; allow local use when relay is
  unreachable, but require the relay to allocate new IDs.
- [x] Feed local state into existing visuals, DHD status and horizon passage.
- [x] Add DE/EN text and focused persistence/routing/timing tests. Movement
  itself requires the native player test.
- [x] Audit existing Development/Demo gate records and transfer journals,
  back up data, deploy to Development only, and verify runtime. Stop for player
  acceptance. Demo plugin deployment remains pending.
- [x] Development player check: same-sector rejection, network-off denial,
  second-sector DHD target, local passage and return. The player confirmed
  passage only through the visible opening, not through the misplaced preview.
- [ ] Test `/sg placegate` without an ID in an empty sector and inspect the
  automatic arrival point/facing. Stop after the setup check.
- [ ] Test local travel while the relay is deliberately unavailable, then
  restore it and verify new ID reservation again.
- [ ] Test network-on cross-server travel and custody regression on both test
  servers after the Development local workflow is accepted.
- [ ] At the end of Phase 6, profile client/server asset requests and load
  stalls on join and plugin reload. Count unique paths/asset IDs and repeated
  OBJ requests. Compare the existing per-runtime `ModelAsset` caches with
  imports using `ModelImportSettings`, and the 140 water plus 12 closing
  `MeshAsset` frames. Reuse or simplify only the assets proven redundant;
  validate rendering, animation, collision, and reload before/after.
- [ ] Revisit the blue `Area3D` debug preview at phase close; verify the
  native object's coordinate convention or replace only the preview. Keep
  the accepted passage logic unchanged.

## Risks and validation

- Older worlds may already have multiple gates in one sector. Preserve all
  records and fail the migration explicitly instead of dropping a gate.
- Existing test worlds currently have network travel enabled by behavior. A
  default-off setting changes that unless their world-specific settings opt in.
- Client and relay versions must be rolled out in a compatible order. Keep
  address assignment and existing transfer replay intact during the switch.
- Live player tests must cover registration rejection in the same sector,
  local travel with unchanged inventory, offline relay behavior, network-off
  denial, and network-on cross-server regression.

## Current checkpoint

The 2026-09-29 optional-enrollment prototype was superseded before player
testing. The revised relay and Development plugin are now active. Relay image
build, local Mongo integration test, plugin package and 50 JUnit tests pass.
Development native reload, relay readiness, automatic sector migration and
SQLite integrity pass. Development retains `B08AA181` at sector `(0,0)` with
one DHD/model/alignment and zero active transfers. The relay retains both
original gate IDs and zero active transfers; Development reports cross-server
travel disabled, Demo remains enabled on its prior plugin. Mongo and Development
SQLite backups are stored on the test host under phase6-20260929. Player
acceptance is tracked in the root handoff. The first player check passed:
existing gate and DHD work, same-sector registration is denied, and remote
dialing is blocked by the default-off switch. Development SQLite integrity
remains OK, with one sector row and no active transfers. A consistent backup
was taken before adding a second test gate. The second gate and DHD have now
been set up in sector `(0,-1)`; local travel has not yet been player-tested.
Demo deployment remains pending.

## Development feedback and correction

The second gate `50DDFCAC2E854A40` was registered in sector `(0,-1)`, with
its DHD and local destination visible. The player requested one-step new gate
placement, a model about 1.2 m lower in terrain, and a centred blue passage
preview. The latest Steam screenshot showed the preview beside the rotated
gate. `/sg placegate` without an ID now reserves an ID and atomically stores
the gate, model, aligned passage and forward-facing arrival;
`/sg placegate <ID>` retains manual placement and lowers newly placed models.
Unit tests pass; native positioning and preview appearance still require
Development acceptance. Development was
updated after a SQLite backup; only the new gate model and aligned passage
were lowered by 2.4 world units. Reload and database integrity passed.

The next Steam screenshot (`20260929235342_1.jpg`) confirms the lower model
height but shows the preview still displaced right and upward. The preview
origin now subtracts the centre offset rotated by the exact same quaternion
used to render the box. This revised JAR was loaded on Development at 21:56:47
UTC; 51 tests, reload, relay readiness and two-gate SQLite integrity passed.
Await a new `showhorizon` screenshot before local travel testing.

Two additional screenshots from different angles showed the Area3D debug box
almost edge-on in the visible ring despite its corrected centre. The box is
now rotated a quarter turn independently of the persisted passage. The 51
tests and Development reload at 22:00:44 UTC passed; native visual acceptance
is still pending.

The player reported that the blue debug frame still renders incorrectly, but
crossing it from either side did not trigger travel. Passing through the
visible event horizon did trigger the local trip, and the return trip also
worked. The Development SQLite audit after travel passed integrity, retained
both sectors/gates and showed no active transfer rows. Treat the debug frame
as a separate visual defect; do not infer travel geometry from its rendering.

The game developer reported hundreds of duplicate OBJ requests and over
1000 asset requests on reload, with `ModelAsset`/`MeshAsset` instances created
with import settings receiving distinct IDs. Code inspection shows gate and
DHD OBJ assets are already cached once per path per plugin runtime, while
`WormholeAssets` creates 140 water and 12 closing meshes once per runtime.
Measure actual request IDs and paths before changing asset lifetime or import
settings. This performance work is scheduled after the remaining Phase 6
functional tests, as requested by the player.

## Admin placement menu (current milestone)

Objective: offer one-step gate with DHD, gate only, and DHD placement in the
admin radial menu. A gate already in the sector keeps its relay address when
it is moved. Automatic DHD placement is 8 m in front and 2 m to the right
when looking from the DHD toward the gate, with its height sampled at that
position.

- [x] Add three distinct admin menu icons/actions; show DHD-only only in a sector with a gate.
- [x] Resolve terrain/construction surface height for automatic DHD placement.
- [x] Persist gate arrival, model, passage, and optional DHD in one transaction; preserve gate ID on move.
- [x] Reject busy gates, open transfers, sector boundary crossings, invalid ground and overlapping passages.
- [x] Verify native DHD position, move behavior and menu visibility with a player on Development.

52 tests pass, including SQLite rollback when a coordinated move fails. The
Development upload reloaded at 06:57:12 server time on 2026-09-30. A consistent
SQLite/JAR backup is at `.stargate-test-backup/radial-placement-20260930` on
the test host. Pre-upload integrity was OK, with three sector gates and no
active transfers. Demo remains unchanged. The DHD-only radial action follows
the existing `/sg placedhd` convention: 2.5 m ahead of the admin, with the
height measured at the target spot. Combined placement uses 8 m forward from
the gate front and 2 m to the right as seen while looking toward the gate.

The player confirmed all three placement actions, the sector-dependent menu,
and several local transfers after moving gate+DHD, gate only and DHD only.
Entry and exit points stayed synchronized.

## Gate+DHD removal and icon correction

- [x] Add an admin-only radial removal action only in occupied sectors, with a
  Yes/No confirmation naming the gate ID.
- [x] Reuse relay unregister acknowledgement; local gate deletion cascades to
  sector ownership, visual, horizon and DHD records. Block deletion while the
  gate is busy, an active transfer exists, or a local unregister is pending.
- [x] Replace the first low-detail action icons in both styles. Use the root
  `modern-actions`/`classic-actions` icon guide and semantic definitions.
- [x] Validate build, 52 tests, SQLite cascade and Development reload.
- [x] Player removal test on a disposable gate, including confirmation cancel.

The Development reload completed at 08:17:31 server time. Before upload a
consistent DB/JAR backup was written to
`.stargate-test-backup/gate-delete-icons-20260930`; four gate sectors and zero
active transfers were present. The post-reload SQLite integrity check passed.

NPC horizon detection was investigated separately in
`docs/active/phase-6-npc-travel-feasibility.md`. No NPC travel code is active.
The player confirmed the deletion tests on 2026-09-30. A subsequent
Development SQLite audit returned `integrity_check=ok`, four matching gate,
visual, aligned-horizon and DHD IDs, and no active transfers. NPC travel is
deferred until after the next release. The remaining pre-release Phase 6 work
is the previously scheduled OBJ/asset-request performance investigation and
release-readiness review.

The asset registration audit is recorded in `docs/active/phase-6-asset-audit.md`.
No per-gate duplicate OBJ registrations were found; client request IDs are
not available in the server log. Keep the native importer pause as a known
release limitation unless a clean client trace identifies a plugin-owned
duplicate source. The remaining work is the release-readiness review.

Risk/rollback: native raycasts may miss an unloaded surface, so no placement
is saved without a valid hit. Back up Development SQLite before deployment;
the previous JAR and backup restore the prior behavior. Demo remains unchanged.
