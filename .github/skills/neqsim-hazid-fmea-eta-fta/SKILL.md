---
name: neqsim-hazid-fmea-eta-fta
version: "1.0.0"
description: "Structured hazard-identification workflows — HAZOP worksheets with the seven IEC 61882 guidewords, FMEA failure-mode tables with RPN/criticality scoring, event-tree analysis (ETA) for outcome frequency, fault-tree analysis (FTA) with β-factor common-cause modelling and minimal cut sets, and escalation-graph (domino) screening. USE WHEN: a task requires systematic hazard identification, qualitative-to-quantitative scenario development, top-event decomposition, or escalation/domino analysis between adjacent equipment. Anchors on neqsim.process.safety.hazid, neqsim.process.safety.risk.eta, neqsim.process.safety.risk.fta, neqsim.process.safety.escalation."
last_verified: "2026-04-26"
requires:
  java_packages:
    - neqsim.process.safety.hazid
    - neqsim.process.safety.risk.eta
    - neqsim.process.safety.risk.fta
    - neqsim.process.safety.escalation
---

# NeqSim HAZID / FMEA / ETA / FTA Skill

The four canonical hazard-identification and scenario-development techniques in
one place — used together they produce auditable HAZOP / SIL / QRA documentation
per IEC 61882, IEC 61508/61511, ISO 17776 and the CCPS guideline series.

## When to Use

- HAZOP node walkdown — apply guidewords to design intent at every node
- FMEA / FMECA — equipment-level failure-mode tabulation with RPN scoring
- Event-tree analysis (ETA) — outcome-frequency development from initiating event
- Fault-tree analysis (FTA) — top-event decomposition, minimal cut sets, β-factor CCF
- Escalation / domino analysis — graph of adjacency between fire/explosion sources

Pairs with `neqsim-process-safety` (LOPA / SIL / risk matrix) and
`neqsim-consequence-analysis` (probit / IRPA roll-up).

## Standards

- **IEC 61882** — HAZOP studies — application guide
- **IEC 60812** — FMEA / FMECA procedure
- **IEC 61025** — fault tree analysis
- **IEC 61508 / 61511** — β-factor common-cause modelling
- **CCPS Guidelines for Hazard Evaluation Procedures** (3rd ed.)
- **ISO 17776** — major-accident hazard management

## Method 1 — HAZOP Worksheet

```java
import neqsim.process.safety.hazid.HAZOPTemplate;

HAZOPTemplate ws = new HAZOPTemplate("V-100", "HP separator pressure control node");
ws.addDeviation(HAZOPTemplate.GuideWord.MORE, HAZOPTemplate.Parameter.PRESSURE,
    "PCV stuck open",
    "Vessel rupture, gas release",
  "PSV-101; Operator response",
  "Verify PSV sizing per API 521");
String report = ws.report();
```

The seven IEC 61882 guidewords are defined in `HAZOPTemplate.GuideWord`.
Use `addDeviation(...)` once per `(guideWord, parameter)` deviation per node,
or `generateGrid(...)` to create empty rows for selected parameters.

For STID/P&ID + plant-data + NeqSim simulation workflows, use MCP `runHAZOP`.
It accepts a standard `runProcess` JSON definition plus extracted nodes,
safeguards, evidence references, failure modes, and an optional barrier register.
It returns HAZOP rows with scenario-simulation evidence and report markdown.

## Method 2 — FMEA / FMECA

```java
import neqsim.process.safety.hazid.FMEAWorksheet;

FMEAWorksheet f = new FMEAWorksheet("Compressor train");
f.addEntry("K-101", "Centrifugal compressor",
    "Surge", "Suction valve closed too fast",
    "Bearing damage, unplanned shutdown",
    8, 4, 5);   // Severity, Occurrence, Detection
double rpn = f.totalRPN();
```

`RPN = S × O × D` (IEC 60812). Items above the company threshold (often RPN > 100
or S ≥ 9) trigger design changes. The `criticalEntries(threshold)` helper returns
the subset above a cut-off.

## Method 3 — Event Tree (ETA)

```java
import neqsim.process.safety.risk.eta.EventTreeAnalyzer;

EventTreeAnalyzer eta = new EventTreeAnalyzer("Gas leak", 1.0e-4); // /yr
eta.addBranch("Immediate ignition?", 0.05);
eta.addBranch("Confined?", 0.30);
eta.addBranch("Delayed ignition?", 0.10);

double fJet  = eta.outcomeFrequency(new boolean[] {true,  false, false});
double fVCE  = eta.outcomeFrequency(new boolean[] {false, true,  true});
double fFlash = eta.outcomeFrequency(new boolean[] {false, false, true});
double fSafe = eta.outcomeFrequency(new boolean[] {false, false, false});
String tree = eta.toTextTree();
```

Branches multiply: `f_outcome = f_init · Π p_branch` (or 1-p) along the path. The
text-tree output is suitable for embedding in HAZOP reports.

## Method 4 — Fault Tree (FTA)

```java
import neqsim.process.safety.risk.fta.FaultTreeAnalyzer;
import neqsim.process.safety.risk.fta.FaultTreeNode;

FaultTreeNode psvA = FaultTreeNode.basic("PSV-A fails", 0.005);
FaultTreeNode psvB = FaultTreeNode.basic("PSV-B fails", 0.005);
FaultTreeNode bothFail = FaultTreeNode.and("Both PSVs fail", psvA, psvB)
                                      .withCCF(0.10);  // β-factor 10 %

FaultTreeAnalyzer fta = new FaultTreeAnalyzer();
double pTop = fta.topEventProbability(bothFail);
java.util.Set<java.util.List<String>> cuts =
    fta.minimalCutSets(bothFail, /* maxCardinality */ 4);
```

Gate types: `AND`, `OR`, `VOTING (k-of-n)`. Common-cause is applied via
`withCCF(β)`. Minimal cut sets are returned by brute-force enumeration up to a
user-specified cardinality (sufficient for typical < 20-basic-event trees).

### β-factor common-cause semantics

The implementation uses the IEC 61508 convex-combination form:

`P(top with CCF) = (1 − β) · P_independent + β · max(P_basic_i)`

For redundant (AND) configurations this *increases* the failure probability —
the dominant safety effect. For series (OR) configurations it *decreases* the
disjunction probability because the common-mode replaces independent
co-occurrence.

## Method 5 — Escalation / Domino Graph

```java
import neqsim.process.safety.escalation.EscalationGraphAnalyzer;

EscalationGraphAnalyzer esc = new EscalationGraphAnalyzer();
esc.addEquipment("V-100", 1.0e-4);     // base release frequency
esc.addEquipment("V-101", 1.0e-4);
esc.addAdjacency("V-100", "V-101", 0.30); // 30 % escalation if V-100 ignites
double fEscalated = esc.escalatedFrequency("V-101");
java.util.List<String> chain =
    esc.criticalChain("V-100", "V-101"); // worst-case domino path
```

Used to screen plot-plan layouts before detailed CFD (KFX / FLACS) runs.

## Workflow — From STID to Simulation-backed HAZOP

1. Retrieve STID/P&ID, C&E, SRS, line-list, and operating-data documents into
  the task references folder.
2. Extract nodes with `nodeId`, `designIntent`, `equipment`, `safeguards`, and
  `evidenceRefs` using the technical-document-reading and STID-retriever skills.
3. Build a NeqSim process JSON definition from extracted topology or existing
  models.
4. Run `runHAZOP` with selected failure modes, or enable all failure modes.
5. Review generated rows, scenario results, failed simulations, and evidence
  references with the chaired HAZOP team.
6. Pass accepted safeguards to `runBarrierRegister`, LOPA, SIL, bow-tie, and QRA
  workflows.

Detailed user documentation: `docs/safety/automated_hazop_from_stid.md`.

## Workflow — From HAZOP to LOPA in One Notebook

1. **HAZOP** the node → identify deviation, cause, consequence
2. **FTA** the cause → quantify cause frequency from basic events
3. **ETA** the consequence → split into outcome branches
4. **Probit** + IRPA — see `neqsim-consequence-analysis`
5. **LOPA** + SIL — see `neqsim-process-safety`

A reference end-to-end workflow lives in the test class
`HAZOPFMEATest` + `EscalationGraphAnalyzerTest`.

## Common Pitfalls

- **Guideword ≠ cause** — HAZOP guidewords describe the *deviation*, not the
  cause. Every deviation can have multiple causes; document each separately.
- **RPN inflation** — companies vary on detection scales. Document the scale
  used (CCPS 1–10 vs IEC 60812 1–10) at the top of every FMEA.
- **β-factor for OR gates** — does not represent "more failure" the way it does
  for AND gates. See semantics box above.
- **Cut-set explosion** — limit `maxCardinality` to 4–6 for trees with > 15
  basics; the enumeration is `O(2^n)` in basic-event count.

## Verification Tests

```bash
./mvnw test -Dtest=HAZOPFMEATest,EventTreeAnalyzerTest,FaultTreeAnalyzerTest,EscalationGraphAnalyzerTest
```

## See Also

- `neqsim-process-safety` — LOPA, SIL, bow-tie, risk matrix
- `neqsim-consequence-analysis` — fire, dispersion, probit, IRPA
- `neqsim-depressurization-mdmt` — emergency blowdown, MDMT
