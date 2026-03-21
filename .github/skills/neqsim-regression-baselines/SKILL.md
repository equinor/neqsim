---
name: neqsim-regression-baselines
description: "Regression baseline management for NeqSim. USE WHEN: modifying solver logic, property correlations, or EOS implementations. Ensures changes don't silently degrade accuracy. Covers creating baseline fixtures, writing regression tests, and detecting accuracy drift."
---

# NeqSim Regression Baseline Management

Preventing silent accuracy drift in a physics engine requires committed baseline
values that CI validates on every build.

## Why Baselines Matter

A property correlation change that improves methane density by 0.1% might
degrade ethane viscosity by 5%. Without baselines, these regressions are invisible
until a downstream user reports wrong results months later.

## Baseline Workflow

### When to Create Baselines

Create baselines **before** modifying:
- Flash calculation algorithms (`flashops/`)
- Phase property calculations (`phase/`, `physicalproperties/`)
- Component parameter databases (`COMP.csv`, `mbwr32param.csv`)
- Mixing rule implementations
- Process equipment calculations that depend on thermodynamic properties
- Cost estimation or mechanical design correlations

### Step 1: Capture Current Values

Run the existing code and record results in a JSON fixture file:

```java
@Test
void captureBaseline_SRK_methane_density() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 60.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initProperties();

    // Record these values as the baseline
    double density = fluid.getDensity("kg/m3");     // e.g., 45.23
    double Cp = fluid.getCp("J/molK");              // e.g., 38.5
    double Z = fluid.getZ();                        // e.g., 0.892

    assertEquals(45.23, density, 0.5, "Methane density at 25C/60bar");
    assertEquals(38.5, Cp, 0.5, "Methane Cp at 25C/60bar");
    assertEquals(0.892, Z, 0.005, "Methane Z-factor at 25C/60bar");
}
```

### Step 2: Choose Appropriate Tolerances

| Property Type | Typical Tolerance | Rationale |
|--------------|-------------------|-----------|
| Density | 0.5-1.0% relative | Well-predicted by cubic EOS |
| Z-factor | 0.5% absolute (0.005) | Directly from EOS |
| Viscosity | 2-5% relative | Correlation-dependent, more variable |
| Thermal conductivity | 5-10% relative | Least accurate transport property |
| Phase fractions | 1% absolute (0.01) | Phase split sensitivity varies |
| Enthalpy/Cp | 1-2% relative | Derived from EOS |
| Bubble/dew point | 0.5-1.0 K or 0.5-1.0 bar | Phase boundary sensitivity |
| Compressor power | 1-2% relative | Depends on enthalpy accuracy |
| Separator compositions | 2% relative per component | Depends on K-value accuracy |

### Step 3: Create Fixture File (Optional for Complex Baselines)

For multi-point baselines, create JSON fixtures in `src/test/resources/baselines/`:

```json
{
  "description": "SRK methane properties at standard conditions",
  "eos": "SystemSrkEos",
  "created": "2026-03-21",
  "reference": "NIST WebBook",
  "points": [
    {
      "T_C": 25.0, "P_bara": 10.0,
      "density_kg_m3": 6.52, "Z": 0.987, "Cp_J_molK": 36.1
    },
    {
      "T_C": 25.0, "P_bara": 60.0,
      "density_kg_m3": 45.23, "Z": 0.892, "Cp_J_molK": 38.5
    },
    {
      "T_C": 25.0, "P_bara": 200.0,
      "density_kg_m3": 142.8, "Z": 0.745, "Cp_J_molK": 42.1
    }
  ]
}
```

### Step 4: Write Regression Test

Place regression tests in the test package that mirrors the modified source:

```java
package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 * Regression baselines for SRK EOS properties.
 * These values were captured on [date] and validated against NIST.
 * If a test fails after your change, verify whether the new values
 * are MORE accurate (update baseline) or LESS accurate (revert change).
 */
public class SrkEosRegressionTest extends neqsim.NeqSimTest {

    @Test
    void methane_density_multipoint() {
        SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 60.0);
        fluid.addComponent("methane", 1.0);
        fluid.setMixingRule("classic");

        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        ops.TPflash();
        fluid.initProperties();

        assertEquals(45.23, fluid.getDensity("kg/m3"), 0.5,
            "Methane density at 25C/60bar — baseline captured 2026-03-21");
    }
}
```

## When a Baseline Test Fails

A failing baseline test means a code change altered numerical results. Follow this decision tree:

```
Baseline test fails
  |
  ├─ Was the change intentional (bug fix, new correlation)?
  |    |
  |    ├─ YES: Compare new values against external reference (NIST, experiment)
  |    |    |
  |    |    ├─ New values are MORE accurate → Update baseline, document in commit message
  |    |    |
  |    |    └─ New values are LESS accurate → Revert the change
  |    |
  |    └─ NO (unexpected side effect): Investigate which change caused the regression
  |
  └─ Is the change within tolerance?
       |
       ├─ YES: Test should not have failed — tighten or loosen tolerance appropriately
       |
       └─ NO: Genuine regression — investigate and fix
```

## Key Update Rule

When updating a baseline value, the commit message MUST include:
1. Which property changed and by how much
2. Why (which code change caused it)
3. External reference confirming the new value is correct (or more accurate)

Example commit message:
```
Update SRK methane density baseline: 45.23 -> 45.31 kg/m3 (+0.18%)

Changed volume translation parameter for methane. New value validated
against NIST: experimental = 45.35 kg/m3 at 25C/60bar.
Old error: 0.27%, New error: 0.09%.
```

## Baseline Categories

### Tier 1: Critical (Must Never Regress)
- Pure component properties vs NIST (density, Cp, Z-factor)
- Binary VLE vs experimental data (K-values, bubble/dew points)
- Flash mass/energy balance closure (must be < 1e-6)

### Tier 2: Important (Should Be Monitored)
- Multi-component mixture properties
- Process equipment outputs (compressor power, separator splits)
- Transport properties (viscosity, conductivity)

### Tier 3: Informational (Track for Trends)
- Complex system properties (10+ component mixtures)
- Near-critical behavior
- Trace component phase distribution

## Existing Test Files to Reference

When adding baselines, check existing tests for patterns:
- `src/test/java/neqsim/thermo/` — Thermodynamic property tests
- `src/test/java/neqsim/process/` — Process equipment tests
- `src/test/java/neqsim/pvtsimulation/` — PVT simulation tests
- `src/test/java/neqsim/standards/` — Standards calculation tests
