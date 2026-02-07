---
title: "Data Reconciliation and Steady-State Detection"
description: "Weighted least squares data reconciliation for process measurements with steady-state detection. Adjusts plant measurements to satisfy mass and energy balance constraints, detects gross errors, and supports iterative sensor fault identification. Includes R-statistic based steady-state detection for online monitoring."
---

# Data Reconciliation and Steady-State Detection

The `neqsim.process.util.reconciliation` package provides:

1. A **weighted least squares (WLS) data reconciliation engine** that adjusts plant measurements so that mass (and optionally energy) balance constraints are exactly satisfied.
2. A **steady-state detector (SSD)** based on the R-statistic method that monitors process variables and determines when the plant has reached steady state — a prerequisite for meaningful reconciliation.

## Contents

- [Steady-State Detection](#steady-state-detection)
  - [Why Detect Steady State?](#why-detect-steady-state)
  - [The R-Statistic Method](#the-r-statistic-method)
  - [SSD Step-by-Step Usage](#ssd-step-by-step-usage)
  - [SSD Java Example](#ssd-java-example)
  - [SSD Python Example](#ssd-python-example)
  - [SSD API Reference](#ssd-api-reference)
  - [Tuning the Detector](#tuning-the-detector)
  - [Bridging to Data Reconciliation](#bridging-to-data-reconciliation)
- [Data Reconciliation](#data-reconciliation)
- [Overview](#overview)
- [When to Use Data Reconciliation](#when-to-use-data-reconciliation)
- [Mathematical Background](#mathematical-background)
- [Architecture](#architecture)
- [Step-by-Step Usage](#step-by-step-usage)
  - [Step 1 — Define Measurements](#step-1--define-measurements)
  - [Step 2 — Set Measurement Uncertainties](#step-2--set-measurement-uncertainties)
  - [Step 3 — Define Balance Constraints](#step-3--define-balance-constraints)
  - [Step 4 — Run Reconciliation](#step-4--run-reconciliation)
  - [Step 5 — Read Reconciled Values](#step-5--read-reconciled-values)
  - [Step 6 — Detect Gross Errors](#step-6--detect-gross-errors)
- [Working with Model (Tuned) Variables](#working-with-model-tuned-variables)
- [Complete Java Example](#complete-java-example)
- [Complete Python Example](#complete-python-example)
- [API Reference](#api-reference)
- [Uncertainty Guidelines](#uncertainty-guidelines)
- [Multi-Node Network Example](#multi-node-network-example)
- [Gross Error Elimination](#gross-error-elimination)
- [Integration with ProcessSystem](#integration-with-processsystem)
- [JSON and Text Reports](#json-and-text-reports)
- [Troubleshooting](#troubleshooting)
- [Related Documentation](#related-documentation)

---

## Steady-State Detection

### Why Detect Steady State?

Data reconciliation assumes that the measured values represent a single operating point governed by conservation laws. If the plant is actively transitioning (e.g., a rate change, startup, or upset), reconciling transient data produces meaningless results. Steady-state detection (SSD) answers the question: **Are the current readings stable enough to reconcile?**

### The R-Statistic Method

The detector uses the **Cao-Rhinehart R-statistic** (Cao & Rhinehart, 1995) — a ratio of the *filtered variance* to the *unfiltered variance*:

$$
R = \frac{\sigma^2_f}{\sigma^2_u}
$$

Where:

- **Filtered variance** $\sigma^2_f$ — computed from successive differences: $\frac{1}{2(n-1)} \sum_{i=2}^{n} (x_i - x_{i-1})^2$. This captures sample-to-sample noise.
- **Unfiltered variance** $\sigma^2_u$ — the ordinary sample variance: $\frac{1}{n-1} \sum_{i=1}^{n} (x_i - \bar{x})^2$. This captures both noise and any trend.

**Interpretation:**

| R value | Meaning |
|---------|---------|
| R close to 1.0 | White noise only — steady state |
| R much less than 1.0 | Trend, drift, or step change — transient |
| R greater than 1.0 | Oscillation or alternating pattern |

The default threshold is **R &ge; 0.5**. Optional supplementary tests:

| Test | Purpose | Default |
|------|---------|---------|
| **Slope test** | Catches slow monotonic drift that R might miss | Disabled (threshold = 0) |
| **Std.dev test** | Rejects signals that are technically "steady" but too noisy | Disabled (threshold = 0) |

### SSD Step-by-Step Usage

#### Step 1 — Create the detector

```java
// Window of 30 samples, R-threshold 0.5
SteadyStateDetector detector = new SteadyStateDetector(30);
detector.setRThreshold(0.5);
```

#### Step 2 — Register variables

```java
// By name (uses default window size)
detector.addVariable("FI-1001");
detector.addVariable("TI-2001");

// Or with explicit window and uncertainty
SteadyStateVariable v = new SteadyStateVariable("FI-1001", 30);
v.setUnit("kg/hr");
v.setUncertainty(20.0); // needed if bridging to reconciliation
detector.addVariable(v);
```

#### Step 3 — Feed data (streaming loop)

```java
// In your scan loop (e.g., every 10 seconds):
detector.updateVariable("FI-1001", readTag("FI-1001"));
detector.updateVariable("TI-2001", readTag("TI-2001"));
```

Or update all at once:

```java
Map<String, Double> snapshot = new LinkedHashMap<String, Double>();
snapshot.put("FI-1001", readTag("FI-1001"));
snapshot.put("TI-2001", readTag("TI-2001"));
detector.updateAll(snapshot);
```

#### Step 4 — Evaluate

```java
SteadyStateResult result = detector.evaluate();

if (result.isAtSteadyState()) {
    System.out.println("Plant is at steady state — safe to reconcile");
} else {
    System.out.println("Transient variables: " + result.getTransientVariables());
}
```

Or combine update + evaluate:

```java
SteadyStateResult result = detector.updateAndEvaluate(snapshot);
```

#### Step 5 — Read results

```java
// Per-variable diagnostics
for (SteadyStateVariable v : result.getVariables()) {
    System.out.printf("%-12s  R=%.3f  mean=%.1f  steady=%s%n",
        v.getName(), v.getRStatistic(), v.getMean(), v.isAtSteadyState());
}

// Reports
System.out.println(result.toReport()); // formatted table
String json = result.toJson();          // machine-readable
```

### SSD Java Example

```java
import neqsim.process.util.reconciliation.*;

// Create detector
SteadyStateDetector ssd = new SteadyStateDetector(30);
ssd.setRThreshold(0.5);
ssd.setSlopeThreshold(0.5);   // Optional: catch slow drifts

// Register variables with uncertainties
SteadyStateVariable feed = new SteadyStateVariable("feed_flow", 30);
feed.setUnit("kg/hr").setUncertainty(20.0);
ssd.addVariable(feed);

SteadyStateVariable gas = new SteadyStateVariable("gas_flow", 30);
gas.setUnit("kg/hr").setUncertainty(15.0);
ssd.addVariable(gas);

SteadyStateVariable liquid = new SteadyStateVariable("liquid_flow", 30);
liquid.setUnit("kg/hr").setUncertainty(10.0);
ssd.addVariable(liquid);

// Simulate 30 readings at steady state
for (int i = 0; i < 30; i++) {
    ssd.updateVariable("feed_flow", 1000.0 + (Math.random() - 0.5) * 2);
    ssd.updateVariable("gas_flow", 605.0 + (Math.random() - 0.5) * 2);
    ssd.updateVariable("liquid_flow", 398.0 + (Math.random() - 0.5) * 2);
}

SteadyStateResult ssResult = ssd.evaluate();
System.out.println(ssResult.toReport());

if (ssResult.isAtSteadyState()) {
    // Bridge directly to reconciliation
    DataReconciliationEngine engine = ssd.createReconciliationEngine();
    engine.addMassBalanceConstraint("separator",
        new String[]{"feed_flow"},
        new String[]{"gas_flow", "liquid_flow"});
    ReconciliationResult recResult = engine.reconcile();
    System.out.println(recResult.toReport());
}
```

### SSD Python Example

```python
from neqsim import jneqsim

SteadyStateDetector = jneqsim.process.util.reconciliation.SteadyStateDetector
SteadyStateVariable = jneqsim.process.util.reconciliation.SteadyStateVariable

# Create detector with window=30
ssd = SteadyStateDetector(30)
ssd.setRThreshold(0.5)

# Register variables
feed = SteadyStateVariable("feed_flow", 30)
feed.setUnit("kg/hr").setUncertainty(20.0)
ssd.addVariable(feed)

gas = SteadyStateVariable("gas_flow", 30)
gas.setUnit("kg/hr").setUncertainty(15.0)
ssd.addVariable(gas)

# Push 30 constant-ish readings
import random
for i in range(30):
    ssd.updateVariable("feed_flow", 1000.0 + random.uniform(-1, 1))
    ssd.updateVariable("gas_flow", 600.0 + random.uniform(-1, 1))

result = ssd.evaluate()
print(result.toReport())

if result.isAtSteadyState():
    engine = ssd.createReconciliationEngine()
    # ... add constraints and reconcile
```

### SSD API Reference

#### SteadyStateDetector

| Method | Description |
|--------|-------------|
| `SteadyStateDetector(int windowSize)` | Create detector with given default window size |
| `addVariable(SteadyStateVariable v)` | Register a pre-configured variable |
| `addVariable(String name)` | Register by name using default window; returns created variable |
| `removeVariable(String name)` | Unregister a variable; returns true if found |
| `getVariable(String name)` | Get variable by name (null if not found) |
| `getVariableCount()` | Number of registered variables |
| `updateVariable(String name, double value)` | Push a new sample for one variable |
| `updateAll(Map name, Double value)` | Push new samples for all variables |
| `evaluate()` | Evaluate all variables; returns `SteadyStateResult` |
| `updateAndEvaluate(Map)` | Convenience: updateAll + evaluate |
| `createReconciliationEngine()` | Build a `DataReconciliationEngine` from steady-state variables |
| `setRThreshold(double)` | Set R-statistic threshold (default 0.5) |
| `setSlopeThreshold(double)` | Set max absolute slope (0 = disabled) |
| `setStdDevThreshold(double)` | Set max standard deviation (0 = disabled) |
| `setRequiredFraction(double)` | Fraction of variables that must be steady (default 1.0) |
| `setRequireFullWindow(boolean)` | Require window to be full before evaluating (default true) |
| `clear()` | Remove all variables and reset |

#### SteadyStateVariable

| Method | Description |
|--------|-------------|
| `SteadyStateVariable(String name, int windowSize)` | Create with name and sliding window size (min 3) |
| `addValue(double value)` | Add a sample; recomputes statistics |
| `clear()` | Clear all samples |
| `getMean()` | Window mean |
| `getStandardDeviation()` | Window standard deviation |
| `getRStatistic()` | Cao-Rhinehart R-statistic |
| `getSlope()` | Linear regression slope (per sample) |
| `isAtSteadyState()` | Whether last evaluation flagged as steady |
| `getCount()` | Number of samples in window |
| `getWindowSize()` | Configured window size |
| `setUnit(String)` | Set engineering unit (fluent) |
| `setUncertainty(double)` | Set measurement uncertainty for reconciliation (fluent) |

#### SteadyStateResult

| Method | Description |
|--------|-------------|
| `isAtSteadyState()` | Overall SSD verdict |
| `getSteadyCount()` | Number of steady variables |
| `getTransientCount()` | Number of transient variables |
| `getVariables()` | All variables with their per-variable statistics |
| `getTransientVariables()` | Only the variables that failed the SSD test |
| `toReport()` | Human-readable formatted text report |
| `toJson()` | Machine-readable JSON |

### Tuning the Detector

| Parameter | Typical range | Guidance |
|-----------|--------------|----------|
| **Window size** | 20-60 | Larger = more stable but slower response. 30 is a good default for 10-second scan intervals (~5 min window) |
| **R-threshold** | 0.3-0.8 | Lower = more tolerant of trends. 0.5 works well for most process variables |
| **Slope threshold** | 0-1.0 | Enable only if you need to catch very slow drifts. Units depend on your variable's scale |
| **Std.dev threshold** | 0-inf | Enable to reject signals that are "steady" but too noisy to be useful |
| **Required fraction** | 0.5-1.0 | Set below 1.0 to allow reconciliation even if some variables are still settling |

### Bridging to Data Reconciliation

The `createReconciliationEngine()` method creates a `DataReconciliationEngine` pre-populated with variables that:

1. **Are at steady state** (per R-statistic evaluation)
2. **Have defined uncertainty** (from `setUncertainty()`)

The bridge uses each variable's **window mean** as the measurement value and the configured uncertainty as sigma. Transient variables and variables without uncertainty are excluded.

```java
// Typical workflow
SteadyStateDetector ssd = new SteadyStateDetector(30);
// ... add variables, push data, evaluate() ...

if (ssd.evaluate().isAtSteadyState()) {
    DataReconciliationEngine engine = ssd.createReconciliationEngine();
    // Variables are already populated with mean values and uncertainties
    engine.addMassBalanceConstraint("node1", inlets, outlets);
    ReconciliationResult result = engine.reconcile();
}
```

---

## Data Reconciliation

## Overview

Plant instruments (flow meters, pressure transmitters, temperature sensors) always have measurement errors. Raw readings almost never satisfy the fundamental conservation laws — mass in rarely equals mass out when you add up the meter tags. **Data reconciliation** corrects these readings by finding the smallest statistically-weighted adjustments that make all balances close exactly.

### Key Capabilities

| Feature | Description |
|---------|-------------|
| **Weighted Least Squares** | Adjustments weighted by 1/sigma² — uncertain meters move more |
| **Linear Constraints** | Mass balance, energy balance, or any linear relation A·x = 0 |
| **Gross Error Detection** | Per-variable normalized residual test flags faulty sensors |
| **Iterative Elimination** | Automatically removes worst sensor and re-reconciles |
| **Chi-Square Global Test** | Detects if overall measurement quality is acceptable |
| **JSON / Text Reports** | Machine-readable and human-readable output formats |
| **EJML Matrix Engine** | Uses the EJML `SimpleMatrix` library already in NeqSim |

### Typical Workflow

```
Plant DCS/Historian ──► Python (collect tags) ──► Set measurements on Engine
                                                          │
                                                          ▼
                                                   Define constraints
                                                          │
                                                          ▼
                                                   engine.reconcile()
                                                          │
                                                          ▼
                                               Read reconciled values
                                                   Detect bad sensors
                                                   Update ProcessSystem
```

> **Online loop**: The data collection and scheduling happen externally, typically in Python. The Java engine provides the reconciliation math — you feed it measurements via setters and read back reconciled values.

---

## When to Use Data Reconciliation

| Scenario | Recommendation |
|----------|----------------|
| Mass balance doesn't close across separator/mixer | Use reconciliation |
| Need to identify a faulty flow meter | Use gross error detection |
| Calibrating model parameters to plant data | Use reconciliation first, then [BatchParameterEstimator](README) |
| Adjusting a single variable for a target | Use `Adjuster` instead |
| Streaming real-time data at high frequency | Collect externally, call `reconcile()` at each interval |

---

## Mathematical Background

Given $n$ measurements $\mathbf{y}$ with diagonal covariance $\mathbf{V} = \text{diag}(\sigma_1^2, \ldots, \sigma_n^2)$ and $m$ linear constraints $\mathbf{A} \cdot \mathbf{x} = \mathbf{0}$, the WLS solution is:

$$
\hat{\mathbf{x}} = \mathbf{y} - \mathbf{V} \mathbf{A}^T (\mathbf{A} \mathbf{V} \mathbf{A}^T)^{-1} \mathbf{A} \mathbf{y}
$$

**Objective minimized:**

$$
J = \sum_{i=1}^{n} \left(\frac{\hat{x}_i - y_i}{\sigma_i}\right)^2
$$

**Normalized residual** for gross error detection:

$$
r_i = \frac{\hat{x}_i - y_i}{\sqrt{V_{ii} - V_{ii}^{adj}}}
$$

where $V^{adj} = V - V A^T (A V A^T)^{-1} A V$. If $|r_i|$ exceeds a z-threshold (default 1.96 for 95% confidence), the measurement is flagged as a **gross error**.

**Global test**: The objective $J$ follows a chi-square distribution with $m$ degrees of freedom under the null hypothesis of no gross errors.

---

## Architecture

```
neqsim.process.util.reconciliation
├── ReconciliationVariable    — One measured variable (value + sigma + result)
├── ReconciliationResult      — Full result with statistics, JSON, report
├── DataReconciliationEngine  — WLS solver, gross error detection
└── package-info.java         — Package documentation
```

| Class | Responsibility |
|-------|---------------|
| `ReconciliationVariable` | Holds a single measurement: name, measured value, uncertainty (sigma), reconciled value, unit, optional equipment/property link, normalized residual, gross error flag |
| `DataReconciliationEngine` | Builds the problem (variables + constraints), solves the WLS system using EJML, runs statistical tests |
| `ReconciliationResult` | Immutable result container: all variables, objective value, chi-square statistic, degrees of freedom, global test, gross errors list, constraint residuals before/after, compute time, JSON and text report output |

---

## Step-by-Step Usage

### Step 1 — Define Measurements

Create a `ReconciliationVariable` for each plant measurement. The constructor takes **(name, measuredValue, uncertainty)**:

```java
// Each variable is one plant tag
ReconciliationVariable feed = new ReconciliationVariable("feed_flow", 1000.0, 20.0);
ReconciliationVariable gas  = new ReconciliationVariable("gas_flow",   620.0, 15.0);
ReconciliationVariable liq  = new ReconciliationVariable("liq_flow",   370.0, 10.0);
```

**Parameters:**
- **name** — unique string identifier (e.g. the DCS tag name)
- **measuredValue** — the raw meter reading in engineering units
- **uncertainty** — standard deviation (sigma) of the measurement error in the same units

You can also link a variable to a specific equipment property in a `ProcessSystem`:

```java
ReconciliationVariable feed = new ReconciliationVariable(
    "feed_flow",          // name
    "HP_Separator",       // equipmentName in ProcessSystem
    "massFlowRate",       // property name
    1000.0,               // measured value
    20.0                  // uncertainty (sigma)
);
feed.setUnit("kg/hr");
```

### Step 2 — Set Measurement Uncertainties

The uncertainty (sigma) is the **standard deviation** of the measurement error. It controls how much each measurement is allowed to move during reconciliation:

- **Low sigma** → high confidence in reading → small adjustment allowed
- **High sigma** → low confidence → variable absorbs more of the imbalance

```java
// Precise Coriolis meter: sigma = 0.5% of reading
ReconciliationVariable precise = new ReconciliationVariable("coriolis_flow", 1000.0, 5.0);

// Less precise orifice meter: sigma = 2% of reading
ReconciliationVariable rough = new ReconciliationVariable("orifice_flow", 600.0, 12.0);

// Calculated/estimated value: sigma = 5% of reading
ReconciliationVariable estimated = new ReconciliationVariable("estimated_flow", 400.0, 20.0);
```

**Typical uncertainty guidelines** (see [Uncertainty Guidelines](#uncertainty-guidelines) for detailed values).

### Step 3 — Define Balance Constraints

Add linear constraints of the form: $\sum_i a_i \cdot x_i = 0$

**Option A — Raw coefficient array:**

```java
DataReconciliationEngine engine = new DataReconciliationEngine();
engine.addVariable(feed);   // index 0
engine.addVariable(gas);    // index 1
engine.addVariable(liq);    // index 2

// Constraint: feed - gas - liq = 0  (mass balance around separator)
engine.addConstraint(new double[]{1.0, -1.0, -1.0});
```

The coefficient array has one entry per variable, in the order they were added. Use **+1** for inlets, **-1** for outlets.

**Option B — Named mass balance (recommended):**

```java
engine.addVariable(feed);
engine.addVariable(gas);
engine.addVariable(liq);

engine.addMassBalanceConstraint("Separator balance",
    new String[]{"feed_flow"},                    // inlet names
    new String[]{"gas_flow", "liq_flow"});        // outlet names
```

This is equivalent to `{1.0, -1.0, -1.0}` but self-documenting and less error-prone for large networks.

### Step 4 — Run Reconciliation

```java
ReconciliationResult result = engine.reconcile();

if (result.isConverged()) {
    System.out.println("Reconciliation successful");
    System.out.println("Objective (weighted SSQ): " + result.getObjectiveValue());
} else {
    System.out.println("Failed: " + result.getErrorMessage());
}
```

### Step 5 — Read Reconciled Values

After reconciliation, each variable holds its adjusted value:

```java
for (ReconciliationVariable v : result.getVariables()) {
    System.out.printf("%-15s  meas=%.1f  rec=%.1f  adj=%.2f %s%n",
        v.getName(),
        v.getMeasuredValue(),
        v.getReconciledValue(),
        v.getAdjustment(),
        v.getUnit());
}
```

You can also look up individual variables by name:

```java
double reconciledFeed = engine.getVariable("feed_flow").getReconciledValue();
double feedAdjustment = engine.getVariable("feed_flow").getAdjustment();
```

### Step 6 — Detect Gross Errors

The engine computes a **normalized residual** for each variable. If $|r_i| > \text{threshold}$ (default 1.96), the variable is flagged:

```java
for (ReconciliationVariable v : result.getVariables()) {
    if (v.isGrossError()) {
        System.out.println("GROSS ERROR: " + v.getName()
            + " |r|=" + Math.abs(v.getNormalizedResidual()));
    }
}

// Or check the global test
if (!result.isGlobalTestPassed()) {
    System.out.println("WARNING: Global chi-square test failed — possible gross errors");
}
```

---

## Working with Model (Tuned) Variables

In many online optimization workflows, you compare the **reconciled plant values** against **model-predicted values** from a tuned process simulation. The difference between reconciled and model-predicted values highlights where the simulation deviates from reality.

### Setting Model Values

After running a NeqSim `ProcessSystem` simulation, set its predicted values on each variable:

```java
// 1. Run the process simulation
ProcessSystem process = ... ; // your process model
process.run();

// 2. Read simulation outputs
double modelFeedFlow  = process.getMeasurementDevice("feed_FT").getMeasuredValue();
double modelGasFlow   = process.getMeasurementDevice("gas_FT").getMeasuredValue();
double modelLiqFlow   = process.getMeasurementDevice("liq_FT").getMeasuredValue();

// 3. Set model values on the reconciliation variables
engine.getVariable("feed_flow").setModelValue(modelFeedFlow);
engine.getVariable("gas_flow").setModelValue(modelGasFlow);
engine.getVariable("liq_flow").setModelValue(modelLiqFlow);
```

### Reading Model vs Reconciled Comparison

```java
for (ReconciliationVariable v : result.getVariables()) {
    if (v.hasModelValue()) {
        double modelDelta = v.getReconciledValue() - v.getModelValue();
        System.out.printf("%-15s  reconciled=%.1f  model=%.1f  delta=%.2f%n",
            v.getName(), v.getReconciledValue(), v.getModelValue(), modelDelta);
    }
}
```

### Using Reconciled Values to Tune the Process Model

After reconciliation gives you validated, balanced measurements, use those values to update the simulation model parameters:

```java
// After reconciliation:
double validatedFeed = engine.getVariable("feed_flow").getReconciledValue();
double validatedGas  = engine.getVariable("gas_flow").getReconciledValue();

// Update simulation inputs with reconciled values
Stream feedStream = (Stream) process.getUnit("Feed");
feedStream.setFlowRate(validatedFeed, "kg/hr");

// Re-run the simulation with corrected inputs
process.run();

// Compare model outputs to reconciled values to identify where model needs tuning
double modelLiqOut = ((Separator) process.getUnit("HP Sep")).getLiquidOutStream()
    .getFlowRate("kg/hr");
double reconciledLiq = engine.getVariable("liq_flow").getReconciledValue();
double modelError = reconciledLiq - modelLiqOut;
// If modelError is large, the separator model parameters need adjustment
```

### Full Reconciliation-then-Calibration Workflow

For a complete loop that reconciles measurements and then tunes model parameters, combine with `BatchParameterEstimator`:

```
 ┌─────────────────────────────────────────────────┐
 │ 1. Collect plant measurements (Python/DCS)      │
 │ 2. Set measurements on DataReconciliationEngine │
 │ 3. engine.reconcile()                           │
 │ 4. Check gross errors, remove bad sensors       │
 │ 5. Use reconciled values as "truth"             │
 │ 6. Feed into BatchParameterEstimator            │
 │    to tune model parameters (UA, efficiency,    │
 │    k-values, etc.)                              │
 │ 7. Update ProcessSystem with tuned parameters   │
 │ 8. Repeat at next time interval                 │
 └─────────────────────────────────────────────────┘
```

---

## Complete Java Example

```java
import neqsim.process.util.reconciliation.*;

public class SeparatorReconciliation {
    public static void main(String[] args) {
        // Create engine
        DataReconciliationEngine engine = new DataReconciliationEngine();

        // Add plant measurements: (name, measuredValue, uncertainty)
        engine.addVariable(
            new ReconciliationVariable("feed", 10000.0, 200.0).setUnit("kg/hr"));
        engine.addVariable(
            new ReconciliationVariable("gas", 3500.0, 100.0).setUnit("kg/hr"));
        engine.addVariable(
            new ReconciliationVariable("oil", 4800.0, 150.0).setUnit("kg/hr"));
        engine.addVariable(
            new ReconciliationVariable("water", 1900.0, 80.0).setUnit("kg/hr"));

        // Measurement imbalance: 10000 - 3500 - 4800 - 1900 = -200 kg/hr

        // Define mass balance: feed - gas - oil - water = 0
        engine.addMassBalanceConstraint("3-Phase Separator",
            new String[]{"feed"},
            new String[]{"gas", "oil", "water"});

        // Reconcile
        ReconciliationResult result = engine.reconcile();

        // Print text report
        System.out.println(result.toReport());

        // Access individual reconciled values
        double recFeed = engine.getVariable("feed").getReconciledValue();
        double recGas  = engine.getVariable("gas").getReconciledValue();
        double recOil  = engine.getVariable("oil").getReconciledValue();
        double recWater = engine.getVariable("water").getReconciledValue();

        System.out.printf("Balance check: %.2f - %.2f - %.2f - %.2f = %.6f%n",
            recFeed, recGas, recOil, recWater,
            recFeed - recGas - recOil - recWater);

        // Check for gross errors
        if (result.hasGrossErrors()) {
            System.out.println("*** Gross errors detected in: ");
            for (ReconciliationVariable ge : result.getGrossErrors()) {
                System.out.println("  " + ge.getName());
            }
        }

        // Machine-readable output
        System.out.println(result.toJson());
    }
}
```

---

## Complete Python Example

```python
from neqsim import jneqsim

# Import reconciliation classes
ReconciliationVariable = jneqsim.process.util.reconciliation.ReconciliationVariable
DataReconciliationEngine = jneqsim.process.util.reconciliation.DataReconciliationEngine

# Create engine
engine = DataReconciliationEngine()

# Add measurements from plant DCS/historian
engine.addVariable(ReconciliationVariable("feed", 10000.0, 200.0).setUnit("kg/hr"))
engine.addVariable(ReconciliationVariable("gas", 3500.0, 100.0).setUnit("kg/hr"))
engine.addVariable(ReconciliationVariable("oil", 4800.0, 150.0).setUnit("kg/hr"))
engine.addVariable(ReconciliationVariable("water", 1900.0, 80.0).setUnit("kg/hr"))

# Mass balance: feed - gas - oil - water = 0
engine.addMassBalanceConstraint("3-Phase Sep",
    ["feed"], ["gas", "oil", "water"])

# Run reconciliation
result = engine.reconcile()
print(result.toReport())

# Read reconciled values
for v in result.getVariables():
    print(f"{v.getName():15s}  meas={v.getMeasuredValue():10.1f}  "
          f"rec={v.getReconciledValue():10.1f}  "
          f"adj={v.getAdjustment():+8.2f}  "
          f"|r|={abs(v.getNormalizedResidual()):6.3f}  "
          f"{'**GE**' if v.isGrossError() else 'ok'}")

# Check if measurements are globally consistent
if result.isGlobalTestPassed():
    print("All measurements consistent (chi-square test passed)")
else:
    print("WARNING: measurement quality issue detected")
```

### Python Online Loop Pattern

```python
import time
from neqsim import jneqsim

ReconciliationVariable = jneqsim.process.util.reconciliation.ReconciliationVariable
DataReconciliationEngine = jneqsim.process.util.reconciliation.DataReconciliationEngine

def get_plant_measurements():
    """Read current measurements from DCS/historian (user implementation)."""
    # Example: read from OPC-UA, PI, IP.21, or CSV
    return {
        "feed": (10050.0, 200.0),   # (value, sigma)
        "gas": (3520.0, 100.0),
        "oil": (4780.0, 150.0),
        "water": (1880.0, 80.0),
    }

# Periodic reconciliation loop
while True:
    measurements = get_plant_measurements()

    engine = DataReconciliationEngine()
    for name, (value, sigma) in measurements.items():
        engine.addVariable(ReconciliationVariable(name, float(value), float(sigma)))

    engine.addMassBalanceConstraint("Sep",
        ["feed"], ["gas", "oil", "water"])

    result = engine.reconcile()

    if result.isConverged():
        print(f"OK  obj={result.getObjectiveValue():.3f}  "
              f"gross_errors={result.hasGrossErrors()}")
        # Use reconciled values downstream...
    else:
        print(f"FAILED: {result.getErrorMessage()}")

    time.sleep(60)  # run every 60 seconds
```

---

## API Reference

### ReconciliationVariable

| Method | Returns | Description |
|--------|---------|-------------|
| `ReconciliationVariable(name, value, sigma)` | — | Constructor: name, measured value, uncertainty |
| `ReconciliationVariable(name, equip, prop, value, sigma)` | — | Constructor with equipment/property link |
| `getName()` | `String` | Variable identifier |
| `getMeasuredValue()` | `double` | Raw plant reading |
| `getUncertainty()` | `double` | Standard deviation (sigma) |
| `getReconciledValue()` | `double` | Adjusted value after reconciliation |
| `getAdjustment()` | `double` | reconciledValue − measuredValue |
| `getNormalizedResidual()` | `double` | Statistical test value for gross error detection |
| `isGrossError()` | `boolean` | True if flagged by normalized residual test |
| `getModelValue()` | `double` | Model-predicted value (NaN if not set) |
| `setModelValue(double)` | `void` | Set model prediction for comparison |
| `hasModelValue()` | `boolean` | Whether a model value was set |
| `setUnit(String)` | `this` | Engineering unit (fluent) |
| `setEquipmentName(String)` | `this` | Link to ProcessSystem equipment (fluent) |
| `setPropertyName(String)` | `this` | Property being measured (fluent) |

### DataReconciliationEngine

| Method | Returns | Description |
|--------|---------|-------------|
| `addVariable(var)` | `this` | Register a measurement variable |
| `addConstraint(double[])` | `this` | Add unnamed linear constraint A·x = 0 |
| `addConstraint(double[], name)` | `this` | Add named linear constraint |
| `addMassBalanceConstraint(name, inlets, outlets)` | `this` | Named mass balance by variable names |
| `reconcile()` | `ReconciliationResult` | Run WLS reconciliation with gross error detection |
| `reconcileWithGrossErrorElimination(max)` | `ReconciliationResult` | Iterative elimination of worst sensor |
| `getVariable(name)` | `ReconciliationVariable` | Look up variable by name |
| `getVariableCount()` | `int` | Number of registered variables |
| `getConstraintCount()` | `int` | Number of registered constraints |
| `setGrossErrorThreshold(z)` | `this` | Set z-value (1.96=95%, 2.576=99%) |
| `clear()` | `void` | Remove all variables and constraints |
| `clearConstraints()` | `void` | Remove constraints only, keep variables |

### ReconciliationResult

| Method | Returns | Description |
|--------|---------|-------------|
| `isConverged()` | `boolean` | Whether reconciliation succeeded |
| `getObjectiveValue()` | `double` | Weighted sum of squared adjustments |
| `getChiSquareStatistic()` | `double` | Same as objective — compared against chi-square distribution |
| `getDegreesOfFreedom()` | `int` | Number of constraints (redundancy) |
| `isGlobalTestPassed()` | `boolean` | True if objective within chi-square critical value |
| `getVariables()` | `List` | All variables with reconciled values |
| `getGrossErrors()` | `List` | Variables flagged as gross errors |
| `hasGrossErrors()` | `boolean` | Whether any gross errors were detected |
| `getConstraintResidualsBefore()` | `double[]` | Constraint residuals using raw measurements |
| `getConstraintResidualsAfter()` | `double[]` | Constraint residuals after reconciliation (near-zero) |
| `getComputeTimeMs()` | `long` | Execution time in milliseconds |
| `getErrorMessage()` | `String` | Error description if not converged |
| `toJson()` | `String` | Full JSON output |
| `toReport()` | `String` | Formatted text table |

---

## Uncertainty Guidelines

Choosing the right uncertainty (sigma) is critical. Here are typical values for common instrument types:

| Instrument Type | Typical Accuracy | Sigma as % of Reading |
|----------------|-----------------|----------------------|
| Coriolis flow meter | ±0.1–0.5% | 0.2–0.5% |
| Ultrasonic flow meter | ±0.5–1.0% | 0.5–1.0% |
| Orifice plate | ±1.0–2.0% | 1.0–2.0% |
| Vortex flow meter | ±0.5–1.5% | 0.5–1.5% |
| Turbine meter | ±0.25–0.5% | 0.25–0.5% |
| Level-inferred flow | ±3–5% | 3–5% |
| RTD temperature | ±0.1–0.3 °C | Use absolute value |
| Thermocouple | ±1.0–2.5 °C | Use absolute value |
| Pressure transmitter | ±0.1–0.25% FS | 0.1–0.25% of full scale |
| Calculated/estimated | ±5–10% | 5–10% |

**Example: computing sigma from instrument spec:**

```java
double flowReading = 5000.0;  // kg/hr
double accuracy = 0.01;       // 1% orifice plate
double sigma = flowReading * accuracy;  // 50.0 kg/hr

ReconciliationVariable v = new ReconciliationVariable("FT-101", flowReading, sigma);
```

**Rules of thumb:**
- If a meter is known to be drifting, increase its sigma (2–3× normal)
- For calculated values (not directly measured), use sigma = 5–10% of value
- All sigma values must be in the **same engineering units** as the measured value

---

## Multi-Node Network Example

For process networks with multiple balance points, add one constraint per node:

```java
DataReconciliationEngine engine = new DataReconciliationEngine();

// Node 1: Feed separator
engine.addVariable(new ReconciliationVariable("well_flow",  5000.0, 100.0));
engine.addVariable(new ReconciliationVariable("sep_gas",    2100.0,  50.0));
engine.addVariable(new ReconciliationVariable("sep_liquid", 2850.0,  70.0));

// Node 2: Compressor (gas path)
engine.addVariable(new ReconciliationVariable("comp_out",   2080.0,  50.0));

// Node 3: Pump (liquid path)
engine.addVariable(new ReconciliationVariable("pump_out",   2870.0,  70.0));

// Constraints: one per balance node
engine.addMassBalanceConstraint("Separator",
    new String[]{"well_flow"},
    new String[]{"sep_gas", "sep_liquid"});

engine.addMassBalanceConstraint("Compressor",
    new String[]{"sep_gas"},
    new String[]{"comp_out"});

engine.addMassBalanceConstraint("Pump",
    new String[]{"sep_liquid"},
    new String[]{"pump_out"});

// Solve all balances simultaneously
ReconciliationResult result = engine.reconcile();

// Degrees of freedom = 3 (three constraints, 5 variables → 2 DoF)
System.out.println("DoF: " + result.getDegreesOfFreedom());
System.out.println(result.toReport());
```

**Important:** The system must be **over-determined** (more variables than constraints) for reconciliation to work. If $n \leq m$, the system is exactly determined or under-determined and the engine returns an error.

---

## Gross Error Elimination

When one sensor has a large systematic bias, simple reconciliation distorts all other readings. Use iterative elimination to automatically identify and downweight the faulty sensor:

```java
// Set a stricter threshold (99% confidence)
engine.setGrossErrorThreshold(2.576);

// Iteratively eliminate up to 2 gross errors
ReconciliationResult result = engine.reconcileWithGrossErrorElimination(2);

// Check which sensors were flagged
for (ReconciliationVariable ge : result.getGrossErrors()) {
    System.out.println("Faulty sensor: " + ge.getName()
        + " (normalized residual: " + ge.getNormalizedResidual() + ")");
}
```

**How it works:**
1. Run standard reconciliation
2. Find the variable with the largest normalized residual exceeding the threshold
3. Set that variable's uncertainty to a very large number (1×10¹²) — effectively removing it
4. Re-reconcile
5. Repeat until no more gross errors or max eliminations reached
6. Restore original uncertainties and report all identified gross errors

---

## Integration with ProcessSystem

The reconciliation engine is designed to work alongside a NeqSim process simulation. A typical pattern:

```java
// 1. Build and run process simulation
SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 60.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.setMixingRule("classic");

Stream feed = new Stream("Feed", fluid);
feed.setFlowRate(10000.0, "kg/hr");

Separator sep = new Separator("HP Sep", feed);

ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(sep);
process.run();

// 2. Get model predictions
double modelFeed = feed.getFlowRate("kg/hr");
double modelGas  = sep.getGasOutStream().getFlowRate("kg/hr");
double modelLiq  = sep.getLiquidOutStream().getFlowRate("kg/hr");

// 3. Reconcile plant measurements
DataReconciliationEngine engine = new DataReconciliationEngine();
ReconciliationVariable vFeed = new ReconciliationVariable(
    "feed", "Feed", "massFlowRate", 10050.0, 200.0);
ReconciliationVariable vGas = new ReconciliationVariable(
    "gas", "HP Sep gas", "massFlowRate", 6180.0, 120.0);
ReconciliationVariable vLiq = new ReconciliationVariable(
    "liquid", "HP Sep liq", "massFlowRate", 3720.0, 80.0);

engine.addVariable(vFeed);
engine.addVariable(vGas);
engine.addVariable(vLiq);

engine.addMassBalanceConstraint("HP Sep balance",
    new String[]{"feed"}, new String[]{"gas", "liquid"});

ReconciliationResult result = engine.reconcile();

// 4. Compare reconciled vs model
vFeed.setModelValue(modelFeed);
vGas.setModelValue(modelGas);
vLiq.setModelValue(modelLiq);

for (ReconciliationVariable v : result.getVariables()) {
    if (v.hasModelValue()) {
        double gap = v.getReconciledValue() - v.getModelValue();
        System.out.printf("%s: reconciled=%.1f, model=%.1f, gap=%.1f%n",
            v.getName(), v.getReconciledValue(), v.getModelValue(), gap);
    }
}

// 5. Update simulation with reconciled inputs for model tuning
feed.setFlowRate(engine.getVariable("feed").getReconciledValue(), "kg/hr");
process.run();
```

---

## JSON and Text Reports

### JSON Output

```java
String json = result.toJson();
// Returns:
// {
//   "converged": true,
//   "objectiveValue": 0.4123,
//   "chiSquareStatistic": 0.4123,
//   "degreesOfFreedom": 1,
//   "globalTestPassed": true,
//   "computeTimeMs": 3,
//   "variables": [
//     {"name": "feed", "measured": 10000.0, "reconciled": 9985.2, ...},
//     {"name": "gas",  "measured": 3500.0,  "reconciled": 3507.1, ...},
//     ...
//   ]
// }
```

### Text Report

```java
String report = result.toReport();
// Returns:
// === Data Reconciliation Report ===
// Converged: true
// Objective (weighted SSQ): 0.4123
// Chi-square statistic: 0.4123 (df=1)
// Global test passed: true
// Compute time: 3 ms
//
// Variable               Measured   Reconciled   Adjustment |r_norm|     Flag
// --------               --------   ----------   ---------- --------     ----
// feed                 10000.0000   9985.2000     -14.8000    0.234       ok
// gas                   3500.0000   3507.1000       7.1000    0.156       ok
// oil                   4800.0000   4795.3000      -4.7000    0.098       ok
// water                 1900.0000   1882.8000     -17.2000    0.312       ok
```

---

## Troubleshooting

| Problem | Cause | Solution |
|---------|-------|---------|
| "No variables added" | Called `reconcile()` before adding any variables | Add variables with `addVariable()` first |
| "No constraints added" | Called `reconcile()` without constraints | Add at least one constraint with `addConstraint()` or `addMassBalanceConstraint()` |
| "Need more variables than constraints" | More constraints than variables (under-determined) | Add more measurements or remove redundant constraints |
| All adjustments are zero | Measurements already satisfy constraints exactly | This is correct — no adjustment needed |
| One variable gets all adjustment | Its uncertainty is much larger than others | Review sigma values — ensure they reflect actual instrument accuracy |
| Global test fails | Systematic bias or faulty sensor present | Use `reconcileWithGrossErrorElimination()` to identify the culprit |
| `IllegalArgumentException: Uncertainty must be positive` | Sigma ≤ 0 | All uncertainties must be strictly positive |
| `IllegalArgumentException: Constraint length does not match` | Coefficient array size ≠ number of variables | Ensure constraint array has exactly one entry per registered variable |
| `IllegalArgumentException: Variable not found` | Name in `addMassBalanceConstraint` doesn't match any variable | Check variable names match exactly (case-sensitive) |
| Matrix inversion fails | Singular constraint matrix (redundant constraints) | Check that constraints are linearly independent |

---

## Related Documentation

- [Process Optimization Framework](README) — Batch parameter estimation with Levenberg-Marquardt
- [Optimization Overview](OPTIMIZATION_OVERVIEW) — When to use which optimizer
- [Constraint Framework](constraint-framework) — Unified ProcessConstraint interface
- [Batch Studies](batch-studies) — Sensitivity analysis with parameter sweeps
- [External Optimizer Integration](../../integration/EXTERNAL_OPTIMIZER_INTEGRATION) — Python/SciPy integration patterns

### References

- Cao, S. & Rhinehart, R.R. (1995). "An efficient method for on-line identification of steady state." *Journal of Process Control*, 5(6), 363-374.
- Jiang, T., Chen, B. & He, X. (2003). "Industrial application of wavelet transform to the on-line prediction of side draw qualities of crude unit." *Computers & Chemical Engineering*, 27(4), 519-527.
- Narasimhan, S. & Jordache, C. (2000). *Data Reconciliation and Gross Error Detection*. Gulf Publishing.
