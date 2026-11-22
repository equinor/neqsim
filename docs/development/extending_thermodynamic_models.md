---
title: Extending NeqSim with New Thermodynamic Models
description: Executable SRK extension examples, interaction-parameter configuration, and validation requirements for new thermodynamic models.
---

# Extending NeqSim with New Thermodynamic Models

Start a model extension from a working implementation and preserve its results before
changing its thermodynamics. This guide provides complete Java classes for an SRK
extension baseline, followed by a working temperature-dependent interaction-parameter
example. The baseline retains SRK physics; implementing a different equation of state
requires the additional derivatives and validation described below.

The sample classes belong to the application package `example.thermo`. They are **not
classes shipped in the NeqSim JAR**. Save each Java block in its named file under
`example/thermo/` in your Java source directory and compile with NeqSim on the classpath.
The final test class also requires JUnit Jupiter on the test classpath. All Java blocks
on this page are compiled together and executed by
`ThermodynamicExtensionGuideDocumentationTest` in the NeqSim repository.

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Creating a New Equation of State](#creating-a-new-equation-of-state)
3. [Creating Phase Classes](#creating-phase-classes)
4. [Creating Component Classes](#creating-component-classes)
5. [Implementing Mixing Rules](#implementing-mixing-rules)
6. [Complete Example: Modified SRK EoS](#complete-example-modified-srk-eos)
7. [Testing Thermodynamic Models](#testing-thermodynamic-models)
8. [Python Integration](#python-integration)
9. [Best Practices](#best-practices)

## Architecture Overview

NeqSim separates system configuration, phase properties, and component properties.
Choose a concrete implementation close to your proposed model before overriding its
behavior.

| Level | Interfaces and base classes | SRK implementation | Responsibility |
|-------|-----------------------------|--------------------|----------------|
| System | `SystemInterface`, `SystemThermo`, `SystemEos` | `SystemSrkEos` | Creates phases and holds the fluid used by flash operations |
| Phase | `PhaseInterface`, `PhaseEosInterface`, `PhaseEos` | `PhaseSrkEos` | Mixture parameters, equation of state, and residual properties |
| Component | `ComponentInterface`, `ComponentEos` | `ComponentSrk` | Pure-component parameters, attractive terms, and composition derivatives |
| Mixing rule | `EosMixingRulesInterface` | Implementations in `EosMixingRuleHandler` | Mixture parameters and their temperature/composition derivatives |

`ThermodynamicOperations` performs flash calculations on a system. A TP flash updates
phase fractions and compositions; `fluid.initProperties()` then initializes properties
needed for reporting, including physical properties such as density. Creating a new
class alone does not register a new name with Python's `fluid(...)` factory.

## Creating a New Equation of State

### Step 1: Create the System Class

The following `SystemCustomEos.java` is an executable **extension baseline**. It creates
custom fluid-phase objects while inheriting SRK characterization and attractive-term
configuration. No equation or fitted parameter has been changed. The two-argument
constructor uses temperature in kelvin and pressure in bara.

```java
package example.thermo;

import neqsim.thermo.system.SystemSrkEos;

/** SRK baseline for developing and testing a custom fluid-phase model. */
public class SystemCustomEos extends SystemSrkEos {
    private static final long serialVersionUID = 1000L;

    public SystemCustomEos() {
        this(298.15, 1.0);
    }

    public SystemCustomEos(double temperatureK, double pressureBara) {
        super(temperatureK, pressureBara);
        for (int i = 0; i < numberOfPhases; i++) {
            phaseArray[i] = new PhaseCustomEos();
            phaseArray[i].setTemperature(temperatureK);
            phaseArray[i].setPressure(pressureBara);
        }
    }

    @Override
    public SystemCustomEos clone() {
        return (SystemCustomEos) super.clone();
    }
}
```

This baseline covers ordinary fluid phases. When extending it to solids, hydrates, or
additional aqueous phases, preserve the corresponding phase creation and reference-phase
logic in the existing system implementation and add tests for those calculations.

## Creating Phase Classes

### Step 2: Create the Phase Class

Save this block as `PhaseCustomEos.java`. Calling the inherited `addComponent` method
preserves the phase's component count and mole inventory before replacing the component
object. Assigning only `componentArray[compNumber]` omits that bookkeeping.

```java
package example.thermo;

import neqsim.thermo.phase.PhaseSrkEos;

/** Fluid phase that retains the SRK equations while installing custom components. */
public class PhaseCustomEos extends PhaseSrkEos {
    private static final long serialVersionUID = 1000L;

    @Override
    public void addComponent(String name, double moles, double molesInPhase,
            int compNumber) {
        super.addComponent(name, moles, molesInPhase, compNumber);
        componentArray[compNumber] = new ComponentCustomEos(
            name, moles, molesInPhase, compNumber);
    }

    @Override
    public PhaseCustomEos clone() {
        return (PhaseCustomEos) super.clone();
    }
}
```

The inherited SRK cubic form is

$$P = \frac{RT}{v-b} - \frac{a(T)}{v(v+b)}$$

where $v$ is molar volume, $a(T)$ the molar attractive parameter, and $b$ the molar
co-volume. A new cubic form must keep the residual Helmholtz energy, fugacity
coefficients, and their temperature, volume, and composition derivatives consistent.
Changing just `molarVolume`, `calcA`, or a fugacity expression is insufficient.

NeqSim's internal EOS volume convention differs from SI: the no-argument
`getMolarVolume()` returns the numerical SI molar volume multiplied by $10^5$. Use the
unit-bearing `getMolarVolume("m3/mol")` accessor when reporting physical volume and
check the existing EOS implementation's conventions before writing a volume solver.
The accessor with units includes any configured volume correction.

## Creating Component Classes

### Step 3: Create the Component Class

Save this block as `ComponentCustomEos.java`. It deliberately inherits the SRK pure
component parameters and attractive term. This makes the starting point executable and
allows a baseline comparison before adding a new alpha function.

```java
package example.thermo;

import neqsim.thermo.component.ComponentSrk;

/** Component extension baseline with unchanged SRK parameters and derivatives. */
public class ComponentCustomEos extends ComponentSrk {
    private static final long serialVersionUID = 1000L;

    public ComponentCustomEos(String name, double moles, double molesInPhase,
            int compNumber) {
        super(name, moles, molesInPhase, compNumber);
    }

    @Override
    public ComponentCustomEos clone() {
        return (ComponentCustomEos) super.clone();
    }
}
```

For an attractive-term extension, inspect `ComponentSrk` and
`neqsim.thermo.component.attractiveeosterm.AttractiveTermSrk`. The current component
methods include `calca()`, `calcb()`, and `double aT(double temperature)`; avoid
inventing `void calcAT(...)` or `void calcB()` overrides. A new $a(T)$ model also needs
consistent first and second temperature derivatives. Validate those derivatives before
using the model in enthalpy, heat-capacity, or energy-balance calculations.

## Implementing Mixing Rules

### Step 4: Reuse an Existing Rule Where Possible

For the classic mixing rule, the molar mixture parameters are

$$a_{\mathrm{mix}} = \sum_i\sum_j x_i x_j \sqrt{a_i a_j}(1-k_{ij}),\qquad b_{\mathrm{mix}} = \sum_i x_i b_i$$

Here $x_i$ is the mole fraction within the phase. NeqSim's `calcA` and `calcB` return
**extensive** parameters: $A=n^2a_{\mathrm{mix}}$ and $B=nb_{\mathrm{mix}}$, with $n$
the total moles in that phase. Returning the molar expressions directly breaks the
amount and derivative conventions used by the flash calculations.

Use `setMixingRule(EosMixingRuleType.CLASSIC)` for a constant interaction parameter and
`setBinaryInteractionParameter(...)` to set it. Temperature-dependent classic mixing
is already available as `EosMixingRuleType.CLASSIC_T` (type 8). It evaluates

$$k_{ij}(T) = k_{ij,0} + k_{ij,T}\left(\frac{T}{273.15\ \mathrm{K}}-1\right)$$

The two dimensionless coefficients are set through
`EosMixingRulesInterface.setBinaryInteractionParameter` and
`setBinaryInteractionParameterT1`. The constant-parameter getter returns the stored
$k_{ij,0}$, not the evaluated value at the current temperature.

A genuinely new mixing rule must implement the complete `EosMixingRulesInterface`,
including `calcAi`, `calcAij`, `calcAT`, `calcATT`, `calcAiT`, and the required
co-volume derivatives. Start from the appropriate implementation in
`EosMixingRuleHandler`; a class with only `calcA`, `calcB`, and a parameter matrix is
not a complete implementation. See the [Mixing Rules Guide](../thermo/mixing_rules_guide)
for existing model options.

## Complete Example: Modified SRK EoS

This example configures the existing temperature-dependent SRK mixing rule. It does
not introduce a new cubic EOS or require a `PhaseModifiedSrkEos` class. The illustrative
parameters are chosen to demonstrate the API; they are not a validated fit for
methane–CO2.

### System Class

Save this block as `SystemModifiedSrkEos.java`. The convenience method accepts
$k_{ij,\mathrm{ref}}$ at 298.15 K and a slope $s$ in $\mathrm{K^{-1}}$:

$$k_{ij}(T) = k_{ij,\mathrm{ref}} + s(T-298.15\ \mathrm{K})$$

It converts them to the type-8 coefficients as
$k_{ij,0}=k_{ij,\mathrm{ref}}+s(273.15-298.15)\ \mathrm{K}$ and
$k_{ij,T}=s\times273.15\ \mathrm{K}$. Both coefficients are installed on all existing
EOS phase objects, including temporarily inactive phases.

```java
package example.thermo;

import neqsim.thermo.mixingrule.EosMixingRuleType;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemSrkEos;

/** SRK with a convenience setter for the existing temperature-dependent mixing rule. */
public class SystemModifiedSrkEos extends SystemSrkEos {
    private static final long serialVersionUID = 1000L;

    public SystemModifiedSrkEos(double temperatureK, double pressureBara) {
        super(temperatureK, pressureBara);
    }

    public void setLinearInteractionParameter(String first, String second,
            double valueAt298K, double slopePerKelvin) {
        if (getMixingRule() != EosMixingRuleType.CLASSIC_T) {
            throw new IllegalStateException("Select CLASSIC_T before setting its coefficients");
        }
        if (!Double.isFinite(valueAt298K) || !Double.isFinite(slopePerKelvin)) {
            throw new IllegalArgumentException("Interaction coefficients must be finite");
        }
        int firstIndex = getPhase(0).getComponent(first).getComponentNumber();
        int secondIndex = getPhase(0).getComponent(second).getComponentNumber();
        double valueAt273K = valueAt298K + slopePerKelvin * (273.15 - 298.15);
        double normalizedSlope = slopePerKelvin * 273.15;
        for (PhaseInterface phase : phaseArray) {
            if (phase instanceof PhaseEosInterface) {
                EosMixingRulesInterface rule = ((PhaseEosInterface) phase).getEosMixingRule();
                rule.setBinaryInteractionParameter(firstIndex, secondIndex, valueAt273K);
                rule.setBinaryInteractionParameterT1(firstIndex, secondIndex, normalizedSlope);
            }
        }
    }

    @Override
    public SystemModifiedSrkEos clone() {
        return (SystemModifiedSrkEos) super.clone();
    }
}
```

### Usage

Add all components, select the mixing rule, then apply the coefficients. Reapply them
if you subsequently change the mixing rule or add components, because those operations
may rebuild the interaction matrices. Changing only temperature uses the stored
coefficients automatically. Ordinary EOS clones share mixing-rule parameter state:
create a separately configured fluid when fitting a different parameter set. Changing
coefficients on a clone can also affect its original; cloning is suitable here for
changes to temperature, pressure, and phase state with fixed model parameters.

Save this block as `ModifiedSrkExample.java` and run its `main` method.

```java
package example.thermo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.mixingrule.EosMixingRuleType;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class ModifiedSrkExample {
    private static final Logger logger = LogManager.getLogger(ModifiedSrkExample.class);

    public static SystemModifiedSrkEos calculate(double temperatureK) {
        SystemModifiedSrkEos fluid = new SystemModifiedSrkEos(temperatureK, 50.0);
        fluid.addComponent("methane", 0.8);
        fluid.addComponent("CO2", 0.2);
        fluid.setMixingRule(EosMixingRuleType.CLASSIC_T);
        fluid.setLinearInteractionParameter("methane", "CO2", 0.1, 0.0005);
        new ThermodynamicOperations(fluid).TPflash();
        fluid.initProperties();
        return fluid;
    }

    public static void main(String[] args) {
        SystemModifiedSrkEos fluid = calculate(300.0);
        logger.info("Gas Z = {}", fluid.getPhase("gas").getZ());
        logger.info("Gas density = {} kg/m3", fluid.getPhase("gas").getDensity("kg/m3"));
    }
}
```

## Testing Thermodynamic Models

### Runnable Baseline and Parameter Tests

Save this block as `ExtensionGuideExampleTest.java` under your test source directory.
These tests compare the inherited extension against ordinary SRK, check phase/component
inventories, verify the temperature coefficient conversion, and check cloned state changes.
They establish API and regression behavior; they do not establish agreement with
experimental measurements.

```java
package example.thermo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class ExtensionGuideExampleTest {
    @Test
    public void inheritedModelPreservesTwoPhaseFlash() {
        SystemInterface custom = new SystemCustomEos(250.0, 30.0);
        SystemInterface standard = new SystemSrkEos(250.0, 30.0);
        for (SystemInterface fluid : new SystemInterface[] {custom, standard}) {
            fluid.addComponent("methane", 0.7);
            fluid.addComponent("n-hexane", 0.3);
            fluid.setMixingRule("classic");
            new ThermodynamicOperations(fluid).TPflash();
            fluid.initProperties();
        }
        assertEquals(2, custom.getNumberOfPhases());
        assertTrue(custom.getPhase(0) instanceof PhaseCustomEos);
        assertTrue(custom.getPhase(0).getComponent(0) instanceof ComponentCustomEos);
        double[] amounts = {0.7, 0.3};
        for (int component = 0; component < amounts.length; component++) {
            double recoveredMoles = 0.0;
            for (int phase = 0; phase < custom.getNumberOfPhases(); phase++) {
                recoveredMoles += custom.getPhase(phase).getComponent(component)
                    .getNumberOfMolesInPhase();
            }
            assertEquals(amounts[component], recoveredMoles, 1e-9);
        }
        for (String phase : new String[] {"gas", "oil"}) {
            assertEquals(standard.getPhase(phase).getZ(), custom.getPhase(phase).getZ(), 1e-10);
            assertEquals(standard.getPhase(phase).getDensity("kg/m3"),
                custom.getPhase(phase).getDensity("kg/m3"), 1e-8);
        }
    }

    @Test
    public void temperatureDependentKijMatchesConstantKijAtEachTemperature() {
        for (double temperatureK : new double[] {280.0, 300.0, 320.0}) {
            SystemModifiedSrkEos modified = ModifiedSrkExample.calculate(temperatureK);
            SystemInterface reference = new SystemSrkEos(temperatureK, 50.0);
            reference.addComponent("methane", 0.8);
            reference.addComponent("CO2", 0.2);
            reference.setMixingRule("classic");
            reference.setBinaryInteractionParameter("methane", "CO2",
                0.1 + 0.0005 * (temperatureK - 298.15));
            new ThermodynamicOperations(reference).TPflash();
            reference.initProperties();
            assertEquals(reference.getPhase("gas").getZ(),
                modified.getPhase("gas").getZ(), 1e-10);
            assertEquals(reference.getPhase("gas").getDensity("kg/m3"),
                modified.getPhase("gas").getDensity("kg/m3"), 1e-8);
        }
    }

    @Test
    public void cloneRetainsCoefficientsWhenOnlyStateChanges() {
        SystemModifiedSrkEos original = ModifiedSrkExample.calculate(300.0);
        SystemModifiedSrkEos clone = original.clone();
        assertNotSame(original.getPhase(0), clone.getPhase(0));
        clone.setTemperature(320.0);
        new ThermodynamicOperations(clone).TPflash();
        assertEquals(ModifiedSrkExample.calculate(320.0).getPhase("gas").getZ(),
            clone.getPhase("gas").getZ(), 1e-10);
        assertEquals(300.0, original.getTemperature(), 1e-12);
        new ThermodynamicOperations(original).TPflash();
        assertEquals(ModifiedSrkExample.calculate(300.0).getPhase("gas").getZ(),
            original.getPhase("gas").getZ(), 1e-10);
    }
}
```

The second test compares isothermal phase properties. A temperature-dependent $k_{ij}$
also contributes to temperature derivatives, so enthalpy and heat capacity need their
own derivative tests; they need not equal a model whose $k_{ij}$ is held constant.

## Python Integration

### Using the Existing Rule from Python

This example runs with the `neqsim` Python package and does not require the custom Java
classes. It reproduces the Java configuration through the existing public APIs.

```python
from neqsim import jneqsim

fluid = jneqsim.thermo.system.SystemSrkEos(300.0, 50.0)
fluid.addComponent("methane", 0.8)
fluid.addComponent("CO2", 0.2)
fluid.setMixingRule(8)

reference_kij = 0.1
slope_per_kelvin = 0.0005
for phase_index in range(fluid.getMaxNumberOfPhases()):
    rule = fluid.getPhase(phase_index).getMixingRule()
    rule.setBinaryInteractionParameter(
        0, 1, reference_kij + slope_per_kelvin * (273.15 - 298.15)
    )
    rule.setBinaryInteractionParameterT1(0, 1, slope_per_kelvin * 273.15)

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()
fluid.initProperties()
print(f"Gas Z = {fluid.getPhase('gas').getZ():.6f}")
print(f"Gas density = {fluid.getPhase('gas').getDensity('kg/m3'):.3f} kg/m3")
```

To use your own Java classes, first compile and package them against a compatible
NeqSim version. Add that JAR using `jpype.addClassPath(...)` and resolve the class with
`jpype.JClass("example.thermo.SystemModifiedSrkEos")`. The path passed to
`addClassPath` must be the JAR you actually built; installing the `neqsim` package alone
does not provide these application classes.

### Generating Phase Envelopes

Phase-envelope arrays belong to the calculation object. Retrieve them with
`ops.get("dewT")`, `ops.get("dewP")`, `ops.get("bubT")`, and `ops.get("bubP")`.
Temperatures are in kelvin and pressures in bara. Paired `NaN` values mark breaks
between calculated branch segments; retain them so Matplotlib leaves gaps rather than
connecting discontinuous segments. This complete example compares the shipped SRK and
PR models and requires `matplotlib`.

```python
from neqsim import jneqsim
import matplotlib.pyplot as plt


def generate_phase_envelope(fluid_class):
    fluid = fluid_class(250.0, 1.0)
    for name, fraction in {"methane": 0.8, "ethane": 0.1, "propane": 0.1}.items():
        fluid.addComponent(name, fraction)
    fluid.setMixingRule("classic")
    ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
    ops.calcPTphaseEnvelope()
    return {
        key: list(ops.get(key))
        for key in ("dewT", "dewP", "bubT", "bubP")
    }


models = [
    (jneqsim.thermo.system.SystemSrkEos, "SRK"),
    (jneqsim.thermo.system.SystemPrEos, "PR"),
]
fig, ax = plt.subplots(figsize=(8, 5))
for fluid_class, name in models:
    envelope = generate_phase_envelope(fluid_class)
    for branch, style in (("dew", "-"), ("bub", "--")):
        temperatures = envelope[branch + "T"]
        pressures = envelope[branch + "P"]
        if len(temperatures) != len(pressures) or not temperatures:
            raise RuntimeError(f"Missing or mismatched {name} {branch} envelope arrays")
        ax.plot(temperatures, pressures, style, label=f"{name} {branch}")
ax.set_xlabel("Temperature (K)")
ax.set_ylabel("Pressure (bara)")
ax.set_title("Phase envelope comparison")
ax.grid(True)
ax.legend()
fig.tight_layout()
plt.show()
```

## Best Practices

1. **Preserve a baseline.** First verify that the inherited extension reproduces the
   original model's phase split and properties, including total and component mole
   balances. Then change one thermodynamic assumption at a time.
2. **Validate new physics against traceable data.** Record the source, fluid composition,
   conditions, units, uncertainty, and applicability range of each reference. The tests
   above are regression checks, not external validation. Pure-component vapor-pressure
   comparisons must use subcritical temperatures; a vapor-pressure test above the
   component's critical temperature is physically invalid.
3. **Keep derivatives consistent.** Check analytical temperature, volume, and composition
   derivatives against numerical perturbations. Test enthalpy, heat capacity, fugacity
   equality, and energy flashes alongside TP flashes.
4. **Retain numerical safeguards.** Reuse the existing EOS volume/root implementation
   until the new model actually requires a different solver. Test single-phase,
   two-phase, near-critical, and dilute-component conditions, and require finite results
   and meaningful convergence failures.
5. **Preserve cloning and serialization.** Give serializable classes a stable
   `serialVersionUID` and deep-copy new mutable state. Ordinary EOS cloning shares
   mixing-rule parameters; keep those parameters fixed during cloned calculations and
   configure independent fluids for parameter fitting. Add a serialization round-trip
   test when introducing new fields or external parameter containers.
6. **Document model scope.** Separate fitted parameters from illustrative values and
   describe which phase types, mixture families, temperature/pressure ranges, and
   property calculations have been validated. Confirm that a passing API example does
   not imply that the chosen parameter set is an improvement for an engineering case.

## See Also

- [Mathematical Models Overview](../thermo/mathematical_models)
- [EoS System Types](../thermo/system/)
- [Mixing Rules Guide](../thermo/mixing_rules_guide)
- [Extending Physical Properties](extending_physical_properties)
- [Python Extension Patterns](python_extension_patterns)

*Document last updated: September 2026*
