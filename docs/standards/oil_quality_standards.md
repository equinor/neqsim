---
title: "Oil Quality Standards"
description: "Comprehensive guide to oil quality standards in NeqSim including ASTM D86 distillation, D445 viscosity, D4052 density/API gravity, D4294 sulfur, D2500 cloud point, D97 pour point, and BS&W."
---

# Oil Quality Standards

NeqSim provides thermodynamics-based implementations of key ASTM standards used for crude oil and petroleum product quality characterization.

## Available Standards

| Standard | Class | Property | Key Output |
|----------|-------|----------|------------|
| ASTM D86 | `Standard_ASTM_D86` | Atmospheric distillation | IBP, T10-T90, FBP |
| ASTM D445 | `Standard_ASTM_D445` | Kinematic viscosity | KV40, KV100, Viscosity Index |
| ASTM D4052 | `Standard_ASTM_D4052` | Density / API gravity | Density, SG, API, classification |
| ASTM D4294 | `Standard_ASTM_D4294` | Total sulfur content | Sulfur wt%, sweet/sour class |
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

| Parameter | Description |
|-----------|-------------|
| `IBP` | Initial boiling point |
| `T5` - `T95` | Temperature at 5%-95% volume distilled |
| `FBP` | Final boiling point |

### Temperature Units

Supported via `getValue(param, unit)`: `"C"`, `"K"`, `"F"`, `"R"`

### Example

```java
Standard_ASTM_D86 d86 = new Standard_ASTM_D86(oil);
d86.calculate();

// Individual points
double ibp = d86.getValue("IBP", "C");
double t50 = d86.getValue("T50", "C");
double fbp = d86.getValue("FBP", "C");

// Full distillation curve as double[N][2] (fraction, temperature_K)
double[][] curve = d86.getDistillationCurve();
for (double[] point : curve) {
    System.out.printf("%.0f%% -> %.1f C%n", point[0] * 100, point[1] - 273.15);
}
```

### How It Works

1. **IBP** - Bubble point temperature flash at system pressure
2. **Intermediate points** (T5-T95) - TV fraction flash at specified volume fractions
3. **FBP** - Dew point temperature flash

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
