# History

## Unreleased

## 0.2.0 — 2026-09-30

- Add one persistent gate per sector, local sector addresses and local travel; gate and DHD placement, repositioning and removal are available to administrators in the radial menu.
- Add a placed DHD with direct interaction, paged destination selection and seven-chevron dialing. Ordinary players enter an open gate to travel; chat travel commands remain administrator debug tools.
- Add persistent, colliding Milky Way gate and DHD models with animated chevrons, ring, water surface, opening surge, shutdown effect and night lighting. Include model source and license notices.
- Make cross-server travel opt-in with `network.enabled=false` by default. The relay still reserves globally unique gate addresses.
- Reject a new incoming transfer before source inventory clearing while its target holds an earlier visitor inventory. Preserve blocked inventory for review instead of overwriting it.
- Skip the held visitor-base snapshot for an empty target inventory and empty clothing, so returning gear to an otherwise empty home server does not leave a pointless inventory lock.
- Apply admin changes to `network.enabled` to the relay immediately and show PluginSettings labels and descriptions in the player's selected language.
- Add a per-player Stargate shortcut visibility setting.
- Announce incoming wormholes only in the gate's sector and make other Stargate chat messages opt-in through a per-player debug setting.

## 0.1.0 — 2026-09-26

- First command-based Stargate release, based on the OZ Maven template, Java 20, PluginAPI 0.9.3.2 and OZ Tools 0.26.2.
- Register durable gate addresses with local arrival position and orientation; discover and dial online remote gates through protocol v1.
- Let all players list gates, dial and travel; restrict registration, removal, trust inspection and inventory debug commands to administrators.
- Transfer inventory, worn clothing and supported vital values using SQLite custody and a MongoDB release/claim journal. Preserve prior destination inventory for ordinary login after return.
- Recover declined/expired unclaimed transfers without repeated inventory application; leave uncertain claimed transfers blocked for review.
- Persist player observations and optionally prevent game-mode changes. Deterministic network grouping is not authentication.
- Validate native travel, return, cancellation, source restart recovery and non-admin permissions on two designated test servers.
