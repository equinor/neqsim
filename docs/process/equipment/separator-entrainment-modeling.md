---
title: "Enhanced Separator Entrainment Modeling"
description: "Physics-based separator performance modeling with droplet size distributions, flow regime prediction, inlet device modeling, grade efficiency curves, vessel geometry, and internals databases. Comparable to commercial tools like MySep and ProSep."
---

# Enhanced Separator Entrainment Modeling

Physics-based, inlet-to-outlet separator performance calculation using open-literature
correlations. This is an optional enhancement to the standard NeqSim separator that
computes entrainment from first principles rather than user-specified fractions.

**Package:** `neqsim.process.equipment.separator.entrainment`

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Quick Start](#quick-start)
- [Calculation Chain (7 Stages)](#calculation-chain-7-stages)
  - [Stage 1: Inlet Flow Regime Prediction](#stage-1-inlet-flow-regime-prediction)
  - [Stage 2: Inlet Droplet Size Distribution](#stage-2-inlet-droplet-size-distribution)
  - [Stage 3: Inlet Device Modeling](#stage-3-inlet-device-modeling)
  - [Stage 4: Vessel Geometry](#stage-4-vessel-geometry)
  - [Stage 5: Gravity Settling Section](#stage-5-gravity-settling-section)
  - [Stage 6: Mist Eliminator](#stage-6-mist-eliminator)
  - [Stage 7: Liquid-Liquid Separation (Three-Phase)](#stage-7-liquid-liquid-separation-three-phase)
- [Separator Internals Database](#separator-internals-database)
- [Vendor-Certified Efficiency Curves](#vendor-certified-efficiency-curves)
- [Droplet Size Distributions](#droplet-size-distributions)
- [Grade Efficiency Curves](#grade-efficiency-curves)
- [Calibration Framework](#calibration-framework)
- [Using the Enhanced Mode](#using-the-enhanced-mode)
  - [Java API](#java-api)
  - [Python API](#python-api)
- [JSON Output](#json-output)
- [Comparison to Commercial Tools](#comparison-to-commercial-tools)
- [Correlations and References](#correlations-and-references)
- [Class Reference](#class-reference)

---

## Overview

Traditional NeqSim separators use user-specified entrainment fractions via `setEntrainment()`.
The enhanced model instead derives entrainment from physical properties automatically:

| Aspect | Standard Model | Enhanced Model |
|--------|---------------|----------------|
| Entrainment source | User-specified fractions | Computed from DSD + vessel geometry |
| Flow regime | Not considered | Mandhane (horizontal), Taitel-Dukler (vertical) |
| Inlet device | Not modeled | 7 device types with momentum limits |
| Gravity section | Not modeled | Schiller-Naumann drag, cut diameter |
| Mist eliminator | Not modeled | Grade efficiency curve (wire mesh, vane, cyclone) |
| Liquid-liquid | Not modeled | Stokes settling with oil pad geometry |
| K-factor | External | Computed, with flooding detection |

The enhanced model is enabled by a single flag and runs alongside the thermodynamic
flash in the existing `Separator` and `ThreePhaseSeparator` classes.

---

## Architecture

```
Inlet Pipe                    Separator Vessel
 ┌──────────┐    ┌─────────────────────────────────────────┐
 │ Flow     │    │  Inlet     Gravity     Mist             │
 │ Regime   │───>│  Device    Section     Eliminator        │──> Gas Out
 │ + DSD    │    │  (bulk     (settling)  (grade eff.)      │
 │ Predict  │    │   sep.)                                   │
 └──────────┘    │                                           │
                 │  Liquid-Liquid Section (3-phase only)     │
                 │  (oil/water settling, coalescence)        │──> Liquid Out(s)
                 └─────────────────────────────────────────┘
```

**Key classes:**

| Class | Purpose |
|-------|---------|
| `SeparatorPerformanceCalculator` | Main orchestrator — routes through all stages |
| `MultiphaseFlowRegime` | Predicts inlet flow regime, generates DSD |
| `InletDeviceModel` | Models inlet device bulk separation + DSD transformation |
| `SeparatorGeometryCalculator` | Vessel geometry for H/V vessels, K-factor |
| `DropletSizeDistribution` | Rosin-Rammler and log-normal DSD models |
| `DropletSettlingCalculator` | Schiller-Naumann terminal velocity |
| `GradeEfficiencyCurve` | S-curve grade efficiency for mist eliminators |
| `SeparatorInternalsDatabase` | CSV-backed database of internals specifications |

---

## Quick Start

### Java — Two-Phase Separator with Enhanced Entrainment

```java
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.entrainment.*;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

// Create fluid
SystemSrkEos fluid = new SystemSrkEos(273.15 + 30.0, 70.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("n-heptane", 0.05);
fluid.setMixingRule("classic");

Stream feed = new Stream("Feed", fluid);
feed.setFlowRate(50000.0, "kg/hr");
feed.run();

// Create separator with enhanced entrainment
Separator separator = new Separator("HP Sep", feed);
separator.setInternalDiameter(2.0);
separator.setSeparatorLength(6.0);

// Enable enhanced mode
separator.setEnhancedEntrainmentCalculation(true);
separator.setInletDeviceType(InletDeviceModel.InletDeviceType.INLET_VANE);
separator.setInletPipeDiameter(0.254);  // 10-inch pipe

// Configure mist eliminator
separator.getPerformanceCalculator()
    .setMistEliminatorCurve(GradeEfficiencyCurve.wireMeshDefault());

separator.run();

// Results
System.out.println("Inlet flow regime: " + separator.getInletFlowRegime());
System.out.println("K-factor: " + separator.getKFactor());
System.out.println("K-factor utilization: " + separator.getKFactorUtilization());
System.out.println("Mist eliminator flooded: " + separator.isMistEliminatorFlooded());
System.out.println("Overall gas-liquid efficiency: "
    + separator.getPerformanceCalculator().getOverallGasLiquidEfficiency());
```

### Python — Enhanced Separator

```python
from neqsim import jneqsim
import jpype

# Classes
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
Stream = jneqsim.process.equipment.stream.Stream
Separator = jneqsim.process.equipment.separator.Separator
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
InletDeviceModel = jneqsim.process.equipment.separator.entrainment.InletDeviceModel
GradeEfficiencyCurve = jneqsim.process.equipment.separator.entrainment.GradeEfficiencyCurve

# Fluid
fluid = SystemSrkEos(273.15 + 30.0, 70.0)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("n-heptane", 0.05)
fluid.setMixingRule("classic")

feed = Stream("Feed", fluid)
feed.setFlowRate(50000.0, "kg/hr")

# Separator with enhanced mode
sep = Separator("HP Sep", feed)
sep.setInternalDiameter(2.0)
sep.setSeparatorLength(6.0)
sep.setEnhancedEntrainmentCalculation(True)
sep.setInletDeviceType(InletDeviceModel.InletDeviceType.INLET_VANE)
sep.setInletPipeDiameter(0.254)
sep.getPerformanceCalculator().setMistEliminatorCurve(
    GradeEfficiencyCurve.wireMeshDefault())

process = ProcessSystem()
process.add(feed)
process.add(sep)
process.run()

print(f"Inlet flow regime: {sep.getInletFlowRegime()}")
print(f"K-factor: {sep.getKFactor():.4f} m/s")
print(f"K-factor utilization: {sep.getKFactorUtilization():.1%}")
print(f"Overall efficiency: "
      f"{sep.getPerformanceCalculator().getOverallGasLiquidEfficiency():.2%}")
```

---

## Calculation Chain (7 Stages)

When `setEnhancedEntrainmentCalculation(true)` is called, the `SeparatorPerformanceCalculator`
executes a 7-stage pipeline. Each stage feeds the next.

### Stage 1: Inlet Flow Regime Prediction

**Class:** `MultiphaseFlowRegime`

Predicts the multiphase flow regime in the inlet pipe using dimensionless
superficial velocity maps:

- **Horizontal pipes:** Mandhane, Gregory, and Aziz (1974) flow pattern map
- **Vertical pipes:** Taitel, Dukler, and Barnea (1980) flow pattern transitions

**Flow regimes (enum `FlowRegime`):**

| Regime | Description | Typical Gas Velocity |
|--------|-------------|---------------------|
| `STRATIFIED_SMOOTH` | Separated layers, calm interface | Low vsg, low vsl |
| `STRATIFIED_WAVY` | Separated layers, wavy interface | Moderate vsg, low vsl |
| `SLUG` | Alternating liquid slugs and gas pockets | Moderate vsg, moderate vsl |
| `PLUG` | Elongated gas bubbles in liquid | Low vsg, moderate vsl |
| `ANNULAR` | Liquid film on wall, gas core | High vsg |
| `ANNULAR_MIST` | Annular with entrained droplets | Very high vsg |
| `DISPERSED_BUBBLE` | Small gas bubbles in liquid | Low vsg, high vsl |
| `CHURN` | Chaotic vertical oscillating flow | Moderate-high vsg (vertical) |
| `BUBBLE` | Discrete bubbles rising in liquid | Low vsg (vertical) |

**API pattern (setter → predict → getter):**

```java
MultiphaseFlowRegime calc = new MultiphaseFlowRegime();
calc.setGasDensity(50.0);              // kg/m3
calc.setLiquidDensity(800.0);          // kg/m3
calc.setGasViscosity(1.0e-5);          // Pa.s
calc.setLiquidViscosity(1.0e-3);       // Pa.s
calc.setSurfaceTension(0.025);         // N/m
calc.setPipeDiameter(0.254);           // m
calc.setGasSuperficialVelocity(15.0);  // m/s
calc.setLiquidSuperficialVelocity(0.1); // m/s
calc.setPipeOrientation("horizontal"); // or "vertical"

calc.predict();

MultiphaseFlowRegime.FlowRegime regime = calc.getPredictedRegime();
DropletSizeDistribution dsd = calc.getGeneratedDSD();
double entrainedFraction = calc.calcEntrainedLiquidFraction();
```

The flow regime determines which DSD correlation is used in Stage 2.

### Stage 2: Inlet Droplet Size Distribution

The DSD is generated automatically based on the predicted flow regime:

| Flow Regime | DSD Correlation | Reference |
|-------------|----------------|-----------|
| Annular / Annular-Mist | Azzopardi (1997) — Sauter mean diameter from Weber number and Reynolds number | Azzopardi, B.J. (1997) |
| Stratified / Stratified-Wavy | Ishii-Grolmes (1975) — entrainment onset and droplet generation from interfacial shear | Ishii and Grolmes (1975) |
| Slug / Plug / Churn | Hinze (1955) — maximum stable droplet from turbulent breakup | Hinze (1955) |
| Bubble / Dispersed Bubble | Hinze (1955) — applied for gas bubble breakup in liquid | Hinze (1955) |

The entrained liquid fraction is computed using the **Oliemans, Pots, and Trompe (1986)** correlation.

**Alternatively, specify the DSD directly:**

```java
// Rosin-Rammler distribution
DropletSizeDistribution dsd = DropletSizeDistribution.rosinRammler(100e-6, 2.6);

// Log-normal distribution
DropletSizeDistribution dsd = DropletSizeDistribution.logNormal(80e-6, 0.8);

// Set on calculator
separator.getPerformanceCalculator().setGasLiquidDSD(dsd);
```

### Stage 3: Inlet Device Modeling

**Class:** `InletDeviceModel`

Models the inlet device that distributes the feed into the vessel. The inlet device
provides partial bulk separation of liquid from gas before the gravity section.

**Device types (enum `InletDeviceType`):**

| Device Type | Typical Bulk Efficiency | Max Momentum [Pa] | Pressure Drop Coeff. | Typical Application |
|-------------|------------------------|-------------------|---------------------|---------------------|
| `NONE` | 0% | — | 0 | Simple pipe entry |
| `DEFLECTOR_PLATE` | 50-65% | 1500-2000 | 0.5-0.6 | Low-cost basic service |
| `HALF_PIPE` | 65-75% | 3000-3500 | 0.8-1.0 | Moderate liquid load |
| `INLET_VANE` | 75-85% | 5000-7000 | 1.5-2.0 | Standard gas-liquid separators |
| `INLET_CYCLONE` | 85-95% | 8000-10000 | 3.0-5.0 | High-performance scrubbers |
| `SCHOEPENTOETER` | 80-90% | 7000-9000 | 2.0-3.0 | Large diameter vessels |
| `IMPINGEMENT_PLATE` | 55-70% | 2000-3000 | 0.8-1.2 | Slug catcher service |

**Key physics:**

- **Momentum flux** $\rho_m v_n^2$ where $\rho_m$ is mixture density and $v_n$ is nozzle velocity
- When momentum exceeds the device's maximum limit (**Bothamley, 2013**), efficiency degrades
- The DSD is transformed downstream — large droplets are removed, remaining DSD shifts to smaller sizes

```java
InletDeviceModel model = new InletDeviceModel(
    InletDeviceModel.InletDeviceType.INLET_VANE);
model.setInletNozzleDiameter(0.254);  // 10-inch

model.calculate(inletDSD, gasDensity, liquidDensity,
    gasVolumeFlow, liquidVolumeFlow, surfaceTension);

double bulkEff = model.getBulkSeparationEfficiency();
double momentum = model.getMomentumFlux();        // Pa
double pressureDrop = model.getPressureDrop();    // Pa
DropletSizeDistribution remainingDSD = model.getDownstreamDSD();
```

### Stage 4: Vessel Geometry

**Class:** `SeparatorGeometryCalculator`

Computes exact vessel cross-sectional areas, gas/liquid heights, and
residence times for both horizontal and vertical vessels.

**Horizontal vessel geometry:**

The gas and liquid cross-sectional areas are computed from the exact
circular segment formula:

$$
A_{liquid} = \frac{D^2}{4} \left[ \cos^{-1}\left(1 - \frac{2h}{D}\right) - \left(1 - \frac{2h}{D}\right)\sqrt{\frac{4h}{D} - \frac{4h^2}{D^2}} \right]
$$

where $h$ is the liquid height and $D$ is the internal diameter.

**Vertical vessel geometry:**

$$
A_{gas} = \frac{\pi D^2}{4}, \quad L_{gas} = L_{TT} \cdot (1 - f_{liquid})
$$

The full cross-section is available for gas flow; the gas residence time
depends on the effective gas height above the liquid level.

**K-factor (Souders-Brown):**

$$
K = v_g \sqrt{\frac{\rho_g}{\rho_l - \rho_g}}
$$

This static method is used to check flooding conditions:

```java
double kFactor = SeparatorGeometryCalculator.calcKFactor(
    gasVelocity, gasDensity, liquidDensity);
```

**API:**

```java
SeparatorGeometryCalculator geom = new SeparatorGeometryCalculator();
geom.setOrientation("horizontal");     // or "vertical"
geom.setInternalDiameter(2.0);         // m
geom.setTangentToTangentLength(6.0);   // m
geom.setNormalLiquidLevel(0.5);        // fraction of diameter

geom.calculate(gasVolumeFlow, liquidVolumeFlow);

double gasArea = geom.getGasArea();                   // m2
double liquidArea = geom.getLiquidArea();              // m2
double settlingHeight = geom.getEffectiveGasSettlingHeight();  // m
double gasResTime = geom.getGasResidenceTime();        // s
double liqResTime = geom.getLiquidResidenceTime();     // s

// Three-phase
geom.calculateThreePhase(gasVolumeFlow, oilVolumeFlow,
    waterVolumeFlow, oilLevelFraction);
double oilPad = geom.getOilPadThickness();             // m
double waterLayer = geom.getWaterLayerHeight();        // m
```

### Stage 5: Gravity Settling Section

**Class:** `DropletSettlingCalculator`

Computes the terminal velocity of each droplet size class using the
**Schiller-Naumann** drag correlation, which smoothly transitions from
Stokes flow to Newton's law:

$$
C_D = \frac{24}{Re}\left(1 + 0.15 Re^{0.687}\right), \quad Re < 1000
$$

$$
v_t = \sqrt{\frac{4 g d_p |\Delta\rho|}{3 C_D \rho_c}}
$$

The gravity cut diameter is the smallest droplet that can settle across the
effective settling height within the available residence time:

$$
d_{cut} = d_p \text{ where } v_t(d_p) = \frac{H_{settle}}{t_{residence}}
$$

Droplets smaller than $d_{cut}$ pass through the gravity section;
larger droplets are captured.

```java
// Single droplet terminal velocity
double vt = DropletSettlingCalculator.calcTerminalVelocity(
    dropletDiameter,   // m
    continuousDensity, // kg/m3 (gas for liquid droplets)
    dispersedDensity,  // kg/m3 (liquid for liquid droplets)
    continuousViscosity); // Pa.s
```

### Stage 6: Mist Eliminator

**Class:** `GradeEfficiencyCurve`

Models separation internals using an S-shaped grade efficiency curve defined by
$d_{50}$ (50% collection efficiency diameter) and maximum achievable efficiency:

$$
\eta(d_p) = \eta_{max} \cdot \left[1 - \exp\left(-0.693 \left(\frac{d_p}{d_{50}}\right)^n\right)\right]
$$

where $n$ is the sharpness parameter (typically 2-4).

**Pre-configured internals types:**

| Factory Method | $d_{50}$ | Max $\eta$ | Application |
|---------------|----------|-----------|-------------|
| `wireMeshDefault()` | 5 $\mu$m | 99.8% | Standard demisting pad |
| `vanePackDefault()` | 15 $\mu$m | 99.0% | Higher capacity, coarser cut |
| `axialCycloneDefault()` | 3 $\mu$m | 99.8% | Highest efficiency |
| `platePack(d50, maxEff)` | Custom | Custom | Liquid-liquid coalescence |

**Overall efficiency** integrates the grade efficiency over the DSD:

$$
\eta_{overall} = \int_0^{\infty} \eta(d_p) \cdot f(d_p) \, dd_p
$$

```java
GradeEfficiencyCurve curve = GradeEfficiencyCurve.wireMeshDefault();

// Single-size efficiency
double eta100um = curve.getEfficiency(100e-6);  // ~99.8% for wire mesh

// Overall efficiency against a DSD
double etaOverall = curve.calcOverallEfficiency(dsd);
```

**Flooding detection:** If the K-factor exceeds the internals' maximum K-factor
(from the database), the mist eliminator is flagged as flooded and its efficiency
is degraded.

### Stage 7: Liquid-Liquid Separation (Three-Phase)

For `ThreePhaseSeparator`, the enhanced model also computes:

1. **Oil-in-water settling** — water droplets rising through the oil pad
2. **Water-in-oil settling** — oil droplets settling through the water layer
3. **Gas bubble carry-under** — gas rising through the liquid phase

The liquid-liquid residence time uses the actual vessel geometry to determine
the available settling time. Stokes settling velocity is used with the
interfacial tension between oil and water phases.

---

## Separator Internals Database

**Class:** `SeparatorInternalsDatabase`

A CSV-backed database of separator internals specifications, loaded as a
singleton from `src/main/resources/designdata/`:

- [SeparatorInternals.csv](../../../src/main/resources/designdata/SeparatorInternals.csv) — 70+ records covering wire mesh (19 variants incl. Monel, Hastelloy, PTFE, Duplex), vane pack (12 variants), axial cyclone (10 variants), plate pack (13 variants), and gravity (10 variants)
- [SeparatorInletDevices.csv](../../../src/main/resources/designdata/SeparatorInletDevices.csv) — 31 records covering all inlet device types (elbow inlets, distributors, vanes, cyclones, deflector plates, schoepentoeter, impingement plates)
- [SeparatorVendorCurves.csv](../../../src/main/resources/designdata/SeparatorVendorCurves.csv) — 25 vendor-certified grade efficiency curves from factory acceptance tests (FAT), covering wire mesh, vane pack, axial cyclone, and plate pack; includes atmospheric and high-pressure (50 bar) test data

### Internals Records

Each record in `SeparatorInternals.csv` contains:

| Field | Description | Example |
|-------|-------------|---------|
| `internalsType` | Category | WIRE_MESH, VANE_PACK, AXIAL_CYCLONE, PLATE_PACK, GRAVITY |
| `subType` | Variant | Standard Knitted, Double Pocket, High Efficiency Tube |
| `manufacturer` | Manufacturer | Generic (extensible to vendor-specific) |
| `d50_um` | 50% collection diameter [$\mu$m] | 3.0 to 500.0 |
| `sharpness` | Grade efficiency curve sharpness | 1.5 to 4.0 |
| `maxEfficiency` | Maximum achievable efficiency | 0.90 to 0.999 |
| `maxKFactor` | Maximum K-factor before flooding [m/s] | 0.04 to 0.30 |
| `minKFactor` | Minimum recommended K-factor [m/s] | 0.01 to 0.05 |
| `pressureDrop_mbar` | Typical pressure drop [mbar] | 0.1 to 10.0 |
| `designStandard` | Applicable standard | API 12J, NORSOK P-100 |
| `material` | Construction material | SS316L, Monel, Hastelloy C-276, PTFE, Titanium Gr2, Duplex 2205 |
| `maxTemperature_C` | Maximum operating temperature [C] | 150 to 800 |
| `minThickness_mm` | Minimum pad/element thickness [mm] | 50 to 300 |
| `weight_kg_m2` | Installed weight per unit area [kg/m²] | 10 to 120 |
| `reference` | Published reference | Brunazzi and Paglianti (1998), El-Dessouky et al. (2000), York (2003) |

### Inlet Device Records

Each record in `SeparatorInletDevices.csv` contains:

| Field | Description | Example |
|-------|-------------|---------|
| `deviceType` | Category | DEFLECTOR_PLATE, INLET_VANE, INLET_CYCLONE, etc. |
| `subType` | Variant | Flat Baffle, Multi-Tube, Standard |
| `minMomentum_Pa` | Minimum recommended momentum [Pa] | 0 to 1000 |
| `maxMomentum_Pa` | Maximum recommended momentum [Pa] | 500 to 10000 |
| `typicalBulkEfficiency` | Typical bulk liquid separation efficiency | 0.0 to 0.95 |
| `dsdMultiplier` | DSD transformation factor (downstream/upstream d50) | 0.45 to 1.0 |
| `pressureDropCoeff` | Pressure loss coefficient | 0.0 to 5.0 |
| `reference` | Published reference | Bothamley (2013) |

### Querying the Database

```java
SeparatorInternalsDatabase db = SeparatorInternalsDatabase.getInstance();

// Find all wire mesh types
List<SeparatorInternalsDatabase.InternalsRecord> meshes = db.findByType("WIRE_MESH");
for (SeparatorInternalsDatabase.InternalsRecord rec : meshes) {
    System.out.printf("%-30s  d50=%5.1f um  maxK=%.3f m/s  maxEff=%.3f%n",
        rec.subType, rec.d50_um, rec.maxKFactor, rec.maxEfficiency);
}

// Find specific variant
SeparatorInternalsDatabase.InternalsRecord hiEff =
    db.findByTypeAndSubType("WIRE_MESH", "High Efficiency");

// Convert to grade efficiency curve for calculations
GradeEfficiencyCurve curve = hiEff.toGradeEfficiencyCurve();

// Find inlet devices
List<SeparatorInternalsDatabase.InletDeviceRecord> vanes =
    db.findInletDeviceByType("INLET_VANE");

// Export full catalog as JSON
String json = db.toCatalogJson();
```

### Extending the Database

Add rows to the CSV files to include vendor-specific or custom internals.
The database reloads automatically on next `getInstance()` call after JVM restart.
Fields are public for direct access — no getters needed.

---

## Vendor-Certified Efficiency Curves

The database includes **25 vendor-certified grade efficiency curves** from factory
acceptance tests (FAT), stored in `SeparatorVendorCurves.csv`. Each curve contains
12 measured (droplet diameter, efficiency) points obtained from standardized testing
(EN 13544, ISO 29042, or vendor in-house methods).

### Vendor Curve Records

| Field | Description | Example |
|-------|-------------|--------|
| `curveId` | Unique identifier | VC001, VC015 |
| `internalsType` | WIRE_MESH, VANE_PACK, AXIAL_CYCLONE, PLATE_PACK | WIRE_MESH |
| `vendorName` | Vendor name | VendorA, VendorB |
| `productFamily` | Product model designation | WM-StdKnit-150, VP-DP-Standard |
| `testStandard` | Test method | EN 13544 / ISO 29042 |
| `testFluid` | Gas-liquid pair used | Air-Water, N2-Exxsol |
| `testPressure_bar` | Test pressure [bar] | 1.0, 50.0 |
| `testTemperature_C` | Test temperature [°C] | 20, 25 |
| `diameterPoints_um` | Measured droplet diameters [µm] | 1;2;3;5;8;10;15;20;30;50;80;100 |
| `efficiencyPoints` | Measured collection efficiencies [0-1] | 0.05;0.12;0.25;0.50;... |
| `maxKFactor` | Maximum K-factor at test conditions [m/s] | 0.107 |
| `testDate` | Factory acceptance test date | 2022-03-15 |
| `certificateRef` | Certificate or report reference | FAT-2022-001 |

### Coverage

| Internals Type | Number of Curves | Includes HP (50 bar) |
|----------------|-----------------|---------------------|
| WIRE_MESH | 8 | Yes (2 curves) |
| VANE_PACK | 6 | Yes (1 curve) |
| AXIAL_CYCLONE | 6 | Yes (1 curve) |
| PLATE_PACK | 3 | No |
| **Total** | **25** | |

### Querying Vendor Curves

```java
SeparatorInternalsDatabase db = SeparatorInternalsDatabase.getInstance();

// All vendor curves
List<SeparatorInternalsDatabase.VendorCurveRecord> all = db.getAllVendorCurves();

// Find by internals type
List<SeparatorInternalsDatabase.VendorCurveRecord> meshCurves =
    db.findVendorCurvesByType("WIRE_MESH");

// Find by vendor
List<SeparatorInternalsDatabase.VendorCurveRecord> vendorACurves =
    db.findVendorCurvesByVendor("VendorA");

// Find specific curve by ID
SeparatorInternalsDatabase.VendorCurveRecord curve = db.findVendorCurveById("VC001");
System.out.println("Product: " + curve.productFamily);
System.out.println("Test pressure: " + curve.testPressure_bar + " bar");
System.out.println("Points: " + curve.diameterPoints_um.length);

// Convert to GradeEfficiencyCurve for calculations
GradeEfficiencyCurve gec = curve.toGradeEfficiencyCurve();
double eta10 = gec.getEfficiency(10e-6);  // Efficiency at 10 µm

// Find by type AND vendor
List<SeparatorInternalsDatabase.VendorCurveRecord> vendorBMesh =
    db.findVendorCurvesByTypeAndVendor("WIRE_MESH", "VendorB");
```

### Using Vendor Curves in Simulation

```java
// Use a specific vendor curve instead of the generic defaults
SeparatorInternalsDatabase db = SeparatorInternalsDatabase.getInstance();
SeparatorInternalsDatabase.VendorCurveRecord vc =
    db.findVendorCurveById("VC001");

separator.getPerformanceCalculator()
    .setMistEliminatorCurve(vc.toGradeEfficiencyCurve());
```

### Python API

```python
db = SeparatorInternalsDatabase.getInstance()

# Browse vendor curves
for vc in db.getAllVendorCurves():
    print(f"{vc.curveId:6s}  {vc.internalsType:15s}  {vc.vendorName:10s}  "
          f"{vc.productFamily:25s}  P={vc.testPressure_bar:.0f} bar")

# Get a specific curve and use it
curve = db.findVendorCurveById("VC001")
gec = curve.toGradeEfficiencyCurve()
print(f"Efficiency at 10 um: {gec.getEfficiency(10e-6):.3f}")
```

---

## Droplet Size Distributions

**Class:** `DropletSizeDistribution`

Two statistical models are supported:

### Rosin-Rammler Distribution

$$
F(d) = 1 - \exp\left[-\left(\frac{d}{d_0}\right)^n\right]
$$

where $d_0$ is the characteristic diameter (63.2% cumulative) and $n$ is the
spread parameter (typically 2.0-4.0 for separator applications).

```java
// d0 = 100 um, spread = 2.6
DropletSizeDistribution dsd = DropletSizeDistribution.rosinRammler(100e-6, 2.6);
```

### Log-Normal Distribution

$$
f(d) = \frac{1}{d \sigma \sqrt{2\pi}} \exp\left[-\frac{(\ln d - \ln d_{50})^2}{2\sigma^2}\right]
$$

```java
// d50 = 80 um, geometric standard deviation = 0.8
DropletSizeDistribution dsd = DropletSizeDistribution.logNormal(80e-6, 0.8);
```

### Key Properties

```java
double d50 = dsd.getD50();               // Median diameter [m]
double d32 = dsd.getSauterMeanDiameter(); // Volume-to-surface mean [m]

// Discrete classes for numerical integration (20 bins)
double[][] classes = dsd.getDiscreteClasses();
// Each row: [d_lower, d_mid, volume_fraction]
```

---

## Grade Efficiency Curves

**Class:** `GradeEfficiencyCurve`

The S-shaped collection efficiency function characterizes any separation device
(mist eliminator, coalescer, gravity section):

```java
// Pre-configured types
GradeEfficiencyCurve wireMesh = GradeEfficiencyCurve.wireMeshDefault();
GradeEfficiencyCurve vanePack = GradeEfficiencyCurve.vanePackDefault();
GradeEfficiencyCurve cyclone  = GradeEfficiencyCurve.axialCycloneDefault();

// Custom curve
GradeEfficiencyCurve custom = GradeEfficiencyCurve.platePack(20e-6, 0.98);

// From database record
SeparatorInternalsDatabase db = SeparatorInternalsDatabase.getInstance();
GradeEfficiencyCurve fromDB = db.findByTypeAndSubType("VANE_PACK", "Double Pocket")
    .toGradeEfficiencyCurve();

// Query efficiency
double eta = wireMesh.getEfficiency(10e-6);  // Efficiency at 10 um
double overall = wireMesh.calcOverallEfficiency(dsd);  // Integrated over DSD
```

---

## Calibration Framework

The enhanced model includes a structured calibration system for matching model
predictions to plant or laboratory measurements. Three independent calibration
multipliers adjust three categories of entrainment:

| Multiplier | Affects | Default |
|-----------|---------|--------|
| `liquidInGasCalibrationFactor` | Oil-in-gas, water-in-gas | 1.0 |
| `gasCarryUnderCalibrationFactor` | Gas-in-oil, gas-in-water | 1.0 |
| `liquidLiquidCalibrationFactor` | Oil-in-water, water-in-oil | 1.0 |

### Manual Calibration

Set factors directly:

```java
SeparatorPerformanceCalculator perf = separator.getPerformanceCalculator();
perf.setLiquidInGasCalibrationFactor(1.5);     // 50% more carryover than model
perf.setGasCarryUnderCalibrationFactor(0.8);   // 20% less gas carry-under
perf.setLiquidLiquidCalibrationFactor(2.0);    // double liq-liq cross-contamination
```

### Auto-Calibration from a Single Measurement

Fit factors to one set of measured fractions:

```java
SeparatorPerformanceCalculator.CalibrationSummary summary =
    perf.calibrateFromMeasuredFractions(
        1.5e-3,   // measured oil-in-gas fraction
        0.0,      // measured water-in-gas (0 if not measured)
        8.0e-3,   // measured gas-in-oil
        0.0,      // measured gas-in-water
        2.0e-2,   // measured oil-in-water
        0.0,      // measured water-in-oil
        1e-12);   // model floor (avoid divide-by-zero)

System.out.println("New liquid-in-gas factor: " + summary.newLiquidInGasFactor);
System.out.println("New gas carry-under factor: " + summary.newGasCarryUnderFactor);
System.out.println("New liquid-liquid factor: " + summary.newLiquidLiquidFactor);
```

### Grouped-Measurement Convenience

When plant data is reported as grouped categories (not individual fractions):

```java
SeparatorPerformanceCalculator.CalibrationSummary summary =
    perf.calibrateFromGroupedMeasurements(
        1.5e-3,   // measured grouped liquid-in-gas
        8.0e-3,   // measured grouped gas carry-under
        2.0e-2,   // measured grouped liquid-liquid
        1e-12);   // model floor
```

### Batch Calibration from CSV Case Library

Fit calibration factors across multiple operating points from a CSV file:

```java
// Load calibration cases from CSV
List<SeparatorPerformanceCalculator.CalibrationCase> cases =
    SeparatorPerformanceCalculator.loadCalibrationCasesFromCsv(
        "src/main/resources/designdata/SeparatorCalibrationCasesTemplate.csv");

// Fit (finds median ratio across all cases)
SeparatorPerformanceCalculator.BatchCalibrationSummary fit =
    perf.calibrateFromCaseLibrary(cases, 1e-12);

System.out.println("Cases: " + fit.nCases);
System.out.println("Before MAPE: " + fit.beforeMAPE);
System.out.println("After MAPE: " + fit.afterMAPE);
```

### JSON Calibration Report

Generate a comprehensive report with per-case residuals:

```java
String report = perf.buildBatchCalibrationReportJson(cases, fit);
System.out.println(report);  // JSON with calibrationFactors, caseResults, summary

// Or save to file
perf.saveBatchCalibrationReportJson("calibration_report.json", cases, fit);
```

---

## Using the Enhanced Mode

### Java API

The enhanced mode is activated on a `Separator` or `ThreePhaseSeparator` via
convenience methods that delegate to the internal `SeparatorPerformanceCalculator`:

```java
Separator sep = new Separator("V-100", feed);
sep.setInternalDiameter(2.0);
sep.setSeparatorLength(6.0);

// ── Enable enhanced mode ──
sep.setEnhancedEntrainmentCalculation(true);

// ── Configure inlet device ──
sep.setInletDeviceType(InletDeviceModel.InletDeviceType.INLET_VANE);
sep.setInletPipeDiameter(0.254);          // 10-inch feed pipe
sep.setGasLiquidSurfaceTension(0.025);    // N/m (optional override)

// ── Configure mist eliminator ──
sep.getPerformanceCalculator()
    .setMistEliminatorCurve(GradeEfficiencyCurve.wireMeshDefault());

// ── Run ──
sep.run();

// ── Results ──
// Flow regime at inlet
MultiphaseFlowRegime.FlowRegime regime = sep.getInletFlowRegime();

// Souders-Brown K-factor and utilization (% of max K before flooding)
double kFactor = sep.getKFactor();
double kUtil = sep.getKFactorUtilization();

// Flooding check
boolean flooded = sep.isMistEliminatorFlooded();

// Detailed performance from calculator
SeparatorPerformanceCalculator perf = sep.getPerformanceCalculator();
double gravityEff = perf.getGravitySectionEfficiency();
double meEff = perf.getMistEliminatorEfficiency();
double overallEff = perf.getOverallGasLiquidEfficiency();
double dCut = perf.getGravityCutDiameter();

// Entrainment fractions (fed back into Separator outlet compositions)
double oilInGas = perf.getOilInGasFraction();
double waterInGas = perf.getWaterInGasFraction();
double gasInOil = perf.getGasInOilFraction();

// Full JSON report
String json = perf.toJson();

// Optional: one-point grouped calibration from measured field data
// (liquid-in-gas, gas carry-under, liquid-liquid)
perf.calibrateFromGroupedMeasurements(
    1.5e-3, // measured grouped liquid-in-gas
    8.0e-3, // measured grouped gas carry-under
    2.0e-2, // measured grouped liquid-liquid cross-contamination
    1e-12); // model floor

// Optional: batch calibration from a field-case library CSV
List<SeparatorPerformanceCalculator.CalibrationCase> cases =
    SeparatorPerformanceCalculator.loadCalibrationCasesFromCsv(
        "src/main/resources/designdata/SeparatorCalibrationCasesTemplate.csv");
SeparatorPerformanceCalculator.BatchCalibrationSummary fit =
    perf.calibrateFromCaseLibrary(cases, 1e-12);
```

### Advanced: Direct Calculator Usage

For standalone calculations without a `Separator` equipment object:

```java
SeparatorPerformanceCalculator calc = new SeparatorPerformanceCalculator();

// Set inlet DSD (manual or from flow regime prediction)
calc.setGasLiquidDSD(DropletSizeDistribution.rosinRammler(100e-6, 2.6));

// Set mist eliminator
calc.setMistEliminatorCurve(GradeEfficiencyCurve.vanePackDefault());

// Enable enhanced mode
calc.setUseEnhancedCalculation(true);
calc.setInletPipeDiameter(0.254);
calc.setSurfaceTension(0.025);
calc.setInletDeviceModel(
    new InletDeviceModel(InletDeviceModel.InletDeviceType.INLET_VANE));

// Run: gas density, oil density, water density,
//      gas viscosity, oil viscosity, water viscosity,
//      gas velocity, separator diameter, separator length,
//      orientation, liquid level fraction
calc.calculate(
    50.0, 800.0, 0.0,          // densities (0 = no water)
    1.5e-5, 3.0e-3, 0.0,       // viscosities
    3.0, 2.0, 6.0,             // velocity, diameter, length
    "horizontal", 0.5);         // orientation, liquid level

// Get results
double overallEff = calc.getOverallGasLiquidEfficiency();
double kFactor = calc.getKFactor();
```

### Python API

```python
from neqsim import jneqsim

# Import entrainment classes
InletDeviceModel = jneqsim.process.equipment.separator.entrainment.InletDeviceModel
GradeEfficiencyCurve = jneqsim.process.equipment.separator.entrainment.GradeEfficiencyCurve
DropletSizeDistribution = jneqsim.process.equipment.separator.entrainment.DropletSizeDistribution
SeparatorInternalsDatabase = jneqsim.process.equipment.separator.entrainment.SeparatorInternalsDatabase
MultiphaseFlowRegime = jneqsim.process.equipment.separator.entrainment.MultiphaseFlowRegime

# Query internals database
db = SeparatorInternalsDatabase.getInstance()
all_internals = list(db.getAllInternals())
for rec in all_internals:
    print(f"{rec.internalsType:15s} {rec.subType:25s} "
          f"d50={rec.d50_um:6.1f} um  maxK={rec.maxKFactor:.3f} m/s")

# Create custom DSD
dsd = DropletSizeDistribution.rosinRammler(100e-6, 2.6)
print(f"D50 = {dsd.getD50() * 1e6:.1f} um")
print(f"D32 = {dsd.getSauterMeanDiameter() * 1e6:.1f} um")

# Predict flow regime
regime_calc = MultiphaseFlowRegime()
regime_calc.setGasDensity(50.0)
regime_calc.setLiquidDensity(800.0)
regime_calc.setGasViscosity(1.0e-5)
regime_calc.setLiquidViscosity(1.0e-3)
regime_calc.setSurfaceTension(0.025)
regime_calc.setPipeDiameter(0.254)
regime_calc.setGasSuperficialVelocity(15.0)
regime_calc.setLiquidSuperficialVelocity(0.1)
regime_calc.setPipeOrientation("horizontal")
regime_calc.predict()
print(f"Flow regime: {regime_calc.getPredictedRegime()}")
```

---

## JSON Output

The `toJson()` method on `SeparatorPerformanceCalculator` produces a comprehensive
JSON report. When enhanced mode is active, it includes additional sections:

```json
{
  "gasLiquidDSD": {
    "type": "ROSIN_RAMMLER",
    "characteristicDiameter_m": 1.0e-4,
    "spreadParameter": 2.6,
    "d50_m": 8.3e-5,
    "sauterMeanDiameter_m": 7.1e-5
  },
  "gravitySectionEfficiency": 0.72,
  "gravityCutDiameter_m": 4.5e-5,
  "mistEliminatorEfficiency": 0.998,
  "overallGasLiquidEfficiency": 0.9994,
  "oilInGasFraction": 6.0e-4,
  "waterInGasFraction": 0.0,
  "gasInOilFraction": 0.002,
  "enhancedResults": {
    "inletFlowRegime": "ANNULAR",
    "kFactor_m_s": 0.068,
    "kFactorUtilization": 0.63,
    "mistEliminatorFlooded": false,
    "inletDeviceBulkEfficiency": 0.82,
    "inletDeviceMomentumFlux_Pa": 3200,
    "inletDevicePressureDrop_Pa": 450,
    "gasResidenceTime_s": 8.5,
    "liquidResidenceTime_s": 120.0,
    "gasSettlingHeight_m": 0.95,
    "postInletDeviceDSD": {
      "type": "ROSIN_RAMMLER",
      "characteristicDiameter_m": 5.5e-5,
      "spreadParameter": 2.6
    }
  }
}
```

---

## Comparison to Commercial Tools

### Tool Landscape (2025)

The separator performance modeling market breaks into four tiers:

| Tier | Tools | Approach |
|------|-------|----------|
| **Dedicated separator design** | MySep, Kelvin (Osprey) | Full DSD tracking through inlet-to-outlet stages |
| **Process simulators** | HYSYS, UniSim, Symmetry | Equilibrium flash, no entrainment physics |
| **Transient multiphase** | OLGA, LedaFlow, K-Spice | Pipeline slug delivery to separator, volume-based holdup |
| **Liquid-liquid specialist** | dEMULion (ConocoPhillips) | Emulsion breakup, coalescence, chemical dosing |
| **Open-source** | **NeqSim Enhanced** | DSD tracking + EOS coupling + calibration + vendor curves |

### Feature Comparison Matrix

| Feature | MySep | Kelvin (Osprey) | HYSYS / UniSim | OLGA / LedaFlow | dEMULion | **NeqSim Enhanced** |
|---------|-------|-----------------|----------------|-----------------|----------|---------------------|
| DSD tracking through stages | Yes (proprietary) | Yes (open-lit.) | No | Pipe only | Yes (L-L only) | **Yes** — 7-stage chain |
| Inlet device modeling | Yes (proprietary) | Yes | No | No | Partial | **Yes** — 7 types, Bothamley limits |
| Gravity settling | Yes (proprietary trajectory) | Yes (Stokes-Newton) | K-factor only | Volume-based | Yes (L-L) | **Yes** — Schiller-Naumann drag |
| Mist eliminator | Yes (vendor-calibrated) | Yes (vendor + open) | No | No | No | **Yes** — Souders-Brown + Brunazzi-Paglianti |
| Liquid-liquid separation | Yes | Yes | Retention time | Simplified | **Best-in-class** | **Yes** — Stokes + Csanady + Hinze DSD |
| Flow regime prediction | Yes (proprietary) | Partial | No | Yes (pipe) | Partial | **Yes** — Mandhane + Taitel-Dukler |
| Three-phase oil/water | Yes | Yes | Equilibrium flash | Three-layer | Emulsion model | **Yes** — EOS volume fractions |
| Turbulent diffusion correction | Yes (CFD-based) | Unknown | No | N/A | No | **Yes** — Csanady (1963) + Koenders (2015) |
| Partial flooding degradation | Yes | Unknown | No | N/A | No | **Yes** — Fabian/GPSA linear model |
| API 12J compliance check | Yes | Unknown | No | No | No | **Yes** — K, cut dia., HRT |
| Coupled with thermo EOS | Via simulator link | No (standalone) | Native (flash) | Native (pipe flow) | Limited | **Native** — SRK, PR, CPA in-process |
| Internals database | Large (proprietary, 100+) | Moderate | None | N/A | N/A | **100+ records** (CSV-extensible) |
| FPSO vessel motion | **Yes** (unique) | No | No | No | No | No |
| Sand deposition | **Yes** | No | No | No | No | No |
| Dynamic capability | Via MySep Engine plug-in | No | Yes (holdup) | Yes (transient) | No | Steady-state |
| Process integration | Via links to HYSYS etc. | Standalone | Native process sim | Pipeline focus | Standalone | **Native ProcessSystem** — recycles, adjusters, controllers |
| Open-source | No | No | No | No | No | **Yes** |
| Approx. price (USD/seat/yr) | ~15k–40k | ~5k–15k | In HYSYS license | ~30k–80k | JIP license | **Free** |

### Calibration Capability Comparison

| Calibration Feature | MySep | Kelvin | HYSYS | OLGA | **NeqSim Enhanced** |
|---------------------|-------|--------|-------|------|---------------------|
| Manual factor tuning | Yes | Yes | K-factor only | Vol./split | **Yes** — 3 independent multipliers |
| Auto-calibrate from measured data | Limited | Unknown | No | Pipeline tuning | **Yes** — `calibrateFromMeasuredFractions()` |
| Grouped-measurement convenience | No | No | No | No | **Yes** — `calibrateFromGroupedMeasurements()` |
| CSV batch calibration from case library | No | No | No | No | **Yes** — `calibrateFromCaseLibrary()` + CSV loader |
| MAPE-based fit quality metrics | No | No | No | No | **Yes** — before/after MAPE in `BatchCalibrationSummary` |
| JSON calibration report with residuals | No | No | No | No | **Yes** — `buildBatchCalibrationReportJson()` |
| Vendor-specific internals curves | **Yes** (proprietary test data) | Some | No | No | **Yes** — 25 FAT-certified curves (CSV-extensible) |
| CFD-calibrated corrections | **Yes** (core R&D) | No | No | No | No |

### Key Advantages of the NeqSim Approach

1. **Thermodynamic coupling** — fluid properties come directly from the EOS
   (SRK, PR, CPA), not from user inputs. Property changes with T/P are automatic.
   The three-phase oil/water split uses actual EOS volumetric phase fractions.

2. **Transparency** — every formula traces to a published, peer-reviewed reference.
   No black-box or proprietary data. Any engineer can audit the calculation chain.

3. **Structured calibration framework** — 3-group calibration multipliers, one-point
   auto-calibration, batch fitting from CSV case libraries, MAPE quality metrics,
   and JSON reports with per-case residuals. More systematic than the manual tuning
   in most commercial tools.

4. **Process integration** — the separator runs inside a `ProcessSystem` with
   recycles, adjusters, and controllers. Commercial tools are standalone or require
   external links to process simulators.

5. **Cost** — free, open-source, no vendor lock-in. Suitable for operators, EPCs,
   and academics who cannot justify commercial license fees.

6. **Extensible database** — add vendor data, plant measurements, or company-specific
   internals by editing CSV files without vendor support.

### Remaining Gaps vs. Commercial Tools

| Gap | Impact | Affected Tool | Possible Mitigation |
|-----|--------|---------------|---------------------|
| No CFD-calibrated DSD corrections | High | MySep | Open-literature DSD + calibration factors from field data |
| ~~No vendor-certified internals curves~~ | ~~Medium~~ | ~~MySep, Kelvin~~ | **Closed** — 25 FAT-certified vendor curves in `SeparatorVendorCurves.csv` |
| No FPSO vessel motion module | Medium | MySep | Applicable to floating facilities only |
| No sand deposition modeling | Low | MySep | Rare requirement; handle separately |
| No droplet coalescence/breakup in gravity section | Low-Medium | MySep, dEMULion | Conservative DSD assumption is standard practice |
| ~~Internals library depth (30 vs 100+)~~ | ~~Medium~~ | ~~MySep~~ | **Closed** — 100+ records (70 internals + 31 inlet devices + 25 vendor curves) |

### When to Use Which Tool

| Scenario | Recommended Tool |
|----------|-----------------|
| Detailed separator bid evaluation / vendor comparison | MySep |
| Transparent, auditable feasibility screening | **NeqSim** |
| Process simulation with realistic separation | **NeqSim** (native ProcessSystem) |
| Operator with no commercial separator license | **NeqSim** |
| Slug catcher sizing from pipeline transients | OLGA/LedaFlow → separator volume |
| Emulsion breaking / chemical dosing optimization | dEMULion |
| Quick material balance without entrainment | HYSYS/UniSim |
| Calibrating to plant data across multiple operating points | **NeqSim** (batch CSV calibration) |
| FPSO separator sizing with vessel motion | MySep |
| Academic research / teaching | **NeqSim** (open-source, auditable) |

---

## Correlations and References

| Correlation | Application | Reference |
|-------------|-------------|-----------|
| Mandhane-Gregory-Aziz flow map | Horizontal flow regime prediction | Mandhane, J.M., Gregory, G.A., Aziz, K. (1974). A flow pattern map for gas-liquid flow in horizontal pipes. *Int. J. Multiphase Flow*, 1(4), 537-553. |
| Taitel-Dukler-Barnea | Vertical flow regime transitions | Taitel, Y., Dukler, A.E. (1976); Barnea, D. (1987). A unified model for predicting flow-pattern transitions. *Int. J. Multiphase Flow*, 13(1), 1-12. |
| Azzopardi d32 | Annular flow Sauter mean diameter | Azzopardi, B.J. (1997). Drops in annular two-phase flow. *Int. J. Multiphase Flow*, 23, 1-53. |
| Ishii-Grolmes | Stratified flow entrainment onset | Ishii, M., Grolmes, M.A. (1975). Inception criteria for droplet entrainment in two-phase concurrent film flow. *AIChE J.*, 21(2), 308-318. |
| Hinze | Turbulent droplet breakup | Hinze, J.O. (1955). Fundamentals of the hydrodynamic mechanism of splitting in dispersion processes. *AIChE J.*, 1(3), 289-295. |
| Oliemans et al. | Entrained liquid fraction | Oliemans, R.V.A., Pots, B.F.M., Trompe, N. (1986). Modelling of annular dispersed two-phase flow in vertical pipes. *Int. J. Multiphase Flow*, 12(5), 711-732. |
| Schiller-Naumann | Drag coefficient correlation | Schiller, L., Naumann, A. (1933). Uber die grundlegenden Berechnungen bei der Schwerkraftaufbereitung. *Z. Ver. Dtsch. Ing.*, 77, 318-320. |
| Souders-Brown | K-factor for mist eliminator flooding | Souders, M., Brown, G.G. (1934). Design of fractionating columns. *Ind. Eng. Chem.*, 26(1), 98-103. |
| Bothamley | Inlet device momentum limits | Bothamley, M. (2013). Gas/Liquid Separators: Quantifying Separation Performance. *Oil Gas Facilities*, 2(4), 21-29. |
| Brunazzi-Paglianti | Wire mesh demister performance | Brunazzi, E., Paglianti, A. (1998). Design of wire mesh mist eliminators. *AIChE J.*, 44(3), 505-512. |
| Csanady | Turbulent diffusion cut-diameter correction | Csanady, G.T. (1963). Turbulent diffusion of heavy particles in the atmosphere. *J. Atmos. Sci.*, 20(3), 201-208. |
| Koenders et al. | Turbulence intensity from K-factor load | Koenders, M.A. et al. (2015). Gas-liquid separation: prediction of liquid carryover from gravity settlers. *SPE J.*, 20(4), 810-822. |
| API 12J | Compliance thresholds (K, cut diameter, HRT) | API Specification 12J (2014). Specification for Oil and Gas Separators. American Petroleum Institute. |
| Fabian et al. / GPSA | Partial flooding efficiency degradation | Fabian, P. et al. (1993). Demisting applications. *Chem. Eng. Progress*, 89(10), 58-63; GPSA Engineering Data Book (14th ed.), Ch. 7. |
| Hinze | Liquid-liquid droplet breakup (oil-water DSD) | Hinze, J.O. (1955). Fundamentals of the hydrodynamic mechanism of splitting in dispersion processes. *AIChE J.*, 1(3), 289-295. |

---

## Class Reference

### `SeparatorPerformanceCalculator`

Main orchestrator. Call `calculate()` with fluid properties and vessel geometry.

| Method | Description |
|--------|-------------|
| `calculate(...)` | Run standard or enhanced calculation |
| `setUseEnhancedCalculation(boolean)` | Switch between standard and enhanced |
| `setGasLiquidDSD(DropletSizeDistribution)` | Set inlet DSD (overrides flow regime prediction) |
| `setMistEliminatorCurve(GradeEfficiencyCurve)` | Set mist eliminator grade efficiency |
| `setInletDeviceModel(InletDeviceModel)` | Set inlet device |
| `setFlowRegimeCalculator(MultiphaseFlowRegime)` | Set flow regime predictor |
| `setGeometryCalculator(SeparatorGeometryCalculator)` | Set vessel geometry |
| `setInletPipeDiameter(double)` | Feed pipe diameter [m] |
| `setSurfaceTension(double)` | Gas-liquid interfacial tension [N/m] |
| `setOilWaterInterfacialTension(double)` | Oil-water interfacial tension [N/m] |
| `setLiquidInGasCalibrationFactor(double)` | Calibration multiplier for liquid-in-gas fractions |
| `setGasCarryUnderCalibrationFactor(double)` | Calibration multiplier for gas carry-under fractions |
| `setLiquidLiquidCalibrationFactor(double)` | Calibration multiplier for liquid-liquid fractions |
| `calibrateFromMeasuredFractions(...)` | Auto-calibrate from measured entrainment fractions |
| `calibrateFromGroupedMeasurements(...)` | Auto-calibrate from grouped measurement categories |
| `static loadCalibrationCasesFromCsv(String)` | Load calibration cases from CSV file |
| `calibrateFromCaseLibrary(List, double)` | Batch fit factors across multiple cases |
| `buildBatchCalibrationReportJson(List, BatchCalibrationSummary)` | JSON calibration report |
| `saveBatchCalibrationReportJson(String, List, BatchCalibrationSummary)` | Save report to file |
| `setOilVolumeFraction(double)` | Oil fraction in liquid for 3-phase geometry [0-1] — auto-set from EOS when running via `Separator` |
| `setApplyTurbulenceCorrection(boolean)` | Enable/disable Csanady turbulence correction (default: true) |
| `getApiComplianceResult()` | API 12J compliance result after `calculate()` |
| `static generateLiquidLiquidDSD(sigma, rhoCont, vNozzle, dNozzle)` | Hinze-based oil-water DSD from inlet conditions |
| `getOverallGasLiquidEfficiency()` | Combined gravity + mist eliminator efficiency |
| `getGravitySectionEfficiency()` | Gravity section efficiency alone |
| `getMistEliminatorEfficiency()` | Mist eliminator efficiency alone |
| `getGravityCutDiameter()` | Smallest droplet removed by gravity [m] |
| `getKFactor()` | Souders-Brown K-factor [m/s] |
| `getKFactorUtilization()` | K-factor as fraction of max allowed |
| `isMistEliminatorFlooded()` | True if K exceeds internals max K |
| `getInletFlowRegime()` | Predicted flow regime |
| `getPostInletDeviceDSD()` | DSD after inlet device separation |
| `toJson()` | Full results as JSON string |

### `MultiphaseFlowRegime`

| Method | Description |
|--------|-------------|
| `setGasDensity(double)` | Gas density [kg/m3] |
| `setLiquidDensity(double)` | Liquid density [kg/m3] |
| `setGasViscosity(double)` | Gas dynamic viscosity [Pa.s] |
| `setLiquidViscosity(double)` | Liquid dynamic viscosity [Pa.s] |
| `setSurfaceTension(double)` | Gas-liquid surface tension [N/m] |
| `setPipeDiameter(double)` | Pipe inner diameter [m] |
| `setGasSuperficialVelocity(double)` | Gas superficial velocity [m/s] |
| `setLiquidSuperficialVelocity(double)` | Liquid superficial velocity [m/s] |
| `setPipeOrientation(String)` | "horizontal" or "vertical" |
| `predict()` | Run prediction (no arguments) |
| `getPredictedRegime()` | Get result `FlowRegime` enum |
| `getGeneratedDSD()` | Get auto-generated DSD |
| `calcEntrainedLiquidFraction()` | Oliemans et al. correlation |

### `InletDeviceModel`

| Method | Description |
|--------|-------------|
| `InletDeviceModel(InletDeviceType)` | Constructor with device type |
| `setInletNozzleDiameter(double)` | Nozzle diameter [m] |
| `calculate(DSD, gasDens, liqDens, gasFlow, liqFlow, sigma)` | Run calculation |
| `getBulkSeparationEfficiency()` | Fraction of liquid removed at inlet |
| `getDownstreamDSD()` | DSD after inlet device |
| `getNozzleVelocity()` | Mixture velocity at nozzle [m/s] |
| `getMomentumFlux()` | Inlet momentum $\rho v^2$ [Pa] |
| `getPressureDrop()` | Device pressure drop [Pa] |

### `SeparatorGeometryCalculator`

| Method | Description |
|--------|-------------|
| `setOrientation(String)` | "horizontal" or "vertical" |
| `setInternalDiameter(double)` | Vessel ID [m] |
| `setTangentToTangentLength(double)` | Vessel length [m] |
| `setNormalLiquidLevel(double)` | Liquid level as fraction of diameter |
| `calculate(gasFlow, liqFlow)` | Two-phase geometry |
| `calculateThreePhase(gasFlow, oilFlow, waterFlow, oilFrac)` | Three-phase |
| `getGasArea()` / `getLiquidArea()` | Cross-sectional areas [m2] |
| `getEffectiveGasSettlingHeight()` | Gas settling height [m] |
| `getEffectiveLiquidSettlingHeight()` | Liquid settling height [m] |
| `getGasResidenceTime()` / `getLiquidResidenceTime()` | Residence times [s] |
| `getOilPadThickness()` / `getWaterLayerHeight()` | Three-phase heights [m] |
| `static calcKFactor(vGas, rhoGas, rhoLiq)` | Souders-Brown K [m/s] |

### `DropletSizeDistribution`

| Method | Description |
|--------|-------------|
| `static rosinRammler(d0, n)` | Create Rosin-Rammler DSD |
| `static logNormal(d50, sigma)` | Create log-normal DSD |
| `getD50()` | Median diameter [m] |
| `getSauterMeanDiameter()` | Volume-surface mean d32 [m] |
| `getDiscreteClasses()` | 20-bin discretization |
| `getCumulativeFraction(d)` | CDF at diameter d |

### `DropletSettlingCalculator`

| Method | Description |
|--------|-------------|
| `static calcTerminalVelocity(d, rhoCont, rhoDisp, muCont)` | Terminal velocity [m/s] |
| `static calcDragCoefficient(Re)` | Schiller-Naumann drag |
| `static calcTurbulenceCorrectedCutDiameter(dCut, vGas, H, kFactor, kDesign, rhoGas, rhoLiq, muGas)` | Csanady (1963) turbulence-corrected effective cut diameter [m] |
| `static checkApi12JCompliance(dCut, kFactor, hasME, liquidHRT, orientation, isThreePhase)` | API 12J compliance check returning `ApiComplianceResult` |

### `GradeEfficiencyCurve`

| Method | Description |
|--------|-------------|
| `static wireMeshDefault()` | Standard wire mesh pad |
| `static vanePackDefault()` | Standard vane pack |
| `static axialCycloneDefault()` | Standard axial cyclone |
| `static platePack(d50, maxEff)` | Custom plate pack |
| `getEfficiency(d)` | Grade efficiency at diameter d |
| `calcOverallEfficiency(DSD)` | Integrated efficiency over DSD |

### `SeparatorInternalsDatabase`

| Method | Description |
|--------|-------------|
| `static getInstance()` | Singleton accessor |
| `findByType(String)` | Find internals by type |
| `findByTypeAndSubType(String, String)` | Find specific variant |
| `findInletDeviceByType(String)` | Find inlet devices by type |
| `getAllInternals()` | All internals records (70+) |
| `getAllInletDevices()` | All inlet device records (31) |
| `getAllVendorCurves()` | All vendor curve records (25) |
| `findVendorCurvesByType(String)` | Find vendor curves by internals type |
| `findVendorCurvesByVendor(String)` | Find vendor curves by vendor name |
| `findVendorCurveById(String)` | Find a specific vendor curve by ID |
| `findVendorCurvesByTypeAndVendor(String, String)` | Find curves by type and vendor |
| `toCatalogJson()` | Full catalog as JSON (internals + inlet devices + vendor curves) |

---

## Related Documentation

- [Separator Equipment](separators.md) — Base separator documentation, entrainment specification, design constraints
- [Capacity Constraint Framework](../CAPACITY_CONSTRAINT_FRAMEWORK.md) — K-value and performance constraints system
