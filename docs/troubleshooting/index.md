---
title: NeqSim Troubleshooting Guide
description: Solutions to common NeqSim problems - convergence issues, density problems, Python integration errors, and more.
---

# NeqSim Troubleshooting Guide

Quick solutions to common problems when using NeqSim.

## Error Categories

| Category | Common Problems |
|----------|-----------------|
| [Flash Convergence](#flash-convergence-issues) | Flash fails, wrong phases, oscillation |
| [Density Issues](#density-issues) | Wrong density values, unit confusion |
| [Python Integration](#python-integration-issues) | Import errors, JVM problems, type errors |
| [Process Simulation](#process-simulation-issues) | Equipment failures, recycle divergence |
| [Phase Envelope](#phase-envelope-issues) | Envelope calculation fails |

---

## Flash Convergence Issues

### Problem: Flash calculation fails or doesn't converge

**Symptoms:**
- Exception during TPflash
- `getNumberOfPhases()` returns unexpected value
- Properties look unrealistic

**Solutions:**

1. **Check temperature and pressure ranges**
   ```python
   # Ensure T and P are within reasonable bounds
   # T should be in Kelvin (not Celsius!)
   fluid = SystemSrkEos(273.15 + 25, 50.0)  # 25°C, 50 bara
   # NOT: SystemSrkEos(25, 50)  # This is -248°C!
   ```

2. **Enable multiphase check**
   ```python
   fluid.setMultiPhaseCheck(True)
   ```

3. **Try different initial estimates**
   ```python
   ops = ThermodynamicOperations(fluid)
   ops.TPflash()
   
   # If that fails, try:
   fluid.init(0)  # Reset to composition only
   fluid.setTemperature(273.15 + 30)  # Slightly different T
   ops.TPflash()
   ```

4. **Check composition normalization**
   ```python
   # Components should sum to 1.0 (or close)
   total = sum([fluid.getComponent(i).getz() for i in range(fluid.getNumberOfComponents())])
   print(f"Total composition: {total}")  # Should be ~1.0
   ```

5. **Use appropriate EoS for the fluid**
   ```python
   # For CO2/water, use CPA not SRK
   fluid = SystemSrkCPAstatoil(T, P)  # Not SystemSrkEos
   ```

### Problem: Wrong number of phases detected

**Solutions:**

1. **Enable stability analysis**
   ```python
   fluid.setMultiPhaseCheck(True)
   ops.TPflash()
   ```

2. **Check if near critical point**
   ```python
   # Near critical point, phase detection is difficult
   # Try slightly different conditions
   ```

3. **Verify mixing rule is set**
   ```python
   fluid.setMixingRule("classic")  # REQUIRED!
   ```

---

## Density Issues

### Problem: Density value seems wrong

**Root Cause:** Most common issue - using `getDensity()` without a unit string.

**Wrong:**
```python
density = fluid.getDensity()  # Returns EoS density WITHOUT Peneloux correction
```

**Correct:**
```python
density = fluid.getDensity("kg/m3")  # Returns density WITH volume correction
```

### Problem: Gas density too low / liquid density too high

1. **Ensure properties are initialized**
   ```python
   fluid.initProperties()  # Call AFTER flash
   density = fluid.getDensity("kg/m3")
   ```

2. **Check if Peneloux is enabled**
   ```python
   # Peneloux correction should be automatic with getDensity("kg/m3")
   # But verify you're reading the right phase:
   gas_density = fluid.getPhase("gas").getDensity("kg/m3")
   ```

3. **For phase-specific density**
   ```python
   if fluid.hasPhaseType("gas"):
       gas_density = fluid.getPhase("gas").getDensity("kg/m3")
   if fluid.hasPhaseType("oil"):
       oil_density = fluid.getPhase("oil").getDensity("kg/m3")
   ```

### Problem: Density changes unexpectedly after operations

**Cause:** Flash changes phase fractions. Re-read phase-specific properties.

```python
# After any operation that changes state:
fluid.initProperties()  # Re-initialize
density = fluid.getDensity("kg/m3")
```

---

## Python Integration Issues

### Problem: `ModuleNotFoundError: No module named 'neqsim'`

**Solution:** Install neqsim-python:
```bash
pip install neqsim
```

### Problem: `AttributeError: 'NoneType' object has no attribute...`

**Cause:** JVM not started properly.

**Solution:** Use the correct import pattern:
```python
# CORRECT - auto-starts JVM
from neqsim import jneqsim

# Create fluid through jneqsim gateway
fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + 25, 50.0)
```

### Problem: `TypeError: No matching overloads found`

**Cause:** Wrong parameter types passed to Java methods.

**Solutions:**

1. **Explicitly cast numbers to float**
   ```python
   # If value might be int:
   stream.setFlowRate(float(10000), "kg/hr")
   ```

2. **Convert Python strings properly**
   ```python
   # Java String to Python string for formatting:
   name = str(component.getComponentName())
   print(f"Component: {name}")
   ```

### Problem: `RuntimeError: JVM cannot be restarted`

**Cause:** JVM was already started and stopped in this Python session.

**Solution:** Restart the Python kernel/process. JVM can only be started once per process.

### Problem: Java String in Python f-string fails

**Symptom:** `TypeError` when using format specifiers with Java strings.

```python
# WRONG - Java String doesn't support Python format specs
comp = fluid.getComponent(0)
print(f"Name: {comp.getComponentName()}")  # May fail with :.4f etc.

# CORRECT - Convert to Python string first
name = str(comp.getComponentName())
print(f"Name: {name}")
```

---

## Process Simulation Issues

### Problem: Equipment doesn't run / stream properties are zero

**Causes:**
1. Stream not added to ProcessSystem
2. Equipment not connected properly
3. ProcessSystem not run

**Solution:**
```python
# CORRECT pattern
process = ProcessSystem()

# Add ALL equipment
process.add(feed)
process.add(separator)
process.add(compressor)

# Run the entire system
process.run()

# NOW check results
print(separator.getGasOutStream().getFlowRate("kg/hr"))
```

### Problem: Recycle doesn't converge

**Solutions:**

1. **Improve initial guess**
   ```python
   # Set reasonable initial flow in recycle stream
   recycle_stream.setFlowRate(estimated_flow, "kg/hr")
   ```

2. **Loosen tolerance**
   ```python
   recycle.setTolerance(1e-4)  # Less strict
   ```

3. **Increase iterations**
   ```python
   recycle.setMaximumIterations(100)
   ```

4. **Add damping**
   ```python
   recycle.setDampingFactor(0.5)  # Slower but more stable
   ```

### Problem: Compressor power is unrealistic

**Check:**
1. Efficiency is set (default may be 100%)
   ```python
   compressor.setIsentropicEfficiency(0.75)  # Typical: 70-85%
   ```

2. Pressure ratio is reasonable
   ```python
   # Very high ratios need multiple stages
   ratio = outlet_P / inlet_P
   if ratio > 3.5:
       print("Consider multi-stage compression")
   ```

---

## Phase Envelope Issues

### Problem: Phase envelope calculation fails

**Solutions:**

1. **Ensure composition is valid**
   ```python
   # No zero or negative mole fractions
   fluid.addComponent("methane", 0.85)  # Not 0.0!
   ```

2. **Use appropriate EoS**
   ```python
   # Phase envelope works best with cubic EoS
   fluid = SystemSrkEos(T, P)  # or SystemPrEos
   ```

3. **Try different starting point**
   ```python
   # Start at lower pressure
   fluid = SystemSrkEos(273.15 + 20, 10.0)  # Lower P
   ops.calcPTphaseEnvelope()
   ```

### Problem: Incomplete phase envelope

**Cause:** Calculation stopped at convergence issue.

**Solutions:**

1. **Increase points**
   ```python
   # More points may trace envelope better
   ops.calcPTphaseEnvelopeSpecificPoint(specific_T, specific_P)
   ```

2. **Check for near-critical behavior**
   ```python
   # Near critical, envelope is sensitive
   # Try adding trace of inert component
   fluid.addComponent("nitrogen", 0.001)  # May help convergence
   ```

---

## Performance Issues

### Problem: Simulation is very slow

**Solutions:**

1. **Reduce flash iterations**
   ```python
   # For quick estimates
   fluid.init(1)  # Basic properties only
   ```

2. **Avoid repeated initialization**
   ```python
   # DON'T call initProperties() in loops
   for T in temperatures:
       fluid.setTemperature(T)
       ops.TPflash()
   # THEN call initProperties() once
   fluid.initProperties()
   ```

3. **Clone instead of recreating**
   ```python
   # Faster than creating new fluid
   fluid2 = fluid.clone()
   fluid2.setTemperature(new_T)
   ```

---

## Common Error Messages

| Error Message | Likely Cause | Fix |
|---------------|--------------|-----|
| `NullPointerException` | Uninitialized object or missing component | Check all objects are created and added |
| `IndexOutOfBoundsException` | Accessing non-existent phase/component | Check `getNumberOfPhases()` first |
| `No matching overloads` | Wrong parameter type | Cast to float/str explicitly |
| `JVM cannot be restarted` | JVM already started | Restart Python kernel |
| `Flash did not converge` | Bad initial guess or impossible state | Check T, P, composition |

---

## Quick Fix Recipes

For copy-paste solutions to common tasks, see the **[Cookbook](../cookbook/index)**:

| Problem | Recipe |
|---------|--------|
| Get density correctly | [thermodynamics-recipes.md#get-density-correctly](../cookbook/thermodynamics-recipes#get-density-correctly) |
| Which EoS to use? | [thermodynamics-recipes.md#which-eos-should-i-use](../cookbook/thermodynamics-recipes#which-eos-should-i-use) |
| Set up recycle | [process-recipes.md#recycle-stream](../cookbook/process-recipes#recycle-stream) |
| Pipeline pressure drop | [pipeline-recipes.md#simple-pressure-drop](../cookbook/pipeline-recipes#simple-pressure-drop) |

---

## Getting Help

1. **Check the JavaDoc API**: [https://equinor.github.io/neqsimhome/javadoc/site/apidocs/index.html](https://equinor.github.io/neqsimhome/javadoc/site/apidocs/index.html)

2. **Search GitHub Issues**: [https://github.com/equinor/neqsim/issues](https://github.com/equinor/neqsim/issues)

3. **Check documentation**: [Reference Manual](../REFERENCE_MANUAL_INDEX)

4. **Browse the Cookbook**: [Quick recipes](../cookbook/index) for common tasks

5. **Open a new issue** with:
   - NeqSim version
   - Python version (if using Python)
   - Minimal code to reproduce
   - Full error message/traceback
