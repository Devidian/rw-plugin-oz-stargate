### Added

- Registered gates automatically own their server-local sector address; a
  second gate in the same sector is rejected. Existing gate IDs are preserved.
- Cross-server travel has an admin setting, off by default. The relay remains
  available to reserve globally unique gate IDs when new gates are created.
- `/sg placegate` now reserves the ID and stores the model, aligned passage
  and forward-facing local arrival in one transaction. New model placements
  sit 1.2 m lower; the aligned passage preview is centred on the aperture.
