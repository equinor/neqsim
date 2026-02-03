# Pull Request: AI-Friendly Validation Framework

## Summary

This PR introduces a comprehensive validation and AI integration framework for NeqSim, designed to make the library more accessible to AI/ML agents and provide structured error handling with actionable remediation hints.

## Motivation

When AI agents (like GitHub Copilot, ChatGPT, or custom ML pipelines) work with NeqSim:
- They need **structured validation** to understand what went wrong
- They need **remediation hints** to fix errors automatically
- They need **API discovery** to understand available methods
- They need **safe execution wrappers** that don't crash on invalid input

This framework addresses all these needs.

## Changes

### New Packages

#### `neqsim.util.validation`

| File | Description |
|------|-------------|
| `ValidationResult.java` | Structured container for validation issues with severity levels |
| `SimulationValidator.java` | Static facade for validating any NeqSim object |
| `AIIntegrationHelper.java` | Unified entry point combining validation, RL, and documentation |

#### `neqsim.util.validation.contracts`

| File | Description |
|------|-------------|
| `ModuleContract.java` | Base interface for pre/post-condition checking |
| `ThermodynamicSystemContract.java` | Validates `SystemInterface` objects |
| `StreamContract.java` | Validates `StreamInterface` objects |
| `SeparatorContract.java` | Validates `Separator` equipment |
| `ProcessSystemContract.java` | Validates `ProcessSystem` flowsheets |

#### `neqsim.util.annotation`

| File | Description |
|------|-------------|
| `AIExposable.java` | Annotation marking methods for AI discovery |
| `AIParameter.java` | Annotation documenting parameter constraints |
| `AISchemaDiscovery.java` | Reflection service for finding annotated methods |

### Modified Files

#### Interfaces with `validateSetup()` method
- `SystemInterface.java` - Added default `validateSetup()` method
- `ProcessEquipmentInterface.java` - Added default `validateSetup()` method

#### Exceptions with `getRemediation()` method
- `InvalidInputException.java` - Returns valid input suggestions
- `TooManyIterationsException.java` - Returns convergence fix hints
- `IsNaNException.java` - Returns the problematic parameter name
- `InvalidOutputException.java` - Returns expected output description
- `NotInitializedException.java` - Returns required initialization steps

### Test Coverage

| Test Class | Tests | Status |
|------------|-------|--------|
| `ValidationResultTest` | 13 | ✅ Pass |
| `SimulationValidatorTest` | 16 | ✅ Pass |
| `ModuleContractTest` | 14 | ✅ Pass |
| `AISchemaDiscoveryTest` | 13 | ✅ Pass |
| `AIIntegrationHelperTest` | 15 | ✅ Pass |

**Total: 71 new tests**

---

## Usage Recommendations

### For End Users

#### Basic Validation Before Running

```java
import neqsim.util.validation.SimulationValidator;
import neqsim.util.validation.ValidationResult;

// Create your fluid
SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
fluid.addComponent("methane", 0.9);
fluid.addComponent("ethane", 0.1);
fluid.setMixingRule("classic");

// Validate before flash calculation
ValidationResult result = SimulationValidator.validate(fluid);
if (!result.isValid()) {
    System.out.println("Cannot proceed:");
    System.out.println(result.getReport());
    return;
}

// Safe to run
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
```

#### Process System Validation

```java
import neqsim.util.validation.AIIntegrationHelper;

ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(separator);
process.add(compressor);

AIIntegrationHelper helper = AIIntegrationHelper.forProcess(process);

if (helper.isReady()) {
    // Safe execution with structured result
    AIIntegrationHelper.ExecutionResult result = helper.safeRun();
    
    if (result.isSuccess()) {
        System.out.println("Process completed successfully");
    } else {
        System.out.println(result.toAIReport());
    }
} else {
    // Get issues for debugging
    System.out.println(helper.getValidationReport());
}
```

### For AI/ML Integration

#### With Reinforcement Learning

```java
ProcessSystem process = buildYourProcess();
AIIntegrationHelper helper = AIIntegrationHelper.forProcess(process);

// Validate before creating RL environment
if (helper.isReady()) {
    RLEnvironment env = helper.createRLEnvironment();
    
    // Standard RL loop
    StateVector obs = env.reset();
    while (!done) {
        ActionVector action = agent.selectAction(obs);
        RLEnvironment.StepResult result = env.step(action);
        obs = result.observation;
        done = result.done;
    }
} else {
    // Fix issues before training
    throw new IllegalStateException(helper.getValidationReport());
}
```

#### Structured Error Handling for AI Agents

```java
// When an AI agent gets a validation failure
String[] issues = helper.getIssuesAsText();
// Returns: ["[MAJOR] No components defined - Add components using addComponent(name, moles)"]

// Each issue has:
// - Severity level for prioritization
// - Clear error message
// - Actionable remediation hint
```

#### API Discovery for Prompts

```java
AISchemaDiscovery discovery = new AISchemaDiscovery();

// Get quick-start documentation for AI agent
String prompt = discovery.getQuickStartPrompt();

// Discover methods in specific classes
List<MethodSchema> methods = discovery.discoverMethods(MyAnnotatedClass.class);
String methodDocs = discovery.generateMethodPrompt(methods);
```

### For Library Developers

#### Adding Validation to New Equipment

```java
public class MyNewEquipment extends ProcessEquipmentBaseClass {
    
    @Override
    public ValidationResult validateSetup() {
        ValidationResult result = new ValidationResult(getName());
        
        // Check preconditions
        if (getInletStream() == null) {
            result.addError("inlet", "No inlet stream connected",
                "Connect an inlet stream using setInletStream()");
        }
        
        if (someParameter < 0) {
            result.addWarning("parameter", "Negative value may cause issues",
                "Consider using positive values");
        }
        
        return result;
    }
}
```

#### Creating Custom Contracts

```java
public class MyEquipmentContract implements ModuleContract<MyEquipment> {
    
    private static final MyEquipmentContract INSTANCE = new MyEquipmentContract();
    
    public static MyEquipmentContract getInstance() {
        return INSTANCE;
    }
    
    @Override
    public ValidationResult checkPreconditions(MyEquipment equipment) {
        ValidationResult result = new ValidationResult(equipment.getName());
        // Add your validation logic
        return result;
    }
    
    @Override
    public ValidationResult checkPostconditions(MyEquipment equipment) {
        ValidationResult result = new ValidationResult(equipment.getName());
        // Check outputs are valid
        return result;
    }
}
```

#### Annotating Methods for AI Discovery

```java
@AIExposable(
    description = "Calculate bubble point pressure at given temperature",
    category = "phase-equilibrium",
    example = "ops.bubblePointPressureFlash(false)",
    priority = 90,
    safe = true,
    tags = {"thermodynamics", "flash", "bubble-point"}
)
public void bubblePointPressureFlash(
    @AIParameter(
        name = "bubblePointPressure", 
        description = "Whether to use bubble point pressure",
        defaultValue = "false"
    ) boolean bubblePointPressure
) {
    // implementation
}
```

---

## Integration with Existing NeqSim Infrastructure

This framework is designed to work with NeqSim's existing ML capabilities:

| Existing Component | Integration Point |
|-------------------|-------------------|
| `RLEnvironment` | Created via `AIIntegrationHelper.createRLEnvironment()` |
| `GymEnvironment` | Uses same `StateVector`/`ActionVector` |
| `ProcessLinkedMPC` | Validate via `SimulationValidator.validate(process)` |
| `ProductionOptimizer` | Validate inputs before optimization |
| `SurrogateModelRegistry` | Physics constraint checking |

---

## Breaking Changes

None. All changes are additive:
- New packages and classes
- Default methods on interfaces (backward compatible)
- New optional methods on exceptions

---

## Testing

Run the validation framework tests:

```bash
./mvnw test -Dtest="ValidationResultTest,SimulationValidatorTest,ModuleContractTest,AISchemaDiscoveryTest,AIIntegrationHelperTest"
```

Run all tests:

```bash
./mvnw test
```

---

## Future Work

1. **Apply `@AIExposable` annotations** to core NeqSim methods
2. **Add MPC validation contracts** for `ProcessLinkedMPC`
3. **Integrate with neqsim-python** for Python AI frameworks
4. **Real-time validation** during transient simulations
5. **Custom validation rules** via pluggable validators

---

## Related Documentation

- [AI Validation Framework Reference](ai_validation_framework.md)
- [NeqSim ML Integration](ml_integration.md)
- [MPC Integration Guide](mpc_integration.md)
