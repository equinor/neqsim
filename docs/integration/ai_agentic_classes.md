---
title: Agentic Java Classes
description: "Java infrastructure for AI-driven simulation QA: TaskResultValidator for results.json validation, SimulationQualityGate for automated process checks, AgentSession for context tracking, and AgentFeedbackCollector for structured feedback."
---

# Agentic Java Classes

NeqSim includes Java classes that support AI agent workflows — validating simulation results, checking process quality, and tracking agent sessions. These are used by the task-solving workflow and can be called from Python via jpype.

## Classes

| Class | Package | Purpose |
|-------|---------|---------|
| `TaskResultValidator` | `neqsim.util.agentic` | Validates `results.json` files produced by task-solving notebooks |
| `SimulationQualityGate` | `neqsim.util.agentic` | Automated QA checks on a completed `ProcessSystem` |
| `AgentSession` | `neqsim.util.agentic` | Tracks agent identity, timestamps, and task context |
| `AgentFeedbackCollector` | `neqsim.util.agentic` | Collects structured feedback (ratings, text, tags) |

---

## SimulationQualityGate

Validates a completed `ProcessSystem` against physical and engineering constraints:

- **Physical bounds** — temperatures > 0 K, pressures > 0 bara, reasonable ranges
- **Stream consistency** — no NaN or infinite values in flow rates, enthalpies
- **Composition normalization** — mole fractions sum to 1.0 in every phase
- **Configurable tolerances** — mass and energy balance closure thresholds

### Java Usage

```java
import neqsim.util.agentic.SimulationQualityGate;

ProcessSystem process = new ProcessSystem();
// ... build and run process ...
process.run();

SimulationQualityGate gate = new SimulationQualityGate(process);
gate.setMassBalanceTolerance(0.01);   // 1% (default)
gate.setEnergyBalanceTolerance(0.05); // 5% (default)
gate.validate();

boolean passed = gate.isPassed();
int errors = gate.getErrorCount();
int warnings = gate.getWarningCount();
String report = gate.toJson();

// Iterate issues
for (SimulationQualityGate.QualityIssue issue : gate.getIssues()) {
    System.out.println(issue.severity + ": " + issue.message);
    System.out.println("  Fix: " + issue.remediation);
}
```

### Python Usage

```python
import jpype
SimulationQualityGate = jpype.JClass("neqsim.util.agentic.SimulationQualityGate")

gate = SimulationQualityGate(process)
gate.validate()

if not gate.isPassed():
    print(f"FAILED: {gate.getErrorCount()} errors, {gate.getWarningCount()} warnings")
    print(gate.toJson())
else:
    print("All quality checks passed")
```

### JSON Output

The `toJson()` method returns:

```json
{
  "passed": true,
  "errorCount": 0,
  "warningCount": 1,
  "massBalanceTolerance": 0.01,
  "energyBalanceTolerance": 0.05,
  "issues": [
    {
      "severity": "WARNING",
      "category": "physical_bounds",
      "message": "Stream 'LP Gas' has very low temperature: -45.2 C",
      "remediation": "Verify this is physically reasonable for the process"
    }
  ]
}
```

### Issue Categories

| Category | Checks |
|----------|--------|
| `physical_bounds` | T > 0 K, P > 0, reasonable temperature/pressure ranges |
| `stream_consistency` | No NaN/Inf in flow rates, enthalpies; no negative flows |
| `composition` | Mole fractions sum to 1.0; no negative or NaN fractions |

### Severity Levels

| Severity | Meaning |
|----------|---------|
| `ERROR` | Critical — causes `isPassed()` to return false |
| `WARNING` | Should be reviewed but does not fail the gate |
| `INFO` | Informational note |

---

## TaskResultValidator

Validates `results.json` files produced by the task-solving workflow notebooks. Checks for required fields, data types, value ranges, and structural consistency.

### Java Usage

```java
import neqsim.util.agentic.TaskResultValidator;

String json = new String(Files.readAllBytes(Paths.get("results.json")));
TaskResultValidator.ValidationReport report = TaskResultValidator.validate(json);

System.out.println("Valid: " + report.isValid());
System.out.println("Errors: " + report.getErrorCount());
System.out.println("Warnings: " + report.getWarningCount());

for (TaskResultValidator.ValidationMessage msg : report.getErrors()) {
    System.out.println("[" + msg.field + "] " + msg.message);
}
```

### Python Usage (in notebooks)

```python
import jpype
TaskResultValidator = jpype.JClass("neqsim.util.agentic.TaskResultValidator")

with open("results.json", "r") as f:
    json_str = f.read()

report = TaskResultValidator.validate(json_str)
print(f"Valid: {report.isValid()}  Errors: {report.getErrorCount()}")

if not report.isValid():
    for err in report.getErrors():
        print(f"  [{err.field}] {err.message}")
```

---

## AgentSession

Lightweight context tracker for AI agent sessions — records the agent name, task ID, timestamps, and step context.

```java
import neqsim.util.agentic.AgentSession;

AgentSession session = new AgentSession("solve.task", "my-task-id");
session.setCurrentStep("step2_analysis");  // Track workflow stage
String json = session.toJson();
```

---

## AgentFeedbackCollector

Collects structured feedback for rating agent outputs:

```java
import neqsim.util.agentic.AgentFeedbackCollector;

AgentFeedbackCollector feedback = new AgentFeedbackCollector();
feedback.setRating(4);                   // 1-5
feedback.setComment("Good convergence"); // Free text
feedback.addTag("process-simulation");   // Searchable tags
String json = feedback.toJson();
```

---

## Integration with Task-Solving Workflow

These classes are designed to be called between Step 2 (analysis) and Step 3 (reporting):

1. **After `process.run()`** — call `SimulationQualityGate.validate()` to check process results
2. **After saving `results.json`** — call `TaskResultValidator.validate()` to check the output file
3. Both checks must pass before proceeding to report generation

The workflow pattern is documented in [AGENTS.md](../../../AGENTS.md) under "Save results.json".

## Related Documentation

- [AI Validation Framework](../integration/ai_validation_framework.md)
- [AI Agentic Programming Intro](../integration/ai_agentic_programming_intro.md)
- [AI Workflow Examples](../integration/ai_workflow_examples.md)
