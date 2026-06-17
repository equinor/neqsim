---
title: "Depressurization (Blowdown) per API 521"
description: "Transient blowdown simulation in NeqSim using DepressurizationSimulator — VU-flash isenthalpic vessel inventory, fire heat input per API 521, BDV sizing, and minimum-temperature reporting for MDMT input."
---

# Depressurization (Blowdown) per API 521

NeqSim provides `neqsim.process.safety.depressurization.DepressurizationSimulator`
for transient vessel blowdown analysis per API STD 521 (7th ed., 2020) §5.20.

## Method

The simulator integrates a vessel as a constant-volume control mass, performing a
VU-flash (constant volume / internal energy) at each step:

$$
\frac{dM}{dt} = -\dot{m}_{out}, \qquad \frac{dU}{dt} = \dot{Q}_{fire} - \dot{m}_{out} h_{out}
$$

Fire heat input follows API 521 §4.4.13:

$$
Q_{fire} = C_1 \cdot F \cdot A_{wetted}^{0.82}
$$

with $C_1 = 43.2$ kW/m² (adequate drainage + prompt firefighting; otherwise
$C_1 = 70.9$).

## Code pattern

```java
SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 100.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.setMixingRule("classic");
fluid.setTotalNumberOfMoles(5000.0);  // initial inventory
fluid.init(3);

DepressurizationSimulator sim = new DepressurizationSimulator(fluid);
sim.setVesselVolume(50.0);          // m³
sim.setOrificeArea(0.001);          // m² (BDV throat)
sim.setBackPressure(1.5);           // bara
sim.setHeatInput(150.0);            // kW (API 521 fire case)
sim.run(900.0, 1.0);                // 15 min, 1 s steps

double tHalf = sim.timeToPressure(50.0);  // s to 50 bara
double Tmin  = sim.minTemperatureC();     // for MDMT check
double mdotMax = sim.peakMassFlow();      // for flare network sizing
```

## API 521 acceptance criteria

| Criterion | Limit | Source |
|-----------|-------|--------|
| Time to ≤ 50 % design pressure | ≤ 15 min | API 521 §5.20.2 |
| Minimum metal temperature | ≥ MDMT (UCS-66) | API 521 §5.20.3 |
| BDV downstream pressure | ≤ MAWP of flare header | API 521 §5.20.5 |

## See also

- [MDMT Assessment](mdmt_assessment.md)
- [Dispersion and Consequence Analysis](dispersion_and_consequence.md)
- [Fire Blowdown Capabilities](fire_blowdown_capabilities.md)
