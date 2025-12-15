# Venturi Flow Calculation in NeqSim

This document describes the Venturi flow meter calculation methods implemented in NeqSim for computing mass flow rates from differential pressure measurements, and vice versa.

## Overview

NeqSim implements Venturi flow calculations primarily in the `DifferentialPressureFlowCalculator` class, which is a utility for calculating mass flow rates from various differential pressure devices using NeqSim thermodynamic properties. The calculator supports both:

1. **Flow from dP**: Calculate mass flow rate given differential pressure
2. **dP from Flow**: Calculate differential pressure given mass flow rate (inverse calculation)

**Location:** [DifferentialPressureFlowCalculator.java](../../src/main/java/neqsim/process/equipment/diffpressure/DifferentialPressureFlowCalculator.java)

## Supported Flow Meter Types

The calculator supports multiple differential pressure device types:

| Flow Type | Default Discharge Coefficient |
|-----------|------------------------------|
| Venturi | 0.985 |
| Orifice | Calculated (Reader-Harris/Gallagher) |
| V-Cone | 0.82 |
| Nozzle | Calculated |
| DallTube | Calculated |
| Annubar | Calculated |
| Simplified | User-provided Cv |
| Perrys-Orifice | Subsonic: 0.62, Sonic: 0.75-0.84 |

## Venturi Calculation Method

### Fundamental Equation

The Venturi flow calculation uses the compressible flow equation with an expansibility (expansion) factor:

$$
\dot{m} = \frac{C}{\sqrt{1 - \beta^4}} \cdot \varepsilon \cdot \frac{\pi d^2}{4} \cdot \sqrt{2 \rho \Delta P}
$$

Where:
- $\dot{m}$ = mass flow rate (kg/s)
- $C$ = discharge coefficient (default: 0.985 for Venturi)
- $\beta$ = diameter ratio = $d/D$
- $d$ = throat diameter (m)
- $D$ = upstream pipe diameter (m)
- $\varepsilon$ = expansibility factor
- $\rho$ = upstream fluid density (kg/m³)
- $\Delta P$ = differential pressure (Pa)

### Expansibility Factor (ε)

The expansibility factor accounts for compressibility effects in gas flow and is calculated using an isentropic expansion model:

$$
\varepsilon = \sqrt{\frac{\kappa \cdot \tau^{2/\kappa}}{\kappa - 1} \cdot \frac{1 - \beta^4}{1 - \beta^4 \tau^{2/\kappa}} \cdot \frac{1 - \tau^{(\kappa-1)/\kappa}}{1 - \tau}}
$$

Where:
- $\tau$ = pressure ratio = $P_1 / (P_1 + \Delta P)$
- $\kappa$ = isentropic exponent (Cp/Cv) of the fluid
- $\beta^4$ = fourth power of diameter ratio

### Implementation in NeqSim

```java
private static double[] calcVenturi(double[] dp, double[] p, double[] rho, double[] kappa,
    double D, double d, double C) {
  double beta = d / D;
  double beta4 = Math.pow(beta, 4.0);
  double betaTerm = Math.sqrt(Math.max(1.0 - beta4, 1e-30));
  double[] massFlow = new double[dp.length];
  
  for (int i = 0; i < dp.length; i++) {
    double tau = p[i] / (p[i] + dp[i]);
    double k = kappa[i];
    double tau2k = Math.pow(tau, 2.0 / k);
    
    // Expansibility factor calculation
    double numerator = k * tau2k / (k - 1.0) * (1.0 - beta4)
        / (1.0 - beta4 * tau2k) * (1.0 - Math.pow(tau, (k - 1.0) / k)) / (1.0 - tau);
    double eps = Math.sqrt(Math.max(numerator, 0.0));
    
    // Mass flow calculation
    double rootTerm = Math.sqrt(Math.max(dp[i] * rho[i] * 2.0, 0.0));
    double value = C / betaTerm * eps * Math.PI / 4.0 * d * d * rootTerm;
    massFlow[i] = tau == 1.0 ? 0.0 : value * 3600.0;  // Convert to kg/h
  }
  return massFlow;
}
```

## Inverse Calculation: Differential Pressure from Flow

### Fundamental Equation

To calculate the differential pressure from a known mass flow rate, we rearrange the Venturi equation:

$$
\Delta P = \frac{1}{2\rho} \left( \frac{\dot{m} \cdot \sqrt{1 - \beta^4}}{C \cdot \varepsilon \cdot A} \right)^2
$$

Where:
- $A$ = throat area = $\frac{\pi d^2}{4}$

Since the expansibility factor $\varepsilon$ depends on the differential pressure (through the pressure ratio $\tau$), an iterative solution is required.

### Algorithm

1. **Initial estimate** (assuming incompressible flow, $\varepsilon = 1$):
   $$
   \Delta P_0 = \frac{1}{2\rho} \left( \frac{\dot{m} \cdot \sqrt{1 - \beta^4}}{C \cdot A} \right)^2
   $$

2. **Iterate** until convergence:
   - Calculate pressure ratio: $\tau = \frac{P_1}{P_1 + \Delta P}$
   - Calculate expansibility factor $\varepsilon$ from $\tau$ and $\kappa$
   - Update: $\Delta P_{n+1} = \frac{1}{2\rho} \left( \frac{\dot{m} \cdot \sqrt{1 - \beta^4}}{C \cdot \varepsilon \cdot A} \right)^2$
   - Check convergence: $|\Delta P_{n+1} - \Delta P_n| < 0.01$ Pa

### Implementation in NeqSim

```java
public static double calculateDpFromFlowVenturi(double massFlowKgPerHour, double pressureBara,
    double density, double kappa, double pipeDiameterMm, double throatDiameterMm,
    double dischargeCoefficient) {

  double D = pipeDiameterMm / 1000.0;
  double d = throatDiameterMm / 1000.0;
  double C = dischargeCoefficient;
  double massFlowKgPerSec = massFlowKgPerHour / 3600.0;

  double beta = d / D;
  double beta4 = Math.pow(beta, 4.0);
  double betaTerm = Math.sqrt(Math.max(1.0 - beta4, 1e-30));

  // Initial estimate (incompressible)
  double A = Math.PI / 4.0 * d * d;
  double dpInitial = Math.pow(massFlowKgPerSec * betaTerm / (C * A), 2) / (2.0 * density);

  // Iterate to account for expansibility factor
  double dpPa = dpInitial;
  double pPa = pressureBara * 1.0e5;

  for (int iter = 0; iter < 100; iter++) {
    double tau = pPa / (pPa + dpPa);
    double tau2k = Math.pow(tau, 2.0 / kappa);
    double numerator = kappa * tau2k / (kappa - 1.0) * (1.0 - beta4)
        / (1.0 - beta4 * tau2k) * (1.0 - Math.pow(tau, (kappa - 1.0) / kappa)) / (1.0 - tau);
    double eps = Math.sqrt(Math.max(numerator, 1e-30));

    double dpNew = Math.pow(massFlowKgPerSec * betaTerm / (C * eps * A), 2) / (2.0 * density);

    if (Math.abs(dpNew - dpPa) < 0.01) {
      dpPa = dpNew;
      break;
    }
    dpPa = dpNew;
  }

  return dpPa / 100.0;  // Convert Pa to mbar
}
```

## Input Parameters

The calculator requires the following inputs:

### Geometry Parameters (flowData array)
| Index | Parameter | Unit |
|-------|-----------|------|
| 0 | Pipe diameter (D) | mm |
| 1 | Throat diameter (d) | mm |
| 2 | Discharge coefficient (optional) | - |

### Operating Conditions
| Parameter | Unit |
|-----------|------|
| Pressure | barg |
| Temperature | °C |
| Differential Pressure | mbar |

### Fluid Composition
- Component names (e.g., "methane", "CO2", "N2")
- Mole fractions

## Thermodynamic Properties

NeqSim uses the SRK (Soave-Redlich-Kwong) equation of state to calculate the required thermodynamic properties:

1. **Density (ρ)** - Calculated at actual flowing conditions
2. **Viscosity (μ)** - Used for Reynolds number calculations in orifice/nozzle
3. **Isentropic exponent (κ)** - Cp/Cv ratio, calculated at low pressure conditions
4. **Molecular weight** - For standard flow conversions

## Output Results

The `FlowCalculationResult` class provides:

| Output | Unit |
|--------|------|
| Mass flow rate | kg/h |
| Volumetric flow rate (actual) | m³/h |
| Standard volumetric flow | MSm³/day |
| Molecular weight | g/mol |

## Usage Example

```java
import neqsim.process.equipment.diffpressure.DifferentialPressureFlowCalculator;
import neqsim.process.equipment.diffpressure.DifferentialPressureFlowCalculator.FlowCalculationResult;
import java.util.Arrays;
import java.util.List;

// Operating conditions
double[] pressureBarg = {50.0};        // 50 barg
double[] temperatureC = {25.0};        // 25°C
double[] dpMbar = {200.0};             // 200 mbar differential pressure

// Venturi geometry: D=300mm, d=200mm, Cd=0.985
double[] flowData = {300.0, 200.0, 0.985};

// Gas composition
List<String> components = Arrays.asList("methane", "ethane", "propane");
double[] fractions = {0.85, 0.10, 0.05};

// Calculate flow
FlowCalculationResult result = DifferentialPressureFlowCalculator.calculate(
    pressureBarg, temperatureC, dpMbar, "Venturi", flowData,
    components, fractions, true);

double massFlowKgH = result.getMassFlowKgPerHour()[0];
double stdFlowMSm3Day = result.getStandardFlowMSm3PerDay()[0];
```

### Example 2: Calculate Differential Pressure from Flow (Inverse)

```java
import neqsim.process.equipment.diffpressure.DifferentialPressureFlowCalculator;
import java.util.Arrays;
import java.util.List;

// Known mass flow rate
double massFlowKgPerHour = 50000.0;  // 50,000 kg/h

// Operating conditions
double pressureBarg = 50.0;          // 50 barg
double temperatureC = 25.0;          // 25°C

// Venturi geometry: D=300mm, d=200mm, Cd=0.985
double[] flowData = {300.0, 200.0, 0.985};

// Gas composition
List<String> components = Arrays.asList("methane", "ethane", "propane");
double[] fractions = {0.85, 0.10, 0.05};

// Calculate differential pressure
double dpMbar = DifferentialPressureFlowCalculator.calculateDpFromFlow(
    massFlowKgPerHour, pressureBarg, temperatureC, "Venturi", flowData,
    components, fractions, true);

System.out.println("Differential pressure: " + dpMbar + " mbar");
```

### Example 3: Direct Calculation with Known Fluid Properties

```java
// If you already have fluid properties calculated
double massFlowKgPerHour = 50000.0;
double pressureBara = 51.0125;       // bara
double density = 42.5;               // kg/m³
double kappa = 1.28;                 // isentropic exponent
double pipeDiameterMm = 300.0;       // mm
double throatDiameterMm = 200.0;     // mm
double Cd = 0.985;                   // discharge coefficient

double dpMbar = DifferentialPressureFlowCalculator.calculateDpFromFlowVenturi(
    massFlowKgPerHour, pressureBara, density, kappa, 
    pipeDiameterMm, throatDiameterMm, Cd);

System.out.println("Differential pressure: " + dpMbar + " mbar");
```

## Comparison with Other Flow Meter Types

### Orifice Plate

Uses the Reader-Harris/Gallagher correlation (ISO 5167) for discharge coefficient with iterative solution:

$$
C = 0.5961 + 0.0261\beta^2 - 0.216\beta^8 + 0.000521\left(\frac{10^6\beta}{Re_D}\right)^{0.7} + \ldots
$$

Expansibility factor:
$$
\varepsilon = 1 - (0.351 + 0.256\beta^4 + 0.93\beta^8)\left[1 - \left(\frac{P_2}{P_1}\right)^{1/\kappa}\right]
$$

### ISA 1932 Nozzle

Uses a similar approach to Venturi but with a different discharge coefficient correlation:
$$
C = 0.99 - 0.2262\beta^{4.1} - (0.00175\beta^2 - 0.0033\beta^{4.15})\left(\frac{10^7}{Re_D}\right)^{1.15}
$$

### V-Cone

Uses a modified beta ratio based on cone geometry:
$$
\beta_{V-Cone} = \sqrt{1 - \frac{d_{cone}^2}{D^2}}
$$

Expansibility factor:
$$
\varepsilon = 1 - (0.649 + 0.696\beta^4)\frac{\Delta P}{\kappa \cdot P}
$$

## Standards and References

The implementations are based on:
- **ISO 5167** - Measurement of fluid flow by means of pressure differential devices
- **Reader-Harris/Gallagher correlation** - For orifice discharge coefficients
- **Perry's Chemical Engineers' Handbook** - For compressible flow through orifices

## Key Considerations

1. **Compressibility**: The expansibility factor is critical for gas flow; for liquids, ε ≈ 1.0
2. **Beta Ratio Limits**: Typically valid for 0.2 ≤ β ≤ 0.75
3. **Reynolds Number**: Correlations are valid for Re > 4000 (turbulent flow)
4. **Pressure Recovery**: Venturi meters have better pressure recovery (~90%) compared to orifice plates (~40%)
5. **Accuracy**: Typical uncertainty is ±0.5% to ±1.5% depending on installation and calibration

## Related Classes

- [Orifice.java](../../src/main/java/neqsim/process/equipment/diffpressure/Orifice.java) - Equipment class for orifice calculations in process simulations
- [VirtualFlowMeter.java](../../src/main/java/neqsim/process/measurementdevice/vfm/VirtualFlowMeter.java) - Virtual flow meter using differential pressure
