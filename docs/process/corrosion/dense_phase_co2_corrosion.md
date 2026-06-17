---
title: "DensePhaseCO2Corrosion — CCS Pipeline Corrosion Assessment"
description: "API reference for DensePhaseCO2Corrosion — corrosion assessment for dense-phase CO2 transport pipelines per DNV-RP-J202 and ISO 27913. Covers impurity limits, free water risk, water solubility, and material recommendations for CCS systems."
---

# DensePhaseCO2Corrosion

**Package:** `neqsim.process.corrosion`

**Standards:** DNV-RP-J202, ISO 27913, DNV-RP-F104, ASME B31.4

Evaluates corrosion risk in dense-phase CO2 transport systems for carbon capture and storage (CCS). In dry dense-phase CO2, corrosion is negligible — but free water formation from impurity interactions can cause severe attack exceeding 10 mm/yr.

## Key Principle

Dense-phase CO2 is only corrosive when **free water** forms. The critical assessment is whether the actual water content exceeds the water solubility limit at the operating conditions:

$$
\text{Water margin} = \text{Solubility limit} - \text{Actual water content}
$$

Negative margin = free water risk = potential for severe corrosion.

## Impurity Specification Limits

| Impurity | Typical Spec Limit | Risk |
|----------|--------------------|------|
| H2O | 50–500 ppmv | Free water formation → corrosion |
| O2 | 10–100 ppmv | Acidification with SO2/NOx |
| SO2 | 10–50 ppmv | Sulfurous acid formation |
| NOx | 10–50 ppmv | Nitric acid formation |
| H2S | 50–200 ppmv | Sour corrosion if water present |
| H2 | < 2 mol% | Material embrittlement |

## Quick Start

### Java

```java
import neqsim.process.corrosion.DensePhaseCO2Corrosion;

DensePhaseCO2Corrosion co2 = new DensePhaseCO2Corrosion();
co2.setTemperatureC(25.0);
co2.setPressureBara(110.0);
co2.setCo2PurityMolPct(98.5);
co2.setWaterContentPpmv(200.0);
co2.setO2ContentPpmv(50.0);
co2.setSo2ContentPpmv(20.0);
co2.setNoxContentPpmv(10.0);
co2.setH2sContentPpmv(100.0);
co2.setH2ContentMolPct(0.5);
co2.setMaterialType("Carbon steel");
co2.evaluate();

System.out.println("Free Water Risk: " + co2.isFreeWaterRisk());
System.out.println("Water Margin: " + co2.getWaterMarginPpmv() + " ppmv");
System.out.println("Risk Level: " + co2.getRiskLevel());
System.out.println("Wet Corrosion Rate: " + co2.getWetCorrosionRateMmYr() + " mm/yr");
System.out.println("Meets Specs: " + co2.isMeetsImpuritySpecs());
System.out.println("Phase: " + co2.getCo2PhaseState());
System.out.println("Recommendation: " + co2.getRecommendation());
```

### Python

```python
from neqsim import jneqsim

DenseCO2 = jneqsim.process.corrosion.DensePhaseCO2Corrosion

co2 = DenseCO2()
co2.setTemperatureC(25.0)
co2.setPressureBara(110.0)
co2.setCo2PurityMolPct(98.5)
co2.setWaterContentPpmv(200.0)
co2.setO2ContentPpmv(50.0)
co2.setSo2ContentPpmv(20.0)
co2.evaluate()

print(f"Free water risk: {co2.isFreeWaterRisk()}")
print(f"Risk level: {co2.getRiskLevel()}")
print(f"Phase: {co2.getCo2PhaseState()}")
```

### Integration with NeqSim Fluid

```java
// Auto-extract impurity levels from a NeqSim fluid
DensePhaseCO2Corrosion co2 = new DensePhaseCO2Corrosion();
co2.setFluid(flashedCO2System);  // auto-reads compositions
co2.evaluate();
```

## Input Parameters

| Method | Type | Default | Description |
|--------|------|---------|-------------|
| `setTemperatureC(double)` | double | 25.0 | Temperature (°C) |
| `setPressureBara(double)` | double | 100.0 | Pressure (bara) |
| `setCo2PurityMolPct(double)` | double | 99.0 | CO2 purity (mol%) |
| `setWaterContentPpmv(double)` | double | 0.0 | Water content (ppmv) |
| `setO2ContentPpmv(double)` | double | 0.0 | O2 content (ppmv) |
| `setSo2ContentPpmv(double)` | double | 0.0 | SO2 content (ppmv) |
| `setNoxContentPpmv(double)` | double | 0.0 | NOx content (ppmv) |
| `setH2sContentPpmv(double)` | double | 0.0 | H2S content (ppmv) |
| `setH2ContentMolPct(double)` | double | 0.0 | H2 content (mol%) |
| `setN2ContentMolPct(double)` | double | 0.0 | N2 content (mol%) |
| `setArContentMolPct(double)` | double | 0.0 | Ar content (mol%) |
| `setMaterialType(String)` | String | "Carbon steel" | Material type |
| `setFluid(SystemInterface)` | SystemInterface | — | Auto-extract compositions from fluid |

## Output Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `isFreeWaterRisk()` | boolean | Whether free water may form |
| `getWaterSolubilityLimitPpmv()` | double | Water solubility limit at conditions (ppmv) |
| `getWaterMarginPpmv()` | double | Margin below solubility limit (negative = risk) |
| `getRiskLevel()` | String | "Low", "Medium", "High", "Very High" |
| `getWetCorrosionRateMmYr()` | double | Estimated corrosion rate if water present (mm/yr) |
| `isMeetsImpuritySpecs()` | boolean | Whether all impurities are within spec |
| `getImpurityIssues()` | List&lt;String&gt; | List of impurity limit exceedances |
| `getCo2PhaseState()` | String | "Gas", "Liquid", "Supercritical", "Dense phase" |
| `getRecommendation()` | String | Corrosion management recommendation |
| `getNotes()` | List&lt;String&gt; | Assessment notes |
| `toMap()` | Map | All results as a LinkedHashMap |
| `toJson()` | String | Complete JSON report |

## Related Documentation

- [CO2 Injection Well Analysis](../co2_injection_well_analysis) — Full CO2 injection well safety analysis
- [Pipeline Corrosion Integration](pipeline_corrosion_integration) — Process simulation integration
- [NorsokM506CorrosionRate](norsok_m506_corrosion_rate) — CO2 corrosion rate prediction
