---
title: "API 14C SAFE Chart and NORSOK P-002 Compliance"
description: "Auto-generate an API RP 14C / ISO 10418 SAFE chart of required protective devices, screen flare/blowdown/vent hydraulics and drainage against NORSOK P-002, and superimpose coupled multi-vessel blowdown loads — Api14cSafeChartBuilder, NorsokP002ComplianceChecker, and MultiVesselBlowdownStudy."
keywords: "API 14C, API RP 14C, ISO 10418, SAFE chart, PSH, PSL, LSH, LSL, PSV, NORSOK P-002, flare line Mach, blowdown rho v2, vent velocity, erosional velocity, multi-vessel blowdown, header"
---

# API 14C SAFE Chart and NORSOK P-002 Compliance

| Class | Purpose | Standard |
|-------|---------|----------|
| `Api14cSafeChartBuilder` | Enumerate equipment and check required protective devices | API RP 14C / ISO 10418 |
| `NorsokP002ComplianceChecker` | Screen flare/blowdown/vent hydraulics and drainage | NORSOK P-002 |
| `MultiVesselBlowdownStudy` | Superimpose coupled blowdown sources and check header Mach | API 521 §7 / NORSOK P-002 |

Classes live under `neqsim.process.safety.api14c`,
`neqsim.process.safety.compliance`, and
`neqsim.process.safety.depressurization`.

## API 14C SAFE chart

The Safety Analysis Function Evaluation (SAFE) chart lists every process
component and the protective devices API RP 14C requires for it. Declare the
devices actually installed, then build the chart against a `ProcessSystem`:

```java
import java.util.EnumSet;
import neqsim.process.safety.api14c.Api14cSafeChartBuilder;
import neqsim.process.safety.api14c.Api14cDeviceType;

Api14cSafeChartBuilder chart = new Api14cSafeChartBuilder()
    .declarePresent("HP separator",
        EnumSet.of(Api14cDeviceType.PSH, Api14cDeviceType.PSV))
    .build(process);

boolean complete = chart.isComplete();   // false → some required devices missing
chart.getItems();                        // per-equipment coverage items
chart.getGaps();                         // equipment with missing required devices
String md = chart.toMarkdown();          // SAFE chart as a markdown table
String json = chart.toJson();
```

`buildAssumingComplete(process)` enumerates the equipment and assumes full
device coverage — useful to generate the SAFE-chart skeleton before populating
the installed devices. Device types come from `Api14cDeviceType` (PSH, PSL, LSH,
LSL, TSH, TSL, USV, SDV, BDV, PSV, FSV, FIRE, GAS) and equipment categories from
`Api14cEquipmentCategory` (e.g. `PRESSURE_VESSEL`).

## NORSOK P-002 process-design compliance

`NorsokP002ComplianceChecker` is a fluent screen for the flare/blowdown/vent and
drainage criteria in NORSOK P-002. Each `check…`/`record…` call adds a finding;
the checker aggregates them into a single compliant/non-compliant verdict.

```java
import neqsim.process.safety.compliance.NorsokP002ComplianceChecker;

NorsokP002ComplianceChecker c = new NorsokP002ComplianceChecker()
    .checkFlareLineMach("Header", 0.5)             // ≤ 0.7 Mach
    .checkBlowdownRhoV2("BDV-1", 150000.0)         // ρv² ≤ 200 000 kg/(m·s²)
    .checkVentGasVelocity("Vent", 45.0)            // ≤ 60 m/s
    .checkLiquidCarryOver("V-100", 1.0e-4)         // ≤ 0.1 vol%
    .checkErosionalVelocity("Line-200", 80000.0)
    .recordDepressurisationValve("BDV-2", true, "Sized for fire case")
    .recordDrainSlope("CD-1", true, "1:100 slope OK");

boolean compliant = c.isCompliant();
int failures = c.countNonCompliant();
String json = c.toJson();   // findings tagged with P002Criterion codes
```

Findings reference the `P002Criterion` enum (`FLARE_LINE_MACH_07`,
`BLOWDOWN_RHO_V2`, `EROSIONAL_VELOCITY`, `VENT_GAS_VELOCITY`,
`LIQUID_CARRY_OVER`, `DEPRESSURISATION_VALVE_SIZE`, `DRAIN_SLOPE_CAPACITY`).

## Coupled multi-vessel blowdown header load

When several vessels blow down to a common header, `MultiVesselBlowdownStudy`
superimposes their mass-flow profiles onto a common time grid and checks the
resulting header Mach number against the NORSOK P-002 / API 521 limit.

```java
import neqsim.process.safety.depressurization.MultiVesselBlowdownStudy;
import neqsim.process.safety.depressurization.MultiVesselBlowdownStudy.MultiVesselBlowdownResult;

MultiVesselBlowdownStudy study = new MultiVesselBlowdownStudy()
    .setGridStep(1.0)
    .addSourceResult("HP-sep", hpSepDepressurizationResult)
    .addSourceResult("Inlet-sep", inletSepDepressurizationResult)
    .setHeader(0.80, 1.5, 288.15, 0.020, 1.30);   // D[m], P[bara], T[K], MW, gamma

MultiVesselBlowdownResult res = study.run();
double peak = res.getPeakTotalMassFlowKgPerS();      // combined peak [kg/s]
double tPeak = res.getPeakTimeS();                   // time of combined peak [s]
res.getPeakContributionKgPerS();                     // per-source contribution at peak
double mach = res.getHeaderMach();
boolean headerOk = res.isHeaderMachAcceptable();     // Mach ≤ 0.70
```

Each source is a `DepressurizationResult` from `DepressurizationSimulator` (see
[Depressurization per API 521](depressurization_per_API_521.md)).

## Verification

```bash
./mvnw test -Dtest=Api14cSafeChartBuilderTest,NorsokP002ComplianceCheckerTest,MultiVesselBlowdownStudyTest
```

## Related Documentation

- [NOG 070 SIL, STS-0131 Gate and ESD Response Time](nog070_sil_sts0131_esd.md)
- [Depressurization per API 521](depressurization_per_API_521.md)
- [Relief Valve Sizing (API 520/521)](relief_valve_sizing_api.md)
