# Local gates and discovery
<!-- codemap-id: rw-plugin-oz-stargate/gates -->

## Responsibility

Own local sector gate placement, discovery, addressing and dialing.

## Source and tests

- [`DiscoveryService`](../../src/de/omegazirkel/risingworld/stargate/discovery/DiscoveryService.java) — discovery workflow. Candidate scans and reuse respect the active Rising World sector limit.
- [`GatePlacementService`](../../src/de/omegazirkel/risingworld/stargate/sector/GatePlacementService.java) — gate placement.
- [`LocalDialService`](../../src/de/omegazirkel/risingworld/stargate/sector/LocalDialService.java) — local dialing.
- [`AddressBookService`](../../src/de/omegazirkel/risingworld/stargate/addressbook/AddressBookService.java) — address book.
- [Tests](../../src-test/de/omegazirkel/risingworld/stargate/) cover repository behavior. Verify relevant scenarios before implementing changes.
- [Repository index](../README.md) and [settings defaults](../../src/settings.default.json).
