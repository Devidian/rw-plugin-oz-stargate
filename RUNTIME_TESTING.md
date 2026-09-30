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

## Phase 5A: dialing sequence

1. Dial a remote gate: observe seven chevrons over roughly 14 seconds. During dialing, warp must fail without changing inventory. After the target reply, warp should work normally.
2. Dial an invalid/offline address from an idle source: seven steps, then failure, no wormhole and unchanged inventory.
3. Two players: A dials B, then B starts dialing A a few seconds later. A finishes first; B receives an interruption message and its gate becomes incoming. Only A can travel through the resulting wormhole.
4. Additional dials on an active source/target must fail. After 60 seconds, both gates close and can be dialed again. Cancelling native travel must still restore inventory.
5. Stop for player acceptance before DHD/UI work. Do not infer a tested game-process crash after claim from these tests.

## Phase 5B: DHD console

1. As admin, /sg binddhd <local gate ID>, interact with a placed usable object within 16m of the gate, then interact again. Only the DHD UI should open.
2. As a normal player, use the same object, refresh/select a remote address, dial and observe seven lit chevrons plus outgoing/open status. Double-click Dial: only one dialing sequence should start. Close and reopen the DHD while dialing; progress should remain correct.
3. After opening, close the DHD and use /sg warp <destination> for the unchanged journey. Native cancellation must still restore inventory.
4. Test /sg binddhd, /sg unbinddhd and /sg canceldhd as non-admin: denied. As admin, unbind the object and verify normal object interaction returns. Rebind for subsequent tests.
5. Remove a console or delete its gate: stale UI must not dial. Reopen after ordinary logout/login; persisted console still works. Precise reload persistence can be tested at a coordinated plugin reload.
6. With more than eight reachable remote gates, verify pagination; with none, an empty-state message. No test gates are created automatically for this UI check.

Stop for acceptance before event-horizon or model work.

## Phase 5C: passage zone (native acceptance pending)

1. Admin: set a zone at the intended passage with /sg sethorizon <local gate ID>, inspect the blue 30s preview. Set up each test server separately. /sg showhorizon <gate> repeats the preview. Test custom X/Y/Z dimensions; bounds follow world axes, not look direction.
2. Leave the zone. As a normal player, dial through the DHD, close the UI, wait for OPEN/outgoing, then walk into the zone. Exactly one native confirmation should appear. Decline: inventory/clothing return; staying/moving inside must not repeat the prompt. Leave and re-enter: a new attempt should work while the window is open.
3. Accept outbound travel and return using the other server's DHD/zone. Inventory/clothes must remain correct and arrival must not start another prompt. The incoming side must not allow reverse travel until the old connection closes and a new outgoing dial completes.
4. Enter while idle/dialing, then remain inside until OPEN: no travel. Leave and re-enter to travel. Repeat for an expired/offline connection; no inventory mutation.
5. Non-admin setup/show/remove commands must be denied. Admin setup beyond 16m, non-finite/out-of-range dimensions, missing gates and overlapping zones must fail. Removing a zone leaves its DHD/gate intact; re-create it afterward.
6. Ordinary logout/login inside must not trigger travel. After a coordinated plugin reload, show the zone again to verify persistence; a stationary player inside must not travel. Native reload and multiple-zone overlap need an explicit fixture; do not infer them from a two-gate test on different servers.

Stop for player acceptance. Gate artwork and command-access changes are outside this milestone.

## Post-5C command access (acceptance pending)

1. Non-admin: /sg gatelist, /sg dial <remote gate>, /sg warp <remote gate> each return admin-only, including while a wormhole is open. No dial or transfer should start.
2. Same non-admin: /sg help describes DHD/passage travel; use the DHD and zone for outbound/return travel normally.
3. Admin: help still lists debug travel and zone commands; gatelist/dial/warp remain available with their existing gate-state checks.

Stop for acceptance; this update does not import gate models.

## Phase 5D.1 static model preview (native acceptance pending)

1. Admin: on level ground, face roughly horizontally and run /sg previewgate. Step backwards to inspect the front; walk around to inspect back, ring opening, glyphs, nine chevrons and apparent scale (main ring about 6.1m).
2. Check ground contact and upright orientation for different horizontal viewing directions. Looking up/down must not tilt the gate. A neutral grey material is expected; no lighting/animation or final textures yet.
3. /sg previewgate replaces the previous preview; /sg clearpreview removes it. Automatic expiry after two minutes and disconnect/reload cleanup must remove it. It is visible only to the requesting admin and has no collider or travel trigger.
4. Non-admin preview/clear commands must be denied. Existing DHD/horizon travel remains functional and gate/zone/arrival coordinates remain unchanged.
5. Report visual artifacts, wrong scale/orientation, load delay or frame-time impact. Screenshot front/back if appearance is wrong. Native model import and client performance are not established by server reload.

Stop for visual acceptance before persistent placement and animation.

## Phase 5D.2: persistent model acceptance

1. As admin, clear any preview and stand on the intended ring bottom. Use `/sg placegate <local ID>`, then step backwards. Check scale and orientation; the ring stays longer than two minutes.
2. Place the same ID again at a different point/direction. Exactly one permanent model should remain. A nearby second player should see it without admin permissions.
3. Move more than 160 world units (roughly 80 m) away, then within 128 (64 m). The model should disappear/reappear within one second. Reconnect and verify placement is retained.
4. While no transfer confirmation is open, execute `rp`; after network readiness the model should return at the same transform, without duplicates. A second client should still see the model when the first disconnects.
5. `/sg removegatemodel <ID>` removes the model for nearby players; DHD binding, gate registration, arrival and horizon remain usable. Place again for subsequent alignment work.
6. Normal players cannot place/remove models. Unknown gate IDs and vertical facing cannot create a placement. Temporary `/sg previewgate` and its cleanup still work alongside permanent models.
7. Gate unregister cleanup may be checked on a disposable gate only; SQLite tests already exercise its cleanup trigger. Do not delete established test gate IDs just to test rendering.

Log checks establish plugin activation only. Native visibility, lifetime and performance require player acceptance.

## Phase 5D.3: explicit alignment

- On both test servers, place or retain a model. Stand on walkable ground/platform centred 1–4m in front of the model; `/sg aligngate <ID>`. Confirm the bounding preview faces the same direction as the ring. A rear/too-near/too-far arrival point is rejected.
- Dial through the DHD, walk through the model opening. Travel should use the new zone, with arrival on the chosen destination point facing away from the front of the destination ring. Inventory/clothing and return travel must remain correct.
- Decline a transfer, exit the thin zone and re-enter; confirmation can be requested again within the same outgoing window.
- During dialing/open/active transfer, alignment and horizon editing are blocked. Model move/removal is blocked while aligned. When idle: removehorizon, placegate at a diagonal angle, aligngate and repeat passage/arrival tests.
- `rp` and relog retain arrival/zone orientation and the accepted persistent-model recovery behavior. Native frame is a rotated bounding rectangle; its corners do not trigger the circular passage.
- Legacy unaligned zones retain their old axis-aligned feet-point behavior. Updating the plugin must not align/move them automatically.

## Phase 5D.4 — visual dialing

Use existing placed/aligned gates; no new setup is required.

1. Start an outgoing dial through the DHD. Check inner-ring motion and seven progressive
   amber chevrons, with the top chevron last. DHD progress must still update.
2. Observe the destination when possible: incoming state stops an outgoing visual sequence;
   after OPEN the seven active chevrons stay brighter. A third gate is needed to separately
   exercise incoming-priority preemption; do not claim that scenario from two-server dialing.
3. If a dial fails (for example an unavailable destination), ring/chevrons must reset;
   a failed attempt must never display OPEN. Closing/expiry also resets both ends.
4. Check that passage and arrival still match the model. Decline/exit/re-entry remains usable.
5. Run `rp`, wait up to 15 seconds for presence repair, then dial again. Check all model parts
   return together and no duplicate frames appear. Relog/proximity re-entry must also work.

No wormhole surface is included yet. Native animation and material readability require
player acceptance; unit tests cover only state mapping and asset checks cover rest geometry.

## Phase 5D.5 — slower ring and moving chevrons

Use the existing DHD and aligned gates. Check the slower roughly 25-second outgoing
sequence, individual block engagement (top last), and release after connection expiry.
Observe both ends if possible. After `rp` or relog, check all parts return in the current
state and the next dial still animates. A failed dial must release all blocks; interruption
must not trigger a later stale engagement. No additional placement commands are needed.

## Corrected V-chevron acceptance

The previous 5D.5 visual test failed. Test the corrected sequence on both gates:
1. Ring visibly accelerates, slows and stops before each V stroke.
2. V (not centre block) moves inward, its strips illuminate at the inward stop, then it returns outward.
3. Whole housings/centre blocks stay neutral. Previously locked Vs remain at their home position.
4. After closure/failure lights go dark; interruption cannot cause a later stale stroke/lock.
5. After rp/relog all parts return; late viewing during a dial follows current phase.

The roughly 35-second default sequence includes seven 5-second steps. Incoming handling
still follows relay priority; no visual timer opens a connection or authorizes travel.

### Chevron lighting refinement

During a dial, check that each confirmed lock lights both the V strips and the inner
surface of its fixed outer part. At OPEN both should brighten again. On closure both
return dark. The V movement and ring timing should match the previously tested sequence.

## Phase 5E.1 — wormhole surface and opening

1. Dial the existing gate. No surface while dialing; upon OPEN the surface appears and a
   brief surge extends toward the front, retracting into a moving blue/silver water surface.
2. Inspect the surface on both sides: fits the inner rim, no rectangular texture borders or
   visible sorting flashes. It must not obscure the chevrons outside the opening.
3. Travel/decline/re-enter as before. The visual has no collider and adds no travel permission.
4. After normal closure the surface dissolves irregularly from outside inward over 0.6 seconds, reaching 80% white blend at 0.3 seconds; the surge stops. Connection loss hides everything immediately. A failed dial must
   never show water. Incoming OPEN displays the same surface on the destination.
5. Move out of model range/relog into an already open connection: no new opening surge.
   Run rp and check full model recovery and another dial. Resetting the plugin may close
   the connection via the existing relay disconnect handling.

Native material illumination, opening shape and smoothness require player acceptance.

### Collapse refinement

Check the longer forward surge and the normal shutdown collapse. Once the connection closes,
travel is already disabled even during the brief visual collapse. A new dial or network loss
must cancel any previous closing tail. Chevron light timing and gate texturing are deferred
at the player's request until the final visual review.

### Reference water loop

Compare to supplied SG_Puddle.png/PuddleLoop.mp4: bright central caustics and dark blue rim,
patterns morph rather than the disk rotating. Observe at least25seconds (two full loops),
including atlas changes every5.83s, from both sides. Watch for missing/flashing frames,
black rectangles, tiling seams, first-loop stalls and excessive blur at close range. Verify
peak white blend at0.3s, irregular alpha erosion without uniform disk scaling, 0.6s total shutdown, travel/decline and rp recovery as before.

### Kawoosh hold / rear return

Observe from the side: front extends35% farther than the accepted2.8m peak (now3.78m),
stays at maximum for0.4s, retracts, then a smaller twisted vortex briefly extends behind
(+Z) and settles by2.65s after OPEN. Check the rear view too. No replay after entering
model range late/relog; closure/offline cancels either protrusion. Accepted water/dissolve,
travel/decline and rp recovery must remain intact.

### Chevron finish / collision

1. Observe V inward -> both authored lights on -> V returns, seven symbols, brighter OPEN.
2. Check ordinary dialing smoothness;30Hz is an improvement attempt, not proven stutter repair.
3. Walk against both side frames/front/back: solid geometry should block. Walk through the
   clear centre without a connection and with an outgoing OPEN connection; normal travel,
   decline/leave/re-entry remain usable. Water and Kawoosh must not push/block the player.
4. Run rp/relog: model and collider should recover together, with no invisible old barrier.
   Check first-load stalls and placement orientation. Collision approximates the ring; decorative protrusions have no separate collision.

### Gate materials

Inspect in daylight and shade, front/back/side and close to the glyph ring. Check mottled
surface detail, glyph readability, excessive darkness, stretching/seams and first-load stalls.
Dial once: texture must move with ring/V; light strips/water remain as accepted. Frame blocks,
centre stays clear. Check rp/relog recovery; native OBJ importer errors remain an open issue.

## Persistent DHD model (Development/Demo player test)

1. Admin: stand by an existing local gate, `/sg placedhd <gate ID>`; inspect scale, orientation,
   material, collision and all visible keys from several angles. Use `/sg placedhd` again at
   another nearby position: exactly one model should remain. More than 16m away is rejected.
2. Normal player: interact with the model within 2m. Existing DHD menu should open; select a
   remote gate and dial once. From farther away the menu must not open. Non-admin placement
   and removal commands must be rejected. Legacy bound-world-object DHD still works.
3. Close/reopen the menu while dialing and after wormhole closes. Removing the model while
   its UI is open must make that session unavailable; existing world-object links remain.
4. `rp`, logout/relog and plugin reload must show one model at the saved position. Check the
   two test servers independently. Deleting a registered gate removes its model row; only
   do this on a disposable test gate. Confirm travel/inventory after the visual test.

## Supplied DHD visual candidate

After test-server activation, inspect the already placed Development DHD from front,
side and above: authored keypad slope should face the player, glyphs and central dome
should remain visible, base should meet ground, collision and DHD menu must work.
`rp` must not duplicate/disappear the model. Demo remains unplaced until its own test.
Compare with the accepted parametric screenshot in
`local.res/stargate-assets/dhd-model-first-placement.png`; report any missing parts,
wrong scale, shading or keyboard orientation before treating the replacement as accepted.
## Phase 6: local sector travel (Development)

1. Audit the existing world's gate sectors and transfer journals before
   deployment. If two existing gates share a sector, resolve that conflict
   explicitly; migration must not choose one or delete either gate.
2. Confirm existing gate IDs, models, DHDs and passage zones survive the
   automatic sector indexing. Registration of another gate in an occupied
   sector must be denied before any relay ID is reserved.
3. On a fresh world, `network.enabled=false` must block cross-server dialing
   while local travel works. New gate registration still requires the relay
   and receives a globally unique ID. On the two established test worlds,
   explicitly enable network travel for the cross-server regression.
4. Test local dial, seven-chevron animation, passage, arrival and return with
   unchanged inventory/clothing. Repeat with the relay disconnected; new gate
   registration must fail, existing local travel must continue.
5. On a separate empty sector, run `/sg placegate` without an ID. Confirm one
   relay-reserved ID, one model lowered into the ground, one aligned passage and
   an arrival point about 1.2 m in front of the model facing away from it.
   Repeated placement in that sector must fail without creating another ID.
   The older `/sg placegate <ID>` remains available for manual correction.
6. `/sg showhorizon <ID>` should draw a blue preview centred on the visible
   aperture, including when the model is rotated off world axes.
