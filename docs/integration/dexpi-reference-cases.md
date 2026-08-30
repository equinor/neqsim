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

`DexpiVisualQualityAssessmentTest` expands the fixed rendering benchmark with the same simple,
branched, and per-area cases plus a mixer/heater/splitter/recycle/control-loop case and an isolated
empty topology placeholder. Every sheet is exported only through NeqSim's Proteus-compatible DEXPI
writer and rendered from its actual graphical primitives. The suite checks:

- exact Proteus `SchemaVersion 4.1.1` reporting without describing the document as native DEXPI
  XML 2.0;
- stable catalogue references, component identities, locations, line/curve/text primitives, SVG
  identity projection, and SHA-256 fingerprints;
- drawing bounds, duplicate IDs, minimum text-height risks, missing symbols, and empty SVG output;
- exactly one source-to-destination flow-direction arrow for every routed material segment, with
  solid fill preserved in the rendered SVG;
- every measurement function having a resolvable process-segment, nozzle, mount, piping-component,
  or equipment sensing location;
- separator and tank level functions referencing the vessel equipment boundary rather than an inlet
  or liquid-product nozzle, with invalid attachments counted separately;
- central/control-room symbols and matching location metadata for controller functions, plus a
  real tagged final element and directed actuating signal before a loop is classified complete;
- synthesized measurement proposals being both machine-identifiable and visibly marked `[PROP]`,
  without synthesized controllers or command lines;
- deterministic report JSON and SVG output across repeated exports; and
- preservation of an intentionally unconfigured stream through `ProcessSystem.run()` without
  inventing a fluid state.

The structural gate complements, but does not replace, full-sheet and readable-detail PNG visual
inspection. It distinguishes renderer/layout defects from missing source-model engineering data.

The flow-arrow regression records routed-segment, source-arrow, and rendered-filled-arrow counts.
Missing, duplicate, or renderer-dropped arrows produce stable findings instead of relying on visual
memory. This is a directional-readability gate, not a standards-conformance determination.

The first full-sheet benchmark inspection found that instrument bubbles intersected full equipment
data bars and sat outside the generated battery limit. The layout now reserves 55 mm above an
equipment centre for instruments and expands the battery-limit envelope through the highest bubble;
an exact coordinate regression protects both clearances. Re-rendered simple, branched, and
recycle/control cases show separated bubbles and data bars inside the boundary.

A subsequent whole-sheet instrumentation review found generic measuring lines terminating at
equipment centres, controller bubbles using field-location symbols, and synthesized controller
signals ending in empty process space. Measurements now resolve to process segments or nozzles,
their bubble groups follow the sensing-point lane outside equipment data bars, controller location
symbols are explicit, and closed-loop actuation is emitted only for an attached final control
element. The assessment records measurement, sensing-location, controller, complete-loop,
incomplete-loop, and synthesized-proposal counts so these defects fail deterministically in future
diagram work.

A vessel-level continuation found that separator measurements were associated with the liquid
product nozzle and tanks could not host a `LevelTransmitter`. Level measurements now use the
authoritative separator or tank liquid-level state, associate with the equipment identity, and start
their measuring line at the rendered vessel boundary. The assessment reports vessel-level function,
valid boundary-attachment, and invalid attachment counts; synthesized tank measurements retain
`[PROP]` and measurement-only provenance. This is a design-support projection, not a designed
process tap, transmitter technology selection, or approved loop.

The vessel-level bubble lane also clears the outlet-side continuation symbols instead of running a
vertical measuring line through a product connector. A coordinate regression reserves at least
45 mm between the vessel centre and each level bubble.

The same full-sheet review exposed a storage-tank roof arc whose endpoints did not meet the side
walls and whose radius spread far outside the equipment symbol. The catalogue arc now terminates at
both wall tops; exact start angle, end angle, and radius assertions protect that rendered geometry.

The compatibility metrics from the first topology review remain available alongside the stricter
DEXPI-oriented metrics: transmitter/controller counts, resolved and missing measurement attachments,
measuring-line validity, closed/incomplete loops, actuating-signal validity, and synthesized proposal
counts. Missing source data is reported separately from renderer/layout failures; the exporter does
not invent a nozzle, controller, valve, or operational intent to make a proposal appear complete.

Recycle connectivity is projected when the recycle block exposes a configured outlet through the
standard equipment outlet API and a downstream unit consumes that same stream identity. The benchmark
asserts the directed recycle-to-mixer connection in the Proteus XML before rendering. The writer does
not infer a return line from convergence state: an unconfigured outlet remains absent, and no fluid
state or connection is invented.

Run the focused regression with the repository Maven wrapper:

```bash
./mvnw -Dtest=EngineeringDiagramReferenceCasesTest,EngineeringDiagramDeliveryTest,DexpiVisualQualityAssessmentTest test
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
