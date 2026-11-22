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
4. **Autonomous findings** — Hypothesis-specific anomaly and topology evidence
5. **Simulation** — Coverage-qualified, magnitude-aware verification from NeqSim perturbation

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
    ├── AnomalyScanner + topology → Add matched autonomous evidence to ranking
    ├── SimulationVerifier    →  Clone, perturb, compare normalized response magnitude
    │
    └── RootCauseReport       →  Ranked hypotheses with confidence scores
```

Each built-in hypothesis carries expected signal fingerprints. For example,
compressor bearing degradation expects increasing vibration and bearing
temperature, while liquid ingestion expects a step change in vibration and high
upstream separator or scrubber level. Evidence that matches the fingerprint is
marked as supporting; opposite behavior is marked as contradictory; unrelated
trends are ignored.

## Diagnosing a flow discrepancy across connected equipment

A filling separator or a packing flowline can take in more mass than it delivers.
Use `ProcessBalanceDiagnosis.assess(window, inventoryChangeBoundsKg, meters)` on
a common historian window to test the physical explanations. The same calculation
is available in the optional `massBalance` block of `runRootCauseAnalysis`.
The nested result supplies independent evidence alongside the ordinary RCA rankings.

### Define the measurement boundary

Identify non-overlapping control volumes on the P&ID. Supply each total inventory
in kg, including gas, oil and water as applicable. A level signal alone needs a
level-to-volume curve, phase densities and an allowance for their uncertainty.
Use signed instantaneous kg/s for every boundary crossing, including vents,
drains and recycle crossings where measured. Give a shared transfer meter one
tag and connect it to both volumes. A recycle wholly within one volume cancels.
Record the clock basis, tag quality, unit conversion, operating mode and source
of each bound. Split windows at gaps or cadence changes. Compressed event
samples and interval-average meters need preprocessing on an appropriate basis.

For a window of duration $T=(n-1)\Delta t$, the signed residual for volume $j$ is

$$r_j = \sum_e A_{je}\overline{q}_e - (M_{j,n}-M_{j,1})/T.$$

Here $A$ is +1 for inflow and −1 for outflow, and the mean of each meter is
trapezoidally integrated. Its absolute error budget is the sum of the incident
healthy meter bounds in kg/s plus the supplied inventory-change bound divided
by $T$. Include integration, timing and inventory-model error in the budget.
For independent endpoint measurements with only absolute bounds available,
summing both endpoint bounds is conservative. A constant inventory offset
cancels. These are user-supplied engineering limits, with no distributional
or probability interpretation.

Each candidate must fit all balances using the same signed healthy error for every shared meter. A high-reading shared
meter gives a negative residual upstream and a positive one downstream. An
unmetered outflow gives a positive residual only at its own volume. The solver bounds the
compatible mean rate subject to all meter and inventory errors simultaneously.
It screens the listed single faults. Variable deviations can cancel within a
window. Inventory drift, omitted transfers and simultaneous faults can mimic a
listed candidate.

| Result | Interpretation and next check |
| --- | --- |
| `WITHIN_BOUNDS` | One set of healthy measurement errors explains every balance. Document the smallest discrepancy the measurements could resolve. |
| `SINGLE_CANDIDATE` | One listed signature fits. Confirm independently, for example with a redundant flow measurement. |
| `AMBIGUOUS` | Report every surviving candidate and signed rate interval. A single vessel's balance alone can confound an underestimated export and an unmetered outflow. |
| `UNEXPLAINED` | No listed single fault fits all volumes. Recheck inventory conversion, timing, omitted streams and overlapping faults. |

### Worked separator and receiver case

Over 2 s, the feed samples are 6, 10 and 6 kg/s. Gas leaves at 2 kg/s,
liquid transfers at 3 kg/s and the receiver exports 2 kg/s. Separator inventory
rises from 100 to 106 kg and receiver inventory from 50 to 52 kg. Both balances
close. Adding a +1 kg/s measurement bias on the shared liquid transfer gives
residuals of −1 and +1 kg/s. With an absolute bound of 0.02 kg/s for each meter
and change bounds of 0.08 kg and 0.12 kg respectively, the shared meter is the
only listed candidate and its signed bias interval is +0.90 to +1.10 kg/s.

To reproduce the biased case, add this property to an existing equipment RCA
request. The sample arrays correspond to 0, 1 and 2 s.

```json
{
  "massBalance": {
    "sampleIntervalSeconds": 1,
    "signals": {
      "separator_kg": [100, 103, 106], "receiver_kg": [50, 51, 52],
      "feed": [6, 10, 6], "gas": [2, 2, 2], "transfer": [4, 4, 4], "export": [2, 2, 2]
    },
    "inventoryChangeBoundsKg": {"separator_kg": 0.08, "receiver_kg": 0.12},
    "meters": [
      {"tag": "feed", "from": null, "to": "separator_kg", "errorKgPerS": 0.02},
      {"tag": "gas", "from": "separator_kg", "to": null, "errorKgPerS": 0.02},
      {"tag": "transfer", "from": "separator_kg", "to": "receiver_kg", "errorKgPerS": 0.02},
      {"tag": "export", "from": "receiver_kg", "to": null, "errorKgPerS": 0.02}
    ]
  }
}
```

### Literature and qualification

| Source | Application to troubleshooting |
| --- | --- |
| Venkatasubramanian et al. (2003), [quantitative model-based diagnosis](https://doi.org/10.1016/S0098-1354(02)00160-6) and [qualitative models](https://doi.org/10.1016/S0098-1354(02)00161-8) | Use conservation equations to calculate residuals and the flowsheet to explain their signs. |
| [PHMSA study, Appendix C](https://primis.phmsa.dot.gov/rd/FileGet/11429/02B%20DTPH5614H000007_-_0339-1404_Final_Appendix_Gas.pdf) | Inventory change and meter uncertainty enter the assessment during pipeline transients. |
| [Han et al. (2004), industrial inventory reconciliation](https://skoge.folk.ntnu.no/prost/proceedings/dycops04/pdffiles/papers/133.pdf) | Redundant inventory and flow measurements can help distinguish calibration errors from process changes. |
| [Synchronized node imbalances (2007)](https://doi.org/10.1016/j.compchemeng.2006.09.007) | Time alignment and process dynamics change how node discrepancies can be interpreted for sensor biases and losses. Here, measured inventory change supplies the accumulation term. |
| [FPSO three-phase separator diagnosis (2026)](https://doi.org/10.1016/j.jtice.2026.106626) | Sparse labelled incidents motivate expert hypotheses. The balance screen gives those hypotheses an independent physical consistency check when the required tags exist. |
| [Stief et al. (2018), multiphase flow facility](https://doi.org/10.1016/j.ifacol.2018.09.327) | Assess alarms and measurements against separately labelled normal and seeded-fault runs at more than one operating point. |
| [Vargas et al. (2019), offshore well events](https://doi.org/10.1016/j.petrol.2019.106223) | The Petrobras 3W benchmark supports well-event classification. Its well-event labels alone cannot establish topside balance accuracy. |

The analytic regressions exercise healthy accumulation, signed transfer bias,
local diversion, shared-error cancellation, uncertainty thresholds and incompatible faults.
A PR-EOS gas/condensate separator test withholds a 20% diversion of the gas stream and compares
the recovered missing flow against the independently simulated splitter branch.
The committed small-network test compares every feasible candidate and both rate endpoints
with exhaustive control-volume subset balances in 120 seeded cases with two to six inventories. Field qualification
requires synchronized inventories and metering with independently established
events. Reserve entire operating runs for testing and report event-level false
alarms, missed events, time to detection and ambiguous diagnoses by regime.

## Confidence Scoring

Each hypothesis receives a confidence score calculated as:

$$
\text{confidence} = P_{\text{prior}} \times L_{\text{historian}} \times V_{\text{simulation}}
$$

Where:
- $P_{\text{prior}}$ — Prior probability from reliability data (IOGP/SINTEF, CCPS, IEEE 493, etc.)
- $L_{\text{historian}}$ — Likelihood score from time-series evidence
- $V_{\text{simulation}}$ — Verification score from direction and normalized response-magnitude matching

Likelihood and verification each carry an `EvaluationStatus`: `UNKNOWN`, `EVALUATED`,
`UNSUPPORTED`, or `FAILED`. Only `EVALUATED` scores participate in the product. An
evaluated score of exactly zero is contradictory evidence, while a stage that was not
run or could not run remains explicitly neutral. JSON includes both statuses plus
simulation KPI coverage and comparison count.

## Reproducible Diagnosis Cases

`DiagnosisCase` records the equipment, historian time window, source provenance,
process-model identity/revision, operating context, and data-quality metadata. Attach an
explicit case when source query IDs or model revisions are known; otherwise the analyzer
derives a minimal case and historian quality counts.

```java
DiagnosisCase diagnosisCase = new DiagnosisCase("Export Compressor")
    .setCaseId("trip-2026-07-19")
    .setTimeWindow(1784480400.0, 1784484000.0)
    .setProcessModel("export-compression", "model-rev-7")
    .addDataSource("PI", "compressed-query-id-42")
    .addOperatingContext("mode", "recycle")
    .addDataQuality("coverage", "98%");
rca.setDiagnosisCase(diagnosisCase);
```

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
String textReport = report.toTextReport();
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
    "simulationEnabled": true,
    "diagnosisCase": {
        "caseId": "trip-2026-07-19",
        "processModelId": "export-compression",
        "processModelRevision": "model-rev-7",
        "dataSources": { "PI": "compressed-query-id-42" },
        "operatingContext": { "mode": "recycle" },
        "dataQuality": { "coverage": "98%" }
    }
}
```

`processJson` is passed as a JSON string to the MCP runner. The schema catalog
entry `run_root_cause_analysis` shows the exact input fields and output shape.
The returned `diagnosisCase` object and per-hypothesis evaluation statuses are additive,
so consumers of the existing report fields continue to work.

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
| `DiagnosisCase` | Reproducible window, provenance, model identity, context, and quality metadata |
| `SimulationVerifier` | Process perturbation with explicit status, KPI coverage, and magnitude-aware scoring |
| `RootCauseReport` | Ranked report with JSON, text, and results map output |
| `RootCauseAnalyzer` | Main orchestrator integrating all components |
