---
name: neqsim-process-safety
version: "1.0.0"
description: "Process safety methodology — barrier management, PSFs/SCEs, HAZOP guidewords, LOPA worksheets, SIL determination per IEC 61511, bow-tie analysis, risk-matrix scoring, and trapped-liquid fire rupture screening. USE WHEN: a task requires barrier registers, hazard identification, layer-of-protection analysis, safety-integrity-level assignment for an SIF, trapped liquid rupture/PFP demand, or quantitative risk evaluation. Anchors on neqsim.process.safety.barrier, neqsim.process.safety.risk, and neqsim.process.safety.rupture classes."
last_verified: "2026-06-23"
requires:
  java_packages: [neqsim.process.safety.barrier, neqsim.process.safety.risk, neqsim.process.safety.rupture, neqsim.process.safety.risk.sis.nog070, neqsim.process.safety.esd, neqsim.process.safety.api14c, neqsim.process.safety.compliance]
---

# NeqSim Process Safety Skill

Systematic hazard identification, layer-of-protection analysis (LOPA), and
safety-integrity-level (SIL) determination — the quantitative half of process
safety that complements depressurization (`neqsim-dynamic-simulation`) and relief
sizing (`neqsim-relief-flare-network`).

## When to Use

- HAZOP / HAZID node walkdown with deviation guidewords
- Barrier management for PSFs/SCEs with document evidence and performance standards
- LOPA for a specific scenario — calculate residual frequency and required RRF
- SIL determination for an SIF — IEC 61508 / IEC 61511 verification
- Bow-tie analysis (top event with threats + barriers + consequences)
- ALARP / risk-matrix scoring (5×5)
- Trapped-liquid fire rupture screening for blocked-in liquid-filled segments,
  including PFP demand and source-term handoff

Standards: **IEC 61508**, **IEC 61511**, **CCPS LOPA Guidelines**, **API 521 / ISO 23251**, **ASME B31.3/B31.4**, **ASME B16.5**, **API 754**, **NORSOK Z-013**.

## Method 0b — Trapped-Liquid Fire Rupture Screening

Load `neqsim-trapped-liquid-fire-rupture` when a safety study concerns a
blocked-in liquid-filled pipe segment exposed to fire, no pressure relief, PFP
endurance, flange/pipe rupture, or a Word/HTML study report based on P&IDs/STID,
line lists, material certificates, and fire documents.

Recommended sequence:

1. Retrieve the evidence package with `neqsim-stid-retriever`: P&ID/STID,
   line list, piping spec, material certificate, flange/bolt/gasket data,
   fire-zone/PFP documents, relief basis, and design basis.
2. Extract a structured segment list with `neqsim-technical-document-reading`:
   isolation boundary, line numbers, fluid, P/T, ID, wall thickness, length,
   material grade, flange class, fire basis, PFP endurance, relief availability,
   acceptance criteria, and evidence gaps.
3. Use `TrappedInventoryCalculator` to calculate trapped mass and volume.
4. Use `FireExposureScenario`, `MaterialStrengthCurve`, and
   `TrappedLiquidFireRuptureStudy` to calculate event times and limiting mode.
5. Convert outputs to `SafetySystemDemand` for PFP checks and to
   `SourceTermResult` for consequence handoff if rupture is predicted.
6. Report all screening defaults as assumptions. Missing project data must stay
   visible in an evidence/gaps register.

## Method 0 — Evidence-Linked Barrier Register

Use `BarrierRegister`, `SafetyCriticalElement`, `SafetyBarrier`, `PerformanceStandard`,
and `DocumentEvidence` when agents read technical documentation and need a traceable
handoff into LOPA, SIL, bow-tie, or QRA.

Recommended sequence:

1. Extract `DocumentEvidence` from P&IDs, C&E charts, SRS, SIL verification reports,
   inspection reports, vendor datasheets, and operating procedures.
2. Build `PerformanceStandard` objects for each PSF/SIF/SCE function with target PFD,
   availability, proof-test interval, response time, and acceptance criteria.
3. Build `SafetyBarrier` objects with type, status, PFD/effectiveness, equipment tags,
   hazard ids, owner, evidence, and performance-standard links.
4. Group barriers under `SafetyCriticalElement` records using process equipment tags.
5. Run MCP `runBarrierRegister` to get validation findings plus `lopaHandoff`,
   `silHandoff`, `bowTieHandoff`, and `qraHandoff` blocks.

Credit rule: do not claim LOPA credit unless the barrier is `AVAILABLE`, has a valid
PFD, has a linked performance standard, and has traceable evidence. Missing evidence
must remain visible as a validation finding.

## Method 1 — HAZOP Guidewords

Apply the 7 standard guidewords to each design intent at every node:

| Guideword | Deviation example                         | Typical cause                       |
| --------- | ----------------------------------------- | ----------------------------------- |
| NO        | No flow to separator                      | Pump trip, blocked inlet            |
| MORE      | More pressure in V-100                    | PCV stuck open, inlet surge         |
| LESS      | Less level in V-100                       | LCV stuck open, drain leak          |
| AS WELL AS | Water carryover in gas                   | Demister flooding, wave action      |
| PART OF   | Wrong composition fed                     | Crossover from neighbouring train   |
| REVERSE   | Reverse flow from compressor              | Check valve failure                 |
| OTHER THAN | Maintenance with line live               | Procedural / isolation failure      |

Output as a HAZOP worksheet table; each row → candidate LOPA scenario.

For automated studies from STID/P&ID documents and NeqSim process simulations,
use MCP `runHAZOP`. It accepts a standard `runProcess` JSON model plus optional
document-extracted nodes, safeguards, evidence references, selected failure
modes, and a `barrierRegister`. The runner executes generated safety scenarios
on copied `ProcessSystem` models and returns IEC 61882 rows, simulation evidence,
quality gates, barrier-register handoff, and report markdown. See
`docs/safety/automated_hazop_from_stid.md`.

## Method 2 — LOPA Worksheet

Use [`LOPAResult`](../../../src/main/java/neqsim/process/safety/risk/sis/LOPAResult.java) to compute residual frequency:

```java
import neqsim.process.safety.risk.sis.LOPAResult;

LOPAResult lopa = new LOPAResult();
lopa.setScenarioName("V-100 overpressure during compressor surge");
lopa.setInitiatingEventFrequency(0.1);   // /yr — surge event
lopa.setTargetFrequency(1.0e-5);         // /yr — tolerable for safety SIF

// Independent Protection Layers (each must be IEC 61511 IPL-eligible)
lopa.addLayer("BPCS pressure control",     0.10, 0.1,    0.01);
lopa.addLayer("Operator response (alarm)", 0.10, 0.01,   0.001);
lopa.addLayer("PSV @ design",              0.01, 0.001,  1.0e-5);

System.out.println(lopa.toVisualization());
System.out.println("Target met? " + lopa.isTargetMet());
System.out.println("Required additional SIL: " + lopa.getRequiredAdditionalSIL());
```

**IPL eligibility rules (IEC 61511):**
- Independent of initiating event and other IPLs
- Specific (one task, one mode)
- Auditable (testable, with proof-test interval)
- BPCS counts as one IPL only (typically PFD = 0.1)

## Method 3 — SIL Determination

After LOPA tells you a SIL is needed, verify with [`SafetyInstrumentedFunction`](../../../src/main/java/neqsim/process/safety/risk/sis/SafetyInstrumentedFunction.java):

```java
import neqsim.process.safety.risk.sis.SafetyInstrumentedFunction;
import neqsim.process.safety.risk.sis.SafetyInstrumentedFunction.SIFCategory;

SafetyInstrumentedFunction sif = SafetyInstrumentedFunction.builder()
    .id("SIF-001")
    .name("HIPPS on V-100")
    .description("Close XV-1001A/B on PT-1001 high-high (2oo3)")
    .sil(2)                           // claimed
    .pfd(5.0e-3)                      // PFD_avg from supplier verification
    .testIntervalHours(8760.0)        // 1-year proof test
    .mttr(8.0)
    .architecture("2oo3")
    .category(SIFCategory.HIPPS)
    .initiatingEvent("Compressor surge → backflow")
    .safeState("XV closed, V-100 isolated")
    .build();

double rrf = sif.getRiskReductionFactor();    // 1/PFD
int silAchieved = sif.getSil();
```

**SIL bands (IEC 61508):**

| SIL | PFDavg              | RRF             |
| --- | ------------------- | --------------- |
| 1   | 1e-2 to 1e-1        | 10–100          |
| 2   | 1e-3 to 1e-2        | 100–1000        |
| 3   | 1e-4 to 1e-3        | 1000–10000      |
| 4   | 1e-5 to 1e-4        | 10000–100000    |

### Method 3b — NOG 070 SIL Catalogue (Norwegian shelf typical SIFs)

For Norwegian-shelf projects, the NOG 070 (070 Norsk olje og gass) guideline
gives **pre-determined minimum SIL** for typical SIFs — use
`Nog070SilCatalogue` / `Nog070SilDetermination` to look up the minimum and
verify the achieved SIL from PFD:

```java
import neqsim.process.safety.risk.sis.nog070.Nog070SifType;
import neqsim.process.safety.risk.sis.nog070.Nog070SilDetermination;

// HIPPS minimum is SIL 3; PFD 1e-4 achieves SIL 3 → compliant
Nog070SilDetermination r =
    Nog070SilDetermination.evaluate(Nog070SifType.HIPPS_PIPELINE, 1.0e-4);
int achieved = r.getAchievedSil();   // 3
int minimum  = r.getMinimumSil();    // 3
boolean ok   = r.isCompliant();      // true
String json  = r.toJson();
```

`Nog070SifType` includes `HIPPS_PIPELINE`, `ESD_SUBSEA_ISOLATION` (SIL 3),
`BLOWDOWN_HYDROCARBON_SEGMENT`, `PSD_PROCESS_SEGMENT` (SIL 2), and `CUSTOM`
(supply an explicit minimum via the 3-arg `evaluate`). Verified by
`Nog070SilCatalogueTest`.

### Method 3c — ESD Response-Time Budget (NOG 070 / IEC 61511)

Verify the full detection → logic → final-element chain fits the allowable ESD
response time with `EsdResponseTimeSimulator`:

```java
import neqsim.process.safety.esd.EsdResponseTimeSimulator;
import neqsim.process.safety.esd.EsdResponseTimeSimulator.EsdResponseTimeResult;

EsdResponseTimeResult res = new EsdResponseTimeSimulator()
    .setSifTag("ESD-1234")
    .addDetection("PT-1001 high-pressure", 1.0)         // [s]
    .addLogic("Logic solver scan + 2oo3 vote", 0.5)     // [s]
    .addValve("ESDV-2001", 0.5, 8.0)                    // command delay + stroke [s]
    .setAllowableResponseTimeS(15.0)
    .evaluate();

double total = res.getTotalResponseTimeS();   // 10.0
double margin = res.getMarginS();             // 5.0
boolean within = res.isWithinBudget();        // true
```

Verified by `EsdResponseTimeSimulatorTest`.

## Method 4 — Bow-Tie Analysis

Use [`BowTieModel`](../../../src/main/java/neqsim/process/safety/risk/bowtie/BowTieModel.java)
+ [`BowTieAnalyzer`](../../../src/main/java/neqsim/process/safety/risk/bowtie/BowTieAnalyzer.java):

```java
import neqsim.process.safety.risk.bowtie.BowTieModel;
import neqsim.process.safety.risk.bowtie.BowTieModel.Threat;
import neqsim.process.safety.risk.bowtie.BowTieAnalyzer;

BowTieModel bt = new BowTieModel("Loss of containment — V-100 gas phase");
bt.addThreat(new Threat("Overpressure", 0.1));
bt.addThreat(new Threat("Corrosion-induced rupture", 0.01));
// preventive barriers reduce threat → top-event frequency
// mitigative barriers reduce consequence severity

BowTieAnalyzer analyzer = new BowTieAnalyzer(bt);
double topEventFreq = analyzer.calculateTopEventFrequency();
```

Export to SVG with `BowTieSvgExporter` for the report.

## Method 5 — 5×5 Risk Matrix

Use [`RiskMatrix`](../../../src/main/java/neqsim/process/safety/risk/RiskMatrix.java) to score and rank scenarios per ISO 31000 / NORSOK Z-013.
Frequencies × Consequence categories → ALARP / intolerable / broadly acceptable bands.

## Method 6 — API RP 14C SAFE Chart (offshore device coverage)

Auto-enumerate process equipment and check that each has its API RP 14C–required
protective devices (PSH/PSL/LSH/LSL/PSV …) with `Api14cSafeChartBuilder`:

```java
import java.util.EnumSet;
import neqsim.process.safety.api14c.Api14cSafeChartBuilder;
import neqsim.process.safety.api14c.Api14cDeviceType;

// declarePresent(...) lists the devices actually installed, then build(process)
Api14cSafeChartBuilder chart = new Api14cSafeChartBuilder()
    .declarePresent("HP separator",
        EnumSet.of(Api14cDeviceType.PSH, Api14cDeviceType.PSV))
    .build(process);

boolean complete = chart.isComplete();   // false → missing devices
chart.getGaps();                         // equipment with missing required devices
String md = chart.toMarkdown();          // SAFE chart table
String json = chart.toJson();
```

`buildAssumingComplete(process)` enumerates equipment and assumes full coverage
(useful for generating the SAFE chart skeleton). Equipment categories come from
`Api14cEquipmentCategory` (e.g. `PRESSURE_VESSEL`). Verified by
`Api14cSafeChartBuilderTest`.

## Method 7 — NORSOK P-002 Process Design Compliance

Screen flare/blowdown/vent hydraulics and drainage against NORSOK P-002 limits
with `NorsokP002ComplianceChecker` (fluent, aggregates all findings):

```java
import neqsim.process.safety.compliance.NorsokP002ComplianceChecker;

NorsokP002ComplianceChecker c = new NorsokP002ComplianceChecker()
    .checkFlareLineMach("Header", 0.5)             // ≤ 0.7 Mach
    .checkBlowdownRhoV2("BDV-1", 150000.0)         // ρv² limit [Pa]
    .checkVentGasVelocity("Vent", 45.0)
    .checkLiquidCarryOver("V-100", 1.0e-4)
    .checkErosionalVelocity("Line-200", 80000.0)
    .recordDepressurisationValve("BDV-2", true, "Sized for fire case")
    .recordDrainSlope("CD-1", true, "1:100 slope OK");

boolean compliant = c.isCompliant();
int failures = c.countNonCompliant();
String json = c.toJson();      // findings tagged e.g. "FLARE_LINE_MACH_07"
```

Verified by `NorsokP002ComplianceCheckerTest`.

## Method 8 — STS-0131 Technical Safety Gate

Aggregate the project safety acceptance checks (PSV margin, MDMT, SIL, custom)
into a single pass/fail gate with `Sts0131Gate`:

```java
import neqsim.process.safety.compliance.Sts0131Gate;
import neqsim.process.safety.risk.sis.nog070.Nog070SifType;
import neqsim.process.safety.risk.sis.nog070.Nog070SilDetermination;

Sts0131Gate gate = new Sts0131Gate();
gate.addPsvSizingMargin(1.0, 1.15, 0.10);   // required, available, minMargin
gate.addMdmt(-20.0, -29.0);                 // operating MDMT vs design min
gate.addSil(Nog070SilDetermination.evaluate(Nog070SifType.PSD_PROCESS_SEGMENT, 5.0e-3));
gate.addCustom("Fire case", true, "Heat input within API 521 envelope");

boolean acceptable = gate.isAcceptable();
int failures = gate.countFailures();
String json = gate.toJson();
```

Verified by `Sts0131GateTest`.

## Common Mistakes

| Mistake                                              | Fix                                                                  |
| ---------------------------------------------------- | -------------------------------------------------------------------- |
| Counting BPCS twice as two IPLs                      | One BPCS = one IPL; control + alarm on same DCS = single layer       |
| Claiming credit for procedural IPL with PFD 0.01     | Operator-action IPL minimum PFD = 0.1 (CCPS) unless trained+timed    |
| Ignoring common-cause between IPLs                   | Same sensor / same final element ⇒ shared failure, halve credit      |
| Setting target frequency = "fatality"                | Use tolerable individual risk × exposed persons × outcome conditional |
| Picking SIL by "feel"                                | Always derive from LOPA gap (RRF needed) or risk-matrix calibration |
| Forgetting proof-test interval in PFD                | PFDavg ≈ λ_DU × T_proof / 2; double T → double PFD                   |

## Validation Checklist

- [ ] Each IPL satisfies CCPS independence/specificity/auditability
- [ ] No common-cause between adjacent IPLs (sensor, valve, logic solver)
- [ ] BPCS PFD ≥ 0.1; operator-action PFD ≥ 0.1
- [ ] Target frequency cites a corporate or regulatory criterion
- [ ] SIL claimed ≤ SIL achieved by PFD_avg
- [ ] Spurious-trip rate documented (production impact)
- [ ] Results saved to `results.json` with `safety_analysis` section + JSON from `LOPAResult.toJson()` / `SILVerificationResult.toJson()`

## Verification Tests

```bash
./mvnw test -Dtest=Nog070SilCatalogueTest,Sts0131GateTest,Api14cSafeChartBuilderTest,NorsokP002ComplianceCheckerTest,EsdResponseTimeSimulatorTest
```

## Related Skills

- [`neqsim-relief-flare-network`](../neqsim-relief-flare-network/SKILL.md) — when LOPA shows PSV is the IPL of last resort
- [`neqsim-trapped-liquid-fire-rupture`](../neqsim-trapped-liquid-fire-rupture/SKILL.md) — blocked-in liquid fire rupture, PFP demand, and source-term handoff
- [`neqsim-dynamic-simulation`](../neqsim-dynamic-simulation/SKILL.md) — depressurization & blowdown
- [`neqsim-standards-lookup`](../neqsim-standards-lookup/SKILL.md) — IEC 61508/61511, NORSOK Z-013, API 754
