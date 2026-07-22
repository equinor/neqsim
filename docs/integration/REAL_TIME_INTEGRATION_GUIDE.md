---
title: NeqSim Real-Time Digitalization Integration Guide
description: This guide shows how to integrate NeqSim process simulations with your existing digitalization ecosystem, including live data feedback, SCADA/PLC interfaces, and historian systems.
---

# NeqSim Real-Time Digitalization Integration Guide

## Overview
This guide shows how to integrate NeqSim process simulations with your existing digitalization ecosystem, including live data feedback, SCADA/PLC interfaces, and historian systems.

## Integration Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Plant/Field   │    │   Control Layer │    │ NeqSim Digital  │
│   Instruments   │◄──►│   (PLC/SCADA)   │◄──►│     Twin        │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   OPC UA/DA     │    │   PI Historian  │    │   Seeq/Analytics│
│   Real-time     │    │   Time-series   │    │   Advanced      │
│   Data Exchange │    │   Data Storage  │    │   Analytics     │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## Key Integration Points

### 1. **OPC UA/DA Integration**
Connect to PLCs and SCADA systems for real-time data exchange:

```java
// Subscribe to live plant data
opcClient.subscribe("PLC.PI_101.Value", this::updatePressureReading);
opcClient.subscribe("PLC.TI_101.Value", this::updateTemperatureReading);

// Send predicted values back to control system
opcClient.writeValue("PLC.PI_101.Predicted", simulatedPressure);
```

**Benefits:**
- Real-time model validation against plant data
- Predictive control optimization
- What-if scenario analysis with live conditions

### 2. **PI/OSIsoft Historian Integration**
Store both actual and simulated values for analytics:

```java
// Configure tags for both actual and simulated data
piHistorian.configureTag("PLANT.V101.Pressure.Actual", "PI-101");
piHistorian.configureTag("NEQSIM.V101.Pressure.Simulated", "PI-101");

// Write comparison data
piHistorian.writeValue("PLANT.V101.Pressure.Actual", actualValue);
piHistorian.writeValue("NEQSIM.V101.Pressure.Simulated", simulatedValue);
```

**Analytics Opportunities:**
- Model accuracy trending
- Equipment performance degradation detection
- Energy optimization opportunities
- Maintenance planning support

### 3. **SCADA/DCS Integration**
Integrate with control room displays and alarm systems:

```java
// Register process alarms
scadaInterface.registerAlarm("HIGH_PRESSURE", 55.0, this::triggerHighPressureAlarm);

// Update operator displays with predictions
scadaInterface.updateTrends(processSystem);
```

### 4. **Seeq Integration Pattern**
For advanced analytics and investigation:

```java
public class SeeqIntegration {
  
  public void publishAdvancedAnalytics() {
    // Equipment efficiency calculations
    double efficiency = calculateSeparatorEfficiency();
    seeqClient.writeCalculation("V101.Efficiency", efficiency);
    
    // Energy optimization metrics
    double energyIntensity = calculateEnergyIntensity();
    seeqClient.writeCalculation("Process.EnergyIntensity", energyIntensity);
    
    // Predictive maintenance indicators
    double foulingIndex = calculateFoulingIndex();
    seeqClient.writeCalculation("V101.FoulingIndex", foulingIndex);
  }
}
```

## Implementation Patterns

### Digital Twin Pattern
```java
public class ProcessDigitalTwin {
  private ProcessSystem physicalModel;
  private DataReconciliation reconciler;
  private PredictiveController controller;
  
  public void synchronizeWithPlant() {
    // 1. Get live plant data
    Map<String, Double> plantData = opcClient.readAllTags();
    
    // 2. Update simulation with current conditions
    updateModelWithPlantData(plantData);
    
    // 3. Run simulation
    physicalModel.run();
    
    // 4. Compare predictions with reality
    reconciler.validatePredictions(plantData);
    
    // 5. Adjust model if needed
    if (reconciler.hasSignificantDeviation()) {
      reconciler.adjustModelParameters();
    }
    
    // 6. Generate predictions for control system
    controller.generateOptimalSetpoints();
  }
}
```

### Model Predictive Control Integration
```java
public class MPCIntegration {
  
  public void optimizeControlActions() {
    // Run multiple scenarios with NeqSim
    List<ProcessSafetyScenario> scenarios = generateControlScenarios();
    
    for (ProcessSafetyScenario scenario : scenarios) {
      ScenarioExecutionSummary result = runner.runScenario(
          scenario.getName(), scenario, 30.0, 1.0
      );
      
      // Evaluate economic objective function
      double profit = calculateProfit(result);
      double safety = evaluateSafetyMargins(result);
      double emissions = calculateEmissions(result);
      
      // Multi-objective optimization
      double objectiveFunction = profit - safety_penalty - emissions_cost;
      
      if (objectiveFunction > bestObjective) {
        bestControlActions = extractControlActions(scenario);
      }
    }
    
    // Send optimal setpoints to control system
    opcClient.writeValue("PLC.PV_101.Setpoint", bestControlActions.valveOpening);
  }
}
```

### Real-Time Optimization
```java
public class RealTimeOptimizer {
  
  public void continuousOptimization() {
    while (true) {
      try {
        // 1. Get current plant state
        ProcessState currentState = getCurrentPlantState();
        
        // 2. Update NeqSim model
        updateSimulationModel(currentState);
        
        // 3. Run optimization scenarios
        OptimizationResult optimal = findOptimalOperatingPoint();
        
        // 4. Check if changes are beneficial
        if (optimal.improvementPercent > 2.0) {
          // Send new setpoints to control system
          implementOptimalSetpoints(optimal);
          
          // Log optimization action
          piHistorian.writeEvent("NEQSIM.Optimization", 
              "Improvement: " + optimal.improvementPercent + "%");
        }
        
        Thread.sleep(60000); // Optimize every minute
        
      } catch (Exception e) {
        handleOptimizationError(e);
      }
    }
  }
}
```

## Technology Stack Recommendations

### OPC Connectivity
```xml
<!-- Eclipse Milo OPC UA Client -->
<dependency>
    <groupId>org.eclipse.milo</groupId>
    <artifactId>sdk-client</artifactId>
    <version>0.6.8</version>
</dependency>
```

### PI System Integration
```xml
<!-- OSIsoft PI SDK (commercial license required) -->
<dependency>
    <groupId>com.osisoft</groupId>
    <artifactId>pi-web-api-client</artifactId>
    <version>1.13.0</version>
</dependency>
```

### MQTT for IoT Integration
```xml
<!-- Eclipse Paho MQTT Client -->
<dependency>
    <groupId>org.eclipse.paho</groupId>
    <artifactId>org.eclipse.paho.client.mqttv3</artifactId>
    <version>1.2.5</version>
</dependency>
```

### REST API Integration
```xml
<!-- Spring Boot for REST APIs -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <version>3.2.0</version>
</dependency>
```

## Data Flow Examples

### 1. **Live Process Monitoring**
```
Plant Sensors → PLC → OPC Server → NeqSim → Model Validation → PI Historian
                                         ↓
                                   Alarm Generation → SCADA Display
```

### 2. **Predictive Control**
```
Current State → NeqSim Scenarios → Optimization → Setpoint Updates → PLC
                      ↓
               Performance Metrics → Seeq Analytics → KPI Dashboard
```

### 3. **Equipment Health Monitoring**
```
Process Data → NeqSim Physics Model → Fouling Detection → Maintenance Alert
                                               ↓
                                     Work Order System → CMMS
```

## Benefits of Integration

### Operational Excellence
- **Real-time optimization**: 2-5% efficiency improvements
- **Predictive maintenance**: 20-30% reduction in unplanned downtime  
- **Energy optimization**: 3-8% energy savings
- **Quality prediction**: Reduced off-spec production

### Safety & Compliance
- **What-if analysis**: Evaluate scenarios before implementation
- **Alarm rationalization**: Physics-based alarm limits
- **Emergency response**: Validated emergency procedures
- **Environmental compliance**: Emissions prediction and control

### Digital Twin Capabilities
- **Virtual sensors**: Estimate unmeasured variables
- **Model reconciliation**: Automatic parameter adjustment
- **Scenario planning**: Test operational changes safely
- **Training simulator**: Operator training with real plant data

## Getting Started

1. **Start Small**: Begin with one unit operation and a few measurement points
2. **Validate Models**: Ensure NeqSim predictions match plant performance
3. **Implement Gradually**: Add more integration points as confidence builds
4. **Monitor Benefits**: Track KPIs to demonstrate value

The integration patterns shown here transform NeqSim from a standalone simulation tool into a live digital twin that continuously optimizes your process operations.