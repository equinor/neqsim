# NeqSim Future Infrastructure Overview

This document provides an overview of the foundational infrastructure added to NeqSim to support the future of process simulation.

## Vision

The future of process simulation involves:

1. **Living Digital Twins** - Models that evolve with assets from concept to decommissioning
2. **Real-Time Advisory** - Predictive simulations supporting operations
3. **AI + Physics Integration** - ML efficiency with thermodynamic rigor
4. **Safety Analysis** - Automated what-if scenario generation
5. **Rapid Screening** - Cloud-scale concept evaluation
6. **Sustainability** - Emissions tracking and reporting
7. **Composable Architecture** - Modular, extensible design
8. **Engineer Empowerment** - High-level APIs for non-programmers

## Module Overview

```
neqsim/process/
├── processmodel/
│   └── lifecycle/           # Living Digital Twins
│       ├── ProcessSystemState.java
│       └── ModelMetadata.java
├── advisory/                # Real-Time Advisory
│   └── PredictionResult.java
├── ml/
│   └── surrogate/          # AI + Physics Integration
│       ├── SurrogateModelRegistry.java
│       └── PhysicsConstraintValidator.java
├── safety/
│   └── scenario/           # Safety Analysis
│       └── AutomaticScenarioGenerator.java
├── sustainability/         # Emissions Tracking
│   └── EmissionsTracker.java
└── util/
    └── optimization/       # Rapid Screening
        └── BatchStudy.java
```

## Quick Start

### Lifecycle Management

```java
// Export model state for version control
ProcessSystemState state = process.exportState();
state.setVersion("1.0.0");
state.saveToFile("model_v1.0.0.json");

// Track model lifecycle
ModelMetadata metadata = state.getMetadata();
metadata.setLifecyclePhase(LifecyclePhase.OPERATION);
metadata.recordValidation("Matched field data", "TEST-001");
```

### Emissions Tracking

```java
// Calculate emissions (includes Expander power generation)
EmissionsReport report = process.getEmissions();
System.out.println(report.getSummary());

// With location-specific grid factor
EmissionsReport norwayReport = process.getEmissions(0.05);

// Export to JSON for external tools
report.exportToJSON("emissions.json");
String json = report.toJson();
```

### Safety Scenarios

```java
// Generate what-if scenarios
AutomaticScenarioGenerator generator = new AutomaticScenarioGenerator(process);
generator.addFailureModes(FailureMode.COOLING_LOSS, FailureMode.COMPRESSOR_TRIP);

// Run all scenarios automatically
List<ScenarioRunResult> results = generator.runAllSingleFailures();

// Get execution summary
String summary = generator.summarizeResults(results);
System.out.println(summary);

// Or run manually
List<ProcessSafetyScenario> scenarios = generator.generateSingleFailures();
for (ProcessSafetyScenario scenario : scenarios) {
    ProcessSystem copy = process.copy();
    scenario.applyTo(copy);
    copy.run();
    // Analyze...
}
```

### Batch Studies

```java
// Run parameter study with extended parameter paths
BatchStudy study = BatchStudy.builder(process)
    .vary("compressor.outletPressure", 30.0, 80.0, 6)
    .vary("heater.outletTemperature", 50.0, 150.0, 5)
    .addObjective("power", Objective.MINIMIZE, p -> p.getTotalPower())
    .addObjective("emissions", Objective.MINIMIZE, p -> p.getTotalCO2Emissions())
    .parallelism(8)
    .build();

BatchStudyResult result = study.run();

// Export results
result.exportToCSV("results.csv");
result.exportToJSON("results.json");

// Pareto front analysis
List<CaseResult> paretoFront = result.getParetoFront("power", "emissions");
```

### ML Integration

```java
// Register surrogate model
SurrogateModelRegistry.getInstance()
    .register("flash-calc", myNeuralNetwork);

// Validate AI actions
PhysicsConstraintValidator validator = new PhysicsConstraintValidator(process);
ValidationResult check = validator.validate(aiProposedAction);
if (!check.isValid()) {
    System.out.println("Rejected: " + check.getRejectionReason());
}
```

### Advisory Systems

```java
// Create prediction result
PredictionResult prediction = new PredictionResult(Duration.ofHours(2));
prediction.addPredictedValue("pressure", new PredictedValue(50.0, 2.5, "bara"));

if (prediction.hasViolations()) {
    System.out.println(prediction.getAdvisoryRecommendation());
}
```

## Documentation

Detailed documentation for each module:

| Module | Documentation |
|--------|---------------|
| Lifecycle Management | [lifecycle/README.md](lifecycle/README.md) |
| Sustainability | [sustainability/README.md](sustainability/README.md) |
| Advisory Systems | [advisory/README.md](advisory/README.md) |
| ML Integration | [ml/README.md](ml/README.md) |
| Safety Scenarios | [safety/README.md](safety/README.md) |
| Batch Studies | [optimization/README.md](optimization/README.md) |

## Architecture Principles

### 1. Backward Compatibility
All new features are additive. Existing code continues to work unchanged.

### 2. Progressive Enhancement
Start with simple APIs, access advanced features when needed.

### 3. Physics First
ML models include automatic fallback to physics calculations.

### 4. Safety by Design
All AI actions are validated against physical constraints.

### 5. Sustainability Built-In
Emissions tracking is first-class, not an afterthought.

## Integration Points

### External ML Platforms
- TensorFlow Serving
- ONNX Runtime
- PyTorch via JNI

### Real-Time Systems
- OPC UA for live data
- Kafka for streaming
- REST APIs for web services

### Cloud Platforms
- Azure Batch for parallel studies
- Kubernetes for scaling
- S3/Blob for result storage

## Future Roadmap

### Phase 1 ✅
- Core infrastructure classes
- Basic integration with ProcessSystem
- Comprehensive test coverage

### Phase 2 (Current) ✅
- Equipment-specific property capture in ProcessSystemState
- Expander support in EmissionsTracker
- JSON export for EmissionsReport and BatchStudyResult
- Pareto front analysis for multi-objective optimization
- Extended parameter paths for BatchStudy (12+ equipment properties)
- Scenario execution framework with ScenarioRunResult

### Phase 3 (Planned)
- Enhanced ML model loading (ONNX, SavedModel)
- Real-time data connectors
- OPC UA integration

### Phase 4 (Vision)
- AutoML for surrogate training
- Digital twin synchronization
- Regulatory reporting automation
- Time-series emissions tracking

## Contributing

When extending these modules:

1. Follow existing patterns for consistency
2. Include comprehensive JavaDoc
3. Add unit tests for new functionality
4. Update documentation

## Related Resources

- [NeqSim Main Documentation](../README.md)
- [Developer Setup](../development/DEVELOPER_SETUP.md)
- [Contributing Guide](../development/contributing-structure.md)
