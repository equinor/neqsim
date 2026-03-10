---
title: "Instrument Design for Process Equipment"
description: "Comprehensive guide to NeqSim's instrument design framework — ISA-5.1 identification, SIL-rated safety instruments, I/O counting, DCS/SIS cabinet sizing, and cost estimation. Covers separators, compressors, heat exchangers, pipelines, and valves with plant-wide SystemInstrumentDesign aggregation."
---

# Instrument Design for Process Equipment

NeqSim includes an instrument design framework that mirrors the existing
electrical design and mechanical design systems. After running a process
simulation, the instrument design system automatically determines which field
instruments are required for each equipment item, generates ISA-5.1 tag
numbers, classifies safety instruments by SIL level, counts I/O channels,
and estimates instrument and control system costs.

## Architecture Overview

The instrument design follows the same composition pattern as `ElectricalDesign`
and `MechanicalDesign`:

```
ProcessEquipmentInterface
  └── getInstrumentDesign() ──► InstrumentDesign (base)
                                    ├── InstrumentList
                                    │     └── InstrumentSpecification (per instrument)
                                    └── InstrumentDesignResponse (JSON)
```

Equipment-specific subclasses add the correct instruments for each equipment
type:

| Equipment | Instrument Design Class | Key Instruments |
|-----------|-------------------------|-----------------|
| Separator | `SeparatorInstrumentDesign` | PT×2, PSH, TT, LT×2, LSH, LSLL, ZT×2 |
| Compressor | `CompressorInstrumentDesign` | PT (suction/discharge), PDT, PSHH, TT (suction/discharge/bearings), TSHH, FT, FCV, VT×4, VSHH, ST, lube oil PT/PSLL |
| Heat Exchanger | `HeatExchangerInstrumentDesign` | TT (in/out), PT (in/out), PDT, TSHH; type-specific additions |
| Pipeline | `PipelineInstrumentDesign` | PT×2, TT×2, FT, ZS×2 (pig), PSHH, PSLL |
| Valve | `ValveInstrumentDesign` | ZT, ZC; safety valves add XV, ZSO, ZSC |
| System (plant-wide) | `SystemInstrumentDesign` | Aggregates all equipment; sizes DCS/SIS cabinets |

## Quick Start

```java
// 1. Run process simulation
ProcessSystem process = new ProcessSystem();
Stream feed = new Stream("feed", gas);
feed.setFlowRate(50000.0, "kg/hr");
Separator sep = new Separator("HP Sep", feed);
Compressor comp = new Compressor("1st Stage", sep.getGasOutStream());
comp.setOutletPressure(50.0);
process.add(feed);
process.add(sep);
process.add(comp);
process.run();

// 2. Get individual equipment instrument design
InstrumentDesign sepInstr = sep.getInstrumentDesign();
sepInstr.calcDesign();
System.out.println("Separator I/O: " + sepInstr.getTotalIOCount());
System.out.println("Separator cost: " + sepInstr.getEstimatedCostUSD() + " USD");
System.out.println(sepInstr.toJson());

// 3. Get plant-wide instrument summary
SystemInstrumentDesign sysInstr = process.getSystemInstrumentDesign();
sysInstr.calcDesign();
System.out.println(sysInstr.toJson());
```

---

## 1. InstrumentSpecification (ISA-5.1)

Each instrument is represented as an `InstrumentSpecification` — a data sheet
entry following ISA-5.1 / ISA-20 conventions.

### Analog Instruments

For transmitters and continuous measurements (PT, TT, LT, FT, VT, ST):

```java
InstrumentSpecification pt = new InstrumentSpecification(
    "PT",           // ISA-5.1 symbol
    "Inlet Pressure",  // service description
    0.0,            // range minimum
    100.0,          // range maximum
    "bara",         // engineering unit
    "AI"            // I/O type
);
```

### Discrete / Safety Instruments

For switches and trip devices (PSH, LSLL, TSHH, VSHH, XV):

```java
InstrumentSpecification psh = new InstrumentSpecification(
    "PSH",              // ISA-5.1 symbol
    "High Pressure Switch",  // service description
    "DI",               // I/O type
    2                   // SIL rating (IEC 61508)
);
```

Safety instruments with a SIL rating > 0 are automatically marked as
`safetyRelated = true` and their output signal defaults to `"Discrete 24VDC"`.

### ISA-5.1 Symbol Mapping

The constructor auto-detects the instrument type from the ISA symbol:

| ISA Symbol Prefix | Instrument Type | Default Cost (USD) |
|:-:|:---|---:|
| PT, PI | PressureTransmitter | 3,500 |
| PS | PressureSwitch | 3,500 |
| TT, TI, TE | TemperatureTransmitter | 2,500 |
| TS | TemperatureSwitch | 2,500 |
| LT, LI | LevelTransmitter | 5,000 |
| LS | LevelSwitch | 5,000 |
| FT, FI, FE | FlowTransmitter | 8,000 |
| AT, AE | Analyser | 15,000 |
| VT | VibrationTransmitter | 4,000 |
| ST | SpeedTransmitter | 2,000 |
| ZT, ZS | PositionTransmitter | 3,000 |
| XV | SolenoidValve | 6,000 |

### I/O Types

| Code | Description | Direction |
|:---:|:---|:---|
| AI | Analog Input (4–20 mA from field to DCS) | Field → DCS |
| AO | Analog Output (4–20 mA from DCS to field) | DCS → Field |
| DI | Digital Input (discrete status from field) | Field → DCS/SIS |
| DO | Digital Output (discrete command to field) | DCS/SIS → Field |

### Specification Attributes

Every `InstrumentSpecification` carries a complete data sheet:

| Attribute | Default | Description |
|:---|:---:|:---|
| `outputSignal` | `"4-20mA HART"` | Output protocol |
| `connectionSize` | `"1/2 NPT"` | Process connection |
| `material` | `"316SS"` | Wetted parts material |
| `hazardousAreaZone` | `"Zone 1"` | Area classification |
| `exProtection` | `"Ex ia IIC T4 Ga"` | Explosion protection marking |

---

## 2. InstrumentList

The `InstrumentList` collects all instruments for one equipment item and
provides I/O counting, safety instrument counting, cost aggregation, and
automatic tag number generation.

```java
InstrumentList list = new InstrumentList("V-101");
list.add(new InstrumentSpecification("PT", "Pressure", 0.0, 100.0, "bara", "AI"));
list.add(new InstrumentSpecification("TT", "Temperature", 0.0, 200.0, "degC", "AI"));
list.add(new InstrumentSpecification("PSH", "High Pressure", "DI", 2));

System.out.println("AI count: " + list.getAnalogInputCount());   // 2
System.out.println("DI count: " + list.getDigitalInputCount());  // 1
System.out.println("Total I/O: " + list.getTotalIOCount());      // 3
System.out.println("Safety: " + list.getSafetyInstrumentCount()); // 1
System.out.println("Cost: " + list.getTotalCostUSD() + " USD");
```

Tag numbers are auto-generated in the format `<ISA Symbol>-<counter>` (e.g.
`PT-1`, `PT-2`, `TT-3`) unless manually assigned before adding to the list.

---

## 3. InstrumentDesign (Base Class)

The base `InstrumentDesign` class mirrors `ElectricalDesign`:

| Property | Default | Description |
|:---|:---:|:---|
| `hazardousAreaZone` | `"Zone 1"` | IEC 60079-10 zone classification |
| `protectionConcept` | `"Ex ia"` | Explosion protection (intrinsic safety) |
| `instrumentStandard` | `"IEC"` | `"IEC"` or `"ISA"` |
| `includeSafetyInstruments` | `true` | Whether to add SIS instruments |
| `defaultSilLevel` | `2` | Default SIL for safety instruments |

### API

```java
InstrumentDesign design = new InstrumentDesign(equipment);
design.setHazardousAreaZone("Zone 2");
design.setDefaultSilLevel(3);
design.calcDesign();

int ioCount = design.getTotalIOCount();
double cost = design.getEstimatedCostUSD();
String json = design.toJson();
```

---

## 4. Equipment-Specific Instrument Designs

### 4.1 Separator

A separator vessel requires pressure, temperature, and level control plus
safety trips:

| Instrument | ISA Symbol | I/O | SIL | Notes |
|:---|:---:|:---:|:---:|:---|
| Process Pressure | PT | AI | — | Voted pair (2 transmitters) |
| Process Pressure (Redundant) | PT | AI | — | |
| High Pressure Switch | PSH | DI | 2 | Overpressure trip |
| Process Temperature | TT | AI | — | |
| Liquid Level | LT | AI | — | Voted pair (2 transmitters) |
| Liquid Level (Redundant) | LT | AI | — | |
| High Level Switch | LSH | DI | 2 | Overflow protection |
| Low-Low Level Switch | LSLL | DI | 2 | Pump protection |
| Level Control Valve Position | ZT | AI | — | |
| Pressure Control Valve Position | ZT | AI | — | |

**Three-phase separators** add:

| Instrument | ISA Symbol | I/O | Notes |
|:---|:---:|:---:|:---|
| Water/Oil Interface Level | LT | AI | Interface level transmitter |
| Water Dump Valve Position | ZT | AI | |

```java
Separator sep = new Separator("HP Sep", feed);
sep.run();

SeparatorInstrumentDesign instrDesign =
    (SeparatorInstrumentDesign) sep.getInstrumentDesign();
instrDesign.calcDesign();

InstrumentList list = instrDesign.getInstrumentList();
System.out.println("Instruments: " + list.size());  // ~10
System.out.println("Safety: " + list.getSafetyInstrumentCount());  // 3
System.out.println(instrDesign.toJson());
```

### 4.2 Compressor

Compressor instrumentation follows API 617 (centrifugal compressors) and
API 670 (machinery protection):

| Instrument | ISA Symbol | I/O | SIL | Notes |
|:---|:---:|:---:|:---:|:---|
| Suction Pressure | PT | AI | — | |
| Discharge Pressure | PT | AI | — | |
| Differential Pressure (Surge) | PDT | AI | — | Anti-surge detection |
| Discharge Overpressure Trip | PSHH | DI | 2 | |
| Suction Temperature | TT | AI | — | |
| Discharge Temperature | TT | AI | — | |
| Bearing *n* Temperature | TT | AI | — | Per bearing (default 2) |
| Discharge Overtemperature Trip | TSHH | DI | 2 | |
| Suction Flow (Anti-Surge) | FT | AI | — | |
| Anti-Surge Valve Output | FCV | AO | — | |
| Bearing *n* Vibration X | VT | AI | — | API 670 X-Y probes |
| Bearing *n* Vibration Y | VT | AI | — | Per bearing (default 2) |
| High Vibration Trip | VSHH | DI | 2 | |
| Shaft Speed | ST | AI | — | |
| Lube Oil Pressure | PT | AI | — | |
| Low Lube Oil Pressure Trip | PSLL | DI | 2 | |

With 2 bearings, a compressor has **~18 instruments** including **5 safety trips**.

```java
Compressor comp = new Compressor("1st Stage", feed);
comp.setOutletPressure(50.0);
comp.run();

CompressorInstrumentDesign instrDesign =
    (CompressorInstrumentDesign) comp.getInstrumentDesign();
instrDesign.setNumberOfBearings(3);  // Override default of 2
instrDesign.setIncludeAntiSurge(true);
instrDesign.calcDesign();

System.out.println("Total I/O: " + instrDesign.getTotalIOCount());
System.out.println(instrDesign.toJson());
```

### 4.3 Heat Exchanger

The design auto-detects the heat exchanger type from the equipment class:

| Equipment Class | Detected Type | Additional Instruments |
|:---|:---|:---|
| `HeatExchanger` | `SHELL_AND_TUBE` | Utility side TT×2, PT |
| `Cooler` | `AIR_COOLER` | Ambient TT, fan ST, fan VT |
| `Heater` | `ELECTRIC_HEATER` | Element TT, element TSHH |

All types share a common instrument set:

| Instrument | ISA Symbol | I/O | Notes |
|:---|:---:|:---:|:---|
| Process Inlet Temperature | TT | AI | |
| Process Outlet Temperature | TT | AI | |
| Process Inlet Pressure | PT | AI | |
| Process Outlet Pressure | PT | AI | |
| Process Side dP | PDT | AI | Fouling/blockage detection |
| Overtemperature Trip | TSHH | DI | SIL-rated |

```java
Heater heater = new Heater("Electric Heater", feed);
heater.setOutTemperature(273.15 + 80.0);
heater.run();

HeatExchangerInstrumentDesign instrDesign =
    (HeatExchangerInstrumentDesign) heater.getInstrumentDesign();
instrDesign.calcDesign();

System.out.println("Type: " + instrDesign.getHeatExchangerType());
System.out.println("Instruments: " + instrDesign.getInstrumentList().size());
```

### 4.4 Pipeline

Pipeline instrumentation includes pressure/temperature monitoring, flow
metering, pig detection, and leak detection:

| Instrument | ISA Symbol | I/O | SIL | Notes |
|:---|:---:|:---:|:---:|:---|
| Inlet Pressure | PT | AI | — | |
| Outlet Pressure | PT | AI | — | |
| Overpressure Trip | PSHH | DI | 2 | |
| Inlet Temperature | TT | AI | — | |
| Outlet Temperature | TT | AI | — | |
| Pipeline Flow | FT | AI | — | Custody/operational metering |
| Low Pressure (Leak Detection) | PSLL | DI | 2 | |
| Pig Signaller Inlet | ZS | DI | — | |
| Pig Signaller Outlet | ZS | DI | — | |

```java
AdiabaticPipe pipe = new AdiabaticPipe("Export Line", feed);
pipe.setLength(50000.0);
pipe.setDiameter(0.508);
pipe.run();

PipelineInstrumentDesign instrDesign =
    (PipelineInstrumentDesign) pipe.getInstrumentDesign();
instrDesign.setIncludePigDetection(true);
instrDesign.setIncludeLeakDetection(true);
instrDesign.calcDesign();

System.out.println("Total I/O: " + instrDesign.getTotalIOCount());
```

### 4.5 Valve

All valves get basic position feedback. Safety/ESD valves (auto-detected from
class name) add a trip solenoid and limit switches:

**Control valve:**

| Instrument | ISA Symbol | I/O | Notes |
|:---|:---:|:---:|:---|
| Valve Position | ZT | AI | Position transmitter |
| Valve Positioner Output | ZC | AO | I/P converter or digital positioner |

**Safety / ESD valve** (adds):

| Instrument | ISA Symbol | I/O | SIL | Notes |
|:---|:---:|:---:|:---:|:---|
| Trip Solenoid | XV | DO | 2 | De-energise to trip |
| Open Limit Switch | ZSO | DI | — | |
| Closed Limit Switch | ZSC | DI | — | |

Auto-detection recognises `ESDValve`, `HIPPSValve`, and `BlowdownValve` as
safety valves.

```java
ThrottlingValve valve = new ThrottlingValve("PCV-101", feed);
valve.setOutletPressure(30.0);
valve.run();

ValveInstrumentDesign instrDesign =
    (ValveInstrumentDesign) valve.getInstrumentDesign();
instrDesign.calcDesign();  // gets ZT + ZC (2 instruments)

// Force safety valve mode
instrDesign.setSafetyValve(true);
instrDesign.calcDesign();  // now gets ZT + ZC + XV + ZSO + ZSC (5 instruments)
```

---

## 5. System-Level Instrument Design

`SystemInstrumentDesign` aggregates all equipment instrument designs across a
`ProcessSystem` and sizes the DCS/SIS control system infrastructure.

### DCS and SIS Cabinet Sizing

| System | Channels/Card | Cards/Cabinet | Notes |
|:---:|:---:|:---:|:---|
| DCS | 16 | 16 | Process (non-safety) I/O |
| SIS | 8 | 8 | Safety-related I/O (redundant) |
| Marshalling | — | — | 1 cabinet per ~200 I/O channels |

### Cost Estimation Defaults

| Item | Unit Cost |
|:---|---:|
| DCS I/O channel | 500 USD |
| SIS I/O channel | 1,500 USD |
| DCS cabinet | 50,000 USD |
| SIS cabinet | 80,000 USD |
| Marshalling cabinet | 15,000 USD |

### Usage

```java
ProcessSystem process = new ProcessSystem();
// ... add equipment ...
process.run();

SystemInstrumentDesign sysInstr = process.getSystemInstrumentDesign();
sysInstr.calcDesign();

// Access results
System.out.println("Total instruments: " + sysInstr.getTotalInstruments());
System.out.println("Total I/O: " + sysInstr.getTotalIO());
System.out.println("  AI: " + sysInstr.getTotalAI());
System.out.println("  AO: " + sysInstr.getTotalAO());
System.out.println("  DI: " + sysInstr.getTotalDI());
System.out.println("  DO: " + sysInstr.getTotalDO());
System.out.println("Safety I/O: " + sysInstr.getTotalSafetyIO());
System.out.println("DCS cabinets: " + sysInstr.getDcsCabinets());
System.out.println("SIS cabinets: " + sysInstr.getSisCabinets());
System.out.println("Marshalling cabinets: " + sysInstr.getMarshallingCabinets());
System.out.println("Total cost: " + sysInstr.getTotalInstrumentCostUSD() + " USD");

// Full JSON report
System.out.println(sysInstr.toJson());
```

The JSON output includes:
- Plant-level I/O summary (AI/AO/DI/DO totals)
- Safety I/O breakdown
- DCS/SIS sizing (cards, cabinets)
- Cost breakdown (instruments, DCS, SIS, marshalling)
- Per-equipment instrument summaries

---

## 6. ProcessSystem Integration

The instrument design integrates with `ProcessSystem` via two hooks on
`ProcessEquipmentInterface`:

| Method | Description |
|:---|:---|
| `getInstrumentDesign()` | Returns the equipment's instrument design (creates on first call for lazy-init equipment) |
| `initInstrumentDesign()` | Explicitly initialises the instrument design |

### Equipment Initialisation

| Equipment | Init Mode | Notes |
|:---|:---:|:---|
| Separator | Eager | Created in constructor |
| Compressor | Eager | Created in constructor |
| Heater / Cooler | Lazy | Created on first `getInstrumentDesign()` call |
| AdiabaticPipe | Lazy | Created on first `getInstrumentDesign()` call |

### Complete Workflow Example

```java
// Build process
SystemInterface gas = new SystemSrkEos(273.15 + 25.0, 60.0);
gas.addComponent("methane", 0.85);
gas.addComponent("ethane", 0.10);
gas.addComponent("propane", 0.05);
gas.setMixingRule("classic");

ProcessSystem process = new ProcessSystem();

Stream feed = new Stream("Feed", gas);
feed.setFlowRate(50000.0, "kg/hr");
process.add(feed);

Separator hpSep = new Separator("HP Separator", feed);
process.add(hpSep);

Compressor comp = new Compressor("Export Compressor",
    hpSep.getGasOutStream());
comp.setOutletPressure(120.0);
process.add(comp);

Cooler afterCooler = new Cooler("Aftercooler",
    comp.getOutletStream());
afterCooler.setOutTemperature(273.15 + 40.0);
process.add(afterCooler);

// Run process
process.run();

// Get plant-wide instrument summary
SystemInstrumentDesign sysInstr = process.getSystemInstrumentDesign();
sysInstr.calcDesign();

System.out.println("=== Plant Instrument Summary ===");
System.out.println("Total instruments: " + sysInstr.getTotalInstruments());
System.out.println("Total I/O: " + sysInstr.getTotalIO());
System.out.println("DCS cabinets: " + sysInstr.getDcsCabinets());
System.out.println("SIS cabinets: " + sysInstr.getSisCabinets());
System.out.println("Total CAPEX: $" + sysInstr.getTotalInstrumentCostUSD());
System.out.println(sysInstr.toJson());
```

---

## 7. Applicable Standards

| Standard | Scope | Used By |
|:---|:---|:---|
| ISA-5.1 | Instrument identification and symbols | `InstrumentSpecification` |
| ISA-18.2 | Alarm management | `InstrumentDesign` |
| ISA-20 | Instrument specification forms | `InstrumentSpecification` |
| IEC 61508 | Functional safety — SIL levels | `InstrumentSpecification` (SIL rating) |
| IEC 61511 | Safety instrumented systems (SIS) for process industry | `SystemInstrumentDesign` (SIS sizing) |
| IEC 60079 / ATEX | Hazardous area classification | `InstrumentDesign` (Ex protection) |
| API 670 | Machinery protection systems (vibration) | `CompressorInstrumentDesign` (VT probes) |
| API 617 | Centrifugal compressor instrumentation | `CompressorInstrumentDesign` |

---

## 8. Class Reference

| Class | Package | Description |
|:---|:---|:---|
| `InstrumentDesign` | `process.instrumentdesign` | Base class — hazardous area, SIL, instrument list |
| `InstrumentSpecification` | `process.instrumentdesign` | Single instrument data sheet entry (ISA-5.1) |
| `InstrumentList` | `process.instrumentdesign` | Instrument collection with I/O counting and cost |
| `InstrumentDesignResponse` | `process.instrumentdesign` | JSON serialisation helper |
| `SeparatorInstrumentDesign` | `process.instrumentdesign.separator` | Separator-specific (PT, LT, PSH, LSH, LSLL) |
| `CompressorInstrumentDesign` | `process.instrumentdesign.compressor` | Compressor-specific (API 670/617 suite) |
| `HeatExchangerInstrumentDesign` | `process.instrumentdesign.heatexchanger` | Heat exchanger — auto-detects S&T / air cooler / electric |
| `PipelineInstrumentDesign` | `process.instrumentdesign.pipeline` | Pipeline (pig detection, leak detection, metering) |
| `ValveInstrumentDesign` | `process.instrumentdesign.valve` | Valve (position, positioner, solenoid, limit switches) |
| `SystemInstrumentDesign` | `process.instrumentdesign.system` | Plant-wide aggregation, DCS/SIS cabinet sizing |

---

## Related Documentation

- [Electrical Design](electrical-design) — Motor, VFD, cable, transformer, switchgear design
- [Mechanical Design](mechanical_design) — Pressure vessel, pipeline wall thickness
- [Equipment Design Parameters](EQUIPMENT_DESIGN_PARAMETERS) — autoSize and design parameter overview
- [Process Design Guide](process_design_guide) — Complete design workflow using NeqSim
