---
name: neqsim-compressor-antisurge-recycle
version: "1.0.0"
description: "Set up anti-surge recycle control and coordinated minimum-speed recycle control for centrifugal compressors in NeqSim, including compressor performance chart generation, anti-surge calculation, MinimumSpeedRecycleControllerStructure use for coordinated pressure/speed/recycle split-range control, fuel gas savings, CO2 emission reductions, and monetary cost evaluations. USE WHEN: a task needs to protect a compressor from surge, coordinate speed and recycle valve control at minimum speed, eliminate uncoordinated recycle valve opening, or evaluate power and CO2 savings from control loop optimization."
last_verified: "2026-08-26"
requires:
  java_packages: [neqsim.process.equipment.compressor, neqsim.process.controllerdevice.structure, neqsim.process.controllerdevice]
---

# NeqSim Compressor Anti-Surge & Minimum-Speed Recycle Control Skill

This skill provides patterns for anti-surge protection, minimum-speed recycle coordination, and energy/CO2 emission evaluations for centrifugal compressor recycle loops.

## When to Use

- Coordinating compressor speed and recycle-valve opening at minimum speed (e.g. PEPR control optimization)
- Preventing opposing speed demand and recycle-valve opening in compressor pressure loops
- Sizing anti-surge recycle valves and modeling anti-surge controllers
- Calculating power penalties, fuel gas consumption, CO2 emission reductions, and financial cost savings ($CO_2$ tax + fuel gas value) from eliminating unnecessary recycle opening
- Running dynamic simulation of split-range pressure, speed, and recycle control using `MinimumSpeedRecycleControllerStructure`

Standards: **API 617 / API 619**, **IEC 60534 / ISA-75**, **ISO 50001 / ISO 14064** (energy & carbon emissions).

## Key Java Class: `MinimumSpeedRecycleControllerStructure`

`neqsim.process.controllerdevice.structure.MinimumSpeedRecycleControllerStructure` coordinates pressure control between compressor speed and recycle-valve opening when the compressor reaches its minimum speed.

### Core Features

1. **Split-Range Output Transition**: Pressure controller output is divided at a configurable transition (e.g., 75%).
2. **Speed Command Range**: Above transition (75% to 105%), recycle addition is zero and compressor speed increases linearly from minimum (75%) to maximum (100%).
3. **Inverse Recycle Addition**: Below transition (0% to 75%), speed is held at minimum (75%) while an inverse recycle addition increases as pressure output falls.
4. **Latch on Entry**: On lower-range entry, the previously selected recycle command is latched and pressure-derived recycle is added to that baseline.
5. **High Selector Protection**: Independent anti-surge and suction-pressure demands participate in a high selector ($R_{selected} = \max(R_{latch} + R_{add}, R_{AS}, R_{suction})$) so protection authority is never suppressed.
6. **Unwind before Speed Increase**: On rising pressure, pressure-derived recycle unwinds to the latched baseline before speed increases.
7. **Dynamic Saturation Floor**: Applies a lower output limit (`latched * 74 / 100`) to prevent integral windup.

### Java Usage Pattern

```java
import neqsim.process.controllerdevice.ControllerDeviceInterface;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.controllerdevice.structure.MinimumSpeedRecycleControllerStructure;

// Create controllers
ControllerDeviceInterface pressureController = new ControllerDeviceBaseClass("PIC-0205A");
ControllerDeviceInterface antiSurgeController = new ControllerDeviceBaseClass("UIC-0231");
ControllerDeviceInterface suctionController = new ControllerDeviceBaseClass("PIC-0131");

// Instantiate structure: (pressureCtrl, antiSurgeCtrl, suctionCtrl, transitionPct, maxOutputPct, minSpeedPct, maxSpeedPct)
MinimumSpeedRecycleControllerStructure coordStructure = new MinimumSpeedRecycleControllerStructure(
    pressureController, antiSurgeController, suctionController, 75.0, 105.0, 75.0, 100.0
);

// In transient loop or update step:
coordStructure.update(pressureControllerOutput, antiSurgeOutput, suctionPressureOutput);

double speedCommand = coordStructure.getSpeedOutput();             // % speed
double recycleAddition = coordStructure.getRecycleAddition();       // % recycle addition
double recycleCommand = coordStructure.getLatchedRecycleOutput();   // % latched baseline
```

## Energy & CO2 Savings Quantification Pattern

When recycle opens uncoordinatedly while the compressor is operating at minimum or elevated speed, extra gas is re-compressed without increasing net forward production.

### Governing Equations

1. **Compressor Shaft Power**:
   $$P_{shaft} = \frac{\dot{m}_{total} \cdot h_{poly}}{\eta_p} = \frac{\dot{m}_{net} \cdot h_{poly}}{\eta_p (1 - \alpha_{recycle})}$$
   where $\alpha_{recycle}$ is the recycle fraction ($0 \le \alpha < 1$).

2. **Power Penalty**:
   $$\Delta P = P_{shaft}(\alpha) - P_{shaft}(0) = P_0 \left(\frac{\alpha}{1 - \alpha}\right)$$

3. **Fuel Gas & CO2 Emission Penalty**:
   $$\Delta \dot{m}_{fuel} = \frac{\Delta P}{\eta_{driver} \cdot LHV} \quad (\text{kg/hr})$$
   $$\Delta \dot{m}_{CO2} = \Delta \dot{m}_{fuel} \cdot e_{CO2} \quad (\text{kg } CO_2/\text{hr})$$

4. **Financial Cost Savings**:
   $$\text{Annual Savings (MNOK/yr)} = \frac{\Delta m_{CO2, yr} \cdot \text{Tax}_{CO2} + \Delta m_{fuel, yr} \cdot \text{Value}_{gas}}{10^6}$$

### Python / NeqSim Process Simulation Recipe

```python
from neqsim import jneqsim

# 1. Fluid Creation
fluid = jneqsim.thermo.system.SystemSrkEos(311.15, 12.5) # 38 °C, 12.5 bara
fluid.addComponent("methane", 0.78)
fluid.addComponent("ethane", 0.085)
fluid.addComponent("propane", 0.045)
fluid.addComponent("CO2", 0.040)
fluid.setMixingRule("classic")

# 2. Recompressor Model
stream = jneqsim.process.equipment.stream.Stream("Suction Gas", fluid)
stream.setFlowRate(150000.0, "kg/hr") # 150 t/h net flow
stream.run()

comp = jneqsim.process.equipment.compressor.Compressor("Recompressor", stream)
comp.setOutletPressure(45.0, "bara")
comp.setPolytropicEfficiency(0.78)
comp.setUsePolytropicCalc(True)
comp.run()

base_power_kw = comp.getPower() / 1000.0

# 3. Recycle Sweep
for r_frac in [0.0, 0.10, 0.20, 0.30]:
    total_flow = 150000.0 / (1.0 - r_frac)
    stream.setFlowRate(total_flow, "kg/hr")
    stream.run()
    comp.run()
    p_kw = comp.getPower() / 1000.0
    extra_kw = p_kw - base_power_kw
    extra_co2_t_hr = (extra_kw / (0.34 * 48000.0)) * 3600.0 * 2.75 / 1000.0
    print(f"Recycle {r_frac*100:.0f}%: Power = {p_kw:.1f} kW (+{extra_kw:.1f} kW), Extra CO2 = +{extra_co2_t_hr:.2f} t/h")
```

## Checklist for PEPR & Control Optimization Tasks

- [ ] Verify fluid composition, suction temperature, and pressure basis
- [ ] Confirm compressor polytropic efficiency, pressure ratio, and head
- [ ] Model net forward flow vs recycle flow
- [ ] Calculate shaft power, driver fuel gas rate, and CO2 emissions rate
- [ ] Benchmark split-range mapping with `MinimumSpeedRecycleControllerStructure`
- [ ] Quantify annual MWh, fuel gas tonnes, CO2 tonnes, and MNOK cost savings across operating hours
- [ ] Generate dynamic time-series comparison plots (Pressure, Speed, Recycle, Power & CO2)
