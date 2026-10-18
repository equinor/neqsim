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

Since NeqSim 3.25.0, `AspenIP21Database`, `OnlineSignal`, and the measurement-device
methods that expose or configure the legacy signal are deprecated (issue
[#4186](https://github.com/equinor/neqsim/issues/4186)). Constructors, method
signatures, serialized field layouts and `serialVersionUID` values are retained.
Existing connection attempts and read/failure behavior are unchanged. Deprecation
does not certify the legacy JDBC/ODBC path on current JVMs or provide a new
historian connector.

Move historian acquisition, authentication, tag mapping, sample quality and
freshness checks into the application integration layer using a supported client
for the chosen historian. For NeqSim condition analysis, pass the checked numeric
sample and engineering unit to
`MeasurementDeviceBaseClass.setOnlineMeasurementValue(double, String)` and read it
with `getOnlineMeasurementValue()`. This input does not populate `OnlineSignal`,
implement its timestamp/connection behavior, or change the legacy `getOnlineValue()`
method. Applications using that method must migrate their acquisition/read path
explicitly. Offline calculated measurements remain available through
`getMeasuredValue(...)`.

No replacement JDBC query API or automatic sample adapter is introduced. Retain
existing integrations during migration and qualify the application's sample
mapping separately. Deleting these public types or methods requires the release
period and breaking-change process above; this deprecation does not schedule
removal in a patch release.
