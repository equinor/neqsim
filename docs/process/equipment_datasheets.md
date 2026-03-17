---
title: "Equipment Datasheet Generator"
description: "Generate structured JSON equipment datasheets from process simulation results for separators, compressors, heat exchangers, and valves."
---

# Equipment Datasheet Generator

The `EquipmentDatasheetGenerator` produces structured JSON datasheets from a completed `ProcessSystem` simulation. Each datasheet includes operating conditions, fluid properties, mechanical design parameters, and equipment-specific performance data.

**Class**: `neqsim.process.mechanicaldesign.EquipmentDatasheetGenerator`

---

## Supported Equipment Types

| Equipment | Datasheet Contents |
|-----------|-------------------|
| `Separator` | Operating P/T, phase fractions, liquid levels, gas/liquid outlet conditions |
| `Compressor` | Power, polytropic/isentropic efficiency, head, speed, surge margin |
| `Heater` / `Cooler` | Duty, inlet/outlet temperatures, UA if available |
| `ThrottlingValve` | Cv, pressure drop, Joule-Thomson effect |
| Other equipment | Generic operating conditions and fluid properties |

---

## Java Example

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.mechanicaldesign.EquipmentDatasheetGenerator;
import neqsim.thermo.system.SystemSrkEos;

// Build and run process
SystemSrkEos fluid = new SystemSrkEos(273.15 + 30, 80.0);
fluid.addComponent("methane", 0.80);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.addComponent("n-pentane", 0.03);
fluid.addComponent("n-hexane", 0.02);
fluid.setMixingRule("classic");

ProcessSystem process = new ProcessSystem();

Stream feed = new Stream("Well Stream", fluid);
feed.setFlowRate(100000.0, "kg/hr");
process.add(feed);

Separator sep = new Separator("HP Separator", feed);
process.add(sep);

Compressor comp = new Compressor("Export Compressor", sep.getGasOutStream());
comp.setOutletPressure(150.0);
process.add(comp);

process.run();

// Generate all datasheets
EquipmentDatasheetGenerator gen = new EquipmentDatasheetGenerator(process);
String allSheets = gen.generateAllDatasheets();
System.out.println(allSheets);

// Or generate for a single equipment
String sepSheet = gen.generateDatasheet(sep);
System.out.println(sepSheet);
```

---

## Python Example

```python
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
Separator = jneqsim.process.equipment.separator.Separator
Compressor = jneqsim.process.equipment.compressor.Compressor
DatasheetGen = jneqsim.process.mechanicaldesign.EquipmentDatasheetGenerator

fluid = SystemSrkEos(273.15 + 30.0, 80.0)
fluid.addComponent("methane", 0.80)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)
fluid.addComponent("n-pentane", 0.03)
fluid.addComponent("n-hexane", 0.02)
fluid.setMixingRule("classic")

process = ProcessSystem()

feed = Stream("Well Stream", fluid)
feed.setFlowRate(100000.0, "kg/hr")
process.add(feed)

sep = Separator("HP Separator", feed)
process.add(sep)

comp = Compressor("Export Compressor", sep.getGasOutStream())
comp.setOutletPressure(150.0)
process.add(comp)

process.run()

gen = DatasheetGen(process)
import json
sheets = json.loads(str(gen.generateAllDatasheets()))
for name, sheet in sheets.items():
    print(f"\n--- {name} ---")
    print(json.dumps(sheet, indent=2))
```

---

## JSON Output Structure

Each datasheet is a JSON object with these sections:

```json
{
  "equipmentName": "HP Separator",
  "equipmentType": "Separator",
  "operatingConditions": {
    "temperature_K": 303.15,
    "pressure_bara": 80.0,
    "massFlowRate_kg_hr": 100000.0,
    "molarFlowRate_mol_sec": 142.5,
    "numberOfPhases": 2
  },
  "fluidProperties": {
    "density_kg_m3": 65.3,
    "molarMass_kg_mol": 0.022,
    "zFactor": 0.82
  },
  "performanceData": {
    "gasPhaseFraction": 0.85,
    "liquidPhaseFraction": 0.15,
    "gasOutletTemperature_K": 303.15,
    "liquidOutletTemperature_K": 303.15
  }
}
```

For compressors, the `performanceData` section includes:

```json
{
  "performanceData": {
    "power_kW": 1250.0,
    "polytropicEfficiency": 0.75,
    "isentropicEfficiency": 0.72,
    "polytropicHead_kJ_kg": 85.2,
    "outletPressure_bara": 150.0,
    "outletTemperature_K": 385.0
  }
}
```

---

## Use Cases

- **FEED documentation** — auto-generate equipment datasheets from simulation
- **Design verification** — compare datasheet values against vendor data
- **Management of Change** — re-run and diff datasheets when process conditions change
- **AI-assisted review** — structured JSON enables automated checks

---

## Related Documentation

- [Mechanical Design](index.md) - Weight and wall thickness calculations
- [Process Simulation](../process/index.md) - Building and running process systems
