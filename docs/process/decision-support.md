---
title: "Engineering Decision Support for Control Room Operators"
description: "Framework for answering operator engineering questions using validated NeqSim process simulations. Covers rate changes, gas quality impact, derate options, equipment status, product spec checks, and what-if scenarios with full audit trail."
---

# Engineering Decision Support

The `neqsim.process.decisionsupport` package provides a framework for answering
operator engineering questions such as:

- *"Can we run at 6000 kg/hr with today's gas quality?"*
- *"What is the safest derate option right now?"*
- *"Are we meeting sales gas spec?"*
- *"What happens if we change compressor discharge pressure to 150 bara?"*

Every recommendation is auditable, traceable, and produced by running a validated
NeqSim steady-state process model — not heuristics or lookup tables.

## Architecture

```
┌──────────────┐    ┌─────────────────────┐    ┌────────────────────────┐
│ OperatorQuery │───▶│ DecisionSupportEngine│───▶│EngineeringRecommendation│
└──────────────┘    │  ├─ clone model      │    │  ├─ verdict            │
                    │  ├─ dispatch workflow │    │  ├─ findings           │
                    │  └─ log audit record  │    │  ├─ constraint checks  │
                    └─────────┬────────────┘    │  ├─ derate options     │
                              │                 │  └─ confidence         │
                    ┌─────────▼────────────┐    └────────────────────────┘
                    │    QueryWorkflow      │
                    │  (pluggable per type) │
                    └──────────────────────┘
```

| Component | Purpose |
|-----------|---------|
| `OperatorQuery` | Structured operator question (type, parameters, urgency) |
| `EngineeringRecommendation` | Auditable answer (verdict, findings, constraints, derate options) |
| `DecisionSupportEngine` | Dispatcher — clones the model, selects workflow, logs audit |
| `OperatingSpecification` | Plant-specific limits loaded from JSON (product specs + equipment limits) |
| `QueryWorkflow` | Pluggable interface — one implementation per question type |
| `AuditLogger` | Audit trail persistence (in-memory or file-based) |

## Quick Start (Java)

```java
// 1. Build and run your base process model
ProcessSystem process = new ProcessSystem();
// ... add equipment, run() ...

// 2. Define operating specifications (plant-specific, from JSON)
OperatingSpecification spec = new OperatingSpecification("Gas Export Plant");
spec.addProductSpec("waterDewPoint_C", Double.NaN, -18.0, "C", "ISO 6327");
spec.addProductSpec("wobbeIndex_MJ", 46.1, 52.2, "MJ/Sm3", "EN 16726");
spec.addEquipmentLimit("K-100", "surgeMargin", 0.10, Double.NaN, "fraction", "API 617");

// 3. Create and configure the engine
DecisionSupportEngine engine = new DecisionSupportEngine(process);
engine.setOperatingSpecification(spec);
engine.setModelVersion("platform-model-v2.1");

// 4. Register workflows
engine.registerWorkflow(QueryType.RATE_CHANGE_FEASIBILITY,
    new RateChangeFeasibilityWorkflow());
engine.registerWorkflow(QueryType.DERATE_OPTIONS,
    new DerateOptionsWorkflow());
engine.registerWorkflow(QueryType.EQUIPMENT_STATUS,
    new EquipmentStatusWorkflow());
engine.registerWorkflow(QueryType.GAS_QUALITY_IMPACT,
    new GasQualityImpactWorkflow());
engine.registerWorkflow(QueryType.PRODUCT_SPEC_CHECK,
    new ProductSpecCheckWorkflow());
engine.registerWorkflow(QueryType.WHAT_IF,
    new WhatIfWorkflow());

// 5. Ask a question
OperatorQuery query = OperatorQuery.builder()
    .queryType(QueryType.RATE_CHANGE_FEASIBILITY)
    .description("Can we increase to 6000 kg/hr?")
    .parameter("targetFlowRate", 6000.0)
    .parameter("flowRateUnit", "kg/hr")
    .parameter("feedStreamName", "feed")
    .requestedBy("operator-shift-A")
    .urgency(Urgency.PRIORITY)
    .build();

// 6. Get the recommendation
EngineeringRecommendation rec = engine.evaluate(query);

System.out.println(rec.getVerdict());        // FEASIBLE, FEASIBLE_WITH_WARNINGS, NOT_FEASIBLE, ...
System.out.println(rec.toHumanReadable());   // Formatted for control room display
System.out.println(rec.toJson());            // Full JSON for logging/integration
```

## Quick Start (Python / Jupyter)

```python
from neqsim import jneqsim

# Import decision support classes
OperatorQuery = jneqsim.process.decisionsupport.OperatorQuery
DecisionSupportEngine = jneqsim.process.decisionsupport.DecisionSupportEngine
OperatingSpecification = jneqsim.process.decisionsupport.OperatingSpecification
RateChangeFeasibilityWorkflow = jneqsim.process.decisionsupport.workflow.RateChangeFeasibilityWorkflow
QueryType = OperatorQuery.QueryType

# Set up engine (assumes 'process' is a running ProcessSystem)
engine = DecisionSupportEngine(process)
spec = OperatingSpecification("My Plant")
spec.addProductSpec("waterDewPoint_C", float('nan'), -18.0, "C", "ISO 6327")
engine.setOperatingSpecification(spec)
engine.registerWorkflow(QueryType.RATE_CHANGE_FEASIBILITY, RateChangeFeasibilityWorkflow())

# Ask a question
query = (OperatorQuery.builder()
    .queryType(QueryType.RATE_CHANGE_FEASIBILITY)
    .parameter("targetFlowRate", 6000.0)
    .parameter("flowRateUnit", "kg/hr")
    .parameter("feedStreamName", "feed")
    .build())

rec = engine.evaluate(query)
print(rec.toHumanReadable())
```

## Query Types and Workflows

### RATE_CHANGE_FEASIBILITY

*"Can we run at X?"*

Sets the target flow rate on the feed stream, runs the simulation, and checks
for equipment bottlenecks and capacity overloads.

| Parameter | Type | Description |
|-----------|------|-------------|
| `targetFlowRate` | double | Target flow rate value |
| `flowRateUnit` | String | Unit (e.g., "kg/hr", "MSm3/day") |
| `feedStreamName` | String | Name of the feed stream in the model |

### GAS_QUALITY_IMPACT

*"What happens with this new gas composition?"*

Updates the feed composition, runs the simulation, checks product specs and
capacity utilization.

| Parameter | Type | Description |
|-----------|------|-------------|
| `feedStreamName` | String | Name of the feed stream |
| `composition` | Map | Component name to mole fraction |

### DERATE_OPTIONS

*"What is the safest derate option?"*

Sweeps a range of flow rates from current down to minimum, evaluates constraints
at each step, and recommends the highest safe rate.

| Parameter | Type | Description |
|-----------|------|-------------|
| `currentFlowRate` | double | Current operating flow rate |
| `minFlowRate` | double | Minimum viable flow rate |
| `flowRateUnit` | String | Unit |
| `feedStreamName` | String | Name of the feed stream |
| `steps` | int | Number of intermediate steps to evaluate (default 5) |

### EQUIPMENT_STATUS

*"What is the current equipment status?"*

Runs the model and reports capacity utilization and bottleneck status for all
(or a specified) equipment.

| Parameter | Type | Description |
|-----------|------|-------------|
| `equipmentName` | String | (Optional) specific equipment to check |

### PRODUCT_SPEC_CHECK

*"Are we meeting sales gas spec?"*

Checks measured or calculated values against the `OperatingSpecification`.

| Parameter | Type | Description |
|-----------|------|-------------|
| `productValues` | Map | (Optional) spec name to measured value |

### WHAT_IF

*"What if we change parameter X to Y?"*

Applies arbitrary parameter changes via the `ProcessAutomation` API, reruns the
model, and compares before/after equipment utilization.

| Parameter | Type | Description |
|-----------|------|-------------|
| `changes` | Map | Variable address to new value |
| `outputVariables` | Map | (Optional) address to unit — values to report back |

## Operating Specifications

Operating specifications define the plant-specific limits that queries are checked
against. They are company-independent and can be loaded from JSON:

```json
{
  "name": "Troll A Gas Export",
  "productSpecs": {
    "waterDewPoint_C": { "specName": "waterDewPoint_C", "maxValue": -18.0, "unit": "C", "standardRef": "ISO 6327" },
    "wobbeIndex_MJ":   { "specName": "wobbeIndex_MJ", "minValue": 46.1, "maxValue": 52.2, "unit": "MJ/Sm3", "standardRef": "EN 16726" }
  },
  "equipmentLimits": [
    { "equipmentName": "K-100", "parameterName": "surgeMargin", "minValue": 0.10, "unit": "fraction", "standardRef": "API 617" }
  ]
}
```

Load with:

```java
OperatingSpecification spec = OperatingSpecification.fromJson(jsonString);
```

## Verdicts

Every `EngineeringRecommendation` has a `Verdict`:

| Verdict | Meaning |
|---------|---------|
| `FEASIBLE` | All constraints satisfied with margin |
| `FEASIBLE_WITH_WARNINGS` | Feasible, but some parameters near limits |
| `NOT_FEASIBLE` | One or more constraints violated |
| `REQUIRES_FURTHER_ANALYSIS` | Insufficient data or simulation failure |

## Audit Trail

Every evaluation is automatically logged with:

- Full query JSON
- Full recommendation JSON
- Model state hash (for reproducibility)
- Simulation duration
- NeqSim version
- Workflow ID

Two `AuditLogger` implementations are provided:

- `InMemoryAuditLogger` — for testing and short-lived sessions
- `FileAuditLogger` — appends JSON lines to a file for persistent audit trails

## Custom Workflows

Implement `QueryWorkflow` to add your own question types:

```java
public class MyCustomWorkflow implements QueryWorkflow {

    @Override
    public boolean canHandle(OperatorQuery query) {
        return query.getQueryType() == QueryType.CUSTOM;
    }

    @Override
    public EngineeringRecommendation execute(
            ProcessSystem process, OperatorQuery query, OperatingSpecification spec) {
        // Your simulation logic here
        process.run();
        return EngineeringRecommendation.builder()
            .verdict(Verdict.FEASIBLE)
            .summary("Custom analysis complete")
            .build();
    }

    @Override
    public String getWorkflowId() { return "my-custom"; }

    @Override
    public String getDescription() { return "My custom analysis"; }
}
```

Register it:

```java
engine.registerWorkflow(QueryType.CUSTOM, new MyCustomWorkflow());
```

## Design Principles

1. **Company-independent** — No hardcoded operator-specific logic. Plant limits are
   loaded from JSON. Workflows operate on generic `ProcessSystem` models.

2. **Stateless simulation** — The base model is cloned for every query. No mutation
   of the shared model, safe for concurrent use.

3. **Pluggable** — Workflows and audit loggers are interfaces. Add new question types
   or persistence backends without modifying the engine.

4. **Auditable** — Every recommendation links to the query, model state, and timing.
   Audit records are immutable and can be persisted to files or databases.

5. **Simulation-backed** — All recommendations come from running the actual NeqSim
   thermodynamic and process model, not from heuristics or lookup tables.

## Package Structure

```
neqsim.process.decisionsupport
├── OperatorQuery.java              # Structured operator question
├── EngineeringRecommendation.java  # Auditable recommendation
├── DecisionSupportEngine.java      # Central dispatcher
├── OperatingSpecification.java     # Plant-specific limits
├── QueryWorkflow.java              # Workflow interface
├── AuditLogger.java                # Audit persistence interface
├── AuditRecord.java                # Immutable audit record
├── InMemoryAuditLogger.java        # In-memory audit (testing)
├── FileAuditLogger.java            # File-based audit (production)
├── GsonFactory.java                # Shared JSON serialization
└── workflow/
    ├── RateChangeFeasibilityWorkflow.java
    ├── GasQualityImpactWorkflow.java
    ├── DerateOptionsWorkflow.java
    ├── EquipmentStatusWorkflow.java
    ├── ProductSpecCheckWorkflow.java
    └── WhatIfWorkflow.java
```
