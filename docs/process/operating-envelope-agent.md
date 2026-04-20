---
title: "Operating Envelope and Trip Prevention Agent"
description: "AI-powered advisory system for monitoring process operating margins, predicting equipment trips, and recommending mitigations using NeqSim thermodynamic calculations. Covers hydrate risk, compressor surge, separator levels, composition drift, and cascade impact analysis."
---

# Operating Envelope & Trip Prevention Agent

The `neqsim.process.envelope` package provides an AI-powered advisory layer that continuously evaluates the health of a running process simulation. It monitors operating margins on every piece of equipment, predicts trips before they happen, and recommends corrective actions — all without ever writing to the control system.

## Overview

In real-time process operations, equipment trips and unplanned shutdowns are often preceded by gradual margin erosion that operators may not notice until it is too late. The Operating Envelope Agent bridges this gap by:

1. **Scanning** all equipment in a `ProcessSystem` and computing the margin between current operating values and their design/safety limits.
2. **Tracking** how those margins change over time using linear regression trend analysis.
3. **Predicting** when a margin will reach zero (trip), with severity classification.
4. **Recommending** operator actions from a library of pre-built mitigation playbooks.
5. **Analyzing** the impact of feed composition changes on thermodynamic safety boundaries (hydrate temperature, dew points).
6. **Tracing** how a perturbation on one piece of equipment cascades downstream through the process topology.

All outputs are **advisory only**. The agent never modifies setpoints, valve positions, or controller modes.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                  ProcessDigitalTwinLoop                  │
│  (reads plant data → updates model → evaluates → publishes) │
│                                                         │
│  ┌───────────────────────────────────────────────────┐  │
│  │           OperatingEnvelopeAgent                   │  │
│  │                                                   │  │
│  │  ┌──────────────────────────┐                     │  │
│  │  │ ProcessOperatingEnvelope │ ← scans equipment   │  │
│  │  │   OperatingMargin[]      │   margins           │  │
│  │  │   MarginTracker[]        │ ← trend analysis    │  │
│  │  └──────────────────────────┘                     │  │
│  │                                                   │  │
│  │  ┌──────────────────────────┐                     │  │
│  │  │ CompositionChangeAnalyzer│ ← ΔT_hydrate,      │  │
│  │  │                          │   ΔT_dew, ΔZ       │  │
│  │  └──────────────────────────┘                     │  │
│  │                                                   │  │
│  │  ┌──────────────────────────┐                     │  │
│  │  │   MitigationStrategy     │ ← playbook library  │  │
│  │  └──────────────────────────┘                     │  │
│  │                                                   │  │
│  │  Output: AgentEvaluationResult                    │  │
│  │    → TripPrediction[]                             │  │
│  │    → MitigationAction[]                           │  │
│  │    → EnvelopeDashboardData (JSON for operator UI) │  │
│  └───────────────────────────────────────────────────┘  │
│                                                         │
│  ┌───────────────────────────────────────────────────┐  │
│  │       CascadeImpactAnalyzer                       │  │
│  │  (what happens downstream if X changes?)          │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

## Package Classes

| Class | Purpose |
|-------|---------|
| `OperatingMargin` | Single margin measurement: current value vs. limit, with status thresholds |
| `MarginTracker` | Time-series tracking with linear regression trend prediction |
| `ProcessOperatingEnvelope` | Scans all equipment in a ProcessSystem, produces margin inventory |
| `CompositionChangeAnalyzer` | Evaluates how feed composition changes affect hydrate T, dew points, Z-factor |
| `MitigationAction` | Recommended corrective action (advisory only) |
| `TripPrediction` | Predicted trip event with severity and time-to-breach |
| `MitigationStrategy` | Library of pre-built mitigation playbooks by threat type |
| `OperatingEnvelopeAgent` | Main orchestrator tying all components together |
| `AgentEvaluationResult` | Immutable output per evaluation cycle (Builder pattern) |
| `EnvelopeDashboardData` | Structured JSON for operator displays |
| `CascadeImpactAnalyzer` | Traces perturbation through process topology |
| `ProcessDigitalTwinLoop` | Full monitoring loop: read data → update model → evaluate → publish |

## Quick Start

### Basic Evaluation

```java
// Build and run your process
ProcessSystem process = new ProcessSystem();
// ... add equipment ...
process.run();

// Create the agent and evaluate
OperatingEnvelopeAgent agent = new OperatingEnvelopeAgent(process);
AgentEvaluationResult result = agent.evaluate();

// Check overall status
System.out.println("Status: " + result.getOverallStatus());
System.out.println("Summary: " + result.getSummaryMessage());

// Iterate over ranked margins (most critical first)
for (OperatingMargin m : result.getRankedMargins()) {
    System.out.printf("  %s: %.1f%% (%s)%n",
        m.getKey(), m.getMarginPercent(), m.getStatus());
}

// Check for imminent trips
for (TripPrediction trip : result.getTripPredictions()) {
    System.out.printf("  TRIP WARNING: %s in %.0f min (%s)%n",
        trip.getEquipmentName(),
        trip.getEstimatedTimeToTripMinutes(),
        trip.getSeverity());
}

// Get recommended actions
for (MitigationAction action : result.getMitigationActions()) {
    System.out.printf("  [%s] %s on %s%n",
        action.getPriority(), action.getDescription(),
        action.getTargetEquipment());
}
```

### Composition Change Analysis

```java
// Set baseline composition
agent.setCompositionBaseline(originalFluid);
agent.setOperatingConditions(25.0, 50.0);  // °C, bara

// When feed changes, update and re-evaluate
agent.updateFeedFluid(newFluid);
AgentEvaluationResult result = agent.evaluate();

// Or use the analyzer directly
CompositionChangeAnalyzer analyzer = new CompositionChangeAnalyzer(baselineFluid);
CompositionChangeAnalyzer.ImpactReport report =
    analyzer.analyzeImpact(newFluid, 25.0, 50.0);

if (report.hasSignificantImpact()) {
    System.out.println("Hydrate temp shift: " + report.getHydrateTempShift() + " K");
    System.out.println("HC dew point shift: " + report.getHcDewTempShift() + " K");
    System.out.println("Significant impacts: " + report.getSignificantImpacts());
}
```

### Cascade Impact Analysis

```java
ProcessOperatingEnvelope envelope = new ProcessOperatingEnvelope(process);
envelope.evaluate();

CascadeImpactAnalyzer cascade = new CascadeImpactAnalyzer(process, envelope);

// What is downstream of HP Separator?
List<String> downstream = cascade.getDownstreamChain("HP Separator");

// What happens if we change compressor discharge pressure?
List<CascadeImpactAnalyzer.ImpactNode> impacts =
    cascade.analyzeImpact("Export Compressor", "outletPressure", 120.0, "bara");

for (CascadeImpactAnalyzer.ImpactNode node : impacts) {
    System.out.printf("  %s: %s margin changed by %.1f%%%n",
        node.getEquipmentName(), node.getMarginKey(), node.getDeltaPercent());
}
```

### Digital Twin Loop

```java
OperatingEnvelopeAgent agent = new OperatingEnvelopeAgent(process);
ProcessDigitalTwinLoop loop = new ProcessDigitalTwinLoop(process, agent);

// Set up data provider (plant historian, OPC, or static for testing)
Map<String, Double> liveData = new HashMap<String, Double>();
liveData.put("Feed Gas.temperature", 30.0);
liveData.put("Feed Gas.pressure", 48.0);
loop.setDataProvider(new ProcessDigitalTwinLoop.StaticDataProvider(liveData));

// Add consumer for results
loop.addResultConsumer(new ProcessDigitalTwinLoop.ResultConsumer() {
    public void consume(AgentEvaluationResult result) {
        // Push to dashboard, alarm system, historian, etc.
        EnvelopeDashboardData dashboard = EnvelopeDashboardData.fromResult(result);
        System.out.println(dashboard.toJson());
    }
});

// Execute one cycle (call from your scheduler — e.g., every 30 seconds)
AgentEvaluationResult result = loop.executeCycle();
```

### Dashboard JSON Output

```java
AgentEvaluationResult result = agent.evaluate();
EnvelopeDashboardData dashboard = EnvelopeDashboardData.fromResult(result);
String json = dashboard.toJson();
// Returns structured JSON with:
// - overallStatus, timestamp
// - equipmentCards[] with marginGauges[] per equipment
// - tripAlerts[] with severity colors
// - advisories[] with priority and descriptions
```

## Operating Margin Model

### Status Thresholds

Each `OperatingMargin` automatically classifies into one of five statuses based on how close the current value is to the limit:

| Status | Margin % | Meaning |
|--------|----------|---------|
| NORMAL | > 20% | Comfortable operating range |
| ADVISORY | 10–20% | Approaching limit — increase monitoring |
| WARNING | 5–10% | Close to limit — consider corrective action |
| CRITICAL | 0–5% | Very close to limit — act now |
| VIOLATED | ≤ 0% | Limit exceeded — trip may have occurred |

### Supported Margin Types

| MarginType | Direction | Equipment | Description |
|------------|-----------|-----------|-------------|
| PRESSURE | HIGH | Separator, Vessel | Operating vs. max design pressure |
| TEMPERATURE | HIGH | Compressor | Discharge temperature vs. max allowed |
| LEVEL | HIGH/LOW | Separator | Liquid level vs. HH/LL setpoints |
| SURGE | LOW | Compressor | Distance to surge line |
| SPEED | HIGH | Compressor | Speed vs. mechanical maximum |
| VALVE_OPENING | HIGH | ThrottlingValve | Valve opening vs. 95% (near saturation) |
| HYDRATE | LOW | Stream | Temperature above hydrate formation |
| HC_DEW_POINT | LOW | Stream | Temperature above HC dew point |
| WATER_DEW_POINT | LOW | Stream | Temperature above water dew point |
| CUSTOM | any | any | User-defined custom margin |

### Trend Prediction

`MarginTracker` maintains a circular buffer of timestamped margin samples and fits a least-squares linear regression. From the slope:

- **Time to breach**: extrapolates when the margin will reach zero
- **Trend direction**: IMPROVING, STABLE, DEGRADING, or RAPIDLY_DEGRADING
- **R-squared**: confidence in the trend fit (higher = more reliable)

The agent uses these trends to generate `TripPrediction` objects with automatic severity classification:

| Severity | Time to Breach |
|----------|---------------|
| IMMINENT | ≤ 2 minutes |
| HIGH | ≤ 10 minutes |
| MEDIUM | ≤ 30 minutes |
| LOW | > 30 minutes |

## Mitigation Playbooks

`MitigationStrategy` ships with 9 built-in threat playbooks:

| Threat | Typical Actions |
|--------|----------------|
| `HYDRATE_RISK` | Increase MEG injection, raise upstream temperature, reduce pressure |
| `COMPRESSOR_SURGE` | Increase anti-surge valve opening, reduce speed, open recycle |
| `SEPARATOR_HIGH_LEVEL` | Increase liquid outlet valve, reduce inlet flow, check LC |
| `SEPARATOR_LOW_LEVEL` | Reduce liquid outlet flow, increase inlet, check for gas blow-by |
| `COOLER_FOULING` | Reduce feed temperature setpoint, schedule cleaning |
| `COMPOSITION_DRIFT` | Adjust blending ratio, review setpoints |
| `HIGH_PRESSURE` | Reduce inlet flow, open downstream valve |
| `HIGH_TEMPERATURE` | Increase cooling, reduce throughput |
| `VALVE_SATURATION` | Check controller, switch to manual temporarily |

You can register custom playbooks:

```java
MitigationStrategy strategy = new MitigationStrategy();
strategy.registerStrategy("WAX_RISK", Arrays.asList(
    new MitigationAction("Increase pipeline insulation heating",
        "Pipeline Heater", "setpoint", 45.0, "C",
        MitigationAction.Priority.SOON,
        "Maintains fluid above wax appearance temperature")
));
```

## Configuration

```java
OperatingEnvelopeAgent agent = new OperatingEnvelopeAgent(process);

// Whether to re-run process.run() before each evaluation
agent.setAutoRunProcess(true);         // default: true

// Trend confidence thresholds
agent.setRSquaredThreshold(0.5);       // min R² for trend-based trip prediction
agent.setTrendConfidenceThreshold(0.3);// min confidence for trip generation

// MarginTracker window size (number of samples to keep)
agent.setTrackerWindowSize(60);        // default: 60

// Enable/disable thermodynamic checks (slower but catches phase boundary issues)
ProcessOperatingEnvelope envelope = agent.getEnvelope();
envelope.setHydrateCheckEnabled(true);  // default: true
envelope.setDewPointCheckEnabled(true); // default: true
```

## Design Principles

1. **Advisory only** — The agent never modifies process setpoints, valve positions, or controller modes. All outputs are recommendations for human operators or higher-level decision systems.

2. **Equipment-agnostic** — Works with any `ProcessSystem` containing standard NeqSim equipment (separators, compressors, valves, heaters, streams). No configuration needed for auto-detection.

3. **Extensible** — Add custom margins via `addCustomMargin()`, custom playbooks via `registerStrategy()`, and custom data providers via the `DataProvider` interface.

4. **Serializable** — All classes implement `Serializable` for compatibility with `ProcessSystem.copy()` and snapshot/restore workflows.

5. **General-purpose** — Not tied to any specific operator, platform, or regulatory framework. Applicable to offshore platforms, onshore gas plants, refineries, CCS facilities, or any process modeled in NeqSim.

## Related Documentation

- [Process Simulation](../process/index.md) — Building and running ProcessSystem models
- [Dynamic Simulation](../process/dynamic-simulation.md) — Transient simulation with controllers
- [Flow Assurance](../process/flow-assurance.md) — Hydrate, wax, and corrosion analysis
- [Automation API](../process/automation.md) — String-addressable variable access
