---
name: neqsim-process-safety
version: "1.0.0"
description: "Process safety methodology — HAZOP guidewords, LOPA worksheets, SIL determination per IEC 61511, bow-tie analysis, risk-matrix scoring. USE WHEN: a task requires hazard identification, layer-of-protection analysis, safety-integrity-level assignment for an SIF, or quantitative risk evaluation. Anchors on neqsim.process.safety.risk classes (LOPAResult, SafetyInstrumentedFunction, SILVerificationResult, RiskMatrix, BowTieModel, BowTieAnalyzer)."
last_verified: "2026-04-26"
requires:
  java_packages: [neqsim.process.safety.risk]
---

# NeqSim Process Safety Skill

Systematic hazard identification, layer-of-protection analysis (LOPA), and
safety-integrity-level (SIL) determination — the quantitative half of process
safety that complements depressurization (`neqsim-dynamic-simulation`) and relief
sizing (`neqsim-relief-flare-network`).

## When to Use

- HAZOP / HAZID node walkdown with deviation guidewords
- LOPA for a specific scenario — calculate residual frequency and required RRF
- SIL determination for an SIF — IEC 61508 / IEC 61511 verification
- Bow-tie analysis (top event with threats + barriers + consequences)
- ALARP / risk-matrix scoring (5×5)

Standards: **IEC 61508**, **IEC 61511**, **CCPS LOPA Guidelines**, **API 754**, **NORSOK Z-013**.

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
- [`neqsim-dynamic-simulation`](../neqsim-dynamic-simulation/SKILL.md) — depressurization & blowdown
- [`neqsim-standards-lookup`](../neqsim-standards-lookup/SKILL.md) — IEC 61508/61511, NORSOK Z-013, API 754
