---
title: "Electrical Design for Process Equipment"
description: "Comprehensive guide to NeqSim's electrical engineering framework — motor sizing, VFD selection, cable sizing, switchgear, transformers, hazardous area classification, and plant-wide electrical load analysis. Covers IEC 60034, IEC 60502, IEC 60076, IEC 61439, IEEE 519, and IECEx/ATEX standards."
---

# Electrical Design for Process Equipment

NeqSim includes a complete electrical engineering framework that mirrors the
existing mechanical design system. After running a process simulation, the
electrical design system automatically sizes motors, variable frequency drives
(VFDs), power cables, switchgear, transformers, and classifies hazardous areas
for every driven equipment item.

## Architecture Overview

The electrical design follows the same composition pattern as `MechanicalDesign`:

```
ProcessEquipmentInterface
  └── getElectricalDesign() ──► ElectricalDesign (base)
                                    ├── ElectricalMotor
                                    ├── VariableFrequencyDrive
                                    ├── ElectricalCable (power + control)
                                    ├── Switchgear
                                    ├── Transformer
                                    └── HazardousAreaClassification
```

Equipment-specific subclasses provide additional detail:

| Equipment | Electrical Design Class | Special Features |
|-----------|------------------------|------------------|
| Compressor | `CompressorElectricalDesign` | Auxiliary loads (lube oil, seal gas, cooling), VFD auto-detection |
| Pump | `PumpElectricalDesign` | Auto voltage selection, single-phase for small pumps |
| Separator | `SeparatorElectricalDesign` | Control valve actuators, instrumentation, lighting, optional heat tracing |
| Heater / Cooler | `HeatExchangerElectricalDesign` | Auto-detects type: electric heater (full duty), air cooler (fan motors), shell-and-tube (auxiliary only) |
| Pipeline | `PipelineElectricalDesign` | Electrical heat tracing (W/m × length), cathodic protection, instrumentation |
| System (plant-wide) | `SystemElectricalDesign` | Aggregates all equipment loads, adds utility/UPS, sizes main transformer and emergency generator |

## Quick Start

```java
// 1. Run process simulation
ProcessSystem process = new ProcessSystem();
// ... add equipment ...
process.run();

// 2. Run all electrical designs
process.runAllElectricalDesigns();

// 3. Get plant-wide load list
ElectricalLoadList loadList = process.getElectricalLoadList();
System.out.println("Total demand: " + loadList.getMaximumDemandKW() + " kW");
System.out.println(loadList.toJson());

// 4. Get individual equipment design
ElectricalDesign compDesign = process.getEquipmentElectricalDesign("1st stage");
System.out.println(compDesign.toJson());
```

---

## 1. Motor Sizing (IEC 60034-30-1)

### Standard Motor Selection

Motors are selected from IEC standard power steps (0.37 kW to 10 MW):

$$
P_{\text{rated}} = \min \{ P_{\text{std}} \in \text{IEC series} \mid P_{\text{std}} \ge P_{\text{shaft}} \times f_{\text{margin}} \}
$$

where $f_{\text{margin}}$ is the sizing margin (default 1.10, i.e. 10%).

### Synchronous and Rated Speed

The synchronous speed of an AC induction motor is:

$$
n_s = \frac{120 \times f}{p}
$$

where $f$ is the supply frequency (Hz) and $p$ is the number of poles.
The rated (actual) speed accounts for slip $s$:

$$
n_r = n_s \times (1 - s)
$$

| Power Range | Typical Slip |
|-------------|-------------|
| ≤ 7.5 kW | 5% |
| 7.5–75 kW | 3% |
| 75–375 kW | 2% |
| > 375 kW | 1.5% |

### Efficiency Classes (IEC 60034-30-1)

The framework models four efficiency classes with approximate full-load
efficiencies for 4-pole, 50 Hz machines:

| Power (kW) | IE1 (Standard) | IE2 (High) | IE3 (Premium) | IE4 (Super Premium) |
|-----------|:-----------:|:--------:|:----------:|:-----------------:|
| ≤ 1.1 | 79.5% | 81.0% | 82.5% | 83.5% |
| 1.1–4 | 84.7% | 86.2% | 87.7% | 88.7% |
| 4–11 | 88.0% | 89.5% | 91.0% | 92.0% |
| 11–37 | 90.3% | 91.8% | 93.3% | 94.3% |
| 37–110 | 92.0% | 93.5% | 95.0% | 96.0% |
| 110–375 | 93.0% | 94.5% | 96.0% | 97.0% |
| > 375 | 93.5% | 95.0% | 96.5% | 97.5% |

### Rated Current

The full-load current for a three-phase motor:

$$
I_{\text{FL}} = \frac{P_{\text{rated}} \times 1000}{\sqrt{3} \times V \times \eta \times \cos\varphi}
$$

where $\eta$ is efficiency and $\cos\varphi$ is the power factor at full load.

### Part-Load Performance

Motor efficiency varies with load. Peak efficiency occurs around 75% load:

$$
\eta(L) = \begin{cases}
\eta_{\text{FL}} - 20 \times (0.25 - L) & \text{if } L < 0.25 \\
\eta_{\text{FL}} & \text{if } 0.25 \le L \le 1.0 \\
\eta_{\text{FL}} - 5 \times (L - 1.0) & \text{if } L > 1.0
\end{cases}
$$

Power factor at part load drops more steeply:

$$
\cos\varphi(L) = \begin{cases}
\cos\varphi_{\text{FL}} - 0.25 & \text{if } L < 0.25 \\
\cos\varphi_{\text{FL}} - 0.12 & \text{if } 0.25 \le L < 0.50 \\
\cos\varphi_{\text{FL}} - 0.05 & \text{if } 0.50 \le L < 0.75 \\
\cos\varphi_{\text{FL}} & \text{if } L \ge 0.75
\end{cases}
$$

### API Usage

```java
ElectricalMotor motor = new ElectricalMotor();
motor.setEfficiencyClass("IE3");
motor.setPoles(4);
motor.sizeMotor(250.0, 1.10, "IEC");  // 250 kW shaft, 10% margin

// Read results
System.out.println("Rated: " + motor.getRatedPowerKW() + " kW");
System.out.println("Speed: " + motor.getRatedSpeedRPM() + " RPM");
System.out.println("Efficiency: " + motor.getEfficiencyPercent() + " %");
System.out.println("Current: " + motor.getRatedCurrentA() + " A");
System.out.println("Frame: " + motor.getFrameSize());
System.out.println("Weight: " + motor.getWeightKg() + " kg");

// Part-load performance
double eff_50 = motor.getEfficiencyAtLoad(0.50);
double pf_50 = motor.getPowerFactorAtLoad(0.50);
```

---

## 2. Variable Frequency Drive — VFD (IEEE 519)

### Topology Selection

The VFD topology is automatically selected based on voltage and power:

| Voltage | Power | Topology | Pulse Config | THD | Active Rectifier |
|---------|-------|----------|-------------|-----|:----------------:|
| ≤ 690 V | ≤ 250 kW | 2-level | 6-pulse | 35% | No |
| ≤ 690 V | > 250 kW | 2-level | AFE | 5% | Yes |
| 690–3300 V | ≤ 2000 kW | 3-level | 12-pulse | 10% | No |
| > 3300 V | > 2000 kW | Multi-level | AFE | 3% | Yes |

### VFD Electrical Input

The VFD adds its own losses on top of the motor input:

$$
P_{\text{input,VFD}} = \frac{P_{\text{input,motor}}}{\eta_{\text{VFD}}}
$$

$$
P_{\text{input,motor}} = \frac{P_{\text{shaft}}}{\eta_{\text{motor}}}
$$

Therefore the total electrical input from the bus:

$$
P_{\text{electrical}} = \frac{P_{\text{shaft}}}{\eta_{\text{motor}} \times \eta_{\text{VFD}}}
$$

### VFD Efficiency at Part Load

VFD efficiency degrades at low load and low speed:

$$
\eta_{\text{VFD}}(L, s) = \eta_{\text{rated}} - \Delta\eta_L - \Delta\eta_s
$$

where:

$$
\Delta\eta_L = \begin{cases} 5\% & \text{if } L < 0.25 \\ 2\% & \text{if } 0.25 \le L < 0.5 \\ 0 & \text{otherwise} \end{cases}
$$

$$
\Delta\eta_s = \begin{cases} 2\% & \text{if } s < 0.3 \\ 0 & \text{otherwise} \end{cases}
$$

### Heat Dissipation and Cooling

$$
Q_{\text{heat}} = P_{\text{rated}} \times \left(1 - \frac{\eta_{\text{VFD}}}{100}\right)
$$

| Condition | Cooling Method |
|-----------|---------------|
| $P > 500$ kW or $V > 3300$ V | Water cooling |
| Otherwise | Air cooling |

### Harmonic Distortion (IEEE 519)

The Total Harmonic Distortion (THD) in current is defined as:

$$
\text{THD}_I = \frac{\sqrt{\sum_{h=2}^{\infty} I_h^2}}{I_1} \times 100\%
$$

IEEE 519 limits for general systems are typically 5–8% THD at the PCC (Point
of Common Coupling). The framework flags THD levels and automatically selects
input filters or active front-end (AFE) rectifiers when THD exceeds limits.

### API Usage

```java
VariableFrequencyDrive vfd = new VariableFrequencyDrive();
vfd.sizeVFD(motor);  // motor already sized

System.out.println("Topology: " + vfd.getTopologyType());
System.out.println("THD: " + vfd.getThdCurrentPercent() + " %");
System.out.println("Efficiency: " + vfd.getEfficiencyPercent() + " %");
System.out.println("Cooling: " + vfd.getCoolingMethod());
System.out.println("Heat dissipation: " + vfd.getHeatDissipationKW() + " kW");
```

---

## 3. Cable Sizing (IEC 60502 / IEC 60364)

### Ampacity Selection

Cables are sized to carry the derated load current. The required base ampacity:

$$
I_{\text{base,required}} = \frac{I_{\text{load}}}{f_{\text{temp}} \times f_{\text{group}} \times f_{\text{depth}}}
$$

The cable cross-section is selected from the IEC 60228 standard series
(1.5 mm² to 630 mm²) such that the base ampacity of the cable equals or
exceeds $I_{\text{base,required}}$.

### Temperature Derating (IEC 60364-5-52)

For XLPE insulated cables rated at 90°C conductor temperature:

$$
f_{\text{temp}} = \sqrt{\frac{T_{\text{max}} - T_{\text{ambient}}}{T_{\text{max}} - T_{\text{base}}}}
$$

where $T_{\text{max}} = 90°\text{C}$ and $T_{\text{base}} = 30°\text{C}$.

| Ambient (°C) | Derating Factor |
|:---:|:---:|
| 25 | 1.00 |
| 30 | 1.00 |
| 35 | 0.96 |
| 40 | 0.91 |
| 45 | 0.87 |
| 50 | 0.82 |

### Grouping Derating

| Installation Method | Factor |
|:---|:---:|
| Ladder / Open air | 1.00 |
| Tray | 0.85 |
| Direct burial | 0.90 |
| Conduit | 0.80 |

### Voltage Drop

Three-phase voltage drop along a cable of length $L$:

$$
\Delta V = \sqrt{3} \times I \times L \times (r \cos\varphi + x \sin\varphi)
$$

$$
\Delta V\% = \frac{\Delta V}{V_{\text{system}}} \times 100
$$

where:
- $r = \rho / A$ is the resistance per metre ($\rho_{\text{Cu}} = 0.0175\;\Omega\cdot\text{mm}^2/\text{m}$, $\rho_{\text{Al}} = 0.028$)
- $x \approx 0.00008\;\Omega/\text{m}$ (typical reactance)
- $\cos\varphi = 0.85$ (assumed for voltage drop calculation)

The maximum allowable voltage drop is 5% (IEC 60364-5-52). If exceeded,
the algorithm automatically upsizes the cable.

### Short-Circuit Withstand (IEC 60949)

The adiabatic short-circuit withstand current:

$$
I_{\text{sc}} = \frac{k \times A}{\sqrt{t}}
$$

where $k$ is the material constant ($k_{\text{Cu}} = 143$, $k_{\text{Al}} = 94$ A·√s/mm²),
$A$ is the cross-section in mm², and $t$ is the fault duration in seconds.

### API Usage

```java
ElectricalCable cable = new ElectricalCable();
cable.setLengthM(100.0);
cable.sizeCable(150.0, 400.0, 100.0, "Tray", 40.0);

System.out.println("Cross-section: " + cable.getCrossSectionMM2() + " mm²");
System.out.println("Ampacity: " + cable.getAmpacityA() + " A");
System.out.println("Voltage drop: " + cable.getVoltageDropPercent() + " %");
System.out.println("SC withstand: " + cable.getShortCircuitWithstandKA() + " kA");
```

---

## 4. Transformer Sizing (IEC 60076)

### Rating Selection

Transformers are selected from IEC standard ratings (100 kVA to 25 MVA) with
a 15% margin:

$$
S_{\text{rated}} = \min \{ S_{\text{std}} \mid S_{\text{std}} \ge S_{\text{load}} \times 1.15 \}
$$

### Loss Estimation

| Loss Type | Typical Value | Description |
|-----------|:---:|-------------|
| No-load (iron) | 0.2% of rating | Core hysteresis + eddy current |
| Full-load (copper) | 1.0% of rating | Winding $I^2R$ |

$$
\eta_{\text{xfmr}} = \left(1 - \frac{P_{\text{NL}} + P_{\text{FL}}}{S_{\text{rated}}}\right) \times 100\%
$$

### Impedance

| Primary Voltage | Typical $Z\%$ |
|:---:|:---:|
| > 30 kV | 10% |
| 10–30 kV | 6% |
| < 10 kV | 4% |

### Cooling Types

| Rating | Cooling | Description |
|:---:|:---:|-------------|
| ≤ 5 MVA | ONAN | Oil Natural, Air Natural |
| 5–10 MVA | ONAF | Oil Natural, Air Forced |
| > 10 MVA | OFAF | Oil Forced, Air Forced |

---

## 5. Switchgear / MCC (IEC 61439)

### Starter Type Selection

The starter type is automatically selected based on motor size and VFD usage:

| Motor Size | With VFD | Starter Type |
|-----------|:---:|:---:|
| ≤ 11 kW | No | DOL (Direct On Line) |
| 11–200 kW | No | Star-Delta |
| > 200 kW | No | Soft Starter |
| Any | Yes | VFD |

### Circuit Breaker Sizing

$$
I_{\text{CB}} = \min \{ I_{\text{std}} \mid I_{\text{std}} \ge 1.25 \times I_{\text{FL}} \}
$$

Standard ratings: 100, 160, 250, 400, 630, 800, 1000, 1250, 1600, 2000, 2500, 3150, 4000 A.

### Fuse Rating

$$
I_{\text{fuse}} = \min \{ I_{\text{std,fuse}} \mid I_{\text{std,fuse}} \ge 1.6 \times I_{\text{FL}} \}
$$

### Short-Circuit Rating

| System Voltage | Typical Short-Circuit Rating |
|:---:|:---:|
| > 6 kV | 40 kA |
| 1–6 kV | 31.5 kA |
| < 1 kV | 25 kA |

---

## 6. Hazardous Area Classification (IECEx / ATEX)

### Zone Classification (IEC 60079-10)

| Equipment Type | Zone | EPL | Ex Protection |
|:---|:---:|:---:|:---:|
| Compressor, Pump, Separator | Zone 1 | Gb | Ex d |
| Heat Exchanger, Cooler, Pipeline | Zone 2 | Gc | Ex e |
| Non-hydrocarbon service | Safe area | — | None |

### Temperature Classes (IEC 60079-20-1)

| Class | Max Surface Temp (°C) | Typical Gas |
|:---:|:---:|:---|
| T1 | 450 | Hydrogen |
| T2 | 300 | Ethylene |
| T3 | 200 | Gasoline, Hexane |
| T4 | 135 | Acetaldehyde |
| T5 | 100 | Carbon disulphide |
| T6 | 85 | — |

### Ex Marking Format

The framework generates the full Ex marking string:

```
Ex d IIA T3 Gb
```

Components: Protection type + Gas group + Temperature class + Equipment
protection level.

---

## 7. Power Triangle and Load Analysis

### Power Triangle

For AC systems the relationship between active, reactive, and apparent power:

$$
S = P + jQ
$$

$$
|S| = \frac{P}{\cos\varphi}
$$

$$
Q = S \times \sin(\arccos(\cos\varphi))
$$

where:
- $P$ = Active power (kW) — does real work
- $Q$ = Reactive power (kVAR) — sustains magnetic fields
- $S$ = Apparent power (kVA) — total power drawn
- $\cos\varphi$ = Power factor

### Full-Load Current (3-Phase)

$$
I_{\text{FL}} = \frac{P_{\text{kW}} \times 1000}{\sqrt{3} \times V \times \cos\varphi}
$$

### Starting Current

| Starting Method | Starting Current |
|:---|:---:|
| VFD | $1.0 \times I_{\text{FL}}$ |
| DOL | $6.0\text{–}7.0 \times I_{\text{FL}}$ |
| Star-Delta | $2.0\text{–}2.3 \times I_{\text{FL}}$ |

### Load List Aggregation (IEC 61936)

The electrical load list sums all equipment loads with demand and diversity
factors:

$$
P_{\text{max,demand}} = \sum_{i} P_{\text{absorbed},i} \times f_{\text{demand},i} \times f_{\text{diversity},i}
$$

$$
S_{\text{max,demand}} = \sum_{i} \frac{P_{\text{max,demand},i}}{\cos\varphi_i}
$$

Overall power factor:

$$
\cos\varphi_{\text{overall}} = \frac{P_{\text{max,demand}}}{S_{\text{max,demand}}}
$$

Transformer sizing with design margin:

$$
S_{\text{transformer}} = S_{\text{max,demand}} \times 1.15
$$

Generator sizing (extra margin):

$$
S_{\text{generator}} = S_{\text{max,demand}} \times 1.15 \times 1.10
$$

---

## 8. Efficiency Chain

The total efficiency from electrical supply to shaft output:

$$
\eta_{\text{total}} = \eta_{\text{xfmr}} \times \eta_{\text{VFD}} \times \eta_{\text{motor}}
$$

Total losses:

$$
P_{\text{loss}} = P_{\text{electrical}} - P_{\text{shaft}}
$$

For a typical system (IE3 motor, VFD, transformer):
- Transformer: ~98.5%
- VFD: ~97%
- Motor: ~95%
- **Overall: ~91%**

---

## 9. ProcessSystem Integration

### Workflow

```java
// Step 1: Build and run process
ProcessSystem process = new ProcessSystem();
Stream feed = new Stream("Feed", gas);
Compressor comp1 = new Compressor("1st Stage", feed);
comp1.setOutletPressure(30.0);
Cooler cooler1 = new Cooler("Intercooler", comp1.getOutletStream());
cooler1.setOutTemperature(308.15);
Compressor comp2 = new Compressor("2nd Stage", cooler1.getOutletStream());
comp2.setOutletPressure(80.0);
Pump pump = new Pump("Export Pump", liquidStream);
pump.setOutletPressure(90.0);

process.add(feed);
process.add(comp1);
process.add(cooler1);
process.add(comp2);
process.add(pump);
process.run();

// Step 2: Run electrical designs
process.runAllElectricalDesigns();

// Step 3: Get load list
ElectricalLoadList loadList = process.getElectricalLoadList();
loadList.calculateSummary();
System.out.println("Total connected: " + loadList.getTotalConnectedLoadKW() + " kW");
System.out.println("Max demand: " + loadList.getMaximumDemandKW() + " kW");
System.out.println("Required transformer: " + loadList.getRequiredTransformerKVA() + " kVA");
System.out.println("Overall PF: " + loadList.getOverallPowerFactor());

// Step 4: JSON reports
System.out.println(loadList.toJson());
```

### Individual Equipment Design

```java
ElectricalDesign design = process.getEquipmentElectricalDesign("1st Stage");
System.out.println(design.toJson());
```

### Compressor with VFD

```java
Compressor comp = new Compressor("VFD Compressor", feed);
comp.setUseVFD(true);
// The CompressorElectricalDesign auto-detects VFD from driver type
// and includes auxiliary loads in the total
CompressorElectricalDesign ced =
    (CompressorElectricalDesign) comp.getElectricalDesign();
ced.calcDesign();
System.out.println("Total with auxiliaries: " + ced.getTotalConnectedLoadKW() + " kW");
```

---

## 10. Applicable Standards

| Standard | Scope | Used By |
|:---|:---|:---|
| IEC 60034-30-1 | Motor efficiency classes IE1–IE4 | `ElectricalMotor` |
| IEC 60072 | Motor frame sizes | `ElectricalMotor` |
| IEC 60228 | Cable conductor sizes | `ElectricalCable` |
| IEC 60364-5-52 | Cable installation / derating | `ElectricalCable` |
| IEC 60502 | Power cables up to 36 kV | `ElectricalCable` |
| IEC 60949 | Short-circuit thermal withstand | `ElectricalCable` |
| IEC 60076 | Power transformers | `Transformer` |
| IEC 61439 | LV switchgear assemblies | `Switchgear` |
| IEC 61936 | Power installations > 1 kV | `ElectricalLoadList` |
| IEC 60079-10 | Hazardous area classification | `HazardousAreaClassification` |
| IECEx / ATEX | Equipment for explosive atmospheres | `HazardousAreaClassification` |
| IEEE 519 | Harmonic limits | `VariableFrequencyDrive` |
| NEMA MG1 | Motor standards (US) | `ElectricalMotor` (NEMA mode) |

---

## Class Reference

| Class | Package | Description |
|:---|:---|:---|
| `ElectricalDesign` | `process.electricaldesign` | Base class — sizes motor, VFD, cables, switchgear |
| `ElectricalDesignResponse` | `process.electricaldesign` | JSON serialization helper |
| `ElectricalMotor` | `process.electricaldesign.components` | AC induction motor model |
| `VariableFrequencyDrive` | `process.electricaldesign.components` | VFD with harmonics / topology |
| `ElectricalCable` | `process.electricaldesign.components` | Cable sizing with derating |
| `Transformer` | `process.electricaldesign.components` | Power transformer model |
| `Switchgear` | `process.electricaldesign.components` | MCC / switchgear bucket |
| `HazardousAreaClassification` | `process.electricaldesign.components` | Zone / Ex marking |
| `CompressorElectricalDesign` | `process.electricaldesign.compressor` | Compressor-specific design |
| `PumpElectricalDesign` | `process.electricaldesign.pump` | Pump-specific design |
| `SeparatorElectricalDesign` | `process.electricaldesign.separator` | Separator auxiliary loads (valves, instruments, lighting, heat tracing) |
| `HeatExchangerElectricalDesign` | `process.electricaldesign.heatexchanger` | Heat exchanger design (electric heater / air cooler / shell-and-tube) |
| `PipelineElectricalDesign` | `process.electricaldesign.pipeline` | Pipeline heat tracing, cathodic protection, instrumentation |
| `SystemElectricalDesign` | `process.electricaldesign.system` | Plant-wide aggregation, transformer and generator sizing |
| `LoadItem` | `process.electricaldesign.loadanalysis` | Single load entry |
| `ElectricalLoadList` | `process.electricaldesign.loadanalysis` | Plant-wide load aggregation |

---

## Equipment-Specific Electrical Design Details

### Separator Electrical Design

Separators have no rotating equipment. All electrical loads are auxiliary:

| Load | Default | Typical Range |
|:---|:---:|:---:|
| Control valve actuators | 3 × 1.0 kW = 3.0 kW | 0.5–2 kW each |
| Instrumentation | 2.0 kW | 1–3 kW |
| Lighting (hazardous area rated) | 0.5 kW | 0.5–1 kW |
| Heat tracing (optional) | 0 kW | 5–20 kW |

```java
Separator sep = new Separator("HP Sep", feed);
sep.run();

SeparatorElectricalDesign elecDesign =
    (SeparatorElectricalDesign) sep.getElectricalDesign();
elecDesign.setNumberOfControlValves(4);
elecDesign.setHasHeatTracing(true);
elecDesign.setHeatTracingKW(10.0);
elecDesign.calcDesign();

System.out.println("Total auxiliary: " + elecDesign.getTotalAuxiliaryKW() + " kW");
```

### Heat Exchanger Electrical Design

The design auto-detects the type from the equipment class:

| Equipment Class | Detected Type | Electrical Scope |
|:---|:---|:---|
| `Heater` | `ELECTRIC_HEATER` | Full thermal duty as electrical — motor/cable sizing via base class |
| `Cooler` | `AIR_COOLER` | Fan motors sized at ~1% of duty (min 2 kW per fan) |
| Manual override | `SHELL_AND_TUBE` | Instrumentation + CW pump only (no motor) |

```java
Heater heater = new Heater("Electric Heater", feed);
heater.setOutTemperature(273.15 + 80.0);
heater.run();

HeatExchangerElectricalDesign hxDesign =
    (HeatExchangerElectricalDesign) heater.getElectricalDesign();
hxDesign.calcDesign();

System.out.println("Type: " + hxDesign.getHeatExchangerType());
System.out.println("Input: " + hxDesign.getElectricalInputKW() + " kW");
```

### Pipeline Electrical Design

Pipelines have no shaft power but may have significant distributed loads:

| Load | Default | Formula |
|:---|:---:|:---|
| Heat tracing | Off | $P_{\text{EHT}} = W_{\text{per\_m}} \times L / 1000$ (kW) |
| Cathodic protection | Off | Fixed kW (default 2.0 kW per TR unit) |
| Instrumentation | 1.0 kW | Fixed |

```java
AdiabaticPipe pipe = new AdiabaticPipe("Export Line", feed);
pipe.setLength(50000.0);
pipe.setDiameter(0.508);
pipe.run();

PipelineElectricalDesign pipeDesign =
    (PipelineElectricalDesign) pipe.getElectricalDesign();
pipeDesign.setHasHeatTracing(true);
pipeDesign.setHeatTracingWPerM(25.0);  // 25 W/m
pipeDesign.setHasCathodicProtection(true);
pipeDesign.setCathodicProtectionKW(3.0);
pipeDesign.calcDesign();

// Heat tracing: 25 W/m × 50000 m = 1250 kW
System.out.println("Total: " + pipeDesign.getTotalAuxiliaryKW() + " kW");
```

### System Electrical Design (Plant-Wide)

Aggregates all equipment loads and adds system-level requirements:

$$
P_{\text{plant}} = P_{\text{process}} + P_{\text{utility}} + P_{\text{UPS}}
$$

$$
S_{\text{transformer}} = \frac{P_{\text{plant}} \times (1 + f_{\text{expansion}})}{\cos\varphi}
$$

| Parameter | Default | Description |
|:---|:---:|:---|
| Utility load | 7% of process | HVAC, lighting, fire & gas |
| UPS load | 2% of process | Critical instrumentation |
| Future expansion | 15% | Design margin for growth |
| Emergency generator | 35% of plant | Essential loads |
| Main bus voltage | 11 kV | HV distribution |
| Distribution voltage | 400 V | LV distribution |

```java
ProcessSystem process = new ProcessSystem();
// ... add equipment ...
process.run();

SystemElectricalDesign sysDesign = process.getSystemElectricalDesign();

System.out.println("Process load:    " + sysDesign.getTotalProcessLoadKW() + " kW");
System.out.println("Plant load:      " + sysDesign.getTotalPlantLoadKW() + " kW");
System.out.println("Main transformer: " + sysDesign.getMainTransformerKVA() + " kVA");
System.out.println("Emergency gen:   " + sysDesign.getEmergencyGeneratorKVA() + " kVA");
```
