---
title: "NOG 070 SIL, STS-0131 Gate and ESD Response Time"
description: "Pre-determined SIL for typical Norwegian-shelf SIFs (NOG 070), an aggregated STS-0131 technical-safety acceptance gate, and an ESD response-time budget per IEC 61511 — Nog070SilCatalogue, Nog070SilDetermination, Sts0131Gate, and EsdResponseTimeSimulator."
keywords: "SIL, SIF, NOG 070, 070, HIPPS, ESD, STS-0131, IEC 61511, IEC 61508, PFD, response time, valve stroke time, Norwegian shelf"
---

# NOG 070 SIL, STS-0131 Gate and ESD Response Time

These classes support safety-integrity verification for Norwegian-shelf projects:

| Class | Purpose | Standard |
|-------|---------|----------|
| `Nog070SilCatalogue` | Pre-determined minimum SIL for typical SIFs | NOG 070 (Norsk olje og gass 070) |
| `Nog070SilDetermination` | Map PFD → achieved SIL and check against the catalogue minimum | IEC 61508 / IEC 61511 |
| `Sts0131Gate` | Aggregate PSV-margin, MDMT, SIL and custom checks into one pass/fail gate | Equinor STS-0131 |
| `EsdResponseTimeSimulator` | Detection → logic → final-element response-time budget | IEC 61511 / NOG 070 |

All classes live under `neqsim.process.safety.risk.sis.nog070`,
`neqsim.process.safety.compliance`, and `neqsim.process.safety.esd`.

## NOG 070 pre-determined SIL

NOG 070 assigns a typical minimum SIL to common offshore SIFs. Look up the
minimum and verify an achieved SIL from a calculated PFD:

```java
import neqsim.process.safety.risk.sis.nog070.Nog070SifType;
import neqsim.process.safety.risk.sis.nog070.Nog070SilCatalogue;
import neqsim.process.safety.risk.sis.nog070.Nog070SilDetermination;

// Catalogue minimum for a HIPPS pipeline function (SIL 3)
int minSil = Nog070SilCatalogue.getMinimumSil(Nog070SifType.HIPPS_PIPELINE);

// Achieved SIL from PFD, checked against the catalogue minimum
Nog070SilDetermination r =
    Nog070SilDetermination.evaluate(Nog070SifType.HIPPS_PIPELINE, 1.0e-4);
int achieved = r.getAchievedSil();   // 3
int minimum  = r.getMinimumSil();    // 3
boolean ok   = r.isCompliant();      // true
String json  = r.toJson();
```

`Nog070SifType` covers `HIPPS_PIPELINE`, `ESD_SUBSEA_ISOLATION`,
`BLOWDOWN_HYDROCARBON_SEGMENT`, `PSD_PROCESS_SEGMENT`, and `CUSTOM`. For a
custom SIF, pass an explicit minimum with the three-argument
`evaluate(type, pfd, explicitMinSil)`. The static helper
`Nog070SilDetermination.pfdToSil(0.2)` maps a PFD to an integer SIL band
directly.

## STS-0131 technical-safety gate

`Sts0131Gate` aggregates the project safety acceptance checks into a single
pass/fail decision. Each `addXxx` call records a finding; the gate fails if any
finding fails.

```java
import neqsim.process.safety.compliance.Sts0131Gate;
import neqsim.process.safety.risk.sis.nog070.Nog070SifType;
import neqsim.process.safety.risk.sis.nog070.Nog070SilDetermination;

Sts0131Gate gate = new Sts0131Gate();
gate.addPsvSizingMargin(1.0, 1.15, 0.10);   // required, available, minMargin
gate.addMdmt(-20.0, -29.0);                 // operating MDMT vs design min [°C]
gate.addSil(Nog070SilDetermination.evaluate(Nog070SifType.PSD_PROCESS_SEGMENT, 5.0e-3));
gate.addCustom("Fire case", true, "Heat input within API 521 envelope");

boolean acceptable = gate.isAcceptable();
int failures = gate.countFailures();
String json = gate.toJson();
```

The MDMT check fails when the operating minimum temperature is below the design
MDMT (e.g. `addMdmt(-60.0, -29.0)`), and the PSV check fails when the available
margin is below the required minimum (e.g. `addPsvSizingMargin(1.0, 1.05, 0.10)`).

## ESD response-time budget

`EsdResponseTimeSimulator` sums the detection, logic-solver, and final-element
contributions and compares the total against the allowable ESD response time.

```java
import neqsim.process.safety.esd.EsdResponseTimeSimulator;
import neqsim.process.safety.esd.EsdResponseTimeSimulator.EsdResponseTimeResult;

EsdResponseTimeResult res = new EsdResponseTimeSimulator()
    .setSifTag("ESD-1234")
    .addDetection("PT-1001 high-pressure", 1.0)         // sensor delay [s]
    .addLogic("Logic solver scan + 2oo3 vote", 0.5)     // logic delay [s]
    .addValve("ESDV-2001", 0.5, 8.0)                    // command delay + stroke time [s]
    .setAllowableResponseTimeS(15.0)
    .evaluate();

double detection = res.getDetectionTimeS();      // 1.0
double logic     = res.getLogicTimeS();          // 0.5
double element   = res.getFinalElementTimeS();   // 8.5
double total     = res.getTotalResponseTimeS();  // 10.0
double margin    = res.getMarginS();             // 5.0
boolean within   = res.isWithinBudget();         // true
```

## Verification

These examples are exercised by the unit tests:

```bash
./mvnw test -Dtest=Nog070SilCatalogueTest,Sts0131GateTest,EsdResponseTimeSimulatorTest
```

## Related Documentation

- [SIS Logic Implementation](sis_logic_implementation.md)
- [Event and Fault Trees](event_fault_trees.md)
- [API 14C SAFE Chart and NORSOK P-002 Compliance](api14c_norsok_p002.md)
- [ESD Dynamic Testing Workflow](esd_testing_workflow.md)
