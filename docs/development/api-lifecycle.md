---
title: Public API and schema lifecycle
description: Compatibility, deprecation, and migration rules for NeqSim public contracts.
---

# Public API and schema lifecycle

NeqSim public contracts include Java and Python APIs, JSON schemas, DEXPI
representations, MCP tools, command-line interfaces, and persisted model or
engineering packages.

## Compatibility rules

- Additive optional fields and methods are permitted within a major contract
  version.
- Required fields, identifiers, enum meanings, and existing method behavior are
  not removed or redefined within a major version.
- Readers reject unknown major schema versions instead of interpreting them as
  the current contract.
- Writers emit one declared schema version and include an explicit schema URI.
- Deterministic artifacts retain stable ordering and content fingerprints.

## Deprecation

Deprecation requires documentation of the replacement and migration path. A
deprecated public contract remains supported through at least one subsequent
minor- or major-version release after the deprecation is announced. A
patch-version release does not satisfy this minimum. For the current NeqSim
release workflow, count the minor- or major-version release when it is published
using the version set in the POM. Removal is a separate breaking change and
must follow the rules below, including a new major contract version.

An LTS release does not by itself extend this minimum period. If the contract
has an explicit LTS support commitment, keep supporting it for that LTS line
until its published end-of-support date, unless a security or correctness issue
makes that unsafe.

## Breaking changes

A breaking change requires an accepted NRC, a new major contract version,
migration documentation, compatibility tests, and release notes. Where
practical, NeqSim should provide a reader or converter for the immediately
preceding major version.

## Qualification boundary

API stability says that integrations can rely on a contract. It does not say
that the underlying calculation is qualified for a particular project,
standard, operating range, or engineering decision.

## Legacy TP-flash solver migration

`SysNewtonRhapsonTPflashNew` is deprecated following issue
[#4212](https://github.com/equinor/neqsim/issues/4212). It is the legacy solver
that originally used JAMA; the production `TPflash` implementation uses `SysNewtonRhapsonTPflash`.
Application callers should migrate to `ThermodynamicOperations.TPflash()` so that
the normal stability and equilibrium orchestration remains in control.

The public class, constructor and methods remain available. The later EJML
migration changed its internal matrix fields, so old JAMA-based serialized solver
instances are incompatible even though the version identifier is unchanged;
recreate those working solvers from their model inputs. Existing calls are not
redirected and intermediate states are not equivalent: the legacy solver uses logarithmic K-value
variables, whereas the current Newton solver uses Michelsen's u variables.
Direct users must compare their fluid cases and convergence behavior when migrating.

Removal remains a separate change after the required release-cycle/LTS deprecation
period and the breaking-change process above. Absence of in-repository callers
alone does not establish absence of external users. No removal date is promised.

## Numerical backend migration

Replacing internal JAMA calculations with EJML does not retire public Matrix
signatures or serialized state. Follow the
[JAMA to EJML migration and dependency retirement guide](jama-ejml-migration.md)
before removing the dependency. Its release gates apply independently to each
deprecated public contract.

## Legacy Aspen IP.21 historian integration

`AspenIP21Database`, `OnlineSignal`, and the measurement-device methods that
exposed or configured the legacy signal (`getOnlineSignal()`, `setOnlineSignal(...)`,
`isOnlineSignal()`, `setIsOnlineSignal(...)`, `getOnlineValue()`) were deprecated in
NeqSim 3.25.0 (issue [#4186](https://github.com/equinor/neqsim/issues/4186)), first
published in 3.26.0, and are removed in 3.30.0 after the required minor-release
deprecation period. The legacy path used the removed `sun.jdbc.odbc` bridge driver and a
placeholder connection string, so no working integration is lost.

Move historian acquisition, authentication, tag mapping, sample quality and
freshness checks into the application integration layer using a supported client
for the chosen historian. For NeqSim condition analysis, pass the checked numeric
sample and engineering unit to
`MeasurementDeviceBaseClass.setOnlineMeasurementValue(double, String)` and read it
with `getOnlineMeasurementValue()`. Offline calculated measurements remain available
through `getMeasuredValue(...)`.

Compatibility notes for 3.30.0:

- `serialVersionUID` values of the measurement-device classes are unchanged. Serialized
  models whose `onlineSignal` field was `null` (every model that never called
  `setIsOnlineSignal`) still deserialize; the removed field is skipped. A stream that
  contains a non-null `OnlineSignal` instance fails with `ClassNotFoundException` and
  must be recreated from its model inputs.
- The `onlineSignal` key is no longer written to the native instrument configuration of
  P&ID engineering packages (`PidNativeControlSystem`). Packages exported earlier with
  `onlineSignal: false` still validate and apply; a package declaring `onlineSignal: true`
  is rejected because that state can no longer be represented.
- Custom `MeasurementDeviceInterface` implementations must drop their `getOnlineSignal()`
  and `isOnlineSignal()` overrides.
