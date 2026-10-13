# OZ Stargate 0.7.1 release — completed 2026-10-13

## Objective

Remove the runtime OBJ renderers and `gate.assetMode`; ship the accepted gate, DHD, wormhole and platform bundles without generated OBJ models or build reports.

## Constraints and affected systems

- Owner: `rw-plugin-oz-stargate`. OZ Tools 0.26.2 and Stargate Network 0.5.1 remain unchanged; no Production deployment.
- Preserve saved world settings and SQLite data. A legacy `gate.assetMode` key is ignored; rollback to 0.7.0 can reuse the same data. The 0.7.0 platform schema rollback caveat still applies to older versions.
- Source OBJ files remain tracked because the bundle builders use them. The release ZIP excludes them and pure build reports.

## Checklist

- [x] Remove OBJ runtime renderers, fallback paths and setting; use bundle gate prefab for the collision-free, unlit temporary preview.
- [x] Correct preview placement to share the permanent gate's feet anchor, depth and facing after Development feedback.
- [x] Update 0.7.1 version, README, history and release notes; retain 0.7.0 history.
- [x] Run Java 25 Maven build and 88 tests; verify missing-bundle errors, entrypoint architecture and ZIP integrity.
- [x] Check all four bundle hashes against the public 0.7.0 ZIP; verify no OBJ or build reports and compare size (151,495,052 to 138,890,732 bytes).
- [x] Upload final build to Development with world settings and databases preserved; verify automatic reload and 0.7.1 bundle initialization in `Logs/OZStargate/current.log`.
- [x] Confirm gate, DHD, event horizon and platforms in a game client on Development.
- [x] Confirm corrected `/sg previewgate` placement and collision in a game client on Development.
- [x] Publish public plugin tag/release and verify CI, stable flags and ZIP asset.
- [x] Publish DE/EN forum entry, portfolio TSV and generated HTML; verify generator check.

## Risks and validation

Development client acceptance covers the gate, DHD, event horizon, platforms and corrected preview placement/collision. If a bundle is absent or unloadable, the existing render service logs the error and suspends that visual instead of selecting an OBJ fallback. Roll back to the 0.7.0 plugin package if the new preview fails; keep world data intact.

## Release evidence

- Plugin commit/tag: `5e0abf9`, `v0.7.1`. Main workflow `38309257121` and tag workflow `38309268883` completed successfully.
- Public GitHub Release is neither draft nor prerelease. Its `OZStargate-0.7.1.zip` is 138,890,717 bytes. The downloaded ZIP passed integrity checking, contains the four unchanged bundles, runtime textures and source notices, and contains no OBJ files or build reports. The JAR descriptor says 0.7.1 and the default settings omit `gate.assetMode`.
- Development automatically reloaded the final build at 2026-10-12 21:02:34 UTC. Stargate, wormhole, platform and DHD bundle prefabs loaded without render errors; player accepted the corrected preview and the other visuals.
- Root forum/portfolio commit: `bd8c937`; generator check passed. The original root worktree's unrelated changes were preserved by publishing from a clean checkout.
- Production plugin and relay were not deployed.
