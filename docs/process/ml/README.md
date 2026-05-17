---
title: Machine Learning Integration
description: This module provides infrastructure for integrating machine learning models with physics-based simulation.
---

# Machine Learning Integration

This module provides infrastructure for integrating machine learning models with physics-based simulation.

## Overview

The future of process simulation combines physics rigor with ML efficiency:

- **Surrogate Models**: Fast ML approximations of expensive calculations
- **Physics Constraints**: Ensure ML predictions respect thermodynamic laws
- **Hybrid Execution**: Seamless switching between physics and ML
- **Safety Guardrails**: Prevent physically impossible recommendations

## Classes

### SurrogateModelRegistry

Central registry for managing trained ML surrogate models.

#### Key Features

- **Model Caching**: Keep frequently-used models in memory
- **Automatic Fallback**: Fall back to physics when ML fails
- **Validity Tracking**: Monitor extrapolation and failure rates
- **Persistence**: Save/load models to disk

#### Usage Example

```java
// Get singleton registry
SurrogateModelRegistry registry = SurrogateModelRegistry.getInstance();

// Register a surrogate model
registry.register("flash-separator-1", new SurrogateModel() {
    @Override
    public double[] predict(double[] input) {
        // Neural network inference
        return neuralNet.forward(input);
    }
    
    @Override
    public int getInputDimension() { return 5; }
    
    @Override
    public int getOutputDimension() { return 3; }
});

// Use with automatic physics fallback
double[] result = registry.predictWithFallback(
    "flash-separator-1",
    input,
    physicsModel::calculate  // Fallback function
);
```

#### Metadata and Monitoring

```java
// Register with metadata
SurrogateMetadata metadata = new SurrogateMetadata();
metadata.setModelType("neural-network");
metadata.setTrainingDataSource("simulation-data-2024");
metadata.setInputBounds(
    new double[]{0.0, 0.0, 0.0},    // min values
    new double[]{100.0, 500.0, 1.0} // max values
);

registry.register("flash-model", model, metadata);

// Check model statistics
Optional<SurrogateMetadata> stats = registry.getMetadata("flash-model");
if (stats.isPresent()) {
    double failureRate = stats.get().getFailureRate();
    double extrapolationRate = stats.get().getExtrapolationRate();
}
```

#### Model Persistence

```java
// Save model to disk
registry.saveModel("flash-model", "models/flash_model.ser");

// Load model from disk
registry.loadModel("flash-model", "models/flash_model.ser");
```

### PhysicsConstraintValidator

Validates AI-proposed actions against thermodynamic and safety constraints.

#### Key Features

- **Default Physical Bounds**: Temperature > 0, pressure > 0, etc.
- **Equipment Limits**: Custom limits per equipment
- **Mass/Energy Balance**: Check conservation laws
- **Rejection Explanation**: Clear reasons for rejected actions

#### Usage Example

```java
ProcessSystem process = new ProcessSystem();
// ... configure process ...

PhysicsConstraintValidator validator = new PhysicsConstraintValidator(process);

// Add equipment-specific limits
validator.addPressureLimit("separator", 10.0, 80.0, "bara");
validator.addTemperatureLimit("heater-outlet", 0.0, 300.0, "C");
validator.addFlowLimit("feed", 0.0, 1000.0, "kg/hr");

// Set tolerances
validator.setMassBalanceTolerance(0.01);  // 1%
validator.setEnergyBalanceTolerance(0.05); // 5%

// Validate an AI-proposed action
Map<String, Double> proposedAction = new HashMap<>();
proposedAction.put("heater.duty", 5000000.0);
proposedAction.put("valve.opening", 0.85);

ValidationResult result = validator.validate(proposedAction);

if (result.isValid()) {
    // Safe to apply action
    applyAction(proposedAction);
} else {
    // Action rejected - explain why
    System.out.println("Rejected: " + result.getRejectionReason());
    for (ConstraintViolation v : result.getViolations()) {
        System.out.println("  - " + v.getMessage());
    }
}
```

#### Default Constraints

The validator includes sensible default constraints:

| Variable Pattern | Min | Max | Reason |
|------------------|-----|-----|--------|
| `temperature` | 0 K | ∞ | Absolute zero limit |
| `pressure` | 0 Pa | ∞ | Physical minimum |
| `flow` | 0 | ∞ | Non-negative flows |
| `valve.opening` | 0 | 1 | Percentage bounds |

#### Validation Modes

```java
// Enable/disable specific checks
validator.setEnforceMassBalance(true);
validator.setEnforceEnergyBalance(true);
validator.setEnforcePhysicalBounds(true);

// Validate current state (not a proposed action)
ValidationResult currentState = validator.validateCurrentState();
```

## Integration Patterns

### Hybrid Physics-ML Execution

```java
// Decision logic for using surrogate vs physics
public double[] calculate(double[] input) {
    SurrogateModelRegistry registry = SurrogateModelRegistry.getInstance();
    
    // Check if surrogate is suitable
    Optional<SurrogateMetadata> meta = registry.getMetadata("my-model");
    if (meta.isPresent() && meta.get().isInputValid(input)) {
        // Use surrogate with physics fallback
        return registry.predictWithFallback(
            "my-model", 
            input, 
            this::physicsCalculation
        );
    } else {
        // Use physics directly
        return physicsCalculation(input);
    }
}
```

### Reinforcement Learning Safety

```java
// RL agent proposes action
Map<String, Double> rlAction = agent.proposeAction(state);

// Validate before execution
ValidationResult result = validator.validate(rlAction);
if (!result.isValid()) {
    // Penalize agent for invalid action
    agent.recordPenalty(rlAction, result.getViolations());
    
    // Use safe fallback action
    rlAction = getSafeDefaultAction();
}

// Execute validated action
executeAction(rlAction);
```

### Surrogate Model Training Pipeline

```java
// 1. Generate training data using physics
List<double[]> inputs = generateInputSamples();
List<double[]> outputs = new ArrayList<>();
for (double[] input : inputs) {
    outputs.add(physicsModel.calculate(input));
}

// 2. Train ML model (external tool)
// ... Python/TensorFlow/PyTorch training ...

// 3. Register trained model
SurrogateModel trained = loadTrainedModel("model.onnx");
SurrogateMetadata meta = new SurrogateMetadata();
meta.setInputBounds(getMinBounds(inputs), getMaxBounds(inputs));
meta.setTrainingDataSource("neqsim-generated-2024");

registry.register("trained-model", trained, meta);

// 4. Monitor in production
// Fallback rate, extrapolation rate tracked automatically
```

## Best Practices

1. **Fallback Strategy**: Always provide physics fallback for ML models
2. **Input Validation**: Check inputs are within training range
3. **Constraint Checking**: Validate all AI actions before execution
4. **Monitoring**: Track failure and extrapolation rates
5. **Versioning**: Version both ML models and physics models together

## Related Documentation

- [Advisory Systems](../advisory/) - Use predictions for operator guidance
- [Batch Studies](../optimization/) - Generate training data efficiently
- [AI Platform Integration](../../integration/ai_platform_integration) - External ML platform integration
