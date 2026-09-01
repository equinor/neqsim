---
layout: default
title: "Reading Fluid Properties with Python"
description: "Executed NeqSim Python tutorial for thermodynamic, transport, phase, component, unit-conversion, interfacial, and JSON property workflows."
parent: Examples
nav_order: 1
---

> **Note:** This is an auto-generated Markdown version of the Jupyter notebook
> [`ReadingFluidProperties.ipynb`](https://github.com/equinor/neqsim/blob/master/docs/examples/ReadingFluidProperties.ipynb).
> You can also [view it on nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/ReadingFluidProperties.ipynb)
> or [open in Google Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/ReadingFluidProperties.ipynb).

---

## Reading Fluid Properties in NeqSim

This executable notebook shows how to read thermodynamic, transport, phase,
component, and interfacial properties through the public NeqSim Python gateway.
Values are model results for one illustrative SRK fluid; they are not reference
data or design guarantees.

The examples keep units explicit and include numerical consistency checks.
Constructors use kelvin and bar absolute (`bara`) unless an overload explicitly
accepts a unit.

```python
# Public-PyPI-compatible setup for a clean Google Colab runtime.
import importlib.util
import subprocess
import sys

if importlib.util.find_spec("neqsim") is None:
    subprocess.check_call(
        [sys.executable, "-m", "pip", "install", "--quiet", "neqsim"]
    )

import importlib.metadata
import json

import matplotlib.pyplot as plt
import pandas as pd
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ThermodynamicOperations = jneqsim.thermodynamicoperations.ThermodynamicOperations

print(f"NeqSim: {importlib.metadata.version('neqsim')}")
print(f"Python: {sys.version.split()[0]}")
```

<details>
<summary>Output</summary>

```
NeqSim: 3.17.0
Python: 3.12.13
```

</details>

## Create and flash a two-phase fluid

`addComponent(name, amount)` adds moles. Because these amounts sum to one mole,
they are also the overall mole fractions for this example. A TP flash establishes
the equilibrium phase count and compositions before properties are read.

```python
fluid = SystemSrkEos(298.15, 50.0)
component_amounts = {
    "nitrogen": 0.02,
    "CO2": 0.03,
    "methane": 0.70,
    "ethane": 0.10,
    "propane": 0.08,
    "n-butane": 0.04,
    "n-pentane": 0.02,
    "n-hexane": 0.01,
}

for component_name, amount_mol in component_amounts.items():
    fluid.addComponent(component_name, amount_mol)

fluid.setMixingRule("classic")
ThermodynamicOperations(fluid).TPflash()
fluid.initProperties()

assert fluid.getNumberOfPhases() == 2
assert fluid.hasPhaseType("gas")
assert fluid.hasPhaseType("oil")

print(f"Phases: {fluid.getNumberOfPhases()}")
print(f"Temperature: {fluid.getTemperature('C'):.2f} °C")
print(f"Pressure: {fluid.getPressure('bara'):.2f} bara")
```

<details>
<summary>Output</summary>

```
Phases: 2
Temperature: 25.00 °C
Pressure: 50.00 bara
```

</details>

## Initialization levels

| Call | Main result made available |
| --- | --- |
| `init(0)` | Composition bookkeeping |
| `init(1)` | Fugacity coefficients, compressibility, and equation-of-state volume |
| `init(2)` | Enthalpy, entropy, heat capacities, and related thermodynamic properties |
| `init(3)` | Composition derivatives used by advanced algorithms |
| `initPhysicalProperties()` | Transport properties and corrected-density property models |
| `initProperties()` | Convenience call for `init(2)` plus physical properties |

`initProperties()` is appropriate for this reporting example. It does not imply
that every optional specialized model, derivative, or phase-specific correlation
has been validated for every fluid.

## Overall and phase properties

Overall extensive properties depend on the amount of fluid in the object. The
table below therefore uses explicit molar or mass-specific units where possible.

```python
overall_properties = pd.DataFrame(
    [
        ("Molar mass", fluid.getMolarMass("gr/mol"), "g/mol"),
        ("Molar enthalpy", fluid.getEnthalpy("J/mol"), "J/mol"),
        ("Molar entropy", fluid.getEntropy("J/molK"), "J/(mol·K)"),
        ("Mass-specific Cp", fluid.getCp("kJ/kgK"), "kJ/(kg·K)"),
        ("Mass-specific Cv", fluid.getCv("kJ/kgK"), "kJ/(kg·K)"),
        ("Cp/Cv", fluid.getGamma(), "-"),
    ],
    columns=["Property", "Value", "Unit"],
)

assert overall_properties["Value"].map(lambda value: float(value)).notna().all()
overall_properties
```

<details>
<summary>Output</summary>

```
           Property        Value       Unit
0        Molar mass    24.275120      g/mol
1    Molar enthalpy -1611.716128      J/mol
2     Molar entropy   -26.540090  J/(mol·K)
3  Mass-specific Cp     2.485042  kJ/(kg·K)
4  Mass-specific Cv     1.637272  kJ/(kg·K)
5             Cp/Cv     1.517794          -
```

</details>

```python
phase_rows = []
for phase_index in range(fluid.getNumberOfPhases()):
    phase = fluid.getPhase(phase_index)
    phase_rows.append(
        {
            "Phase": str(phase.getType()),
            "Mole fraction": phase.getMoleFraction(),
            "Z": phase.getZ(),
            "Molar mass [g/mol]": phase.getMolarMass("gr/mol"),
            "Density [kg/m³]": phase.getDensity("kg/m3"),
            "Viscosity [cP]": phase.getViscosity("cP"),
            "Conductivity [W/(m·K)]": phase.getThermalConductivity("W/mK"),
            "Sound speed [m/s]": phase.getSoundSpeed(),
        }
    )

phase_properties = pd.DataFrame(phase_rows)
assert abs(phase_properties["Mole fraction"].sum() - 1.0) < 1.0e-10
assert (phase_properties["Density [kg/m³]"] > 0.0).all()
assert (phase_properties["Viscosity [cP]"] > 0.0).all()
phase_properties
```

<details>
<summary>Output</summary>

```
  Phase  Mole fraction  ...  Conductivity [W/(m·K)]  Sound speed [m/s]
0   GAS       0.922517  ...                0.035558         346.675386
1   OIL       0.077483  ...                0.093688         624.541749

[2 rows x 8 columns]
```

</details>

## Equation-of-state and corrected density

The no-argument `phase.getDensity()` reads the equation-of-state density. The
overload with an explicit unit reads the initialized physical-property density,
which applies the configured volume correction for this SRK fluid. The corrected
result is generally preferable for reporting, but it remains a model prediction
that should be validated against representative density data for engineering use.

```python
density_rows = []
for phase_index in range(fluid.getNumberOfPhases()):
    phase = fluid.getPhase(phase_index)
    eos_density = phase.getDensity()
    corrected_density = phase.getDensity("kg/m3")
    density_rows.append(
        {
            "Phase": str(phase.getType()),
            "EoS density [kg/m³]": eos_density,
            "Corrected density [kg/m³]": corrected_density,
            "Relative change [%]": 100.0
            * (corrected_density - eos_density)
            / eos_density,
        }
    )

density_comparison = pd.DataFrame(density_rows)
assert (density_comparison["Corrected density [kg/m³]"] > 0.0).all()
density_comparison
```

<details>
<summary>Output</summary>

```
  Phase  EoS density [kg/m³]  Corrected density [kg/m³]  Relative change [%]
0   GAS            53.662199                  53.870577             0.388315
1   OIL           479.969712                 515.117847             7.322990
```

</details>

## Component material-balance check

`getz()` is the overall mole fraction and `getx()` is the mole fraction in one
phase. For each component, the flashed state should satisfy
`z_i = Σ_α β_α x_i,α` within numerical tolerance.

```python
gas_phase = fluid.getPhase("gas")
oil_phase = fluid.getPhase("oil")
gas_fraction = gas_phase.getMoleFraction()
oil_fraction = oil_phase.getMoleFraction()

composition_rows = []
maximum_balance_error = 0.0
for component_index in range(fluid.getNumberOfComponents()):
    component_name = str(fluid.getComponent(component_index).getComponentName())
    overall_fraction = fluid.getComponent(component_index).getz()
    gas_composition = gas_phase.getComponent(component_index).getx()
    oil_composition = oil_phase.getComponent(component_index).getx()
    reconstructed_fraction = (
        gas_fraction * gas_composition + oil_fraction * oil_composition
    )
    balance_error = abs(reconstructed_fraction - overall_fraction)
    maximum_balance_error = max(maximum_balance_error, balance_error)
    composition_rows.append(
        {
            "Component": component_name,
            "z overall": overall_fraction,
            "y gas": gas_composition,
            "x oil": oil_composition,
            "K = y/x": gas_composition / oil_composition,
            "Balance error": balance_error,
        }
    )

composition_table = pd.DataFrame(composition_rows)
assert maximum_balance_error < 1.0e-10
print(f"Maximum component balance error: {maximum_balance_error:.3e}")
composition_table
```

<details>
<summary>Output</summary>

```
Maximum component balance error: 3.469e-18

   Component  z overall     y gas     x oil   K = y/x  Balance error
0   nitrogen       0.02  0.021474  0.002454  8.751849   0.000000e+00
1        CO2       0.03  0.031036  0.017666  1.756812   0.000000e+00
2    methane       0.70  0.741289  0.208407  3.556924   0.000000e+00
3     ethane       0.10  0.099369  0.107509  0.924288   0.000000e+00
4    propane       0.08  0.069830  0.201091  0.347253   0.000000e+00
5   n-butane       0.04  0.026506  0.200661  0.132094   0.000000e+00
6  n-pentane       0.02  0.008337  0.158855  0.052485   3.469447e-18
7   n-hexane       0.01  0.002159  0.103357  0.020887   1.734723e-18
```

</details>

```python
plot_table = composition_table.set_index("Component")[["y gas", "x oil"]]
axis = plot_table.plot.bar(figsize=(10, 4.8), width=0.8)
axis.set_ylabel("Mole fraction [-]")
axis.set_title("Equilibrium gas and oil compositions at 25 °C and 50 bara")
axis.grid(axis="y", alpha=0.3)
axis.legend(["Gas phase", "Oil phase"])
plt.xticks(rotation=35, ha="right")
plt.tight_layout()
plt.show()
```

The gas phase is methane-rich, while heavier hydrocarbons are enriched in the
oil phase. This is the expected volatility ordering for the stated SRK screening
case. The result should not be transferred to custody-transfer or reservoir-fluid
work without selecting and validating a thermodynamic model against suitable
measurements.

## Pure-component constants and explicit unit conversion

For the current component API, `getTC()` returns kelvin, `getPC()` returns bar
absolute, and `getMolarMass()` returns kg/mol. Explicit-unit system getters are
used below; changing the global `Units` display configuration does not convert
the no-argument system temperature and pressure getters.

```python
constant_rows = []
for component_index in range(fluid.getNumberOfComponents()):
    component = fluid.getComponent(component_index)
    constant_rows.append(
        {
            "Component": str(component.getComponentName()),
            "Tc [K]": component.getTC(),
            "Pc [bara]": component.getPC(),
            "Acentric factor [-]": component.getAcentricFactor(),
            "Molar mass [g/mol]": 1000.0 * component.getMolarMass(),
        }
    )

component_constants = pd.DataFrame(constant_rows)
assert (component_constants["Tc [K]"] > 0.0).all()
assert (component_constants["Pc [bara]"] > 0.0).all()
component_constants
```

<details>
<summary>Output</summary>

```
   Component  Tc [K]  Pc [bara]  Acentric factor [-]  Molar mass [g/mol]
0   nitrogen  126.10     33.944               0.0403             28.0135
1        CO2  304.19     73.815               0.2276             44.0100
2    methane  190.56     45.990               0.0115             16.0430
3     ethane  305.32     48.720               0.0995             30.0700
4    propane  369.83     42.480               0.1523             44.0970
5   n-butane  425.12     37.960               0.2002             58.1230
6  n-pentane  469.70     33.700               0.2515             72.1500
7   n-hexane  507.60     30.250               0.3013             86.1770
```

</details>

```python
unit_conversions = pd.DataFrame(
    [
        ("Pressure", fluid.getPressure("bara"), "bara"),
        ("Pressure", fluid.getPressure("Pa"), "Pa"),
        ("Pressure", fluid.getPressure("psia"), "psia"),
        ("Temperature", fluid.getTemperature("K"), "K"),
        ("Temperature", fluid.getTemperature("C"), "°C"),
        ("Temperature", fluid.getTemperature("F"), "°F"),
        ("Gas density", gas_phase.getDensity("lb/ft3"), "lb/ft³"),
        ("Gas viscosity", gas_phase.getViscosity("Pas"), "Pa·s"),
    ],
    columns=["Property", "Value", "Unit"],
)

assert abs(fluid.getPressure("Pa") - 5.0e6) < 1.0e-6
assert abs(fluid.getTemperature("F") - 77.0) < 1.0e-10
unit_conversions
```

<details>
<summary>Output</summary>

```
        Property         Value    Unit
0       Pressure  5.000000e+01    bara
1       Pressure  5.000000e+06      Pa
2       Pressure  7.251887e+02    psia
3    Temperature  2.981500e+02       K
4    Temperature  2.500000e+01      °C
5    Temperature  7.700000e+01      °F
6    Gas density  3.363030e+00  lb/ft³
7  Gas viscosity  1.260166e-05    Pa·s
```

</details>

## Interfacial tension and JSON output

Interfacial tension is meaningful only when both requested phases exist and the
selected interfacial-property model is applicable. The JSON helpers are useful
for reporting, but downstream code should treat their field names and units as
an explicit schema contract rather than infer units from a number.

```python
interfacial_tension_n_per_m = fluid.getInterfacialTension("gas", "oil")
assert interfacial_tension_n_per_m > 0.0
print(
    "Gas-oil interfacial tension: "
    f"{1000.0 * interfacial_tension_n_per_m:.4f} mN/m"
)

fluid_json = json.loads(str(fluid.toJson()))
component_json = json.loads(str(fluid.toCompJson()))
assert "properties" in fluid_json
assert "composition" in fluid_json
assert "properties" in component_json
print("Fluid JSON top-level keys:", sorted(fluid_json))
print("Component JSON entries:", len(component_json["properties"]))
```

<details>
<summary>Output</summary>

```
Gas-oil interfacial tension: 5.4182 mN/m
Fluid JSON top-level keys: ['composition', 'conditions', 'name', 'properties']
Component JSON entries: 8
```

</details>

## Interpretation checklist

1. Flash the state before reading phase-dependent properties.
2. Call the initialization level required by the property; use `initProperties()`
   for the thermodynamic and transport reporting shown here.
3. Request units explicitly. No-argument temperature and pressure getters remain
   kelvin and bara even if a global display-unit set is changed.
4. Check phase existence before accessing a named phase.
5. Verify component balances and physically plausible phase ordering.
6. Treat SRK densities, transport properties, interfacial tension, and phase split
   as model predictions requiring validation for the intended fluid and range.

Related current interfaces:

- [SystemInterface](https://github.com/equinor/neqsim/blob/master/src/main/java/neqsim/thermo/system/SystemInterface.java)
- [PhaseInterface](https://github.com/equinor/neqsim/blob/master/src/main/java/neqsim/thermo/phase/PhaseInterface.java)
- [ComponentInterface](https://github.com/equinor/neqsim/blob/master/src/main/java/neqsim/thermo/component/ComponentInterface.java)
- [Reading fluid properties](../thermo/reading_fluid_properties.md)

