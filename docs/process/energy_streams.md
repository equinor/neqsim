---
title: Energy streams and equipment ports
description: Connect heat, shaft-work, and electrical duties between NeqSim unit operations.
---

`EnergyStream` carries a power rate between process equipment. Its canonical unit is watt (W), while unit-aware APIs accept `W`, `kW`, `MW`, `hp`, and `BTU/hr`.

Typed `EnergyPort` metadata separates three concepts:

- `EnergyType`: `HEAT`, `SHAFT_WORK`, `ELECTRICAL`, `CHEMICAL`, or legacy `UNSPECIFIED`.
- `EnergyPortDirection`: physical flow relative to the equipment boundary.
- `EnergyPortMode`: whether the equipment calculates the duty, reads it as a specification, or leaves it to a balance solver.

Calculation mode controls process-graph ordering. An equipment port in `CALCULATED` mode is scheduled before a unit reading the same stream through a `SPECIFICATION` port, even if units were added to the process in the opposite order.

## Energy-driven pump

A connected shaft-work stream makes `Pump` calculate its outlet pressure from available shaft power, inlet volumetric flow, and efficiency:

```java
SystemInterface water = new SystemSrkEos(298.15, 2.0);
water.addComponent("water", 1.0);
water.setMixingRule("classic");

Stream feed = new Stream("pump feed", water);
feed.setFlowRate(100000.0, "kg/hr");
feed.run();

EnergyStream shaft = new EnergyStream("pump shaft", EnergyType.SHAFT_WORK);
shaft.setPower(100.0, "kW");

Pump pump = new Pump("energy-driven pump", feed);
pump.setIsentropicEfficiency(0.75);
pump.setEnergyStream(shaft);
pump.run();

double outletPressure = pump.getOutletStream().getPressure("bara");
EnergyPortMode mode = pump.getEnergyPort("shaftPower").getMode();
```

Without an externally connected energy stream, `Pump` keeps its existing pressure-specified behavior and publishes the calculated shaft duty through `getEnergyStream()`.

## Multi-party buses and shafts

Use `EnergyBus` when several producers or consumers share a heat or electrical network. Named contributions are signed: positive values inject power and negative values withdraw power.

```java
EnergyBus grid = new EnergyBus("main electrical bus", EnergyType.ELECTRICAL);
grid.setContribution("solar", 2.0, "MW");
grid.setContribution("electrolyzer", -1.5, "MW");
double reserve = grid.getNetPower("kW");
```

`MechanicalShaft` is a shaft-work bus with convenience methods for generation and loads:

```java
MechanicalShaft shaftTrain = new MechanicalShaft("expander-compressor shaft");
shaftTrain.setMechanicalEfficiency(0.98);
shaftTrain.setGeneratedPower("expander", 10.0e6);
shaftTrain.setConsumedPower("compressor", 8.0e6);
double sparePower = shaftTrain.getNetPower("MW");
```

Point-to-point `EnergyStream` connections reject multiple calculated producers or specification consumers during graph construction. Use `EnergyBus` for intentional multi-party distribution.

## Coupled networks and repeated execution

When a specification consumer is connected to an `EnergyBus`, it reads the net power excluding its own contribution from the previous run. After calculation, supported consumers publish their actual withdrawal back to the bus. This makes repeated steady-state runs stable instead of progressively subtracting the same load.

`getNetPowerExcluding(String)` exposes the same balance operation for custom dispatch logic. Input and output ports convert equipment power to negative withdrawals and positive injections. A bidirectional specification port treats positive equipment duty as a bus withdrawal, which supports recovered-heat links such as condenser to heater.

The process graph orders calculated producers before specification consumers. This applies to coupled networks such as:

- expander → `MechanicalShaft` → compressor or pump
- solar or other generation → electrical `EnergyBus` → electrolyzer
- condenser heat output → heat-recovery `EnergyBus` → heater

These connections remain stable when the flowsheet is executed repeatedly, and Java serialization preserves shared bus identity, port metadata, and named contributions.

## Deterministic allocation and balancing

For a state-of-the-art multi-party network, ports publish offers or requests and the bus solves one deterministic allocation. Lower priority numbers are served first; equal-priority participants share available power proportionally.

```java
EnergyBus allocatedGrid = new EnergyBus("allocated grid", EnergyType.ELECTRICAL);

EnergyPort generator = new EnergyPort("power", EnergyType.ELECTRICAL,
    EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED);
generator.setOwnerName("generator");
generator.connect(allocatedGrid);
generator.setDuty(100.0, "kW");

EnergyPort essentialLoad = new EnergyPort("power", EnergyType.ELECTRICAL,
    EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION);
essentialLoad.setOwnerName("essential load");
essentialLoad.setPriority(10);
essentialLoad.setRequestedPower(80.0, "kW");
essentialLoad.connect(allocatedGrid);

EnergyPort flexibleLoad = new EnergyPort("power", EnergyType.ELECTRICAL,
    EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION);
flexibleLoad.setOwnerName("flexible load");
flexibleLoad.setPriority(20);
flexibleLoad.setRequestedPower(80.0, "kW");
flexibleLoad.connect(allocatedGrid);

EnergyNetworkReport allocation = allocatedGrid.solveBalance();
double essentialAllocation = essentialLoad.getPowerMagnitude("kW"); // 80 kW
double flexibleAllocation = flexibleLoad.getPowerMagnitude("kW");   // 20 kW
double unmetDemand = allocation.getUnmetDemand();                   // 60000 W
```

A `BALANCE` port can inject power during shortage and absorb power during surplus. Configure its generation and consumption limits with `setBalanceLimits`. `BatteryStorage.enableAutomaticBalancing` provides this behavior with state-of-charge, charge/discharge efficiency, power limits, ramp response, and trip handling.

When a bus is part of a graph-executed process, add an `EnergyNetworkSolver` to make allocation an explicit scheduling node:

```java
EnergyNetworkSolver network = new EnergyNetworkSolver("electrical allocation", allocatedGrid);
process.add(network);
```

The graph schedules calculated participants before the solver and specification or balance participants after it.

## Coupled process-energy convergence

A single graph-ordered run gives the correct causal sequence, but an energy-limited consumer can publish a revised request after the network has already been solved. Use `CoupledProcessEnergySolver` to repeat the complete process until stream pressure, temperature, mass flow, energy requests, allocations, shortages, curtailment, and losses stop changing.

```java
CoupledProcessEnergySolver coupledSolver = new CoupledProcessEnergySolver(process);
coupledSolver.setMaximumIterations(50);
coupledSolver.setProcessTolerance(1.0e-6);
coupledSolver.setPowerTolerance(1.0e3); // 1 kW
coupledSolver.setRelaxationFactor(0.5);

CoupledProcessEnergyResult result = coupledSolver.solve();
if (!result.isConverged()) {
  throw new IllegalStateException(result.toJson());
}
```

The relaxation factor is applied only to `SPECIFICATION` requests between complete process runs. A value of one disables damping; values below one stabilize oscillating feedback such as available motor power changing compressor operation, which then changes the next compressor-power request. The result contains iteration-by-iteration process and power residuals plus immutable reports from the final energy-network solution.

## Conversion equipment and rotating drives

The `neqsim.process.equipment.energy` package provides process units with explicit input, useful-output, and heat-loss ports:

| Equipment | Conversion |
|---|---|
| `ElectricMotor` | electrical → shaft work |
| `Generator` | shaft work → electricity |
| `Gearbox` | shaft work → shaft work, with speed ratio |
| `Inverter` | electrical → electrical, with voltage/frequency quality |
| `Transformer` | electrical → electrical, with voltage ratio |
| `PrimeMover` | chemical/fuel energy → shaft work |

`MotorDriveTrain` connects an electric motor to any pump, compressor, or other unit exposing `shaftPower`. `MotorAssistedDriveTrain` connects an expander, an assist motor, and a compressor to the same `MechanicalShaft`. The two network solvers then dispatch electrical supply to the motor and combined shaft supply to the compressor.

## Energy quality and utility levels

`EnergyQuality` adds voltage, frequency, temperature, pressure, and shaft-speed metadata. Ports may declare required quality, and incompatible specified qualities are rejected during connection.

`UtilityEnergyBus` represents typed thermal utilities:

- high-, medium-, and low-pressure steam
- hot oil
- cooling and chilled water
- refrigeration
- ambient cooling

`ThermalUtilitySource` and `ThermalUtilityConsumer` participate in the same allocation, shortage, cost, and emissions reporting as electrical and shaft networks. Heaters, coolers, condensers, reboilers, two-stream heat exchangers, multi-stream exchangers, and `LNGHeatExchanger` publish or consume typed heat duties.

### Thermodynamic utility mass flow

Configure explicit supply and return states when the utility network must report physical circulation rather than only thermal power. Specific enthalpy is supplied in J/kg, allowing the values to come from NeqSim, vendor data, or another qualified property package.

```java
ThermalUtilityState steamSupply =
    new ThermalUtilityState(425.0, 4.0e5, 2.8e6);
ThermalUtilityState condensateReturn =
    new ThermalUtilityState(383.0, 4.0e5, 0.6e6);

UtilityEnergyBus lpSteam = new UtilityEnergyBus(
    "LP steam", UtilityLevel.LOW_PRESSURE_STEAM,
    steamSupply, condensateReturn);

ThermalUtilitySource boiler =
    new ThermalUtilitySource("boiler", UtilityLevel.LOW_PRESSURE_STEAM);
ThermalUtilityConsumer reboiler =
    new ThermalUtilityConsumer("reboiler", UtilityLevel.LOW_PRESSURE_STEAM);
boiler.connectEnergyStream(ThermalUtilitySource.OUTPUT_PORT,
    lpSteam, EnergyPortMode.CALCULATED);
reboiler.connectEnergyStream(ThermalUtilityConsumer.INPUT_PORT,
    lpSteam, EnergyPortMode.SPECIFICATION);
boiler.setAvailablePower(2.0e6);
reboiler.setRequestedPower(1.5e6);

boiler.run();
lpSteam.solveBalance();
reboiler.run();

double servedSteamKgPerSecond = lpSteam.getServedMassFlow();
double curtailedSteamKgPerSecond = lpSteam.getCurtailedMassFlow();
double servedSteamTonPerHour = lpSteam.getMassFlowForDuty(
    lpSteam.getLastReport().getServedDemand(), "W", "ton/hr");
```

Heating utilities require supply enthalpy above return enthalpy. Cooling-water, chilled-water, refrigeration, and ambient-cooling utilities require return enthalpy above supply enthalpy because they absorb process heat. The solved bus exposes requested, served, offered, accepted, unmet, and curtailed mass-flow equivalents.

## Dynamics and reporting

`MechanicalShaft.advanceTransient(dt)` integrates rotational kinetic energy from the solved net shaft power. Moment of inertia, friction loss, maximum speed, acceleration/deceleration limits, and trip coastdown are configurable.

Every solved bus returns an `EnergyNetworkReport` containing offered and accepted supply, requested and served demand, balancing generation/consumption, unmet demand, curtailment, conversion loss, delivery efficiency, fuel-energy rate, operating cost, and CO2-equivalent rate. Set marginal price and emission factor on producer ports with `setEnergyPricePerMWh` and `setEmissionFactorKgPerMWh`.

## Equipment coverage

| Equipment group | Typed port | Supported role |
|---|---|---|
| Pump, Compressor | `shaftPower` input | Calculated duty or external power specification |
| Expander, SteamTurbine | `shaftPower` output | Calculated shaft power |
| GasTurbine | `shaftPower` and `exhaustHeat` outputs | Calculated power/heat; shaft output can also be a fuel-sizing specification |
| GasTurbineUnit | `shaftPower` output | Calculated delivered shaft power at the active demand and site limit |
| CombinedCycleSystem | `electricalPower` output | Calculated combined-cycle generation |
| Heater, Cooler | `heatDuty` bidirectional | Calculated duty or legacy external duty specification |
| Condenser | `heatDuty` output | Calculated heat removal |
| Reboiler | `heatDuty` input | Calculated duty or legacy external duty specification |
| SolarPanel, WindTurbine, WindFarm, FuelCell | `electricalPower` output | Calculated generation |
| BatteryStorage | `electricalPower` bidirectional | Calculated charge/discharge or automatic balance |
| Electrolyzer | `electricalPower` input | Feed-calculated demand or connected power-driven specification |
| CO2Electrolyzer, BioFeedstockPreparation | `electricalPower` input | Calculated demand |
| AmmoniaSynthesisReactor | `reactionHeat` output | Calculated reaction heat |
| StirredTankReactor | `heatDuty` bidirectional; `agitatorPower` input | Calculated or specified heat duty and calculated electrical demand |
| HeatExchanger, MultiStreamHeatExchanger, LNGHeatExchanger | `heatDuty` bidirectional | Calculated recoverable heat |
| Energy converters | `energyInput`, `energyOutput`, `heatLoss` | Specified input, calculated useful output and loss |

## Reboiler duty reporting

A `Reboiler` now publishes its calculated heat duty through `getEnergyStream()`. The stream is typed as `HEAT`, and `getDuty("kW")` or `getEnergyFlow("MW")` can be used for utility summaries and downstream coupling.

## Compatibility

Existing `getDuty()`, `setDuty(double)`, equality, and `setEnergyStream(EnergyStream)` behavior remain available. Legacy equipment that encodes direction in a signed duty keeps that convention until it is migrated to typed ports. New integrations should declare an `EnergyType` and use named ports so type validation and graph scheduling are available.
