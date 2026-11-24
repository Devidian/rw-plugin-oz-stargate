# Gate UI and presentation
<!-- codemap-id: rw-plugin-oz-stargate/presentation -->

## Responsibility

Drive DHD interaction, horizon passage, visuals and audio.

## Source and tests

- [`DhdService`](../../src/de/omegazirkel/risingworld/stargate/dhd/DhdService.java) — DHD interaction.
- [`HorizonService`](../../src/de/omegazirkel/risingworld/stargate/horizon/HorizonService.java) — horizon passage.
- [`GateVisualService`](../../src/de/omegazirkel/risingworld/stargate/visual/GateVisualService.java) — gate presentation.
- [`GateAudioService`](../../src/de/omegazirkel/risingworld/stargate/audio/GateAudioService.java) — gate audio.
- [Tests](../../src-test/de/omegazirkel/risingworld/stargate/) cover repository behavior. Verify relevant scenarios before implementing changes.
- [Repository index](../README.md) and [settings defaults](../../src/settings.default.json).
