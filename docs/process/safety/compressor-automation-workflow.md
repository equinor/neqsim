---
title: "Compressor automation engineering workflow"
description: "Run a public natural-gas compressor model, canonical control and safety proposal, native dynamic acceptance cases and DEXPI 2.0.1 P&ID export."
---

# Compressor automation engineering workflow

`neqsim.process.engineering.CompressorAutomationWorkflow` is a public, packaged entrypoint for a
synthetic natural-gas compression train. It composes the native compressor study, canonical
instrument/control import, actual safety-function import, configured-copy readback, strict dynamic
runner and professional package exporter. Its physical process and acceptance results come from
those APIs; there is no second solver or independent verdict engine in the wrapper.

The template includes a suction mixer/scrubber, mapped centrifugal compressor and native driver,
aftercooler, discharge check valve/receiver, export isolation and a cooled anti-surge recycle.
Actual field channels, a held calculated map-margin signal, native controllers and actual shutdown
actions supply the P&ID objects and automation registers. A calculated margin is not a separate
physical transmitter. Documentary machinery-protection requirements remain unresolved requirements
until actual hardware, native behavior and accountable engineering evidence are supplied.

## Agent invocation

Build the current source and its runtime classpath, then invoke the main class. The example assumes
the Java runtime required by the current POM. Java 8 source syntax does not establish whole-JAR
Java 8 runtime compatibility. Java 17 serialization may require the module opens shown here.

```bash
./mvnw -DskipTests package dependency:build-classpath -Dmdep.outputFile=target/runtime-classpath.txt
java --add-opens java.base/java.util=ALL-UNNAMED \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.io=ALL-UNNAMED \
  --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
  -cp "target/classes:$(cat target/runtime-classpath.txt)" \
  neqsim.process.engineering.CompressorAutomationWorkflow - target/compressor-default
```

The positional contract is `<design.json|-> <new-output-dir> [canonical-pid.json]`. A dash selects
the public `Design` constructor defaults. The output directory must not exist: retries use a new
directory and never replace previous evidence. The main class runs every public native profile at
0.1 s using the model's declared 20 s duration and event at 2 s. Programmatic callers can supply an
explicit list of `CaseDefinition` objects; `allPublicProfilesSelected` discloses subsets.

A design file is a strict JSON object of scalar and composition `CompressorAutomationStudy.Design` field names.
For example, `{"feedFlowKgPerSecond":6.0,"antiSurgeOpeningSeconds":0.3}` retains all other typed
defaults. Pressure fields end in `Bara`, temperature fields in `K`, motion fields in `Seconds`,
flow in `KgPerSecond`, and fractional setpoints identify their physical quantity. Unknown keys,
duplicates, nulls, numeric strings and non-finite values are rejected. The native constructor then
checks physical design relationships before execution. `design-input.json` retains the accepted
values. Optional `composition` maps component names to finite numeric mole fractions; duplicate
component names and numeric strings are rejected. The native factory validates and normalizes the
positive composition sum. `steady-state.json` and configuration receipts separately retain actual native readback.

The default mixture and map are public synthetic assumptions. Changing a design scalar can rebuild
that synthetic reference template; it is not a vendor-map similarity assessment or proof that an
installed machine accepts a different duty. Vendor curves, margins, gas cases and driver limits
must replace the assumptions in a controlled application.

## Canonical revision and fresh execution

An agent can edit supported control settings in `canonical-pid.json` and pass that file as the
third argument on a new invocation using the same physical design. The wrapper imports the actual
source, applies the chosen canonical settings to an isolated healthy train, reads them back, and
uses that governed train for the saved runnable process and all scenario copies.

Actual PID/anti-surge gains, setpoints and supported valve/instrument settings are governed by the
native control configuration. Unsupported SIF setpoint, voting or action-tree edits fail safety
readback; they are not silently ignored. To change the template's trip design, revise its explicit
`Design` trip inputs and regenerate the native source and proposal, then review the new cause/effect
and configuration evidence. Structural safety changes still require a compatible native factory.

Each mechanical fault is a separate declared override. A preflight copied prototype produces the
frozen fault-specific receipt. The actual runner-owned copy must match it before events or physical
steps. The observer never reapplies healthy settings after a fault, and the evidence collector never
executes native SIFs a second time. Event-injected measurement faults remain event declarations and
actual sampled evidence; they are not relabeled as a healthy physical process disturbance.

## Actual outputs and review gates

| Artifact | Meaning |
|---|---|
| `design-input.json`, `steady-state.json` | Accepted inputs; actual stream P/T/mass/enthalpy readback and native work/heat duties |
| `canonical-pid.json` | The proposal selected for this run; corresponding current bindings are in the native package |
| `automation-registers.json` | Source-backed I/O, control loops, cause/effect and unresolved requirements |
| `case-*/scenario.json`, `configuration.json` | Exact declared case and frozen actual control/safety settings |
| `case-*/result.json`, `full-trace.json` | Native case output and actual ordered configured-copy physical samples |
| `case-*/native-result.raw.txt` | Exact native result text, preserved independently from strict JSON normalization |
| `native-evidence-crosswalk.json` | Native-result fingerprint and separate strict output-file SHA-256 values |
| `dynamic-pid-feedback.json` | Current per-function demand/no-demand/failed/incomplete evidence without SIL credit |
| `engineering-simulation-results.json` | The coordinated strict runner's typed results; failures remain failures |
| `native-pid-package/` | DEXPI 2.0.1, professional native P&IDs/PFD, registers and governed runnable process |
| `workflow-status.json` | Execution completion, native criteria counts, errors, selected scope and retained HOLDs |

Agent-facing JSON is strict: undefined native floating-point values become `null` with their path
and native kind retained in `nonFiniteValues`, never a substituted zero. Authoritative map-margin
validity/reason flags remain in the native samples. Exact native text/fingerprints and normalized
file hashes are separate quantities and must not be described as byte-identical representations.

The wrapper does not grant layout approval. Run the repository's strict external PFD/layout checks,
native P&ID QC and independent visual inspection on every generated sheet. Check actual port/line/
signal identities, feedback crosswalks, dynamic-capability diagnostics, full-window acceptance,
mass/component/energy closure, timestep sensitivity and all retained failed or incomplete cases.
Expected-failure assertions are software checks, not successful protection. No-demand monitoring is
not observed trip response. Native response PASS cannot clear numerical or engineering HOLDs.

The NORSOK-derived requirements are a disclosed template, not an assertion of jurisdiction or
applicability. ISO 15519/10628/14617, ISA 5.1, IEC 62424, machinery/protection and functional-safety
requirements need controlled editions and accountable review. Missing vendor curves, machinery
monitoring, installed independence, relief/disposal design, restart permissives and SRS/SIL/PLC
application or construction approval remain explicit HOLDs.

`CompressorAutomationWorkflowTest` verifies strict input handling, actual steady readback, native
configured-copy execution, changed canonical anti-surge readback, separate raw/strict hashes, and
strict JSON artifacts. The native physical model and P&ID/control tests provide their own distinct
physics, identity, fault and layout coverage.

See [P&ID synthesis](../../pid-design-synthesis.md),
[closed-loop verification](closed-loop-sif-verification.md), and
[compressor anti-surge control](../equipment/compressor_antisurge_control.md).
