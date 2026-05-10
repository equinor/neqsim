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
