# Heat Transfer in Pipelines

## Overview

NeqSim's `PipeBeggsAndBrills` class supports non-adiabatic operation with heat exchange to/from the surroundings. This is important for:

- Subsea pipelines (cold seawater)
- Buried pipelines (ground temperature)
- Uninsulated surface lines
- Wax and hydrate risk assessment

## Heat Transfer Modes

### 1. Adiabatic (Default)
No heat exchange with surroundings:
```java
pipe.setRunAdiabatic(true);  // Default
```

### 2. Constant Surface Temperature
Heat transfer with fixed ambient temperature:
```java
pipe.setRunAdiabatic(false);
pipe.setRunConstantSurfaceTemperature(true);
pipe.setConstantSurfaceTemperature(277.15);  // 4°C (seawater)
```

### 3. Specified Heat Transfer Coefficient
```java
pipe.setHeatTransferCoefficient(50.0);  // W/m²K
```

### 4. Estimated Heat Transfer
Uses internal correlations:
```java
pipe.setHeatTransferCoefficientMethod("Estimated");
```

## Heat Transfer Equations

### Overall Heat Balance

The temperature change across a segment is calculated from:

$$\dot{Q} = U \cdot A \cdot \Delta T_{lm}$$

Where:
- $U$ = overall heat transfer coefficient (W/m²K)
- $A = \pi D L$ = heat transfer area
- $\Delta T_{lm}$ = log-mean temperature difference

### Log-Mean Temperature Difference

$$\Delta T_{lm} = \frac{(T_s - T_{out}) - (T_s - T_{in})}{\ln\left(\frac{T_s - T_{out}}{T_s - T_{in}}\right)}$$

Where:
- $T_s$ = surface/ambient temperature
- $T_{in}$ = segment inlet temperature
- $T_{out}$ = segment outlet temperature

### Gnielinski Correlation

For internal convection in turbulent flow (3000 < Re < 5×10⁶):

$$Nu = \frac{(f/8)(Re - 1000)Pr}{1 + 12.7\sqrt{f/8}(Pr^{2/3} - 1)}$$

Where:
- $Nu$ = Nusselt number = $hD/k$
- $f$ = Darcy friction factor
- $Re$ = Reynolds number
- $Pr$ = Prandtl number = $c_p \mu / k$

The internal heat transfer coefficient:
$$h_{internal} = \frac{Nu \cdot k}{D}$$

## Typical Heat Transfer Coefficients

### Overall U-Values (Pipeline + Insulation)

| Configuration | U (W/m²K) | Application |
|--------------|-----------|-------------|
| Bare steel in air | 10-25 | Onshore exposed |
| Bare steel in water | 300-500 | Uninsulated subsea |
| 25mm insulation | 3-5 | Standard insulated |
| 50mm insulation | 1.5-3 | Well insulated |
| 75mm+ insulation | <1.5 | Heavily insulated |
| Pipe-in-pipe | 0.5-2 | High spec subsea |

### Internal Convection Coefficients

| Fluid | h_internal (W/m²K) |
|-------|-------------------|
| Gas (low P) | 20-50 |
| Gas (high P) | 100-300 |
| Light oil | 100-300 |
| Heavy oil | 50-150 |
| Water | 1000-5000 |
| Two-phase | 200-1000 |

## Usage Examples

### Subsea Pipeline Cooling

```java
SystemInterface gas = new SystemSrkEos(353.15, 100.0);  // 80°C wellhead
gas.addComponent("methane", 0.85);
gas.addComponent("ethane", 0.10);
gas.addComponent("propane", 0.05);
gas.setMixingRule(2);

Stream wellhead = new Stream("wellhead", gas);
wellhead.setFlowRate(100000, "kg/hr");
wellhead.run();

PipeBeggsAndBrills subsea = new PipeBeggsAndBrills("subsea", wellhead);
subsea.setLength(20000);           // 20 km
subsea.setDiameter(0.254);         // 10 inch
subsea.setElevation(-200);         // 200m water depth
subsea.setPipeWallRoughness(4.6e-5);
subsea.setNumberOfIncrements(40);

// Heat transfer to seawater
subsea.setRunAdiabatic(false);
subsea.setRunConstantSurfaceTemperature(true);
subsea.setConstantSurfaceTemperature(277.15);  // 4°C seabed
subsea.setHeatTransferCoefficient(5.0);        // Insulated

subsea.run();

double outletTemp = subsea.getOutletTemperature() - 273.15;
System.out.println("Arrival temperature: " + outletTemp + " °C");
```

### Temperature Profile

```java
// Get temperature along the pipeline
List<Double> tempProfile = subsea.getTemperatureProfile();
double segmentLength = 20000.0 / 40;

for (int i = 0; i < tempProfile.size(); i++) {
    double distance = i * segmentLength / 1000.0;  // km
    double tempC = tempProfile.get(i) - 273.15;
    System.out.println(distance + " km: " + tempC + " °C");
}
```

## Hydrate and Wax Considerations

### Hydrate Formation
Monitor temperature relative to hydrate equilibrium:
```java
double hydroEqTemp = ...; // From hydrate flash
double margin = outletTemp - hydroEqTemp;
if (margin < 5.0) {
    System.out.println("WARNING: Close to hydrate region");
}
```

### Wax Appearance
Check against wax appearance temperature (WAT):
```java
double WAT = ...; // From wax analysis
if (outletTemp < WAT) {
    System.out.println("WARNING: Below WAT - wax may deposit");
}
```

## Limitations

1. **Steady-state heat transfer**: No thermal mass of pipe wall
2. **Constant ambient**: No variation along pipe length
3. **Single U-value**: Same coefficient for entire pipe
4. **No Joule-Thomson**: Expansion cooling handled separately

## Best Practices

### 1. Segment Sizing for Heat Transfer
Use more segments for accurate temperature profiles:
```java
pipe.setNumberOfIncrements(50);  // For long, cooling pipelines
```

### 2. Validate Against Simple Cases
For long pipes with large temperature change, check:
$$T_{out} \approx T_s + (T_{in} - T_s) \cdot e^{-UAL/(\dot{m}c_p)}$$

### 3. Consider Two-Phase Effects
Heat transfer coefficients are higher for two-phase flow due to turbulence.

## See Also

- [Pipeline Pressure Drop Overview](pipeline_pressure_drop.md)
- [Beggs & Brill Correlation](beggs_and_brill_correlation.md)
- [Thermodynamic Properties](../thermo/physical_properties.md)
