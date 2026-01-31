# P1: Dynamic Simulation Integration

## Overview

The Dynamic Simulation package extends NeqSim's Monte Carlo risk analysis to include transient effects during equipment failures. Traditional steady-state analysis captures production losses during failures but misses the significant losses that occur during:

- **Shutdown transients** - Production ramp-down when equipment fails
- **Startup transients** - Production ramp-up when equipment is restored

These transient losses can represent 15-30% of total production losses in dynamic systems.

## Key Classes

### DynamicRiskSimulator

The main entry point for dynamic risk simulation.

```java
DynamicRiskSimulator simulator = new DynamicRiskSimulator("Platform Risk Analysis");

// Set base production
simulator.setBaseProductionRate(150.0);  // MMscf/day
simulator.setProductionUnit("MMscf/day");

// Add equipment with failure characteristics
// Parameters: name, MTBF (hours), repair time (hours), production impact (0-1)
simulator.addEquipment("Export Compressor", 8760, 72, 1.0);  // Critical
simulator.addEquipment("HP Separator", 17520, 24, 0.6);      // Major
simulator.addEquipment("Glycol Pump", 4380, 8, 0.1);         // Minor
```

### Ramp Profiles

Configure how production changes during transients:

```java
// Available profiles
simulator.setShutdownProfile(DynamicRiskSimulator.RampProfile.LINEAR);
simulator.setStartupProfile(DynamicRiskSimulator.RampProfile.S_CURVE);

// Profile options:
// - LINEAR: Constant rate change
// - EXPONENTIAL: Rapid initial change, slowing over time
// - S_CURVE: Slow-fast-slow (most realistic)
// - STEP: Instantaneous change (traditional model)

// Set durations
simulator.setShutdownTime(4.0);  // 4 hours to shut down
simulator.setStartupTime(8.0);   // 8 hours to restore
```

### Running Simulation

```java
// Configure simulation parameters
simulator.setSimulationHorizon(8760);  // 1 year in hours
simulator.setIterations(10000);        // Monte Carlo iterations
simulator.setTimeStep(1.0);            // 1-hour resolution

// Run simulation
DynamicRiskResult result = simulator.runSimulation();
```

### DynamicRiskResult

Results include standard statistics plus transient analysis:

```java
// Production statistics
double expected = result.getExpectedProduction();
double p10 = result.getP10Production();  // Optimistic
double p50 = result.getP50Production();  // Median
double p90 = result.getP90Production();  // Conservative

// Transient analysis
TransientLossStatistics transient = result.getTransientLoss();
double shutdownLoss = transient.getShutdownLoss();
double startupLoss = transient.getStartupLoss();
double steadyStateLoss = result.getSteadyStateLoss();

// Availability
double availability = result.getAvailability();
```

### ProductionProfile

Access time-series production data:

```java
ProductionProfile profile = result.getSampleProductionProfile();
for (ProductionProfile.TimePoint point : profile.getTimePoints()) {
    double time = point.getTime();        // hours
    double production = point.getProduction();  // production rate
    String state = point.getState();      // "NORMAL", "SHUTDOWN", "STARTUP"
}
```

## Use Cases

### 1. Assessing Transient Impact

```java
DynamicRiskSimulator sim = new DynamicRiskSimulator("Transient Impact Study");
sim.setBaseProductionRate(100.0);
sim.addEquipment("Compressor", 8760, 72, 1.0);

// Compare step vs realistic profiles
sim.setShutdownProfile(DynamicRiskSimulator.RampProfile.STEP);
sim.setStartupProfile(DynamicRiskSimulator.RampProfile.STEP);
DynamicRiskResult stepResult = sim.runSimulation();

sim.setShutdownProfile(DynamicRiskSimulator.RampProfile.S_CURVE);
sim.setStartupProfile(DynamicRiskSimulator.RampProfile.S_CURVE);
DynamicRiskResult dynamicResult = sim.runSimulation();

double transientImpact = dynamicResult.getTransientLoss().getTotalTransientLoss();
System.out.println("Additional losses from transients: " + transientImpact);
```

### 2. Optimizing Startup Procedures

```java
// Test different startup times
double[] startupTimes = {4.0, 8.0, 12.0, 24.0};
for (double startupTime : startupTimes) {
    sim.setStartupTime(startupTime);
    DynamicRiskResult result = sim.runSimulation();
    System.out.println("Startup " + startupTime + "h: " + 
        result.getExpectedProduction() + " MMscf/year");
}
```

### 3. Equipment Criticality Analysis

```java
// Identify which equipment transients cause most losses
DynamicRiskSimulator sim = new DynamicRiskSimulator("Criticality Analysis");
sim.setBaseProductionRate(100.0);
sim.addEquipment("Compressor", 8760, 72, 1.0);
sim.addEquipment("Separator", 17520, 24, 0.8);
sim.addEquipment("Pump", 4380, 12, 0.3);

DynamicRiskResult result = sim.runSimulation();

// Get per-equipment contribution
for (EquipmentRiskContribution contrib : result.getEquipmentContributions()) {
    System.out.println(contrib.getName() + 
        ": Steady=" + contrib.getSteadyStateLoss() +
        ", Transient=" + contrib.getTransientLoss());
}
```

## Integration with Process Simulation

The dynamic simulator can be connected to NeqSim process models:

```java
// Create process system
ProcessSystem process = createProcessSystem();

// Create simulator
DynamicRiskSimulator sim = new DynamicRiskSimulator("Process Risk");

// Add equipment from process
for (ProcessEquipmentInterface equip : process.getUnitOperations()) {
    double mtbf = getEquipmentMTBF(equip);
    double repairTime = getRepairTime(equip);
    double impact = calculateProductionImpact(process, equip);
    sim.addEquipment(equip.getName(), mtbf, repairTime, impact);
}

// Run simulation
DynamicRiskResult result = sim.runSimulation();
```

## Output Format

### JSON Export

```java
String json = result.toJson();
```

Example output:
```json
{
  "simulationName": "Platform Risk Analysis",
  "baseProduction": 150.0,
  "productionUnit": "MMscf/day",
  "simulationHorizon": 8760,
  "iterations": 10000,
  "results": {
    "expectedProduction": 52560.5,
    "p10Production": 54230.0,
    "p50Production": 52800.0,
    "p90Production": 50120.0,
    "steadyStateLoss": 1200.5,
    "transientLoss": {
      "shutdownLoss": 180.2,
      "startupLoss": 320.8,
      "totalTransientLoss": 501.0
    },
    "availability": 0.965
  },
  "equipmentContributions": [
    {"name": "Export Compressor", "steadyStateLoss": 800.0, "transientLoss": 350.0},
    {"name": "HP Separator", "steadyStateLoss": 300.0, "transientLoss": 120.0}
  ]
}
```

## Best Practices

1. **Time Resolution**: Use 1-hour time steps for most analyses; use finer resolution (0.25h) only when studying fast transients

2. **Iterations**: Use 5,000-10,000 iterations for reliable P90 estimates

3. **Startup Profiles**: S_CURVE is most realistic for rotating equipment; use EXPONENTIAL for thermal processes

4. **Equipment Impact**: Carefully estimate production impact factors using process models or historical data

5. **Correlation**: Consider equipment dependencies (not yet in this implementation but planned)

## References

- OREDA Handbook (Offshore Reliability Data)
- ISO 14224: Collection and exchange of reliability and maintenance data
- SPE 181025: Production Loss Analysis Using Monte Carlo Simulation
