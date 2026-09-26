# Development player test: inventory experiment

1. On `rw-server-dev`, confirm the server log shows OZStargate enabled after plugin reload.
2. As an administrator, place distinct items in regular inventory, hotbar, and equipment slots, and wear distinct clothing. Run `/sg pack`. Confirm **all inventory slots and worn clothes are empty**.
3. Run `/sg pack` again; it must refuse to replace the stored set. Run `/sg unpack`; confirm exact items, stacks, positions, equipment, and worn clothes return.
4. Run `/sg unpack` a second time; it must refuse and must not duplicate anything. Repeat the full cycle across `reloadplugins` to verify SQLite persistence.
5. While packed, put one test item into inventory or wear one garment. `/sg unpack` must refuse until both inventory and worn clothes are empty again.
6. Run `/sg unpack` without a prior pack as a different administrator; confirm a localized missing-snapshot message and unchanged inventory. A copy-only snapshot from the previous build must be rejected.
7. As a non-admin, run `/sg pack` and `/sg unpack`; confirm denial and unchanged inventory.
8. If an interrupted operation reports a recovery warning, inspect the server log and use `/sg recover`. If the state cannot be identified exactly, leave the custody row untouched for manual review.

Record gameplay acceptance in the root active plan. A successful build or plugin reload alone does not prove inventory restoration.

# Development player test: gate discovery and dialing

1. Confirm the relay responds on `wss://sgn.omega-zirkel.de/ws` and the plugin status shows connected on both Development and `rw-demo`.
2. As an admin on each server, run `/sg registerGate` and record the returned gate IDs. `/sg gatelist` must list both while both servers are online.
3. Run `/sg dial <remote gate ID>` from a server with one local gate. The target server should announce an incoming wormhole; the origin should receive the target host/port.
4. Dial the same target again within one minute. It must report busy. Dial a gate on the same server and a nonexistent ID; both must fail.
5. Stop or disconnect one test server. Its gate must disappear from `/sg gatelist`, and dialing it must fail. Reconnect and verify it reappears without re-registering.
6. Historical phase-2 access was admin-only. Phase 4 supersedes access to gatelist/dial; registration and removal remain admin-only. Inventory custody must still work independently.

The player accepted these phase-2 behaviors on 2026-09-26. Phase 3A is now deployed for its focused test; phase 3B transfers remain gated on that acceptance.

# Development player test: phase 3A observations and game mode

1. On both Development and `rw-demo`, confirm the plugin reconnects and `/sg gatelist` still shows the existing gate IDs.
2. Join each server with the same test account. As an admin, run `/sg trust` on the server where you are logged in. The response should show one observation for each visited server, including seconds played and the server-local permission group. A new test account should have no observations on the other server until its first visit.
3. With `forbiddenActions.ChangeGameMode=true`, attempt a game-mode change. The change must be cancelled and a localized native error dialog displayed. Check that no inventory or clothing changed.
4. If needed for normal admin activity, set `forbiddenActions.ChangeGameMode=false` in the world settings, reload the plugin settings, and confirm game-mode changes work again. Return it to `true` for the test.

This phase-3A player test was accepted on 2026-09-26; continue with the phase-3B test below.

# Development player test: phase 3B transfer and return

Phase 3A was accepted on 2026-09-26. Phase 3B is deployed for this controlled test. Use ordinary test items and distinct clothing; keep the same account connected to only one test server at a time. Both servers' Player.db and Stargate files were backed up before deployment.

1. On `rw-demo`, prepare a recognizable target inventory and clothes, then log out. On Development, prepare a different source set. Record both sets.
2. As admin on Development, `/sg dial <demo gate>` followed by `/sg warp <demo gate>`. Decline the native server-change prompt. Source inventory and clothes must return exactly once. `/sg unpack` must not recreate the transfer snapshot.
3. After the one-minute gate reservation expires, dial again, run `/sg warp <demo gate>`, and accept. Confirm arrival at the existing demo gate with its saved orientation, source inventory, and source clothes. Health, hunger, thirst, and stamina should follow within target bounds. Demo's previous items must not be merged into the travelling set.
4. On demo, dial the Development gate and run `/sg warp <development gate>`. Accept and confirm the travelling set returns to Development without duplicates.
5. Log out and join demo normally through the server list. Its original pre-visit inventory and clothing must be restored once. Repeat a normal login to confirm they are not duplicated.
6. Report the first failing step and message. If the plugin disconnects the player because state is ambiguous, stop and inspect the journal; do not delete databases or manually replay snapshots.

Stop for player acceptance after this basic path. A real game-server restart during travel and deliberately delayed/failed connections require a separately coordinated runtime test with a player present; relay crash/restart, expiry, duplicate packets, and SQLite reopen already passed isolated checks.

Player result: all four requested behaviors passed (decline restoration, outbound inventory/clothes, return travel, original destination inventory restoration). Arrival orientation was plausible but not conclusively checked. Controlled source restart during the confirmation dialog has since passed: relay ABORTED, source OUT/ABORTED, target IN/CANCELLED; the player confirmed complete restoration and no duplicates on repeat login. A controlled source restart does not establish safety for a forced game-process crash after target claim.

## Phase 4: non-admin command access

1. Use an account without admin rights on both designated test servers. Check /sg help and /sg gatelist.
2. Dial the remote gate, warp, and return. Verify inventory and clothes travel once, and local arrival is correct. Decline one prompt and verify restoration.
3. Try /sg registerGate, /sg unregisterGate <existing ID>, /sg trust, /sg pack, /sg unpack, /sg recover as that non-admin. All must return the localized admin-only message without changing gates or inventory.
4. Stop for player acceptance. No release or production deployment is included in this milestone.

Player acceptance on 2026-09-26: all phase-4 test groups passed without admin rights (listing/travel/return, cancellation restoration, and denial of all management/debug commands).
