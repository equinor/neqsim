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
The bundle manifest labels the PFD exchange as `DEXPI_2_0_PROCESS`. For the
P&ID proposal, the same exchange is labelled
`PROCESS_PFD_BFD_COMPANION_ONLY`: it is not a DEXPI Plant or Proteus P&ID
exchange.

The facade is intentionally opt-in. Existing Graphviz, native DEXPI Process and
Plant, Proteus, simulation, and document APIs are unchanged.

## Requirement and evidence matrix

| Requirement | Current implementation | Automated evidence | Remaining acceptance work |
| --- | --- | --- | --- |
| One canonical plant, distinct PFD/P&ID profiles | Dual-profile facade and shared source fingerprint | `EngineeringDiagramDualProfileDeliveryTest` | Run and qualify the full notebook model |
| Reviewable vector and PDF sheets | Existing native SVG/PDF renderer, A3 default, fixed-port orthogonal routing | Existing renderer tests plus bundle artifact checks | Full-sheet and detail inspection of every new reference sheet |
| Stable regeneration | Deterministic child and bundle manifests | Fresh-model repeated-delivery test | Normalized reference baselines for the full model |
| Native PFD exchange | Native DEXPI 2.0 Process artifact | Existing delivery assessment and bundle labels | Full-model topology/loss evidence |
| P&ID exchange identity | Fail-closed companion-only label | Manifest assertion | Add and qualify a separately labelled Plant/Proteus proposal artifact |
| Stream and H&MB companions | Existing governed stream and balance models | Existing focused tests | Publish full-model companions and boundary assignments |
| Piping and instrumentation content | Existing governed proposal, Proteus writer, and detailed DEXPI paths | Existing DEXPI/P&ID tests | Bind explicit line/nozzle/valve/reducer/instrument/control/interface registers to this model |
| Manual layout and routing | Existing evidence-bearing layout register | Existing layout and renderer tests | Declare full-model sheets, pins, protected routes, and stale-reference regeneration |
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
