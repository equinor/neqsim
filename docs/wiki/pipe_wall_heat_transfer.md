# Pipe Wall Construction and Heat Transfer Modeling

This document describes the pipe wall construction and heat transfer modeling capabilities in NeqSim, including material properties, multi-layer walls, and surrounding environment modeling.

## Table of Contents

1. [Overview](#overview)
2. [Pipe Materials](#pipe-materials)
3. [Material Layers](#material-layers)
4. [Pipe Wall Assembly](#pipe-wall-assembly)
5. [Surrounding Environment](#surrounding-environment)
6. [Heat Transfer Calculations](#heat-transfer-calculations)
7. [API Reference](#api-reference)
8. [Examples](#examples)

---

## Overview

The pipe wall modeling system in NeqSim provides:
- **Material database** with thermal properties
- **Multi-layer wall construction** (pipe + insulation + coating)
- **Surrounding environment models** (air, water, soil)
- **Heat transfer resistance calculations**
- **Integration with transient flow simulation**

---

## Pipe Materials

### Standard Materials

NeqSim includes pre-defined pipe materials with thermal properties:

| Material | Thermal Conductivity (W/m·K) | Density (kg/m³) | Specific Heat (J/kg·K) |
|----------|------------------------------|-----------------|------------------------|
| Carbon Steel | 50.0 | 7850 | 490 |
| Stainless Steel 316 | 16.3 | 8000 | 500 |
| Duplex Steel | 15.0 | 7800 | 500 |
| Super Duplex | 14.0 | 7800 | 500 |
| Titanium | 21.9 | 4500 | 523 |
| Inconel 625 | 9.8 | 8440 | 410 |
| Monel 400 | 21.8 | 8800 | 427 |
| Copper | 401.0 | 8960 | 385 |
| HDPE | 0.5 | 960 | 1800 |
| PVC | 0.19 | 1400 | 1000 |
| GRP (Fiberglass) | 0.3 | 1850 | 900 |

### Creating Custom Materials

```java
// Using standard material
PipeMaterial steel = PipeMaterial.CARBON_STEEL;

// Creating custom material
PipeMaterial custom = new PipeMaterial(
    "Custom Alloy",
    25.0,    // thermalConductivity (W/m·K)
    7500,    // density (kg/m³)
    480      // specificHeatCapacity (J/kg·K)
);
```

### Material Properties

- **Thermal Conductivity ($k$)**: Ability to conduct heat (W/m·K)
- **Density ($\rho$)**: Material mass per unit volume (kg/m³)
- **Specific Heat Capacity ($C_p$)**: Energy to raise temperature by 1 K (J/kg·K)
- **Thermal Diffusivity**: $\alpha = k / (\rho C_p)$ (m²/s)

---

## Material Layers

A `MaterialLayer` combines a material with its thickness:

```java
// Create insulation layer
MaterialLayer insulation = new MaterialLayer(
    "Polyurethane Foam",
    0.025,   // thermalConductivity (W/m·K)
    40,      // density (kg/m³)
    1500,    // specificHeatCapacity (J/kg·K)
    0.05     // thickness (m) = 50 mm
);

// Using pipe material
MaterialLayer pipeWall = new MaterialLayer(
    PipeMaterial.CARBON_STEEL,
    0.012    // thickness = 12 mm
);
```

### Layer Properties

| Property | Description | Units |
|----------|-------------|-------|
| `thickness` | Layer thickness | m |
| `thermalConductivity` | Heat conduction coefficient | W/(m·K) |
| `density` | Material density | kg/m³ |
| `specificHeatCapacity` | Thermal capacity | J/(kg·K) |

---

## Pipe Wall Assembly

### Multi-Layer Construction

The `PipeWall` class represents a complete pipe wall with multiple layers:

```java
// Method 1: Create layer by layer
PipeWall wall = new PipeWall(0.15);  // inner radius = 150 mm

wall.addLayer(new MaterialLayer(PipeMaterial.CARBON_STEEL, 0.012));
wall.addLayer(new MaterialLayer("Insulation", 0.025, 40, 1500, 0.050));
wall.addLayer(new MaterialLayer("Coating", 0.3, 1200, 1400, 0.005));

// Method 2: Using PipeWallBuilder (fluent API)
PipeWall wall = new PipeWallBuilder()
    .innerRadius(0.15)
    .addPipeLayer(PipeMaterial.CARBON_STEEL, 0.012)
    .addInsulationLayer(0.025, 0.050)
    .addCoatingLayer(0.3, 0.005)
    .build();
```

### Thermal Resistance

The total radial thermal resistance through a cylindrical wall:

$$
R_{total} = \sum_{i=1}^{n} R_i = \sum_{i=1}^{n} \frac{\ln(r_{i+1}/r_i)}{2\pi k_i L}
$$

Where:
- $r_i$ = inner radius of layer $i$ (m)
- $r_{i+1}$ = outer radius of layer $i$ (m)
- $k_i$ = thermal conductivity of layer $i$ (W/m·K)
- $L$ = pipe length (m)

Per unit length:

$$
R'_{total} = \sum_{i=1}^{n} \frac{\ln(r_{i+1}/r_i)}{2\pi k_i} \quad \text{(m·K/W)}
$$

### Key Properties

```java
double outerRadius = wall.getOuterRadius();           // m
double totalThickness = wall.getTotalWallThickness(); // m
double resistance = wall.getTotalResistancePerLength(); // m·K/W
double heatCapacity = wall.getThermalMass();          // J/(m·K)
int layerCount = wall.getLayerCount();
```

---

## Surrounding Environment

### Environment Types

The `PipeSurroundingEnvironment` class models the external conditions:

| Environment | Description | Typical $h$ (W/m²·K) |
|-------------|-------------|----------------------|
| Still Air | Natural convection | 5-25 |
| Moving Air | Forced convection | 10-200 |
| Seawater | Subsea pipelines | 150-1000 |
| Soil | Buried pipelines | 1-10 |

### Creating Environments

```java
// Using factory methods
PipeSurroundingEnvironment air = 
    PipeSurroundingEnvironment.stillAir(25.0);  // 25°C

PipeSurroundingEnvironment seawater = 
    PipeSurroundingEnvironment.seawater(4.0);   // 4°C

PipeSurroundingEnvironment soil = 
    PipeSurroundingEnvironment.soil(15.0, 1.5); // 15°C, k=1.5 W/m·K

// Custom environment
PipeSurroundingEnvironment custom = 
    new PipeSurroundingEnvironment("Wind", 10.0, 50.0);
    // 10°C ambient, h = 50 W/m²·K
```

### Convection Coefficients

**Still Air (Natural Convection)**:
$$
h \approx 5 + 5\sqrt{T_{surface} - T_{ambient}} \quad \text{W/(m²·K)}
$$

**Seawater**:
$$
h \approx 150 + 70 \cdot v_{current}^{0.8} \quad \text{W/(m²·K)}
$$

**Soil (Buried Pipe)**:
$$
h_{equiv} = \frac{k_{soil}}{r_o \cdot \ln(2H/r_o)} \quad \text{W/(m²·K)}
$$

Where $H$ is burial depth and $r_o$ is outer radius.

---

## Heat Transfer Calculations

### Overall Heat Transfer Coefficient

The overall U-value combining all resistances:

$$
\frac{1}{U A} = \frac{1}{h_i A_i} + \sum \frac{\ln(r_{o}/r_i)}{2\pi k L} + \frac{1}{h_o A_o}
$$

Based on outer surface area:

$$
U_o = \frac{1}{r_o \left(\frac{1}{h_i r_i} + \sum \frac{\ln(r_{i+1}/r_i)}{k_i} + \frac{1}{h_o}\right)}
$$

### Heat Transfer Rate

Heat flow per unit length:

$$
q' = U_o \cdot 2\pi r_o \cdot (T_{fluid} - T_{ambient}) \quad \text{W/m}
$$

Total heat flow:

$$
Q = q' \cdot L = U_o \cdot A_o \cdot \Delta T \quad \text{W}
$$

### Temperature Profile

The fluid temperature along the pipe (steady-state):

$$
T(x) = T_{ambient} + (T_{inlet} - T_{ambient}) \exp\left(-\frac{U_o \cdot \pi D_o}{\dot{m} C_p} x\right)
$$

### Wall Temperature Distribution

Temperature at interface between layers $j$ and $j+1$:

$$
T_j = T_{fluid} - q' \cdot \left(\frac{1}{h_i \cdot 2\pi r_i} + \sum_{k=1}^{j} \frac{\ln(r_{k+1}/r_k)}{2\pi k_k}\right)
$$

---

## API Reference

### PipeMaterial Enum

```java
public enum PipeMaterial {
    CARBON_STEEL(50.0, 7850, 490),
    STAINLESS_316(16.3, 8000, 500),
    // ... more materials
    
    double getThermalConductivity();
    double getDensity();
    double getSpecificHeatCapacity();
    double getThermalDiffusivity();
}
```

### MaterialLayer Class

```java
public class MaterialLayer {
    // Constructors
    MaterialLayer(String name, double k, double rho, double cp, double t);
    MaterialLayer(PipeMaterial material, double thickness);
    
    // Properties
    double getThickness();
    double getThermalConductivity();
    double getDensity();
    double getSpecificHeatCapacity();
    
    // Calculations
    double getRadialResistance(double innerRadius);
    double getHeatCapacityPerLength(double innerRadius);
}
```

### PipeWall Class

```java
public class PipeWall {
    // Construction
    PipeWall(double innerRadius);
    void addLayer(MaterialLayer layer);
    
    // Properties
    double getInnerRadius();
    double getOuterRadius();
    double getTotalWallThickness();
    int getLayerCount();
    
    // Thermal calculations
    double getTotalResistancePerLength();
    double getUValuePerLength(double hInner, double hOuter);
    double getThermalMass();
}
```

### PipeSurroundingEnvironment Class

```java
public class PipeSurroundingEnvironment {
    // Factory methods
    static stillAir(double ambientTemp);
    static movingAir(double ambientTemp, double windSpeed);
    static seawater(double ambientTemp);
    static soil(double ambientTemp, double thermalConductivity);
    
    // Properties
    double getAmbientTemperature();
    double getConvectionCoefficient();
}
```

### PipeWallBuilder Class

```java
public class PipeWallBuilder {
    PipeWallBuilder innerRadius(double r);
    PipeWallBuilder innerDiameter(double d);
    PipeWallBuilder addLayer(MaterialLayer layer);
    PipeWallBuilder addPipeLayer(PipeMaterial material, double thickness);
    PipeWallBuilder addInsulationLayer(double k, double thickness);
    PipeWallBuilder addCoatingLayer(double k, double thickness);
    PipeWall build();
}
```

---

## Examples

### Example 1: Subsea Pipeline

```java
// Create multi-layer subsea pipe wall
PipeWall subseaPipe = new PipeWallBuilder()
    .innerDiameter(0.254)  // 10" ID
    .addPipeLayer(PipeMaterial.DUPLEX_STEEL, 0.0127)
    .addInsulationLayer(0.15, 0.060)  // Syntactic foam
    .addCoatingLayer(0.22, 0.006)     // Polypropylene
    .build();

// Seawater environment at 4°C
PipeSurroundingEnvironment seawater = 
    PipeSurroundingEnvironment.seawater(4.0);

// Calculate overall U-value
double hInner = 500;   // W/m²·K (turbulent gas flow)
double hOuter = seawater.getConvectionCoefficient();
double U = subseaPipe.getUValuePerLength(hInner, hOuter);

System.out.printf("U-value: %.2f W/(m²·K)%n", U);
```

### Example 2: Buried Gas Pipeline

```java
// Create insulated buried pipeline
PipeWall buriedPipe = new PipeWallBuilder()
    .innerDiameter(0.508)  // 20" ID
    .addPipeLayer(PipeMaterial.CARBON_STEEL, 0.0127)
    .addCoatingLayer(0.22, 0.003)  // FBE coating
    .build();

// Soil at 12°C, buried 1.5m deep
PipeSurroundingEnvironment soil = 
    PipeSurroundingEnvironment.soil(12.0, 1.2);

// Print configuration
System.out.printf("Wall thickness: %.1f mm%n", 
    buriedPipe.getTotalWallThickness() * 1000);
System.out.printf("Total resistance: %.4f m·K/W%n", 
    buriedPipe.getTotalResistancePerLength());
```

### Example 3: Temperature Profile Calculation

```java
// Pipeline parameters
double length = 50000;     // 50 km
double mDot = 15.0;        // kg/s
double Cp = 2500;          // J/(kg·K) - gas
double Tinlet = 80;        // °C
double Tambient = 5;       // °C
double Uo = 2.5;           // W/(m²·K) - overall U-value
double Do = 0.32;          // m - outer diameter

// Calculate outlet temperature
double exponent = -Uo * Math.PI * Do * length / (mDot * Cp);
double Toutlet = Tambient + (Tinlet - Tambient) * Math.exp(exponent);

System.out.printf("Outlet temperature: %.1f °C%n", Toutlet);
System.out.printf("Heat loss: %.0f kW%n", mDot * Cp * (Tinlet - Toutlet) / 1000);
```

### Example 4: Integration with Flow Simulation

```java
// Create fluid
SystemInterface gas = new SystemSrkEos(323.15, 50e5);
gas.addComponent("methane", 0.9);
gas.addComponent("ethane", 0.07);
gas.addComponent("propane", 0.03);
gas.setMixingRule("classic");

// Create inlet stream
Stream inlet = new Stream("Inlet", gas);
inlet.setFlowRate(500000, "kg/hr");
inlet.run();

// Create pipeline with heat transfer
OnePhasePipeLine pipe = new OnePhasePipeLine("Export", inlet);
pipe.setNumberOfLegs(1);
pipe.setNumberOfNodesInLeg(100);
pipe.setPipeDiameters(new double[] {0.508, 0.508});
pipe.setLegPositions(new double[] {0.0, 50000.0});
pipe.setOuterTemperature(278.15);  // 5°C ambient

// Run steady-state
pipe.run();

// Get outlet conditions
System.out.printf("Outlet T: %.1f °C%n", 
    pipe.getOutStream().getTemperature("C"));
System.out.printf("Outlet P: %.1f bara%n", 
    pipe.getOutStream().getPressure("bara"));
```

---

## References

1. Incropera, F.P. & DeWitt, D.P. (2011). *Fundamentals of Heat and Mass Transfer*. Wiley.
2. GPSA Engineering Data Book. Gas Processors Suppliers Association.
3. API 5L - Specification for Line Pipe.
4. DNVGL-ST-F101 - Submarine Pipeline Systems.
