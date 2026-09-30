# Phase 6 release readiness (2026-09-30)

## Objective

Prepare the next public Stargate plugin/relay release with NPC travel excluded.
Public publication remains gated by explicit user authorization in `AGENTS.md`.

## Scope and checks

- [x] Player accepted Phase 6 local placement, moves, transfers and gate+DHD deletion.
- [x] Review deferred asset freeze: 33 unique Stargate file registrations per
  Development reload; no per-gate duplication found. See
  `phase-6-asset-audit.md`. Native importer stalls remain known.
- [x] Plugin package and 52 JUnit tests pass; relay `yarn test` passes.
- [x] Back up Development and Demo before the current two-server regression.
- [x] Upload the same plugin JAR to Demo, preserve world DB/settings, and
  validate sector migration, DHD, SQLite integrity and relay membership.
- [x] Player tests Development-to-Demo and return with distinct inventory
  and clothing; confirm each item once and restore Demo's original inventory.
- [x] Resolve the 2026-09-30 claimed transfer safely and validate both native
  inventories before resuming player travel. See incident below.
- [x] Restore the temporary network switches to the user's desired test-server
  setting after the regression.
- [x] Curate `0.2.0` versions, changelog and release notes in plugin and relay;
  archive plugin fragments under `changelog/consumed/0.2.0`.
- [x] Draft DE/EN forum release and update portfolio source/HTML.
- [x] Validate exact plugin ZIP, local CI checks, isolated relay transfer smoke
  and test-server activation of the candidate.
- [ ] Verify Git staging and hosted CI after the prepared commits are pushed.
- [ ] Publish relay then plugin only after explicit release authorization.
- [x] Resolve the late admin-settings regression on Development: relay off/on
  followed UI edits, German labels appeared, DHD reopened without
  `network_disabled`, local destinations remained visible, and no transfer
  remained active.
- [ ] Activate the corrected candidate on Demo and verify the DE/EN settings
  and relay toggle there before release. Publication remains paused.

## Test state / rollback

Demo and Development used the same plugin JAR hash prefix
`3d1618affc722370` before the incident fix. Both relay sessions were online
with matching network code, `travelEnabled=true`, and gate counts 1 (Demo) /
4 (Development) during the two-server test. Current installed fix and settings
are recorded below.
Both SQLite databases pass integrity, sectors match gates, DHDs are present,
and active transfers were zero before this regression. `network.enabled=true` was added temporarily
to both world settings for the regression; the original files and consistent
DB snapshots are in `.stargate-test-backup/pre-release-regression-20260930`
under each test server. Demo's previous JAR/assets/i18n are backed up too.
Production has not been changed.

An initial atomic settings write inherited `root:root` ownership and produced
an `IOException` on each server. The files were reassigned to the plugin user,
the settings reload succeeded, and relay membership above was verified.

## Claimed-transfer incident (2026-09-30)

Demo-to-Development transfer `896e166b-33e2-4015-b424-5c2142782abb`
reached relay `CLAIMED`, target `IN/PREPARED`, and source `OUT/DEPARTING`.
Both logins are blocked by the inventory safety guard. Target Development has
an older `HELD` visitor base and a different nonempty native player inventory;
applying the new transfer would overwrite that inventory. Demo's native player
inventory is empty, while its transfer journal retains the outgoing snapshot.
Native Player.db and plugin DB backups exist on both test servers under
`.stargate-test-backup/20260930-transfer-896e166b`; the relay document is saved
as `relay-transfer.extjson` in the Development backup.

Recovery requires an explicit, audited decision to cancel this already claimed
transfer after rechecking all three journals and both native player inventories.
The user explicitly authorized this transfer's recovery after automatic approval
review rejected an earlier direct mutation. Guarded updates set relay `CLAIMED`
to `ABORTED`, target `PREPARED` to `CANCELLED`, and source `DEPARTING` to
`ABORTED`. The relay payload was retained for the recovery audit. Both native
inventory and clothing checksums remained unchanged after these updates.
The player confirmed the Demo inventory and clothing were restored exactly once.
Read-only verification found source `OUT/CANCELLED`, target `IN/CANCELLED`, no
active local transfer on either server, Demo native inventory/clothing matching
the outgoing snapshot, and Development native inventory/clothing unchanged.
The tested plugin JAR with the `HELD` guard (SHA-256
`04f11e86b958a65340ba60c1273e8d6bc1fe61349f0e9874b43d70242b521a2d`)
is loaded on both test servers; both reported `RELOADED ALL PLUGINS` and relay
ready. The player confirmed that a new Demo-to-Development attempt is rejected
while Demo inventory and clothing remain complete. A read-only post-test check
found no active transfer on either server; native inventory/clothing checksums
are unchanged. The two-server round-trip regression remains pending because the
older `HELD` Development visitor inventory must be handled separately.

The plugin now rejects a new incoming transfer before source inventory clearing
when a prior visitor base remains `HELD` on the target.

## First round-trip leg accepted

Before testing, both native Player.db and plugin DBs were snapshotted in
`.stargate-test-backup/pre-roundtrip-20260930` on each test server. The player
traveled Development to Demo and confirmed inventory and clothing each once.
Transfer `86ae6090-6a1e-4f58-a400-f63212a4027d` is `DONE` at both ends.
Demo's native inventory and clothing checksums match the prior Development
values. Demo holds its original local inventory as visitor base (`HELD`);
Development's visitor base is `RESTORE_PENDING`. Stop here per the requested
player-test checkpoint.

## Return leg accepted

The player traveled Demo to Development and confirmed inventory and clothing
each once. Transfer `55878384-cacb-413d-82a4-1f6714c6d975` is `DONE` at both
ends. Development's native inventory/clothing checksums match its state before
the round trip; no transfer is active. Demo's original local inventory remains
in its visitor base as `RESTORE_PENDING`. The next separate player checkpoint
is a normal Demo login to verify that base is restored exactly once. Do not
start another gate transfer before that check.

## Demo base restoration and test cleanup accepted

The player confirmed the original Demo inventory was fully restored. Read-only
verification found no Demo visitor base, no active transfer on either server,
and native Demo inventory/clothing checksums matching their pre-round-trip
values. Development likewise retains its pre-round-trip inventory/clothing;
its visitor base remains `HELD`, so a new manual Demo-to-Development visit is
deliberately rejected before source inventory clearing. A later Development
departure through the gate is the supported way to release that held base.

The temporary `network.enabled=true` overrides were removed from both test
server world settings. Their prior files differed only by that switch; both
reloaded successfully. Relay membership remains online for global address
reservation, with `travelEnabled=false` on both servers. No production server
was changed. The cancelled incident's relay payload remains retained under
its `ABORTED` record and separately backed up for the recovery audit.

## 0.2.0 preparation

This feature release spans both standalone repositories. The plugin descriptor,
POM, HISTORY and release notes, plus the relay package, Compose example and
release notes, are prepared for `0.2.0`; publishing has not been authorized.
The plugin requires OZ Tools 0.26.2 and PluginAPI 0.9.3.2. The forum draft is
`../../../docs/forum/release-2026-09-30-stargate-0.2.0.html` from this file.
The rebuilt ZIP passed integrity, contains the model source/license notices,
and embeds the `0.2.0` descriptor. The candidate JAR SHA-256 is
`87950491162edb4b7abae34d8877eb34dc77bfe36e19d1f7dad7fd79da907e59`;
both Development and Demo loaded that exact JAR, reported `RELOADED ALL PLUGINS`,
and rejoined the relay with `travelEnabled=false`. The plugin's 52 JUnit tests,
entrypoint/API checks and forum generator check passed. Relay `yarn test` and
its isolated MongoDB transfer smoke passed; the latter used a unique test DB
and did not replace the running relay. Hosted CI remains pending until source
is pushed. Do not move either tag while an active transfer exists.
