---
title: H2S Scavenger Unit Operation
description: Guide to using the H2S scavenger unit operation for modeling chemical scavenging of hydrogen sulfide from gas streams. Covers scavenger types, injection rate sizing, efficiency correlations, and cost estimation.
keywords: "H2S scavenger, triazine, sour gas, chemical treatment, injection rate, process simulation"
---

# H2S Scavenger Unit Operation

The `H2SScavenger` class models chemical scavenging of hydrogen sulfide (H2S) from gas streams. Unlike absorption or amine treatment, this unit operation uses empirical correlations based on scavenger type, injection rate, and operating conditions rather than rigorous chemical reaction calculations.

## Overview

H2S scavengers are commonly used for:
- Treating low-to-moderate H2S concentrations (typically < 1000 ppm)
- Wellhead or field gas conditioning
- Emergency or temporary H2S control
- Situations where amine systems are impractical

The implementation is based on literature correlations from:
- GPSA Engineering Data Book
- Kohl & Nielsen "Gas Purification"
- Arnold & Stewart "Surface Production Operations"

## Scavenger Types

Five scavenger types are supported, each with different characteristics:

| Type | Chemical | Stoichiometry | Base Efficiency | Typical Application |
|------|----------|---------------|-----------------|---------------------|
| `TRIAZINE` | MEA-Triazine | 4.5 lb/lb H2S | 95% | Most common liquid scavenger |
| `GLYOXAL` | Glyoxal-based | 5.5 lb/lb H2S | 90% | Lower temperature applications |
| `IRON_SPONGE` | Iron oxide on wood chips | 2.5 lb/lb H2S | 98% | Dry bed, batch operation |
| `CAUSTIC` | Sodium hydroxide (NaOH) | 2.4 lb/lb H2S | 95% | High pH applications |
| `LIQUID_REDOX` | LO-CAT, SulFerox | Catalytic | 99.5% | Continuous regeneration |

## Basic Usage

### Java Example

<!-- h2s-scavenger-example: triazine-quick-start -->

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.absorber.H2SScavenger;
import neqsim.process.equipment.absorber.H2SScavenger.ScavengerType;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

public final class H2SScavengerQuickStart {
  private static final Logger logger =
      LogManager.getLogger(H2SScavengerQuickStart.class);

  private H2SScavengerQuickStart() {}

  public static void main(String[] args) {
    SystemSrkEos sourGas = new SystemSrkEos(273.15 + 40.0, 50.0);
    sourGas.addComponent("methane", 0.90);
    sourGas.addComponent("ethane", 0.04);
    sourGas.addComponent("H2S", 0.005);
    sourGas.addComponent("CO2", 0.015);
    sourGas.setMixingRule("classic");

    Stream feed = new Stream("Sour Gas", sourGas);
    feed.setFlowRate(100000.0, "Sm3/day");

    H2SScavenger scavenger =
        new H2SScavenger("MEA-Triazine Injection", feed);
    scavenger.setScavengerType(ScavengerType.TRIAZINE);
    scavenger.setScavengerInjectionRate(50.0, "l/hr");
    scavenger.setScavengerConcentration(0.5);

    ProcessSystem process = new ProcessSystem("H2S treatment");
    process.add(feed);
    process.add(scavenger);
    process.run();

    double inletPpm = scavenger.getInletH2SConcentration();
    double outletPpm = scavenger.getOutletH2SConcentration();
    double removalPercent = scavenger.getH2SRemovalEfficiencyPercent();
    double consumptionKgPerHour =
        scavenger.getActualScavengerConsumption("kg/hr");
    String json = scavenger.toJson();

    assert Double.isFinite(inletPpm) && inletPpm > 0.0;
    assert Double.isFinite(outletPpm) && outletPpm < inletPpm;
    assert removalPercent > 0.0 && removalPercent < 100.0;
    assert consumptionKgPerHour > 0.0;
    assert json.contains("\"injectionRateUnit\":\"l/hr\"");
    assert json.contains("\"h2sRemovedKgPerHr\":");

    logger.info(
        "H2S scavenger: inlet={} ppm, outlet={} ppm, removal={}%, consumption={} kg/hr",
        inletPpm, outletPpm, removalPercent, consumptionKgPerHour);
  }
}
```

The literal program is compiled with Java 8 settings and run with assertions enabled by
[`H2SScavengerDocumentationTest`](../../src/test/java/neqsim/documentation/H2SScavengerDocumentationTest.java):

```bash
./mvnw -Dtest=H2SScavengerDocumentationTest test
```

The component amounts are normalized internally; the example therefore verifies removal from
the simulated inlet rather than treating the unnormalized input amount as an exact ppm specification.

### Python Example (via neqsim-python)

```python
from neqsim import jneqsim

# Import classes
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
Stream = jneqsim.process.equipment.stream.Stream
H2SScavenger = jneqsim.process.equipment.absorber.H2SScavenger
ScavengerType = jneqsim.process.equipment.absorber.H2SScavenger.ScavengerType
ProcessSystem = jneqsim.process.processmodel.ProcessSystem

# Create sour gas (temperature in Kelvin, pressure in bara)
sour_gas = SystemSrkEos(273.15 + 40.0, 50.0)
sour_gas.addComponent("methane", 0.90)
sour_gas.addComponent("ethane", 0.04)
sour_gas.addComponent("H2S", 0.005)  # 5000 ppm
sour_gas.addComponent("CO2", 0.015)
sour_gas.setMixingRule("classic")

# Create feed stream
feed = Stream("Sour Gas", sour_gas)
feed.setFlowRate(100000.0, "Sm3/day")

# Create scavenger unit
scavenger = H2SScavenger("Triazine Injection", feed)
scavenger.setScavengerType(ScavengerType.TRIAZINE)
scavenger.setScavengerInjectionRate(50.0, "l/hr")
scavenger.setScavengerConcentration(0.5)

# Run simulation
process = ProcessSystem()
process.add(feed)
process.add(scavenger)
process.run()

# Results
print(f"Inlet H2S: {scavenger.getInletH2SConcentration():.1f} ppm")
print(f"Outlet H2S: {scavenger.getOutletH2SConcentration():.1f} ppm")
print(f"Efficiency: {scavenger.getH2SRemovalEfficiencyPercent():.1f}%")
print(
    "Consumption: "
    f"{scavenger.getActualScavengerConsumption('kg/hr'):.3f} kg/hr"
)
```

## Configuration Parameters

### Scavenger Properties

| Method | Description | Units |
|--------|-------------|-------|
| `setScavengerType(type)` | Set scavenger chemical type | `ScavengerType` enum |
| `setScavengerInjectionRate(rate, unit)` | Injection rate | l/hr, gal/hr, kg/hr, lb/hr |
| `setScavengerConcentration(conc)` | Active ingredient fraction | 0-1 (e.g., 0.5 = 50%) |

### Operating Conditions

| Method | Description | Default |
|--------|-------------|---------|
| `setContactTime(seconds)` | Gas-liquid contact time | 30 seconds |
| `setMixingEfficiency(eff)` | Contactor mixing quality | 0.85 (0-1 scale) |
| `setTargetH2SConcentration(ppm)` | Target outlet spec | 4.0 ppm |

### Design Sizing

```java
// Calculate required injection rate for a target spec
scavenger.setTargetH2SConcentration(4.0);  // 4 ppm sales gas spec
double requiredRate = scavenger.calculateRequiredInjectionRate();  // l/hr
```

## Efficiency Correlation

The implementation first converts the configured injection rate to kg/h and applies the active
ingredient mass fraction. For active scavenger rate $\dot m_s$, inlet H2S mass flow $\dot m_{H_2S}$,
and the scavenger-type stoichiometric mass ratio $S$, define the dose ratio

$$D=\frac{\dot m_s}{S\,\dot m_{H_2S}}$$

The reported excess is $D-1$; a negative value is an under-dose. The dose factor is piecewise:

$$f_{dose}=\begin{cases}0.9D,&D<1\\1-\exp[-k(D-0.5)],&D\geq1\end{cases}$$

The kinetic constant $k$ is 1.8 for triazine, 1.5 for glyoxal, 1.2 for iron sponge,
2.5 for caustic, and 3.0 for liquid redox. The other implemented factors are

$$f_{contact}=1-\exp(-t/15\,\mathrm{s})$$

$$f_{mix}=0.5+0.5\,\epsilon_{mix}$$

and the temperature factor, with $T_C$ in degrees Celsius,

$$f_T=\begin{cases}0.7+0.03T_C,&T_C<10\\1,&10\leq T_C\leq80\\1-0.005(T_C-80),&T_C>80\end{cases}$$

The final removal fraction is

$$\eta=\operatorname{clamp}(\eta_{base}f_{dose}f_{contact}f_Tf_{mix},0,0.999)$$

These equations document the current screening implementation; they are not independent vendor
performance correlations. Pressure is accepted by the internal calculation interface but does not
currently add a correction factor. Revalidate the guide and regression if the implementation changes.

## Output Methods

### Concentration and Removal

| Method | Description | Units |
|--------|-------------|-------|
| `getInletH2SConcentration()` | Feed H2S content | ppm (molar) |
| `getOutletH2SConcentration()` | Treated gas H2S | ppm (molar) |
| `getH2SRemovalEfficiency()` | Removal fraction | 0-1 |
| `getH2SRemovalEfficiencyPercent()` | Removal percentage | % |
| `getH2SRemoved(unit)` | Mass of H2S removed | kg/hr, lb/hr, kg/day |

### Scavenger Consumption

| Method | Description | Units |
|--------|-------------|-------|
| `getActualScavengerConsumption(unit)` | Scavenger consumed reacting with H2S | `kg/hr`, `lb/hr` |
| `getScavengerExcess()` | Excess over stoichiometric | fraction |

### Cost Estimation

```java
// Calculate operating cost
double costPerGal = 7.0;  // $/gal for triazine
double hourlyCost = scavenger.calculateHourlyCost(costPerGal, "$/gal");
double dailyCost = hourlyCost * 24;
double annualCost = dailyCost * 365;
```

## JSON Output

The `toJson()` response uses the following literal field names. Numerical values below are
illustrative; calculate decisions from the configured case rather than this sample.

```json
{
  "name": "MEA-Triazine Injection",
  "scavengerType": "MEA-Triazine",
  "injectionRate": 50.0,
  "injectionRateUnit": "l/hr",
  "concentration": 0.5,
  "inletH2Sppm": 5208.3,
  "outletH2Sppm": 4560.0,
  "removalEfficiencyPercent": 12.4,
  "h2sRemovedKgPerHr": 4.1,
  "scavengerConsumptionKgPerHr": 18.4,
  "scavengerExcessPercent": -82.0
}
```

## Design Guidelines

### Scavenger Selection

| Condition | Recommended Type |
|-----------|------------------|
| General purpose, moderate H2S | TRIAZINE |
| Low temperature (< 20°C) | GLYOXAL |
| Batch operation, high efficiency | IRON_SPONGE |
| Very high H2S, continuous operation | LIQUID_REDOX |
| High pH tolerance required | CAUSTIC |

### Typical Operating Ranges

| Parameter | Typical Range |
|-----------|---------------|
| H2S inlet | 10 - 10,000 ppm |
| Gas flow | 1,000 - 500,000 Sm³/day |
| Injection rate | 5 - 500 l/hr |
| Contact time | 15 - 120 seconds |
| Temperature | 10 - 60°C |
| Pressure | 1 - 150 bara |

### Excess Ratio Guidelines

| Target | Recommended Excess |
|--------|-------------------|
| Normal operation | 20-50% excess |
| High reliability required | 50-100% excess |
| Upset conditions | 100-200% excess |

## Limitations

1. **Correlation-based**: Results are estimates based on empirical correlations, not rigorous reaction kinetics
2. **Single-phase assumption**: Assumes gas-phase H2S removal; does not model aqueous phase reactions in detail
3. **No regeneration**: Does not model scavenger regeneration (relevant for liquid redox systems)
4. **Temperature range**: Correlations are most accurate for 20-60°C range

## References

1. GPSA Engineering Data Book, 14th Edition, Section 21
2. Kohl, A.L. & Nielsen, R.B., "Gas Purification", 5th Edition, Gulf Publishing
3. Arnold, K. & Stewart, M., "Surface Production Operations", Vol. 2, Gulf Publishing
4. Nagl, G.J., "Controlling H2S Emissions", Chemical Engineering, 1997

## See Also

- [H2S Distribution Modeling Guide](../thermo/H2S_distribution_guide.md)
- [Absorber Equipment Guide](equipment/absorbers.md)
- [Process Index](index.md)
