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
deprecated public contract remains supported for at least one normal release
cycle and, when included in an LTS release, until the next LTS line unless a
security or correctness issue makes that unsafe.

## Breaking changes

A breaking change requires an accepted NRC, a new major contract version,
migration documentation, compatibility tests, and release notes. Where
practical, NeqSim should provide a reader or converter for the immediately
preceding major version.

## Qualification boundary

API stability says that integrations can rely on a contract. It does not say
that the underlying calculation is qualified for a particular project,
standard, operating range, or engineering decision.
