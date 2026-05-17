---
title: "NorsokM506CorrosionRate — NORSOK M-506 CO2 Corrosion Rate Model"
description: "API reference for NorsokM506CorrosionRate — CO2 corrosion rate prediction for carbon steel pipelines per NORSOK M-506 (2005/2017). Covers fugacity, pH, baseline rate, correction factors, parameter sweeps, and JSON reporting."
---

# NorsokM506CorrosionRate

**Package:** `neqsim.process.corrosion`

**Standard:** NORSOK M-506 (2005/2017) — "CO2 corrosion rate calculation model"

CO2 corrosion rate prediction for carbon steel pipelines and process piping in CO2-containing environments with free water. Based on the de Waard-Milliams-Lotz equations with NORSOK-specific corrections.

## Calculation Steps

The `calculate()` method runs the following in sequence:

1. **CO2 fugacity** — simplified Peng-Robinson correction on CO2 partial pressure
2. **Equilibrium pH** — in-situ pH of CO2-saturated water considering temperature, fugacity, bicarbonate, and ionic strength
3. **Baseline corrosion rate** — de Waard-Milliams equation (different regimes for T below/above 20 °C)
4. **pH correction factor** ($F_{pH,T}$) — asymmetric formula per NORSOK M-506
5. **Scaling temperature** ($T_{scale}$) — protective FeCO3 film formation threshold
6. **Scale correction factor** ($F_{scale}$) — reduction when T > $T_{scale}$
7. **Wall shear stress & flow correction** — from Reynolds number and Moody friction
8. **Glycol/MEG correction** — reduced water activity
9. **Inhibitor efficiency** — applied as $(1 - \eta)$ multiplier
10. **Final corrected rate** — product of all factors

$$CR_{corrected} = CR_{baseline} \times F_{pH,T} \times F_{scale} \times F_{flow} \times F_{glycol} \times (1 - \eta_{inhibitor})$$

## Applicable Range

Per NORSOK M-506:

| Parameter | Valid Range |
|-----------|-------------|
| Temperature | 5 – 150 °C |
| CO2 partial pressure | up to 10 bar |
| pH | 3.5 – 6.5 |
| Total pressure | up to 1000 bar |
| Material | Carbon steel only |

Use `checkApplicableRange()` to verify inputs are within range.

## Constructors

```java
// Default parameters
NorsokM506CorrosionRate model = new NorsokM506CorrosionRate();

// With initial conditions
NorsokM506CorrosionRate model = new NorsokM506CorrosionRate(60.0, 100.0, 0.02);
//   temperatureC, totalPressureBara, co2MoleFraction
```

## Input Setters

| Method | Default | Description |
|--------|---------|-------------|
| `setTemperatureCelsius(double)` | 60.0 | Operating temperature (°C) |
| `setTotalPressureBara(double)` | 100.0 | Total system pressure (bara) |
| `setCO2MoleFraction(double)` | 0.02 | CO2 mole fraction in gas (0–1) |
| `setH2SMoleFraction(double)` | 0.0 | H2S mole fraction for sour check |
| `setActualPH(double)` | -1 | Measured pH (-1 = calculate from equilibrium) |
| `setBicarbonateConcentrationMgL(double)` | 0.0 | HCO3- for pH adjustment (mg/L) |
| `setIonicStrengthMolL(double)` | 0.0 | Ionic strength for activity coefficient |
| `setFlowVelocityMs(double)` | 2.0 | Flow velocity (m/s) |
| `setPipeDiameterM(double)` | 0.254 | Pipe internal diameter (m) |
| `setLiquidDensityKgM3(double)` | 1000.0 | Liquid density (kg/m3) |
| `setLiquidViscosityPas(double)` | 0.001 | Liquid viscosity (Pa.s) |
| `setInhibitorEfficiency(double)` | 0.0 | Inhibitor efficiency (0–1) |
| `setGlycolWeightFraction(double)` | 0.0 | MEG/DEG weight fraction (0–1) |
| `setUsePHCorrection(boolean)` | true | Enable pH correction factor |
| `setUseScaleCorrection(boolean)` | true | Enable scale correction factor |
| `setUseFlowCorrection(boolean)` | true | Enable flow correction factor |

## Output Getters

Call `calculate()` before accessing results.

### Corrosion Rate Results

| Method | Unit | Description |
|--------|------|-------------|
| `getCorrectedCorrosionRate()` | mm/yr | Final corrected corrosion rate |
| `getBaselineCorrosionRate()` | mm/yr | Uncorrected de Waard-Milliams rate |
| `getCorrosionSeverity()` | — | Low / Medium / High / Very High |

### Intermediate Results

| Method | Unit | Description |
|--------|------|-------------|
| `getCO2FugacityBar()` | bar | CO2 fugacity |
| `getCO2FugacityCoefficient()` | — | Fugacity coefficient |
| `getCalculatedPH()` | — | Equilibrium pH of CO2-saturated water |
| `getEffectivePH()` | — | Actual pH if set, otherwise calculated pH |
| `getScalingTemperatureC()` | °C | FeCO3 protective film threshold |
| `getWallShearStressPa()` | Pa | Wall shear stress |
| `getPHCorrectionFactor()` | — | $F_{pH,T}$ |
| `getScaleCorrectionFactor()` | — | $F_{scale}$ |
| `getFlowCorrectionFactor()` | — | $F_{flow}$ |
| `getGlycolCorrectionFactor()` | — | $F_{glycol}$ |

### H2S / Sour Service

| Method | Unit | Description |
|--------|------|-------------|
| `getCO2PartialPressureBar()` | bar | CO2 partial pressure |
| `getH2SPartialPressureBar()` | bar | H2S partial pressure |
| `isSourService()` | boolean | True if H2S pp > 0.003 bar (NACE MR0175) |
| `getSourSeverityClassification()` | — | Non-sour / Mild / Moderate / Severe |

### Corrosion Allowance

| Method | Description |
|--------|-------------|
| `calculateCorrosionAllowance(double designLifeYears)` | Returns CA in mm (min 1.0 mm per NORSOK M-001) |

## Severity Classification

| Severity | Rate (mm/yr) |
|----------|--------------|
| Low | < 0.1 |
| Medium | 0.1 – 0.3 |
| High | 0.3 – 1.0 |
| Very High | > 1.0 |

## Parameter Sweeps

```java
// Temperature sensitivity study
List<Map<String, Object>> tempSweep =
    model.runTemperatureSweep(5.0, 150.0, 30);  // min, max, steps

// Pressure sensitivity study
List<Map<String, Object>> pSweep =
    model.runPressureSweep(10.0, 200.0, 20);
```

Each point in the returned list contains: temperature/pressure, CO2 fugacity, pH, scaling temperature, baseline rate, corrected rate, and severity.

## JSON / Map Output

```java
String json = model.toJson();       // Full JSON report
Map<String, Object> map = model.toMap();  // Map for programmatic access
```

The output includes:
- Input conditions (temperature, pressure, composition, flow)
- Intermediate calculations (fugacity, pH, shear stress)
- Correction factors (pH, scale, flow, glycol)
- Final results (corrected rate, severity, sour classification)
- Range check results

## Java Example

```java
NorsokM506CorrosionRate model = new NorsokM506CorrosionRate();
model.setTemperatureCelsius(80.0);
model.setTotalPressureBara(50.0);
model.setCO2MoleFraction(0.03);
model.setH2SMoleFraction(0.001);
model.setFlowVelocityMs(5.0);
model.setPipeDiameterM(0.254);
model.setLiquidDensityKgM3(1010.0);
model.setLiquidViscosityPas(0.0008);
model.setInhibitorEfficiency(0.80);
model.calculate();

System.out.println("Rate: " + model.getCorrectedCorrosionRate() + " mm/yr");
System.out.println("Severity: " + model.getCorrosionSeverity());
System.out.println("pH: " + model.getCalculatedPH());
System.out.println("Sour: " + model.isSourService());
System.out.println("CA (25yr): " + model.calculateCorrosionAllowance(25.0) + " mm");
```

## Python Example

```python
from neqsim import jneqsim

NorsokM506 = jneqsim.process.corrosion.NorsokM506CorrosionRate

model = NorsokM506()
model.setTemperatureCelsius(80.0)
model.setTotalPressureBara(50.0)
model.setCO2MoleFraction(0.03)
model.setH2SMoleFraction(0.001)
model.setFlowVelocityMs(5.0)
model.setPipeDiameterM(0.254)
model.setInhibitorEfficiency(0.80)
model.calculate()

print(f"Rate: {model.getCorrectedCorrosionRate():.2f} mm/yr")
print(f"Severity: {model.getCorrosionSeverity()}")
print(f"pH: {model.getCalculatedPH():.2f}")
print(f"Sour: {model.isSourService()}")
```

## Related

- [NorsokM001MaterialSelection](norsok_m001_material_selection) — Material selection from corrosion results
- [Pipeline Corrosion Integration](pipeline_corrosion_integration) — Automated analysis from process simulation
- [Flow Assurance Screening Tools](../../pvtsimulation/flowassurance/flow_assurance_screening_tools) — De Waard-Milliams (simpler screening model)
