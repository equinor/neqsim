---
title: safety
description: safety documentation for NeqSim
---

{%- include_relative README.md -%}

## Safety functions, reliability, and facility response

- [Closed-loop SIF verification](closed-loop-sif-verification.md): execute detection,
  voting, logic delay, and final-element response in an isolated transient.
- [HAZOP and LOPA to draft SRS handoff](hazop-lopa-srs-handoff.md): trace supplied
  hazard and risk inputs through IPL eligibility, LOPA arithmetic, and an unapproved SRS draft.
- [Integrated facility safety response](integrated-facility-safety-response.md): join
  executed ESD, compressor-trip, relief, blowdown, flare, and process-limit evidence.
- [Safety change revalidation and independent benchmarks](safety-change-revalidation-and-benchmarks.md):
  propagate governed model changes and qualify methods against controlled references.
- [SIF reliability uncertainty and degraded modes](sif-reliability-and-degraded-modes.md):
  assess seeded PFD/PFH uncertainty, bypass, maintenance, failure, and proof-test states.

## Compressor controls and shutdown studies

- [Compressor automation workflow](compressor-automation-workflow.md): native steady and
  dynamic process, canonical controls, declared fault cases, configuration readback, and
  DEXPI 2.0.1/P&ID feedback.

## Passive pressure relief

- [PSV simulation and P&ID feedback](psv-pid-verification.md): native opening,
  reseat, and flow response with configuration readback and engineering review gates.

## Source-term models and live sessions

- [Neutral source-term exchange contract](source-term-contract.md): versioned JSON,
  SI quantities, diagnostics, provenance, and deterministic fingerprints.
- [Live source-term sessions](live-source-term-sessions.md): connect steady and
  transient process models to versioned safety-source frames.
- [Coupled release and inventory depletion](coupled-release-inventory.md): couple
  rigid inventories to selected release physics and conservative balances.
- [Model-explicit release source terms](release-flow-models.md): equilibrium,
  finite-rate, drift-flux, and ideal/real-gas release models.
- [Source-term uncertainty](source-term-uncertainty.md): weighted joint scenarios,
  reproducible frames, quantiles, and validation evidence.
- [Source-term platform status](source-term-platform-status.md): delivered
  capabilities, evidence boundaries, and remaining acceptance criteria.

## Scenario generation and dispersion

- [Automatic scenario generation](scenario-generation.md): generate safety
  scenarios from process data and configured event definitions.
- [Release and gas-dispersion scenarios](release-dispersion-scenarios.md): generate
  release source terms, screening endpoints, and CFD handoff cases.
