---
title: "Dual EoS Comparison (SRK vs PR78)"
description: "Cross-check thermodynamic predictions between SRK and Peng-Robinson 78 equations of state per TR1244 best practice."
---

# Dual EoS Comparison

The `DualEosComparison` class runs identical flash calculations with both SRK and Peng-Robinson 78 (PR78) equations of state and flags where thermodynamic predictions diverge beyond a configurable threshold. This is a best practice per TR1244 for field development studies and is recommended whenever fluid characterisation uncertainty is significant.

**Class**: `neqsim.process.util.DualEosComparison`

---

## When to Use

- **Field development studies** — TR1244 recommends cross-checking EoS predictions
- **Fluid characterisation QA** — large SRK/PR78 deviations indicate tuning issues
- **Process design verification** — confirm key equipment sizing is not model-sensitive
- **Phase boundary confirmation** — check that phase envelope topology is consistent

---

## Compared Properties

| Property | Unit | Default Threshold |
|----------|------|-------------------|
| Gas density | kg/m3 | 5% |
| Liquid density | kg/m3 | 5% |
| Gas Z-factor | - | 5% |
| Liquid Z-factor | - | 5% |
| Gas viscosity | Pa.s | 5% |
| Liquid viscosity | Pa.s | 5% |
| Gas Cp | J/molK | 5% |
| Liquid Cp | J/molK | 5% |
| Gas enthalpy | J/mol | 5% |
| Liquid enthalpy | J/mol | 5% |
| Gas mole fraction | - | 5% |

---

## Java Example

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.util.DualEosComparison;

// Create the base SRK fluid
SystemSrkEos fluid = new SystemSrkEos(273.15 + 60, 100.0);
fluid.addComponent("methane", 0.75);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.addComponent("n-butane", 0.03);
fluid.addComponent("n-pentane", 0.02);
fluid.addTBPfraction("C7+", 0.05, 150.0, 0.80);
fluid.setMixingRule("classic");

// Build comparison
DualEosComparison comp = new DualEosComparison(fluid);
comp.setDeviationThreshold(0.05);  // 5% (default)

// Add operating conditions to compare
comp.addConditionCelsius(20.0, 50.0);   // Separator conditions
comp.addConditionCelsius(60.0, 100.0);  // Wellhead conditions
comp.addConditionCelsius(80.0, 200.0);  // Reservoir conditions
comp.addConditionCelsius(-10.0, 30.0);  // Export pipeline

// Run all comparisons
comp.run();

// Check for significant deviations
if (comp.hasSignificantDeviations()) {
    System.out.println("WARNING: SRK and PR78 show significant deviations!");
    for (String flag : comp.getAllFlags()) {
        System.out.println("  " + flag);
    }
}

// Full JSON report
System.out.println(comp.toJson());
```

---

## Python Example

```python
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
DualEosComparison = jneqsim.process.util.DualEosComparison

fluid = SystemSrkEos(273.15 + 60.0, 100.0)
fluid.addComponent("methane", 0.75)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)
fluid.addComponent("n-butane", 0.03)
fluid.addComponent("n-pentane", 0.02)
fluid.addTBPfraction("C7+", 0.05, 150.0, 0.80)
fluid.setMixingRule("classic")

comp = DualEosComparison(fluid)
comp.addConditionCelsius(20.0, 50.0)
comp.addConditionCelsius(60.0, 100.0)
comp.run()

if comp.hasSignificantDeviations():
    print("Significant deviations found:")
    for flag in comp.getAllFlags():
        print(f"  {flag}")

print(comp.toJson())
```

---

## JSON Output Structure

The `toJson()` method returns a JSON object containing:

```json
{
  "deviationThreshold": 0.05,
  "numberOfConditions": 4,
  "hasSignificantDeviations": true,
  "allFlags": ["Condition 1 (293.15 K, 50.0 bar): gasDensity ..."],
  "conditions": [
    {
      "temperature_K": 293.15,
      "pressure_bar": 50.0,
      "srkMixingRule": "classic",
      "prMixingRule": "classic",
      "properties": {
        "gasDensity": {
          "srkValue": 42.5,
          "prValue": 44.1,
          "deviationPercent": 3.8,
          "flagged": false
        }
      }
    }
  ]
}
```

---

## Interpretation Guide

| Deviation Range | Interpretation | Action |
|----------------|----------------|--------|
| All properties less than 3% | Excellent EoS agreement | Use either model confidently |
| Some properties 3-5% | Moderate sensitivity | Document sensitivities |
| Density/Z-factor greater than 5% | Significant EoS dependence | Review fluid characterisation, consider tuning |
| Phase fractions differ | Phase boundary sensitivity | Use phase envelope comparison |

---

## Related Documentation

- [Fluid Characterisation](../thermo/characterization/characterization_workflow.md) - TBP fraction tuning
- [Flash Calculations](../thermo/flash-calculations.md) - TPflash and phase equilibrium
- [Field Development Improvements](../fielddevelopment/NEQSIM_FIELD_DEVELOPMENT_IMPROVEMENTS.md) - TR1244 compliance gaps
