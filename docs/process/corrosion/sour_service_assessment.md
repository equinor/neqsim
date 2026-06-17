---
title: "SourServiceAssessment — ISO 15156 / NACE MR0175 Sour Service"
description: "API reference for SourServiceAssessment — sour service material evaluation per ISO 15156 and NACE MR0175. Covers sour region classification, SSC, HIC, SOHIC risk assessment, and material recommendations for H2S-containing environments."
---

# SourServiceAssessment

**Package:** `neqsim.process.corrosion`

**Standards:** ISO 15156-1/2/3, NACE MR0175, NORSOK M-001, EFC 16/17

Evaluates whether materials are suitable for hydrogen sulfide (H2S)-containing environments by classifying the sour severity region and checking material limits for sulfide stress cracking (SSC), hydrogen-induced cracking (HIC), and stress-oriented hydrogen-induced cracking (SOHIC).

## Sour Service Regions (ISO 15156)

| Region | H2S Level | Description |
|--------|-----------|-------------|
| 0 | pH2S < 0.3 kPa (0.003 bar) | Non-sour |
| 1 | 0.3–1.0 kPa, moderate pH | Mildly sour |
| 2 | 1.0–10.0 kPa | Moderately sour |
| 3 | > 10.0 kPa or low pH | Severely sour |

## Failure Mechanisms Assessed

| Mechanism | Description | Key Factors |
|-----------|-------------|-------------|
| **SSC** | Sulfide stress cracking | Hardness (HRC), yield strength, sour region |
| **HIC** | Hydrogen-induced cracking | H2S level, free water, inclusions |
| **SOHIC** | Stress-oriented HIC | Residual stress, PWHT status |

## Quick Start

### Java

```java
import neqsim.process.corrosion.SourServiceAssessment;

SourServiceAssessment ssa = new SourServiceAssessment();
ssa.setH2SPartialPressureBar(0.05);
ssa.setTotalPressureBar(100.0);
ssa.setCO2PartialPressureBar(3.0);
ssa.setInSituPH(4.0);
ssa.setTemperatureC(80.0);
ssa.setChlorideConcentrationMgL(50000);
ssa.setMaterialGrade("X65");
ssa.setHardnessHRC(22.0);
ssa.setYieldStrengthMPa(450.0);
ssa.setPWHTApplied(true);
ssa.setFreeWaterPresent(true);
ssa.evaluate();

System.out.println("Sour Region: " + ssa.getSourRegion());
System.out.println("SSC Acceptable: " + ssa.isSSCAcceptable());
System.out.println("HIC Acceptable: " + ssa.isHICAcceptable());
System.out.println("SOHIC Acceptable: " + ssa.isSOHICAcceptable());
System.out.println("Overall Risk: " + ssa.getOverallRiskLevel());
System.out.println("Recommended Material: " + ssa.getRecommendedMaterial());
```

### Python

```python
from neqsim import jneqsim

SourServiceAssessment = jneqsim.process.corrosion.SourServiceAssessment

ssa = SourServiceAssessment()
ssa.setH2SPartialPressureBar(0.05)
ssa.setTotalPressureBar(100.0)
ssa.setCO2PartialPressureBar(3.0)
ssa.setInSituPH(4.0)
ssa.setTemperatureC(80.0)
ssa.setMaterialGrade("X65")
ssa.evaluate()

print(f"Sour Region: {ssa.getSourRegion()}")
print(f"SSC OK: {ssa.isSSCAcceptable()}")
print(f"Overall Risk: {ssa.getOverallRiskLevel()}")
```

### Integration with NeqSim Fluid

```java
// Extract H2S/CO2 partial pressures from a flashed fluid
SourServiceAssessment ssa = new SourServiceAssessment();
ssa.setFluid(flashedSystem);  // auto-extracts partial pressures
ssa.setMaterialGrade("X65");
ssa.evaluate();
```

## Input Parameters

| Method | Type | Default | Description |
|--------|------|---------|-------------|
| `setH2SPartialPressureBar(double)` | double | 0.0 | H2S partial pressure (bar) |
| `setTotalPressureBar(double)` | double | 0.0 | Total system pressure (bar) |
| `setCO2PartialPressureBar(double)` | double | 0.0 | CO2 partial pressure (bar) |
| `setInSituPH(double)` | double | 5.0 | In-situ pH of aqueous phase |
| `setTemperatureC(double)` | double | 60.0 | Operating temperature (°C) |
| `setChlorideConcentrationMgL(double)` | double | 0.0 | Chloride concentration (mg/L) |
| `setMaterialGrade(String)` | String | "X65" | Material grade (e.g., "X65", "L80", "316L") |
| `setYieldStrengthMPa(double)` | double | 450.0 | Actual yield strength (MPa) |
| `setHardnessHRC(double)` | double | 22.0 | Maximum hardness (HRC) |
| `setPWHTApplied(boolean)` | boolean | false | Post-weld heat treatment applied |
| `setFreeWaterPresent(boolean)` | boolean | true | Free water present in system |
| `setElementalSulfurPresent(boolean)` | boolean | false | Elemental sulfur present |
| `setFluid(SystemInterface)` | SystemInterface | — | Auto-extracts H2S/CO2 partial pressures from fluid |

## Output Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `getSourRegion()` | int | ISO 15156 sour region (0–3) |
| `isSSCAcceptable()` | boolean | SSC risk acceptable for this material |
| `isHICAcceptable()` | boolean | HIC risk acceptable |
| `isSOHICAcceptable()` | boolean | SOHIC risk acceptable |
| `getSSCRiskLevel()` | String | SSC risk: "Low", "Medium", "High", "Very High" |
| `getHICRiskLevel()` | String | HIC risk level |
| `getOverallRiskLevel()` | String | Combined worst-case risk level |
| `getRecommendedMaterial()` | String | Recommended material for this environment |
| `getNotes()` | List&lt;String&gt; | Detailed assessment notes and warnings |
| `getStandardsApplied()` | List&lt;String&gt; | Standards referenced in the assessment |
| `toMap()` | Map | All results as a LinkedHashMap |
| `toJson()` | String | Complete JSON report |

## SSC Acceptability Criteria

| Region | Max HRC | Max Yield (MPa) | PWHT Required | Additional |
|--------|---------|-----------------|---------------|------------|
| 0 | — | — | No | Non-sour; no SSC restrictions |
| 1 | 22 | 760 | No | Standard CS grades acceptable |
| 2 | 22 | 760 | Yes | PWHT mandatory for welds |
| 3 | 22 | 550 | Yes | Reduced yield limit; CRA may be needed |

## Related Documentation

- [NorsokM001MaterialSelection](norsok_m001_material_selection) — Material selection framework
- [CO2CorrosionMaterialSelection](co2_corrosion_material_selection) — CRA selection hierarchy
- [Pipeline Corrosion Integration](pipeline_corrosion_integration) — Process simulation integration
