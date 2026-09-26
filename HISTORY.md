# History

## 0.1.0 — 2026-09-26

- First command-based Stargate release, based on the OZ Maven template, Java 20, PluginAPI 0.9.3.2 and OZ Tools 0.26.2.
- Register durable gate addresses with local arrival position and orientation; discover and dial online remote gates through protocol v1.
- Let all players list gates, dial and travel; restrict registration, removal, trust inspection and inventory debug commands to administrators.
- Transfer inventory, worn clothing and supported vital values using SQLite custody and a MongoDB release/claim journal. Preserve prior destination inventory for ordinary login after return.
- Recover declined/expired unclaimed transfers without repeated inventory application; leave uncertain claimed transfers blocked for review.
- Persist player observations and optionally prevent game-mode changes. Deterministic network grouping is not authentication.
- Validate native travel, return, cancellation, source restart recovery and non-admin permissions on two designated test servers.
