# Network and transfers
<!-- codemap-id: rw-plugin-oz-stargate/network -->

## Responsibility

Coordinate remote gate dialing and player transfer with the Stargate relay. Own local transfer and inventory custody data.

## Source and tests

- [`GateNetworkClient`](../../src/de/omegazirkel/risingworld/stargate/network/GateNetworkClient.java) — relay WebSocket client.
- [`TransferService`](../../src/de/omegazirkel/risingworld/stargate/transfer/TransferService.java) — transfer orchestration.
- [`TransferStore`](../../src/de/omegazirkel/risingworld/stargate/transfer/TransferStore.java) — local transfer state.
- [`InventorySnapshotStore`](../../src/de/omegazirkel/risingworld/stargate/inventory/InventorySnapshotStore.java) — inventory custody state.
- [Tests](../../src-test/de/omegazirkel/risingworld/stargate/) cover repository behavior. Verify relevant scenarios before implementing changes.
- [Repository index](../README.md) and [settings defaults](../../src/settings.default.json).

## Dependencies

- `rw-stargate-network/protocol` — WebSocket protocol for gate routing and transfer coordination. [Integration source](../../src/de/omegazirkel/risingworld/stargate/network/GateNetworkClient.java)
