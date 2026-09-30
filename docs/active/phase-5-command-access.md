# Phase 5: admin debug commands after horizon acceptance

Status: implementation/package complete and activated on both test servers; player acceptance confirmed on 2026-09-27.

- [x] Require admin before dispatching gatelist, dial and warp chat commands, alongside existing gate-management/trust commands.
- [x] Keep DHD listing/dialing and horizon-triggered travel available to ordinary players; guard is at chat dispatch only.
- [x] Split DE/EN help by role. Normal-player help explains DHD/horizon travel; admin help includes debug travel and zone setup.
- [x] Package and ten existing tests pass; entrypoint/whitespace checks pass. No new test framework for this small dispatch change.
- [x] No active transfers before rollout; artifacts backed up. Both designated test servers reloaded and rejoined the network; identical class content and preserved gate/DHD/horizon records confirmed.
- [x] Native acceptance: normal player denied gatelist/dial/warp, normal DHD/horizon travel still works, admin retains debug commands and appropriate help.

No schema, relay or dependency change. Roll back JAR/i18n if necessary; retain all world data. Gate assets are audited separately; no model payload accompanies this update.
