---
title: Separation/compression PFD and P&ID visual acceptance
description: Coordinated dual-profile source-fidelity, exchange, rendering, and qualification requirements for the public comparesimulations2 model.
---

# Separation/compression PFD and P&ID visual acceptance

This reference tracks the coordinated visual-acceptance milestone in
[#1332](https://github.com/equinor/neqsim/issues/1332) and
[#2899](https://github.com/equinor/neqsim/issues/2899). It uses the public
`EvenSol/NeqSim-Colab/notebooks/process/comparesimulations2.ipynb` source model.

## Source fidelity

The inspected notebook is blob `68f13ad17dce03ee343e2f711437d57cdcb58f19`.
Cell `b46addc3` defines reusable `getprocess()`; cell `63e9cf5b` contains a
detailed reference illustration. Cell `78f9c5ff` is a retained historical
Graphviz output and is not fresh visual-acceptance evidence.

The runnable model contains 39 named process elements covering three separation
stages, flash-gas cooling/scrubbing/recompression, liquid returns, two-sided gas
heat exchange, fuel-gas splitting, gas export, and oil export. The illustration
shows `24-VB-01`, but `getprocess()` does not contain that equipment or a
water-removal duty. Implementations must not invent either to resemble the
illustration.

## Coordinated delivery API

`EngineeringDiagramDualProfileDelivery` publishes `pfd/` and `pid/`
sub-deliveries from the same `ProcessSystem`. It fails when either delivery is
incomplete or the canonical source-graph fingerprints differ.

Each sub-delivery contains controlled document JSON, native SVG sheet(s), a
native PDF drawing set, native DEXPI 2.0 Process XML, and a delivery manifest.
The bundle manifest labels the PFD exchange as `DEXPI_2_0_PROCESS`. The P&ID
child-delivery exchange remains labelled `PROCESS_PFD_BFD_COMPANION_ONLY`.
When an executed operating case and explicit balance-boundary declarations are
supplied, the coordinated bundle additionally publishes:

- `stream-table.json`, containing governed temperature, pressure, mass-flow,
  and specific-enthalpy values with units, bases, provenance, and diagnostics;
- `balance-table.json`, containing declared inlet/outlet assignments and
  calculated mass/energy closure evidence;
- `pid/dexpi-plant-2.0.xml` plus its native Plant-profile assessment; and
- `pid/proteus-4.1.xml`, explicitly labelled as a compatibility proposal.

Boundary declarations resolve exact canonical source labels and fail closed for
missing, duplicate, or invalid operating evidence. The compatibility writer's
volatile export date/time is normalized to the documented
`1970-01-01T00:00:00` reproducibility sentinel; it is not an engineering
revision timestamp. The controlled revision remains in the manifests.

The facade is intentionally opt-in. Existing Graphviz, native DEXPI Process and
Plant, Proteus, simulation, and document APIs are unchanged. Supplying an
operating-case identity opts into the companion package and therefore requires
at least one explicit balance boundary.

`Request.Builder.includePidEngineeringRegisters(true)` additionally publishes
`pid/pid-design-model.json`, `pid/pid-completeness-report.json`, and
`pid/pid-engineering-registers.json`. It reuses the existing offshore complete
proposal rules with teaching area code `00` and a persistent plant identity.
The option defaults to false and is enabled by the full-model reference below.
`Report.getPidEngineeringRegisters()` returns the immutable register snapshot,
or `null` when the option is disabled.

The registers preserve canonical material-connection identities, source-linked
nozzle/valve/instrument/interface proposals, control and safeguarding signal
relationships, rule provenance, and unresolved completeness findings. Material
links between nozzles or relief destinations are not counted as control signals.
Candidate line bindings require project review. Missing pipe sizes, classes,
schedules and materials remain `PROJECT_INPUT_REQUIRED`; reducers remain
`NO_GOVERNED_REDUCER_DECLARATION` rather than invented fittings.

The opt-in registers add a source-linked,
`P&ID PROPOSAL OVERLAY - REVIEW REQUIRED` layer to the P&ID SVG/PDF sheets
only. Each proposed nozzle, valve, instrument, and declared interface has an
independent marker, legible full tag, and stable semantic identity at its canonical
source equipment. Dense instrument and valve callouts use deterministic four-column
racks with multiple rows plus distributed attachment points along the owning
equipment boundary instead of one wide converging fan. Same-equipment
control/safeguarding relationships use alternating external tracks; other
relationships retain stable separated lanes. PFD rendering, the simulation, and both DEXPI
exchange profiles remain unchanged. The overlay is review evidence, not a
complete project P&ID or a qualified symbol catalog.

The bundle manifest records that projection boundary and fingerprints all three
sidecars. `isComplete()` confirms bundle delivery, not an approved P&ID design;
the completeness report retains engineering errors and review gaps.

## Requirement and evidence matrix

| Requirement | Current implementation | Automated evidence | Remaining acceptance work |
| --- | --- | --- | --- |
| One canonical plant, distinct PFD/P&ID profiles | Dual-profile facade, shared source fingerprint, and P&ID-only proposal overlay | `EngineeringDiagramDualProfileDeliveryTest` and full-model profile assertions | Improve whole-sheet clarity without changing the canonical plant |
| Reviewable vector and PDF sheets | Native A1 SVG/PDF sheets, process-equipment symbols, fixed-port orthogonal routing, endpoint-aligned separated off-page lanes, collision-scored horizontal route labels, flow arrows, compact multi-row P&ID proposal racks, and external same-equipment signal tracks | Renderer label-segment/connector-alignment tests, rack-span/row/track checks, bundle artifact checks, and fresh full-sheet/detail inspection | Resolve any residual process-route congestion and retain accountable reviewed baselines |
| Stable regeneration | Deterministic child and bundle manifests plus byte-stable SVG/PDF | Fresh-model repeated-delivery test | Retain accountable reviewed visual baselines |
| Native PFD exchange | Native DEXPI 2.0 Process artifact | Delivery assessment, bundle labels, and full-model topology assertions | External interoperability qualification |
| P&ID exchange identity | Companion-only child label plus separate native DEXPI 2.0 Plant and Proteus 4.1 proposal artifacts | Plant assessment, profile labels, artifact and deterministic-regeneration assertions | Qualify the full-model proposal and external interoperability |
| Stream and H&MB companions | Opt-in governed stream/balance artifacts with exact boundary resolution | Valid, missing-case, unknown-boundary, and repeated-delivery tests | Publish and qualify full-model operating values and boundary assignments |
| Piping and instrumentation content | Opt-in source-linked proposal registers, sidecars, and per-element P&ID-only SVG/PDF callouts with distributed equipment-boundary attachment points | Register fidelity, immutability, signal classification, full-tag/semantic-ID and distinct attachment-point coverage, unique signal-path, profile-difference, and regeneration tests | Supply governed inputs and materialize reviewed per-element exchange content |
| Manual layout and routing | Three persistent proposed A1 sheets, serpentine process-order pins, fixed ports, orthogonal routes, and reciprocal continuations | Layout, renderer, full-model topology, and repeated-delivery tests | Accountable route refinement and reviewed visual baselines |
| Standards alignment | Explicit scope and no-conformance boundary | Manifest flags and documentation checks | Licensed clause mapping and accountable review |

## Engineering and qualification boundary

The target scope uses ISO 10628-1:2014 and ISO 10628-2:2012 for process-diagram
content and symbols, ANSI/ISA-5.1-2024 for instrumentation/control
identification, and applicable ISO 14617, ISO 5457, ISO 7200, ISO 3098, and IEC
62424 requirements. Public catalog metadata establishes scope only. Licensed
clause mapping, project convention approval, external DEXPI qualification,
discipline checking, certification, and construction fitness remain gaps.

The P&ID output is a teaching proposal until project line classes, sizes,
specifications, nozzles, valves, reducers, instruments, control functions,
isolation, drains, vents, and relief/blowdown interfaces have explicit governed
evidence. Missing data must remain visible; the steady-state model does not
supply a complete control or safety design.


## Executable full-model reference

`Comparesimulations2EngineeringDiagramReference` executes the existing
`OilGasProcessSimulationOptimization` model at its default operating case and
publishes the coordinated bundle:

```bash
mvn -q -DskipTests package
java -cp target/classes neqsim.process.examples.Comparesimulations2EngineeringDiagramReference \
  build/comparesimulations2-engineering-diagrams
```

The reference fails before publication when the simulated plant does not close
mass balance within 0.01 percent or if `24-VB-01` appears in the canonical
runnable model. Four proposed balance-boundary declarations use `well stream`
as the inlet and `fuel gas`, `export gas`, and `export oil` as outlets.

The retained layout register proposes three A1 landscape sheets: three-stage
separation/oil export, flash-gas recompression/dew point, and fuel split/gas
export compression. Major model objects have persistent proposed sheet
assignments and paper-millimetre pins. Fixed-port orthogonal routing remains
active for unprotected connections. These records are reproducible teaching
layout evidence, not checked project layout or engineering approval.

The slow regression test executes two fresh plants and compares manifest,
source-topology, SVG, and PDF evidence byte-for-byte. It also checks the
operating stream/H&MB companions, all three P&ID proposal/register sidecars,
and the separate native DEXPI 2.0 Plant and Proteus 4.1 P&ID proposal exchanges.
Passing automation establishes deterministic generation only. Fresh
full-sheet/detail inspection must still reject unreadable or congested routes,
labels, markers, and signals. Project metadata, completed and reviewed
line/nozzle/valve/reducer/instrument/control registers, reviewed exchange
projection, and accountable discipline review remain
mandatory before visual acceptance.
