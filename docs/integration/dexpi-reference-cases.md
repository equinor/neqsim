---
title: DEXPI engineering-diagram reference cases
description: Synthetic public simple, branched, and multi-area regression cases for canonical topology, DEXPI exchange, Proteus compatibility, and governed P&ID proposals.
---

# DEXPI engineering-diagram reference cases

NeqSim carries three synthetic public reference cases as executable regression evidence for the
coordinated professional PFD and DEXPI/P&ID campaigns. They establish a reproducible acceptance
baseline without introducing a new production API or changing the legacy diagram exporters.

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
- deterministic Proteus-compatible output for all cases and deterministic per-area sheets for the
  `ProcessModel` case;
- unchanged deterministic legacy combined and per-area DOT/Graphviz output for the multi-area case;
  and
- deterministic governed P&ID proposal models whose elements remain `REVIEW_REQUIRED` and whose
  completeness evidence remains not fit for construction.

Run the focused regression with the repository Maven wrapper:

```bash
./mvnw -Dtest=EngineeringDiagramReferenceCasesTest test
```

## Explicit limitations

The cases do not claim ISO 10628 conformance, DEXPI EV certification, or interoperability with a
named commercial CAE product. Native DEXPI Process export remains `ProcessSystem`-only and reports
multi-area hierarchy, controlled document/sheet semantics, and drawing graphics as unsupported.
The native professional SVG/PDF drawing-set renderer is also not yet available.

The Proteus per-area files are compatibility sheets, not controlled drawings with paired off-page
references and accountable revision approval. Generated P&ID content is a proposal until piping,
valve, nozzle, instrument, safeguard, design-data, safety-lifecycle, and discipline reviews are
completed by accountable engineers.

