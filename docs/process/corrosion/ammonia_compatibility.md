---
title: "AmmoniaCompatibility — Ammonia Service Material Assessment"
description: "API reference for AmmoniaCompatibility — material compatibility assessment for ammonia service per CGA G-2.1, ASME B31.3, and API 660. Covers SCC of carbon steel in anhydrous NH3, O2 inhibitor requirements, copper alloy restrictions, and temperature limits."
---

# AmmoniaCompatibility

**Package:** `neqsim.process.corrosion`

**Standards:** CGA G-2 / G-2.1, ASME B31.3, IGC Code, 49 CFR 173.315, API 660

Evaluates material suitability for anhydrous ammonia, aqueous ammonia, and ammonia-as-hydrogen-carrier service. Key concern is stress corrosion cracking (SCC) of carbon steel in anhydrous NH3 when the oxygen inhibitor level is insufficient.

## Key Failure Mechanisms

| Mechanism | Material | Conditions |
|-----------|----------|------------|
| **NH3 SCC** | Carbon steel | Anhydrous NH3 without adequate O2 inhibitor (< 0.1 wt%) |
| **Dissolution/SCC** | Copper alloys | Any ammonia concentration — copper alloys are incompatible |
| **Nitridation** | Carbon steel, low-alloy | Temperature > 300°C |
| **Caustic embrittlement** | Carbon steel | Concentrated aqueous NH3 at high temperature |

## O2 Inhibitor Requirement

For carbon steel in anhydrous ammonia service, a small amount of dissolved oxygen (0.1–0.2 wt%) inhibits SCC. The class checks whether the applied inhibitor level meets the minimum requirement:

| Condition | Required O2 (wt%) |
|-----------|--------------------|
| Anhydrous NH3, carbon steel | ≥ 0.1 |
| Anhydrous NH3, high stress (> 0.8 yield) | ≥ 0.2 |
| Aqueous NH3 or stainless steel | Not required |

## Quick Start

### Java

```java
import neqsim.process.corrosion.AmmoniaCompatibility;

AmmoniaCompatibility nh3 = new AmmoniaCompatibility();
nh3.setTemperatureC(25.0);
nh3.setPressureBara(10.0);
nh3.setNh3ConcentrationWtPct(99.5);
nh3.setAnhydrous(true);
nh3.setMaterialType("Carbon steel");
nh3.setO2InhibitorWtPct(0.15);
nh3.setStressRatio(0.7);
nh3.setHardnessHRC(20.0);
nh3.setPwhtApplied(true);
nh3.evaluate();

System.out.println("Compatible: " + nh3.isCompatible());
System.out.println("Risk Level: " + nh3.getRiskLevel());
System.out.println("Mechanism: " + nh3.getPrimaryMechanism());
System.out.println("O2 Adequate: " + nh3.isO2InhibitorAdequate());
System.out.println("Max Temp: " + nh3.getMaxAllowableTempC() + " °C");
System.out.println("Recommended Material: " + nh3.getRecommendedMaterial());
```

### Python

```python
from neqsim import jneqsim

AmmoniaCompat = jneqsim.process.corrosion.AmmoniaCompatibility

nh3 = AmmoniaCompat()
nh3.setTemperatureC(25.0)
nh3.setPressureBara(10.0)
nh3.setNh3ConcentrationWtPct(99.5)
nh3.setAnhydrous(True)
nh3.setMaterialType("Carbon steel")
nh3.setO2InhibitorWtPct(0.15)
nh3.evaluate()

print(f"Compatible: {nh3.isCompatible()}")
print(f"Risk: {nh3.getRiskLevel()}")
print(f"Mechanism: {nh3.getPrimaryMechanism()}")
```

## Input Parameters

| Method | Type | Default | Description |
|--------|------|---------|-------------|
| `setTemperatureC(double)` | double | 25.0 | Temperature (°C) |
| `setPressureBara(double)` | double | 10.0 | Pressure (bara) |
| `setNh3ConcentrationWtPct(double)` | double | 99.5 | NH3 concentration (wt%) |
| `setAnhydrous(boolean)` | boolean | true | Anhydrous (true) or aqueous (false) |
| `setWaterContentWtPct(double)` | double | 0.0 | Water content (wt%) |
| `setO2InhibitorWtPct(double)` | double | 0.0 | Dissolved O2 inhibitor level (wt%) |
| `setMaterialType(String)` | String | "Carbon steel" | Material: "Carbon steel", "316L", "Copper alloy", etc. |
| `setStressRatio(double)` | double | 0.8 | Applied stress / yield stress (0–1) |
| `setPwhtApplied(boolean)` | boolean | false | Post-weld heat treatment applied |
| `setHardnessHRC(double)` | double | 22.0 | Maximum hardness (HRC) |

## Output Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `isCompatible()` | boolean | Whether the material is compatible |
| `getRiskLevel()` | String | "Low", "Medium", "High", "Very High" |
| `getPrimaryMechanism()` | String | Dominant failure mechanism (e.g., "NH3 SCC", "Copper dissolution") |
| `isO2InhibitorAdequate()` | boolean | Whether O2 inhibitor meets minimum |
| `getRequiredO2InhibitorWtPct()` | double | Required O2 level for carbon steel (wt%) |
| `getMaxAllowableTempC()` | double | Maximum allowable temperature (°C) |
| `getMaxAllowableHRC()` | double | Maximum allowable hardness (HRC) |
| `getRecommendedMaterial()` | String | Recommended material for the conditions |
| `getNotes()` | List&lt;String&gt; | Assessment notes |
| `toMap()` | Map | All results as a LinkedHashMap |
| `toJson()` | String | Complete JSON report |

## Ammonia as Hydrogen Carrier

For ammonia cracking / hydrogen production applications, consider:

- Cracking temperatures (400–900°C) require heat-resistant alloys
- Downstream H2-rich streams need [HydrogenMaterialAssessment](hydrogen_material_assessment) evaluation
- Residual NH3 in product H2 affects downstream material selection

## Related Documentation

- [SourServiceAssessment](sour_service_assessment) — H2S/sour service assessment
- [DensePhaseCO2Corrosion](dense_phase_co2_corrosion) — CCS pipeline corrosion
