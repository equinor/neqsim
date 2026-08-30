---
title: "Black-Oil Separator Equipment"
description: "Use BlackOilSeparator with SystemBlackOil in a NeqSim ProcessSystem, including its pressure-temperature specification, outlet systems, units, and model boundary."
---

`BlackOilSeparator` is the process-equipment bridge for NeqSim's pressure-indexed black-oil
PVT model. It performs one equilibrium black-oil flash at a specified outlet pressure and
temperature and can participate in normal `ProcessSystem` execution.

## Model contract

| Item | API or unit |
| --- | --- |
| Java class | `neqsim.process.equipment.blackoil.BlackOilSeparator` |
| Inlet model | `neqsim.blackoil.SystemBlackOil` |
| Outlet pressure | bar absolute |
| Outlet temperature | K |
| Products | `getOilOut()`, `getGasOut()`, and `getWaterOut()` as `SystemBlackOil` objects |
| Flash evidence | `getLastFlashResult()` and `getResultsMap()` |
| Process reporting | `toJson()` |

The constructor requires the equipment name, inlet `SystemBlackOil`, outlet pressure, and outlet
temperature. The model does not use `StreamInterface`: its inlet and three products remain
black-oil systems containing standard oil, gas, and water totals. Use the compositional
[separator equipment](separators) when a flowsheet needs component-level material streams.

## Execution and conservation

On each run the unit sets the specified pressure and temperature, flashes the inlet black-oil
system, and partitions standard totals into oil, free-gas, and water products. A robust workflow
checks:

1. standard oil, gas, and water closure across the three products;
2. the `Rs`, `Rv`, `Bo`, `Bg`, and `Bw` values in `BlackOilFlashResult`;
3. pressure and temperature units before comparing with laboratory or simulator tables;
4. the PVT-table pressure and temperature validity range.

The temperature is a state label for the selected black-oil table; the underlying table does not
create an independent continuous temperature correlation. Use separate qualified tables when
temperature dependence is material.

## Related documentation

- [Black-oil package](../../blackoil/) — table construction, flashes, conversion, and export
- [Separators](separators) — compositional two- and three-phase process separators
- [ProcessSystem](../processmodel/process_system) — flowsheet execution and reporting
- [Complete equipment catalog](equipment_catalog) — every concrete equipment implementation
