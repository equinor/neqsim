---
title: Compressor Mechanical Design
description: "Mechanical design calculations for centrifugal compressors in NeqSim: staging, impeller sizing, casing wall thickness per ASME VIII, flange rating per ASME B16.5, nozzle loads per API 617, NACE MR0175 compliance, thermal growth, split-line bolting, and barrel casing design."
---

# Compressor Mechanical Design

This document describes the mechanical design calculations for centrifugal compressors in NeqSim, implemented in the `CompressorMechanicalDesign` and `CompressorCasingDesignCalculator` classes.

## Overview

The mechanical design module provides sizing and design calculations for centrifugal compressors based on **API 617** (Axial and Centrifugal Compressors) and industry practice. The calculations enable:

- Preliminary equipment sizing for cost estimation
- Module footprint planning
- Driver selection
- Verification of operating point against mechanical limits
- **Casing pressure containment design per ASME Section VIII**
- **Material selection and NACE MR0175 sour-service compliance**
- **Flange rating verification per ASME B16.5/B16.47**
- **Nozzle load analysis per API 617 Table 3**
- **Thermal growth and differential expansion analysis**
- **Split-line bolt and barrel casing end-cover design**

## Design Standards Reference

| Standard | Description |
|----------|-------------|
| API 617 | Axial and Centrifugal Compressors and Expander-compressors |
| API 672 | Packaged, Integrally Geared Centrifugal Air Compressors |
| API 692 | Dry Gas Sealing Systems |
| API 614 | Lubrication, Shaft-Sealing and Oil-Control Systems |
| ASME Section VIII Div. 1 | Pressure Vessel Design (UG-27, UG-34, UG-99) |
| ASME Section II Part D | Material Properties (allowable stress tables) |
| ASME B16.5 | Pipe Flanges and Flanged Fittings (NPS 1/2–24) |
| ASME B16.47 | Large Diameter Steel Flanges (NPS 26–60) |
| NACE MR0175 / ISO 15156 | Materials for Sour (H2S) Service |

## Design Calculations

### 1. Number of Stages

The number of compression stages is determined by the total polytropic head and the maximum allowable head per stage:

```
numberOfStages = ceil(totalPolytropicHead / maxHeadPerStage)
```

**Design Limit:** Maximum head per stage = **30 kJ/kg** (typical for process gas centrifugal compressors)

The actual head per stage is then:
```
headPerStage = totalPolytropicHead / numberOfStages
```

### 2. Impeller Sizing

#### Tip Speed Calculation

The impeller tip speed is derived from the head requirement using the work coefficient:

```
tipSpeed = sqrt(headPerStage [J/kg] / workCoefficient)
```

Where:
- `workCoefficient` = 0.50 (typical for backward-curved impellers, range 0.4-0.6)

**Design Limit:** Maximum tip speed = **350 m/s** (material limit for steel impellers)

#### Impeller Diameter

From the tip speed and rotational speed:

```
impellerDiameter [mm] = (tipSpeed × 60) / (π × speedRPM) × 1000
```

The design verifies the flow coefficient is within acceptable range (0.01-0.15):

```
flowCoefficient = volumeFlow [m³/s] / (D² × U)
```

### 3. Shaft Diameter

Shaft diameter is calculated from torque requirements and allowable shear stress:

```
torque [Nm] = power [kW] × 1000 × 60 / (2π × speedRPM)
shaftDiameter [mm] = ((16 × torque) / (π × allowableShear))^(1/3) × 1000 × safetyFactor
```

Where:
- `allowableShear` = 50 MPa (typical for alloy steel shafts)
- `safetyFactor` = 1.5

### 4. Driver Sizing

Driver power includes margins per API 617:

| Shaft Power | Driver Margin |
|-------------|---------------|
| < 150 kW | 25% |
| 150-750 kW | 15% |
| > 750 kW | 10% |

```
driverPower = (shaftPower + mechanicalLosses) × driverMargin
```

### 5. Casing Design

#### Design Pressure and Temperature

```
designPressure = dischargePressure × 1.10  (10% margin)
designTemperature = dischargeTemperature + 30°C
```

#### Casing Type Selection

| Design Pressure | Casing Type |
|-----------------|-------------|
| > 100 bara | Barrel |
| 40-100 bara | Horizontally Split |
| < 40 bara | Vertically Split |

#### Casing Wall Thickness (ASME VIII Div. 1 UG-27)

The `CompressorCasingDesignCalculator` computes the required casing wall thickness for the cylindrical shell under internal pressure:

$$
t = \frac{P \times R}{S \times E - 0.6 \times P}
$$

where:
- $P$ = design pressure [MPa]
- $R$ = casing inner radius [mm]
- $S$ = allowable stress at design temperature [MPa] (per ASME II Part D)
- $E$ = weld joint efficiency (per ASME VIII UW-12, typically 0.85)

The minimum wall thickness includes:
- Corrosion allowance (default 1.5 mm, configurable)
- API 617 minimum of 12.7 mm (0.5 inch) for compressor casings
- Round-up to the nearest standard plate thickness

#### Maximum Allowable Working Pressure (MAWP)

MAWP is back-calculated from the selected wall thickness:

$$
P_{MAWP} = \frac{S \times E \times t_{eff}}{R + 0.6 \times t_{eff}}
$$

where $t_{eff}$ = selected thickness minus corrosion allowance.

#### Material Selection

Materials are selected from a built-in database covering ASME II Part D properties:

| Grade | Type | SMYS [MPa] | SMTS [MPa] | NACE Compliant | Typical Use |
|-------|------|-----------|-----------|----------------|-------------|
| SA-516-70 | Carbon Steel | 260 | 485 | No | Standard service |
| SA-516-60 | Carbon Steel | 220 | 415 | No | Low-pressure service |
| SA-266-Gr2 | CS Forging | 250 | 485 | No | Forged casings |
| SA-266-Gr4 | CS Forging | 310 | 550 | No | High-pressure barrel casings |
| SA-350-LF2 | Low-Alloy | 250 | 485 | No | Low-temperature service (to -46°C) |
| SA-182-F316L | Austenitic SS | 170 | 485 | Yes | Sour service / corrosive gas |
| SA-182-F304L | Austenitic SS | 170 | 485 | Yes | Cryogenic / sour service |
| SA-182-F22 | CrMo Forging | 310 | 515 | No | High-temperature service (>400°C) |
| Inconel-718 | Nickel Alloy | 1035 | 1241 | Yes | High-pressure sour / HP-HT |

Automatic material recommendation via `recommendMaterial()` considers:
- Sour service → SA-182-F316L or Inconel-718
- Low temperature (<-29°C) → SA-350-LF2 or SA-182-F304L
- High temperature (>450°C) → SA-182-F22
- High pressure barrel → SA-266-Gr4
- Standard service → SA-516-70

Temperature derating of allowable stress is applied per ASME II Part D Table 1A for design temperatures above 200°C.

#### Hydrostatic Test Pressure (ASME VIII UG-99)

$$
P_{test} = 1.3 \times MAWP \times \frac{S_{test}}{S_{design}}
$$

With the additional requirement that the test pressure is at least 1.5 × design pressure per API 617. The test stress must not exceed 90% of SMYS.

#### Flange Rating (ASME B16.5 / B16.47)

Flange class selection follows ASME B16.5 (NPS ≤ 24) or B16.47 (NPS > 24) pressure-temperature tables with Group 1.1 carbon steel derating:

| Flange Class | Ambient Rating [barg] |
|-------------|----------------------|
| 150 | 19.6 |
| 300 | 51.1 |
| 600 | 102.1 |
| 900 | 153.0 |
| 1500 | 255.0 |
| 2500 | 425.0 |

Ratings are derated for temperatures above 38°C.

#### Nozzle Load Analysis (API 617 Table 3)

Allowable nozzle forces and moments scale with nozzle size:

$$
F_{allow} = F_{ref} \times \left(\frac{D}{D_{ref}}\right)^{1.5} \times k
$$

$$
M_{allow} = M_{ref} \times \left(\frac{D}{D_{ref}}\right)^{2.5} \times k
$$

where $k$ = 1.85 (API 617 amplification factor), $D_{ref}$ = 200 mm (8 inch reference nozzle).

#### Thermal Growth and Differential Expansion

$$
\Delta L_{casing} = L \times \alpha \times (T_{op} - T_{amb})
$$

Differential expansion between casing and rotor is monitored to ensure seal clearances remain adequate. An acceptable limit of 2 mm is applied; values beyond this trigger a design warning.

#### Split-Line Bolt Design (Horizontally-Split Casings)

For horizontally-split casings, the split-line bolts must resist:
- Internal pressure separating force: $F_p = P \times D \times L$
- Gasket seating force (~30% of pressure force)

Bolt material is SA-193 B7 with allowable stress of 172 MPa at ambient. The calculator iterates through standard metric bolt sizes (M16–M48) and selects the smallest configuration that meets the stress requirement with minimum bolt spacing of 2.5–4× bolt diameter per API 617.

#### Barrel Casing Design

For barrel casings, the calculator also sizes:
- **Outer barrel**: cylindrical shell wall thickness via the same ASME VIII UG-27 formula
- **Inner bundle**: 2 mm radial clearance from casing ID
- **End cover**: flat head per ASME VIII UG-34 formula $t = d\sqrt{C \times P / (S \times E)}$ with $C = 0.33$
- **End cover bolting**: M36 bolts on bolt circle, minimum 12 bolts (even count)

#### NACE MR0175 / ISO 15156 Sour Service

When sour service is flagged or H2S partial pressure exceeds 0.3 kPa, the calculator:
- Classifies SSC Region (0, 1, or 3) based on H2S partial pressure
- Checks material NACE compliance status
- Verifies SMYS ≤ 360 MPa for carbon steel in sour service
- Reports hardness limit (22 HRC for CS/low-alloy)
- Issues BLOCKER if material is NON_COMPLIANT and recommends alternatives

### 6. Rotor Dynamics

#### Critical Speeds

```
maxContinuousSpeed = operatingSpeed × 1.05
tripSpeed = maxContinuousSpeed × 1.05
```

The first lateral critical speed is estimated using simplified Rayleigh-Ritz formulation based on shaft geometry.

**API 617 Requirement:** Separation margin from critical speed ≥ 15%

#### Bearing Span

```
bearingSpan = numberOfStages × (impellerDiameter × 0.8) + impellerDiameter
```

### 7. Weight Estimation

#### Rotor Weight
```
impellerWeight = numberOfStages × 0.5 × (impellerDiameter/100)^2.5
shaftWeight = bearingSpan/1000 × 7850 × π × (shaftDiameter/2000)²
rotorWeight = impellerWeight + shaftWeight
```

#### Casing Weight
```
casingThickness = max(10mm, designPressure × impellerDiameter / (2 × 150))
casingWeight = π × casingOD × casingLength × casingThickness × 7850 × 1.2
```

For barrel-type casing, add 30% additional weight.

#### Total Skid Weight

| Component | Estimation Method |
|-----------|-------------------|
| Casing | As calculated above |
| Bundle (rotor + internals) | rotorWeight + stage internals |
| Seal system | 100 × (shaftDiameter/100) kg |
| Lube oil system | 200 + driverPower × 0.1 kg |
| Baseplate | casingWeight × 0.3 |
| Piping | emptyVesselWeight × 0.2 |
| Electrical | driverPower × 0.5 kg |
| Structural steel | emptyVesselWeight × 0.15 |

### 8. Module Dimensions

```
moduleLength = compressorLength + driverLength + couplingSpace + auxiliarySpace
moduleWidth = casingOD + 3.0m (access each side)
moduleHeight = casingOD + 2.0m (piping and lifting)
```

Minimum dimensions: 4m × 3m × 3m

## Integration with CompressorMechanicalLosses

The mechanical design integrates with `CompressorMechanicalLosses` for:

- **Seal gas consumption** - Primary/secondary leakage, buffer gas, separation gas
- **Bearing losses** - Radial and thrust bearing power dissipation
- **Lube oil system sizing** - Based on heat removal requirements

When `setDesign()` is called, the mechanical losses model is automatically initialized with the calculated shaft diameter.

## Usage Example

### Basic Mechanical Design

```java
// Create and run compressor
SystemInterface gas = new SystemSrkEos(300.0, 10.0);
gas.addComponent("methane", 1.0);
gas.setMixingRule(2);

Stream inlet = new Stream("inlet", gas);
inlet.setFlowRate(10000.0, "kg/hr");

Compressor comp = new Compressor("export compressor", inlet);
comp.setOutletPressure(40.0);
comp.setPolytropicEfficiency(0.76);
comp.setSpeed(8000);

ProcessSystem ps = new ProcessSystem();
ps.add(inlet);
ps.add(comp);
ps.run();

// Calculate mechanical design
comp.getMechanicalDesign().calcDesign();

// Access design results
int stages = comp.getMechanicalDesign().getNumberOfStages();
double impellerD = comp.getMechanicalDesign().getImpellerDiameter(); // mm
double driverPower = comp.getMechanicalDesign().getDriverPower(); // kW
double totalWeight = comp.getMechanicalDesign().getWeightTotal(); // kg

// Apply design (initializes mechanical losses)
comp.getMechanicalDesign().setDesign();

// Get seal gas consumption
double sealGas = comp.getSealGasConsumption(); // Nm³/hr
```

### Casing Design with Material Selection and NACE

```java
// Configure casing-specific options before calcDesign()
CompressorMechanicalDesign design = comp.getMechanicalDesign();
design.setCasingMaterialGrade("SA-516-70");        // or "SA-182-F316L" for sour
design.setCasingCorrosionAllowanceMm(1.5);
design.setNaceCompliance(true);                    // enable NACE sour-service check
design.setH2sPartialPressureKPa(3.0);             // for NACE assessment
design.calcDesign();

// Access casing calculator
CompressorCasingDesignCalculator casingCalc = design.getCasingDesignCalculator();

double wallThk    = casingCalc.getSelectedWallThicknessMm();   // mm
double mawp       = casingCalc.getMawpBarg();                  // barg
double hydroTest  = casingCalc.getHydroTestPressureBarg();     // barg
int    flangeClass = casingCalc.getFlangeClass();               // e.g. 300, 600
String naceStatus = casingCalc.getNaceComplianceStatus();       // COMPLIANT / NON_COMPLIANT

// Get automatic material recommendation
String recommended = casingCalc.recommendMaterial();

// Full JSON report including all casing analysis
String json = design.toJson();
```

### Standalone Casing Calculator

The `CompressorCasingDesignCalculator` can also be used independently of a process simulation:

```java
CompressorCasingDesignCalculator calc = new CompressorCasingDesignCalculator();
calc.setDesignPressureBara(150.0);
calc.setDesignTemperatureC(180.0);
calc.setCasingInnerDiameterMm(500.0);
calc.setCasingLengthMm(1800.0);
calc.setMaterialGrade("SA-266-Gr4");
calc.setCorrosionAllowanceMm(1.5);
calc.setJointEfficiency(0.85);
calc.setCasingType(CompressorMechanicalDesign.CasingType.BARREL);
calc.setSourService(true);
calc.setH2sPartialPressureKPa(5.0);

calc.calculate();

// Results
System.out.println("Wall thickness: " + calc.getSelectedWallThicknessMm() + " mm");
System.out.println("MAWP: " + calc.getMawpBarg() + " barg");
System.out.println("Hydro test: " + calc.getHydroTestPressureBarg() + " barg");
System.out.println("Flange class: " + calc.getFlangeClass());
System.out.println("NACE status: " + calc.getNaceComplianceStatus());
System.out.println("Barrel OD: " + calc.getBarrelOuterODMm() + " mm");

// Full JSON with all sections
String jsonReport = calc.toJson();
```

## Design Output Parameters

### Process & Sizing Parameters (CompressorMechanicalDesign)

| Parameter | Method | Unit |
|-----------|--------|------|
| Number of stages | `getNumberOfStages()` | - |
| Head per stage | `getHeadPerStage()` | kJ/kg |
| Impeller diameter | `getImpellerDiameter()` | mm |
| Tip speed | `getTipSpeed()` | m/s |
| Shaft diameter | `getShaftDiameter()` | mm |
| Bearing span | `getBearingSpan()` | mm |
| Design pressure | `getDesignPressure()` | bara |
| Design temperature | `getDesignTemperature()` | °C |
| Casing type | `getCasingType()` | enum |
| Driver power | `getDriverPower()` | kW |
| Max continuous speed | `getMaxContinuousSpeed()` | rpm |
| Trip speed | `getTripSpeed()` | rpm |
| First critical speed | `getFirstCriticalSpeed()` | rpm |
| Casing weight | `getCasingWeight()` | kg |
| Bundle weight | `getBundleWeight()` | kg |
| Total skid weight | `getWeightTotal()` | kg |
| Module dimensions | `getModuleLength/Width/Height()` | m |

### Casing Design Parameters (CompressorCasingDesignCalculator)

Access via `comp.getMechanicalDesign().getCasingDesignCalculator()`:

| Parameter | Method | Unit |
|-----------|--------|------|
| Required wall thickness | `getRequiredWallThicknessMm()` | mm |
| Selected wall thickness | `getSelectedWallThicknessMm()` | mm |
| MAWP | `getMawpBarg()` | barg |
| Hoop stress | `getHoopStressMPa()` | MPa |
| Stress ratio | `getStressRatio()` | - |
| Hydro test pressure | `getHydroTestPressureBarg()` | barg |
| Hydro test acceptable | `isHydroTestAcceptable()` | boolean |
| Flange class | `getFlangeClass()` | - |
| Flange rating | `getFlangeRatingBarg()` | barg |
| Flange rating adequate | `isFlangeRatingAdequate()` | boolean |
| Suction nozzle allowable force | `getSuctionNozzleAllowableForceN()` | N |
| Suction nozzle allowable moment | `getSuctionNozzleAllowableMomentNm()` | Nm |
| Discharge nozzle allowable force | `getDischargeNozzleAllowableForceN()` | N |
| Discharge nozzle allowable moment | `getDischargeNozzleAllowableMomentNm()` | Nm |
| Casing axial thermal growth | `getCasingAxialGrowthMm()` | mm |
| Differential expansion | `getDifferentialExpansionMm()` | mm |
| Thermal growth acceptable | `isThermalGrowthAcceptable()` | boolean |
| Split-line bolt count | `getSplitLineBoltCount()` | - |
| Split-line bolt diameter | `getSplitLineBoltDiameterMm()` | mm |
| Split-line bolts adequate | `isSplitLineBoltsAdequate()` | boolean |
| Barrel outer OD | `getBarrelOuterODMm()` | mm |
| Barrel end cover thickness | `getBarrelEndCoverThicknessMm()` | mm |
| NACE compliance status | `getNaceComplianceStatus()` | String |
| Material SMYS | `getSmysMPa()` | MPa |
| Material SMTS | `getSmtsMPa()` | MPa |
| Recommended material | `recommendMaterial()` | String |
| Applied standards list | `getAppliedStandards()` | List |
| Design issues list | `getDesignIssues()` | List |

## Limitations and Assumptions

1. **Single-shaft configuration** - Does not handle integrally geared or multi-body compressors
2. **Empirical correlations** - Weight and dimension estimates are approximate; vendor data should be used for detailed design
3. **Steel impellers** - Tip speed limit assumes conventional steel; titanium or composites allow higher speeds
4. **Backward-curved impellers** - Work coefficient assumes standard backward-curved blade geometry
5. **No intercooling** - Multi-stage calculations assume adiabatic compression; intercooled designs require separate handling
6. **Simplified flange derating** - Linear approximation of ASME B16.5 pressure-temperature tables; rigorous interpolation should be used for final design
7. **Bolt sizing** - Gasket load estimated at 30% of pressure force; consult ASME PCC-1 for detailed gasket analysis

## JSON Output Structure

The `toJson()` method returns a comprehensive JSON report including all design sections. The casing design is nested under the `casingDesign` key:

```json
{
  "equipmentName": "export compressor",
  "designStandard": "API 617",
  "casingDesign": {
    "inputs": { "designPressure_MPa": 5.0, "materialGrade": "SA-516-70", ... },
    "materialProperties": { "SMYS_MPa": 260, "SMTS_MPa": 485, ... },
    "wallThickness": { "selectedThickness_mm": 16.0, "MAWP_barg": 55.2, ... },
    "hydrostaticTest": { "testPressure_barg": 82.8, "acceptable": true },
    "flangeRating": { "class": 600, "rating_barg": 102.1, "adequate": true },
    "nozzleLoads": { "suctionAllowableForce_N": 24789, ... },
    "thermalGrowth": { "differentialExpansion_mm": 0.45, "acceptable": true },
    "splitLineBolts": { "boltCount": 42, "boltDiameter_mm": 24, ... },
    "naceAssessment": { "status": "NOT_APPLICABLE" },
    "appliedStandards": ["API 617 8th Ed.", "ASME Section VIII Div. 1", ...],
    "designIssues": []
  }
}
```

## Related Classes

- `Compressor` - Main compressor process equipment class
- `CompressorCasingDesignCalculator` - Casing wall thickness, material, flange, nozzle, bolt, barrel, NACE calculations
- `CompressorMechanicalLosses` - Seal gas and bearing loss calculations
- `CompressorChart` - Performance curve handling
- `CompressorCostEstimate` - Cost estimation based on mechanical design
- `CompressorDesignFeasibilityReport` - Unified feasibility assessment combining mechanical design, cost, supplier matching, and curve generation (see [Compressor Design Feasibility Report](compressor_design_feasibility.md))
- `CompressorMechanicalDesignResponse` - JSON serialization DTO

## References

1. API 617, 8th Edition - Axial and Centrifugal Compressors
2. Bloch, H.P. - "A Practical Guide to Compressor Technology"
3. Japikse, D. - "Centrifugal Compressor Design and Performance"
4. Lüdtke, K.H. - "Process Centrifugal Compressors"
