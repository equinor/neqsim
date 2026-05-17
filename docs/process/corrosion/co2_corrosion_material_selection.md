---
title: "CO2CorrosionMaterialSelection — CRA Selection for CO2 Service"
description: "API reference for CO2CorrosionMaterialSelection — corrosion resistant alloy (CRA) selection hierarchy for CO2-containing environments per NORSOK M-001 and EFC 17. Covers carbon steel viability, 13Cr/22Cr/25Cr duplex and nickel alloy selection logic."
---

# CO2CorrosionMaterialSelection

**Package:** `neqsim.process.corrosion`

**Standards:** NORSOK M-001, EFC 17, ISO 15156-3, DNV-RP-F112

Determines when carbon steel with inhibition or corrosion allowance is insufficient, and recommends the appropriate CRA grade based on CO2 corrosion rate, H2S content, chloride concentration, and temperature.

## Material Hierarchy

The class evaluates materials in order of increasing cost:

| Priority | Material | Relative Cost | Typical Application |
|----------|----------|---------------|---------------------|
| 1 | Carbon steel + corrosion inhibition | 1.0× | Sweet service, low rate, inhibition feasible |
| 2 | 13Cr martensitic stainless steel | 2.5× | Sweet service, moderate CO2, low chloride |
| 3 | 22Cr duplex stainless steel | 4.0× | Moderate sour, moderate chloride |
| 4 | 25Cr super duplex stainless steel | 5.5× | High chloride, moderate sour, high temperature |
| 5 | Nickel alloy (Alloy 625 or C-276) | 8.0× | Severe sour + high chloride + high temperature |

## Selection Logic

### Carbon Steel Viability

Carbon steel is viable when **all** of these are true:

- Corrosion allowance ≤ 6.0 mm (rate × design life)
- H2S partial pressure < 0.003 bar (non-sour)
- CO2 corrosion rate < 10.0 mm/yr

### CRA Selection Criteria

| Material | Conditions |
|----------|------------|
| 13Cr | Non-sour, temperature ≤ 150°C, chloride ≤ 50,000 mg/L |
| 22Cr duplex | Mild sour acceptable, temperature ≤ 200°C, chloride ≤ 120,000 mg/L |
| 25Cr super duplex | Temperature ≤ 230°C, chloride ≤ 200,000 mg/L |
| Nickel alloy | Fallback when no other CRA qualifies |

## Quick Start

### Java

```java
import neqsim.process.corrosion.CO2CorrosionMaterialSelection;

CO2CorrosionMaterialSelection sel = new CO2CorrosionMaterialSelection();
sel.setCO2PartialPressureBar(5.0);
sel.setH2SPartialPressureBar(0.001);
sel.setCO2CorrosionRateMmyr(2.5);
sel.setTemperatureC(80.0);
sel.setChlorideConcentrationMgL(30000);
sel.setInhibitionFeasible(false);
sel.setDesignLifeYears(25);
sel.evaluate();

System.out.println("Material: " + sel.getSelectedMaterial());
System.out.println("CS Viable: " + sel.isCarbonSteelViable());
System.out.println("Cost Factor: " + sel.getRelativeCostFactor());
System.out.println("Rationale: " + sel.getSelectionRationale());
```

### Python

```python
from neqsim import jneqsim

CRASelection = jneqsim.process.corrosion.CO2CorrosionMaterialSelection

sel = CRASelection()
sel.setCO2PartialPressureBar(5.0)
sel.setH2SPartialPressureBar(0.001)
sel.setCO2CorrosionRateMmyr(2.5)
sel.setTemperatureC(80.0)
sel.setChlorideConcentrationMgL(30000)
sel.setInhibitionFeasible(False)
sel.setDesignLifeYears(25)
sel.evaluate()

print(f"Selected: {sel.getSelectedMaterial()}")
print(f"Cost factor: {sel.getRelativeCostFactor()}")
```

## Input Parameters

| Method | Type | Default | Description |
|--------|------|---------|-------------|
| `setCO2PartialPressureBar(double)` | double | 0.0 | CO2 partial pressure (bar) |
| `setH2SPartialPressureBar(double)` | double | 0.0 | H2S partial pressure (bar) |
| `setCO2CorrosionRateMmyr(double)` | double | 0.0 | Uninhibited CO2 corrosion rate (mm/yr) |
| `setTemperatureC(double)` | double | 60.0 | Operating temperature (°C) |
| `setChlorideConcentrationMgL(double)` | double | 0.0 | Chloride concentration (mg/L) |
| `setInhibitionFeasible(boolean)` | boolean | false | Whether corrosion inhibition is practical |
| `setInhibitorAvailability(double)` | double | 0.9 | Inhibitor availability factor (0–1) |
| `setDesignLifeYears(double)` | double | 25.0 | Design life (years) |
| `setInSituPH(double)` | double | 5.0 | In-situ pH |

## Output Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `getSelectedMaterial()` | String | Recommended material grade |
| `isCarbonSteelViable()` | boolean | Whether CS + CA is acceptable |
| `getCsCorrosionAllowanceMm()` | double | Required corrosion allowance for CS (mm) |
| `getRelativeCostFactor()` | double | Relative cost vs carbon steel |
| `getSelectionRationale()` | String | Explanation of selection decision |
| `toMap()` | Map | All results as a LinkedHashMap |
| `toJson()` | String | Complete JSON report |

## Integration with NorsokM506CorrosionRate

Typical workflow: compute the corrosion rate first, then feed it to material selection:

```java
NorsokM506CorrosionRate m506 = new NorsokM506CorrosionRate();
m506.setTemperatureCelsius(80.0);
m506.setTotalPressureBara(100.0);
m506.setCO2MoleFraction(0.03);
m506.calculate();

CO2CorrosionMaterialSelection sel = new CO2CorrosionMaterialSelection();
sel.setCO2CorrosionRateMmyr(m506.getCorrectedCorrosionRate());
sel.setH2SPartialPressureBar(0.001);
sel.setChlorideConcentrationMgL(30000);
sel.evaluate();
```

## Related Documentation

- [NorsokM506CorrosionRate](norsok_m506_corrosion_rate) — CO2 corrosion rate prediction
- [NorsokM001MaterialSelection](norsok_m001_material_selection) — Full material selection framework
- [SourServiceAssessment](sour_service_assessment) — Sour service evaluation per ISO 15156
