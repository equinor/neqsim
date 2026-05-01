---
title: "ChlorideSCCAssessment — Chloride Stress Corrosion Cracking"
description: "API reference for ChlorideSCCAssessment — chloride stress corrosion cracking risk evaluation for austenitic and duplex stainless steels per NORSOK M-001, ISO 15156-3, EFC 17, and MTI Publication 15."
---

# ChlorideSCCAssessment

**Package:** `neqsim.process.corrosion`

**Standards:** NORSOK M-001 Rev 6, ISO 15156-3, EFC 17, MTI Publication 15

Evaluates whether an alloy is susceptible to chloride-induced stress corrosion cracking (Cl-SCC) based on temperature, chloride concentration, and material type. Each alloy family has a defined temperature–chloride envelope beyond which SCC risk is unacceptable.

## Supported Material Types

| Material | Max Temperature Basis | Key Limitation |
|----------|----------------------|----------------|
| 304L | Lowest tolerance | Avoid above 50°C with chlorides |
| 316L | Moderate tolerance | O2 presence reduces limit by 25°C |
| 22Cr duplex | Good resistance | Max ~120–200°C depending on Cl |
| 25Cr super duplex | Very good resistance | Max ~150–250°C depending on Cl |
| Nickel alloy (625/C-276) | Excellent resistance | Max 300–350°C |

## Risk Classification

Risk is based on the **temperature margin** (operating temperature minus maximum allowable temperature):

| Margin | Risk Level |
|--------|-----------|
| < −30°C | Low |
| −30°C to −10°C | Medium |
| −10°C to 0°C | High |
| ≥ 0°C | Very High |

## Quick Start

### Java

```java
import neqsim.process.corrosion.ChlorideSCCAssessment;

ChlorideSCCAssessment scc = new ChlorideSCCAssessment();
scc.setMaterialType("316L");
scc.setTemperatureC(80.0);
scc.setChlorideConcentrationMgL(50000);
scc.setOxygenPresent(true);
scc.setStressRatio(0.8);
scc.evaluate();

System.out.println("SCC Acceptable: " + scc.isSCCAcceptable());
System.out.println("Risk Level: " + scc.getRiskLevel());
System.out.println("Max Allowable T: " + scc.getMaxAllowableTemperatureC() + " °C");
System.out.println("Temperature Margin: " + scc.getTemperatureMarginC() + " °C");
System.out.println("Recommended Upgrade: " + scc.getRecommendedUpgrade());
```

### Python

```python
from neqsim import jneqsim

ChlorideSCC = jneqsim.process.corrosion.ChlorideSCCAssessment

scc = ChlorideSCC()
scc.setMaterialType("316L")
scc.setTemperatureC(80.0)
scc.setChlorideConcentrationMgL(50000)
scc.setOxygenPresent(True)
scc.evaluate()

print(f"Acceptable: {scc.isSCCAcceptable()}")
print(f"Risk: {scc.getRiskLevel()}")
print(f"Upgrade to: {scc.getRecommendedUpgrade()}")
```

## Input Parameters

| Method | Type | Default | Description |
|--------|------|---------|-------------|
| `setTemperatureC(double)` | double | 60.0 | Operating temperature (°C) |
| `setChlorideConcentrationMgL(double)` | double | 0.0 | Chloride concentration (mg/L) |
| `setMaterialType(String)` | String | "316L" | Alloy type: "304L", "316L", "22Cr", "25Cr", "Nickel alloy" |
| `setStressRatio(double)` | double | 0.8 | Applied stress as fraction of yield (0–1) |
| `setOxygenPresent(boolean)` | boolean | false | Dissolved O2 present (> 10 ppb) — reduces limit by 25°C for austenitic |
| `setAqueousPH(double)` | double | 7.0 | pH of aqueous phase |

## Output Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `isSCCAcceptable()` | boolean | Whether SCC risk is acceptable |
| `getRiskLevel()` | String | "Low", "Medium", "High", "Very High" |
| `getMaxAllowableTemperatureC()` | double | Maximum allowable temperature for this material + chloride level |
| `getMaxAllowableChlorideMgL()` | double | Maximum allowable chloride for this material + temperature |
| `getTemperatureMarginC()` | double | Operating T minus max allowable T (negative = margin available) |
| `getRecommendedUpgrade()` | String | Suggested material upgrade if risk is unacceptable |
| `getNotes()` | List&lt;String&gt; | Assessment notes and warnings |
| `toMap()` | Map | All results as a LinkedHashMap |
| `toJson()` | String | Complete JSON report |

## Typical Temperature–Chloride Limits

These are approximate limits for austenitic stainless steels (316L):

| Chloride (mg/L) | Max T (°C) without O2 | Max T (°C) with O2 |
|------------------|------------------------|---------------------|
| < 50 | 200 | 175 |
| 50–500 | 120 | 95 |
| 500–5,000 | 80 | 55 |
| 5,000–50,000 | 60 | 35 |
| > 50,000 | 40 | 15 |

Duplex and super duplex grades tolerate significantly higher temperatures and chloride levels.

## Related Documentation

- [NorsokM001MaterialSelection](norsok_m001_material_selection) — Full material selection framework
- [CO2CorrosionMaterialSelection](co2_corrosion_material_selection) — CRA selection hierarchy
- [SourServiceAssessment](sour_service_assessment) — Sour service evaluation per ISO 15156
