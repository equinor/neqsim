# NeqSim AI-Friendly Validation Framework

## Overview

This document describes the AI-friendly validation and integration framework added to NeqSim. The framework provides structured validation, error remediation hints, and API discovery for AI/ML agents working with NeqSim thermodynamic simulations.

## Architecture

```
neqsim.util.validation/
├── ValidationResult.java          # Structured validation container
├── SimulationValidator.java       # Static validation facade
├── AIIntegrationHelper.java       # Unified AI entry point
└── contracts/
    ├── ModuleContract.java        # Base contract interface
    ├── ThermodynamicSystemContract.java
    ├── StreamContract.java
    ├── SeparatorContract.java
    └── ProcessSystemContract.java

neqsim.util.annotation/
├── AIExposable.java               # Method discovery annotation
├── AIParameter.java               # Parameter documentation annotation
└── AISchemaDiscovery.java         # Reflection-based API discovery
```

## Key Components

### 1. ValidationResult

A structured container for validation issues with severity levels:

```java
ValidationResult result = SimulationValidator.validate(fluid);
if (!result.isValid()) {
    System.out.println(result.getReport()); // Human-readable
    for (ValidationIssue issue : result.getIssues()) {
        String fix = issue.getRemediation(); // AI-parseable hint
    }
}
```

**Severity Levels:**
- `CRITICAL` - Blocks execution
- `MAJOR` - Likely to cause errors
- `MINOR` - May affect results
- `INFO` - Informational only

### 2. SimulationValidator

Static facade for validating any NeqSim object:

```java
// Validate any object
ValidationResult result = SimulationValidator.validate(object);

// Validate outputs after execution
ValidationResult postRun = SimulationValidator.validateOutput(process);

// Combined validate-and-run
ValidationResult combined = SimulationValidator.validateAndRun(stream);
```

### 3. Module Contracts

Pre/post-condition checking for specific NeqSim types:

```java
ThermodynamicSystemContract contract = ThermodynamicSystemContract.getInstance();
ValidationResult pre = contract.checkPreconditions(system);
ValidationResult post = contract.checkPostconditions(system);
```

**Available Contracts:**
- `ThermodynamicSystemContract` - Validates SystemInterface
- `StreamContract` - Validates StreamInterface
- `SeparatorContract` - Validates Separator equipment
- `ProcessSystemContract` - Validates ProcessSystem

### 4. AIIntegrationHelper

Unified entry point connecting validation with RL/ML infrastructure:

```java
AIIntegrationHelper helper = AIIntegrationHelper.forProcess(process);

// Check readiness
if (helper.isReady()) {
    ExecutionResult result = helper.safeRun();
    System.out.println(result.toAIReport());
}

// Get API documentation for agent
String docs = helper.getAPIDocumentation();

// Create RL environment
RLEnvironment env = helper.createRLEnvironment();
```

### 5. AI Annotations

Annotations for exposing methods to AI agents:

```java
@AIExposable(
    description = "Add a chemical component to the system",
    category = "composition",
    example = "addComponent(\"methane\", 0.9)",
    priority = 100,
    safe = false
)
public void addComponent(
    @AIParameter(name = "name", description = "Component name") String name,
    @AIParameter(name = "moles", minValue = 0.0, maxValue = 1.0) double moles
) { ... }
```

### 6. AISchemaDiscovery

Discovers annotated methods via reflection:

```java
AISchemaDiscovery discovery = new AISchemaDiscovery();

// Discover methods in a class
List<MethodSchema> methods = discovery.discoverMethods(SystemSrkEos.class);

// Generate prompt for AI
String prompt = discovery.generateMethodPrompt(methods);

// Get quick-start documentation
String quickStart = discovery.getQuickStartPrompt();
```

## Integration with Existing NeqSim ML Infrastructure

The framework integrates with NeqSim's existing ML capabilities:

| Component | Package | Integration |
|-----------|---------|-------------|
| RLEnvironment | `neqsim.process.ml` | Create from AIIntegrationHelper |
| GymEnvironment | `neqsim.process.ml` | Compatible state/action vectors |
| ProcessLinkedMPC | `neqsim.process.mpc` | Validate MPC process systems |
| ProductionOptimizer | `neqsim.process.util.optimization` | Validate optimizer inputs |
| SurrogateModelRegistry | `neqsim.process.ml.surrogate` | Physics constraint checking |

## Exception Remediation

Enhanced exceptions with remediation hints:

```java
try {
    process.run();
} catch (InvalidInputException e) {
    String fix = e.getRemediation(); // "Provide valid values: ..."
} catch (TooManyIterationsException e) {
    String fix = e.getRemediation(); // "Increase max iterations or adjust initial estimate"
    int tried = e.getMaxIterations();
}
```

**Enhanced Exceptions:**
- `InvalidInputException` - Lists valid options
- `TooManyIterationsException` - Suggests convergence fixes
- `IsNaNException` - Identifies the problematic parameter
- `InvalidOutputException` - Describes expected output type
- `NotInitializedException` - Lists required initialization steps

## Usage Examples

### Basic Validation

```java
// Create fluid
SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
fluid.addComponent("methane", 0.9);
fluid.addComponent("ethane", 0.1);
fluid.setMixingRule("classic");

// Validate before flash
ValidationResult result = SimulationValidator.validate(fluid);
if (!result.isValid()) {
    System.out.println("Issues found:");
    System.out.println(result.getReport());
}
```

### Process System Validation

```java
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(separator);
process.add(compressor);

AIIntegrationHelper helper = AIIntegrationHelper.forProcess(process);
if (helper.isReady()) {
    ExecutionResult result = helper.safeRun();
    if (result.isSuccess()) {
        // Process ran successfully
    }
} else {
    // Get structured issues for AI to fix
    String[] issues = helper.getIssuesAsText();
    for (String issue : issues) {
        System.out.println(issue);
    }
}
```

### RL Integration

```java
ProcessSystem process = buildProcess();
AIIntegrationHelper helper = AIIntegrationHelper.forProcess(process);

// Validate before creating RL environment
if (helper.isReady()) {
    RLEnvironment env = helper.createRLEnvironment();
    
    // RL training loop
    StateVector obs = env.reset();
    while (!done) {
        ActionVector action = agent.selectAction(obs);
        RLEnvironment.StepResult result = env.step(action);
        obs = result.observation;
        done = result.done;
    }
}
```

## Test Coverage

All components have comprehensive unit tests:

| Test Class | Tests | Coverage |
|------------|-------|----------|
| ValidationResultTest | 13 | Core validation logic |
| SimulationValidatorTest | 16 | Static facade methods |
| ModuleContractTest | 14 | Contract implementations |
| AISchemaDiscoveryTest | 13 | Annotation discovery |
| AIIntegrationHelperTest | 15 | Integration helper |

Run tests:
```bash
./mvnw test -Dtest="ValidationResultTest,SimulationValidatorTest,ModuleContractTest,AISchemaDiscoveryTest,AIIntegrationHelperTest"
```

## Future Enhancements

1. **Apply @AIExposable annotations** to core NeqSim methods (addComponent, setMixingRule, TPflash, etc.)
2. **MPC validation contracts** for ProcessLinkedMPC
3. **Surrogate model validation** integration with SurrogateModelRegistry
4. **Real-time validation** during simulation stepping
5. **Custom validation rules** via pluggable validators
