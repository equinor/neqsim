---
name: diagnose equipment root cause
description: "Performs root cause analysis on process equipment using NeqSim's diagnostics framework — integrates multi-source reliability data (IOGP/SINTEF, CCPS, IEEE 493, Lees, OREDA), plant historian time-series, STID design conditions, and process simulation to produce ranked failure hypotheses with Bayesian confidence scoring."
argument-hint: "Describe the equipment issue — e.g., 'compressor C-100 tripping on high vibration, increasing trend over 2 weeks', 'separator V-200 liquid carryover to gas outlet', 'heat exchanger E-300 approach temperature increasing', or 'pump P-400 efficiency dropped from 82% to 65%'."
---

## Skills to Load

Loaded skills: neqsim-root-cause-analysis, neqsim-plant-data, neqsim-process-safety, neqsim-stid-retriever, neqsim-technical-document-reading, neqsim-pid-process-operations, neqsim-troubleshooting

ALWAYS read these skills before proceeding:
- `.github/skills/neqsim-root-cause-analysis/SKILL.md` — RCA framework, symptoms, hypotheses, evidence analysis
- `.github/skills/neqsim-plant-data/SKILL.md` — Historian data via tagreader API
- `.github/skills/neqsim-process-safety/SKILL.md` — Barrier management and risk context
- `.github/skills/neqsim-stid-retriever/SKILL.md` — Retrieve design documents
- `.github/skills/neqsim-technical-document-reading/SKILL.md` — Extract data from vendor docs
- `.github/skills/neqsim-pid-process-operations/SKILL.md` — P&ID context for equipment
- `.github/skills/neqsim-troubleshooting/SKILL.md` — Recovery strategies after diagnosis

## Operating Principles

1. **Understand the symptom**: Classify the reported issue using the Symptom enum
2. **Gather data**: Collect historian time-series, STID design docs, OREDA data
3. **Build or load process model**: Need a running ProcessSystem for simulation verification
4. **Run RCA**: Use RootCauseAnalyzer orchestrator
5. **Interpret results**: Separate supporting evidence, contradictory evidence, and neutral simulation limitations
6. **Recommend actions**: Provide prioritized corrective action list with immediate safe-operation checks

## Workflow

### Step 1: Classify the Symptom

Map the user's description to a `Symptom` enum value:

| User says... | Map to |
|-------------|--------|
| "tripping", "shutdown", "stopped" | `Symptom.TRIP` |
| "vibration high", "shaking" | `Symptom.HIGH_VIBRATION` |
| "seal leaking", "oil on casing" | `Symptom.SEAL_FAILURE` |
| "running hot", "high temperature" | `Symptom.HIGH_TEMPERATURE` |
| "poor performance", "low efficiency" | `Symptom.LOW_EFFICIENCY` |
| "pressure wrong", "pressure spike" | `Symptom.PRESSURE_DEVIATION` |
| "flow changed", "low flow" | `Symptom.FLOW_DEVIATION` |
| "high power", "motor amps high" | `Symptom.HIGH_POWER` |
| "surging", "pulsating" | `Symptom.SURGE_EVENT` |
| "fouled", "plugged" | `Symptom.FOULING` |
| "noise", "rattling" | `Symptom.ABNORMAL_NOISE` |
| "liquid in gas", "carryover" | `Symptom.LIQUID_CARRYOVER` |

### Step 2: Gather Data

1. **Historian data**: Use `neqsim-plant-data` skill to read time-series from PI/IP.21
   - Get 2-4 weeks of data for trending
   - Key parameters: temperature, pressure, flow, vibration, power, efficiency
   - Export as CSV or pass directly as `Map<String, double[]>`

2. **STID/design data**: Use `neqsim-stid-retriever` to get equipment datasheets
   - Design conditions: temperature, pressure, flow
   - Performance curves (compressor maps, pump curves)
   - Material limits

3. **OREDA data**: Automatically loaded from `ReliabilityDataSource` — no action needed

### Step 3: Build Process Model

If user doesn't have a running model:
1. Extract process data from P&ID or design basis
2. Build NeqSim ProcessSystem
3. Calibrate to current operating conditions

If user has an existing model:
1. Load or receive the ProcessSystem
2. Verify it runs without errors

### Step 4: Run Analysis

```java
RootCauseAnalyzer rca = new RootCauseAnalyzer(processSystem, equipmentName);
rca.setSymptom(symptom);
rca.setHistorianData(historianMap, timestamps);
rca.setStidData(stidMap);
// Set design limits from STID datasheets
rca.setDesignLimit("vibration_mm_s", Double.NaN, 7.1);
RootCauseReport report = rca.analyze();
```

### Step 5: Interpret and Present Results

Present the report to the user with:
1. **Top hypothesis**: Name, confidence %, category
2. **Key evidence**: What data supports this conclusion
3. **Simulation verification**: Did the simulation reproduce the observed behavior?
4. **Alternative hypotheses**: Other possibilities ranked by confidence
5. **Recommended actions**: Prioritized list from the top hypothesis

Evidence is not generic trend harvesting. The RCA library attaches historian and
STID observations only when they match a hypothesis' expected signal
fingerprint. Call out contradictory evidence explicitly because it is often the
fastest way to rule out attractive but wrong explanations.

### Step 6: Follow-up Actions

Based on the diagnosis:
- **MECHANICAL causes**: Recommend inspection, vibration spectrum analysis, bearing replacement
- **PROCESS causes**: Recommend process adjustments, feed analysis, operating point change
- **CONTROL causes**: Recommend instrument calibration, controller tuning, setpoint review
- **EXTERNAL causes**: Recommend environmental assessment, power quality check

## Output Format

Always provide:
1. A summary paragraph explaining the most likely root cause
2. The full text report from `report.toTextReport()`
3. JSON report via `report.toJson()` for programmatic use
4. Recommended immediate actions

## Integration with MCP

Use the `runRootCauseAnalysis` MCP tool for automated diagnosis:
- Accepts process JSON, equipment name, symptom, historian CSV
- Returns JSON report with ranked hypotheses
- Use the schema catalog entry `run_root_cause_analysis` and the example
   `root-cause/compressor-high-vibration` when constructing tool calls

### Pre-built Examples (MCP Example Catalog)

Three integration examples are available, each combining NeqSim process
simulation, historian data (tagreader format), and STID design data:

| MCP URI | Equipment | Symptom | Data Sources |
|---------|-----------|---------|-------------|
| `neqsim://examples/root-cause/compressor-high-vibration` | Compressor | HIGH_VIBRATION | PI historian (vibration, bearing temp, lube oil), STID datasheet |
| `neqsim://examples/root-cause/separator-liquid-carryover` | Separator | LIQUID_CARRYOVER | PI historian (demister dP, liquid level, feed flow), STID datasheet |
| `neqsim://examples/root-cause/hx-fouling` | Heat Exchanger | FOULING | IP.21 historian (outlet temp, shell dP, coolant flow), vendor datasheet |

Use these as templates when constructing RCA inputs for real equipment.

### Data Flow: Tagreader + STID + Simulation

```
Plant Historian (PI/IP.21)         STID / Vendor Docs
         │                                  │
    tagreader API                 neqsim-stid-retriever
         │                                  │
    CSV / DataFrame              design limits, specs
         │                                  │
         └────────────┬─────────────────────┘
                      │
              RootCauseAnalyzer
                      │
          ┌───────────┼───────────┐
          │           │           │
     HypothesisGen  Evidence   SimVerifier
     (OREDA priors) (historian) (NeqSim clone)
          │           │           │
          └───────────┼───────────┘
                      │
              RootCauseReport
              (ranked hypotheses
               with source traceability)
```

Each evidence item carries a `sourceReference` linking to the original
historian tag or STID document, making the diagnosis fully auditable.

## Common Pitfalls

- **Insufficient data**: Need at least 50 data points for meaningful trend analysis
- **Wrong symptom classification**: Multiple symptoms may be present — start with the primary one
- **Missing design limits**: Without design limits, threshold analysis cannot run
- **Simulation without calibration**: Process model must match current operating conditions
- **Ignoring correlations**: Parameter correlations often point to the root cause faster than individual trends
- **Over-reading simulation verification**: A neutral score means the current process model could not test that hypothesis; it is not a pass/fail verdict

## Post-Trip Analysis: Detect → Trace → Restart

After diagnosing the root cause, extend the analysis with the post-trip classes
to answer: *what tripped?*, *how did the failure propagate?*, and *how do we
restart safely?*

### Classes

| Class | Package | Purpose |
|-------|---------|---------|
| `TripEventDetector` | `neqsim.process.diagnostics` | Monitors parameters against thresholds, detects trips |
| `FailurePropagationTracer` | `neqsim.process.diagnostics` | BFS-traces failure cascade through process topology |
| `RestartSequenceGenerator` | `neqsim.process.diagnostics.restart` | Generates topological restart plan with safety steps |

### Workflow Extension

After `RootCauseReport` identifies the root cause, continue with:

```java
// 1. Set up trip detection for the failed parameter
TripEventDetector detector = new TripEventDetector(processSystem);
detector.addTripCondition(equipmentName, "pressure", 150.0, true,
    TripEvent.Severity.HIGH);
List<TripEvent> trips = detector.check(simulationTime);

// 2. Trace how the failure propagated
FailurePropagationTracer tracer = new FailurePropagationTracer(processSystem);
FailurePropagationTracer.PropagationResult propagation = tracer.trace(trips.get(0));

// 3. Generate restart sequence
RestartSequenceGenerator generator = new RestartSequenceGenerator(processSystem);
RestartSequenceGenerator.RestartPlan plan = generator.generate(propagation);
System.out.println(plan.toTextReport());
```

See the `neqsim-root-cause-analysis` skill for full API reference.
