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
- [x] Verify Git staging and hosted CI after the prepared commits are pushed.
- [x] Publish relay then plugin after explicit release authorization.
- [x] Resolve the late admin-settings regression on Development: relay off/on
  followed UI edits, German labels appeared, DHD reopened without
  `network_disabled`, local destinations remained visible, and no transfer
  remained active.
- [x] Activate the corrected candidate on Demo with the existing world settings
  and database preserved.
- [x] Player verified German settings texts and relay off/on on Demo.
- [x] Player verified the reduced chat policy and debug switch on Development
  and Demo.
- [x] Document the rejected Demo-to-Development journey and protect the
  nonempty visitor base. Keep the remaining limitation documented.
- [x] Obtain release acceptance of the documented limitation: a manual server
  switch during an unfinished nonempty visit can block a later gate arrival.
- [x] Player verified that a genuinely empty Development character accepted
  Demo's carried inventory without creating an empty visitor base.

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

## Late admin-settings fix on Demo

The corrected 0.2.0 JAR (`be6767b1a8361c0d89bb7d317f71d1ffed2ac46bd1dca282db5dcfffab71373c`)
and DE/EN translations are installed on Demo. The previous files are backed up
under `.stargate-test-backup/network-settings-fix-20260930`. The server loaded
OZ Stargate 0.2.0 and rejoined the relay; zero transfers are active. Demo's
world setting is currently `network.enabled=true` and the relay reports
`travelEnabled=true`. The player accepted the German setting texts and switch.
The container health check
still targets port 4254 while Demo serves `/info` successfully on 4354; this
is a separate server configuration issue and is not evidence of a plugin
startup failure.

## Pre-release chat and transfer follow-up

The requested chat policy is: announce incoming wormholes only to players in
the gate's sector; all other Stargate chat is available only through a player
setting that defaults off. The safety-critical uncertain-transfer kick remains
visible. The candidate JAR SHA-256 is
`b14c389c3f9cb097213eb5cc46cd203ce6f5c278442ec1d33053fb0722ccfeb4`.
It passed all 52 JUnit tests and is installed on Development after a backup
under `.stargate-test-backup/sector-chat-policy-20260930`. Development reported
`RELOADED ALL PLUGINS`, Stargate 0.2.0 enabled and relay ready. Player acceptance
and Demo deployment are pending. Development's `/info` responds on 5254 while
its container health check still targets 4254, a separate configuration issue.
The first Development upload showed the raw debug-setting key because the UI
looked up `tc.stargate.settings.debug_messages` rather than
`tc.settings.debug_messages`; the corrected candidate was uploaded and reloaded.

The Demo-to-Development attempts `138d2f73-4073-43ba-8b05-18167515d5f6`
and `7abb82e7-e7a0-4709-b4d3-3f7cfb05f062` ended `ABORTED` at the relay
and `CANCELLED` on Demo. Development retained a `HELD` visitor base for the
same player, so its preflight rejected the arrival before the source cleared
inventory. No transfer remains active. Do not remove this preflight without a
safe resolution of the held target inventory.
The player clarified that a complete Development-to-Demo-to-Development journey
does work with inventory intact. The rejection occurs only when starting on
Demo while Development still holds the previous visitor base.
After another Development-to-Demo transfer and normal Development login,
read-only checks show Development has no visitor base and Demo has `HELD`.
An incoming Demo-to-Development transfer would save Development's current
empty inventory as a new `HELD` base. Subsequent manual login on Demo can
restore Demo's base, but Development then rejects a new direct arrival. A
normal gate round trip shifts the held state; it does not permanently clear
both servers. The player has been asked whether manual server switching must
be supported or should be blocked during an unfinished visit. Do not perform
manual SQLite cleanup or allow an arrival over `HELD` as a workaround.

The player identified a safe narrower case: Development's local character was
empty after an accidental manual login, while the original Development gear
remained on Demo. An incoming transfer to that empty character needs no saved
local inventory or clothing. The target now skips `saveBase` only when both
are empty; nonempty target inventories remain protected. The candidate JAR is
`436a831375cf1a71240d82424c361f1d93e331eb3eab72bf29ed8ef20b907194`.
It passed 53 JUnit tests, both native and plugin SQLite DBs were backed up
under `.stargate-test-backup/empty-target-20260930` on each test server, and
Development loaded the JAR and rejoined the relay. The player confirmed DevA
inventory and clothing intact after Demo-to-Development transfer
`526d561e-5c19-4b69-a054-a0774f83269b`; relay and both local journals are
`DONE`, no transfer is active, Development has no visitor-base row, and Demo's
base is `RESTORE_PENDING`. This change does not settle the separate case
where an earlier visitor left nonempty inventory on the target.

The same JAR and DE/EN translations are now installed on Demo after a fresh
native/plugin DB and file backup under
`.stargate-test-backup/chat-empty-target-demo-20260930`. Demo reported
`RELOADED ALL PLUGINS`, Stargate 0.2.0 enabled, relay ready, and `/info` 200.
Both test servers are online with travel enabled and no active transfer.
The player accepted Demo's German debug label, default-off and enabled chat
behavior, and the once-only restoration of the native Demo inventory and
clothing. Read-only SQLite checks found no visitor-base row for this player on
either test server and no active local transfer. This player-test checkpoint
was accepted before publication.

## 0.2.0 preparation

This feature release spans both standalone repositories. The plugin descriptor,
POM, HISTORY and release notes, plus the relay package, Compose example and
release notes, were prepared for `0.2.0`; the user authorized publication.
The plugin requires OZ Tools 0.26.2 and PluginAPI 0.9.3.2. The forum draft is
`../../../docs/forum/release-2026-09-30-stargate-0.2.0.html` from this file.
The current ZIP SHA-256 is
`009a78ac769d916b713930a9c2c99ff2bf1dc19f2f365a083ecbbfbb62ef2cdf`;
its embedded JAR SHA-256 is
`5494600f9328a7ee4e3eed5f98f598a211b4630e28102253cacfdb1f8a694d6f`.
ZIP integrity, 0.2.0 descriptor, model source/license notices, 53 JUnit tests,
entrypoint/API checks and the root forum generator check passed. Development
and Demo accepted the same previous runtime JAR `436a8313...`; comparison of
all 127 JAR entries found identical file contents in that accepted JAR and the
final release JAR. Their different SHA-256 values arise from archive metadata.
Relay
`yarn test` and its isolated MongoDB transfer smoke passed earlier; the latter
used a unique test DB and did not replace the running relay. Neither release
tag was moved while an active transfer existed.

## 0.2.0 publication

Relay `main` CI 36768959961 and tag CI 36769139687 passed. The public
`v0.2.0` GitHub release is neither draft nor prerelease and includes the
Compose example. DockerHub tag `0.2.0` resolves to
`sha256:e77cb6e0003a495ddff9ccd561e3c4a4b88d059e3563f46c8c71e05e3dc5270c`
for linux/amd64 and linux/arm64.

Plugin `main` CI 36768989222 passed. Tag CI 36769574470 validated and built
the release, and published GitHub Packages, but GitHub returned `Server Error`
while creating the Release. A one-time recovery workflow downloaded that
validated tag artifact and published the ZIP in successful run 36770491054.
The recovery workflow was then removed from `main`; final CI 36770691784
passed. The public `v0.2.0` GitHub release is neither draft nor prerelease.
Its downloaded ZIP has SHA-256
`47325e8c25874ffb13b48c2e4a319169983d3cbc03c73e07ba1612c204baa8cf`
and passes ZIP integrity. Its embedded JAR has the same 125 runtime entries
byte-for-byte as the Development-accepted JAR; only the manifest and Maven
properties metadata differ. The root documentation commit `a1fc85b` is pushed.
No production game server was changed.
