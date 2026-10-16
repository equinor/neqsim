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
[#4212](https://github.com/equinor/neqsim/issues/4212). It is the legacy JAMA-based
solver; the production `TPflash` implementation uses `SysNewtonRhapsonTPflash`.
Application callers should migrate to `ThermodynamicOperations.TPflash()` so that
the normal stability and equilibrium orchestration remains in control.

This deprecation preserves the class, constructor, methods, serialized version
identifier and numerical implementation. It does not redirect existing calls or
claim equivalent intermediate states: the legacy solver uses logarithmic K-value
variables, whereas the current Newton solver uses Michelsen's u variables.
Direct users must compare their fluid cases and convergence behavior when migrating.

Removal remains a separate change after the required release-cycle/LTS deprecation
period and the breaking-change process above. Absence of in-repository callers
alone does not establish absence of external users. No removal date is promised.
