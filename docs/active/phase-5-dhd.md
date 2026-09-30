# Phase 5B: placed-object DHD

## Objective / ownership

Plugin-only change using OZ Tools 0.26.2 BasePluginOverlay/AdvancedButton. Reuse relay address discovery and phase-5A state, with no protocol change or extra dependency. Entry class remains sole Listener and only dispatches events.

## Decisions

- Admin /sg binddhd <local gate> arms the next world-object interaction for 60 seconds. /sg unbinddhd arms removal of that binding; /sg canceldhd cancels selection. Recheck admin status on interaction.
- Existing placed interactable objects are temporary consoles; no model import or invented crafting item. Store native object ID, chunk coordinates, creation time/type and gate association in world SQLite. Bindings survive reload. Existing binding cannot be overwritten silently.
- Require interaction/click distance <=4m to console, and console within 16m of gate when binding. UI actions recheck object identity, binding, gate existence and player proximity. No remote radial-menu dialing through DHD.
- UI lists remote gates, eight per page, with selection, refresh, dial, gate status and seven-chevron progress. Local gates are excluded. Dial rechecks authoritative source state; repeated/stale callbacks cannot dispatch repeated requests.
- Only list/dial through UI in 5B. Existing warp chat command remains until 5C adds the event horizon. Closing UI never cancels an active wormhole or alters inventory.

## Migration / rollback

Additive stargate_dhds table and gate-delete cleanup trigger; do not modify inventory/transfer schemas. Back up SQLite and previous plugin artifacts before test deployment. Rollback only plugin JAR/i18n, leaving the additive table intact. Removed/replaced objects fail identity validation; admins can bind replacements. No server restart required.

## Validation

- [x] Store tests: persistence/reopen, object identity, duplicate prevention, missing gate, gate-delete cleanup.
- [x] Maven package/tests (six tests), API signatures and entrypoint check.
- [x] Reload both designated test servers and prove network-ready; relay unchanged. DHD table confirmed on both.
- [x] Player acceptance: bind/use as normal player, select/page/refresh/dial, progress/close/reopen, admin denial and stale/distant console behavior.

Stop for acceptance before event-horizon implementation or disabling player commands. No public release in this milestone.

## Accepted

On 2026-09-26 the player confirmed all tests successful after the absolute-position layout fix. The DHD milestone is complete. Pagination with more than eight remote gates has no separately reported native evidence. Phase 5C is tracked in phase-5-horizon.md; player warp commands remain available until that workflow is accepted.
