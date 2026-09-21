---
title: Neutral safety source-term exchange contract
description: Versioned JSON, newline-delimited live frames, SI quantities, composition, diagnostics, provenance and deterministic fingerprints for safety-simulator input.
---

# Neutral safety source-term exchange contract

`SourceTermFrame` packages a [release-flow result](release-flow-models) without depending on
downstream safety software. Version 1 uses `neqsim_safety_source_term.v1` and
`urn:neqsim:schema:safety-source-term:v1`. The Draft 2020-12 schema ships at
`neqsim/process/safety/release/schema/safety-source-term.schema.json` in the JAR.

## Consumer contract

Every frame carries scenario/source identity, calculation UUID, nonnegative sequence number,
UTC generation instant, simulation time, process provenance, status, diagnostics, and SHA-256
content fingerprint. Successful frames add release-model identity/version, thermodynamic-model
name, opening geometry, receiving pressure, mass rate, choking state, advective momentum rate,
station properties, and overall/phase composition.

All dimensional values use `{ "value": number, "unit": "SI token" }`. Pressure is absolute.
Composition maps explicitly distinguish mole and mass fractions. Native phase names retain the
aqueous and solid-like distinctions; no gas fraction is inferred from phase index.
Advective momentum is mass rate multiplied by orifice-exit velocity; it excludes pressure thrust.
Enthalpy and entropy retain the selected thermodynamic model's reference convention.

| Status | Numeric source payload |
|---|---|
| `VALID` / `VALID_WITH_WARNINGS` | Required. Inspect diagnostics and applicability. |
| `INVALID` / `UNSUPPORTED` | Absent. No substituted or previous flow value. |
| `STALE` | Absent. Current successful process state was not established. |
| `DISABLED` | Absent. Caller disabled the hypothetical opening; this does not assert zero equipment inventory. |

Version 1 emits `evidenceLevel: UNQUALIFIED`. Regression tests establish software behavior,
but do not create independently reviewed engineering qualification. Consumers must validate
both schema and application-specific physical/balance requirements before using a frame.

## Serialization and verification

Create a frame with `SourceTermFrame.calculated(...)`, supplying explicit time, identity,
request, result, and provenance. `SourceTermFrameTest` executes creation and every export API:

```java
String json = frame.toJson();
SourceTermFrame.verifyEnvelope(json);
String liveLine = frame.toNdjson();
String csv = SourceTermFrame.csvHeader() + frame.toCsvRow();
```

`toNdjson()` writes one self-contained compact JSON object plus LF. Consumers can process each
line without a session header. CSV is deliberately a reduced time-series view. Keep the full
JSON frames alongside CSV as its lossless manifest; empty CSV flow means unavailable, not zero.
The CSV header declares the units. String identifiers are quoted and escaped.

Object keys are recursively sorted; array order is preserved. Fingerprints hash UTF-8 compact
JSON excluding the `fingerprint` property, using the writer's numeric representation. The
algorithm is deterministic for NeqSim output; it is not an implementation of a cross-language
canonical-number standard. Reformatting object whitespace/order is accepted by `verifyEnvelope`;
changing numeric spelling may change the digest. Fingerprints detect content changes, not
authenticity. Transport authentication is an application responsibility.

`verifyEnvelope` checks version/URI and fingerprint only. It is explicitly not full JSON Schema
validation and does not deserialize a trusted thermodynamic result. The schema rejects unknown
major versions, wrong units, missing successful payloads, and numeric payloads on failure frames.
The writer additionally rejects nonfinite values, invalid unit direction vectors and malformed
identity/time fields. Phase/composition normalization is enforced by station construction.

Run `SourceTermFrameTest`, then `python devtools/validate_source_term_contract.py` in an
environment with `jsonschema` installed. The validator checks actual Java-emitted success,
located, invalid, unsupported, stale and disabled frames, plus deliberate schema corruptions.
The Python package is a validation dependency only; the Java producer uses existing core dependencies.

## Coordinates and live use

No source location is guessed. Call `withLocation(referenceFrame, positionM, direction)` to
create a new frame with three coordinates and a unit direction vector. The reference-frame
string must document origin and axes in the receiving study. Without this field the frame is
thermodynamic input only, not a complete spatial boundary condition.

UTC generation time and simulation time are separate: simulation stepping need not match wall
clock pacing. Sequence and calculation UUIDs expose gaps and allow downstream deduplication.
The [live process-integration layer](live-source-term-sessions) supplies model/source identifiers,
steady-state and transient stepping, and current-state checks. The frame class itself does not
run processes, open network connections or control equipment.

[Uncertainty ensembles](source-term-uncertainty) also use v1 frames. Their provenance declares
`sequenceMeaning=ENSEMBLE_CASE_INDEX`, with ensemble/case identifiers and probability weights.
These are alternative input cases at one simulation time, not consecutive live states.
Keep their JSON provenance when exporting reduced CSV; never integrate case order as time.

## Compatibility and scope

This additive schema does not reinterpret earlier source-term or CFD formats. Store the schema
version and model version with every saved study. Readers must reject unknown major versions.
Future changes follow the repository API lifecycle policy. No default uncertainty distributions,
isolation assumptions, coordinates, weather, ignition probabilities, or consequence models are
invented by this contract. This is the exchange layer of NRC-3860; see the
[implementation status](source-term-platform-status) for delivered and remaining capabilities.
