# Aqli Optimization Plugin Core

Shared SPI and lifecycle utilities for Aqli inference optimization plugins.

## Responsibility

This module owns the canonical optimization-plugin contracts and shared runtime
configuration/registry types. Feature modules should depend on these types rather than
copying interfaces or configuration classes into their own packages.

Runtime policy remains in Aqli. Reusable tensor operations, attention kernels, and device
primitives belong in Aljabr when they can be used independently of Aqli.

## Build

Run commands from the repository root. The Gradle project path is
`:plugin:aqli-plugin-optimization-core`.

```bash
./gradlew :plugin:aqli-plugin-optimization-core:build
```

Use `./gradlew projects` if the local checkout does not expose that project path.

## Integration status

A module depending on this SPI is not automatically enabled or connected to generation.
Before enabling an optimization plugin, verify that it implements this canonical API,
passes its module tests, is registered through the supported plugin lifecycle, and is
called from an inference-level path.

In particular, `runtime/optimization/aqli-plugin-fa3` currently contains legacy copies of
some optimization contracts. Do not enable it until those types are migrated to this
module or an explicit compatibility adapter is introduced.
