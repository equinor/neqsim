---
title: DEXPI physics-bridge evaluation
description: Independent evaluation of NeqSim as a bridge between physics simulation and DEXPI in both directions, with measured evidence, fixes, and a prioritised roadmap.
keywords: "DEXPI, P&ID, round trip, DexpiSimulationBuilder, pyDEXPI, professional P&ID, process simulation, roadmap"
---

# DEXPI physics-bridge evaluation

**Goal:** NeqSim should be the strongest tool for connecting physics simulation to DEXPI. It should
turn a simulation into a professional P&ID, and turn a P&ID into a runnable model for physics
studies.

This page records an independent, executed evaluation of current master (21 November 2026) against
that goal. It complements the [current-master audit](dexpi-pid-current-master-audit.md), which is a
source inventory. Here every claim comes from running the code on a realistic case and, where
possible, checking the output with an independent consumer (pyDEXPI 2.1.0).

## Evaluation case

The evaluation used an HP/LP separation train with recompression: a 50 t/h well fluid feed (SRK,
12 components), an inlet heater, an HP separator, and a gas cooler and scrubber. It also has an
export compressor (120 bara) and two liquid letdown valves into a mixer and the LP separator, an
oil pump, and LP gas recompression recycled to the HP gas line through a `Recycle`. In total there
are 17 units with one material recycle. A three-phase separator with a three-way splitter, and the
official DEXPI example `C01V04-VER.EX01.xml` from a third-party tool, were used as extra cases.

## Scorecard

| Capability | Result | Evidence |
| --- | --- | --- |
| Simulation to Proteus P&ID XML | Good | 17 units, 16 lines, operating P/T/flow on 31 items, design data, recycle closure kept; 0.2–0.4 s |
| Simulation to native DEXPI 2.0.1 Plant | Good | Schema/profile conformant; pyDEXPI strict load with 0 errors |
| Simulation to native DEXPI 2.0.1 Process | Blocked for recycle models | Conformant and pyDEXPI-clean without the recycle; rejects any `Recycle` unit |
| Simulation to native DEXPI 2.0.0 Process | Defect | Reported CONFORMANT, but omits the mandatory `Method` on `Compressing` and `Cooling`; pyDEXPI cannot instantiate 5 of 13 process steps |
| Simulation to native DEXPI 2.0.0 Plant | Partial | Heater exported as `FiredHeater`, which pyDEXPI rejects; `GlobeValve` attributes not in the model |
| Stream state in Process exchange | Partial | 19 streams with P, T and mass flow; no composition, so the file alone cannot be re-simulated |
| Quick Proteus drawing (`DexpiXmlSvgRenderer`) | Not professional | One 1.8 m-wide row; lines cross equipment; `Recycle` solver helper drawn as equipment |
| Native document-set P&ID view (`EngineeringDiagramDelivery`, PID profile) | Not professional | Labelled boxes in tag order on a grid, no ISO 10628 symbols, heavy line crossings; manifest still reports `completeWithinDeclaredScope: true`. Use the professional package below for P&IDs |
| Professional P&ID (`ProfessionalPidPackageExporter`) | **Was partial; improved** | See the drawing-standards results below |
| NeqSim Proteus P&ID to runnable simulation | **Was broken; fixed** | Before: 3 of 17 units missing, liquid routed as gas, 50 t/h duplicated feed, setpoints lost, run failed. After: identical results (see below) |
| Third-party P&ID to simulation | Improved | Before: disconnected units and a pump crash with no reason. After: connected flowsheet, recycle closed, isolation/check/safety valves handled, 4 missing specifications reported by tag |
| Native DEXPI 2.x to simulation | Gap | Not supported. Before: silently built a feed-only model. After: explicit error naming the supported route |
| Instrument and control-loop intake | Partial | C01 example: 4 instruments, 4 loops, 3 actuating functions read as metadata; not bound to NeqSim controllers on the actual valves |

## Round-trip fidelity after the fix

The 17-unit recycle train was exported with `DexpiXmlWriter.write(...)`, rebuilt with
`DexpiSimulationBuilder`, and run with the same fluid and feed:

| Result | Source model | Rebuilt from DEXPI |
| --- | --- | --- |
| HP separator gas / liquid | 28 014 / 21 986 kg/h | 28 014 / 21 986 kg/h |
| LP separator gas / liquid | 2 668 / 21 935 kg/h | 2 667 / 21 934 kg/h |
| Export compressor power | 612.5 kW | 612.5 kW |
| Recompressor power | 145.0 kW | 144.9 kW |
| Oil pump power | 21.8 kW | 21.8 kW |
| Inlet heater duty | 205.6 kW | 205.6 kW |

The three-phase case also reproduced the 50/30/20 split from the exported branch flows, the water
routing, and the pump power. A legacy file with the new port attributes removed rebuilt identically
through the fallback rules.

## Root causes found and fixed

1. **In-line valves broke every path through them.** NeqSim writes a valve as a `PipingComponent`
   inside its line segment, and connections point at the valve's own nozzles. The resolver mapped
   only equipment nozzles, so those connections were dropped and the valve disappeared.
   `DexpiFlowsheetGraph` now keeps valves with an operating outlet pressure as flowsheet units.
2. **Only the gas outlet was ever wired.** The builder took `getGasOutletStream(...)` for every
   upstream unit, so separator liquid went to pumps as gas. Outlets are now resolved per connection
   (see below).
3. **Mixers kept one inlet.** Disconnected units got a duplicate of the full feed, which doubled
   mass. All incoming connections are now wired, and duplicate feeds are reported.
4. **Setpoints were discarded.** Exported operating outlet P and T were ignored; heaters defaulted
   to 100 °C and compressors to their inlet pressure, which gave negative power. They are now
   applied with unit conversion.
5. **Loops were not closed.** Loops are now torn and closed with a `Recycle`.
6. **Splitters exported only their first branch.** `DexpiXmlWriter` now writes one nozzle per
   branch, and the builder recovers split factors from branch flows.
7. **Nozzle phase was not exchanged.** `DexpiXmlWriter` now writes a `NeqSimPortAttributes`
   generic-attribute set (`PortRole`, `PortIndex`) on every nozzle. Other tools can ignore it. For
   files without it, the builder falls back to the downstream unit type, then the nozzle height,
   then document order.

Regression coverage: `DexpiSimulationRoundTripTest` (5 tests, all of which fail on the previous
builder). All 402 existing DEXPI/diagram tests and the engineering/P&ID tests still pass when run with
the JVM module opens configured in the POM.

## Professional P&ID drawing standards

The professional P&ID was rendered for the same 17-unit train and checked against ISO 10628-1/-2,
ISO 5457, ISO 7200 and ANSI/ISA-5.1 drawing practice. The same automated rules were applied to the
output before and after the change:

| Finding on the 17-unit train | Before | After |
| --- | --- | --- |
| Signal continuation flags pointing to their own sheet | 77 | 0 (direct dashed signal lines) |
| Internal simulation names as line labels (`gasOutStream`, `BL outStream`) | 25 | 0 (NORSOK-style line numbers with register) |
| Automated valves drawn without an actuator | 12 | 0 |
| Sheets without ISO 5457 grid and centring marks, or missing ISO 7200 fields | 14 of 14 | 0 |
| Drawing coverage errors (mixers and the `Recycle` helper) | 6 | 0 |
| PSVs, BDVs, vents and drains drawn (with opt-in standard connections) | 0 (HOLD register) | drawn on pooled auxiliary sheets |
| Level control valve in series with the modelled letdown valve | 1 per separator | 0 (modelled valve is the final element) |
| Sheets for the 17-unit train | 14 (relief devices not drawn) | 15 (relief devices drawn; auxiliary sheets pooled; heaters and coolers share a sheet) |

The full convention table and the check codes are in
[Drawing conventions applied to every sheet](../pid-design-synthesis.md#drawing-conventions-applied-to-every-sheet).
These are automated checks of implemented conventions, not an ISO certification: the symbol
catalogue is NeqSim-authored, and controlled standards and discipline review remain required.

Simple heaters and coolers now share "Heat exchangers" sheets (up to three bands per A1
sheet), while separators and compressors keep their own sheet. On the 17-unit train this cuts the
set from 17 to 15 sheets. A new `PID_STANDARD_DRAWING_AREA` check keeps every symbol inside the
ISO 5457 frame; it caught header-tee continuation flags that started in the filing margin, which
are now moved inside.

Also resolved: PSVs use the ISO 10628-2 angle-pattern body, nozzles are labelled N1, N2 with a
nozzle register, manual valves show their normal position instead of a fail action, and plain
mixers no longer receive nozzle proposals.

Remaining drawing gaps:

- Utility-side control valves and meters (cooler TCVs) stay HOLD until the utility system is supplied.

## Roadmap to the goal

Ordered by how much each item blocks the goal.

| Priority | Item | Why it matters |
| --- | --- | --- |
| P1 | Project `Recycle` as a physical stream connection in the 2.0.1 Process writer, as the 2.0.1 Plant writer already does | Nearly every real plant has a recycle; today it blocks the standards-current Process exchange |
| P1 | Emit the mandatory `Method` in the 2.0.0 Process path, or bundle the 2.0.0 model files and run the model-level multiplicity validator; steer users to 2.0.1 | The current 2.0.0 Process export is reported CONFORMANT but fails in pyDEXPI |
| P1 | Native DEXPI 2.0.1 Plant and Process import into `ProcessSystem`, reusing the flowsheet graph and diagnostics | Modern CAE tools exchange 2.0.1; pyDEXPI already reads NeqSim's 2.0.1 output, but NeqSim does not |
| P1 | Export the fluid definition (composition, EoS reference) with Process streams | Lets a DEXPI file alone carry enough to re-run the physics |
| P3 | Professional P&ID: utility-system input for cooler control valves | ISO frame, title block, ISA symbols, direct signals, line numbers, relief connections, nozzle labels, pooled auxiliary sheets and shared heat-exchanger sheets are done |
| P2 | Make delivery manifests fail-closed on visual quality: no `completeWithinDeclaredScope: true` for an unrouted box diagram | Prevents overstated claims in engineering packages |
| P2 | Bind DEXPI control loops to NeqSim controllers on the named final element (for example `PICSA 4712.02` to `PV4712.02`) | Turns the P&ID into a dynamic model rather than category-renamed auto-instrumentation |
| P2 | Consistent heater mapping across profiles (heat exchanger unless fired) | `FiredHeater` and `HeatingInFurnace` misdescribe topsides heaters |
| P2 | Use excluded relief branches as relief-study inputs (PSV sizing, flare load, blowdown) | Moves from P&ID to physics studies, not just steady-state balance |
| P3 | Named-tool round-trip qualification (for example AVEVA, Hexagon, COMOS) with recorded evidence | Required before any interoperability claim |

## Reproduce

The scorecard numbers come from small Java drivers that build the evaluation case, export through
`DexpiXmlWriter`, `Dexpi20XmlWriter`, `Dexpi201XmlWriter`, `Dexpi20ProcessModelWriter`,
`Dexpi201ProcessModelWriter` and `EngineeringDiagramDelivery`, and rebuild with
`DexpiSimulationBuilder`. Independent checks used
`pydexpi.loaders.public.load_xml_with_report(..., strict=True)` from pyDEXPI 2.1.0.
`DexpiSimulationRoundTripTest` contains the executable form of the round-trip results.

## Related documentation

- [DEXPI import, export, and visualization](dexpi-reader.md)
- [DEXPI and P&ID current-master audit](dexpi-pid-current-master-audit.md)
- [DEXPI 2.0 native exchange and conformance](dexpi-20-conformance.md)
- [DEXPI Engineering Guide](../engineering/dexpi-guide.md)
