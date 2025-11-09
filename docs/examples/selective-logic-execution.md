# Selective Logic Execution in ProcessScenarioRunner

The `ProcessScenarioRunner` provides multiple ways to control which logic sequences execute during a scenario.

## Method 1: Add Only What You Need (Simplest)

Only add the logic sequences you want to run:

```java
ProcessScenarioRunner runner = new ProcessScenarioRunner(processSystem);

// Only add ESD logic - HIPPS and startup won't run
runner.addLogic(esdLogic);

// This scenario will only execute ESD logic
runner.runScenario("ESD Test", scenario, 30.0, 1.0);
```

## Method 2: Add/Remove Logic Dynamically

Add all logic initially, then remove what you don't need:

```java
ProcessScenarioRunner runner = new ProcessScenarioRunner(processSystem);

// Add all logic
runner.addLogic(hippsLogic);
runner.addLogic(esdLogic);
runner.addLogic(startupLogic);

// For this scenario, remove HIPPS
runner.removeLogic("HIPPS Protection");

// This scenario will run ESD and startup, but not HIPPS
runner.runScenario("Test Without HIPPS", scenario, 30.0, 1.0);

// Re-add HIPPS for next scenario
runner.addLogic(hippsLogic);
```

## Method 3: Run Scenario With Specific Logic (Most Flexible)

Register all logic once, then specify which to use per scenario:

```java
ProcessScenarioRunner runner = new ProcessScenarioRunner(processSystem);

// Register all logic sequences once
runner.addLogic(hippsLogic);      // "HIPPS Protection"
runner.addLogic(esdLogic);        // "ESD Level 1"
runner.addLogic(startupLogic);    // "System Startup"

// Scenario 1: Test only HIPPS
runner.runScenarioWithLogic("HIPPS Test", scenario1, 30.0, 1.0, 
    List.of("HIPPS Protection"));

// Scenario 2: Test ESD without HIPPS
runner.runScenarioWithLogic("ESD Test", scenario2, 30.0, 1.0, 
    List.of("ESD Level 1", "System Startup"));

// Scenario 3: Run all logic (pass null or empty list)
runner.runScenarioWithLogic("Full Test", scenario3, 30.0, 1.0, null);

// Or use the standard method (runs all registered logic)
runner.runScenario("Full Test Alternative", scenario3, 30.0, 1.0);
```

## Method 4: Clear and Re-add Between Scenarios

```java
ProcessScenarioRunner runner = new ProcessScenarioRunner(processSystem);

// Scenario 1: Only startup
runner.addLogic(startupLogic);
runner.runScenario("Startup Only", scenario1, 30.0, 1.0);
runner.reset();

// Scenario 2: Only ESD
runner.clearAllLogic();
runner.addLogic(esdLogic);
runner.runScenario("ESD Only", scenario2, 30.0, 1.0);
runner.reset();

// Scenario 3: All logic
runner.clearAllLogic();
runner.addLogic(hippsLogic);
runner.addLogic(esdLogic);
runner.addLogic(startupLogic);
runner.runScenario("All Logic", scenario3, 30.0, 1.0);
```

## Quick Reference

| Method | Use Case |
|--------|----------|
| `addLogic(logic)` | Add a logic sequence to the runner |
| `removeLogic(logic)` | Remove a specific logic object |
| `removeLogic("name")` | Remove logic by name |
| `clearAllLogic()` | Remove all registered logic |
| `runScenario(...)` | Run with all registered logic |
| `runScenarioWithLogic(..., List.of("Logic1", "Logic2"))` | Run with specific logic by name |
| `findLogic("name")` | Find a logic sequence by name |
| `activateLogic("name")` | Activate a logic sequence by name |

## Best Practices

1. **Testing individual systems**: Use `runScenarioWithLogic()` to test each safety system independently
2. **Performance**: If a scenario doesn't need certain logic, excluding it reduces computation
3. **Safety validation**: Test HIPPS and ESD independently, then together to verify independence
4. **Reset between scenarios**: Always call `runner.reset()` between scenarios to clear logic states
