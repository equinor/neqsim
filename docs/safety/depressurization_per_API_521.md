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

## Steady dry-gas capacity at a coincident load

Use `SteadyFlareGasNetwork` when several fixed-opening devices discharge a common dry gas into
one header. Supply absolute pressures in bara, open nozzle areas in m², gas molar mass in kg/mol
and simultaneous relief demands in kg/s. Represent each inlet and tailpipe by its inside diameter,
equivalent length including fittings, and Darcy friction factor. Supply the static pressure and a
rated flow/pressure point for a quadratic tip characteristic.

```java
import java.util.Collections;
import neqsim.process.safety.depressurization.SteadyFlareGasNetwork;
import neqsim.process.safety.depressurization.SteadyFlareGasNetwork.Pipe;

// Illustrative geometry, nozzle and gas properties for a single operating point
SteadyFlareGasNetwork network = new SteadyFlareGasNetwork("header",
    new Pipe(0.14, 95.0, 0.02), "tip", 1.2, 1.2, 2.0, 300.0, 0.020, 1.0, 1.3);
network.addDevice("PSV", 8.0, new Pipe(0.25, 0.0, 0.02), 0.0002, 0.95,
    new Pipe(0.10, 120.0, 0.02), 1.6);
SteadyFlareGasNetwork.Result rating = network.rate(Collections.singletonMap("PSV", 0.4));
double availableKgS = rating.devices.get("PSV").availableKgS;
double flangeBara = rating.devices.get("PSV").outletBara;
```

The solver balances each nozzle against its local inlet and discharge-flange pressures and balances
the combined flow at the header. For each horizontal, isothermal pipe, the pressure-squared change
in Pa² is `f (L/D) (mass flow / area)² ZRT/M`. A negative capacity margin gives the shortfall at
the specified opening. A negative outlet-pressure margin gives the exceedance of the supplied
device limit. Outlet rise is measured from the common zero-flow static pressure. The solver also reports a separate steady
pre-opening pressure for each device by holding that device closed with the other openings fixed.
Its incremental backpressure is the difference between the local outlet pressure and that baseline.
For other event sequences, supply the corresponding openings in a separate case. Represent a closed device with zero open area. An omitted limit yields a null margin in the result and its JSON export.

To rate a sampled blowdown peak, construct the network using source pressures and openings at
that instant, match each device tag to its source, and call `history.ratePeakGasCapacity(network)`.
The rate comparison uses the contributions at the common sampled peak. Other instants can govern
individual branches. The method assumes constant gas properties, low-Mach pipe flow and one common
junction. It rejects pipe outlet Mach above 0.3 and flows outside the rated tip range. Liquid
carryover, KO drum losses, multiple headers, line packing and transient valve lift require a separate
model and qualification.

### V-101 PSV-only reference at negligible losses

`AdvancedV101PsvStudyTest.reliefRefinesAndReplaysWithoutMutatingCanonicalSource` feeds the
native `PSV_RELIEF` outlet rate and its timestamps into `ratePeakGasCapacity` at both native
timesteps. The gas properties come from an SRK flash of the recorded vessel inventory at the
instant of peak flow. All line lengths are zero. The imposed tip curve is flat at 1.2 bara.

| Quantity | 0.10 s step | 0.05 s step |
|---|---:|---:|
| Native and transferred peak flow at 3.20 s, kg/s | 5.49552 | 5.49705 |
| Maximum protected vessel pressure, bara | 42.36369 at 2.40 s | 42.38967 at 2.45 s |
| Inlet flange pressure at peak flow, bara | 42.03573 | 42.04747 |
| Outlet flange, header and tip pressure, bara | 1.20000 | 1.20000 |
| Assumed water-equivalent nozzle capacity, kg/s | 11.71485 | 11.71866 |

The synthetic native valve has a rated Cv of 80. Converting its effective Cv at peak to
a water-equivalent CdA gives 1316.32 mm² in both runs. This assumed nozzle passes about 113%
more gas than the observed valve. Independent nozzle area or certified capacity evidence
would be needed to use this comparison for equipment sizing. The handoff recovers the
fixed outlet boundary and retains the sampled flow demand. It does not advance header
or knockout-drum inventory or return pressure to the native transient.

## See also

- [MDMT Assessment](mdmt_assessment.md)
- [Dispersion and Consequence Analysis](dispersion_and_consequence.md)
- [Fire Blowdown Capabilities](fire_blowdown_capabilities.md)
