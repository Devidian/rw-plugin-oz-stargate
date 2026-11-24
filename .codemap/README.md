# rw-plugin-oz-stargate
<!-- codemap-id: rw-plugin-oz-stargate -->

## Purpose

Local and cross-server Stargate travel, player UI and transfer custody. [Descriptor](../src/resources/plugin.yml) names [`OZStargate`](../src/de/omegazirkel/risingworld/OZStargate.java); [`StargatePluginRuntime`](../src/de/omegazirkel/risingworld/stargate/runtime/StargatePluginRuntime.java) handles lifecycle and dispatch.

## Modules

- [Network and transfers](modules/network.md).
- [Local gates and discovery](modules/gates.md).
- [Gate UI and presentation](modules/presentation.md).

## Build and tests

[Maven](../pom.xml) builds against the Rising World API and OZ Tools. Explore [tests](../src-test/de/omegazirkel/risingworld/stargate/) and follow [repository instructions](../AGENTS.md) for validation.

## Dependencies

- `rw-plugin-oz-tools/shared-api` — build and shared runtime infrastructure. [Build configuration](../pom.xml)
