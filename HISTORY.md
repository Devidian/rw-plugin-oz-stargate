# History

## Unreleased

## 0.6.0 — 2026-10-08

- Let faction members share local gate addresses individually or through an all-address switch; personal gate names stay private to their owner.
- Migrate world-local address book entries for this server's gates when its network code changes, preserving other servers' entries under the old code.
- Keep locally known gates during relay snapshots after a code override, and resend migrated entries. Arrange DHD share controls in the action row and use stateful styles for their active state.
- Refresh the DHD from the current relay address-book snapshot before showing remote destinations.
- Keep DHD address-row frames separate from the hover-sensitive click surfaces so their border and background survive pointer movement and page changes.
- Restore a player's original visibility after a local Stargate trip even if its travel-screen callback is missed or interrupted by a disconnect or plugin reload; block overlapping passages until the trip finishes.

## 0.5.2 — 2026-10-08

- Allow the DHD to reopen after Escape closes its client window without reporting that close to the server.
- Place the DHD on the aimed floor point with overhead clearance checks in the command and radial menu.
- Orient the combined gate-and-DHD placement so the gate faces the DHD and the DHD's back faces the gate; align the arrival point with the gate's front.
- Build the plugin with Java 25 and the current PluginAPI 0.9.3.2 JAR.

## 0.5.1 — 2026-10-05

- Refill each gate's Discovery candidate pool from suitable free sites already found by other gates after a successful discovery, so an empty source pool can recover without waiting for its own scan.
- Skip occupied sectors during candidate scans, retry previously exhausted free sectors after a delay, and remove sites that fail revalidation from all pools. Discovery chance, cooldown, placement checks and persisted gate data are unchanged.

## 0.5.0 — 2026-10-04

- Let admins set gate aliases in the DHD. Players choose alias, local address or network address labels in personal settings; the local address is the fallback when known.
- Reposition DHD controls and prevent repeated interactions with one gate from closing its visible menu. Add a confirmed admin start-gate toggle backed by local SQLite state.
- Let administrators configure Discovery cooldown and an admin exemption, plus random or marked-start-gate first arrival. Sector 0,0 remains the fallback; cross-server arrivals are excluded.
- Add optional Discord channels for internal/external travel, successful Discovery and network status. Status messages explain network-code changes without disclosing the code.
- Synchronize aliases and legacy local-address metadata through OZ Stargate Network 0.5.0 while retaining existing gate IDs, world settings and protocol v1 compatibility.

## 0.4.1 — 2026-10-03

- Allow a Discovery started at a local gate to complete its seven-chevron dial and create a local destination while the relay network is disabled.

## 0.4.0 — 2026-10-03

- Show the travel tunnel for ten seconds during local travel and for a fresh ten seconds after arrival on another server. Players can disable it personally; arrival sound and visibility wait until travel ends.
- Bring first-time regular visitors through a local gate when one is available; transfers and later logins keep their existing arrival behavior.
- Discover gate addresses by entering a DHD chunk, synchronize the personal address book through the relay and allow manual dialing. Known local and remote destinations have distinct DHD button borders; local known destinations remain usable during relay outages.
- Add bounded background Discovery site scans, configurable chance and radius, five-minute persistent per-player cooldown and seven-chevron dialing before a successful discovery becomes known.
- Place a first gate and DHD in the global spawn sector on fresh installations when a safe site is found. Existing installations remain unchanged.

## 0.3.0 — 2026-10-01

- Add selectable spatial sound themes, a personal sound switch and live administrator volume control. Package only a reproducible `silent` theme with 17 Ogg files; administrators can install their own themes under `audio/<theme>/`.
- Align DHD lights, ring movement, chevron locks, opening, open loop, passage and shutdown cues with gate state. Local and network targets activate seven incoming chevrons at 400 ms intervals.
- Open the source gate visually as soon as the target accepts, while preventing passage until the target's 2.8-second activation completes. Local dials check the target at the seventh lock and preempt unfinished outgoing dials there.
- Play the passage cue for arrivals at the target gate. Hide travelers before teleport or inventory clearing and reveal them after successful arrival and clothing restoration; restore prior visibility after a failed outgoing transfer.
- Update the plugin's test-only SQLite JDBC dependency to a patched release.

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
