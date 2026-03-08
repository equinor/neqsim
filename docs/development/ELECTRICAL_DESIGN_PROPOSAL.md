---
title: "Electrical Engineering Package: Architecture Proposal"
description: "Proposal for implementing electrical engineering design capabilities in NeqSim, mirroring the existing mechanical design architecture. Covers motors, VFDs, transformers, cables, switchgear, load analysis, and standards."
---

# Electrical Engineering Package — Architecture Proposal

## 1. Motivation

NeqSim already models **mechanical design** for process equipment (wall thickness, materials, weights, cost) via the `MechanicalDesign` class hierarchy. However, every piece of process equipment also has an **electrical scope**: motors, variable-speed drives, starters, cabling, switchgear, transformers, protection, hazardous area classification, and lighting/heat tracing.

Currently NeqSim tracks power only as a scalar mechanical shaft power (kW). There is no concept of:

- **Voltage level**, frequency, or phases
- **Power factor** (cos φ), reactive power (kVAR), apparent power (kVA)
- **Motor selection** (NEMA/IEC frame, efficiency class IE1–IE4, insulation class, enclosure)
- **Variable Frequency Drive (VFD)** sizing, harmonic distortion, input filter requirements
- **Cable sizing** (ampacity, voltage drop, short-circuit withstand)
- **Transformer sizing** and losses
- **Switchgear / MCC** ratings
- **Electrical one-line diagram** data
- **Total plant electrical load list** with diversity and contingency factors
- **Hazardous area classification** impact on equipment selection (Ex ratings)

Adding an `ElectricalDesign` layer — parallel to `MechanicalDesign` — enables NeqSim to produce equipment load lists, motor data sheets, one-line diagram data, cable schedules, and electrical cost estimates.

---

## 2. Design Principles

| Principle | How |
|-----------|-----|
| Mirror the MechanicalDesign pattern | `ElectricalDesign` base class per equipment, plugged in via `getElectricalDesign()` / `initElectricalDesign()` |
| Separation of concerns | Equipment classes stay focused on process; electrical scope is a separate object |
| Data-driven | Standards data (IEC motor frames, cable ampacity tables) loaded from CSV resources |
| Composable | `ElectricalDesign` owns child objects (`Motor`, `VFD`, `Cables`, `Starter`) that are independently testable |
| JSON-reportable | Every class implements `toJson()` for integration with report generators |
| Java 8 compatible | No var, List.of, records, etc. |

---

## 3. Package Structure

```
src/main/java/neqsim/process/
├── electricaldesign/                         ← NEW top-level package
│   ├── ElectricalDesign.java                 ← Base class (mirrors MechanicalDesign)
│   ├── ElectricalDesignResponse.java         ← JSON serialization helper
│   │
│   ├── components/                           ← Reusable electrical component models
│   │   ├── ElectricalMotor.java              ← Motor model (IEC/NEMA sizing)
│   │   ├── VariableFrequencyDrive.java       ← VFD model
│   │   ├── SoftStarter.java                  ← Soft-starter model
│   │   ├── DirectOnLineStarter.java          ← DOL starter
│   │   ├── ElectricalCable.java              ← Cable sizing model
│   │   ├── Transformer.java                  ← Transformer model
│   │   ├── Switchgear.java                   ← Switchgear / MCC bucket
│   │   ├── UninterruptiblePowerSupply.java   ← UPS model
│   │   ├── ElectricalHeatTracing.java        ← Heat tracing for pipes/vessels
│   │   └── HazardousAreaClassification.java  ← ATEX/IECEx zone classification
│   │
│   ├── loadanalysis/                         ← System-level analysis
│   │   ├── ElectricalLoadList.java           ← Aggregated load list
│   │   ├── LoadItem.java                     ← Single load entry
│   │   ├── LoadDiversityCalculator.java      ← Diversity & demand factors
│   │   └── PowerBalanceReport.java           ← Power balance / load flow summary
│   │
│   ├── standards/                            ← Electrical standards data
│   │   ├── IEC60034Standard.java             ← Motor efficiency classes (IE1-IE4)
│   │   ├── IEC60502Standard.java             ← Cable sizing tables
│   │   ├── IEC61439Standard.java             ← Switchgear standards
│   │   ├── IEEE841Standard.java              ← Petroleum/chemical industry motors
│   │   ├── NEMAMotorStandard.java            ← NEMA motor frame/sizing
│   │   └── ATEXClassification.java           ← ATEX/IECEx zone mapping
│   │
│   ├── data/                                 ← Data source abstractions
│   │   ├── ElectricalDesignDataSource.java   ← Interface for data loading
│   │   └── DatabaseElectricalDesignDataSource.java  ← CSV/H2 data loader
│   │
│   ├── compressor/                           ← Equipment-specific electrical designs
│   │   └── CompressorElectricalDesign.java
│   ├── pump/
│   │   └── PumpElectricalDesign.java
│   ├── heatexchanger/
│   │   └── HeatExchangerElectricalDesign.java
│   ├── separator/
│   │   └── SeparatorElectricalDesign.java
│   ├── pipeline/
│   │   └── PipelineElectricalDesign.java     ← Heat tracing, cathodic protection
│   └── system/
│       └── SystemElectricalDesign.java       ← Plant-wide aggregation
│
├── equipment/                                ← EXISTING (modified)
│   ├── ProcessEquipmentInterface.java        ← Add getElectricalDesign(), initElectricalDesign()
│   └── ProcessEquipmentBaseClass.java        ← Add default implementations
│
└── processmodel/
    └── ProcessSystem.java                    ← Add electrical load aggregation methods
```

---

## 4. Core Classes

### 4.1 ElectricalDesign (Base Class)

Mirrors `MechanicalDesign`. Every equipment can have one.

```java
package neqsim.process.electricaldesign;

public class ElectricalDesign implements java.io.Serializable {

    private ProcessEquipmentInterface processEquipment;

    // === Power requirements (derived from process) ===
    private double shaftPowerKW;           // From process equipment getPower()
    private double electricalInputKW;      // Including motor + VFD losses
    private double apparentPowerKVA;       // = electricalInputKW / powerFactor
    private double reactivePowerKVAR;      // = apparentPowerKVA × sin(φ)
    private double powerFactor;            // cos φ (typically 0.85–0.95)

    // === Voltage & frequency ===
    private double ratedVoltageV;          // e.g. 400, 690, 3300, 6600, 11000 V
    private double frequencyHz;            // 50 or 60 Hz
    private int phases;                    // 1 or 3

    // === Electrical components ===
    private ElectricalMotor motor;
    private VariableFrequencyDrive vfd;    // null if DOL or soft-start
    private ElectricalCable powerCable;
    private ElectricalCable controlCable;
    private Switchgear switchgear;
    private HazardousAreaClassification hazArea;

    // === Design margins ===
    private double motorSizingMargin;      // typically 1.10–1.15 (10–15% over shaft power)
    private double cableDeratingFactor;    // ambient temp, grouping, burial derating
    private double diversityFactor;        // for load list contribution (0–1)
    private boolean isContinuousDuty;      // S1 duty vs intermittent

    // === Standards ===
    private String motorStandard;          // "IEC" or "NEMA"
    private String cableStandard;          // "IEC 60502" or "NEC"
    private String hazAreaStandard;        // "IECEx" or "ATEX" or "NEC 500/505"

    // === Core methods ===
    public void calcDesign() { ... }       // Size all electrical components
    public void readDesignSpecifications() { ... }
    public String toJson() { ... }

    // === Derived values ===
    public double getFullLoadCurrentA() { ... }
    public double getStartingCurrentA() { ... }
    public double getCableVoltageDrop() { ... }
    public double getTotalElectricalLossesKW() { ... }
}
```

### 4.2 ElectricalMotor

```java
package neqsim.process.electricaldesign.components;

public class ElectricalMotor implements java.io.Serializable {

    // === Nameplate data ===
    private double ratedPowerKW;
    private double ratedVoltageV;
    private double ratedCurrentA;
    private double ratedSpeedRPM;
    private int poles;                      // 2, 4, 6, 8
    private double frequencyHz;
    private double efficiencyPercent;       // At full load
    private double powerFactorFL;          // At full load
    private String efficiencyClass;        // IE1, IE2, IE3, IE4 (IEC 60034-30-1)

    // === Motor sizing ===
    private String frameSize;              // IEC: 71, 80, 90, ..., 450  or NEMA: 143T, 286T, ...
    private String enclosureType;          // TEFC, TENV, ODP, TEAAC, etc.
    private String insulationClass;        // B, F, H
    private String dutyType;              // S1 (continuous), S2, S3, ...
    private double serviceFactor;          // 1.0 (IEC) or 1.15 (NEMA)

    // === Starting characteristics ===
    private double lockedRotorCurrentMultiplier;  // Typically 6–8× FLA
    private String startingMethod;         // DOL, Star-Delta, Soft-Start, VFD
    private double startingTorquePercent;   // % of FLT

    // === Hazardous area ===
    private String exProtection;           // Ex d, Ex e, Ex n, Ex p, Ex ia/ib
    private String temperatureClass;       // T1–T6
    private String gasGroup;              // IIA, IIB, IIC

    // === Weight & cost ===
    private double weightKg;
    private double estimatedCostUSD;

    // === Methods ===
    public void sizeMotor(double shaftPowerKW, double margin, String standard) { ... }
    public double getEfficiencyAtLoad(double loadFraction) { ... }
    public double getPowerFactorAtLoad(double loadFraction) { ... }
    public String toJson() { ... }
}
```

### 4.3 VariableFrequencyDrive

```java
package neqsim.process.electricaldesign.components;

public class VariableFrequencyDrive implements java.io.Serializable {

    private double ratedPowerKW;
    private double ratedCurrentA;
    private double inputVoltageV;
    private double outputVoltageV;
    private double maxOutputFrequencyHz;
    private double minOutputFrequencyHz;

    // === VFD characteristics ===
    private double efficiencyPercent;       // Typically 95–98%
    private double inputPowerFactor;       // Near unity with active front end
    private String topologyType;           // "2-level", "3-level", "Multi-level"
    private boolean hasActiveRectifier;    // Active front end (low harmonics)

    // === Harmonics ===
    private double thdCurrentPercent;      // Total Harmonic Distortion
    private boolean requiresInputFilter;   // Harmonic filter needed?
    private String pulseConfiguration;     // 6-pulse, 12-pulse, 18-pulse, AFE

    // === Speed range ===
    private double minSpeedPercent;        // Typically 10–30%
    private double maxSpeedPercent;        // Typically 100–120%
    private boolean hasFieldWeakeningRegion;

    // === Cooling ===
    private double heatDissipationKW;
    private String coolingMethod;          // "Air", "Water", "Oil"

    // === Physical ===
    private double weightKg;
    private double estimatedCostUSD;
    private String enclosureRating;        // IP20, IP21, IP54, IP55

    // === Methods ===
    public void sizeVFD(ElectricalMotor motor) { ... }
    public double getEfficiency(double loadFraction, double speedFraction) { ... }
    public double getElectricalInputKW(double motorInputKW) { ... }
    public double getHarmonicDistortion(int harmonicOrder) { ... }
    public String toJson() { ... }
}
```

### 4.4 ElectricalCable

```java
package neqsim.process.electricaldesign.components;

public class ElectricalCable implements java.io.Serializable {

    private double lengthM;
    private double crossSectionMM2;        // Conductor area (e.g. 2.5, 4, 6, ..., 630 mm²)
    private int numberOfCores;             // 3, 3+E, 4
    private String conductorMaterial;      // "Copper" or "Aluminium"
    private String insulationType;         // XLPE, PVC, EPR, MI (mineral insulated)

    // === Ratings ===
    private double ampacityA;              // Derated current carrying capacity
    private double baseAmpacityA;          // Before derating
    private double voltageDropPercent;     // At full load
    private double maxVoltageDropPercent;  // Limit (typically 5% running, 15% starting)

    // === Derating factors ===
    private double ambientTempDeratingFactor;
    private double groupingDeratingFactor;
    private double burialDepthDeratingFactor;
    private double altitudeDeratingFactor;

    // === Protection ===
    private double shortCircuitWithstandKA;  // Cable thermal withstand
    private double shortCircuitDurationS;    // Usually 1 s

    // === Installation ===
    private String installationMethod;     // "Tray", "Conduit", "Direct burial", "Ladder"
    private String routeReference;         // Cable route ID

    // === Methods ===
    public void sizeCable(double loadCurrentA, double voltageV, double lengthM,
                          String installMethod, double ambientTempC) { ... }
    public double calculateVoltageDrop(double currentA, double powerFactor) { ... }
    public String toJson() { ... }
}
```

### 4.5 CompressorElectricalDesign (Equipment-Specific Example)

```java
package neqsim.process.electricaldesign.compressor;

public class CompressorElectricalDesign extends ElectricalDesign {

    // === Compressor-specific ===
    private boolean useVFD;                // VSD control required?
    private double speedRangeMin;          // Minimum operating speed (fraction)
    private double speedRangeMax;          // Maximum operating speed (fraction)
    private boolean hasAntiSurgeValve;     // Electrically actuated
    private double antiSurgeValvePowerKW;  // Actuator power

    // === Auxiliary loads ===
    private double lubOilPumpPowerKW;
    private double sealGasPanelPowerKW;
    private double instrumentAirPowerKW;
    private double coolingFanPowerKW;      // If air-cooled aftercooler

    // === Instrumentation ===
    private int vibrationProbeCount;
    private int temperatureSensorCount;
    private double instrumentPowerKW;

    @Override
    public void calcDesign() {
        Compressor comp = (Compressor) getProcessEquipment();
        double shaftPower = comp.getPower("kW");

        // 1. Size motor
        getMotor().sizeMotor(shaftPower, getMotorSizingMargin(), getMotorStandard());

        // 2. Size VFD (if applicable)
        if (useVFD) {
            getVfd().sizeVFD(getMotor());
        }

        // 3. Calculate full electrical input
        double motorInput = shaftPower / (getMotor().getEfficiencyPercent() / 100.0);
        double electricalInput = useVFD
            ? getVfd().getElectricalInputKW(motorInput)
            : motorInput;
        setElectricalInputKW(electricalInput + getTotalAuxiliaryPowerKW());

        // 4. Size cables
        getPowerCable().sizeCable(getFullLoadCurrentA(), getRatedVoltageV(),
            getPowerCable().getLengthM(), "Tray", 40.0);

        // 5. Power factor
        double pf = useVFD ? getVfd().getInputPowerFactor()
                           : getMotor().getPowerFactorFL();
        setPowerFactor(pf);
        setApparentPowerKVA(getElectricalInputKW() / pf);
        setReactivePowerKVAR(getApparentPowerKVA()
            * Math.sin(Math.acos(pf)));
    }

    public double getTotalAuxiliaryPowerKW() {
        return lubOilPumpPowerKW + sealGasPanelPowerKW
             + instrumentAirPowerKW + coolingFanPowerKW
             + instrumentPowerKW + antiSurgeValvePowerKW;
    }
}
```

---

## 5. Integration with ProcessEquipmentInterface

Add two new methods to the equipment interface and base class, exactly mirroring the mechanical design pattern:

```java
// ProcessEquipmentInterface.java — add:
ElectricalDesign getElectricalDesign();
void initElectricalDesign();

// ProcessEquipmentBaseClass.java — add default implementations:
private ElectricalDesign electricalDesign;

@Override
public ElectricalDesign getElectricalDesign() {
    return electricalDesign;
}

@Override
public void initElectricalDesign() {
    electricalDesign = new ElectricalDesign(this);
}
```

Equipment classes override when they need specialization:

```java
// In Compressor.java:
private CompressorElectricalDesign electricalDesign;

@Override
public void initElectricalDesign() {
    electricalDesign = new CompressorElectricalDesign(this);
}

@Override
public CompressorElectricalDesign getElectricalDesign() {
    return electricalDesign;
}
```

---

## 6. ProcessSystem Integration

### 6.1 Electrical Load Aggregation

```java
// ProcessSystem.java — add:

public void initAllElectricalDesigns() {
    for (ProcessEquipmentInterface eq : unitOperations) {
        eq.initElectricalDesign();
    }
}

public void runAllElectricalDesigns() {
    for (ProcessEquipmentInterface eq : unitOperations) {
        ElectricalDesign ed = eq.getElectricalDesign();
        if (ed != null) {
            ed.calcDesign();
        }
    }
}

public ElectricalLoadList getElectricalLoadList() {
    ElectricalLoadList loadList = new ElectricalLoadList(getName());
    for (ProcessEquipmentInterface eq : unitOperations) {
        ElectricalDesign ed = eq.getElectricalDesign();
        if (ed != null) {
            loadList.addLoad(new LoadItem(eq.getName(), ed));
        }
    }
    loadList.calculateTotals();
    return loadList;
}

public double getTotalElectricalLoadKW() {
    return getElectricalLoadList().getTotalActiveLoadKW();
}

public double getTotalApparentPowerKVA() {
    return getElectricalLoadList().getTotalApparentPowerKVA();
}

public String getElectricalLoadListJson() {
    return getElectricalLoadList().toJson();
}
```

### 6.2 ElectricalLoadList

```java
package neqsim.process.electricaldesign.loadanalysis;

public class ElectricalLoadList implements java.io.Serializable {

    private String systemName;
    private List<LoadItem> loads;

    // === Aggregated results ===
    private double totalConnectedKW;       // Sum of all rated motor powers
    private double totalRunningKW;         // Sum of running loads × load factors
    private double totalStandbyKW;         // Standby equipment
    private double diversifiedLoadKW;      // After diversity factor
    private double totalApparentPowerKVA;  // Considering individual power factors
    private double totalReactivePowerKVAR;
    private double weightedPowerFactor;    // Weighted average cos φ
    private double contingencyPercent;     // Typically 10–20%
    private double futureGrowthPercent;    // Typically 10%

    // === Voltage level breakdown ===
    private Map<String, Double> loadByVoltageLevel;  // e.g. "6.6kV" → 5000 kW

    // === Methods ===
    public void calculateTotals() { ... }
    public String toJson() { ... }         // Full load list as JSON
    public String toCsvReport() { ... }    // Electrical load list in standard format
}
```

### 6.3 LoadItem

```java
public class LoadItem implements java.io.Serializable {
    private String tagNumber;
    private String equipmentName;
    private String description;
    private double ratedPowerKW;           // Motor nameplate
    private double absorbedPowerKW;        // Actual shaft power from process
    private double electricalInputKW;      // Including losses
    private double apparentPowerKVA;
    private double reactivePowerKVAR;
    private double powerFactor;
    private double ratedVoltageV;
    private double ratedCurrentA;
    private String startingMethod;         // DOL, VFD, Soft-start
    private double loadFactor;             // Operating / rated (0–1)
    private double diversityFactor;        // 0–1
    private boolean isContinuous;
    private boolean isSpare;
    private String hazardousAreaZone;      // Zone 0, 1, 2 or safe
    private String exProtection;           // Ex d, Ex e, etc.
}
```

---

## 7. Data-Driven Design: CSV Resource Files

Place standard reference data in `src/main/resources/electricaldata/`:

| File | Content |
|------|---------|
| `iec_motor_frames.csv` | IEC frame sizes, rated power ranges, weight, dimensions, by pole count |
| `nema_motor_frames.csv` | NEMA frame sizes, HP ranges |
| `iec60034_efficiency.csv` | IE1–IE4 minimum efficiency by power and pole count |
| `cable_ampacity_xlpe.csv` | XLPE cable ampacity by cross-section, installation method, temp |
| `cable_ampacity_pvc.csv` | PVC cable ampacity |
| `transformer_standard_sizes.csv` | Standard kVA ratings (100, 160, 250, 315, 400, 500, ...) |
| `switchgear_ratings.csv` | Standard busbar and breaker ratings |
| `hazarea_equipment_zones.csv` | Default hazardous zone for equipment types |
| `vfd_sizing.csv` | VFD power/current ratings per frame |
| `cable_derating_temp.csv` | Temperature derating factors |
| `cable_derating_grouping.csv` | Grouping derating factors |

---

## 8. Standards Covered

| Standard | Scope | Used In |
|----------|-------|---------|
| **IEC 60034-1** | Rotating electrical machines — rating and performance | `ElectricalMotor` |
| **IEC 60034-30-1** | Efficiency classes IE1–IE4 | `IEC60034Standard`, `ElectricalMotor.efficiencyClass` |
| **IEEE 841** | Motors for petroleum/chemical industry | `IEEE841Standard`, `ElectricalMotor` |
| **NEMA MG 1** | Motors and generators (NEMA frame sizes) | `NEMAMotorStandard` |
| **IEC 60502** | Power cables 1–30 kV | `ElectricalCable` |
| **IEC 60364** | Low-voltage installations — cable sizing | `ElectricalCable` |
| **IEEE 835** | Cable ampacity tables | `ElectricalCable` |
| **IEC 61800** | Adjustable speed electrical power drive systems | `VariableFrequencyDrive` |
| **IEEE 519** | Harmonic limits | `VariableFrequencyDrive.thdCurrentPercent` |
| **IEC 61439** | Low-voltage switchgear assemblies | `Switchgear` |
| **IEC 60076** | Power transformers | `Transformer` |
| **IEC 60079 / ATEX** | Equipment for explosive atmospheres | `HazardousAreaClassification` |
| **API RP 500/505** | Hazardous area classification for petroleum facilities | `HazardousAreaClassification` |
| **NORSOK E-001** | Electrical systems | System-level voltage selection |
| **IEC 61508 / 61511** | Functional safety (SIL) | `ElectricalDesign.silLevel` |

---

## 9. Example Usage

### 9.1 Single Compressor

```java
// After process simulation
Compressor comp = new Compressor("Export Compressor", feed);
comp.setOutletPressure(120.0);
process.add(comp);
process.run();

// Initialize electrical design
comp.initElectricalDesign();
CompressorElectricalDesign elecDesign = comp.getElectricalDesign();

// Configure
elecDesign.setRatedVoltageV(6600);
elecDesign.setFrequencyHz(50);
elecDesign.setMotorStandard("IEC");
elecDesign.setMotorSizingMargin(1.10);  // 10% margin
elecDesign.setUseVFD(true);
elecDesign.setHazAreaStandard("IECEx");
elecDesign.getPowerCable().setLengthM(150.0);

// Run electrical design
elecDesign.calcDesign();

// Get results
String json = elecDesign.toJson();
System.out.println("Motor: " + elecDesign.getMotor().getRatedPowerKW() + " kW");
System.out.println("FLA: " + elecDesign.getFullLoadCurrentA() + " A");
System.out.println("Cable: " + elecDesign.getPowerCable().getCrossSectionMM2() + " mm²");
System.out.println("VFD THD: " + elecDesign.getVfd().getThdCurrentPercent() + " %");
System.out.println("Total input: " + elecDesign.getElectricalInputKW() + " kW");
```

### 9.2 Full Plant Load List

```java
// Build process
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(separator);
process.add(compressor1);
process.add(compressor2);
process.add(pump);
process.add(cooler);
process.run();

// Run all electrical designs
process.initAllElectricalDesigns();
process.runAllElectricalDesigns();

// Get plant electrical summary
ElectricalLoadList loadList = process.getElectricalLoadList();
System.out.println("Total connected: " + loadList.getTotalConnectedKW() + " kW");
System.out.println("Total running: " + loadList.getTotalRunningKW() + " kW");
System.out.println("Total apparent: " + loadList.getTotalApparentPowerKVA() + " kVA");
System.out.println("Weighted PF: " + loadList.getWeightedPowerFactor());
System.out.println(loadList.toJson());
```

### 9.3 Python/Jupyter

```python
from neqsim import jneqsim

# After running process...
comp = process.getUnit("Export Compressor")
comp.initElectricalDesign()
ed = comp.getElectricalDesign()
ed.setRatedVoltageV(6600.0)
ed.setFrequencyHz(50.0)
ed.setUseVFD(True)
ed.calcDesign()

print(f"Motor: {ed.getMotor().getRatedPowerKW():.0f} kW")
print(f"Cable: {ed.getPowerCable().getCrossSectionMM2():.0f} mm²")
print(ed.toJson())
```

---

## 10. JSON Output Structure

```json
{
  "equipmentName": "Export Compressor",
  "equipmentType": "Compressor",
  "shaftPowerKW": 4500.0,
  "electricalInputKW": 4950.0,
  "apparentPowerKVA": 5192.0,
  "reactivePowerKVAR": 1610.0,
  "powerFactor": 0.953,
  "ratedVoltageV": 6600,
  "frequencyHz": 50,
  "phases": 3,
  "motor": {
    "ratedPowerKW": 5000,
    "ratedVoltageV": 6600,
    "ratedCurrentA": 495.2,
    "ratedSpeedRPM": 2985,
    "poles": 2,
    "efficiencyPercent": 96.8,
    "efficiencyClass": "IE3",
    "powerFactorFL": 0.89,
    "frameSize": "450",
    "enclosureType": "TEFC",
    "insulationClass": "F",
    "exProtection": "Ex d IIB T3",
    "startingMethod": "VFD",
    "lockedRotorCurrentMultiplier": 6.5,
    "weightKg": 3200,
    "estimatedCostUSD": 185000
  },
  "vfd": {
    "ratedPowerKW": 5000,
    "ratedCurrentA": 520,
    "topologyType": "3-level",
    "efficiencyPercent": 97.5,
    "inputPowerFactor": 0.98,
    "thdCurrentPercent": 3.2,
    "pulseConfiguration": "AFE",
    "coolingMethod": "Water",
    "enclosureRating": "IP21",
    "weightKg": 2800,
    "estimatedCostUSD": 320000
  },
  "powerCable": {
    "lengthM": 150,
    "crossSectionMM2": 95,
    "conductorMaterial": "Copper",
    "insulationType": "XLPE",
    "numberOfCores": 3,
    "voltageDropPercent": 1.8,
    "ampacityA": 540,
    "installationMethod": "Tray"
  },
  "auxiliaryLoads": {
    "lubOilPumpKW": 15.0,
    "sealGasPanelKW": 5.0,
    "coolingFanKW": 22.0,
    "instrumentationKW": 2.0,
    "antiSurgeValveKW": 3.0,
    "totalAuxiliaryKW": 47.0
  },
  "hazardousArea": {
    "zone": "Zone 1",
    "gasGroup": "IIA",
    "temperatureClass": "T3"
  }
}
```

---

## 11. Implementation Roadmap

### Phase 1 — Foundation (Priority: High)

| Task | Description |
|------|-------------|
| 1.1 | Create `ElectricalDesign` base class with power/voltage/PF fields |
| 1.2 | Create `ElectricalMotor` component with IEC sizing logic |
| 1.3 | Create `VariableFrequencyDrive` component |
| 1.4 | Create `ElectricalCable` component with voltage drop calc |
| 1.5 | Add `getElectricalDesign()` / `initElectricalDesign()` to interface/base |
| 1.6 | Implement `CompressorElectricalDesign` (most valuable use case) |
| 1.7 | CSV data files for motor frames and cable ampacity |
| 1.8 | Unit tests for all Phase 1 classes |

### Phase 2 — Equipment Coverage

| Task | Description |
|------|-------------|
| 2.1 | `PumpElectricalDesign` |
| 2.2 | `SeparatorElectricalDesign` (actuated valves, level instruments) |
| 2.3 | `HeatExchangerElectricalDesign` (electric heaters, fans for air coolers) |
| 2.4 | `PipelineElectricalDesign` (heat tracing, cathodic protection) |
| 2.5 | `Transformer` component |
| 2.6 | `Switchgear` component |

### Phase 3 — System Integration

| Task | Description |
|------|-------------|
| 3.1 | `ElectricalLoadList` and `LoadItem` classes |
| 3.2 | `ProcessSystem.getElectricalLoadList()` |
| 3.3 | `SystemElectricalDesign` for plant-wide summary |
| 3.4 | Power balance report generation |
| 3.5 | One-line diagram data export (JSON/XML) |

### Phase 4 — Advanced Features

| Task | Description |
|------|-------------|
| 4.1 | `HazardousAreaClassification` (ATEX/IECEx zone mapping) |
| 4.2 | Harmonic analysis for VFD installations |
| 4.3 | Short-circuit current calculations |
| 4.4 | `ElectricalHeatTracing` for pipeline insulation |
| 4.5 | Integration with cost estimation framework |
| 4.6 | Subsea power distribution (power umbilicals, step-out distance) |

---

## 12. Relationship to Existing Code

### What changes in existing classes

| Class | Change | Risk |
|-------|--------|------|
| `ProcessEquipmentInterface` | Add 2 methods: `getElectricalDesign()`, `initElectricalDesign()` | Low — default returns null |
| `ProcessEquipmentBaseClass` | Add field + default implementations | Low — backward compatible |
| `ProcessSystem` | Add `initAllElectricalDesigns()`, `runAllElectricalDesigns()`, `getElectricalLoadList()` | Low — additive only |
| `Compressor` | Override `initElectricalDesign()`, `getElectricalDesign()` | Low — follows existing pattern |
| `Pump` | Same override pattern | Low |

### What stays the same

- `MechanicalDesign` and all subclasses — untouched
- `CompressorDriver` / `ElectricMotorDriver` — these model **dynamic driver behavior** (speed transients, torque); `ElectricalMotor` models **electrical design specifications** (frames, efficiency class, cables). They are complementary, not overlapping.
- All existing process calculations — electrical design is a post-processing step

### Interaction with MechanicalDesign

Both designs use the same process equipment as input but produce independent outputs:

```
Process Equipment (Compressor)
├── getPower("kW") → 4500 kW shaft power
│
├── MechanicalDesign → wall thickness, materials, weights, vessel design
│
└── ElectricalDesign → motor, VFD, cables, switchgear, load list
```

The `calcDesign()` methods can reference each other if needed:
- Electrical design needs motor weight → contributes to MechanicalDesign total weight
- MechanicalDesign weight estimates include `weightElectroInstrument` → can be populated by ElectricalDesign

---

## 13. Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| **Separate package** (`electricaldesign/`) rather than merging into `mechanicaldesign/` | Electrical and mechanical are distinct engineering disciplines with different standards, professionals, and deliverables |
| **Component composition** (Motor, VFD, Cable as separate classes) | Each component has independent sizing rules, standards, and is testable in isolation |
| **Post-processing step** (after process simulation) | Electrical sizing depends on process duty (power), not the other way around |
| **Data-driven** sizing from CSV tables | Motor frames, cable ampacity, etc. follow standard tables — hardcoding would be unmaintainable |
| **Equipment-specific subclasses** of ElectricalDesign | Different equipment types have fundamentally different electrical requirements (motor vs heater element vs solenoid valve) |
| **ElectricalMotor vs ElectricMotorDriver** | Driver class models dynamic speed-torque behavior; Motor class models rated nameplate and sizing. Similar to how a mechanical engineer specifies a vessel (MechanicalDesign) while a process engineer models its separation (Separator) |

---

## 14. VSD/Compressor Use Case Deep Dive

The VSD + compressor combination is the most valuable initial use case:

### What NeqSim Can Already Do
- Calculate shaft power at any operating point
- Model speed-dependent compressor performance (head, efficiency)
- `CompressorDriver` and `ElectricMotorDriver` track VFD min/max speed ratios

### What the Electrical Package Adds

1. **Motor selection**: Given 4500 kW shaft power → select 5000 kW motor (IEC frame 450, IE3)
2. **VFD selection**: Given motor → select 5000 kW VFD (3-level, AFE, IP21)
3. **Harmonic impact**: 6-pulse VFD → 28% THDi; AFE → 3% THDi → affects transformer/cable sizing
4. **Cable sizing**: 6.6 kV, 150m run → 95 mm² XLPE, 1.8% voltage drop ✓
5. **Starting analysis**: With VFD → soft-start, no inrush → smaller switchgear
6. **Heat dissipation**: VFD losses = 125 kW → HVAC or water cooling requirement
7. **Power factor correction**: VFD with AFE → PF ≈ 0.98 → no capacitor bank needed
8. **Cost**: Motor $185k + VFD $320k + cables $45k = $550k electrical scope
9. **Load list entry**: 5000 kW connected, 4500 kW absorbed, 0.89 PF, Zone 1 Ex d IIB T3

### End-to-End Flow

```
Compressor.run()
  → shaft power = 4500 kW
  → Compressor.initElectricalDesign()
    → CompressorElectricalDesign.calcDesign()
      → ElectricalMotor.sizeMotor(4500, 1.10, "IEC")  → 5000 kW, Frame 450
      → VariableFrequencyDrive.sizeVFD(motor)          → 5000 kW, AFE
      → ElectricalCable.sizeCable(495A, 6600V, 150m)   → 95 mm² XLPE
      → Calculate apparentPower, reactivePower, powerFactor
      → Sum auxiliary loads (lube oil pump, seal gas, instruments)
  → CompressorElectricalDesign.toJson()
    → Complete equipment electrical data sheet
```
