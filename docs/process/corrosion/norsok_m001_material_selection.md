---
title: "NorsokM001MaterialSelection — NORSOK M-001 Material Selection"
description: "API reference for NorsokM001MaterialSelection — material grade recommendation for pipelines and process piping per NORSOK M-001, NACE MR0175, and ISO 15156. Covers sweet/sour classification, CRA selection, chloride SCC, and corrosion allowance."
---

# NorsokM001MaterialSelection

**Package:** `neqsim.process.corrosion`

**Standards:** NORSOK M-001, NACE MR0175 / ISO 15156

Material selection helper that recommends material grades for pipelines and process piping based on the corrosion environment. Integrates with `NorsokM506CorrosionRate` for CO2 corrosion rate input.

## Evaluation Steps

The `evaluate()` method runs:

1. **Sour service classification** — per NACE MR0175 / ISO 15156, based on H2S partial pressure
2. **Service category** — sweet / sour / chloride / dry, considering free water
3. **Chloride SCC risk** — pitting and stress corrosion cracking risk for stainless steels
4. **Material selection** — recommended grade and alternatives based on all inputs
5. **Corrosion allowance** — per NORSOK M-001 guidelines

## Service Categories

| Category | Conditions |
|----------|------------|
| Dry service (no free water) | No free water present |
| Non-corrosive service | No CO2 or H2S with free water |
| Sweet service (CO2 only) | CO2 present, H2S < 0.003 bar |
| Sweet + chloride service | CO2 + chlorides, H2S < 0.003 bar |
| Sour service | H2S >= 0.003 bar |
| Sour + chloride service | H2S >= 0.003 bar + chlorides |

## Sour Classification (NACE MR0175)

| Classification | H2S Partial Pressure |
|----------------|----------------------|
| Non-sour | < 0.003 bar (0.3 kPa) |
| Mild sour (SSC Region 0) | 0.003 – 0.01 bar |
| Moderate sour (SSC Region 1) | 0.01 – 0.1 bar |
| Severe sour (SSC Region 2/3) | > 0.1 bar |

## Material Selection Logic

### Sweet Service (CO2 Only)

| CO2 Rate (mm/yr) | Low Chloride | High Chloride (> 1000 mg/L) |
|-------------------|--------------|------------------------------|
| < 0.1 | Carbon steel (CS) | CS |
| 0.1 – 0.3 | CS with increased CA | CS with CA |
| 0.3 – 1.0 | 13Cr martensitic SS | 22Cr Duplex SS |
| > 1.0 | 22Cr Duplex SS | 25Cr Super Duplex SS |

### Sour Service

| Severity | Low Chloride | High Chloride |
|----------|--------------|---------------|
| Mild/moderate, low rate | CS (HIC/SSC tested per ISO 15156-2) | 22Cr Duplex SS |
| Mild/moderate, high rate | Super 13Cr (sour qualified) | 22Cr Duplex SS |
| Severe | 22Cr Duplex SS | 25Cr Super Duplex / Alloy C-276 |

## Chloride SCC Risk

| Risk Level | Conditions |
|------------|------------|
| Low | Cl < 50 mg/L or T < 60 °C |
| Medium | 50–1000 mg/L Cl at 60–100 °C |
| High | > 1000 mg/L Cl at T > 80 °C |
| Very High | > 50,000 mg/L at elevated T |

## Corrosion Allowance Rules

| Material | CA Rule |
|----------|---------|
| Carbon steel | max(1.0 mm, rate * design_life), capped at 6.0 mm |
| Carbon steel (sour) | Additional 1.0 mm for localized attack |
| CRA (13Cr, duplex, etc.) | 0 mm — inherently resistant |

CA > 6.0 mm triggers a note recommending CRA for lifecycle cost benefit.

## Constructor

```java
NorsokM001MaterialSelection selector = new NorsokM001MaterialSelection();
```

## Input Setters

| Method | Default | Description |
|--------|---------|-------------|
| `setCO2CorrosionRateMmyr(double)` | 0.0 | CO2 corrosion rate from M-506 or measured (mm/yr) |
| `setH2SPartialPressureBar(double)` | 0.0 | H2S partial pressure (bar) |
| `setDesignTemperatureC(double)` | 60.0 | Design temperature (°C) |
| `setMaxDesignTemperatureC(double)` | 100.0 | Max design temperature (°C) |
| `setChlorideConcentrationMgL(double)` | 0.0 | Chloride concentration (mg/L) |
| `setDesignLifeYears(double)` | 25.0 | Design life (years) |
| `setAqueousPH(double)` | 4.5 | pH of aqueous phase |
| `setCO2PartialPressureBar(double)` | 0.0 | CO2 partial pressure (bar) |
| `setFreeWaterPresent(boolean)` | true | Free water presence |

## Output Getters

Call `evaluate()` before accessing results.

| Method | Type | Description |
|--------|------|-------------|
| `getServiceCategory()` | String | Service classification |
| `getRecommendedMaterial()` | String | Recommended material grade with UNS number |
| `getAlternativeMaterials()` | List&lt;String&gt; | Alternative material options |
| `getRecommendedCorrosionAllowanceMm()` | double | Corrosion allowance (mm) |
| `getMaterialMaxTemperatureC()` | double | Max temperature for recommended material (°C) |
| `getSourClassification()` | String | NACE MR0175 sour classification |
| `getChlorideSCCRisk()` | String | SCC risk level |
| `getNotes()` | List&lt;String&gt; | Warnings and notes |

## JSON / Map Output

```java
String json = selector.toJson();
Map<String, Object> map = selector.toMap();
```

## Java Example

```java
import neqsim.process.corrosion.NorsokM506CorrosionRate;
import neqsim.process.corrosion.NorsokM001MaterialSelection;

// Step 1: Calculate corrosion rate
NorsokM506CorrosionRate model = new NorsokM506CorrosionRate();
model.setTemperatureCelsius(80.0);
model.setTotalPressureBara(50.0);
model.setCO2MoleFraction(0.03);
model.setH2SMoleFraction(0.001);
model.setFlowVelocityMs(5.0);
model.setPipeDiameterM(0.254);
model.calculate();

// Step 2: Select material
NorsokM001MaterialSelection selector = new NorsokM001MaterialSelection();
selector.setCO2CorrosionRateMmyr(model.getCorrectedCorrosionRate());
selector.setH2SPartialPressureBar(model.getH2SPartialPressureBar());
selector.setCO2PartialPressureBar(model.getCO2PartialPressureBar());
selector.setDesignTemperatureC(80.0);
selector.setMaxDesignTemperatureC(80.0);
selector.setChlorideConcentrationMgL(5000);
selector.setDesignLifeYears(25);
selector.evaluate();

System.out.println("Material: " + selector.getRecommendedMaterial());
System.out.println("Service: " + selector.getServiceCategory());
System.out.println("Sour: " + selector.getSourClassification());
System.out.println("SCC risk: " + selector.getChlorideSCCRisk());
System.out.println("CA: " + selector.getRecommendedCorrosionAllowanceMm() + " mm");

for (String note : selector.getNotes()) {
    System.out.println("  - " + note);
}
```

## Python Example

```python
from neqsim import jneqsim

NorsokM506 = jneqsim.process.corrosion.NorsokM506CorrosionRate
NorsokM001 = jneqsim.process.corrosion.NorsokM001MaterialSelection

# Calculate corrosion rate
model = NorsokM506()
model.setTemperatureCelsius(80.0)
model.setTotalPressureBara(50.0)
model.setCO2MoleFraction(0.03)
model.setH2SMoleFraction(0.001)
model.setFlowVelocityMs(5.0)
model.setPipeDiameterM(0.254)
model.calculate()

# Select material
selector = NorsokM001()
selector.setCO2CorrosionRateMmyr(model.getCorrectedCorrosionRate())
selector.setH2SPartialPressureBar(model.getH2SPartialPressureBar())
selector.setDesignTemperatureC(80.0)
selector.setChlorideConcentrationMgL(5000)
selector.setDesignLifeYears(25)
selector.evaluate()

print(f"Material: {selector.getRecommendedMaterial()}")
print(f"Service: {selector.getServiceCategory()}")
print(f"CA: {selector.getRecommendedCorrosionAllowanceMm():.1f} mm")
```

## Related

- [NorsokM506CorrosionRate](norsok_m506_corrosion_rate) — CO2 corrosion rate input
- [Pipeline Corrosion Integration](pipeline_corrosion_integration) — Automated analysis from process simulation
- [Corrosion Module Overview](index) — Module overview and quick start
