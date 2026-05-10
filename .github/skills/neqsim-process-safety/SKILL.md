---
name: neqsim-process-safety
version: "1.0.0"
description: "Process safety methodology — barrier management, PSFs/SCEs, HAZOP guidewords, LOPA worksheets, SIL determination per IEC 61511, bow-tie analysis, risk-matrix scoring, and trapped-liquid fire rupture screening. USE WHEN: a task requires barrier registers, hazard identification, layer-of-protection analysis, safety-integrity-level assignment for an SIF, trapped liquid rupture/PFP demand, or quantitative risk evaluation. Anchors on neqsim.process.safety.barrier, neqsim.process.safety.risk, and neqsim.process.safety.rupture classes."
last_verified: "2026-05-07"
requires:
  java_packages: [neqsim.process.safety.barrier, neqsim.process.safety.risk, neqsim.process.safety.rupture]
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

## Related Skills

- [`neqsim-relief-flare-network`](../neqsim-relief-flare-network/SKILL.md) — when LOPA shows PSV is the IPL of last resort
- [`neqsim-trapped-liquid-fire-rupture`](../neqsim-trapped-liquid-fire-rupture/SKILL.md) — blocked-in liquid fire rupture, PFP demand, and source-term handoff
- [`neqsim-dynamic-simulation`](../neqsim-dynamic-simulation/SKILL.md) — depressurization & blowdown
- [`neqsim-standards-lookup`](../neqsim-standards-lookup/SKILL.md) — IEC 61508/61511, NORSOK Z-013, API 754
