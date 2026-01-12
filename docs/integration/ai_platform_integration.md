# AI Platform Integration Guide

This document describes the NeqSim extensions designed for integration with AI-based production optimization platforms and real-time digital twin systems.

## Overview

Modern AI-based production optimization platforms typically require:
- **High-frequency data streaming** (millions of data points per hour)
- **Hybrid physics-ML models** combining first-principles with machine learning
- **Virtual Flow Meters (VFM)** and soft sensors
- **Continuous auto-calibration** for digital twin accuracy
- **Uncertainty quantification** for risk-aware optimization
- **Well production allocation** for reservoir management

NeqSim provides dedicated packages to support these requirements.

## Table of Contents

1. [Streaming Data](#streaming-data)
2. [Virtual Flow Meters](#virtual-flow-meters)
3. [Soft Sensors](#soft-sensors)
4. [Uncertainty Quantification](#uncertainty-quantification)
5. [ML Integration](#ml-integration)
6. [Online Calibration](#online-calibration)
7. [Well Allocation](#well-allocation)
8. [Event System](#event-system)
9. [Data Export](#data-export)

---

## Streaming Data

**Package:** `neqsim.process.streaming`

The streaming package enables real-time data publishing from NeqSim simulations to external platforms.

### Key Classes

#### TimestampedValue

Represents a value with timestamp, unit, and quality indicator.

```java
import neqsim.process.streaming.TimestampedValue;

// Create a timestamped value
TimestampedValue value = new TimestampedValue(
    100.5,                          // value
    "bara",                         // unit
    Instant.now(),                  // timestamp
    TimestampedValue.Quality.GOOD   // quality
);

// Access properties
double val = value.getValue();
String unit = value.getUnit();
Instant ts = value.getTimestamp();
TimestampedValue.Quality quality = value.getQuality();
```

**Quality Levels:**
- `GOOD` - Normal measurement
- `UNCERTAIN` - Measurement with degraded confidence
- `BAD` - Invalid or failed measurement
- `SIMULATED` - Value from simulation
- `ESTIMATED` - Interpolated or estimated value

#### ProcessDataPublisher

Publishes process data from a ProcessSystem with subscription support.

```java
import neqsim.process.streaming.ProcessDataPublisher;
import neqsim.process.processmodel.ProcessSystem;

// Create publisher linked to process system
ProcessSystem process = new ProcessSystem();
// ... add equipment ...
ProcessDataPublisher publisher = new ProcessDataPublisher(process);

// Subscribe to updates
publisher.subscribeToUpdates("Inlet.pressure", value -> {
    System.out.println("Pressure: " + value.getValue() + " " + value.getUnit());
});

// Publish current state
publisher.publishFromProcessSystem();

// Get state vector for ML models
double[] stateVector = publisher.getStateVector();
```

#### StreamingDataInterface

Interface for custom streaming implementations:

```java
public interface StreamingDataInterface {
    void subscribeToUpdates(String tagId, Consumer<TimestampedValue> callback);
    void publishBatch(Map<String, TimestampedValue> values);
    double[] getStateVector();
    List<TimestampedValue> getHistory(String tagId, Duration period);
}
```

---

## Virtual Flow Meters

**Package:** `neqsim.process.measurementdevice.vfm`

Virtual Flow Meters calculate multiphase flow rates using thermodynamic models when physical meters are unavailable or unreliable.

### VirtualFlowMeter

```java
import neqsim.process.measurementdevice.vfm.VirtualFlowMeter;
import neqsim.process.measurementdevice.vfm.VFMResult;

// Create VFM from a stream
StreamInterface wellStream = new Stream("Well-A", fluid);
wellStream.run();

VirtualFlowMeter vfm = new VirtualFlowMeter("VFM-Well-A", wellStream);

// Calculate flow rates with uncertainty
VFMResult result = vfm.calculate();

// Access results
double gasRate = result.getGasFlowRate();      // Sm3/day
double oilRate = result.getOilFlowRate();      // Sm3/day
double waterRate = result.getWaterFlowRate();  // Sm3/day
double gor = result.getGOR();                  // Sm3/Sm3
double waterCut = result.getWaterCut();        // fraction

// Get uncertainties
UncertaintyBounds gasUncertainty = result.getGasFlowRateUncertainty();
double lower95 = gasUncertainty.getLower95();
double upper95 = gasUncertainty.getUpper95();
```

### Calibration

VFMs can be calibrated using well test data:

```java
// Create calibration from well test
VFMCalibration calibration = new VFMCalibration();
calibration.setWellTestGasRate(50000);   // Sm3/day
calibration.setWellTestOilRate(500);     // Sm3/day
calibration.setWellTestWaterRate(100);   // Sm3/day
calibration.setWellTestDate(Instant.now());

vfm.setCalibration(calibration);
```

### VFMResult Builder

```java
VFMResult result = VFMResult.builder()
    .gasFlowRate(45000)
    .oilFlowRate(450)
    .waterFlowRate(95)
    .gasFlowRateUncertainty(new UncertaintyBounds(42000, 48000, 2000))
    .timestamp(Instant.now())
    .quality(VFMResult.Quality.GOOD)
    .build();
```

---

## Soft Sensors

**Package:** `neqsim.process.measurementdevice.vfm`

Soft sensors estimate unmeasured properties from available measurements using thermodynamic models.

### SoftSensor

```java
import neqsim.process.measurementdevice.vfm.SoftSensor;

// Create soft sensor for GOR estimation
SoftSensor gorSensor = new SoftSensor("GOR-Sensor", stream, SoftSensor.PropertyType.GOR);

// Get estimated value
double gor = gorSensor.getMeasuredValue();
String unit = gorSensor.getUnit();

// Get sensitivity to input changes
double sensitivity = gorSensor.getSensitivity("pressure");
```

**Available Property Types:**
- `GOR` - Gas-Oil Ratio
- `WATER_CUT` - Water Cut
- `DENSITY` - Fluid Density
- `VISCOSITY` - Dynamic Viscosity
- `MOLECULAR_WEIGHT` - Molecular Weight
- `Z_FACTOR` - Compressibility Factor
- `ENTHALPY` - Specific Enthalpy
- `ENTROPY` - Specific Entropy
- `HEAT_CAPACITY` - Heat Capacity

---

## Uncertainty Quantification

**Package:** `neqsim.process.util.uncertainty`

Propagates measurement uncertainties through thermodynamic calculations.

### UncertaintyAnalyzer

```java
import neqsim.process.util.uncertainty.UncertaintyAnalyzer;
import neqsim.process.util.uncertainty.UncertaintyResult;

// Create analyzer for a process system
ProcessSystem process = new ProcessSystem();
// ... configure process ...

UncertaintyAnalyzer analyzer = new UncertaintyAnalyzer(process);

// Define input uncertainties (relative)
analyzer.setInputUncertainty("inlet_pressure", 0.01);      // 1%
analyzer.setInputUncertainty("inlet_temperature", 0.005);  // 0.5%
analyzer.setInputUncertainty("inlet_flowrate", 0.02);      // 2%

// Perform analytical (linear) uncertainty propagation
UncertaintyResult result = analyzer.analyzeAnalytical();

// Get output uncertainties
Map<String, Double> outputUncertainties = result.getOutputUncertainties();

// Get sensitivity matrix
SensitivityMatrix sensMatrix = result.getSensitivityMatrix();
double dP_dT = sensMatrix.getSensitivity("outlet_pressure", "inlet_temperature");
```

### Monte Carlo Analysis

For nonlinear systems, use Monte Carlo:

```java
// Configure Monte Carlo
analyzer.setMonteCarloSamples(10000);

// Run Monte Carlo uncertainty propagation
UncertaintyResult mcResult = analyzer.analyzeMonteCarlo(1000);

// Get percentiles
double p05 = mcResult.getPercentile("outlet_pressure", 0.05);
double p95 = mcResult.getPercentile("outlet_pressure", 0.95);
```

### SensitivityMatrix

```java
import neqsim.process.util.uncertainty.SensitivityMatrix;

// Get Jacobian matrix
SensitivityMatrix matrix = new SensitivityMatrix(inputNames, outputNames);

// Calculate sensitivities via finite differences
for (String input : inputNames) {
    for (String output : outputNames) {
        double sensitivity = calculateNumericalDerivative(input, output);
        matrix.setSensitivity(output, input, sensitivity);
    }
}

// Propagate input variances to output variances
Map<String, Double> inputVariances = Map.of("pressure", 0.01, "temperature", 0.0025);
Map<String, Double> outputVariances = matrix.propagateUncertainty(inputVariances);
```

---

## ML Integration

**Package:** `neqsim.process.integration.ml`

Interfaces for combining physics models with machine learning corrections.

### HybridModelAdapter

Wraps a NeqSim model with ML corrections:

```java
import neqsim.process.integration.ml.HybridModelAdapter;
import neqsim.process.integration.ml.MLCorrectionInterface;

// Create hybrid adapter
ProcessSystem physicsModel = new ProcessSystem();
// ... configure model ...

HybridModelAdapter hybrid = new HybridModelAdapter(physicsModel);

// Add ML correction (implement MLCorrectionInterface)
MLCorrectionInterface mlCorrection = new MyNeuralNetworkCorrection();
hybrid.setCorrection(mlCorrection);

// Set combination strategy
hybrid.setCombinationStrategy(HybridModelAdapter.CombinationStrategy.ADDITIVE);

// Run hybrid model
hybrid.run();

// Get corrected outputs
double correctedPressure = hybrid.getCorrectedOutput("outlet_pressure");
```

**Combination Strategies:**
- `ADDITIVE` - Output = Physics + ML_Correction
- `MULTIPLICATIVE` - Output = Physics × ML_Factor
- `REPLACEMENT` - Output = ML (physics as feature)
- `WEIGHTED_AVERAGE` - Output = w × Physics + (1-w) × ML

### MLCorrectionInterface

Implement this interface to connect external ML models:

```java
public interface MLCorrectionInterface {
    // Get correction for a specific output
    double getCorrection(String outputName, Map<String, Double> inputs);
    
    // Update model with new training data
    void update(Map<String, Double> inputs, Map<String, Double> targets);
    
    // Get model confidence (0-1)
    double getConfidence();
}
```

### FeatureExtractor

Extract features from streams for ML models:

```java
import neqsim.process.integration.ml.FeatureExtractor;

// Create feature extractor
FeatureExtractor extractor = new FeatureExtractor();

// Extract features from a stream
StreamInterface stream = process.getStream("inlet");
double[] features = extractor.extractFeatures(stream);

// Get feature names
String[] featureNames = extractor.getFeatureNames();

// Normalize features
double[] normalized = extractor.normalize(features);
```

---

## Online Calibration

**Package:** `neqsim.process.calibration`

Continuously calibrates models using real-time data.

### OnlineCalibrator

```java
import neqsim.process.calibration.OnlineCalibrator;
import neqsim.process.calibration.CalibrationResult;
import neqsim.process.calibration.CalibrationQuality;

// Create calibrator for a process system
ProcessSystem process = new ProcessSystem();
OnlineCalibrator calibrator = new OnlineCalibrator(process);

// Configure tunable parameters
calibrator.setTunableParameters(Arrays.asList(
    "separator_efficiency",
    "heat_exchanger_UA",
    "compressor_polytropic_efficiency"
));

// Set deviation threshold for triggering recalibration
calibrator.setDeviationThreshold(0.1);  // 10%

// Record measurements and predictions
Map<String, Double> measurements = Map.of(
    "outlet_pressure", 45.2,
    "outlet_temperature", 35.5
);
Map<String, Double> predictions = Map.of(
    "outlet_pressure", 44.8,
    "outlet_temperature", 36.1
);

// Check if recalibration is needed
boolean needsRecalibration = calibrator.recordDataPoint(measurements, predictions);

// Perform incremental update (fast, for real-time)
CalibrationResult incrementalResult = calibrator.incrementalUpdate(measurements, predictions);

// Or perform full recalibration (thorough, periodic)
CalibrationResult fullResult = calibrator.fullRecalibration();

// Check calibration quality
CalibrationQuality quality = calibrator.getQualityMetrics();
System.out.println("Quality Score: " + quality.getOverallScore());
System.out.println("Rating: " + quality.getRating());
System.out.println("Needs Recalibration: " + quality.needsRecalibration());
```

### CalibrationResult

```java
CalibrationResult result = calibrator.fullRecalibration();

if (result.isSuccessful()) {
    Map<String, Double> params = result.getCalibratedParameters();
    double improvement = result.getImprovementPercent();
    System.out.println("Improved by " + improvement + "%");
}
```

### CalibrationQuality

```java
CalibrationQuality quality = calibrator.getQualityMetrics();

// Metrics
double rmse = quality.getRootMeanSquareError();
double r2 = quality.getR2Score();
int samples = quality.getSampleCount();
double coverage = quality.getCoveragePercent();

// Overall assessment
double score = quality.getOverallScore();  // 0-100
CalibrationQuality.Rating rating = quality.getRating();  // EXCELLENT, GOOD, FAIR, POOR

// Check calibration age
Duration age = quality.getCalibrationAge();
```

---

## Well Allocation

**Package:** `neqsim.process.equipment.well.allocation`

Allocates commingled production back to individual wells.

### WellProductionAllocator

```java
import neqsim.process.equipment.well.allocation.WellProductionAllocator;
import neqsim.process.equipment.well.allocation.AllocationResult;

// Create allocator
WellProductionAllocator allocator = new WellProductionAllocator("Field-A-Allocation");

// Add wells with test data
WellProductionAllocator.WellData wellA = allocator.addWell("Well-A");
wellA.setTestRates(500, 50000, 100);  // oil, gas, water (Sm3/day)
wellA.setVFMRates(480, 48000, 95);
wellA.setChokePosition(0.75);
wellA.setProductivityIndex(10.0);
wellA.setReservoirPressure(250);

WellProductionAllocator.WellData wellB = allocator.addWell("Well-B");
wellB.setTestRates(300, 30000, 200);
wellB.setVFMRates(290, 29000, 195);
wellB.setChokePosition(0.60);
wellB.setProductivityIndex(8.0);
wellB.setReservoirPressure(245);

// Set allocation method
allocator.setAllocationMethod(WellProductionAllocator.AllocationMethod.VFM_BASED);

// Allocate total production
AllocationResult result = allocator.allocate(
    780,   // total oil (Sm3/day)
    78000, // total gas (Sm3/day)
    290    // total water (Sm3/day)
);

// Get allocated rates per well
double wellAOil = result.getOilRate("Well-A");
double wellAGas = result.getGasRate("Well-A");
double wellAGOR = result.getGOR("Well-A");
double wellAWC = result.getWaterCut("Well-A");
double uncertainty = result.getUncertainty("Well-A");

// Check allocation balance
boolean balanced = result.isBalanced();
double error = result.getAllocationError();
```

**Allocation Methods:**
- `WELL_TEST` - Based on periodic well test data
- `VFM_BASED` - Based on virtual flow meter estimates
- `CHOKE_MODEL` - Based on choke performance curves
- `COMBINED` - Weighted combination of above methods

---

## Event System

**Package:** `neqsim.process.util.event`

Publish-subscribe system for process events.

### ProcessEventBus

```java
import neqsim.process.util.event.ProcessEventBus;
import neqsim.process.util.event.ProcessEvent;
import neqsim.process.util.event.ProcessEventListener;

// Get event bus instance
ProcessEventBus eventBus = ProcessEventBus.getInstance();

// Subscribe to all events
eventBus.subscribe(event -> {
    System.out.println("Event: " + event.getDescription());
});

// Subscribe to specific event types
eventBus.subscribe(ProcessEvent.EventType.ALARM, event -> {
    // Handle alarm
    sendAlarmNotification(event);
});

// Publish events
eventBus.publish(ProcessEvent.info("Compressor-1", "Startup complete"));
eventBus.publish(ProcessEvent.warning("Separator-1", "Level approaching high limit"));
eventBus.publish(ProcessEvent.alarm("Valve-V101", "Emergency shutdown activated"));

// Publish threshold crossing
eventBus.publish(ProcessEvent.thresholdCrossed(
    "Pressure-PT101", "pressure", 52.5, 50.0, true  // value, threshold, above
));

// Publish model deviation
eventBus.publish(ProcessEvent.modelDeviation(
    "VFM-Well-A", "gas_rate", 48500, 50000  // measured, predicted
));
```

### ProcessEvent Properties

```java
ProcessEvent event = ProcessEvent.alarm("Source", "Description");

// Set custom properties
event.setProperty("priority", 1);
event.setProperty("acknowledged", false);
event.setProperty("operator", "John");

// Get properties
int priority = event.getProperty("priority", Integer.class);

// Standard properties
String eventId = event.getEventId();
ProcessEvent.EventType type = event.getType();
String source = event.getSource();
Instant timestamp = event.getTimestamp();
ProcessEvent.Severity severity = event.getSeverity();
```

### Event History

```java
// Get recent events
List<ProcessEvent> recent = eventBus.getRecentEvents(100);

// Get events by type
List<ProcessEvent> alarms = eventBus.getEventsByType(ProcessEvent.EventType.ALARM, 50);

// Get events by severity
List<ProcessEvent> critical = eventBus.getEventsBySeverity(ProcessEvent.Severity.ERROR, 20);
```

---

## Data Export

**Package:** `neqsim.process.util.export`

Export simulation data for external analysis and ML training.

### TimeSeriesExporter

```java
import neqsim.process.util.export.TimeSeriesExporter;

// Create exporter
ProcessSystem process = new ProcessSystem();
TimeSeriesExporter exporter = new TimeSeriesExporter(process);

// Collect snapshots during simulation
for (int step = 0; step < 1000; step++) {
    process.run();
    exporter.collectSnapshot();
    Thread.sleep(1000);  // 1 second intervals
}

// Export to JSON (AI platform format)
String json = exporter.exportToJson();
Files.writeString(Path.of("timeseries.json"), json);

// Export to CSV for ML training
String csv = exporter.exportToCsv();
Files.writeString(Path.of("training_data.csv"), csv);

// Export as feature matrix for ML
double[][] features = exporter.exportAsMatrix();
```

### ProcessSnapshot

```java
import neqsim.process.util.export.ProcessSnapshot;

// Create snapshot
ProcessSnapshot snapshot = new ProcessSnapshot("snap-001");

// Add measurements
snapshot.setMeasurement("inlet_pressure", 50.0, "bara");
snapshot.setMeasurement("inlet_temperature", 25.0, "C");
snapshot.setMeasurement("outlet_flowrate", 1000.0, "kg/hr");

// Serialize
String json = snapshot.toJson();

// Restore
ProcessSnapshot restored = ProcessSnapshot.fromJson(json);
```

### ProcessDelta

Efficiently sync state changes:

```java
import neqsim.process.util.export.ProcessDelta;

// Create delta between snapshots
ProcessSnapshot before = exporter.createSnapshot("before");
process.run();
ProcessSnapshot after = exporter.createSnapshot("after");

ProcessDelta delta = ProcessDelta.between(before, after);

// Get changes
Map<String, Double> changes = delta.getChangedValues();
double pressureChange = delta.getChange("outlet_pressure");

// Apply delta to another snapshot
ProcessSnapshot updated = delta.applyTo(before);
```

---

## Example: Complete Integration

```java
// Create process system
SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.setMixingRule("classic");

Stream inlet = new Stream("Inlet", fluid);
inlet.setFlowRate(10000, "kg/hr");
inlet.run();

ProcessSystem process = new ProcessSystem();
process.add(inlet);

// Setup streaming
ProcessDataPublisher publisher = new ProcessDataPublisher(process);

// Setup VFM
VirtualFlowMeter vfm = new VirtualFlowMeter("VFM-1", inlet);

// Setup online calibration
OnlineCalibrator calibrator = new OnlineCalibrator(process);
calibrator.setTunableParameters(Arrays.asList("efficiency"));
calibrator.setDeviationThreshold(0.05);

// Setup event bus
ProcessEventBus eventBus = ProcessEventBus.getInstance();
eventBus.subscribe(ProcessEvent.EventType.MODEL_DEVIATION, event -> {
    // Trigger recalibration on significant deviation
    if (calibrator.getQualityMetrics().needsRecalibration()) {
        calibrator.fullRecalibration();
    }
});

// Setup data export
TimeSeriesExporter exporter = new TimeSeriesExporter(process);

// Real-time loop
while (running) {
    // Run simulation step
    process.run();
    
    // Publish streaming data
    publisher.publishFromProcessSystem();
    
    // Get VFM estimate
    VFMResult vfmResult = vfm.calculate();
    
    // Check for deviations
    if (Math.abs(vfmResult.getGasFlowRate() - measuredGasRate) / measuredGasRate > 0.1) {
        eventBus.publish(ProcessEvent.modelDeviation(
            "VFM-1", "gas_rate", measuredGasRate, vfmResult.getGasFlowRate()
        ));
    }
    
    // Record for calibration
    calibrator.recordDataPoint(measurements, predictions);
    
    // Collect for export
    exporter.collectSnapshot();
    
    Thread.sleep(1000);
}

// Export training data
String trainingData = exporter.exportToCsv();
```

---

## Performance Considerations

1. **Streaming Frequency**: ProcessDataPublisher can handle 1000+ updates/second
2. **History Buffer**: Default 1000 points; adjust via `setMaxHistorySize()`
3. **Monte Carlo Samples**: Use 1000-10000 for uncertainty analysis
4. **Calibration**: Incremental updates are O(1); full recalibration is O(n)
5. **Event Bus**: Async delivery recommended for high-frequency events

## Thread Safety

- `ProcessDataPublisher` uses `ConcurrentHashMap` and `CopyOnWriteArrayList`
- `ProcessEventBus` supports async event delivery
- `OnlineCalibrator` history is synchronized
- All classes are `Serializable` for persistence

## Integration with External Systems

For integration with AI-based production optimization platforms:

1. Use `ProcessDataPublisher` to stream real-time data
2. Export training data via `TimeSeriesExporter` in JSON format
3. Implement `MLCorrectionInterface` to connect external ML models
4. Use `HybridModelAdapter` to combine physics with ML corrections
5. Subscribe to `ProcessEventBus` for real-time alerts and triggers
