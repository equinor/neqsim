---
title: "Process Logic Framework for NeqSim"
description: "This document describes the proposed process logic framework for NeqSim, enabling complex automation sequences including ESD, startup, shutdown, and general process control logic."
---

# Process Logic Framework for NeqSim

## Overview
This document describes the proposed process logic framework for NeqSim, enabling complex automation sequences including ESD, startup, shutdown, and general process control logic.

## Architecture

### Core Components

#### 1. **ProcessLogic** (Interface)
Base interface for all process logic implementations.

```java
public interface ProcessLogic {
  String getName();
  LogicState getState();
  void activate();
  void deactivate();
  void reset();
  void execute(double timeStep);
  boolean isActive();
  List<LogicAction> getActions();
  List<ProcessEquipmentInterface> getTargetEquipment();
}
```

#### 2. **LogicSequence**
Executes ordered steps with timing, conditions, and actions.

```java
public class LogicSequence implements ProcessLogic {
  private List<SequenceStep> steps;
  private int currentStep;
  private double elapsedTime;
  private LogicState state; // IDLE, RUNNING, PAUSED, COMPLETED, FAILED
  
  public void addStep(SequenceStep step);
  public void executeCurrentStep(double timeStep);
  public boolean canProceedToNextStep();
}
```

#### 3. **SequenceStep**
Individual step in a logic sequence.

```java
public class SequenceStep {
  private String name;
  private List<LogicAction> actions;
  private List<LogicCondition> preconditions;
  private List<LogicCondition> completionConditions;
  private double minimumDuration; // Min time in step
  private double maximumDuration; // Max time (timeout)
  private double delay; // Initial delay before executing
  
  public void execute();
  public boolean isComplete();
  public boolean hasTimedOut();
}
```

#### 4. **LogicAction**
Represents an action on equipment.

```java
public interface LogicAction {
  void execute();
  String getDescription();
  boolean isComplete();
}

// Common implementations:
// - ValveAction (open, close, set position)
// - PumpAction (start, stop, set speed)
// - SeparatorAction (switch mode)
// - SplitterAction (set split factors)
// - AlarmAction (raise, acknowledge, reset)
```

#### 5. **LogicCondition**
Boolean condition that must be satisfied.

```java
public interface LogicCondition {
  boolean evaluate();
  String getDescription();
}

// Common implementations:
// - PressureCondition (above/below setpoint)
// - TemperatureCondition
// - FlowCondition
// - LevelCondition
// - ValvePositionCondition
// - TimerCondition
// - EquipmentStateCondition
```

### Logic Types

#### A. ESD Logic (`ESDLogic`)

Implements emergency shutdown procedures following IEC 61511 patterns.

**Features:**
- Multiple ESD levels (L1, L2, L3)
- Cause-and-effect matrix
- Voting logic (1oo1, 1oo2, 2oo3, etc.)
- Override capabilities
- Reset logic with permissives

**Example:**
```java
ESDLogic esdL1 = new ESDLogic("ESD Level 1");

// Add triggers
esdL1.addTrigger(new ManualTrigger(pushButton));
esdL1.addTrigger(new PressureTrigger(separator, "HIHI", 55.0, "bara"));

// Define sequence
esdL1.addStep("Close inlet valves")
    .addAction(new TripValveAction(esdValve1))
    .addAction(new TripValveAction(esdValve2))
    .withDelay(0.0);

esdL1.addStep("Open blowdown valve")
    .addAction(new ActivateValveAction(bdValve))
    .withDelay(0.5); // 0.5s after inlet closure

esdL1.addStep("Stop feed pumps")
    .addAction(new StopPumpAction(feedPump1))
    .addAction(new StopPumpAction(feedPump2))
    .withDelay(1.0);

esdL1.addStep("Switch to dynamic mode")
    .addAction(new SeparatorModeAction(separator, false))
    .withDelay(0.0);

// Add reset permissives
esdL1.addResetPermissive(new PressureCondition(separator, "<", 10.0, "bara"));
esdL1.addResetPermissive(new ManualPermissive("Operator approval"));
```

#### B. Startup Logic (`StartupLogic`)

Implements sequential startup procedures with interlocks.

**Features:**
- Step-by-step equipment startup
- Permissive checks before each step
- Automatic vs. manual mode
- Parallel and sequential operations
- Rollback on failure

**Example:**
```java
StartupLogic startup = new StartupLogic("Separator Train Startup");

startup.addStep("Pre-startup checks")
    .addCondition(new ValvePositionCondition(bdValve, "<", 1.0)) // BD closed
    .addCondition(new PressureCondition(separator, "<", 5.0, "bara")) // Depressurized
    .withTimeout(60.0);

startup.addStep("Open feed isolation")
    .addAction(new EnergizeValveAction(esdValve))
    .withDelay(2.0)
    .withMinDuration(5.0); // Wait for valve to fully open

startup.addStep("Start feed flow")
    .addAction(new SetValveOpeningAction(controlValve, 10.0)) // 10% opening
    .addCondition(new FlowCondition(feedStream, ">", 100.0, "kg/hr"))
    .withTimeout(30.0);

startup.addStep("Ramp up to normal flow")
    .addAction(new RampValveAction(controlValve, 10.0, 50.0, 120.0)) // 10% to 50% over 120s
    .withMinDuration(120.0);

startup.addStep("Enable process control")
    .addAction(new EnableControllerAction(pressureController))
    .addAction(new EnableControllerAction(levelController));
```

#### C. Shutdown Logic (`ShutdownLogic`)

Implements orderly shutdown procedures.

**Features:**
- Normal vs. emergency shutdown
- Controlled ramp-down
- Equipment isolation sequence
- Depressurization logic

**Example:**
```java
ShutdownLogic normalShutdown = new ShutdownLogic("Normal Shutdown");

normalShutdown.addStep("Reduce feed rate")
    .addAction(new RampValveAction(controlValve, 50.0, 5.0, 300.0)) // 5 min ramp
    .withMinDuration(300.0);

normalShutdown.addStep("Stop feed")
    .addAction(new SetValveOpeningAction(controlValve, 0.0));

normalShutdown.addStep("Depressurize")
    .addAction(new SetSplitterAction(gasSplitter, new double[]{0.0, 1.0}))
    .addCondition(new PressureCondition(separator, "<", 5.0, "bara"))
    .withTimeout(600.0);

normalShutdown.addStep("Close isolation")
    .addAction(new TripValveAction(esdValve));
```

### Integration with Existing Components

#### Updated PushButton
```java
public class PushButton extends MeasurementDeviceBaseClass {
  private List<ProcessLogic> linkedLogics = new ArrayList<>();
  
  public void linkToLogic(ProcessLogic logic) {
    linkedLogics.add(logic);
  }
  
  public void push() {
    isPushed = true;
    // Activate all linked logic sequences
    for (ProcessLogic logic : linkedLogics) {
      logic.activate();
    }
  }
}
```

#### Equipment Modifications
All equipment should implement `LogicTarget` interface:
```java
public interface LogicTarget {
  void acceptLogicAction(LogicAction action);
  Map<String, Object> getLogicState();
}
```

### Logic Execution Model

#### Transient Simulation Integration
```java
public class ProcessSystem {
  private List<ProcessLogic> activeLogics = new ArrayList<>();
  
  public void runTransient(double timeStep, UUID id) {
    // 1. Evaluate logic triggers
    for (ProcessLogic logic : activeLogics) {
      if (logic.shouldActivate()) {
        logic.activate();
      }
    }
    
    // 2. Execute active logic sequences
    for (ProcessLogic logic : activeLogics) {
      if (logic.isActive()) {
        logic.execute(timeStep);
      }
    }
    
    // 3. Run equipment
    for (ProcessEquipmentInterface equipment : unitOperations) {
      equipment.runTransient(timeStep, id);
    }
  }
}
```

## Usage Examples

### Example 1: ESD System with Multiple Levels
```java
// ESD Level 1 - Process Shutdown
ESDLogic esdL1 = new ESDLogic("ESD-L1");
esdL1.addTrigger(new ManualTrigger(pushButton1));
esdL1.addTrigger(new PressureTrigger(separator, "HH", 55.0));
esdL1.addStep(/* ... */);

// ESD Level 2 - Blowdown
ESDLogic esdL2 = new ESDLogic("ESD-L2");
esdL2.addTrigger(new ManualTrigger(pushButton2));
esdL2.addTrigger(new PressureTrigger(separator, "HIHI", 60.0));
esdL2.addTrigger(new CascadeTrigger(esdL1)); // L1 also triggers L2
esdL2.addStep(/* ... */);

// Link push button to both levels
pushButton1.linkToLogic(esdL1);
pushButton2.linkToLogic(esdL2);
```

### Example 2: Complete Startup Sequence
```java
StartupLogic startup = new StartupLogic("Full Process Startup");

// Add all startup steps with proper interlocks
startup.enableAutoMode(); // Automatic progression between steps
startup.setFailureAction(new RollbackAction()); // Rollback on failure

// Execute
startup.activate();
while (!startup.isComplete()) {
  startup.execute(timeStep);
  processSystem.runTransient(timeStep, UUID.randomUUID());
}
```

### Example 3: Coordinated Multi-Unit Operation
```java
ProcessLogic multiUnitLogic = new LogicSequence("Train A Startup");

// Start compressor first
multiUnitLogic.addStep("Start compressor")
    .addAction(new StartCompressorAction(comp1))
    .addCondition(new RPMCondition(comp1, ">", 3000));

// Then open inlet valve
multiUnitLogic.addStep("Open inlet")
    .addAction(new EnergizeValveAction(inletValve))
    .addPrecondition(new CompressorRunningCondition(comp1));

// Start separator
multiUnitLogic.addStep("Start separator")
    .addAction(new StartSeparatorAction(sep1))
    .withParallel(new StartPumpAction(exportPump));
```

## Implementation Priority

### Phase 1: Core Framework (Immediate)
1. `ProcessLogic` interface
2. `LogicSequence` class
3. `SequenceStep` class
4. Basic `LogicAction` implementations (valve, pump)
5. Basic `LogicCondition` implementations (pressure, flow)

### Phase 2: ESD Logic (High Priority)
1. `ESDLogic` class
2. `ESDLevel` enum
3. Manual trigger integration
4. Automatic trigger (pressure, temp, level)
5. Updated `PushButton` to support multiple targets

### Phase 3: Startup/Shutdown (Medium Priority)
1. `StartupLogic` class
2. `ShutdownLogic` class
3. Permissive checking
4. Rollback capabilities

### Phase 4: Advanced Features (Future)
1. Voting logic (1oo2, 2oo3)
2. Override management
3. Cause-and-effect matrices
4. Visual logic editor integration
5. IEC 61131-3 function block style

## Benefits

1. **Reusability**: Define logic once, use across multiple simulations
2. **Maintainability**: Clear separation of logic from equipment
3. **Testability**: Logic can be unit tested independently
4. **Flexibility**: Easy to modify sequences without changing equipment code
5. **Standards Compliance**: Aligns with IEC 61511/61131 patterns
6. **Documentation**: Self-documenting through sequence steps
7. **Safety**: Enforces proper sequence execution and interlocks

## Design Considerations

### Thread Safety
- Logic execution should be thread-safe for parallel simulations
- Use immutable conditions where possible

### Performance
- Lazy evaluation of conditions
- Cache condition results within a time step
- Efficient equipment lookup

### Error Handling
- Clear failure modes
- Recovery procedures
- Logging and diagnostics

### Backward Compatibility
- Existing examples continue to work
- Optional adoption of logic framework
- Gradual migration path

## Conclusion

This framework provides a robust, extensible foundation for implementing complex process logic in NeqSim while maintaining the library's existing architecture and design patterns.
