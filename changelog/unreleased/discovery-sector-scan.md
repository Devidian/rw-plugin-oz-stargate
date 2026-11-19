# Discovery sector scan

- Scan 16 central chunks, then 64 chunks across the quadrant centres. Scan remaining chunks in a stable, randomized order if the fixed probes produce no eligible site in the radius.
- Persist scan progress and sites in world SQLite across reloads. Placing a gate retires the scan state for that sector.
- Disable discovery at a DHD while its candidate pool is empty and refresh the button as candidates become available. Empty pools do not consume an attempt or start a cooldown.
