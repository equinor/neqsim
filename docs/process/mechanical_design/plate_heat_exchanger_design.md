---
title: "Plate Heat Exchanger Design"
description: "Martin chevron correlations, channel and port pressure drop, counterflow effectiveness-NTU rating, fouling and independent frame/bolt capacity limits for gasketed plate heat exchangers."
---

# Plate heat exchanger design

`neqsim.process.mechanicaldesign.heatexchanger.PlateHeatExchangerDesignCalculator`
calculates film coefficients and overall U from plate geometry and supplied fluid
properties. It reports duty, outlet temperatures, channel and port pressure losses,
and frame expansion headroom. This complements the generic `PLATE_AND_FRAME`
area estimate in `HeatExchangerMechanicalDesign`; it does not change that estimate
or automatically select a plate exchanger for existing process equipment.

## Model and units

The calculator uses the **Martin 1999** friction and heat-transfer formulation.
Its returned friction factor is **Darcy**, four times the Fanning factor in
Martin's appendix. The original transition at Re = 2000 is preserved. The
correlation's documented experimental range is Re = 200–10000; rating remains
available outside it, with `isWithinCorrelationRange()` returning false.
Chevron angles must be from 10 through 80 degrees (the heat-transfer range), measured
relative to the main flow direction. Wall/bulk viscosity correction is unity.

For clear gap b, effective width W and surface enlargement factor phi:

- Hydraulic diameter = 2b/phi.
- Effective straight flow length = developed plate area/(W phi).
- Flow area per side = (channels/passes) b W.
- Reynolds number uses velocity through that flow area, not the port velocity.
- Channel loss = Darcy factor × passes × length/diameter × rho v²/2.
- Port loss = K × passes × rho v_port²/2, based on **total side flow**.

`setPortLossCoefficient` specifies the combined inlet/outlet coefficient per
pass; its illustrative default is 1.5. Port/manifold geometry must be checked
against vendor data. Turning losses, elevation and maldistribution are excluded.
`sizePortDiameter(massFlow, density, maximumVelocity)` gives a minimum circular
port bore from a user-specified velocity limit.

All geometry is in **m**, developed one-face plate area in **m²**, flow in
**kg/s**, viscosity in **Pa s**, density in **kg/m³**, Cp in **J/(kg K)**,
conductivity in **W/(m K)**, and inlet/outlet temperatures in **°C**.
Pressure losses are returned in **Pa**; duty in **W**; U in **W/(m² K)**.

The installed count includes both end plates. There are N−1 alternating fluid
channels and N−2 active plates; area is (N−2) times the area of one plate. An
extra channel with an even installed count belongs to the cold side. Channels
must divide exactly into the specified passes on each side.

## Representative glycol/seawater cooler

This example is covered by `PlateHeatExchangerDesignCalculatorTest`. It reproduces
the **thermal targets** of [issue #3763](https://github.com/equinor/neqsim/issues/3763)
within its stated 5% duty and 10% U tolerances. It is a representative rating, not
an independently validated reconstruction of the vendor cooler: the issue gives
no gap, width, angle, enlargement factor, plate thickness, conductivity, port
diameter or fluid transport-property data. The following missing inputs are
explicit engineering assumptions. They must be replaced with vendor geometry
and a validated property model for equipment decisions.

| Input | Hot side: 40 wt% TEG/water | Cold side: seawater |
|---|---:|---:|
| Mass flow, kg/h, from issue | 844230 | 765000 |
| Inlet temperature, °C, from issue | 37 | 10 |
| Assumed density, kg/m³ | 1055 | 1025 |
| Assumed dynamic viscosity, Pa s | 0.0025 | 0.00108 |
| Constant Cp, J/(kg K) | 3560 | 4020 |
| Assumed conductivity, W/(m K) | 0.45 | 0.60 |

The heat capacities approximately close the issue's stated duty and terminal
temperatures, so that energy-balance agreement is not independent property-model
validation. U is calculated from the correlations; the reference U is never an input.
The example assumes clean surfaces and no wall-viscosity correction.

```java
PlateHeatExchangerDesignCalculator calc = new PlateHeatExchangerDesignCalculator();
calc.setNumberOfPlates(721);
calc.setFrameCapacityPlates(869);
calc.setBoltedCapacityPlates(721);
calc.setPlateArea(1.49);
calc.setEffectiveFlowWidth(0.65);
calc.setPlateSpacing(0.003);
calc.setSurfaceEnlargementFactor(1.18);
calc.setChevronAngle(60.0);
calc.setPlateThickness(0.0006);
calc.setPlateConductivity(16.0);
calc.setHotPortDiameter(0.20);
calc.setColdPortDiameter(0.20);
calc.setPortLossCoefficient(1.5);
calc.setHotSide(844230.0 / 3600.0, 37.0, 1055.0, 0.0025, 3560.0, 0.45);
calc.setColdSide(765000.0 / 3600.0, 10.0, 1025.0, 0.00108, 4020.0, 0.60);

PlateHeatExchangerDesignCalculator.Rating rating = calc.calculate();
double dutyW = rating.getDuty();
double overallU = rating.getOverallU();
double hotOutletC = rating.getHotOutletTemperature();
double coldOutletC = rating.getColdOutletTemperature();
String report = calc.toJson();
```

Expected results (rounded): duty **17.64 MW**, U **2694 W/(m² K)**, outlets
**15.87 / 30.65 °C**, active area **1071.31 m²**. The issue quotes 1078.5 m²;
721 × 1.49 is 1074.29 m², so its plate count, rounded individual area and total
area are not exactly consistent even before excluding the two end plates.
This implementation retains its explicit N−2 active-plate convention.

For the assumed 0.20 m ports, predicted losses are approximately **84 / 67 kPa**.
The hot port velocity is about **7.1 m/s**; port loss is substantial. These are
plausibility results, not a pressure-drop validation against undisclosed vendor
port geometry. Increase the port diameter or use `sizePortDiameter` to assess
an independently specified velocity limit.

## Fouling and retrofit limits

The two sides independently accept existing `FoulingModel` instances. For example:

```java
FoulingModel seawater = FoulingModel.createCoolingWaterModel(0.0002, 720.0);
seawater.advanceTime(720.0);
calc.setColdFoulingModel(seawater);
PlateHeatExchangerDesignCalculator.Rating fouled = calc.calculate();
```

Resistance is read at the model's current operating time on every call, without
advancing time or changing conditions. The overall resistance is
1/h_hot + thickness/k_metal + 1/h_cold + Rf_hot + Rf_cold.
Both clean U and fouled U are reported. This represents thermal fouling resistance;
it does not reduce the channel gap or predict a fouling-related hydraulic blockage.
The cooling-water factory requires service-specific asymptotic resistance and time
constant; it is not a calibrated seawater biofouling model.

For 721 installed plates, frame capacity 869 and bolt capacity 721:

| Result | Value |
|---|---:|
| `getFrameSparePlates()` | 148 plates |
| `getFrameAdditionalArea()` | 220.52 m² |
| Active-area increase permitted by frame alone | 20.58% |
| `getAdditionalPlatesWithoutBoltReplacement()` | 0 plates |

Capacities default to zero, meaning **unknown**. Unknown headroom is null in the
JSON report and raises `IllegalStateException` through the headroom getters.
An installed count exceeding either known capacity is rejected. To assess filling
the frame, update the assumed bolt capacity as well as the installed count only
after establishing that the retrofit is mechanically possible.

Recalculate at every new plate count: more channels lower the velocity and film
coefficients. The area increase must not be used as a proportional increase in
UA or duty. Bolts, tie bars, gaskets, compression length, nozzle loads and design
pressure require a separate mechanical check; these capacity fields are limits
supplied by the caller, not stress calculations or retrofit approval.

## Process streams and limits

`setHotStream(StreamInterface)` and `setColdStream(StreamInterface)` snapshot
already flashed single-phase stream properties without changing the feed. The
calculator can therefore use named streams from a `ProcessSystem`. It does not
modify their outlets or pressures. Properties are frozen at the snapshot inlet
state; for large temperature changes use explicit representative mean-temperature
properties through the side setters. No salinity or glycol-composition model is
embedded in the calculator, and multiphase snapshots are rejected.

`calculate()` supports **single-pass counterflow** through a stable
effectiveness–NTU calculation. It handles equal heat-capacity rates, tiny inlet
temperature differences, equal inlet temperatures and zero flow without LMTD
division. Zero flow on either side gives zero duty, unchanged outlet temperatures
and zero overall conductance. Zero-flow results fall outside the correlation range.
The stream adapter rejects phase change at its input; explicit property inputs
require the caller to establish single-phase validity over the whole exchanger.

`setHotPasses` and `setColdPasses` support separate multipass hydraulic screening
through `calculateHydraulics(boolean hotSide)`. Multipass **thermal** rating is
explicitly rejected: pass counts do not specify the required parallel/counterflow
network, return arrangement and mixing. No silent single-pass approximation is made.

`Rating` and `ChannelResult` are immutable snapshots. Invalid subsequent inputs
raise an exception; no cached old rating is returned. JSON reports label units,
separate channel and port losses, and include both mechanical capacity limits.

## Verification and references

Tests check independent published numerical examples for the Darcy and Nusselt
correlations, both friction branches, energy conservation, port sizing and D⁻⁴
port-loss scaling, equal-capacity and zero-duty limits, fouling time ownership,
plate-count changes, serialization, stream snapshots, invalid input and
extrapolation reporting. The representative cooler is an assumption-based
acceptance example, not an experimental validation of TEG/seawater properties.

- [Martin (1999), appendix equations A–H](https://publikationen.bibliothek.kit.edu/1000034866/2579146).
- [Martin (1996), original chevron plate model](https://doi.org/10.1016/0255-2701(95)04129-X).
- [Fluids documentation: Martin 1999 friction](https://fluids.readthedocs.io/fluids.friction.html#fluids.friction.friction_plate_Martin_1999).
- [HT documentation: Martin Nusselt correlation](https://ht.readthedocs.io/en/release/ht.conv_plate.html#ht.conv_plate.Nu_plate_Martin).
- [General exchanger mechanical design](../../wiki/heat_exchanger_mechanical_design).
- [Shell-and-tube thermal-hydraulic design](thermal_hydraulic_design).
