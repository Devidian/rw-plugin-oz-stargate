# Stargate audio integration

## Objective and constraints

Provide runtime-selectable sound themes under `audio/<theme>/*.ogg`. The first public package contains only `silent`; Development may use the private files listed in `/wsl2development/privat/risingworld/local.res/stargate-audio-ref/audio-ref.md`. Never commit or package those reference recordings. Preserve relay protocol and travel logic; production servers remain untouched.

## Contract

Each theme uses the 17 exact filenames in `audio-ref.md`. The packaged `silent` files contain Vorbis silence with matching duration. Admin settings scan valid theme directories for selection, with missing clips falling back to silence. Newly added themes become selectable when admin settings are reopened; a selected theme is loaded on settings reload. Existing player sound preference defaults on and admin volume defaults to 70%.

Outgoing gate steps use `gate_roll_b/c.ogg` in alternating directions and `chevron_out_1.ogg`; incoming activation uses `chevron_1..7.ogg`. Gate opening, open loop, normal close, failed dial, DHD press, and horizon crossing use the corresponding named clips. Cosmetic movement is aligned to measured reference durations without changing dial protocol timing.

## Checklist

- [x] Update plugin test sqlite-jdbc to 3.53.4.0; local Maven tests/package pass. User approved the jdbc push on 2026-10-01; commit `936177b` is now on remote `main`. GitHub alert closure is still unconfirmed because `gh` authentication is invalid.
- [x] Relay registry audit and tests passed; GitHub alert list still inaccessible with invalid `gh` login.
- [x] Generate and package 17 silent Ogg Vorbis files from measured durations; `audio/generate-silent.py` is reproducible without private source files.
- [x] Implement safe theme scan/select, live volume, player toggle, spatial playback, one open loop per viewer/gate, and state cleanup.
- [x] 60 Maven tests pass, including DHD illumination, sequential dial timing, cue transitions and safe theme names; silent OGG codec/durations and silent-only ZIP verified. In-game sound lifetime awaits player acceptance.
- [x] Copy private reference theme only to Development, select `audio.theme=reference`, and verify final plugin reload/network ready at 11:02:28 UTC on 2026-10-01. SQLite integrity OK and zero active transfers.
- [x] Local Development and Demo/Development player tests completed. The player accepted the final sounds and source-first opening behavior on 2026-10-01.
- [x] Separate feature release approval received on 2026-10-01; publish version 0.3.0 with only the silent theme.

## Correction after traversal and incoming-dial feedback (2026-10-01)

The arrival gate now plays `go_trough.ogg` for the arriving player and nearby listeners after local teleport or a successfully applied network transfer. A local destination is not reserved or lit during the source's seven outgoing steps. At the seventh confirmed lock, the source checks target availability; an unavailable target fails. A target that is still dialing out is preempted without a transient fail cue, its old lights clear, and the seven incoming chevrons light and sound consecutively at 1321 ms each before OPEN. The isolated relay holds accepted remote connections for the same 9247 ms sequence. Incoming sounds now follow actual lit locks; they are not scheduled independently from an initial INCOMING event.

The source player becomes invisible before outgoing inventory/clothing clearing. A successful incoming transfer restores clothing, moves the player, then makes them visible and plays the arrival cue. Failed outgoing transfers restore the original visibility after inventory recovery. Local teleport hides the player during the position change and restores prior visibility at arrival. Player-facing behavior requires the Development/Demo acceptance below.

Validation: 60 Maven tests, `yarn test`, relay Docker build tests and isolated UUID-database transfer smoke passed. Final plugin JAR hash `957952fc66b40d2e07e0f1861857971f4134f485bfe5d33fbabe4ca44f6a5b88` matches both test servers; both `RELOADED ALL PLUGINS` and `Stargate network ready` appeared at 12:13 UTC. Seventeen private reference sounds loaded on both servers, SQLite integrity OK and isolated relay healthy. No active relay transfers before or after update. Backup: `/docker/apps/stargate-audio-arrival-backup-20261001`; rollback relay image: `stargate-network-audio-arrival-rollback:local`. Demo/Development test only; private reference OGGs remain outside the repository and ZIP. Production remains untouched.

Player acceptance: test A→B local while B is already dialing C; B should stop its old ring/sound, clear its old locks, play seven fast incoming locks, then open. Test unavailable local target after seven outgoing locks. Test remote dial in both directions for seven fast incoming locks before OPEN. Pass through locally and across servers with two viewers at the destination: each should hear the arrival cue; the moving player should be hidden while in transit and visible with restored clothing on arrival. Test aborted transfer, relog and plugin reload for visibility recovery. Pause for feedback before a public feature release.

## Source-first opening adjustment (2026-10-01)

The player accepted the previous revision except for the source/target timing. After target approval, the source now enters visible OPEN immediately. The target runs seven incoming locks at 400 ms spacing (2.8 seconds total); local `openTarget` and the relay `gateFree` travel window remain unavailable until the target sequence completes. The source and target closing sounds are anchored to the 60-second expiry after travel readiness, so opening early does not cause an early shutdown cue. The relay reply deadline is separate from the accepted activation wait; a late but valid reply still receives the full sequence. No new wire field or database migration.

Plugin tests/package: 60 passed. Relay `yarn test`, Docker build tests and isolated Mongo transfer smoke passed; the smoke asserts that source OPEN is visible while `transferStart` is still rejected as `gate_not_open`. The package contains only 17 silent OGGs. Local/deployed JAR SHA-256: `481fcc6c5ba936dafdaf50c5f7f82fcd34f9d217fe4a1b66f99156bd7dc2c1dd`. Test relay image: `sha256:31a55183306894d22730d1513139a58f5c3451beb944945253877900dc630bb8`, healthy. Demo reload/network ready at 14:06:26 UTC; Development at 14:06:31 UTC. SQLite integrity OK and zero active transfers before/after. Backup: `/docker/apps/stargate-source-first-backup-20261001`; rollback image: `stargate-network-source-first-rollback:local`. Development and Demo only; production and publication untouched.

Next player check: observe the source opening directly after target approval, seven target locks in about 2.8 seconds, and no travel until the target opens. Verify both local and cross-server directions, then pause for player feedback. The planned future travel video awaits PluginAPI support and is outside this revision.

## Risks

The PluginAPI sound loop and attenuation behavior need a Development test with two players. A missing or invalid custom clip must fall back to the packaged silent asset and log a useful warning. Custom theme files are administrator-managed server content and should survive package updates.

## Development evidence and rollback

Development host plugin path: `/appdata/rising-world/development-server/Plugins/OZStargate`. Backup of the previous JAR, world settings and consistent SQLite database: `/appdata/rising-world/development-server/.stargate-test-backup/audio-20261001/`. `audio/reference` contains exactly the 17 named private OGGs; neither Demo nor production received them. The runtime registered all 17 after the world setting changed and again after final upload. `RELOADED ALL PLUGINS` and Stargate network ready both appeared at 11:02:28 UTC after the correction. Local and Development `GateAudioTransition.class` hashes match. Player hearing and spatial behavior are unverified until the test below.

## Local player test

1. On Development, select a destination in the DHD menu: this should be silent. Start a full local dial: each illuminated DHD key plays its sound, then the ring moves for its full recorded sound, then the Chevron moves for its sound. All three phases are consecutive in each 7-second step.
2. At the seventh Chevron, check opening and open loop; on invalid target or abort check the fail sound. Normal expiry: shutdown begins near 57.4 seconds, while the gate remains open, and its tail ends with the visual collapse near 60.6 seconds. Test incoming interruption separately; interrupted outgoing sounds must stop.
3. Have two players at different distances; verify spatial falloff and one open loop each. Walk out of range and back, disconnect/relog, and reload the plugin. Check no duplicate or stale loops.
4. Toggle personal sound and set admin volume to 0, 70, 100; confirm changes apply live. Add a custom theme folder and reopen admin settings to see it.

Pause for feedback after this local test. Keep Demo/Development network audio test for the next checkpoint.

## Correction after first player feedback (2026-10-01)

DHD menu selection and dial button callbacks no longer play sound. The actual outgoing progress event now lights the DHD model key and plays `dhd_1/2.ogg` from the console position. The ring starts after 1131 ms, stops after its 3182/3090 ms recording, then Chevron movement and `chevron_out_1.ogg` start. All finish within the 7000 ms local step. The 7th Chevron completes before fail/open handling. Normal shutdown sound starts at 57,384 ms after OPEN and ends at 60,600 ms, matching the existing 600 ms visual collapse after the 60-second open window. A 90-second dial request timeout covers the longer seven-step sequence plus target reply.

The relay source also uses a 7000 ms step, with the existing v1 `stepMs` hint and no protocol/schema change; `yarn test` passes. The isolated test relay and Demo server have **not** been updated yet. The current Development deployment is for the local player hearing test; remote audio timing awaits the Demo/Development milestone.
