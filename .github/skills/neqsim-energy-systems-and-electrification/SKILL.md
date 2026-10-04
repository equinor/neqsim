---
name: neqsim-energy-systems-and-electrification
description: "Guides agents through NeqSim energy-bus coupling and facility electrification. USE WHEN: users ask to balance electrical or thermal buses, couple process demand to energy supply, screen power-from-shore, compare electric/gas/steam drivers, dispatch wind/solar/gas turbines, assess turbine degradation or wash intervals, or include carbon-price effects in dispatch. Anchors: EnergyBus, CoupledProcessEnergySolver, MotorDriveTrain, GasTurbineUnit, TurbineDispatchOptimizer, OffshoreEnergySystem."
last_verified: "2026-10-03"
---

# Energy Systems and Electrification

## When to use this

- Balance competing electrical, shaft-work, or thermal-utility producers and consumers.
- Couple process equipment to an energy bus when power allocation changes process operation.
- Compare power-from-shore, gas-turbine generation, steam, wind, solar, fuel-cell, or hybrid supply cases.
- Connect electric, gas-turbine, or steam-turbine drivers to a compressor or other shaft load.
- Evaluate gas-turbine site derating, degradation, water-wash economics, fleet dispatch, or late-life retrofit.
- Run interval profiles for generation, demand, cost, emissions, unmet load, or curtailment.

Use `neqsim-power-generation` for thermodynamic turbine/HRSG/combined-cycle construction. This skill covers the energy bus and electrification layer those equipment recipes do not cover. It complements `neqsim-compressor-antisurge-recycle`: use that skill for compressor protection and recycle power penalties, and this one for source/driver selection and facility energy dispatch.

## Class map

| Class family | Package | What it does | Verified entry points / result methods |
|---|---|---|---|
| Typed energy buses and ports: `EnergyBus`, `EnergyPort`, `EnergyNetworkReport` | `neqsim.process.equipment.stream` | Routes signed electrical, shaft, heat, and other energy contributions with dispatch and allocation reports. | `new EnergyBus(name, EnergyType)`, `setContribution`, `solveBalance`, `getLastReport`, `getServedDemand`, `getUnmetDemand` |
| Process coupling: `EnergyNetworkSolver`, `CoupledProcessEnergySolver`, `CoupledProcessEnergyResult` | `neqsim.process.equipment.energy` | Runs bus dispatch inside equipment execution or iterates process and power balances together. | `addEnergyBus`, `run`, `solve`, `isConverged`, `getIterations`, `getMaximumPowerResidual` |
| Interval profiles: `EnergyTimeSeriesProfile`, `EnergyTimeSeriesSimulator`, `EnergyTimeSeriesResult` | `neqsim.process.equipment.energy` | Applies step/linear profiles and integrates served/unmet/curtailed energy, cost, and emissions over intervals. | `step`, `linear`, `addProfile`, `setIntervalSeconds`, `setDurationSeconds`, `run`, `getServedEnergyMWh` |
| Utility heat and demand quality: `UtilityEnergyBus`, `ThermalUtilitySource`, `ThermalUtilityConsumer`, `ThermalUtilityQualityAnalysis` | `neqsim.process.equipment.energy` | Allocates typed thermal utilities and checks level and temperature-grade compatibility. | `setThermodynamicStates`, `solveBalance`, `setAvailablePower`, `setRequestedPower`, `getAllocatedPower`, `canServeProcessTemperature` |
| Conversion and electrified drives: `EnergyConverter`, `ElectricMotor`, `Gearbox`, `Inverter`, `LoadEfficiencyCurve`, `LoadMappedEnergyConverter`, `MotorDriveTrain`, `MotorAssistedDriveTrain` | `neqsim.process.equipment.energy` | Represents conversion losses, load-dependent efficiency, motor assist, and shaft/electrical interconnection. | `setEfficiency`, `setPerformanceModel`, `setLoadEfficiencyCurve`, `setRequestedShaftPower`, `setPowerTargets`, `validateSetup` |
| Driver curves: `ElectricMotorDriver`, `GasTurbineDriver`, `SteamTurbineDriver` | `neqsim.process.equipment.compressor.driver` | Provides rated power/speed and driver-curve availability for rotating equipment. | Constructors with rated power/efficiency; `getAvailablePower(speed)`, `getDriverType` |
| Mechanical work boundary: `MechanicalShaft`, `EnergyPort` | `neqsim.process.equipment.stream` | Balances generated and consumed shaft power and couples it to driven equipment. | `setGeneratedPower`, `setConsumedPower`, `getSpeed`, `getLastReport` |
| GT site performance: `GasTurbineCatalog`, `GasTurbineSpec`, `GasTurbinePerformanceMap`, `GasTurbineUnit`, `GasTurbineEmissions` | `neqsim.process.equipment.powergeneration.gasturbine` | Uses catalog ratings, ambient correction, compressor demand, degradation, and fuel-based emissions. | `GasTurbineCatalog.get`, `setAmbient`, `addPowerConsumer`, `run`, `getAvailablePowerW`, `getLoadFraction`, `getCO2EmissionKgPerS` |
| GT degradation and wash economics: `GasTurbineDegradation`, `GasTurbineWashPlanner` | `neqsim.process.equipment.powergeneration.gasturbine` | Tracks fired-hour penalties and compares wash intervals with fuel, emissions, and outage cost inputs. | `addFiredHours`, `offlineWash`, `onlineWash`, `optimize` |
| Dispatch and carbon price: `TurbineDispatchOptimizer`, `CO2TaxSchedule`, `LateLifeRetrofitStudy` | `neqsim.process.equipment.powergeneration.gasturbine` | Selects a feasible fleet dispatch and evaluates retrofit cases with explicit fuel/carbon costs. | `dispatch`, `CO2TaxSchedule.getTotalNOKPerTonne`, `LateLifeRetrofitStudy.run` |
| Renewable and hybrid supply: `WindTurbine`, `WindFarm`, `SolarPanel`, `OffshoreEnergySystem` | `neqsim.process.equipment.powergeneration` | Calculates renewable generation profiles and evaluates offshore hybrid dispatch and avoided emissions. | `setWindSpeedTimeSeries`, `runTimeSeries`, `getPowerTimeSeries`, `runHourlyDispatch`, `getWindPowerFraction` |
| Other generation and reference case: `SteamTurbine`, `FuelCell`, `OffshoreEnergyReferenceCase` | `neqsim.process.equipment.powergeneration` and `neqsim.process.equipment.energy` | Provides steam/fuel-cell generation and a reproducible offshore 24-hour energy-system example. | `SteamTurbine.getPower("kW")`, `FuelCell.getPower`, `OffshoreEnergyReferenceCase.run24HourCase` |

### Energy-system data flow

- **Bus balance:** producer/consumer ports publish signed power -> dispatch strategy allocates supply -> `EnergyNetworkReport` records served demand, unmet demand, curtailment, losses, and emissions/cost metadata.
- **Process coupling:** process equipment requests power -> `CoupledProcessEnergySolver` dispatches its buses and reruns the process -> convergence result reports process and power residuals. Use this only when the process response feeds back on demand.
- **Electrification:** simulated shaft demand -> motor/drive conversion and bus request -> grid or hybrid supply -> unmet load and fuel/emissions comparison. Keep site grid intensity and availability as explicit inputs.
- **Hybrid supply and time series:** wind/solar/weather and gas-turbine availability -> interval dispatch -> MWh, curtailment, fuel/cost, and CO2 totals.
- **GT health/dispatch:** measured or declared fired hours and wash effectiveness -> performance-map correction -> feasible fleet dispatch -> fuel/carbon cost or retrofit comparison.

### Practical study sequence

1. Freeze the load basis: process shaft power, electrical auxiliaries, utility heat, operating hours, and the time interval represented by each sample.
2. Choose a balance-only `EnergyBus` for a fixed snapshot; use `CoupledProcessEnergySolver` when process recalculation changes the requested power.
3. For a real turbine driver, use a vendor-rated performance model for fuel matching or a catalog `GasTurbineUnit` for site correction, compressor loads, and fleet dispatch.
4. For electrification, state imported-grid emissions intensity and availability explicitly; compare the released fuel gas separately from the grid CO2 result.
5. For intermittent supply, pass a time-aligned wind/load profile to `EnergyTimeSeriesSimulator` or the applicable offshore dispatch model; report unmet and curtailed energy as well as totals.
6. For a wash or retrofit decision, vary outage hours, recovery, fired-hour deterioration, fuel price, and CO2 schedule. Keep each economic assumption visible.
7. Inspect participant allocations as well as net bus power; an aggregate balance can hide an individual curtailed load.
8. Keep shaft power, electrical input power, and fuel thermal power on separate bases when calculating conversion loss or emissions.

Do not compare a one-hour peak load with annual-average renewable output. Preserve interval durations and include the partial final interval in energy integration.

## Build pattern

This minimal Java example is a bus-level balance. Contributions are signed watts: generation is positive and consumption is negative. Use connected `EnergyPort`s plus `EnergyNetworkSolver` or `CoupledProcessEnergySolver` when the contributions must be produced by live process equipment.

```java
EnergyBus grid = new EnergyBus("shore power", EnergyType.ELECTRICAL);
grid.setContribution("wind", 2.0e6);
grid.setContribution("process load", -1.5e6);
EnergyNetworkReport report = grid.solveBalance();

double servedDemandW = report.getServedDemand();
double unmetDemandW = report.getUnmetDemand();
double curtailedSupplyW = report.getCurtailedSupply();
```

Equivalent Python class lookup:

```python
from neqsim import jneqsim

EnergyBus = jneqsim.process.equipment.stream.EnergyBus
EnergyType = jneqsim.process.equipment.stream.EnergyType

grid = EnergyBus("shore power", EnergyType.ELECTRICAL)
grid.setContribution("wind", 2.0e6)
grid.setContribution("process load", -1.5e6)
report = grid.solveBalance()
served_demand_w = report.getServedDemand()
unmet_demand_w = report.getUnmetDemand()
curtailed_supply_w = report.getCurtailedSupply()
```

For process feedback, create `CoupledProcessEnergySolver(process)`, call `addEnergyBus(bus)`, then `solve()`. Check `result.isConverged()` and `result.getTerminationReason()`; a normal `ProcessSystem.run()` is not a substitute for the coupled solve when equipment load depends on its dispatched allocation.

## Result extraction

- `EnergyNetworkReport.getOfferedSupply`, `getAcceptedSupply`, `getRequestedDemand`, `getServedDemand`, `getUnmetDemand`, and `getCurtailedSupply` are **W**. `EnergyBus.setContribution(participant, power, unit)` converts a supplied unit to W.
- `CoupledProcessEnergyResult.getIterations`, `getMaximumPowerResidual`, `getMaximumProcessResidual`, and `getTerminationReason` are solver diagnostics; compare residuals with configured tolerances.
- `EnergyTimeSeriesResult.getServedEnergyMWh`, `getUnmetEnergyMWh`, and `getCurtailedEnergyMWh` are **MWh**; `getCo2EmissionsKg` is **kg**. `getOperatingCost` uses the energy-price inputs supplied to the ports.
- `GasTurbineUnit.getAvailablePowerW()` is **W**, `getLoadFraction()` is dimensionless, and `getCO2EmissionKgPerS()` is **kg/s**.
- `GasTurbineWashPlanner.WashPlan` reports annual fuel volume, extra CO2 tonnes, costs, and wash interval; `TurbineDispatchOptimizer.dispatch` accepts demanded power in W.
- `SteamTurbine.getPower("kW")` accepts a power unit. `WindFarm.getPower("kW")` accepts a power unit; its no-argument `getPower()` is documented as W.

## Gotchas

- Contribution sign matters: positive injects power, negative withdraws. `EnergyBus` values are W by default; do not pass kW without the overload's unit argument.
- A bus with no registered/connected ports may have a valid arithmetic balance but no process coupling. `EnergyNetworkSolver.validateSetup()` reports “No energy buses are configured” with “Call addEnergyBus(bus)” and warns when a bus has no connected ports.
- `EnergyConverter.validateSetup()` asks for a connected input port and recommends connecting an input energy stream; the output port is a warning. `MotorDriveTrain.validateSetup()` separately requires the motor/bus, shaft, and driven equipment connections.
- A `UtilityEnergyBus` needs a matching utility level and physically feasible supply/return enthalpy/temperature grade. A generic electrical bus is not a thermal utility substitute.
- Ambient turbine temperature is K and ambient pressure is bara. Keep GT load demand in W; label vendor and fuel data separately from catalog defaults.
- Wash optimization is only as credible as the measured degradation trend, wash recovery, outage/deferment, fuel value, and carbon-price inputs. A wash interval at a search bound is not proof of a global optimum.
- These classes do not perform AC load-flow, grid stability/protection studies, power-quality certification, or project sanction economics. Hybrid dispatch and CO2 savings are screening evidence, not a grid connection design.

## Validation / benchmarks

- `OffshoreEnergyReferenceCase.run24HourCase()` provides a reproducible reference time-series case; compare totals via its associated helper methods rather than treating one illustrative profile as site data.
- Regression coverage includes `UtilityEnergyNetworkTest`, `CoupledProcessEnergySolverTest`, `EnergyTimeSeriesSimulatorTest`, `EnergyNetworkDynamicsTest`, `ElectricMotorPerformanceIntegrationTest`, and `OffshoreEnergyReferenceCaseTest`.
- Check power closure per bus, convergence reason/residuals for coupled solves, duration including a partial final interval, and energy integration in MWh. Cross-check GT availability and emissions against declared ratings/fuel composition.
- The `neqsim-power-generation` examples remain the route for detailed GT/steam-cycle thermodynamics, HRSG heat recovery, and combined-cycle process integration.

## Related skills

- `neqsim-power-generation` — GT, steam turbine, HRSG, combined cycle, and thermodynamic generation models.
- `neqsim-compressor-antisurge-recycle` — compressor surge protection and recycle power penalties.
- `neqsim-utility-design` and `neqsim-utilities-specification` — utility duties, levels, and design basis.
- `neqsim-heat-integration` — process heat recovery and utility targeting.
- `neqsim-field-economics` — project NPV and fiscal economics beyond dispatch cost.
- `neqsim-process-modeling` — process equipment and flowsheet assembly.