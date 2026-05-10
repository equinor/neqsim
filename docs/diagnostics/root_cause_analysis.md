---
title: Root Cause Analysis Framework
description: "Equipment diagnostics using Bayesian-inspired hypothesis scoring with multi-source reliability data (IOGP/SINTEF, CCPS, IEEE 493, Lees, OREDA), plant historian time-series, STID design conditions, and NeqSim process simulation verification."
---

# Root Cause Analysis Framework

The `neqsim.process.diagnostics` package provides a structured root cause analysis (RCA)
framework for diagnosing equipment operational anomalies. It integrates four evidence
sources via Bayesian-inspired confidence scoring:

1. **Reliability Data** — Prior probabilities from public reliability databases (IOGP/SINTEF, CCPS 1989, IEEE 493-2007, Lees 2012, SINTEF PDS) with optional OREDA overlay
2. **Historian** — Likelihood scoring from time-series trend, threshold, and correlation analysis
3. **STID** — Cross-reference against design conditions
4. **Simulation** — Verification scoring from NeqSim process model perturbation

## Architecture

```
Symptom (enum)
    │
    ├── HypothesisGenerator  →  Generate candidate hypotheses
    │       │
    │       └── Reliability priors (ReliabilityDataSource)
    │           Sources: IOGP/SINTEF, CCPS, IEEE 493, Lees, OREDA
    │
    ├── EvidenceCollector     →  Analyze historian + STID data against expected signals
    │       │
    │       ├── Trend analysis (linear regression R²)
    │       ├── Threshold exceedance
    │       ├── Rate-of-change step detection (3σ)
    │       └── Pearson correlation (|r| > 0.7)
    │
    ├── SimulationVerifier    →  Clone ProcessSystem, perturb, compare
    │
    └── RootCauseReport       →  Ranked hypotheses with confidence scores
```

Each built-in hypothesis carries expected signal fingerprints. For example,
compressor bearing degradation expects increasing vibration and bearing
temperature, while liquid ingestion expects a step change in vibration and high
upstream separator or scrubber level. Evidence that matches the fingerprint is
marked as supporting; opposite behavior is marked as contradictory; unrelated
trends are ignored.

## Confidence Scoring

Each hypothesis receives a confidence score calculated as:

$$
\text{confidence} = P_{\text{prior}} \times L_{\text{historian}} \times V_{\text{simulation}}
$$

Where:
- $P_{\text{prior}}$ — Prior probability from reliability data (IOGP/SINTEF, CCPS, IEEE 493, etc.)
- $L_{\text{historian}}$ — Likelihood score from time-series evidence
- $V_{\text{simulation}}$ — Verification score from simulation direction-of-change matching

## Data Sources

`ReliabilityDataSource` loads failure rates and repair times from multiple public databases,
requiring no commercial license:

| Source | Coverage | Access |
|--------|----------|--------|
| IOGP Report 434 / SINTEF | Offshore O&G equipment, safety systems | Free |
| CCPS Process Equipment Reliability Data (1989) | Vessels, piping, instruments, valves | Published handbook |
| IEEE 493-2007 (Gold Book) | Industrial power and electrical equipment | Purchasable standard |
| Lees' Loss Prevention (2012) | Generic process industry failure rates | Textbook |
| SINTEF PDS Data Handbook | Safety-instrumented system failure rates | Purchasable |
| API 689 | Rotating and process equipment reliability | Purchasable standard |
| OREDA (optional) | Offshore equipment (consortium license) | Commercial |

Supplementary CSVs are loaded automatically. Primary data takes precedence over supplementary
entries, so users can override any value by editing `equipment_reliability.csv`.

## Supported Equipment Types

| Equipment Type | Symptoms | Hypothesis Categories |
|---------------|----------|----------------------|
| Compressor | HIGH_VIBRATION, HIGH_TEMPERATURE, LOW_EFFICIENCY, SURGE_EVENT, TRIP | Bearing wear, seal leak, fouling, surge, valve fault |
| Pump | HIGH_VIBRATION, LOW_EFFICIENCY, TRIP | Cavitation, impeller wear, seal failure, motor fault |
| Separator | LIQUID_CARRYOVER, PRESSURE_DEVIATION, FOULING | Demister damage, control malfunction, wax/hydrate, inlet device |
| Heat Exchanger | HIGH_TEMPERATURE, LOW_EFFICIENCY, PRESSURE_DEVIATION | Fouling, tube leak, baffle damage, bypass |
| Valve | FLOW_DEVIATION, ABNORMAL_NOISE | Trim erosion, actuator fault, positioner drift, cavitation |

## Java Usage

```java
import neqsim.process.diagnostics.*;

// Build process
ProcessSystem process = ...; // existing process model
process.run();

// Create analyzer
RootCauseAnalyzer rca = new RootCauseAnalyzer(process, "Export Compressor");
rca.setSymptom(Symptom.HIGH_VIBRATION);

// Optional: add historian data
Map<String, double[]> data = new HashMap<>();
data.put("vibration_mm_s", new double[]{2.1, 2.3, 2.8, 3.5, 4.2});
data.put("bearing_temp_C", new double[]{65, 67, 72, 78, 85});
double[] timestamps = {0, 1, 2, 3, 4};
rca.setHistorianData(data, timestamps);

// Optional: design limits
rca.setDesignLimit("vibration_mm_s", 0.0, 4.5);

// Run analysis
RootCauseReport report = rca.analyze();

// Output
System.out.println(report.toTextReport());
String json = report.toJson();
```

## Python (Jupyter) Usage

```python
RootCauseAnalyzer = ns.JClass("neqsim.process.diagnostics.RootCauseAnalyzer")
Symptom = ns.JClass("neqsim.process.diagnostics.Symptom")

rca = RootCauseAnalyzer(process, "Export Compressor")
rca.setSymptom(Symptom.HIGH_VIBRATION)
report = rca.analyze()

# Get JSON report
import json
result = json.loads(str(report.toJson()))
for h in result["hypotheses"]:
    print(f"{h['rank']}. {h['name']} — confidence: {h['confidenceScore']:.3f}")
```

## MCP Tool

The `runRootCauseAnalysis` MCP tool exposes this framework to LLM agents:

```json
{
    "processJson": "{\"fluid\": {...}, \"process\": [...]}",
    "equipmentName": "Export Compressor",
    "symptom": "HIGH_VIBRATION",
    "historianCsv": "timestamp,vibration,bearing_temp\n0,2.1,65\n1,2.3,67\n2,2.8,72",
    "designLimits": { "vibration": [0, 4.5] },
    "simulationEnabled": true
}
```

`processJson` is passed as a JSON string to the MCP runner. The schema catalog
entry `run_root_cause_analysis` shows the exact input fields and output shape.

## Custom Hypotheses

Register domain-specific hypotheses for specialized equipment:

```java
HypothesisGenerator generator = new HypothesisGenerator();
generator.register("compressor", Symptom.HIGH_VIBRATION,
    new Hypothesis.Builder()
        .name("Coupling misalignment")
        .description("Flexible coupling misalignment causing 1x/2x vibration")
        .category(Hypothesis.Category.MECHANICAL)
        .priorProbability(0.15)
        .addExpectedSignal("vibration|2x|axialVibration", Hypothesis.ExpectedBehavior.INCREASE,
            2.5, "Misalignment normally increases axial and 2x vibration")
        .addAction("Laser alignment check")
        .build());

RootCauseAnalyzer rca = new RootCauseAnalyzer(process, "Compressor");
rca.setHypothesisGenerator(generator);
```

## Classes

| Class | Description |
|-------|-------------|
| `Symptom` | Enum of 12 observable symptoms (TRIP, HIGH_VIBRATION, etc.) |
| `Hypothesis` | Ranked failure hypothesis with evidence, confidence, and actions |
| `HypothesisGenerator` | Registry-based generator with built-in equipment libraries |
| `EvidenceCollector` | Time-series analysis (trend, threshold, correlation) |
| `SimulationVerifier` | Process model perturbation and direction-of-change scoring |
| `RootCauseReport` | Ranked report with JSON, text, and results map output |
| `RootCauseAnalyzer` | Main orchestrator integrating all components |
