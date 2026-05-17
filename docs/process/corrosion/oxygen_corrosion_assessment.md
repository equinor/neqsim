---
title: "OxygenCorrosionAssessment — Dissolved Oxygen Corrosion"
description: "API reference for OxygenCorrosionAssessment — dissolved oxygen corrosion and pitting assessment for injection water, utility water, and process systems per NORSOK M-001, DNV-RP-B401, and ISO 21457."
---

# OxygenCorrosionAssessment

**Package:** `neqsim.process.corrosion`

**Standards:** NORSOK M-001 Rev 6, DNV-RP-B401, ISO 21457, NACE SP0499

Evaluates corrosion risk from dissolved oxygen, including pitting potential and general corrosion rate estimation. Provides treatment recommendations for oxygen removal and scavenging.

## System Types

| System Type | O2 Target (ppb) | Rationale |
|-------------|-----------------|-----------|
| `injection_water` | 20 | Prevent downhole corrosion and reservoir souring |
| `closed_loop` | 10 | Minimize corrosion in recirculating systems |
| `seawater` | 50 | Chloride synergy with O2 |
| `produced_water` | 50 | Disposal/reinjection system |

## Corrosion Rate Model

The model estimates general corrosion rate and pitting rate based on dissolved O2, temperature, velocity, and chloride content:

- **Base rate** proportional to O2 concentration (ppb) and temperature
- **Pitting factor** increases with chloride concentration and flow velocity
- **Treatment credit** for deaeration and chemical scavenging

## Quick Start

### Java

```java
import neqsim.process.corrosion.OxygenCorrosionAssessment;

OxygenCorrosionAssessment o2 = new OxygenCorrosionAssessment();
o2.setDissolvedO2Ppb(200.0);
o2.setTemperatureC(40.0);
o2.setChlorideMgL(20000);
o2.setVelocityMS(2.0);
o2.setMaterialType("Carbon steel");
o2.setSystemType("injection_water");
o2.setDeaerationApplied(true);
o2.setScavengerApplied(false);
o2.evaluate();

System.out.println("Risk Level: " + o2.getRiskLevel());
System.out.println("Corrosion Rate: " + o2.getCorrosionRateMmYr() + " mm/yr");
System.out.println("Pitting Rate: " + o2.getPittingRateMmYr() + " mm/yr");
System.out.println("Meets O2 Target: " + o2.isMeetsO2Target());
System.out.println("Target O2: " + o2.getTargetO2Ppb() + " ppb");
System.out.println("Treatment: " + o2.getRecommendedTreatment());
```

### Python

```python
from neqsim import jneqsim

O2Corrosion = jneqsim.process.corrosion.OxygenCorrosionAssessment

o2 = O2Corrosion()
o2.setDissolvedO2Ppb(200.0)
o2.setTemperatureC(40.0)
o2.setChlorideMgL(20000)
o2.setSystemType("injection_water")
o2.evaluate()

print(f"Risk: {o2.getRiskLevel()}")
print(f"Rate: {o2.getCorrosionRateMmYr():.3f} mm/yr")
print(f"Treatment: {o2.getRecommendedTreatment()}")
```

## Input Parameters

| Method | Type | Default | Description |
|--------|------|---------|-------------|
| `setDissolvedO2Ppb(double)` | double | 0.0 | Dissolved oxygen concentration (ppb) |
| `setTemperatureC(double)` | double | 25.0 | Temperature (°C) |
| `setChlorideMgL(double)` | double | 0.0 | Chloride concentration (mg/L) |
| `setVelocityMS(double)` | double | 1.0 | Flow velocity (m/s) |
| `setMaterialType(String)` | String | "Carbon steel" | Material type |
| `setScavengerApplied(boolean)` | boolean | false | Chemical O2 scavenger in use |
| `setDeaerationApplied(boolean)` | boolean | false | Vacuum/gas-strip deaeration in use |
| `setSystemType(String)` | String | "injection_water" | System type for O2 target selection |

## Output Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `getRiskLevel()` | String | "Low", "Medium", "High", "Very High" |
| `getCorrosionRateMmYr()` | double | Estimated general corrosion rate (mm/yr) |
| `getPittingRateMmYr()` | double | Estimated pitting rate (mm/yr) |
| `getPittingFactor()` | double | Pitting factor (pitting rate / general rate) |
| `isMeetsO2Target()` | boolean | Whether O2 level meets system target |
| `getTargetO2Ppb()` | double | Target O2 for the system type (ppb) |
| `getRecommendedTreatment()` | String | Recommended treatment method |
| `getNotes()` | List&lt;String&gt; | Assessment notes |
| `toMap()` | Map | All results as a LinkedHashMap |
| `toJson()` | String | Complete JSON report |

## Related Documentation

- [NorsokM001MaterialSelection](norsok_m001_material_selection) — Material selection framework
- [Pipeline Corrosion Integration](pipeline_corrosion_integration) — Process simulation integration
