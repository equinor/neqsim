---
title: "Energy Conversion and Utility Equipment"
description: "Reference for NeqSim motors, generators, converters, energy-network solvers, thermal utility sources and consumers, and their typed energy-port contracts."
---

The `neqsim.process.equipment.energy` package supplies process units for conversion and allocation
of electrical power, shaft work, heat, and chemical energy. These units connect through typed
`EnergyPort` and `EnergyBus` objects and can be ordered explicitly in a `ProcessSystem` graph.

## Equipment selection

| Equipment | Primary role |
| --- | --- |
| `EnergyConverter` | Generic two-domain conversion with efficiency, capacity, idle loss, ramping, and trip state |
| `LoadMappedEnergyConverter` | Conversion with load-dependent efficiency |
| `ElectricMotor` | Electrical power to shaft work |
| `Generator` | Shaft work to electrical power |
| `Gearbox` | Shaft-work conversion with a speed ratio |
| `Inverter` | Electrical conversion with voltage and frequency quality |
| `Transformer` | Electrical conversion with a voltage ratio |
| `PrimeMover` | Chemical or fuel energy to shaft work |
| `CommittedEnergyGenerator` | Dispatchable generation with commitment constraints |
| `ThermalUtilitySource` | Typed heating or cooling utility supply |
| `ThermalUtilityConsumer` | Temperature-grade-aware thermal utility demand |
| `EnergyNetworkSolver` | Explicit `ProcessSystem` calculation node for one or more energy buses |

`MotorDriveTrain`, `MotorAssistedDriveTrain`, `CoupledProcessEnergySolver`, and the time-series
classes are orchestration services rather than equipment units. They coordinate the concrete
equipment above across shared buses, mechanical shafts, repeated process-energy iterations, or
time horizons.

## Common contract

Energy equipment uses W internally. Unit-aware public APIs accept the units documented by the
underlying energy stream or port. Each converter exposes a specification input, a calculated
useful output, and a calculated heat-loss output. The process graph uses port direction and mode
to order producers, network solvers, and consumers.

Before using an energy result for design or optimization, check:

- energy conservation across input, useful output, and heat loss;
- configured efficiency, idle loss, maximum power, and ramp limits;
- `ValidationResult` setup findings and trip state;
- energy quality such as utility temperature, electrical frequency, or shaft speed;
- unmet demand, curtailed supply, and balancing contributions in `EnergyNetworkReport`.

## Related documentation

- [Energy streams and equipment ports](../energy_streams) — connection patterns and energy balances
- [Energy dispatch](../energy_dispatch) — cost, emissions, and priority dispatch
- [Energy time series](../energy_time_series) — time-varying supply and demand
- [Power generation](power_generation) — turbines, fuel cells, renewables, and combined cycles
- [Battery storage](battery_storage) — state of charge and automatic balancing
- [Complete equipment catalog](equipment_catalog) — every concrete equipment implementation
