---
title: DEXPI engineering-diagram reference cases
description: Synthetic public simple, branched, and multi-area regression cases for canonical topology, DEXPI exchange, Proteus compatibility, and governed P&ID proposals.
---

# DEXPI engineering-diagram reference cases

NeqSim carries three synthetic public reference cases as executable regression evidence for the
coordinated professional PFD and DEXPI/P&ID campaigns. They establish a reproducible acceptance
baseline and exercise the additive `EngineeringDiagramDelivery` facade without changing the legacy
diagram exporters.

| Case | Source object | Topology | Material connections |
| --- | --- | --- | ---: |
| `DEXPI-REF-SIMPLE` | `ProcessSystem` | Feed, valve, separator, compressor, cooler, and product boundaries | 4 |
| `DEXPI-REF-BRANCHED` | `ProcessSystem` | Separator with gas-compression and liquid-pumping branches | 5 |
| `DEXPI-REF-MULTI-AREA` | `ProcessModel` | Inlet, compression, export, and flare-boundary areas | 7 |

The shared test fixture is `EngineeringDiagramReferenceFixtures`. It uses the SRK equation of state
with the classic mixing rule, a synthetic methane/ethane/n-heptane fluid, kg/h material flow, bara
pressure, and degree Celsius temperature specifications. The committed
`src/test/resources/dexpi/reference-cases/reference-case-manifest.json` pins those assumptions,
provenance, expected counts, engineering state, and qualification boundaries.

## Executable evidence

`EngineeringDiagramReferenceCasesTest` creates two fresh instances of every case and checks:

- converged material conservation in kg/h;
- deterministic canonical graph JSON, fingerprints, stable semantic identities, and exact material
  connection topology;
- native DEXPI 2.0 Process and Plant schema/profile validation for the two `ProcessSystem` cases;
- explicit, non-reused native Process material-port identities and structured loss diagnostics;
- deterministic Proteus-compatible engineering content for all cases and deterministic per-area
  sheets for the `ProcessModel` case, excluding generated emission date/time metadata;
- unchanged deterministic legacy combined and per-area DOT/Graphviz output for the multi-area case;
  and
- deterministic governed P&ID proposal models whose elements remain `REVIEW_REQUIRED` and whose
  completeness evidence remains not fit for construction.

`EngineeringDiagramDeliveryTest` reuses the same fixtures to check fail-closed single-area and
multi-area delivery of controlled semantic JSON, assessed DEXPI, native SVG/PDF, content hashes,
visual fingerprints, and structured projection losses.

Run the focused regression with the repository Maven wrapper:

```bash
./mvnw -Dtest=EngineeringDiagramReferenceCasesTest,EngineeringDiagramDeliveryTest test
```

## Explicit limitations

The cases do not claim ISO 10628 conformance, DEXPI EV certification, or interoperability with a
named commercial CAE product. Native DEXPI Process export remains area-scoped. The multi-area
delivery is a deterministic NeqSim package of independently assessed native area files plus explicit
plant-wide manifest evidence; it is not a native DEXPI whole-plant profile. Controlled document and
native SVG/PDF projections are delivered beside DEXPI and remain separate semantic and graphical
views.

The Proteus per-area files are compatibility sheets, not controlled drawings with paired off-page
references and accountable revision approval. Generated P&ID content is a proposal until piping,
valve, nozzle, instrument, safeguard, design-data, safety-lifecycle, and discipline reviews are
completed by accountable engineers.
