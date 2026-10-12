# OZ Stargate 0.7.0 release

## Objective

Publish the Development-accepted AssetBundle gate, DHD, wormhole and four platforms as OZ Stargate 0.7.0. Publish only the plugin repository and player-facing forum/portfolio documentation; Production deployment is separate.

## Constraints and affected systems

- Owner: `rw-plugin-oz-stargate`; no relay protocol or shared dependency change. Requires published OZ Tools 0.26.2 and Stargate Network 0.5.1.
- Keep original third-party FBX/LWO/OBJ/Blend/LightWave source files outside Git and the plugin ZIP. Ship only derived runtime bundles and source notices.
- Preserve existing gate and player data. Existing `gate.assetMode=obj` remains an explicit operator choice; new installations default to `bundle`.
- The additive `stargate_visuals.platform_type` schema needs a database backup and column removal before rolling back to a pre-platform plugin.
- Player accepted geometry, materials, collision, placement and travel behavior on Development. The older second PC ran the bundle faster than the former OBJ version. Grace's rejected transparent-ring trial was rolled back; the opaque ring with four weak lights is the release baseline.

## Checklist

- [x] Align 0.7.0 POM, descriptor, history, release notes and bundle default.
- [x] Verify source package has no original third-party model/scene files and that all four bundle hashes match the Development candidates.
- [x] Run Java 25 package/tests, PluginAPI and entrypoint checks, JSON checks and `git diff --check` (86 tests passed; ZIP integrity checked).
- [ ] Inspect exact staged files, commit and push plugin `main`, then tag and push `v0.7.0`.
- [ ] Verify GitHub Actions and public non-draft, non-prerelease ZIP release; inspect ZIP contents and bundle hashes.
- [ ] Publish one DE/EN WoltLab release log in root docs, update `portfolio.tsv`, regenerate HTML and run generator check; commit and push only those root files.

## Risks and validation

The plugin ZIP is large because it contains separate Windows, Linux and macOS segments for the gate, DHD, wormhole and platforms. Verify CI artifact upload and the public ZIP independently of the tag. Check the author-source notices before publication; the film effect page explicitly marks the author's effect CC0, while the Accessory Pack page provides the download and usage description without a specific license statement. The user confirms intended use. Keep attribution and the external source links in the notices.
