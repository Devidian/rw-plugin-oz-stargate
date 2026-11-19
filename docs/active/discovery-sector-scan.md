# Stargate discovery sector scan

- [x] Scan the central 4×4 chunks first, followed by the four quadrant centres.
- [x] Fall back to a shuffled chunk order once every eligible sector in range has completed the fixed probes.
- [x] Persist sector progress and candidate sites across reloads. Retire the progress when a gate occupies the sector.
- [x] Disable discovery at empty DHDs and re-enable it when a candidate becomes available.
- [x] Verify scan order, persistence, candidate availability, Maven package, API checks and Development activation.
- [ ] Verify the disabled-to-enabled DHD transition in the game.

Keep the existing terrain, placement, radius, cooldown and success-chance rules. Continue to issue at most one terrain probe per scheduler tick. The SQLite upgrade adds a table and trigger and uses existing candidate rows.

The search can exhaust a sector with unsuitable terrain. A saved candidate is checked again at placement time. If it is rejected, the scanner resumes from the saved progress.

Java 25 Maven package passed all 94 tests. API and entry-point checks passed. Development automatically reloaded and registered Stargate at 22:57 UTC on 2026-11-18. SQLite passed its integrity check. All 29 gates had five target sectors, so the empty-pool transition was covered by the automated store tests. The Development backup contains the previous JAR, translations and a consistent SQLite snapshot.

On 2026-11-19 the player confirmed normal gate operation on Development and approved the patch release. The in-game empty-pool check remains open.
