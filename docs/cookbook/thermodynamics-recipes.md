---
title: Thermodynamics Recipes
description: Copy-paste NeqSim recipes for fluids, flash calculations, properties, phase envelopes, model selection, and JSON export.
---

Copy-paste solutions for common thermodynamic tasks. Every code block defines its own fluid and
can run independently in Python after `pip install neqsim`.

## Table of Contents

- [Creating Fluids](#creating-fluids)
- [Flash Calculations](#flash-calculations)
- [Reading Properties](#reading-properties)
- [Phase Envelopes](#phase-envelopes)
- [Choosing an EoS](#which-eos-should-i-use)
- [Export, Parse, and Clone](#export-parse-and-clone)

## Creating Fluids

### Create Natural Gas

```python
from neqsim import jneqsim

fluid = jneqsim.thermo.system.SystemSrkEos(298.15, 50.0)
fluid.addComponent("nitrogen", 0.02)
fluid.addComponent("CO2", 0.01)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.06)
fluid.addComponent("propane", 0.03)
fluid.addComponent("n-butane", 0.02)
fluid.addComponent("n-pentane", 0.01)
fluid.setMixingRule("classic")
```

The constructor uses kelvin and bara. Component amounts are relative mole amounts; they do not
need to sum to one. Set the mixing rule after all components are added.

### Create Oil with a Characterized Plus Fraction

```python
from neqsim import jneqsim

fluid = jneqsim.thermo.system.SystemPrEos(333.15, 200.0)
fluid.addComponent("nitrogen", 0.5)
fluid.addComponent("CO2", 2.0)
fluid.addComponent("methane", 45.0)
fluid.addComponent("ethane", 8.0)
fluid.addComponent("propane", 5.0)
fluid.addComponent("n-butane", 3.0)
fluid.addComponent("n-pentane", 2.0)
fluid.addComponent("n-hexane", 2.0)

# Arguments: relative mole amount, molar mass (kg/mol), density (g/cm3).
# Use a terminal numeric label before default Pedersen characterization.
fluid.addPlusFraction("C7", 32.5, 0.220, 0.82)
fluid.getCharacterization().getLumpingModel().setNumberOfPseudoComponents(12)
fluid.getCharacterization().characterisePlusFraction()
fluid.setMixingRule("classic")
fluid.setMultiPhaseCheck(True)
```

`addTBPfraction(...)` creates one specified pseudo-component. Use `addPlusFraction(...)` followed
by `characterisePlusFraction()` when a residual fraction is to be split and lumped. The current
default Pedersen parser expects a numeric terminal carbon label such as `C7`, not `C7+`.

### Create a CO₂-Water Fluid with CPA

```python
from neqsim import jneqsim

fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(298.15, 100.0)
fluid.addComponent("CO2", 0.95)
fluid.addComponent("methane", 0.03)
fluid.addComponent("water", 0.02)
fluid.setMixingRule(10)
```

CPA is useful when association, especially water, matters. Validate the selected model and binary
interaction parameters against data for the composition and conditions of interest.

## Flash Calculations

This complete example runs TP, constant-enthalpy PH, and constant-entropy PS flashes from the same
initial natural-gas state. Enthalpy and entropy passed to `PHflash` and `PSflash` are total-system
values in J and J/K.

```python
from neqsim import jneqsim

def make_fluid():
    gas = jneqsim.thermo.system.SystemSrkEos(298.15, 50.0)
    gas.addComponent("nitrogen", 0.02)
    gas.addComponent("CO2", 0.01)
    gas.addComponent("methane", 0.85)
    gas.addComponent("ethane", 0.06)
    gas.addComponent("propane", 0.03)
    gas.addComponent("n-butane", 0.02)
    gas.addComponent("n-pentane", 0.01)
    gas.setMixingRule("classic")
    return gas

tp_fluid = make_fluid()
tp_ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(tp_fluid)
tp_ops.TPflash()
tp_fluid.initProperties()
print(f"TP phases: {tp_fluid.getNumberOfPhases()}")

ph_fluid = make_fluid()
ph_ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(ph_fluid)
ph_ops.TPflash()
ph_fluid.initProperties()
initial_enthalpy_j = ph_fluid.getEnthalpy("J")
ph_fluid.setPressure(30.0, "bara")
ph_ops.PHflash(initial_enthalpy_j)
print(f"PH temperature: {ph_fluid.getTemperature() - 273.15:.2f} °C")

ps_fluid = make_fluid()
ps_ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(ps_fluid)
ps_ops.TPflash()
ps_fluid.initProperties()
initial_entropy_j_per_k = ps_fluid.getEntropy("J/K")
ps_fluid.setPressure(20.0, "bara")
ps_ops.PSflash(initial_entropy_j_per_k)
print(f"PS temperature: {ps_fluid.getTemperature() - 273.15:.2f} °C")
```

## Reading Properties

Run a flash and `initProperties()` before reading physical properties. The no-argument
`getDensity()` is based on the EoS phase volumes without Peneloux correction.
`getDensity("kg/m3")` uses initialized physical-property densities and corrected volume fractions.

```python
from neqsim import jneqsim

fluid = jneqsim.thermo.system.SystemSrkEos(298.15, 50.0)
fluid.addComponent("nitrogen", 0.02)
fluid.addComponent("CO2", 0.01)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.06)
fluid.addComponent("propane", 0.03)
fluid.addComponent("n-butane", 0.02)
fluid.addComponent("n-pentane", 0.01)
fluid.setMixingRule("classic")

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()
fluid.initProperties()

print(f"Temperature: {fluid.getTemperature() - 273.15:.2f} °C")
print(f"Pressure: {fluid.getPressure():.2f} bara")
print(f"EoS density: {fluid.getDensity():.2f} kg/m³")
print(f"Corrected density: {fluid.getDensity('kg/m3'):.2f} kg/m³")
print(f"Molar mass: {fluid.getMolarMass('g/mol'):.2f} g/mol")
print(f"Z-factor: {fluid.getZ():.4f}")
print(f"Enthalpy: {fluid.getEnthalpy('kJ/kg'):.2f} kJ/kg")
print(f"Entropy: {fluid.getEntropy('J/kgK'):.2f} J/(kg·K)")
print(f"Cp: {fluid.getCp('kJ/kgK'):.4f} kJ/(kg·K)")
print(f"Cv: {fluid.getCv('kJ/kgK'):.4f} kJ/(kg·K)")
print(f"Sound speed: {fluid.getSoundSpeed('m/s'):.2f} m/s")
print(f"Viscosity: {fluid.getViscosity('cP'):.4f} cP")
print(
    "Thermal conductivity: "
    f"{fluid.getThermalConductivity('W/mK'):.4f} W/(m·K)"
)

if fluid.hasPhaseType("gas"):
    gas = fluid.getPhase("gas")
    print(f"Gas density: {gas.getDensity('kg/m3'):.2f} kg/m³")
    print(f"Gas viscosity: {gas.getViscosity('cP'):.4f} cP")
    print(f"Gas mole fraction: {gas.getBeta():.4f}")

for component_index in range(fluid.getNumberOfComponents()):
    component = fluid.getComponent(component_index)
    name = str(component.getComponentName())
    print(
        f"{name}: z={component.getz():.4f}, "
        f"Tc={component.getTC():.1f} K, "
        f"Pc={component.getPC():.1f} bara"
    )
```

Bulk properties of a multiphase system are mixture-level quantities. Read the named phase when a
phase-specific value is required.

## Phase Envelopes

The named arrays contain temperature in K and pressure in bara. The cricondenbar and
cricondentherm arrays are ordered `[temperature, pressure, ...]`.

```python
from neqsim import jneqsim
import matplotlib.pyplot as plt

fluid = jneqsim.thermo.system.SystemSrkEos(298.15, 50.0)
fluid.addComponent("methane", 0.80)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)
fluid.addComponent("n-butane", 0.05)
fluid.setMixingRule("classic")

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.calcPTphaseEnvelope()

dew_temperature_c = [value - 273.15 for value in list(ops.get("dewT"))]
dew_pressure_bara = list(ops.get("dewP"))
bubble_temperature_c = [value - 273.15 for value in list(ops.get("bubT"))]
bubble_pressure_bara = list(ops.get("bubP"))

cricondenbar = list(ops.get("cricondenbar"))
cricondentherm = list(ops.get("cricondentherm"))
print(
    f"Cricondenbar: {cricondenbar[1]:.2f} bara at "
    f"{cricondenbar[0] - 273.15:.2f} °C"
)
print(
    f"Cricondentherm: {cricondentherm[0] - 273.15:.2f} °C at "
    f"{cricondentherm[1]:.2f} bara"
)

plt.figure(figsize=(9, 5))
plt.plot(dew_temperature_c, dew_pressure_bara, label="Dew line")
plt.plot(bubble_temperature_c, bubble_pressure_bara, label="Bubble line")
plt.xlabel("Temperature (°C)")
plt.ylabel("Pressure (bara)")
plt.title("Pressure-temperature phase envelope")
plt.grid(True)
plt.legend()
plt.show()
```

Near critical points and for strongly associating or highly asymmetric mixtures, check convergence
and compare with a validated fluid model or laboratory data.

## Which EoS Should I Use?

| Fluid or task | Starting model | Important qualification |
|---|---|---|
| Dry natural gas | SRK or PR | Validate density and calorific properties for the composition |
| Gas condensate | PR or SRK | Tune heavy-end characterization to PVT data |
| Black or volatile oil | PR or SRK | Characterize and tune C7+ before process studies |
| Dry CO₂-rich fluid | PR, SRK, or a validated multiparameter model | Check impurities and phase-boundary range |
| CO₂ with water | CPA | Validate water content, mutual solubility, and BIPs |
| Natural-gas reference properties | GERG-2008 | Use only supported components and validity ranges |
| LNG | GERG-2008 or PR | Validate cryogenic liquid density and phase equilibrium |
| Electrolytes and brines | Electrolyte-CPA | Define ions, salinity basis, and precipitation scope |

Selection depends on fluid chemistry, properties of interest, conditions, available tuning data, and
required uncertainty—not only the fluid label.

```python
from neqsim import jneqsim

temperature_k = 288.15
pressure_bara = 70.0

srk_fluid = jneqsim.thermo.system.SystemSrkEos(
    temperature_k,
    pressure_bara,
)
pr_fluid = jneqsim.thermo.system.SystemPrEos(
    temperature_k,
    pressure_bara,
)
cpa_fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(
    temperature_k,
    pressure_bara,
)
gerg_fluid = jneqsim.thermo.system.SystemGERG2008Eos(
    temperature_k,
    pressure_bara,
)
```

## Export, Parse, and Clone

`toJson()` returns a nested response with `conditions`, `properties`, and `composition`. Values are
serialized as objects containing `value` and `unit`; convert numeric strings explicitly.

```python
import json
from neqsim import jneqsim

fluid = jneqsim.thermo.system.SystemSrkEos(298.15, 50.0)
fluid.addComponent("methane", 0.90)
fluid.addComponent("ethane", 0.07)
fluid.addComponent("propane", 0.03)
fluid.setMixingRule("classic")

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()
fluid.initProperties()

json_text = str(fluid.toJson())
data = json.loads(json_text)
temperature = data["conditions"]["overall"]["temperature"]
print(f"Temperature: {float(temperature['value']):.2f} {temperature['unit']}")

fluid_copy = fluid.clone()
fluid_copy.setTemperature(300.0)
print(f"Original: {fluid.getTemperature():.2f} K")
print(f"Copy: {fluid_copy.getTemperature():.2f} K")
```

The clone is independent of the original, but recalculate its equilibrium and properties after
changing temperature, pressure, composition, or model settings.

## See Also

- **[Reading Fluid Properties Guide](../thermo/reading_fluid_properties)** - Property access and initialization
- **[Thermodynamic Models](../thermo/thermodynamic_models)** - Model theory and selection
- **[Phase Envelope Guide](../pvtsimulation/phase_envelope_guide)** - Algorithms, results, and troubleshooting
- **[JavaDoc: SystemInterface](https://equinor.github.io/neqsimhome/javadoc/site/apidocs/neqsim/thermo/system/SystemInterface.html)** - Complete API
