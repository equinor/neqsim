---
title: "Oil Quality Standards"
description: "Comprehensive guide to oil quality standards in NeqSim including ASTM D86 distillation, D445 viscosity, D4052 density/API gravity, D4294 sulfur, D2500 cloud point, D97 pour point, TVP (true vapor pressure), D4737 cetane index, and BS&W."
---

# Oil Quality Standards

NeqSim provides thermodynamics-based implementations of key ASTM standards used for crude oil and petroleum product quality characterization.

## Available Standards

| Standard | Class | Property | Key Output |
|----------|-------|----------|------------|
| ASTM D86 | `Standard_ASTM_D86` | Atmospheric distillation | IBP, T10-T90, FBP, VABP/MeABP, Watson K |
| ASTM D445 | `Standard_ASTM_D445` | Kinematic viscosity | KV40, KV100, Viscosity Index |
| ASTM D4052 | `Standard_ASTM_D4052` | Density / API gravity | Density, SG, API, classification |
| ASTM D4294 | `Standard_ASTM_D4294` | Total sulfur content | Sulfur wt%, sweet/sour class |
| ASTM D6377 | `Standard_ASTM_D6377` | Reid vapor pressure (RVP) | RVP at 37.8 &deg;C |
| TVP (API MPMS 19) | `Standard_TVP` | True vapor pressure | Bubble-point pressure at any reference temperature |
| ASTM D4737 | `Standard_ASTM_D4737` | Calculated cetane index | CCI (4-variable) + D976 (2-variable) |
| ASTM D611 | `Standard_ASTM_D611` | Aniline point (estimate) | Estimated aniline point from Watson K + MeABP |
| ASTM D1322 | `Standard_ASTM_D1322` | Smoke point (estimate) | Estimated jet/kerosene smoke point |
| EN 116 | `Standard_EN116` | Cold filter plugging point (estimate) | CFPP from cloud point + offset |
| ASTM D3230 | `Standard_ASTM_D3230` | Salt content (input-driven) | PTB and mg/kg from water cut + brine salinity |
| ASTM D2500 | `Standard_ASTM_D2500` | Cloud point | Wax appearance temperature |
| ASTM D97 | `Standard_ASTM_D97` | Pour point | Lowest flow temperature |
| BS&W | `Standard_BSW` | Basic sediment & water | Water vol%, on-spec check |

All classes are in `neqsim.standards.oilquality`.

---

## Quick Start

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.standards.oilquality.*;

// Create a characterized crude oil
SystemSrkEos oil = new SystemSrkEos(273.15 + 15.0, 1.01325);
oil.addComponent("methane", 0.01);
oil.addComponent("ethane", 0.02);
oil.addComponent("propane", 0.03);
oil.addTBPfraction("C7", 0.15, 95.0, 0.72);
oil.addTBPfraction("C10", 0.20, 135.0, 0.78);
oil.addTBPfraction("C20", 0.30, 280.0, 0.85);
oil.addTBPfraction("C30", 0.29, 450.0, 0.91);
oil.setMixingRule(2);

// API gravity
Standard_ASTM_D4052 apiStd = new Standard_ASTM_D4052(oil);
apiStd.calculate();
double apiGravity = apiStd.getValue("API gravity");
String classification = apiStd.getOilClassification();
System.out.println("API gravity: " + apiGravity + " (" + classification + ")");

// Distillation curve
Standard_ASTM_D86 distStd = new Standard_ASTM_D86(oil);
distStd.calculate();
double ibp = distStd.getValue("IBP", "C");
double t50 = distStd.getValue("T50", "C");
double fbp = distStd.getValue("FBP", "C");
System.out.println("IBP=" + ibp + ", T50=" + t50 + ", FBP=" + fbp + " C");
```

---

## ASTM D86 - Distillation Curve

Determines the boiling range distribution of petroleum products at atmospheric pressure.

### Parameters

Available via `getValue(param)` (or `getValue(param, unit)` for temperatures):

| Parameter | Description |
|-----------|-------------|
| `IBP` | Initial boiling point |
| `T5` - `T95` (any `Txx`) | Temperature at xx% distilled |
| `FBP` | Final boiling point |
| `VABP` | Volume average boiling point (from the curve) |
| `MABP` | Molal average boiling point |
| `WABP` | Weight average boiling point |
| `CABP` | Cubic average boiling point |
| `MeABP` | Mean average boiling point = (MABP + CABP) / 2 |
| `slope` | D86 slope = (T90 - T10) / 80 |
| `WatsonK` (or `UOPK`) | Watson (UOP) characterization factor |
| `recovery`, `loss`, `residue` | Recovery/loss/residue volume fractions |

> Average boiling points, the Watson factor, the slope, `residue` and `loss`
> are dimensionless or fixed-basis values and are returned unconverted by
> `getValue(param, unit)`.

### Temperature Units

Supported via `getValue(param, unit)`: `"C"`, `"K"`, `"F"`, `"R"`

### Reporting Basis

The recovered fraction can be reported on three bases via `setBasis(...)`:

| Basis | Meaning |
|-------|---------|
| `D86Basis.MOLAR` (default) | Recovered fraction = molar vapor fraction |
| `D86Basis.LIQUID_VOLUME` | Recovered fraction = distilled liquid-volume fraction |
| `D86Basis.TBP_CONVERTED` | Simulated TBP curve converted to D86 (Riazi&ndash;Daubert) |

The default (`MOLAR`, 760 mmHg) reproduces the legacy behaviour, and
`getDistillationCurve()` always returns `[vol%, temperature_C]`.

### Example

```java
Standard_ASTM_D86 d86 = new Standard_ASTM_D86(oil);
d86.calculate();

// Individual points
double ibp = d86.getValue("IBP", "C");
double t50 = d86.getValue("T50", "C");
double fbp = d86.getValue("FBP", "C");

// Characterization properties
double watsonK = d86.getWatsonK();         // UOP K factor
double meabp = d86.getMeABP();             // mean average boiling point (C)
double slope = d86.getSlope();             // D86 slope (C / vol%)
double sg = d86.getSpecificGravity();      // 60/60 F specific gravity

// Recovery / loss / residue (sum to 100%)
double recovered = d86.getPercentRecovered();
double loss = d86.getPercentLoss();
double residue = d86.getPercentResidue();

// Full distillation curve as double[N][2] (vol%, temperature_C)
double[][] curve = d86.getDistillationCurve();
for (double[] point : curve) {
    System.out.printf("%.1f%% -> %.1f C%n", point[0], point[1]);
}

// Compare simulated TBP curve to the converted D86 curve
double[][] tbpCurve = d86.getTBPCurve();   // [vol%, temperature_C]
double[][] d86Curve = d86.getD86Curve();   // Riazi-Daubert converted

// Barometric (Sydney Young) correction and product spec limits
d86.setBarometricPressure(740.0, "mmHg");
d86.setSpecLimit("T90", 360.0);            // C
boolean onSpec = d86.isOnSpec();
```

### How It Works

1. **IBP** - Bubble point temperature flash at atmospheric pressure
2. **Intermediate points** (T5-T95) - Pressure-Vapor-Fraction (PVF) flash at each
   target recovered fraction: pressure is held fixed and temperature is solved,
   producing a rising boiling curve
3. **FBP** - Dew point temperature flash
4. **Average boiling points** (MABP, WABP, CABP, MeABP) are computed from the
   component normal boiling points; VABP and the slope come from the curve
5. **Watson K** = &#8731;(1.8 &middot; MeABP_K) / SG, with SG from an ideal-mixture
   density

---

## ASTM D445 - Kinematic Viscosity

Determines kinematic viscosity at 40 °C and 100 °C, and calculates the Viscosity Index (VI) per ASTM D2270.

### Parameters

| Parameter | Description | Unit |
|-----------|-------------|------|
| `KV40` | Kinematic viscosity at 40 °C | mm2/s (cSt) |
| `KV100` | Kinematic viscosity at 100 °C | mm2/s (cSt) |
| `dynamicViscosity40C` | Dynamic viscosity at 40 °C | mPa.s (cP) |
| `dynamicViscosity100C` | Dynamic viscosity at 100 °C | mPa.s (cP) |
| `density40C` | Density at 40 °C | kg/m3 |
| `density100C` | Density at 100 °C | kg/m3 |
| `VI` | Viscosity Index | dimensionless |

### Example

```java
Standard_ASTM_D445 d445 = new Standard_ASTM_D445(oil);
d445.calculate();

double kv40 = d445.getValue("KV40");     // cSt at 40C
double kv100 = d445.getValue("KV100");   // cSt at 100C
double vi = d445.getValue("VI");         // Viscosity Index

System.out.printf("KV40=%.2f cSt, KV100=%.2f cSt, VI=%.0f%n", kv40, kv100, vi);
```

### Viscosity Index

The VI indicates how much viscosity changes with temperature:

| VI Range | Interpretation |
|----------|----------------|
| < 0 | Very temperature-sensitive |
| 0-40 | Low VI |
| 40-80 | Medium VI |
| 80-120 | High VI |
| > 120 | Very High VI |

---

## ASTM D4052 - Density and API Gravity

Determines density at 15.556 °C (60 °F), specific gravity, API gravity, and classifies the oil.

### Parameters

| Parameter | Description | Unit |
|-----------|-------------|------|
| `density` | Density at 60 °F | kg/m3 |
| `specificGravity` | SG relative to water at 60 °F | dimensionless |
| `API gravity` | API gravity | °API |

### Density Unit Conversion

```java
d4052.getValue("density");          // kg/m3 (default)
d4052.getValue("density", "g/cm3"); // g/cm3
d4052.getValue("density", "lb/ft3");// lb/ft3
```

### Oil Classification

```java
String classification = d4052.getOilClassification();
```

| API Gravity | Classification |
|-------------|----------------|
| > 31.1 | Light |
| 22.3 - 31.1 | Medium |
| 10.0 - 22.3 | Heavy |
| < 10.0 | Extra-Heavy |

### Spec Checking

```java
d4052.setMinAPIGravity(20.0);
d4052.setMaxAPIGravity(45.0);
boolean onSpec = d4052.isOnSpec();
```

---

## ASTM D4294 - Total Sulfur Content

Calculates total sulfur from sulfur-bearing components in the fluid (H2S, mercaptans, COS, CS2, SO2).

### Parameters

| Parameter | Description | Unit |
|-----------|-------------|------|
| `sulfurContent` | Total sulfur | wt% |
| `sulfurContent` (unit `"ppmw"`) | Total sulfur | ppmw |

### Sulfur Classification

```java
String classification = d4294.getSulfurClassification();
```

| Sulfur wt% | Classification |
|------------|----------------|
| < 0.5 | Sweet |
| 0.5 - 1.0 | Medium Sour |
| >= 1.0 | Sour |

### Example

```java
// Create oil with H2S
SystemSrkEos sourOil = new SystemSrkEos(273.15 + 15.0, 1.01325);
sourOil.addComponent("methane", 0.10);
sourOil.addComponent("H2S", 0.02);
sourOil.addTBPfraction("C10", 0.50, 135.0, 0.78);
sourOil.addTBPfraction("C20", 0.38, 280.0, 0.85);
sourOil.setMixingRule(2);

Standard_ASTM_D4294 d4294 = new Standard_ASTM_D4294(sourOil);
d4294.calculate();

double sulfurWtPct = d4294.getValue("sulfurContent");
double sulfurPpmw = d4294.getValue("sulfurContent", "ppmw");
String sweetSour = d4294.getSulfurClassification();

System.out.printf("Sulfur: %.3f wt%% (%.0f ppmw) - %s%n",
    sulfurWtPct, sulfurPpmw, sweetSour);
```

---

## TVP - True Vapor Pressure (API MPMS Chapter 19)

The **True Vapor Pressure** is the equilibrium (bubble-point) pressure of the bulk liquid at a specified reference temperature. Unlike the Reid Vapor Pressure (`Standard_ASTM_D6377`), which is fixed at 37.8 &deg;C and a 4:1 vapor-to-liquid ratio, the TVP is the thermodynamic bubble-point pressure and can be reported at any storage or transport temperature.

TVP is the controlling parameter for storage-tank breathing losses, low-pressure separator and stabiliser design, crude custody-transfer vapour-pressure limits, and pressure/vacuum relief set points on atmospheric and low-pressure tanks.

### Parameters

| Parameter | Description | Unit |
|-----------|-------------|------|
| `TVP` | True vapor pressure at the reference temperature | bara (convertible) |
| `referenceTemperature` | Reference temperature | as configured |

The `TVP` value can be returned in any pressure unit supported by `PressureUnit` (`bara`, `barg`, `psia`, `psig`, `kPa`, `MPa`, `atm`, ...).

### How It Works

`calculate()` clones the fluid, sets it to the reference temperature (default 37.8 &deg;C / 100 &deg;F), and performs a bubble-point pressure flash. The resulting pressure is the TVP. No empirical correlation is used &ndash; the value comes directly from the equation of state.

### Example

```java
Standard_TVP tvp = new Standard_TVP(oil);
tvp.setReferenceTemperature(50.0, "C");   // any storage temperature
tvp.calculate();

double tvpBara = tvp.getValue("TVP", "bara");
double tvpPsia = tvp.getValue("TVP", "psia");
System.out.printf("TVP at 50 C: %.3f bara (%.2f psia)%n", tvpBara, tvpPsia);

// Optional sales-specification check (e.g. TVP <= 1.01325 bara at max storage temp)
tvp.setMaxTvpSpec(1.01325, "bara");
System.out.println("On spec: " + tvp.isOnSpec());
```

The TVP increases with reference temperature, so always evaluate it at the maximum expected storage or transport temperature for conservative design.

---

## ASTM D4737 - Calculated Cetane Index

The **calculated cetane index** estimates the cetane number of a middle-distillate fuel (kerosene, jet, diesel) from its density and distillation recovery temperatures. It is used as a sales-specification surrogate for the engine-measured cetane number (ASTM D613) when an engine test is not available.

Two correlations are evaluated:

- **ASTM D4737** (primary, four-variable) &ndash; uses the 10 %, 50 % and 90 % recovered temperatures and the density at 15 &deg;C.
- **ASTM D976** (two-variable) &ndash; uses the 50 % recovered temperature and the density at 15 &deg;C, provided as a cross-check.

The required inputs are obtained internally from `Standard_ASTM_D86` (distillation curve) and `Standard_ASTM_D4052` (density), so only the fluid is required.

### Parameters

| Parameter | Description | Unit |
|-----------|-------------|------|
| `cetaneIndex` / `CCI` / `cetaneIndexD4737` | Calculated cetane index (ASTM D4737, four-variable) | &ndash; |
| `cetaneIndexD976` | Calculated cetane index (ASTM D976, two-variable) | &ndash; |
| `T10`, `T50`, `T90` | Recovered temperatures (passthrough from D86) | C |
| `density` / `density15C` | Density at 15 &deg;C | kg/m3 |

### Formula (ASTM D4737)

$$
\mathrm{CCI} = 45.2 + 0.0892\,T_{10N} + (0.131 + 0.901\,B)\,T_{50N}
            + (0.0523 - 0.420\,B)\,T_{90N}
            + 0.00049\,(T_{10N}^2 - T_{90N}^2) + 107\,B + 60\,B^2
$$

where $T_{10N} = T_{10} - 215$, $T_{50N} = T_{50} - 260$, $T_{90N} = T_{90} - 310$ (temperatures in &deg;C), $B = e^{-3.5\,(D - 0.85)} - 1$, and $D$ is the density at 15 &deg;C in g/mL.

### Example

```java
Standard_ASTM_D4737 cetane = new Standard_ASTM_D4737(dieselFluid);
cetane.calculate();

double cci = cetane.getValue("cetaneIndex");      // ASTM D4737
double cciD976 = cetane.getValue("cetaneIndexD976"); // ASTM D976 cross-check
System.out.printf("Cetane index D4737=%.1f, D976=%.1f%n", cci, cciD976);

// Optional minimum cetane specification (e.g. 51 for EN 590 automotive diesel)
cetane.setMinCetaneSpec(51.0);
System.out.println("On spec: " + cetane.isOnSpec());
```

---

## ASTM D611 - Aniline Point (estimate)

The **aniline point** is the lowest temperature at which equal volumes of the oil and aniline are completely miscible. It is an inverse measure of aromatic content: paraffinic stocks have high aniline points, aromatic stocks have low aniline points. It feeds the Diesel Index and supports jet-fuel and solvent quality control.

> **Estimate, not a measurement.** The aniline point is governed by paraffinicity and boiling range, so it is estimated from the Watson (UOP) characterization factor and the mean average boiling point (MeABP), both obtained internally from `Standard_ASTM_D86`. The default coefficients reproduce typical middle-distillate behaviour and are configurable for calibration against measured ASTM D611 / API Procedure 2B8.1 (Walsh&ndash;Mortimer) data.

### Parameters

| Parameter | Description | Unit |
|-----------|-------------|------|
| `anilinePoint` / `AP` | Estimated aniline point | C (K, F, R via `getValue(name, unit)`) |
| `MeABP` | Mean average boiling point (passthrough from D86) | C |
| `watsonK` | Watson characterization factor | &ndash; |

### Formula

$$
\mathrm{AP}[^{\circ}\mathrm{C}] = c_0 + c_1\,(K_w - 12.0) + c_2\,(\mathrm{MeABP}[^{\circ}\mathrm{C}] - 190.0)
$$

with defaults $c_0 = 60.0$, $c_1 = 35.0$, $c_2 = 0.083$.

### Example

```java
Standard_ASTM_D611 aniline = new Standard_ASTM_D611(dieselFluid);
aniline.calculate();
double anilinePointC = aniline.getValue("anilinePoint", "C");

// Optional minimum aniline-point specification
aniline.setMinAnilineSpec(70.0, "C");
System.out.println("On spec: " + aniline.isOnSpec());
```

---

## ASTM D1322 - Smoke Point (estimate)

The **smoke point** is the maximum flame height in millimetres at which a kerosene / jet fuel burns without smoking. It controls aviation turbine fuel burning quality (e.g. Jet A-1 requires a smoke point of at least 25 mm). A high smoke point indicates a paraffinic, low-aromatic fuel.

> **Estimate, not a measurement.** The smoke point tracks aromaticity, captured here through the estimated aniline point from `Standard_ASTM_D611`. The default coefficients are configurable for calibration against measured ASTM D1322 data.

### Parameters

| Parameter | Description | Unit |
|-----------|-------------|------|
| `smokePoint` / `SP` | Estimated smoke point | mm |
| `anilinePoint` / `AP` | Aniline point (passthrough from D611) | C |

### Formula

$$
\mathrm{SmokePoint}[\mathrm{mm}] = sp_0 + sp_1\,\mathrm{AP}[^{\circ}\mathrm{C}]
$$

with defaults $sp_0 = 8.5$, $sp_1 = 0.325$.

### Example

```java
Standard_ASTM_D1322 smoke = new Standard_ASTM_D1322(jetFluid);
smoke.calculate();
double smokePointMm = smoke.getValue("smokePoint", "mm");

// Optional minimum smoke-point specification (e.g. 25 mm for Jet A-1)
smoke.setMinSmokeSpec(25.0);
System.out.println("On spec: " + smoke.isOnSpec());
```

---

## EN 116 - Cold Filter Plugging Point (estimate)

The **cold filter plugging point (CFPP)** is the highest temperature at which a given volume of fuel fails to pass through a standardised filtration device when cooled. It characterises the low-temperature operability of diesel and heating oils and is widely used in European specifications.

> **Estimate, not a measurement.** For untreated middle distillates the CFPP closely tracks the cloud point (wax appearance temperature), obtained internally from `Standard_ASTM_D2500`. The default offset is 0 &deg;C (CFPP equal to cloud point); cold-flow additives can depress the CFPP below the cloud point, in which case set the offset from response testing.

### Parameters

| Parameter | Description | Unit |
|-----------|-------------|------|
| `CFPP` | Estimated cold filter plugging point | C (K, F, R via `getValue(name, unit)`) |
| `cloudPoint` / `CP` | Cloud point basis (passthrough from D2500) | C |

### Formula

$$
\mathrm{CFPP}[^{\circ}\mathrm{C}] = \mathrm{cloudPoint}[^{\circ}\mathrm{C}] + \mathrm{offset}
$$

### Example

```java
Standard_EN116 cfpp = new Standard_EN116(dieselFluid);
cfpp.setOffset(-2.0); // optional additive response
cfpp.calculate();
double cfppC = cfpp.getValue("CFPP", "C");

// Optional maximum CFPP specification (e.g. -10 C winter grade)
cfpp.setMaxCfppSpec(-10.0, "C");
System.out.println("On spec: " + cfpp.isOnSpec());
```

> **Note:** the cloud point basis requires an active wax model on the fluid (e.g. `fluid.getWaxModel().addTBPWax()` plus `addSolidComplexPhase("wax")`); without it the cloud point and therefore the CFPP return `NaN`.

---

## ASTM D3230 - Salt Content in Crude Oil (input-driven)

The **salt content** of crude oil is reported as PTB (pounds of NaCl-equivalent per thousand barrels) or as a mass concentration (mg/kg, i.e. ppmw). It is a key desalter performance and corrosion-control parameter. The salt is dissolved in the entrained brine, so it **cannot be predicted from the hydrocarbon equation of state** &ndash; it must be supplied from the produced-water cut and the brine salinity.

> **Input-driven.** Provide the water cut (volume fraction of crude that is water) and the brine salinity (mass of salt per unit volume of brine). The class converts to PTB and mg/kg. If either input is missing, the result is `NaN` and a brine assay is required.

### Parameters

| Parameter | Description | Unit |
|-----------|-------------|------|
| `saltContentPTB` / `saltContent` | Salt content | PTB |
| `saltContentPpmw` / `saltContent` (with `"mg/kg"`) | Salt content | mg/kg |

### Formula

$$
\mathrm{PTB} = w_{\mathrm{water}} \cdot S_{\mathrm{brine}}[\mathrm{kg/m^3}] \cdot 350.51
$$

$$
\mathrm{ppmw} = \frac{w_{\mathrm{water}} \cdot S_{\mathrm{brine}}[\mathrm{kg/m^3}]}{\rho_{\mathrm{crude}}[\mathrm{kg/m^3}]} \cdot 10^6
$$

where the PTB factor $350.51 = 158.987\ \mathrm{m^3/1000\,bbl} \cdot 2.20462\ \mathrm{lb/kg}$, and the crude density is obtained internally from `Standard_ASTM_D4052`.

### Example

```java
Standard_ASTM_D3230 salt = new Standard_ASTM_D3230(crudeFluid);
salt.setWaterCut(0.005);              // 0.5 vol% water (or setWaterCut(0.5, "vol%"))
salt.setBrineSalinity(35.0, "kg/m3"); // 35 g/L brine
salt.calculate();

double ptb = salt.getValue("saltContentPTB");
double ppmw = salt.getValue("saltContent", "mg/kg");

// Optional maximum salt-content specification
salt.setMaxSaltSpec(10.0); // PTB
System.out.println("On spec: " + salt.isOnSpec());
```

---

## Properties NeqSim cannot predict from composition

Some oil-quality properties depend on molecular features (acidic groups, coke-forming
tendency, optical absorption) that are **not represented in a cubic equation-of-state
composition**. NeqSim does not predict these from an EOS fluid, and attempting to do so would
produce misleading numbers. Supply them from laboratory assay data instead.

| Property | Standard | Why it cannot be predicted from an EOS composition |
|----------|----------|----------------------------------------------------|
| Total Acid Number (TAN) | ASTM D664 | Set by naphthenic-acid and other carboxylic-acid concentrations. A cubic EOS pseudo-component carries no acid-group information, so TAN can only be a stoichiometric passthrough **if** acidic pseudo-components with known acid numbers are explicitly defined. |
| Conradson / Micro Carbon Residue (MCR) | ASTM D189 / D4530 | Measures coke left after pyrolysis of the heavy ends &ndash; a thermal-cracking outcome, not a phase-equilibrium property. At best a rough, heavily caveated correlation from residue yield and density; not a thermodynamic prediction. |
| Color | ASTM D1500 | An optical (visible-light absorption) property driven by trace chromophores. There is no thermodynamic basis to derive it from composition. |

For TAN and MCR, if an assay value is available it should be attached to the fluid as
metadata and carried through mass-balance mixing rather than computed from the EOS. Color must
always be taken from measurement.

---

## ASTM D2500 - Cloud Point

Determines the cloud point (wax appearance temperature) of the oil by calculating the temperature at which solid wax first precipitates.

### Parameters

| Parameter | Description | Unit |
|-----------|-------------|------|
| `cloudPoint` | Cloud point temperature | K (default), C/F via unit arg |

### Example

```java
Standard_ASTM_D2500 d2500 = new Standard_ASTM_D2500(oil);
d2500.calculate();

double cloudPointC = d2500.getValue("cloudPoint", "C");
System.out.println("Cloud point: " + cloudPointC + " C");

boolean onSpec = d2500.isOnSpec(); // Checks against max cloud point spec
```

### Requirements

Cloud point calculation relies on the thermodynamic model's ability to predict wax (solid) phase formation. For best results:
- Use characterized fluids with heavy fractions (C20+)
- Enable multi-phase check: `fluid.setMultiPhaseCheck(true)`

---

## ASTM D97 - Pour Point

Determines the pour point (lowest temperature at which oil still flows) by scanning for gel formation via viscosity threshold and wax fraction.

### Parameters

| Parameter | Description | Unit |
|-----------|-------------|------|
| `pourPoint` | Pour point temperature | K (default), C/F via unit arg |

### Example

```java
Standard_ASTM_D97 d97 = new Standard_ASTM_D97(oil);
d97.calculate();

double pourPointC = d97.getValue("pourPoint", "C");
System.out.println("Pour point: " + pourPointC + " C");
```

### Method

The pour point is estimated by cooling the oil in 3 °C steps and checking:
1. Whether kinematic viscosity exceeds 20,000 cP (gelation threshold)
2. Whether precipitated wax fraction exceeds 2%

The pour point is reported as the gel-point temperature plus a 3 °C offset per ASTM convention.

---

## BS&W - Basic Sediment and Water

Determines the water and sediment volume fraction in crude oil per ASTM D4007 / API MPMS Chapter 10.

### Parameters

| Parameter | Description | Unit |
|-----------|-------------|------|
| `BSW` | Basic sediment & water | vol% |
| `waterVolumeFraction` | Water volume fraction | fraction |
| `oilVolumeFraction` | Oil volume fraction | fraction |

### Spec Checking

```java
Standard_BSW bsw = new Standard_BSW(oilWithWater);
bsw.setMaxBSW(0.5); // Max 0.5 vol%
bsw.calculate();

double bswPct = bsw.getValue("BSW");
boolean onSpec = bsw.isOnSpec();
System.out.printf("BS&W: %.2f vol%% - %s%n", bswPct, onSpec ? "ON SPEC" : "OFF SPEC");
```

### Test Temperature

BS&W measurement is performed at 60 °C (centrifuge test conditions). The implementation flashes the fluid at 60 °C and reports the aqueous phase volume fraction.

---

## Complete Oil Quality Report

Generate a comprehensive quality report for a crude oil:

```java
SystemSrkEos oil = new SystemSrkEos(273.15 + 15.0, 1.01325);
oil.addComponent("methane", 0.01);
oil.addComponent("H2S", 0.005);
oil.addTBPfraction("C7", 0.10, 95.0, 0.72);
oil.addTBPfraction("C10", 0.20, 135.0, 0.78);
oil.addTBPfraction("C20", 0.35, 280.0, 0.85);
oil.addTBPfraction("C30", 0.295, 450.0, 0.91);
oil.addComponent("water", 0.01);
oil.setMixingRule(2);

// API Gravity & Density
Standard_ASTM_D4052 d4052 = new Standard_ASTM_D4052(oil);
d4052.calculate();
System.out.printf("API Gravity: %.1f (%s)%n",
    d4052.getValue("API gravity"), d4052.getOilClassification());

// Sulfur Content
Standard_ASTM_D4294 d4294 = new Standard_ASTM_D4294(oil);
d4294.calculate();
System.out.printf("Sulfur: %.3f wt%% (%s)%n",
    d4294.getValue("sulfurContent"), d4294.getSulfurClassification());

// BS&W
Standard_BSW bsw = new Standard_BSW(oil);
bsw.calculate();
System.out.printf("BS&W: %.2f vol%%%n", bsw.getValue("BSW"));

// Viscosity
Standard_ASTM_D445 d445 = new Standard_ASTM_D445(oil);
d445.calculate();
System.out.printf("KV40: %.2f cSt, KV100: %.2f cSt, VI: %.0f%n",
    d445.getValue("KV40"), d445.getValue("KV100"), d445.getValue("VI"));

// Distillation
Standard_ASTM_D86 d86 = new Standard_ASTM_D86(oil);
d86.calculate();
System.out.printf("Distillation: IBP=%.0f, T50=%.0f, FBP=%.0f C%n",
    d86.getValue("IBP", "C"), d86.getValue("T50", "C"), d86.getValue("FBP", "C"));
```

---

## Python Usage

```python
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
Standard_ASTM_D4052 = jneqsim.standards.oilquality.Standard_ASTM_D4052
Standard_ASTM_D86 = jneqsim.standards.oilquality.Standard_ASTM_D86
Standard_ASTM_D445 = jneqsim.standards.oilquality.Standard_ASTM_D445

oil = SystemSrkEos(273.15 + 15.0, 1.01325)
oil.addComponent("methane", 0.01)
oil.addTBPfraction("C7", 0.15, 95.0, 0.72)
oil.addTBPfraction("C10", 0.25, 135.0, 0.78)
oil.addTBPfraction("C20", 0.30, 280.0, 0.85)
oil.addTBPfraction("C30", 0.29, 450.0, 0.91)
oil.setMixingRule(2)

d4052 = Standard_ASTM_D4052(oil)
d4052.calculate()
print(f"API gravity: {d4052.getValue('API gravity'):.1f}")
print(f"Classification: {d4052.getOilClassification()}")
```

---

## Related Documentation

- [ASTM D6377 - Reid Vapor Pressure](astm_d6377_rvp)
- [Sales Contracts](sales_contracts)
- [ISO 6976 - Calorific Values](iso6976_calorific_values)
