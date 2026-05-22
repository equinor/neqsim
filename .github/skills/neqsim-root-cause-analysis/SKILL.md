---
name: neqsim-root-cause-analysis
version: "1.1.0"
description: "Root cause analysis (RCA) framework for process equipment — Bayesian-inspired diagnosis integrating multi-source reliability data (IOGP/SINTEF, CCPS, IEEE 493, Lees, OREDA) as prior, plant historian evidence (likelihood), and NeqSim simulation verification. USE WHEN: diagnosing compressor trips, high vibration, efficiency loss, separator carryover, heat exchanger fouling, or any operational anomaly. Anchors on neqsim.process.diagnostics classes."
last_verified: "2026-05-10"
requires:
  java_packages: [neqsim.process.diagnostics, neqsim.process.equipment.failure, neqsim.process.automation]
---

# NeqSim Root Cause Analysis Skill

Systematic equipment-level root cause analysis integrating process simulation,
multi-source reliability data, plant historian time-series, and STID design conditions.

## When to Use

- Equipment trip investigation (compressor, pump, turbine)
- High vibration diagnosis
- Efficiency degradation analysis
- Separator liquid carryover or gas blow-by
- Heat exchanger fouling assessment
- Valve erosion or malfunction
- Any operational anomaly requiring structured root cause identification

## Architecture Overview

The RCA framework uses a Bayesian-inspired three-stage methodology:

```
Prior (Reliability Data) × Likelihood (Historian) × Verification (Simulation) = Confidence
```

### Reliability Data Sources

`ReliabilityDataSource` loads from multiple public databases automatically:

- **IOGP Report 434 / SINTEF** — offshore O&G equipment (free)
- **CCPS 1989** — process vessels, piping, valves (published handbook)
- **IEEE 493-2007** — industrial electrical equipment (purchasable standard)
- **Lees' Loss Prevention 2012** — generic process industry rates (textbook)
- **SINTEF PDS** — safety-instrumented systems (purchasable)
- **OREDA** — offshore equipment (optional, commercial)

No commercial license is required for the default data.

### Core Classes

| Class | Purpose |
|-------|---------|
| `RootCauseAnalyzer` | Main orchestrator — takes ProcessSystem + equipment name, runs full analysis |
| `Symptom` | Enum of 12 equipment symptoms (TRIP, HIGH_VIBRATION, etc.) |
| `Hypothesis` | Candidate root cause with expected signal fingerprints, evidence, prior/likelihood/verification scores |
| `HypothesisGenerator` | Generates candidate hypotheses from built-in libraries and reliability-data-adjusted priors per equipment type |
| `EvidenceCollector` | Analyzes historian/STID data and attaches only hypothesis-relevant supporting or contradictory evidence |
| `SimulationVerifier` | Clones process, applies physical perturbation, compares simulated vs observed KPI directions |
| `RootCauseReport` | Ranked output with JSON and text report generation |

### Package Location

All classes in `neqsim.process.diagnostics`.

## Quick Start

### Java

```java
// 1. Create analyzer with running process and target equipment
RootCauseAnalyzer rca = new RootCauseAnalyzer(processSystem, "Compressor-1");

// 2. Set the observed symptom
rca.setSymptom(Symptom.HIGH_VIBRATION);

// 3. Provide historian data (from tagreader or CSV)
Map<String, double[]> historianData = new HashMap<>();
historianData.put("vibration_mm_s", vibrationTimeSeries);
historianData.put("discharge_temp_C", tempTimeSeries);
historianData.put("power_kW", powerTimeSeries);
rca.setHistorianData(historianData, timestamps);

// 4. Set design limits from STID
rca.setDesignLimit("vibration_mm_s", Double.NaN, 7.1);
rca.setDesignLimit("discharge_temp_C", Double.NaN, 180.0);

// 5. Optionally provide STID design values
Map<String, String> stidData = new HashMap<>();
stidData.put("design_flow_kg_hr", "50000");
stidData.put("design_efficiency_pct", "82");
rca.setStidData(stidData);

// 6. Run analysis
RootCauseReport report = rca.analyze();

// 7. Get results
System.out.println(report.toTextReport());
String json = report.toJson();
Hypothesis topCause = report.getTopHypothesis();
```

### Python (Jupyter)

```python
RootCauseAnalyzer = ns.JClass("neqsim.process.diagnostics.RootCauseAnalyzer")
Symptom = ns.JClass("neqsim.process.diagnostics.Symptom")

rca = RootCauseAnalyzer(process, "Compressor-1")
rca.setSymptom(Symptom.HIGH_VIBRATION)

# Convert numpy arrays to Java
from jpype import JArray, JDouble
historian = ns.HashMap()
historian.put("vibration_mm_s", JArray(JDouble)(vibration_data.tolist()))
rca.setHistorianData(historian, JArray(JDouble)(timestamps.tolist()))

report = rca.analyze()
print(str(report.toTextReport()))
```

## Symptom Reference

| Symptom | Description | Related Categories |
|---------|-------------|-------------------|
| `TRIP` | Equipment trip/shutdown | MECHANICAL, CONTROL, EXTERNAL |
| `HIGH_VIBRATION` | Vibration above limits | MECHANICAL |
| `SEAL_FAILURE` | Seal leakage or failure | MECHANICAL |
| `HIGH_TEMPERATURE` | Temperature exceeding limits | MECHANICAL, PROCESS |
| `LOW_EFFICIENCY` | Performance below design | PROCESS, MECHANICAL |
| `PRESSURE_DEVIATION` | Unexpected pressure change | PROCESS, CONTROL |
| `FLOW_DEVIATION` | Unexpected flow change | PROCESS, CONTROL |
| `HIGH_POWER` | Power consumption above normal | PROCESS, MECHANICAL |
| `SURGE_EVENT` | Compressor surge | PROCESS, CONTROL |
| `FOULING` | Fouling or deposit buildup | PROCESS |
| `ABNORMAL_NOISE` | Unusual noise patterns | MECHANICAL |
| `LIQUID_CARRYOVER` | Liquid in gas outlet | PROCESS |

## Built-in Hypothesis Libraries

### Compressor

- **HIGH_VIBRATION**: Bearing wear, impeller imbalance, shaft misalignment, fouled impeller, loose foundation
- **HIGH_TEMPERATURE**: Seal degradation, process gas composition change, fouled intercooler, recycle valve malfunction
- **LOW_EFFICIENCY**: Internal leakage, fouled impeller, eroded impeller, gas composition change
- **SURGE_EVENT**: Low suction flow, blocked suction, anti-surge valve failure, gas composition change
- **TRIP**: Bearing failure, high vibration, high discharge temperature, low lube oil pressure, process upset

### Pump

- **HIGH_VIBRATION**: Cavitation, impeller wear, shaft misalignment
- **LOW_EFFICIENCY**: Impeller wear, internal recirculation
- **TRIP**: Bearing failure, seal failure, low suction pressure

### Separator

- **LIQUID_CARRYOVER**: Demister fouled, high liquid level, feed rate exceeded, foam formation
- **PRESSURE_DEVIATION**: Pressure control valve malfunction, blocked outlet, relief valve leaking
- **FOULING**: Wax deposition, scale buildup, sand accumulation

### Heat Exchanger

- **HIGH_TEMPERATURE**: Fouled tubes, loss of cooling, bypass valve stuck, air in cooling water
- **LOW_EFFICIENCY**: Fouling, tube leak, flow maldistribution
- **PRESSURE_DEVIATION**: Tube blockage, shell-side fouling

### Valve

- **FLOW_DEVIATION**: Trim erosion, actuator malfunction, positioner calibration drift
- **ABNORMAL_NOISE**: Cavitation, flashing, vibration from upstream piping

## Evidence Analysis Methods

Evidence is hypothesis-specific. Built-in hypotheses define expected signal
fingerprints such as `vibration|bearing` increasing, `lubeOilPressure` below
limit, `demisterDp` increasing, or `antiSurgeValve` not opening. Historian and
STID observations are matched by alias pattern. Matching observations become
supporting evidence; opposite observations become contradictory evidence;
unrelated trends are ignored instead of inflating all hypotheses.

Each evidence item includes:

- `supporting`: whether the observation supports or contradicts the hypothesis
- `weight`: the importance of the expected signal in the hypothesis fingerprint
- `sourceReference`: optional source/tag/document reference for traceability

### Trend Analysis
Linear regression on time-series data. Reports slope, R-squared, and percent change.
- **STRONG**: R-squared > 0.7 and > 10% change
- **MODERATE**: R-squared > 0.5
- **WEAK**: R-squared > 0.3

### Threshold Exceedance
Checks how many data points exceed design limits.
- **STRONG**: > 20% of data exceeding limits
- **MODERATE**: > 5%
- **WEAK**: 0-5%

### Rate-of-Change Detection
Identifies sudden step changes using 3-sigma detection on first differences.
Step changes produce STRONG evidence.

### Correlation Analysis
Pearson correlation between all parameter pairs. Reports correlations with |r| > 0.7.
- **STRONG**: |r| > 0.9
- **MODERATE**: 0.7 < |r| < 0.9

### STID Cross-Reference
Compares current (latest) values to STID design values.
- **STRONG**: > 20% deviation from design
- **MODERATE**: 10-20% deviation

## Simulation Verification

The verifier clones the process system, applies a perturbation matching each
hypothesis, runs the modified process, and compares the direction of KPI changes
to the historian data pattern.

Unsupported hypotheses or equipment types return a neutral verification score
(`0.5`) with an explicit simulation limitation. Treat this as "not verified by
the current process model", not as evidence for or against the hypothesis.

### Perturbation Examples

| Hypothesis | Perturbation Applied |
|-----------|---------------------|
| Seal degradation | Reduce polytropic efficiency by 20% |
| Fouled impeller | Reduce polytropic efficiency by 15% |
| Surge condition | Reduce polytropic efficiency by 30% |
| Heat exchanger fouling | Increase outlet temperature by 10°C |
| Loss of cooling | Set outlet = inlet temperature |

### Verification Score

The score (0-1) is based on direction matching:
- Count how many KPIs changed in the same direction (sim vs historian)
- Score = matching directions / total comparisons
- 1.0 = perfect match, 0.5 = neutral, 0.0 = opposite

## Integration with Other Skills

| Skill | Integration |
|-------|------------|
| `neqsim-plant-data` | Read historian data via tagreader API |
| `neqsim-stid-retriever` | Retrieve STID design documents |
| `neqsim-technical-document-reading` | Extract design values from datasheets |
| `neqsim-troubleshooting` | Recovery strategies after diagnosis |
| `neqsim-process-safety` | Link RCA findings to barrier management |
| `neqsim-pid-process-operations` | P&ID context for equipment relationships |

## MCP Tool

Use `runRootCauseAnalysis` MCP tool for programmatic access:

```json
{
  "tool": "runRootCauseAnalysis",
  "arguments": {
    "processJson": "{ ... NeqSim process JSON ... }",
    "equipmentName": "Compressor-1",
    "symptom": "HIGH_VIBRATION",
    "historianCsv": "timestamp,vibration,temperature\n0,3.5,150\n3600,3.8,152\n...",
    "designLimits": {"vibration": [null, 7.1], "temperature": [null, 180]},
    "simulationEnabled": true
  }
}
```

## Output Format

### JSON Report Structure

```json
{
  "equipment": "Compressor-1",
  "equipmentType": "compressor",
  "symptom": "HIGH_VIBRATION",
  "timestamp": "2026-06-15T10:30:00",
  "dataPointsAnalyzed": 2400,
  "parametersAnalyzed": 8,
  "summary": "Most likely root cause: Bearing wear (72.3% confidence)...",
  "hypotheses": [
    {
      "rank": 1,
      "name": "Bearing wear",
      "category": "MECHANICAL",
      "confidence": 0.7230,
      "confidenceScore": 0.7230,
      "priorProbability": 0.3000,
      "likelihoodScore": 0.8500,
      "verificationScore": 0.8500,
      "evidence": [
        {
          "parameter": "vibration_mm_s",
          "observation": "increasing trend...",
          "strength": "STRONG",
          "source": "historian-trend",
          "supporting": true,
          "weight": 3.0,
          "sourceReference": "PI:COMP-100-VIB"
        }
      ],
      "recommendedActions": ["Inspect bearings", "Check lubrication system", "Review vibration spectrum"]
    }
  ]
}
```

## Extending the Framework

### Custom Hypothesis Libraries

```java
HypothesisGenerator gen = new HypothesisGenerator();

// Register custom hypotheses for a specific equipment type and symptom
gen.register("compressor", Symptom.HIGH_VIBRATION,
    new Hypothesis.Builder()
        .name("liquid_ingestion")
        .description("Liquid droplets entering compressor causing vibration")
        .category(Hypothesis.Category.PROCESS)
        .priorProbability(0.1)
        .addExpectedSignal("scrubberLevel|separatorLevel|level",
            Hypothesis.ExpectedBehavior.HIGH_LIMIT, 3.0,
            "High upstream scrubber level supports liquid ingestion")
        .addExpectedSignal("vibration|flowInstability", Hypothesis.ExpectedBehavior.STEP_CHANGE,
            2.5, "Liquid ingestion normally creates a sudden vibration/flow disturbance")
        .addAction("Check scrubber level")
        .addAction("Inspect inlet piping for liquid accumulation")
        .build());

RootCauseAnalyzer rca = new RootCauseAnalyzer(process, "Compressor-1");
rca.setHypothesisGenerator(gen);
```

### Loading Evidence from CSV

```java
EvidenceCollector collector = new EvidenceCollector();
collector.loadFromCsv("path/to/historian_export.csv");
// CSV format: timestamp, param1, param2, ...
```

## Integrated Workflow: Tagreader + STID + NeqSim Simulation

The RCA framework is designed to consume data from three sources simultaneously.
Below is the canonical workflow combining all three.

### Step 1: Retrieve Historian Data via Tagreader

Use the `neqsim-plant-data` skill to pull time-series from the plant historian
(OSIsoft PI or Aspen IP.21) and convert to the CSV format RCA expects.

```python
# Python — pull tagreader data and format for RCA
import pandas as pd

# Tag mapping from STID/P&ID documentation
tag_map = {
    "VT-101.PV": "vibration_mm_s",
    "TT-101.PV": "discharge_temp_C",
    "PT-101.PV": "suction_pressure_bara",
    "FT-101.PV": "mass_flow_kg_hr",
    "JI-101.PV": "power_kW",
}

# Read from historian (tagreader API or CSV export)
df = pd.read_csv("historian_export.csv")
df = df.rename(columns=tag_map)

# Convert to CSV string for RCA input
historian_csv = df.to_csv(index=False)
```

### Step 2: Extract STID Design Data

Use the `neqsim-stid-retriever` and `neqsim-technical-document-reading` skills
to extract design limits and reference values from STID datasheets.

```python
# STID data extracted from datasheet DS-K-101 rev.C
stid_data = {
    "designFlow_kg_hr": "50000",
    "designEfficiency_pct": "82",
    "designDischargePressure_bara": "45.0",
    "maxVibration_mm_s": "7.1",
    "maxDischargeTemp_C": "180",
    "bearingType": "tilting_pad",
    "lastInspectionDate": "2024-06-15",
    "tagreaderSource": "PI Web API: VT-101, TT-101, PT-101, FT-101, JI-101",
    "sourceReference": "STID DS-K-101 rev.C and PI trend 2025-06-01 to 2025-06-15",
}
```

### Step 3: Build Process Model and Run RCA

```python
import json

RootCauseRunner = ns.JClass("neqsim.mcp.runners.RootCauseRunner")

rca_input = {
    "processJson": json.dumps(process_json),
    "equipmentName": "Compressor-1",
    "symptom": "HIGH_VIBRATION",
    "historianCsv": historian_csv,
    "simulationEnabled": True,
    "designLimits": {
        "vibration_mm_s": [None, 7.1],
        "discharge_temp_C": [None, 180.0],
        "mass_flow_kg_hr": [40000, 60000],
    },
    "stidData": stid_data,
}

result = json.loads(str(RootCauseRunner.run(json.dumps(rca_input))))
print(f"Top cause: {result['hypotheses'][0]['name']} "
      f"({result['hypotheses'][0]['confidence']:.1%})")
```

### Step 4: Trace Evidence Back to Source

Every evidence item in the RCA output carries a `sourceReference` field
linking to the original tagreader tag or STID document. This enables
auditable traceability from diagnosis to plant data:

```
hypothesis.evidence[0].sourceReference → "PI:VT-101.PV trend analysis"
hypothesis.evidence[1].sourceReference → "STID DS-K-101: design vibration limit 7.1 mm/s"
```

## MCP Example Catalog

Three pre-built examples demonstrate the integration:

| Example | Equipment | Symptom | Data Sources |
|---------|-----------|---------|-------------|
| `compressor-high-vibration` | Compressor | HIGH_VIBRATION | PI historian, STID datasheet |
| `separator-liquid-carryover` | Separator | LIQUID_CARRYOVER | PI historian (demister dP, level), STID datasheet |
| `hx-fouling` | Heat Exchanger | FOULING | IP.21 historian (outlet temp, shell dP), vendor datasheet |

Access via MCP: `neqsim://examples/root-cause/separator-liquid-carryover`

## Post-Trip Analysis: Detect → Trace → Restart

After the RCA framework identifies *what* went wrong, the post-trip analysis
classes answer three follow-up questions:

1. **What tripped?** — `TripEventDetector` monitors process parameters against thresholds
2. **How did the failure propagate?** — `FailurePropagationTracer` traces cascade through topology
3. **How do we restart?** — `RestartSequenceGenerator` produces an optimised restart plan

### Classes

| Class | Package | Purpose |
|-------|---------|---------|
| `TripEvent` | `neqsim.process.diagnostics` | Immutable data record for a detected trip (equipment, parameter, threshold, actual, severity) |
| `TripEventDetector` | `neqsim.process.diagnostics` | Monitors equipment parameters and fires `TripEvent` when thresholds are breached; supports high/low trips, deadband, first-trip-only mode |
| `FailurePropagationTracer` | `neqsim.process.diagnostics` | Uses `ProcessTopologyAnalyzer` + `DependencyAnalyzer` to BFS-trace how a failure cascades downstream/upstream; produces `PropagationResult` with `PropagationStep` list |
| `RestartStep` | `neqsim.process.diagnostics.restart` | Single step in a restart sequence (equipment, action, precondition, delay, priority) |
| `RestartSequenceGenerator` | `neqsim.process.diagnostics.restart` | Generates topologically-ordered restart plans with safety checks, root-cause verification, utility confirmation, equipment-specific ramp-up actions, and system verification |

### Java Quick Start

```java
// ── Step 1: Detect trip ──
TripEventDetector detector = new TripEventDetector(processSystem);
detector.addTripCondition("Compressor-1", "pressure", 150.0, true,
    TripEvent.Severity.HIGH);
List<TripEvent> trips = detector.check(simulationTime);

// ── Step 2: Trace propagation ──
FailurePropagationTracer tracer = new FailurePropagationTracer(processSystem);
FailurePropagationTracer.PropagationResult propagation = tracer.trace(trips.get(0));
System.out.println(propagation.toTextSummary());

// ── Step 3: Generate restart plan ──
RestartSequenceGenerator generator = new RestartSequenceGenerator(processSystem);
generator.setCustomRampUpTime("Compressor-1", 300.0);  // 5 min ramp-up
RestartSequenceGenerator.RestartPlan plan = generator.generate(propagation);
System.out.println(plan.toTextReport());
String json = plan.toJson();
```

### Python (Jupyter)

```python
TripEventDetector = ns.JClass("neqsim.process.diagnostics.TripEventDetector")
TripEvent = ns.JClass("neqsim.process.diagnostics.TripEvent")
FailurePropagationTracer = ns.JClass("neqsim.process.diagnostics.FailurePropagationTracer")
RestartSequenceGenerator = ns.JClass("neqsim.process.diagnostics.restart.RestartSequenceGenerator")

detector = TripEventDetector(process)
detector.addTripCondition("Compressor-1", "pressure", 150.0, True, TripEvent.Severity.HIGH)
trips = detector.check(0.0)

tracer = FailurePropagationTracer(process)
propagation = tracer.trace(trips[0])
print(str(propagation.toTextSummary()))

generator = RestartSequenceGenerator(process)
plan = generator.generate(propagation)
print(str(plan.toTextReport()))
```

### TripEventDetector Features

- **High/low trips**: `addTripCondition(equip, param, threshold, isHigh, severity)`
- **Deadband**: `setDeadbandFraction(0.02)` — 2% of threshold to avoid chatter
- **First-trip-only**: `setFirstTripOnly(true)` — fire once per condition
- **Parameter types**: `"pressure"`, `"temperature"`, `"flowRate"` (reads from equipment outlet)
- **JSON output**: `detector.toJson()` — lists all detected trips

### FailurePropagationTracer Features

- **Trace by name**: `tracer.trace("Compressor-1")`
- **Trace by trip event**: `tracer.trace(tripEvent)`
- **Max cascade depth**: `tracer.setMaxCascadeDepth(3)`
- **Impact levels**: DIRECT, INDIRECT, POTENTIAL per cascade depth
- **Output**: `PropagationResult` with `toJson()`, `toTextSummary()`

### RestartSequenceGenerator Features

- **From propagation**: `generator.generate(propagationResult)`
- **From equipment list**: `generator.generate(Arrays.asList("Comp-1", "Cooler-1"))`
- **Custom ramp-up**: `generator.setCustomRampUpTime("Comp-1", 300.0)`
- **Custom preconditions**: `generator.setCustomPrecondition("Comp-1", "Verify lube oil pressure > 2 bara")`
- **Equipment-specific actions**: auto-selects restart actions by equipment type (compressor, separator, pump, HX, valve, column, pipe)
- **Output**: `RestartPlan` with `toJson()`, `toTextReport()`
- **Plan structure**: Safety check → Root cause verification → Utilities → Equipment (upstream-first) → System verification

