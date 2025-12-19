# Digital Twin Integration Guide

This guide demonstrates how to integrate NeqSim's process simulation capabilities with digital twin architectures for real-time operations.

## Overview

A digital twin combines:
1. **Physics-Based Model**: NeqSim's rigorous thermodynamic simulation
2. **Real-Time Data**: Live sensor readings from the physical asset
3. **Predictive Analytics**: Look-ahead simulations for advisory systems
4. **ML Acceleration**: Surrogate models for faster execution

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      Physical Asset                             │
│                   (Platform, Plant, etc.)                       │
└────────────────────────┬────────────────────────────────────────┘
                         │ Sensors (OPC UA)
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Data Layer                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ Historian    │  │ Real-Time    │  │ Event        │          │
│  │ (PI, OSI)    │  │ Database     │  │ Streaming    │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                   NeqSim Digital Twin                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ ProcessSystem│  │ Surrogate    │  │ Physics      │          │
│  │ (Physics)    │  │ Registry     │  │ Validator    │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ Prediction   │  │ Emissions    │  │ Model        │          │
│  │ Engine       │  │ Tracker      │  │ Metadata     │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Applications                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ Advisory     │  │ MPC/APC      │  │ Operations   │          │
│  │ System       │  │ Controller   │  │ Dashboard    │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘
```

## Implementation Patterns

### 1. State Synchronization

Keep the digital twin synchronized with the physical asset:

```java
public class DigitalTwinService {
    private final ProcessSystem model;
    private final ProcessSystemState currentState;
    private final ModelMetadata metadata;
    
    public DigitalTwinService(ProcessSystem model) {
        this.model = model;
        this.currentState = ProcessSystemState.fromProcessSystem(model);
        this.metadata = new ModelMetadata();
        this.metadata.setLifecyclePhase(LifecyclePhase.OPERATION);
    }
    
    /**
     * Update model from live sensor data.
     */
    public void synchronize(Map<String, Double> sensorData) {
        // Update inlet conditions from sensors
        Stream inlet = (Stream) model.getUnit("inlet");
        if (sensorData.containsKey("inlet.temperature")) {
            inlet.setTemperature(sensorData.get("inlet.temperature"), "C");
        }
        if (sensorData.containsKey("inlet.pressure")) {
            inlet.setPressure(sensorData.get("inlet.pressure"), "bara");
        }
        if (sensorData.containsKey("inlet.flowrate")) {
            inlet.setFlowRate(sensorData.get("inlet.flowrate"), "kg/hr");
        }
        
        // Re-run model with updated inputs
        model.run();
        
        // Capture new state
        currentState.updateFrom(model);
    }
    
    /**
     * Save checkpoint for audit trail.
     */
    public void checkpoint(String version, String description) {
        ProcessSystemState state = ProcessSystemState.fromProcessSystem(model);
        state.setVersion(version);
        state.setDescription(description);
        state.saveToFile("checkpoints/model_" + version + ".json");
        
        metadata.recordModification(description);
    }
}
```

### 2. Look-Ahead Prediction

Generate predictions for operator advisory:

```java
public class AdvisoryService {
    private final ProcessSystem model;
    private final PhysicsConstraintValidator validator;
    
    public AdvisoryService(ProcessSystem model) {
        this.model = model;
        this.validator = new PhysicsConstraintValidator(model);
    }
    
    /**
     * Run look-ahead simulation and generate advisory.
     */
    public PredictionResult predict(Duration horizon) {
        // Clone model for prediction (don't affect main state)
        ProcessSystem predictModel = model.copy();
        
        PredictionResult result = new PredictionResult(horizon, "Look-ahead");
        
        try {
            // Run prediction (could include trend extrapolation)
            predictModel.run();
            
            // Collect predicted values
            for (ProcessEquipmentInterface unit : predictModel.getUnitOperations()) {
                if (unit instanceof Separator) {
                    Separator sep = (Separator) unit;
                    result.addPredictedValue(
                        sep.getName() + ".pressure",
                        new PredictedValue(sep.getPressure(), 0.5, "bara")
                    );
                    result.addPredictedValue(
                        sep.getName() + ".temperature",
                        new PredictedValue(sep.getTemperature(), 1.0, "C")
                    );
                }
            }
            
            // Check for constraint violations
            ValidationResult validation = validator.validateCurrentState();
            for (ConstraintViolation violation : validation.getViolations()) {
                result.addViolation(violation);
            }
            
        } catch (Exception e) {
            result.setStatus(PredictionStatus.FAILED);
            result.setStatusMessage("Prediction failed: " + e.getMessage());
        }
        
        return result;
    }
}
```

### 3. Hybrid Physics-ML Execution

Use surrogates for speed, physics for accuracy:

```java
public class HybridExecutionService {
    private final ProcessSystem physicsModel;
    private final SurrogateModelRegistry surrogateRegistry;
    
    public HybridExecutionService(ProcessSystem physicsModel) {
        this.physicsModel = physicsModel;
        this.surrogateRegistry = SurrogateModelRegistry.getInstance();
    }
    
    /**
     * Execute with automatic physics/ML selection.
     */
    public void executeWithFallback(String unitName, double[] inputs) {
        String surrogateKey = unitName + "-surrogate";
        
        // Try surrogate first, fall back to physics
        double[] result = surrogateRegistry.predictWithFallback(
            surrogateKey,
            inputs,
            this::runPhysicsCalculation
        );
        
        // Apply result to model
        applyResult(unitName, result);
    }
    
    private double[] runPhysicsCalculation(double[] inputs) {
        // Run full physics simulation
        physicsModel.run();
        return extractOutputs(physicsModel);
    }
}
```

### 4. Emissions Monitoring

Track emissions in real-time:

```java
public class EmissionsMonitoringService {
    private final ProcessSystem model;
    private final EmissionsTracker tracker;
    private final List<EmissionsSnapshot> history;
    
    public EmissionsMonitoringService(ProcessSystem model, double gridFactor) {
        this.model = model;
        this.tracker = new EmissionsTracker(model);
        this.tracker.setGridEmissionFactor(gridFactor);
        this.history = new ArrayList<>();
    }
    
    /**
     * Record current emissions snapshot.
     */
    public void recordSnapshot() {
        EmissionsReport report = tracker.calculateEmissions();
        
        EmissionsSnapshot snapshot = new EmissionsSnapshot(
            Instant.now(),
            report.getTotalCO2e("kg/hr"),
            report.getTotalPower("kW")
        );
        
        history.add(snapshot);
    }
    
    /**
     * Get cumulative emissions for period.
     */
    public double getCumulativeCO2e(Instant start, Instant end) {
        return history.stream()
            .filter(s -> !s.timestamp.isBefore(start) && !s.timestamp.isAfter(end))
            .mapToDouble(s -> s.co2eKgPerHr / 3600.0)  // Convert to kg/s for integration
            .sum();
    }
}
```

## Deployment Patterns

### Containerized Deployment

```dockerfile
FROM eclipse-temurin:21-jre

# Copy NeqSim and application
COPY target/neqsim-*.jar /app/neqsim.jar
COPY target/digital-twin.jar /app/app.jar
COPY models/ /app/models/

# Configure
ENV JAVA_OPTS="-Xmx4g"
ENV MODEL_PATH="/app/models/current.json"

WORKDIR /app
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Kubernetes Scaling

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: neqsim-digital-twin
spec:
  replicas: 3
  selector:
    matchLabels:
      app: digital-twin
  template:
    metadata:
      labels:
        app: digital-twin
    spec:
      containers:
      - name: neqsim
        image: neqsim-digital-twin:latest
        resources:
          requests:
            memory: "4Gi"
            cpu: "2"
          limits:
            memory: "8Gi"
            cpu: "4"
        env:
        - name: GRID_EMISSION_FACTOR
          value: "0.05"  # Norway
```

## Data Integration

### OPC UA Integration (Conceptual)

```java
// Pseudo-code for OPC UA integration
public class OpcUaConnector {
    private final DigitalTwinService twinService;
    
    public void startSubscription() {
        opcClient.createSubscription(1000, (nodeId, value) -> {
            Map<String, Double> sensorData = new HashMap<>();
            sensorData.put(nodeId.getIdentifier(), value);
            
            // Update digital twin
            twinService.synchronize(sensorData);
        });
    }
}
```

### Kafka Event Streaming (Conceptual)

```java
// Pseudo-code for Kafka integration
public class KafkaStreamProcessor {
    public void processStream() {
        kafkaConsumer.subscribe("sensor-data");
        
        while (running) {
            ConsumerRecords<String, SensorReading> records = 
                kafkaConsumer.poll(Duration.ofMillis(100));
            
            for (ConsumerRecord<String, SensorReading> record : records) {
                twinService.synchronize(record.value().toMap());
            }
        }
    }
}
```

## Best Practices

1. **State Versioning**: Checkpoint model state regularly for audit and rollback
2. **Validation**: Always validate AI recommendations against physics constraints
3. **Fallback Strategy**: Ensure physics calculation is always available as fallback
4. **Monitoring**: Track prediction accuracy and surrogate model health
5. **Emissions**: Use location-specific grid emission factors

## Related Documentation

- [Lifecycle Management](lifecycle/README.md) - State management and versioning
- [ML Integration](ml/README.md) - Surrogate models and physics validation
- [Advisory Systems](advisory/README.md) - Prediction results
- [Sustainability](sustainability/README.md) - Emissions tracking
