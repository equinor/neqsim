# Process Logic and Scenario Simulation Enhancements

This document describes the enhancements made to the NeqSim process logic framework to support advanced safety system simulation with logic sequences and scenario testing.

## New Core Components Added

### 1. Logic Actions (`src/main/java/neqsim/process/logic/action/`)

#### `SetValveOpeningAction.java`
- Sets throttling valve to specific opening percentage (0-100%)
- Considers action complete when within 1% of target
- Used for controlled valve positioning

#### `OpenValveAction.java`
- Fully opens a throttling valve (100% opening)
- Simple action for emergency or startup valve opening
- Considers complete when >95% open

#### `CloseValveAction.java`
- Fully closes a throttling valve (0% opening)
- Simple action for emergency valve closure
- Considers complete when <5% open

#### `SetSeparatorModeAction.java`
- Switches separator between steady-state and transient calculation modes
- Critical for dynamic process simulation during upsets
- Instantaneous action

### 2. Logic Conditions (`src/main/java/neqsim/process/logic/condition/`)

#### `ValvePositionCondition.java`
- Monitors valve opening percentage with comparison operators
- Supports >, >=, <, <=, ==, != with configurable tolerance
- Essential for startup permissives and safety interlocks

### 3. Scenario Execution Framework (`src/main/java/neqsim/process/util/scenario/`)

#### `ProcessScenarioRunner.java`
- Coordinates process system simulation with logic execution
- Applies `ProcessSafetyScenario` perturbations automatically
- Provides real-time monitoring and status reporting
- Handles multiple logic sequences concurrently
- Integrates with transient simulation framework

#### `ScenarioExecutionSummary.java`
- Captures complete scenario execution results
- Tracks logic sequence final states and status descriptions
- Records errors, warnings, and performance metrics
- Enables automated testing and validation

## Enhanced PushButton Integration

The existing `PushButton` class already supported linking to multiple `ProcessLogic` sequences via the `linkToLogic()` method, enabling:
- Manual ESD activation
- Startup sequence initiation
- Multi-logic coordination from single operator action

## Example Implementation

### `ProcessLogicIntegratedExample.java`

This comprehensive example demonstrates:

1. **Process System Construction**
   - High-pressure gas separation process
   - Safety valves, blowdown systems, and flare
   - Complete instrumentation setup

2. **ESD Logic Implementation**
   ```java
   ESDLogic esdLogic = new ESDLogic("ESD Level 1");
   esdLogic.addAction(new CloseValveAction(inletValve), 0.0);
   esdLogic.addAction(new SetSplitterAction(gasSplitter, new double[]{0.0, 1.0}), 0.5);
   esdLogic.addAction(new ActivateBlowdownAction(bdValve), 0.5);
   esdLogic.addAction(new SetSeparatorModeAction(separator, false), 1.0);
   ```

3. **Startup Logic with Permissives**
   ```java
   StartupLogic startupLogic = new StartupLogic("System Startup");
   startupLogic.addPermissive(new PressureCondition(separator, 5.0, "<"));
   startupLogic.addPermissive(new ValvePositionCondition(bdValve, "<", 5.0));
   startupLogic.addPermissive(new TimerCondition(10.0));
   ```

4. **Scenario Testing**
   ```java
   ProcessScenarioRunner runner = new ProcessScenarioRunner(processSystem);
   runner.addLogic(esdLogic);
   runner.addLogic(startupLogic);
   
   ProcessSafetyScenario scenario = ProcessSafetyScenario.builder("High Pressure")
       .customManipulator("HP Feed", stream -> stream.setPressure(70.0, "bara"))
       .build();
   
   runner.runScenario("High Pressure Test", scenario, 60.0, 1.0);
   ```

## Key Benefits

### 1. **Reusable Components**
- All logic actions and conditions are reusable across different examples
- `ProcessScenarioRunner` can be used for any process system
- Standard patterns for safety system implementation

### 2. **Comprehensive Testing**
- Automated scenario execution with perturbation application
- Real-time monitoring of logic sequence states
- Detailed execution summaries for validation

### 3. **Safety System Integration**
- Multi-layer protection (ESD, startup permissives, PSV)
- Proper sequencing with timing delays
- Automatic mode switching for dynamic simulation

### 4. **Industry Best Practices**
- Follows IEC 61511 safety lifecycle concepts
- Implements defense-in-depth protection philosophy
- Supports SIL (Safety Integrity Level) classifications

## Usage Patterns

### Basic ESD Implementation
```java
ESDLogic esd = new ESDLogic("Emergency Shutdown");
esd.addAction(new CloseValveAction(inletValve), 0.0);
esd.addAction(new ActivateBlowdownAction(blowdownValve), 1.0);
pushButton.linkToLogic(esd);
```

### Startup with Permissives
```java
StartupLogic startup = new StartupLogic("Safe Startup");
startup.addPermissive(new PressureCondition(vessel, 2.0, "<"));
startup.addPermissive(new TemperatureCondition(vessel, 40.0, "<"));
startup.addPermissive(new TimerCondition(30.0));
startup.addAction(new OpenValveAction(feedValve), 0.0);
```

### Scenario Testing
```java
ProcessScenarioRunner runner = new ProcessScenarioRunner(system);
runner.addLogic(esdLogic);

ProcessSafetyScenario overpressure = ProcessSafetyScenario.builder("Overpressure")
    .customManipulator("Feed", s -> s.setPressure(80.0, "bara"))
    .build();

ScenarioExecutionSummary result = runner.runScenario("Test", overpressure, 120.0, 1.0);
```

## Future Enhancements

The framework is designed for easy extension:
- Additional logic actions (pump control, compressor sequencing)
- More sophisticated conditions (flow rate trending, multi-variable)
- Advanced scenario types (Monte Carlo analysis, optimization)
- Integration with external safety systems
- Real-time process historian connectivity

This implementation provides a solid foundation for complex process safety simulation while maintaining the clean architecture and patterns established in the NeqSim framework.