---
title: "Motor Mechanical Design and Combined Equipment Design Report"
description: "Physical and mechanical design of electric motors per IEC 60034, IEEE 841, ISO 10816-3, and NORSOK S-002/E-001. Covers foundation design, vibration analysis, cooling classification, bearing life, noise assessment, enclosure selection, derating, and combined equipment design reports."
---

# Motor Mechanical Design

NeqSim provides a `MotorMechanicalDesign` class for the physical and mechanical aspects of electric
motor specification, complementing the **electrical** design handled by `ElectricalDesign` and
`ElectricalMotor`. Together with `EquipmentDesignReport`, this enables a complete motor and
equipment design from a single process simulation result.

## When to Use

| You need ... | Use class |
|:---|:---|
| Motor electrical sizing (kW, voltage, efficiency class) | `ElectricalDesign` / `ElectricalMotor` |
| Motor physical design (foundation, vibration, cooling, bearings, noise) | `MotorMechanicalDesign` |
| Combined mechanical + electrical + motor report | `EquipmentDesignReport` |

---

## Standards Reference

| Standard | Scope | Used In |
|:---|:---|:---|
| IEC 60034-1 | Rating and performance, derating | Altitude and temperature derating |
| IEC 60034-5 | Degrees of protection (IP code) | Enclosure / IP rating |
| IEC 60034-6 | Methods of cooling (IC code) | Cooling classification |
| IEC 60034-9 | Noise limits | Sound power level limits |
| IEC 60034-14 | Mechanical vibration | Vibration grade A/B limits |
| IEC 60034-30-1 | Efficiency classes (IE1–IE4) | Motor sizing (via ElectricalMotor) |
| IEEE 841 | Petroleum/chemical industry motors | Foundation ratio (3:1), min bearing life |
| ISO 10816-3 | Vibration evaluation — industrial machines | Vibration zone classification (A/B/C/D) |
| ISO 281 | Rolling bearing life calculation | L10 bearing life |
| NORSOK E-001 | Electrical systems (offshore) | Motor installation requirements |
| NORSOK S-002 | Working environment | Noise limit 83 dB(A) at 1 m |
| IEC 60079 | Equipment for explosive atmospheres | Ex marking for Zone 0/1/2 |

---

## Quick Start

```java
// Standalone — from known shaft power
MotorMechanicalDesign motorDesign = new MotorMechanicalDesign(250.0); // 250 kW
motorDesign.setPoles(4);
motorDesign.setAmbientTemperatureC(45.0);
motorDesign.setAltitudeM(500.0);
motorDesign.setHazardousZone(1);
motorDesign.setGasGroup("IIA");
motorDesign.calcDesign();

System.out.println(motorDesign.toJson());
```

```java
// From electrical design — reads motor parameters automatically
Compressor comp = new Compressor("export", feed);
comp.setOutletPressure(80.0);
comp.run();

ElectricalDesign elecDesign = comp.getElectricalDesign();
elecDesign.calcDesign();

MotorMechanicalDesign motorDesign = new MotorMechanicalDesign(elecDesign);
motorDesign.calcDesign();

System.out.println("Foundation mass: " + motorDesign.getRequiredFoundationMassKg() + " kg");
System.out.println("Vibration zone:  " + motorDesign.getVibrationZone());
System.out.println("Cooling code:    " + motorDesign.getCoolingCode());
System.out.println("L10 bearing life:" + motorDesign.getBearingL10LifeHours() + " hours");
System.out.println("Noise at 1m:     " + motorDesign.getSoundPressureLevelAt1mDbA() + " dB(A)");
System.out.println("NORSOK S-002 OK: " + motorDesign.isNoiseWithinNorsokLimit());
```

---

## Design Calculations

### 1. Environmental Derating (IEC 60034-1)

Motors are rated at standard conditions: 40 °C ambient, altitude up to 1000 m.
Beyond these limits, output must be derated:

**Altitude derating:**

$$
f_{\text{alt}} = 1 - \frac{\max(0,\; h - 1000)}{100} \times 0.01
$$

where $h$ is altitude in meters. Each 100 m above 1000 m reduces output by 1%.

**Temperature derating:**

$$
f_{\text{temp}} = 1 - \max(0,\; T_{\text{amb}} - 40) \times 0.025
$$

where $T_{\text{amb}}$ is ambient temperature in °C. Each degree above 40 °C
reduces output by 2.5%.

**Combined derating:**

$$
f_{\text{combined}} = f_{\text{alt}} \times f_{\text{temp}}
$$

### 2. Foundation Design (IEEE 841)

**Static load** — motor weight:

$$
F_{\text{static}} = m_{\text{motor}} \times g
$$

**Dynamic load** — due to residual unbalance per IEC 60034-14 Grade A:

$$
F_{\text{dynamic}} \approx 0.1 \times F_{\text{static}}
$$

**Foundation mass** — IEEE 841 recommends a minimum 3:1 mass ratio
(concrete foundation to motor weight) for adequate vibration isolation:

$$
m_{\text{foundation}} = 3 \times m_{\text{motor}}
$$

Foundation type recommendation:

| Motor Power | Foundation Type |
|:---:|:---|
| < 75 kW | Steel baseplate on concrete pad |
| 75 – 500 kW | Concrete block foundation |
| > 500 kW | Concrete block with spring isolation |

### 3. Cooling Classification (IEC 60034-6)

| IC Code | Description | Power Range |
|:---|:---|:---|
| IC411 | TEFC — totally enclosed, fan-cooled | ≤ 315 kW |
| IC611 | Separate external fan (forced ventilation) | 315 – 2000 kW |
| IC81W | Water-cooled (closed circuit) | > 2000 kW |

Heat dissipation is estimated from motor efficiency:

$$
Q_{\text{loss}} = P_{\text{shaft}} \times \left(\frac{1}{\eta} - 1\right)
$$

### 4. Bearing Selection (ISO 281)

| Motor Power | Bearing Type |
|:---:|:---|
| < 200 kW | Deep groove ball bearings |
| 200 – 1000 kW | Cylindrical roller bearings (DE), ball (NDE) |
| > 1000 kW | Cylindrical roller bearings (both ends) |

**L10 life** per ISO 281:

$$
L_{10} = \left(\frac{C}{P}\right)^p \times \frac{10^6}{60 \times n}
$$

where $C$ is dynamic bearing capacity, $P$ is equivalent load, $p$ = 3 for ball
or 10/3 for roller, $n$ is speed in RPM. IEEE 841 requires minimum L10 of 3 years
(26,280 hours) continuous operation.

### 5. Vibration Analysis (IEC 60034-14 / ISO 10816-3)

IEC 60034-14 Grade A limits (maximum vibration velocity at bearing housing):

| Power Range | Max. Vibration (mm/s RMS) |
|:---:|:---:|
| 0.16 – 15 kW | 1.6 |
| 15 – 75 kW | 2.5 |
| 75 – 300 kW | 3.5 |
| > 300 kW | 4.5 |

ISO 10816-3 classifies the operating condition into zones:

| Zone | Condition | Action |
|:---|:---|:---|
| A | New machine | Normal operation |
| B | Long-term acceptable | No action needed |
| C | Short-term acceptable | Plan corrective action |
| D | Damaging | Immediate shutdown |

### 6. Noise Assessment (IEC 60034-9 / NORSOK S-002)

Sound power level limits depend on motor power and pole count per IEC 60034-9.

**NORSOK S-002 workplace limit:** 83 dB(A) at 1 m from equipment.

Sound pressure at 1 m is estimated from sound power level:

$$
L_p = L_w - 10 \log_{10}(2 \pi r^2) \approx L_w - 8 \; \text{dB}
$$

> **Note:** Motors with VFD may produce 3–8 dB(A) additional noise from
> switching harmonics in the winding. This is flagged in design notes.

### 7. Enclosure and Protection (IEC 60034-5 / IEC 60079)

| Zone | IP Rating | Ex Protection | Enclosure Type |
|:---|:---|:---|:---|
| Safe area | IP55 | — | TEFC |
| Zone 2 | IP55 | Ex nA (non-sparking) | TEFC |
| Zone 1 | IP55 | Ex d (flameproof) | Flameproof |
| Zone 0 | IP66 | Ex d (flameproof) | Flameproof, enhanced |

---

## API Reference

### MotorMechanicalDesign

**Package:** `neqsim.process.mechanicaldesign.motor`

#### Constructors

| Constructor | Description |
|:---|:---|
| `MotorMechanicalDesign()` | Default — set shaft power via setter |
| `MotorMechanicalDesign(double shaftPowerKW)` | From known shaft power |
| `MotorMechanicalDesign(ElectricalDesign elecDesign)` | From electrical design (reads motor params) |

#### Configuration Methods

| Method | Parameters | Description |
|:---|:---|:---|
| `setPoles(int)` | 2, 4, 6, 8 | Motor pole count (default: 4) |
| `setAmbientTemperatureC(double)` | °C | Ambient temperature (default: 40) |
| `setAltitudeM(double)` | meters | Installation altitude (default: 0) |
| `setHazardousZone(int)` | -1, 0, 1, 2 | Hazardous zone (-1 = safe) |
| `setGasGroup(String)` | IIA, IIB, IIC | Gas group for Ex rating |
| `setMotorStandard(String)` | IEC, NEMA | Motor frame standard |
| `setHasVFD(boolean)` | — | Whether motor has VFD |

#### Design and Output Methods

| Method | Returns | Description |
|:---|:---|:---|
| `calcDesign()` | void | Run all design calculations |
| `toJson()` | String | Comprehensive JSON report |
| `toMap()` | Map | Design data as ordered map |

#### Result Getters

| Method | Unit | Description |
|:---|:---|:---|
| `getMotorWeightKg()` | kg | Estimated motor weight |
| `getTotalFoundationLoadKN()` | kN | Static + dynamic foundation load |
| `getRequiredFoundationMassKg()` | kg | Minimum foundation mass (IEEE 841) |
| `getFoundationType()` | — | Recommended foundation type |
| `getCoolingCode()` | — | IEC 60034-6 IC code |
| `getHeatDissipationKW()` | kW | Motor heat loss |
| `getBearingType()` | — | Ball or roller bearing type |
| `getBearingL10LifeHours()` | hours | ISO 281 L10 bearing life |
| `getVibrationZone()` | — | ISO 10816-3 zone (A/B/C/D) |
| `getMaxVibrationMmS()` | mm/s RMS | IEC 60034-14 Grade A limit |
| `getSoundPressureLevelAt1mDbA()` | dB(A) | Sound pressure at 1 m |
| `isNoiseWithinNorsokLimit()` | boolean | True if ≤ 83 dB(A) at 1 m |
| `getCombinedDeratingFactor()` | 0–1 | Altitude × temperature derating |
| `getDeratedPowerKW()` | kW | Available power after derating |
| `getIpRating()` | — | IP protection rating |
| `getExMarking()` | — | Ex marking string (if hazardous) |
| `getAppliedStandards()` | List | Standards applied in design |
| `getDesignNotes()` | List | Warnings and recommendations |

---

## EquipmentDesignReport

A general-purpose class that generates a **combined design report** for any process equipment,
aggregating mechanical design, electrical design, and motor mechanical design into a single
JSON report with a feasibility verdict.

**Package:** `neqsim.process.mechanicaldesign`

### Quick Start

```java
Compressor comp = new Compressor("export", feed);
comp.setOutletPressure(80.0);
comp.run();

EquipmentDesignReport report = new EquipmentDesignReport(comp);
report.setUseVFD(true);
report.setRatedVoltageV(6600);
report.setHazardousZone(1);
report.setAmbientTemperatureC(45.0);
report.setAltitudeM(500.0);
report.generateReport();

System.out.println("Verdict: " + report.getVerdict());
System.out.println(report.toJson());
```

### Configuration

| Method | Default | Description |
|:---|:---:|:---|
| `setUseVFD(boolean)` | false | Whether to use a variable frequency drive |
| `setRatedVoltageV(double)` | 400 | Supply voltage (V) |
| `setFrequencyHz(double)` | 50 | Supply frequency (Hz) |
| `setHazardousZone(int)` | -1 | Hazardous area zone (-1 = safe, 0, 1, 2) |
| `setGasGroup(String)` | IIA | Gas group for Ex classification |
| `setAmbientTemperatureC(double)` | 40 | Ambient temperature (°C) |
| `setAltitudeM(double)` | 0 | Installation altitude (m) |
| `setMotorStandard(String)` | IEC | Motor standard (IEC or NEMA) |
| `setMotorPoles(int)` | 4 | Motor pole count |
| `setMotorSizingMargin(double)` | 1.10 | Motor sizing margin (10%) |
| `setCableLengthM(double)` | 50 | Cable length MCC to motor (m) |

### Verdict Logic

The report evaluates all design results and produces one of three verdicts:

| Verdict | Meaning |
|:---|:---|
| `FEASIBLE` | All checks pass |
| `FEASIBLE_WITH_WARNINGS` | Design possible but has warnings (noise, derating, oversizing) |
| `NOT_FEASIBLE` | Blocking issue found (motor undersized) |

**Issues checked:**

- Motor noise exceeds NORSOK S-002 limit (83 dB(A))
- Bearing L10 life below IEEE 841 minimum (3 years / 26,280 hours)
- Significant derating (combined factor < 0.8)
- Motor undersized (shaft power exceeds motor rating)
- Motor oversized (operating below 50% load)

### JSON Output Structure

```json
{
  "equipmentName": "export",
  "equipmentType": "Compressor",
  "verdict": "FEASIBLE_WITH_WARNINGS",
  "issues": ["WARNING: ..."],
  "mechanicalDesign": { ... },
  "electricalDesign": {
    "shaftPowerKW": 450.0,
    "electricalInputKW": 495.0,
    "motor": { ... },
    "vfd": { ... },
    "powerCable": { ... },
    "switchgear": { ... },
    "hazardousArea": { ... }
  },
  "motorMechanicalDesign": {
    "designInput": { "shaftPowerKW": 450.0, ... },
    "derating": { "combinedDeratingFactor": 0.95, ... },
    "foundation": { "totalLoadKN": 25.3, ... },
    "vibration": { "vibrationZone_ISO10816": "A", ... },
    "cooling": { "coolingCode_IEC60034_6": "IC611", ... },
    "bearings": { "L10LifeHours": 52000, ... },
    "noise": { "soundPressureAt1mDbA": 81.5, ... },
    "enclosure": { "ipRating": "IP55", "exMarking": "Ex d IIA T3 Gb" },
    "appliedStandards": [ ... ]
  }
}
```

### Load List Integration

Use `toLoadListEntry()` to get a summary suitable for an electrical load list:

```java
Map<String, Object> entry = report.toLoadListEntry();
// Returns: equipmentName, ratedMotorPowerKW, absorbedPowerKW,
//          electricalInputKW, apparentPowerKVA, powerFactor, ratedVoltageV, hasVFD
```

---

## Integration Workflow

The typical workflow for a complete equipment design is:

1. **Run the process simulation** — sizes the equipment thermodynamically
2. **Mechanical design** — wall thickness, weights, materials (via `getMechanicalDesign()`)
3. **Electrical design** — motor, VFD, cable, switchgear (via `getElectricalDesign()`)
4. **Motor mechanical design** — foundation, vibration, cooling, bearings, noise
5. **Combined report** — `EquipmentDesignReport` aggregates all three into one

```java
// Step 1: Process simulation
Compressor comp = new Compressor("HP Compressor", feed);
comp.setOutletPressure(100.0);
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(comp);
process.run();

// Steps 2-5: Combined report handles everything
EquipmentDesignReport report = new EquipmentDesignReport(comp);
report.setUseVFD(true);
report.setRatedVoltageV(6600);
report.setHazardousZone(1);
report.setGasGroup("IIA");
report.setAmbientTemperatureC(45.0);
report.generateReport();

// Access individual designs
MechanicalDesign mech = report.getMechanicalDesign();
ElectricalDesign elec = report.getElectricalDesign();
MotorMechanicalDesign motor = report.getMotorMechanicalDesign();

// Full JSON report
String json = report.toJson();
```

---

## Related Documentation

- [Electrical Design Guide](electrical-design) — motor sizing, VFD, cable, switchgear
- [Mechanical Design Overview](mechanical_design) — equipment mechanical design framework
- [Compressor Mechanical Design](CompressorMechanicalDesign) — API 617 compressor design
- [Mechanical Design Standards](mechanical_design_standards) — standards reference matrix
