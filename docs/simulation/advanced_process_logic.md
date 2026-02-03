---
title: Advanced Process Logic Features
description: NeqSim's process logic framework has been extended with powerful advanced features for complex process control, startup/shutdown sequences, and decision-making. This document covers the new capabiliti...
---

# Advanced Process Logic Features

## Overview

NeqSim's process logic framework has been extended with powerful advanced features for complex process control, startup/shutdown sequences, and decision-making. This document covers the new capabilities added to the framework.

## New Features Summary

| Feature | Purpose | Key Classes | Status |
|---------|---------|-------------|--------|
| **Startup Logic** | Permissive-based startup sequences | `StartupLogic`, `LogicCondition` | ✓ Complete |
| **Shutdown Logic** | Controlled/emergency ramp-down | `ShutdownLogic` | ✓ Complete |
| **Conditional Branching** | If-then-else decision making | `ConditionalAction` | ✓ Complete |
| **Parallel Execution** | Simultaneous action execution | `ParallelActionGroup` | ✓ Complete |
| **Voting Logic** | Redundant sensor evaluation | `VotingEvaluator`, `VotingPattern` | ✓ Complete |
| **Logic Conditions** | Runtime condition checking | `PressureCondition`, `TemperatureCondition`, `TimerCondition` | ✓ Complete |

---

## 1. Startup Logic with Permissive Checks

### Purpose
Ensures all required conditions are met before starting equipment, following industry best practices for safe process startup.

### Key Features
- **Permissive checks**: Verify temperature, pressure, level, timer conditions
- **Timeout handling**: Abort if permissives not met within configured time
- **Sequential execution**: Actions execute in order after permissives satisfied
- **Automatic abort**: If permissives lost during startup

### Example
```java
StartupLogic startup = new StartupLogic("Compressor Startup");

// Add permissives (ALL must be true before starting)
startup.addPermissive(new TemperatureCondition(cooler, 50.0, "<"));  // Cooled down
startup.addPermissive(new PressureCondition(suction, 3.0, ">"));     // Min pressure
startup.addPermissive(new TimerCondition(60.0));                     // Warm-up time

// Add startup actions
startup.addAction(new OpenValveAction(suctionValve), 0.0);   // Immediate
startup.addAction(new StartPumpAction(lubePump), 2.0);       // After 2s
startup.addAction(new StartCompressorAction(compressor), 10.0); // After 10s

// Activate and execute
startup.activate();
while (!startup.isComplete()) {
    warmupTimer.update(timeStep);
    startup.execute(timeStep);
}
```

### Status Output
```
Separator Startup - WAITING FOR PERMISSIVES (2.0s / 300.0s)
  Permissives:
    ✓ Pressure > 5.0 bara: MET (current: 10.0 bara)
    ✓ Temperature < 50.0°C: MET (current: 25.0°C)
    ✗ Wait 5.0 seconds: NOT MET (current: 4.0 s)
```

---

## 2. Shutdown Logic with Ramp-Down

### Purpose
Provides controlled, gradual equipment shutdown to prevent thermal shock, pressure surges, or process upsets.

### Key Features
- **Controlled shutdown**: Gradual ramp over configurable time (default 5 minutes)
- **Emergency mode**: Accelerated shutdown (5-10x faster)
- **Sequential actions**: Time-based action execution
- **Progress tracking**: Real-time completion percentage

### Example
```java
ShutdownLogic shutdown = new ShutdownLogic("Reactor Shutdown");
shutdown.setRampDownTime(600.0);  // 10 minutes controlled

// Add ramp-down actions
shutdown.addAction(new ReduceFeedAction(feedValve, 75.0), 0.0);   // 75% immediately
shutdown.addAction(new ReduceFeedAction(feedValve, 50.0), 120.0); // 50% after 2 min
shutdown.addAction(new ReduceFeedAction(feedValve, 25.0), 300.0); // 25% after 5 min
shutdown.addAction(new StopHeaterAction(heater), 450.0);          // Stop at 7.5 min
shutdown.addAction(new CloseFeedAction(feedValve), 600.0);        // Close at 10 min

// Controlled shutdown
shutdown.activate();

// Or emergency shutdown (much faster)
shutdown.setEmergencyMode(true);
shutdown.setEmergencyShutdownTime(30.0); // Complete in 30s
shutdown.activate();
```

### Output Comparison
```
CONTROLLED (10 minutes):
Time: 0.0s   → Valve: 75%   → Progress: 0%
Time: 120.0s → Valve: 50%   → Progress: 20%
Time: 600.0s → Valve: 0%    → Progress: 100%

EMERGENCY (30 seconds):
Time: 0.0s  → Valve: 0%     → Progress: 100% (all actions accelerated)
```

---

## 3. Conditional Branching (If-Then-Else)

### Purpose
Enables dynamic decision-making within process sequences based on runtime conditions.

### Key Features
- **If-then-else logic**: Execute different actions based on conditions
- **Runtime evaluation**: Condition checked when action executes
- **Optional alternative**: Can have if-then without else
- **Nestable**: Conditionals can contain other conditionals

### Example
```java
// If temperature > 100°C, open cooling valve; else open bypass valve
LogicCondition highTemp = new TemperatureCondition(reactor, 100.0, ">");
LogicAction openCooling = new OpenValveAction(coolingValve);
LogicAction openBypass = new OpenValveAction(bypassValve);

ConditionalAction conditional = new ConditionalAction(
    highTemp, 
    openCooling,      // If true
    openBypass,       // If false
    "Temperature Control"
);

// Add to sequence
startupLogic.addAction(conditional, 0.0);

// At runtime, evaluates temperature and opens appropriate valve
conditional.execute();
```

### Use Cases
- Temperature-based equipment selection
- Pressure-based bypass activation
- Level-based pump staging
- Time-based mode switching

---

## 4. Parallel Action Execution

### Purpose
Executes multiple actions simultaneously to reduce total sequence time and coordinate equipment.

### Key Features
- **Simultaneous execution**: All actions run at once
- **Completion tracking**: Group complete when ALL actions done
- **Progress monitoring**: Track individual and overall completion
- **Error handling**: Continues executing even if one action fails

### Example
```java
// Open 3 valves simultaneously
ParallelActionGroup parallelOpen = new ParallelActionGroup("Open All Inlet Valves");
parallelOpen.addAction(new OpenValveAction(valve1));
parallelOpen.addAction(new OpenValveAction(valve2));
parallelOpen.addAction(new OpenValveAction(valve3));

// Add to sequence
startupLogic.addAction(parallelOpen, 0.0);

// Executes all valves at once (saves time vs sequential)
parallelOpen.execute();

System.out.printf("Progress: %d/%d complete (%.0f%%)\n",
    parallelOpen.getCompletedCount(),
    parallelOpen.getTotalCount(),
    parallelOpen.getCompletionPercentage());
```

### Benefits
- **Time savings**: 3 actions in parallel take same time as 1 sequential
- **Coordination**: Equipment starts together (e.g., all pumps)
- **Simplicity**: Easier than managing multiple timers

---

## 5. Voting Logic (Enhanced)

### Purpose
Generic voting logic for redundant sensors or conditions, applicable beyond just safety systems.

### Key Features
- **Digital voting**: Boolean conditions (1oo2, 2oo3, etc.)
- **Analog voting**: Continuous values (median, average, mid-value)
- **Fault handling**: Excludes faulty sensors from voting
- **Standard patterns**: 1oo1, 1oo2, 2oo2, 2oo3, 2oo4, 3oo4

### Example - Digital Voting
```java
// 2 out of 3 pressure switches must be high
VotingEvaluator<Boolean> voting = new VotingEvaluator<>(VotingPattern.TWO_OUT_OF_THREE);
voting.addInput(pt1.isHigh(), pt1.isFaulty());
voting.addInput(pt2.isHigh(), pt2.isFaulty());
voting.addInput(pt3.isHigh(), pt3.isFaulty());

boolean alarmActive = voting.evaluateDigital();
```

### Example - Analog Voting
```java
// Median of 3 temperature sensors (best for safety)
VotingEvaluator<Double> tempVoting = new VotingEvaluator<>(VotingPattern.TWO_OUT_OF_THREE);
tempVoting.addInput(tt1.getValue(), tt1.isFaulty());
tempVoting.addInput(tt2.getValue(), tt2.isFaulty());
tempVoting.addInput(tt3.getValue(), tt3.isFaulty());

double temperature = tempVoting.evaluateMedian();  // Most reliable
double tempAvg = tempVoting.evaluateAverage();     // Alternative
double tempMid = tempVoting.evaluateMidValue();    // For 3 sensors
```

### Applications
- Critical process measurements (pressure, temperature, flow)
- Safety systems (HIPPS, Fire & Gas, ESD)
- Quality control measurements
- Redundant control loops

---

## 6. Logic Conditions

### Purpose
Define runtime conditions that can be checked by startup logic, conditional actions, or custom logic.

### Available Conditions

#### PressureCondition
```java
// Check if pressure meets criteria
PressureCondition minPressure = new PressureCondition(stream, 5.0, ">");   // > 5 bara
PressureCondition stable = new PressureCondition(stream, 10.0, "==", 0.5); // ±0.5 bara
```

#### TemperatureCondition
```java
// Check if temperature meets criteria
TemperatureCondition cooled = new TemperatureCondition(heater, 80.0, "<");  // < 80°C
TemperatureCondition ready = new TemperatureCondition(reactor, 150.0, ">="); // ≥ 150°C
```

#### TimerCondition
```java
// Wait for specified duration
TimerCondition warmup = new TimerCondition(60.0);  // 60 seconds
warmup.start();

// In loop
warmup.update(timeStep);
if (warmup.evaluate()) {
    // Time elapsed
}
```

### Supported Operators
- `>` : Greater than
- `>=` : Greater than or equal
- `<` : Less than
- `<=` : Less than or equal
- `==` : Equal (within tolerance)
- `!=` : Not equal

---

## Integration Examples

### Complete Startup Sequence
```java
// Compressor startup with all features
StartupLogic startup = new StartupLogic("Gas Compressor Startup");

// 1. Permissives
startup.addPermissive(new PressureCondition(suction, 3.0, ">"));
startup.addPermissive(new TemperatureCondition(oil, 40.0, ">"));
startup.addPermissive(new TimerCondition(120.0));

// 2. Parallel valve opening
ParallelActionGroup openValves = new ParallelActionGroup("Open Inlet Valves");
openValves.addAction(new OpenValveAction(suctionValve));
openValves.addAction(new OpenValveAction(recycleValve));
startup.addAction(openValves, 0.0);

// 3. Conditional lubrication
LogicCondition oilPressureLow = new PressureCondition(oilSystem, 2.0, "<");
ConditionalAction startAuxOilPump = new ConditionalAction(
    oilPressureLow,
    new StartPumpAction(auxOilPump),
    "Auxiliary Oil Pump"
);
startup.addAction(startAuxOilPump, 2.0);

// 4. Start compressor
startup.addAction(new StartCompressorAction(compressor), 10.0);

// Execute
startup.activate();
```

### Layered Safety System
```java
// HIPPS → Fire/Gas → ESD with voting
VotingEvaluator<Double> pressureVoting = new VotingEvaluator<>(VotingPattern.TWO_OUT_OF_THREE);
pressureVoting.addInput(pt1.getValue(), pt1.isFaulty());
pressureVoting.addInput(pt2.getValue(), pt2.isFaulty());
pressureVoting.addInput(pt3.getValue(), pt3.isFaulty());

double votedPressure = pressureVoting.evaluateMedian();

// Use voted pressure for HIPPS
if (votedPressure > hippsSetpoint) {
    hipps.activate();
}
```

---

## Best Practices

### Startup Logic
1. **Order permissives** by criticality (most important first)
2. **Use reasonable timeouts** (5 minutes default is good)
3. **Test permissives** before going live
4. **Add delays** between actions for stabilization

### Shutdown Logic
5. **Gradual ramp-down** prevents thermal shock (10-30 minutes typical)
6. **Emergency mode** for critical situations only
7. **Monitor progress** to verify controlled shutdown
8. **Cool down** before stopping agitation/mixing

### Voting Logic
9. **2oo3 is standard** for safety systems (good balance)
10. **Median is preferred** over average for safety (outlier rejection)
11. **Track faulty sensors** and enforce bypass limits
12. **Test voting logic** with sensor failures

### Conditional Logic
13. **Keep conditions simple** - one comparison per condition
14. **Provide alternatives** when feasible (else path)
15. **Test both paths** in simulation
16. **Document decisions** in descriptions

### Parallel Execution
17. **Group related actions** (all valves, all pumps)
18. **Check completion** before proceeding
19. **Independent actions only** (no dependencies)
20. **Monitor individual** actions for failures

---

## Performance Considerations

| Feature | Overhead | Typical Use |
|---------|----------|-------------|
| Startup Logic | Low | Once per startup (minutes) |
| Shutdown Logic | Low | Once per shutdown (minutes/hours) |
| Conditional Action | Very Low | Infrequent (mode changes) |
| Parallel Group | Very Low | Startup/shutdown only |
| Voting Logic | Very Low | Every control loop (seconds) |
| Conditions | Very Low | Continuous monitoring |

All features are designed for real-time performance with minimal overhead.

---

## See Also

- [Process Logic Framework](process_logic_framework.md) - Base architecture
- [SIS Logic Implementation](../safety/sis_logic_implementation.md) - Safety systems
- [HIPPS Safety Logic](../safety/hipps_safety_logic.md) - Pressure protection
- [Layered Safety Architecture](../safety/layered_safety_architecture.md) - Defense in depth
- [Advanced Process Logic Example](../src/main/java/neqsim/process/util/example/AdvancedProcessLogicExample.java) - Complete code example

---

## Summary

The advanced process logic features provide industrial-grade capabilities for:
- ✓ Safe equipment startup with permissive verification
- ✓ Controlled shutdown with thermal protection
- ✓ Dynamic decision-making with conditional branching
- ✓ Efficient parallel operation coordination
- ✓ Reliable redundant sensor voting
- ✓ Flexible runtime condition checking

These features follow industry best practices from standards like ISA-88 (batch), ISA-84 (SIS), and IEC 61131-3 (PLC programming) to provide robust, production-ready process control logic.
