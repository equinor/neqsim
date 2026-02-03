---
title: "Runtime Logic Flexibility in NeqSim Process Framework"
description: "The NeqSim process logic framework is designed with excellent runtime flexibility through its interface-based architecture. You can create, modify, and execute complex process logic sequences entirely..."
---

# Runtime Logic Flexibility in NeqSim Process Framework

## Summary

**YES, it is extremely easy to add new logic programmatically without pre-compilation!**

The NeqSim process logic framework is designed with excellent runtime flexibility through its interface-based architecture. You can create, modify, and execute complex process logic sequences entirely at runtime without any need for pre-compilation.

## Key Flexibility Features

### 1. **Interface-Based Design**
- `LogicAction` and `LogicCondition` are interfaces that can be implemented dynamically
- `ProcessLogic` implementations accept actions/conditions at runtime
- No dependencies on specific compiled action types

### 2. **Runtime Logic Creation**
All logic is created programmatically:

```java
// Create ESD logic at runtime
ESDLogic esdLogic = new ESDLogic("Dynamic ESD");
esdLogic.addAction(new CloseValveAction(valve), 0.0);
esdLogic.addAction(new SetSplitterAction(splitter, new double[]{0.0, 1.0}), 0.5);

// Create startup logic with conditions
StartupLogic startup = new StartupLogic("Dynamic Startup");
startup.addPermissive(new PressureCondition(separator, 5.0, "<"));
startup.addPermissive(new ValvePositionCondition(valve, "<", 5.0));
```

### 3. **Dynamic Action/Condition Creation**
Create custom actions using anonymous classes or lambda expressions:

```java
// Custom action with anonymous class
LogicAction customAction = new LogicAction() {
    private boolean executed = false;
    
    @Override
    public void execute() {
        if (!executed) {
            valve.setPercentValveOpening(75.0);
            executed = true;
        }
    }
    
    @Override
    public String getDescription() {
        return "Custom throttle to 75%";
    }
    
    @Override
    public boolean isComplete() {
        return executed && Math.abs(valve.getPercentValveOpening() - 75.0) < 1.0;
    }
    
    @Override
    public String getTargetName() {
        return valve.getName();
    }
};
```

### 4. **Configuration-Based Logic**
Load logic from external configuration files:

```java
// Configuration format: ACTION_TYPE:EQUIPMENT:PARAMETER:DELAY
String[] esdConfig = {
    "VALVE_CLOSE:Control Valve:0:0.0",
    "VALVE_SET:Backup Valve:25.0:0.5", 
    "SEPARATOR_MODE:Test Separator:transient:1.0"
};

ESDLogic configuredESD = factory.createESDFromConfig("Configured ESD", esdConfig);
```

### 5. **Runtime Logic Modification**
Modify logic sequences during execution:

```java
ESDLogic modifiableLogic = new ESDLogic("Modifiable Logic");
modifiableLogic.addAction(initialAction, 0.0);

// Later, based on runtime conditions:
if (emergencyCondition()) {
    modifiableLogic.addAction(emergencyAction, 2.0);
}
```

## Demonstration Examples

### 1. **DynamicLogicExample.java**
Shows how to create logic entirely at runtime:
- Custom actions with anonymous classes
- Custom conditions with time-based logic  
- Dynamic logic sequences based on scenarios
- Runtime modification of existing logic

### 2. **ConfigurableLogicExample.java**
Demonstrates loading logic from configurations:
- String-based configuration parsing
- Configuration file parsing (simulated)
- Logic factory pattern for creating actions/conditions
- User input-driven logic creation

## Implementation Patterns

### Factory Pattern for Actions
```java
private LogicAction createActionFromConfig(String config) {
    String[] parts = config.split(":");
    String actionType = parts[0];
    String equipmentName = parts[1];
    String parameter = parts[2];
    
    switch (actionType) {
        case "VALVE_CLOSE":
            return createValveCloseAction((ThrottlingValve) equipment.get(equipmentName));
        case "VALVE_SET":
            return createValveSetAction((ThrottlingValve) equipment.get(equipmentName), 
                                      Double.parseDouble(parameter));
        // ... more action types
    }
}
```

### Adaptive Logic Creation
```java
String scenario = determineRuntimeScenario(); // Based on process conditions
ESDLogic adaptiveLogic = createAdaptiveLogic(scenario, valve, separator);

switch (scenario) {
    case "High Pressure Response":
        adaptiveLogic.addAction(createAction("Close valve rapidly", valve, 5.0), 0.0);
        break;
    case "Fire Emergency":
        adaptiveLogic.addAction(createAction("Emergency closure", valve, 0.0), 0.0);
        break;
}
```

## Benefits of Runtime Flexibility

### 1. **No Recompilation Required**
- Logic changes can be deployed without rebuilding the application
- Configuration files can be updated in production
- Hot-swapping of logic sequences during maintenance

### 2. **Dynamic Adaptation**
- Logic can adapt to current process conditions
- Different logic for different operating modes
- Scenario-based logic selection

### 3. **Easy Integration**
- External systems can define logic via APIs
- Configuration management systems can update logic
- Machine learning systems can generate optimized logic

### 4. **Rapid Development**
- Test new logic sequences without compilation cycles
- Prototype complex control strategies quickly
- Debug logic sequences with runtime inspection

## Architecture Benefits

The framework's interface-based design provides:

- **Extensibility**: New action/condition types can be added without modifying existing code
- **Testability**: Logic sequences can be unit tested with mock equipment
- **Maintainability**: Logic is separated from equipment implementation
- **Reusability**: Common actions/conditions can be shared across different logic types

## Conclusion

The NeqSim process logic framework excels at runtime flexibility. You can:

1. ✅ Create entirely new logic sequences at runtime
2. ✅ Load logic from configuration files or external sources  
3. ✅ Modify existing logic sequences during execution
4. ✅ Create custom actions and conditions dynamically
5. ✅ Adapt logic based on runtime conditions
6. ✅ Deploy logic changes without recompilation

This makes it ideal for:
- Dynamic process control systems
- Configuration-driven safety systems
- Adaptive automation platforms
- Rapid prototyping of control strategies
- Integration with external control systems

The examples demonstrate that complex process logic can be created, modified, and executed entirely at runtime with no pre-compilation requirements.