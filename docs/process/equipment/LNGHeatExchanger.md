---
title: "LNG Cryogenic Multi-Stream Heat Exchanger (LNGHeatExchanger)"
description: "Comprehensive guide to the LNGHeatExchanger class for modeling brazed aluminium heat exchangers (BAHX) in LNG service. Covers rigorous H-T curves, pressure drop, exergy analysis, core sizing, freeze-out detection, thermal stress, mercury risk, mechanical design, cost estimation, and vendor matching."
---

# LNG Cryogenic Multi-Stream Heat Exchanger

## Overview

The `LNGHeatExchanger` class provides a production-grade model for **brazed aluminium heat exchangers (BAHX)** used in LNG liquefaction and natural gas processing. It extends NeqSim's `MultiStreamHeatExchanger2` with ten advanced capabilities (P1–P10) covering thermodynamic analysis, mechanical integrity, and project economics.

**Package:** `neqsim.process.equipment.heatexchanger`

**Extends:** `MultiStreamHeatExchanger2`

### When to Use

- LNG main cryogenic heat exchanger (MCHE) design and rating
- Mixed refrigerant (MR) process simulation (C3MR, DMR, SMR)
- Cryogenic gas processing (NGL recovery, nitrogen rejection)
- Any multi-stream plate-fin heat exchanger application

### Key Capabilities

| Feature | ID | Description |
|---------|-----|-------------|
| Rigorous H-T curves | P1 | TP-flash at every zone boundary captures phase transitions |
| Per-stream pressure drop | P2 | Linear ΔP interpolation shifts saturation temperatures |
| Exergy analysis | P3 | Zone-by-zone entropy generation and second-law efficiency |
| Adaptive zone refinement | P4 | Auto-subdivides zones near phase boundaries |
| Manglik-Bergles correlations | P5 | Offset-strip fin j-factor and f-factor |
| Two-phase pressure drop | P6 | Lockhart-Martinelli separated flow model |
| Dynamic cool-down transient | P7 | Lumped thermal mass model for start-up |
| Core sizing | P8 | BAHX L × W × H from duty and fin geometry |
| Freeze-out detection | P9 | CO₂ and heavy hydrocarbon solid risk per zone |
| Flow maldistribution | P10 | MITA correction factor for non-uniform distribution |
| Thermal stress assessment | — | Gradient-based fatigue warning per API 662 Part II |
| Mercury risk assessment | — | LME risk check for aluminium BAHX |
| Mechanical design | — | ASME VIII Div.1 wall thickness, fatigue life (BAHXMechanicalDesign) |
| Cost estimation | — | Weight-based CAPEX with installation factor (BAHXCostEstimator) |
| Design feasibility report | — | Verdict + supplier matching + issues + JSON report |

---

## Architecture

```
LNGHeatExchanger (process equipment)
├── extends MultiStreamHeatExchanger2
│   └── NR solver for outlet temperatures
├── computeRigorousZoneData()
│   ├── P1: TP-flash at each zone boundary
│   ├── P2: Per-stream pressure interpolation
│   ├── P3: Exergy destruction per zone
│   ├── P4: Adaptive zone refinement
│   ├── P5: Manglik-Bergles j/f correlations
│   ├── P6: Lockhart-Martinelli two-phase ΔP
│   ├── P9: Freeze-out detection
│   └── P10: Maldistribution correction
├── sizeCore()                    → P8: Core L × W × H
├── runCooldownTransient()        → P7: Dynamic transient
├── assessMercuryRisk()           → Mercury LME check
└── generateFeasibilityReport()   → Combined report
    ├── BAHXMechanicalDesign      → ASME VIII wall thickness + fatigue
    ├── BAHXCostEstimator         → CAPEX / OPEX / lifecycle
    └── HeatExchangerDesignFeasibilityReport
        ├── Supplier matching     → HeatExchangerSuppliers.csv
        ├── Feasibility checks    → BLOCKER / WARNING / INFO
        └── Verdict               → FEASIBLE / WITH_WARNINGS / NOT_FEASIBLE
```

---

## Quick Start

### Java

```java
import neqsim.process.equipment.heatexchanger.LNGHeatExchanger;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

// 1. Create fluids
SystemInterface hotFluid = new SystemSrkEos(273.15 + 30.0, 50.0);
hotFluid.addComponent("methane", 0.90);
hotFluid.addComponent("ethane", 0.05);
hotFluid.addComponent("propane", 0.03);
hotFluid.addComponent("nitrogen", 0.02);
hotFluid.setMixingRule("classic");

SystemInterface coldFluid = new SystemSrkEos(273.15 - 33.0, 3.0);
coldFluid.addComponent("methane", 0.40);
coldFluid.addComponent("ethane", 0.30);
coldFluid.addComponent("propane", 0.30);
coldFluid.setMixingRule("classic");

// 2. Create streams
Stream hotStream = new Stream("NG Feed", hotFluid);
hotStream.setFlowRate(100000.0, "kg/hr");

Stream coldStream = new Stream("MR Return", coldFluid);
coldStream.setFlowRate(150000.0, "kg/hr");

// 3. Configure BAHX
LNGHeatExchanger mche = new LNGHeatExchanger("MCHE");
mche.addInStream(hotStream);
mche.addInStream(coldStream);
mche.setNumberOfZones(15);
mche.setStreamPressureDrop(0, 1.5);  // 1.5 bar on hot side
mche.setStreamPressureDrop(1, 0.3);  // 0.3 bar on cold side

// 4. Run in process system
ProcessSystem process = new ProcessSystem();
process.add(hotStream);
process.add(coldStream);
process.add(mche);
process.run();

// 5. Read results
System.out.println("MITA: " + mche.getMITA() + " °C");
System.out.println("η_II: " + mche.getSecondLawEfficiency() * 100 + " %");
```

### Python (Jupyter)

```python
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
Stream = jneqsim.process.equipment.stream.Stream
LNGHeatExchanger = jneqsim.process.equipment.heatexchanger.LNGHeatExchanger
ProcessSystem = jneqsim.process.processmodel.ProcessSystem

# Create fluid, stream, and exchanger as above
# (see the example notebook for full Python walkthrough)
```

---

## Inner Classes

### FinGeometry

Describes the offset-strip fin geometry for Manglik-Bergles correlations and core sizing.

| Parameter | Default | Unit | Description |
|-----------|---------|------|-------------|
| `finHeight` | 0.006 | m | Fin height (passage height) |
| `finThickness` | 0.0003 | m | Fin metal thickness |
| `finPitch` | 0.0016 | m | Centre-to-centre fin spacing |
| `stripLength` | 0.003 | m | Offset strip length |
| `plateThickness` | 0.0016 | m | Parting sheet thickness |
| `finConductivity` | 170.0 | W/(m·K) | Fin thermal conductivity (aluminium) |

**Derived properties:**
- `getHydraulicDiameter()` — hydraulic diameter (m)
- `getSigma()` — free-flow area ratio (dimensionless)
- `getBeta()` — surface area density (m²/m³)

### CoreGeometry

Holds the core dimensions from sizing or manual input.

| Parameter | Unit | Description |
|-----------|------|-------------|
| `length` | m | Flow direction |
| `width` | m | Header direction |
| `height` | m | Stack-up direction |
| `numberOfLayers` | — | Layers per stream |
| `weight` | kg | Core metal weight |

**Derived:** `getVolume()` returns L × W × H in m³.

### TransientPoint

Immutable snapshot from cool-down transient analysis.

| Field | Unit | Description |
|-------|------|-------------|
| `timeHours` | hr | Elapsed time |
| `metalTempC` | °C | Average metal temperature |
| `fluidOutTempC` | °C | Fluid outlet temperature |
| `dutyKW` | kW | Instantaneous duty |

---

## Configuration Reference

### Zone and Type Settings

| Method | Default | Description |
|--------|---------|-------------|
| `setNumberOfZones(int)` | 20 | Axial discretisation count |
| `setExchangerType(String)` | `"BAHX"` | Type: `"BAHX"`, `"PCHE"`, `"CWHE"` |

### Pressure Drop (P2)

| Method | Description |
|--------|-------------|
| `setStreamPressureDrop(int idx, double bar)` | Set total ΔP for stream `idx` |
| `getStreamPressureDrop(int idx)` | Get ΔP in bar |

Pressure is linearly interpolated from inlet to outlet during the zone analysis. This shifts saturation temperatures and affects pinch location, typically by 1–3 °C for LNG exchangers.

### Exergy Settings (P3)

| Method | Default | Description |
|--------|---------|-------------|
| `setReferenceTemperature(double °C)` | 15.0 | Dead-state temperature for exergy |

### Adaptive Refinement (P4)

| Method | Default | Description |
|--------|---------|-------------|
| `setAdaptiveRefinement(boolean)` | false | Enable auto-refinement |
| `setMaxAdaptiveZones(int)` | 100 | Maximum zones after refinement |
| `setAdaptiveThresholdFactor(double)` | 2.0 | Gradient multiplier for refinement trigger |

When enabled, zones where enthalpy gradient exceeds `thresholdFactor × average` are bisected. This improves resolution near phase boundaries (e.g., MR evaporation onset).

### Fin Geometry (P5)

| Method | Description |
|--------|-------------|
| `setStreamFinGeometry(int idx, FinGeometry)` | Set fin geometry for stream |
| `getStreamFinGeometry(int idx)` | Get fin geometry (null if not set) |

When fin geometry is set, Manglik-Bergles (1995) correlations compute:
- **j-factor** (Colburn analogy heat transfer coefficient)
- **f-factor** (Fanning friction factor)
- **Detailed ΔP** (single-phase + Lockhart-Martinelli two-phase correction)

### Maldistribution (P10)

| Method | Default | Description |
|--------|---------|-------------|
| `setFlowMaldistributionFactor(double)` | 1.0 | Penalty factor (0.85–0.95 typical) |

Reduces effective UA and MITA. A factor of 0.90 means 10% performance loss due to flow maldistribution.

### Core Geometry (P8)

| Method | Description |
|--------|-------------|
| `setCoreGeometry(CoreGeometry)` | Set core dimensions manually |
| `getCoreGeometry()` | Get core geometry (populated by `sizeCore()`) |
| `sizeCore()` | Auto-size from UA requirement and fin geometry |

### Transient (P7)

| Method | Description |
|--------|-------------|
| `setCoreThermalMass(double kJ/K)` | Set metal thermal mass |
| `runCooldownTransient(targetC, ambientC, steps, hours)` | Run transient |

### Thermal Stress

| Method | Default | Description |
|--------|---------|-------------|
| `setMaxAllowableThermalGradient(double °C/m)` | 5.0 | API 662 Part II limit |

---

## Result Getters

### Temperature Approach

| Method | Returns | Unit |
|--------|---------|------|
| `getMITA()` | Minimum internal temperature approach | °C |
| `getMITAZoneIndex()` | Zone index where MITA occurs | — |
| `getMITAPerZone()` | Per-zone approaches | °C[] |

### Composite Curves

| Method | Returns | Description |
|--------|---------|-------------|
| `getHotCompositeCurve()` | `double[nPoints][2]` | Col 0: cumulative duty (kW), Col 1: temperature (°C) |
| `getColdCompositeCurve()` | `double[nPoints][2]` | Same format |

### Exergy

| Method | Returns | Unit |
|--------|---------|------|
| `getExergyDestructionPerZone()` | Per-zone exergy destruction | kW[] |
| `getTotalExergyDestruction()` | Total destruction | kW |
| `getSecondLawEfficiency()` | η_II | dimensionless (0–1) |

### UA

| Method | Returns | Unit |
|--------|---------|------|
| `getUAPerZone()` | Per-zone UA values | W/K[] |

### Pressure Drop Correlations

| Method | Returns | Unit |
|--------|---------|------|
| `getComputedStreamDP()` | Computed ΔP per stream | bar[] |
| `getStreamJFactor()` | Average j-factor per stream | —[] |
| `getStreamFFactor()` | Average f-factor per stream | —[] |

### Freeze-Out (P9)

| Method | Returns | Description |
|--------|---------|-------------|
| `getFreezeOutRiskPerZone()` | `boolean[]` | True if zone below CO₂ triple point (-56.6 °C) |
| `hasFreezeOutRisk()` | `boolean` | True if any zone has risk |

### Temperature Profiles

| Method | Returns | Unit |
|--------|---------|------|
| `getZoneTempProfileHotC()` | Hot-side temperatures | °C[] |
| `getZoneTempProfileColdC()` | Cold-side temperatures | °C[] |

### Thermal Stress

| Method | Returns | Description |
|--------|---------|-------------|
| `getThermalGradientPerZone()` | `double[]` | Gradient in °C/m |
| `hasThermalStressWarning()` | `boolean` | True if any zone exceeds limit |

### Mercury Risk

| Method | Returns | Description |
|--------|---------|-------------|
| `isMercuryRiskPresent()` | `boolean` | True if Hg exceeds 10 ppb |
| `getMercuryRiskMessage()` | `String` | Detailed warning message |

### Cool-Down Transient

| Method | Returns | Description |
|--------|---------|-------------|
| `getTransientResults()` | `List<TransientPoint>` | Time-series data |

---

## Mechanical Design (BAHXMechanicalDesign)

**Package:** `neqsim.process.mechanicaldesign.heatexchanger`

BAHX-specific mechanical design calculations per ASME VIII Div.1 and ALPEMA standards.

### Materials

| Component | Alloy | Allowable Stress (MPa) | Density (kg/m³) |
|-----------|-------|----------------------|----------------|
| Core (fins, plates) | Al 3003-H14 | 62 | 2730 |
| Headers, nozzles | Al 5083 | 117 | 2730 |

Brazed joint efficiency: **0.80** per ASME VIII.

### Wall Thickness Calculations

**Parting sheet** (ASME UG-27 cylindrical shell formula):

$$t = \frac{P \cdot R}{S \cdot E - 0.6 \cdot P}$$

Where $P$ = design pressure (MPa), $R$ = half fin height (mm), $S$ = allowable stress (MPa), $E$ = joint efficiency.

**Header plate** (flat plate formula):

$$t = d \sqrt{\frac{C \cdot P}{S}}$$

Where $d$ = header port width, $C$ = 0.33 (simply supported).

**Nozzle** (cylindrical shell, same as parting sheet with nozzle radius).

### Thermal Fatigue Assessment

Per API 662 Part II guidelines:
- Computes thermal stress: $\sigma = E \cdot \alpha \cdot \Delta T$
- Uses Al S-N curve (endurance limit 35 MPa at 10⁷ cycles, slope m=4)
- Reports fatigue utilisation ratio: design cycles / allowable cycles

### Usage

```java
BAHXMechanicalDesign mech = new BAHXMechanicalDesign(lngHX);
mech.setMaxOperationPressure(50.0);  // bara
mech.setMaxOperationTemperature(273.15 + 30.0);  // K
mech.calcDesign();

double partingSheet = mech.getRequiredPartingSheetThicknessMm();
double coreWeight = mech.getCoreWeightKg();
String json = mech.toJson();
```

---

## Cost Estimation (BAHXCostEstimator)

**Package:** `neqsim.process.costestimation.heatexchanger`

Weight-based and area-based cost model for BAHX.

### Cost Model

| Component | Rate | Basis |
|-----------|------|-------|
| Core material (Al) | $8/kg | Raw aluminium |
| Manufacturing | $25/kg | Forming, assembly |
| Header/nozzle surcharge | $15/kg | Additional to material |
| Brazing | $120/m² | Vacuum brazing |
| Testing & inspection | 12% | Of material + manufacturing |
| Engineering overhead | 1.15× | Applied to PEC |
| LNG installation factor | 3.5× | Installed cost |
| Contingency | 1.10× | Applied to installed cost |
| Annual maintenance | 2% | Of equipment cost |

### Usage

```java
BAHXCostEstimator cost = new BAHXCostEstimator(mechDesign);
double equipment = cost.getEquipmentCostUSD();
double installed = cost.getInstalledCostUSD();
double specific = cost.getSpecificCostPerM2();
Map<String, Object> breakdown = cost.getCostBreakdown();
```

---

## Design Feasibility Report

**Package:** `neqsim.process.mechanicaldesign.heatexchanger`

Combines mechanical design, cost estimation, supplier matching, and feasibility checks into a single report.

### Verdict Categories

| Verdict | Meaning |
|---------|---------|
| `FEASIBLE` | All checks pass, no blockers or warnings |
| `FEASIBLE_WITH_WARNINGS` | No blockers, but warnings present |
| `NOT_FEASIBLE` | At least one BLOCKER issue |

### Feasibility Checks

**General checks:**
- Temperature range (operating vs. supplier capability)
- Pressure range
- Heat transfer area range
- Cost reasonableness (< $50M equipment cost)
- Supplier availability

**BAHX-specific checks:**
- Mercury risk (aluminium LME)
- Freeze-out risk (CO₂ solidification)
- Thermal stress (gradient limit)
- MITA adequacy (> 1.0 °C)
- Thermal fatigue utilisation (< 1.0)
- Exergy efficiency (> 50%)

### Supplier Database

The report matches against `HeatExchangerSuppliers.csv` containing 18 vendors. Key BAHX suppliers:
- **Chart Industries** — plate-fin BAHX, -269 °C to +65 °C, 100–300,000 kW, 5–30,000 m²
- Other vendors matched on type, duty, area, pressure, and temperature ranges

### Usage

```java
// Direct construction
HeatExchangerDesignFeasibilityReport report =
    new HeatExchangerDesignFeasibilityReport(lngHX);
report.setDesignStandard("ALPEMA / ASME VIII Div.1");
report.setAnnualOperatingHours(8400);
report.generateReport();

String verdict = report.getVerdict();
double pec = report.getPurchasedEquipmentCostUSD();
String json = report.toJson();

// Or use the convenience method
HeatExchangerDesignFeasibilityReport report = lngHX.generateFeasibilityReport();
```

### JSON Report Structure

```json
{
  "verdict": "FEASIBLE_WITH_WARNINGS",
  "operatingPoint": {
    "dutyKW": 45000.0,
    "hotInletTempC": 30.0,
    "coldInletTempC": -33.0,
    "maxPressureBara": 50.0,
    "estimatedAreaM2": 8500.0
  },
  "mechanicalDesign": {
    "partingSheetMm": 1.2,
    "headerThicknessMm": 15.4,
    "coreWeightKg": 4200,
    "totalWeightKg": 5100
  },
  "costEstimation": {
    "purchasedEquipmentCostUSD": 295000,
    "installedCostUSD": 1135000,
    "specificCostPerM2USD": 34.7,
    "annualMaintenanceCostUSD": 5900
  },
  "matchingSuppliers": [...],
  "bahxDiagnostics": {
    "mita_C": 3.2,
    "secondLawEfficiency": 0.91,
    "hasFreezeOutRisk": false,
    "hasThermalStressWarning": false,
    "mercuryRiskPresent": false
  },
  "issues": [
    {"severity": "WARNING", "category": "thermal_stress", "message": "..."}
  ]
}
```

---

## Physical Background

### Composite Curves and MITA

In a multi-stream heat exchanger, composite curves represent the aggregated temperature-duty relationship of all hot streams (being cooled) and all cold streams (being heated). The minimum internal temperature approach (MITA) is the closest point between these curves and represents the thermodynamic bottleneck.

For LNG exchangers, the MITA typically occurs at:
- MR evaporation onset (where the cold curve flattens due to latent heat)
- Methane condensation (where the hot curve flattens)

Typical MITA values: 1.5–3.0 °C for large MCHE, 3.0–5.0 °C for smaller exchangers.

### Exergy Analysis

Exergy destruction quantifies thermodynamic irreversibility:

$$\dot{E}_{dest,zone} = T_0 \cdot \dot{S}_{gen,zone}$$

Where $T_0$ is the dead-state temperature and $\dot{S}_{gen}$ is the entropy generation rate. The second-law efficiency is:

$$\eta_{II} = \frac{\dot{E}_{gained,cold}}{\dot{E}_{released,hot}}$$

Typical η_II for LNG MCHE: 85–95%.

### Manglik-Bergles Correlations

For offset-strip fins (OSF), valid for $120 < Re < 10{,}000$:

$$j = 0.6522 \cdot Re^{-0.5403} \cdot \alpha^{-0.1541} \cdot \delta^{0.1499} \cdot \gamma^{-0.0678} \cdot F_j$$

$$f = 9.6243 \cdot Re^{-0.7422} \cdot \alpha^{-0.1856} \cdot \delta^{0.3053} \cdot \gamma^{-0.2659} \cdot F_f$$

Where $\alpha = s/h$, $\delta = t/l_s$, $\gamma = t/s$ are dimensionless fin geometry ratios.

### Mercury Risk in BAHX

Mercury attacks aluminium through liquid metal embrittlement (LME), forming an Hg-Al amalgam that destroys brazed joints. Industry specification for feed gas to aluminium BAHX: < 10 ppb Hg (some operators specify < 0.01 µg/Nm³). Mitigation: activated carbon or metal sulfide bed upstream.

---

## Applicable Standards

| Standard | Application |
|----------|-------------|
| ASME VIII Div.1, UG-27 | Pressure vessel wall thickness |
| ALPEMA | BAHX design and manufacturing |
| API 662 Part II | Plate-type heat exchanger thermal fatigue |
| ISO 14692 | GRP piping systems (if applicable) |

---

## Related Classes

| Class | Package | Description |
|-------|---------|-------------|
| `MultiStreamHeatExchanger2` | `process.equipment.heatexchanger` | Parent class with NR solver |
| `BAHXMechanicalDesign` | `process.mechanicaldesign.heatexchanger` | ASME VIII wall thickness + fatigue |
| `BAHXCostEstimator` | `process.costestimation.heatexchanger` | BAHX CAPEX/OPEX model |
| `HeatExchangerDesignFeasibilityReport` | `process.mechanicaldesign.heatexchanger` | Combined feasibility report |
| `HeatExchangerType.PLATE_FIN` | `process.mechanicaldesign.heatexchanger` | BAHX geometry model enum |

---

## References

1. Manglik, R.M. and Bergles, A.E. (1995). "Heat transfer and pressure drop correlations for the rectangular offset-strip fin compact heat exchanger." *Experimental Thermal and Fluid Science*, 10(2), pp.171-180.
2. Lockhart, R.W. and Martinelli, R.C. (1949). "Proposed correlation of data for isothermal two-phase, two-component flow in pipes." *Chemical Engineering Progress*, 45(1), pp.39-48.
3. ALPEMA Standards (2010). *The Standards of the Brazed Aluminium Plate-Fin Heat Exchanger Manufacturers' Association*, 3rd Edition.
4. ASME Boiler & Pressure Vessel Code, Section VIII Division 1 (2021).
5. API Standard 662, Part II (2016). *Plate Heat Exchangers for General Refinery Services*.
