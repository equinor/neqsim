---
name: neqsim-autonomous-investigation
version: "1.1.0"
description: "Autonomous investigation loop for operational and engineering anomalies — turns an agent from 'told what to look for' into 'discovers relationships and hypotheses on its own'. USE WHEN: solving a PEPR action, root-cause, or operational study where the symptom, driver, or important relationships are NOT given up front. Runs an observe -> hypothesize -> predict -> test -> discriminate loop, using neqsim.process.diagnostics.RelationshipGraph for unsupervised lead-lag relationship discovery across historian tags, then hands the discovered relationships to neqsim-root-cause-analysis. Anchors on neqsim.process.diagnostics classes."
last_verified: "2026-07-19"
requires:
  java_packages: [neqsim.process.diagnostics, neqsim.process.automation]
---

# NeqSim Autonomous Investigation Skill

Make agents *investigate* instead of *follow a checklist*. Use this skill when a
task (PEPR action, root-cause, operational study, digital-twin deviation) does
**not** tell you the symptom, the driver, or which relationships matter. The goal
is that the agent discovers the important relationships from the data and the
flowsheet on its own, forms competing hypotheses, and tests them — reaching
findings that were not spelled out in the task.

## When to Use

- A PEPR action or work order describes a *problem* but not a *cause*.
- Historian data is available but no one has said "look at tag X vs tag Y".
- A model-vs-plant deviation appears and the responsible variable is unknown.
- Any "why is this happening?" question where spoon-feeding relations is wrong.

Do **not** use this to replace a known, well-scoped calculation — if the symptom
and mechanism are already given, go straight to `neqsim-root-cause-analysis` or
the relevant discipline skill.

## The Investigation Loop (mandatory ordering)

Run this loop *before* fixing a scope. Never assume the task's stated
classification is correct — treat it as a hypothesis to challenge.

1. **Observe (no assumptions).** Pull *all* available tags, not just the ones the
   task names. Scan each tag against its own baseline and (when available) STID
   design envelope for what is abnormal. Discover cross-tag relationships with
   `RelationshipGraph` — including *lead-lag direction*, which distinguishes a
   driver from a follower.
2. **Hypothesize (compete).** Generate **at least three** competing causal
   hypotheses, always including a "not a real problem / instrument or data
   artifact" hypothesis. Seed them from the discovered leaders (candidate causes),
   not from intuition alone.
3. **Predict (differentiate).** For each hypothesis, state what it implies for
   *other* tags/streams. Two hypotheses that predict the same thing cannot be
   distinguished — find a prediction where they disagree (the discriminating
   test).
4. **Test.** Use NeqSim (`runProcess`/`runFlowAssurance`/simulation verification
   via `RootCauseAnalyzer`) plus historian evidence (`EvidenceCollector`) to check
   each prediction.
5. **Discriminate & iterate.** Keep the hypothesis that best explains the *pattern
   across relationships*, not a single number. Loop until one dominates or the
   data is exhausted; report residual ambiguity honestly.

**Report the relationships you discovered, not just the answer.** A finding
without its supporting lead-lag relationships and discriminating test is not
complete.

## Unsupervised relationship discovery — `RelationshipGraph`

`RelationshipGraph` (in `neqsim.process.diagnostics`) scans every tag pair in a
historian data set with **no symptom and no hypothesis supplied**, and reports
which tags move together and, crucially, **which moves first**. Lead-lag
directionality is the signal an agent uses to pick candidate causes on its own.

### Java

```java
import java.util.Map;
import neqsim.process.diagnostics.RelationshipGraph;

RelationshipGraph graph = new RelationshipGraph();
graph.setTimestamps(timestamps);      // optional: enables lag in seconds
graph.setMaxLagSamples(10);           // search +/- 10 samples
graph.setMinAbsCorrelation(0.5);      // only report |r| >= 0.5
List<RelationshipGraph.Relationship> edges = graph.analyze(historianData);
String relationshipReport = graph.toTextReport(edges);

for (RelationshipGraph.Relationship r : edges) {
  // r.getSource() leads r.getTarget() (candidate cause -> candidate effect)
  // r.getDirection(): LEADS or SYNCHRONOUS
  // r.getLagSamples() / r.getLagSeconds(): how far ahead the driver moves
  // r.getCorrelation(): strength & sign at the best lag
}
```

### Python (task notebook / runner)

```python
RelationshipGraph = ns.JClass("neqsim.process.diagnostics.RelationshipGraph")

graph = RelationshipGraph()
graph.setTimestamps(timestamps)     # java double[]; omit if unavailable
graph.setMaxLagSamples(10)
graph.setMinAbsCorrelation(0.5)
edges = graph.analyze(historian_map)  # Map<String, double[]>
for r in edges:
    print(r.getSource(), "->", r.getTarget(),
          "r=", round(r.getCorrelation(), 2),
          "lag_s=", r.getLagSeconds())
```

### Reading the output

- `A -> B (r=+0.85, leads by 300 s)` — A moves first; A is a **candidate cause**
  of B. Prioritise hypotheses about A.
- `A <-> B (r=+0.90, synchronous)` — tightly coupled with no detectable lag; may
  share a common driver — look for a third tag that leads both.
- A strong statistical edge that does **not** follow a physical process path
  (upstream -> downstream in the flowsheet) is a candidate common-cause or
  instrument artifact, not a direct cause.

## Auto-detect the symptom — `AnomalyScanner`

You should not have to be told the symptom either. `AnomalyScanner` (in
`neqsim.process.diagnostics`) scans every tag against its own robust baseline
(median / MAD) and, when supplied, its STID design envelope, and reports abnormal
tags plus a **candidate symptom** inferred from the tag name. Detection kinds:
`THRESHOLD_HIGH/LOW` (crosses a design limit), `SPIKE_HIGH/LOW` (robust-z
outlier), `TREND_UP/DOWN` (sustained drift).

```java
AnomalyScanner scanner = new AnomalyScanner();
scanner.setDesignLimit("Compressor-1.vibration", Double.NaN, 7.1); // optional
List<AnomalyScanner.Anomaly> anomalies = scanner.scan(historianData);
Symptom candidate = scanner.suggestSymptom(anomalies); // e.g. HIGH_VIBRATION
```

## Promote statistics to causes — `CausalTopologyModel`

A statistical lead-lag edge is not proof of causation. `CausalTopologyModel`
overlays the flowsheet connectivity (which equipment feeds which) on the
`RelationshipGraph` edges and classifies each: `CAUSAL_CANDIDATE` (leader is
upstream of follower and moves first), `LOCAL` (same equipment), `COUNTER_FLOW`
(lead-lag opposes process flow — feedback), or `COMMON_CAUSE_OR_ARTIFACT` (no
process path — a shared hidden driver or an instrument artifact).

```java
Map<String, Set<String>> adjacency = CausalTopologyModel.buildDownstreamAdjacency(processSystem);
CausalTopologyModel model = new CausalTopologyModel(adjacency, tagToEquipment);
List<CausalTopologyModel.CausalEdge> edges = model.classify(relationships);
```

## One call — `RootCauseAnalyzer.analyzeAutonomous()`

The three steps above plus the Bayesian scoring are chained in a single entry
point. **No symptom is required** — the analyzer scans anomalies, infers the
symptom, discovers relationships, and classifies them against topology, then converts
hypothesis-matched anomaly and physically consistent topology findings into weighted
evidence used in ranking.

```java
RootCauseAnalyzer rca = new RootCauseAnalyzer(processSystem, "Compressor-1");
rca.setHistorianData(historianData, timestamps);
rca.setDesignLimit("Compressor-1.vibration", Double.NaN, 7.1);

// Autonomous: no setSymptom() call needed.
RootCauseReport report = rca.analyzeAutonomous();                 // anomalies + relationships + RCA
// or, to also get causal-vs-artifact classification:
RootCauseReport report2 = rca.analyzeAutonomous(tagToEquipment);  // + CausalTopologyModel

rca.getLastAnomalies();     // what looked abnormal
rca.getLastRelationships(); // who leads whom
rca.getLastCausalEdges();   // causal candidate vs common-cause/artifact
```

Only `LOCAL` and `CAUSAL_CANDIDATE` edges matching a hypothesis fingerprint affect
ranking. `COUNTER_FLOW`, `COMMON_CAUSE_OR_ARTIFACT`, and `UNKNOWN` findings remain
reportable but are not treated as causal support. This conservative admission rule
prevents correlation alone from inflating confidence.

Python: get the classes with `ns.JClass("neqsim.process.diagnostics.AnomalyScanner")`,
`...RelationshipGraph`, `...CausalTopologyModel`, `...RootCauseAnalyzer`.

## Cooperation (chain both directions)

```
plant-data / enterprise-plant-data   (historian tags)
alarm-events / maintenance-api / STID (events, work orders, design limits)
        v
neqsim-autonomous-investigation      (this skill: discover relations + hypotheses)
        v
neqsim-root-cause-analysis           (Bayesian scoring + simulation verification)
        v
neqsim-process-safety / discipline   (consequence, if the cause is a hazard)
```

- **Gather ALL data first (do not spoon-feed one tag):** pull the full historian
  tag map via `neqsim-plant-data` (community) or `enterprise-plant-data`
  (historian/Seeq); add alarm & event history (`enterprise-alarm-events`),
  maintenance work orders / notifications (`enterprise-maintenance-api`), and STID
  design limits when available. Feed the whole tag map (plus design limits) into
  `AnomalyScanner` / `RelationshipGraph` — the more context, the better the
  discovery.
- **Downstream:** hand the discovered leaders and their lags to
  `neqsim-root-cause-analysis` as the candidate causes / expected signals, so the
  Bayesian scorer verifies them with a NeqSim simulation instead of relying on a
  fixed symptom.
- The one-call path `RootCauseAnalyzer.analyzeAutonomous(tagToEquipment)` runs the
  whole chain (anomaly scan -> symptom inference -> relationship discovery ->
  topology classification -> Bayesian scoring) so the agent only supplies data +
  flowsheet.

## Limitations

- Correlation and lead-lag are **screening** signals, not proof of causation.
  Always confirm with the flowsheet topology and a NeqSim simulation.
- Default correlation is linear (Pearson); enable rank/Spearman mode
  (`RelationshipGraph.setUseRankCorrelation(true)`) to catch strong monotonic
  non-linear couplings. Strongly non-monotonic couplings may still be
  under-reported — consider transforming variables (log, rate-of-change) first.
- Lag resolution is limited by the sampling interval; sub-sample lags round to
  the nearest sample.
- Common-cause structure (one hidden driver behind many tags) is flagged only
  indirectly — look for a tag that leads several others, or a
  `COMMON_CAUSE_OR_ARTIFACT` verdict from `CausalTopologyModel`.
