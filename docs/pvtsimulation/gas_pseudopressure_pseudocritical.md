---
title: "Gas Pseudopressure and Pseudocritical Properties"
description: "Guide to calculating gas pseudopressure (real gas potential) and pseudocritical properties for natural gas mixtures in NeqSim."
---

# Gas Pseudopressure and Pseudocritical Properties

NeqSim provides utilities for gas reservoir engineering calculations: the real gas pseudopressure integral and pseudocritical property correlations for natural gas mixtures.

## Gas Pseudopressure

**Class:** `neqsim.pvtsimulation.util.GasPseudoPressure`

The real gas pseudopressure (also called real gas potential) is defined as:

$$
m(P) = 2 \int_{P_{ref}}^{P} \frac{P'}{\mu(P') \cdot Z(P')} \, dP'
$$

where $\mu$ is the gas viscosity and $Z$ is the gas compressibility factor. This function linearizes the gas flow equation by accounting for the pressure-dependent variations in viscosity and Z-factor.

### Applications

- Gas well deliverability analysis (backpressure and LIT methods)
- Pressure transient analysis (pressure buildup and drawdown tests)
- Gas reservoir material balance calculations
- Rate-transient analysis

### Two Calculation Modes

| Mode | When to Use | Accuracy |
|------|------------|----------|
| **EOS-based** | Known gas composition available | Highest — rigorous thermodynamic model |
| **Correlation-based** | Only gas specific gravity and MW known | Good — uses Hall-Yarborough Z + Lee-Gonzalez-Eakin viscosity |

### EOS-Based Calculation (Java)

Uses a NeqSim thermodynamic system (SRK, PR, GERG-2008, etc.) for rigorous Z and viscosity at each pressure step.

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.pvtsimulation.util.GasPseudoPressure;

// Define gas composition
SystemInterface gas = new SystemSrkEos(373.15, 200.0);
gas.addComponent("methane", 0.90);
gas.addComponent("ethane", 0.05);
gas.addComponent("propane", 0.03);
gas.addComponent("CO2", 0.02);
gas.setMixingRule("classic");

// Calculate pseudopressure
GasPseudoPressure calc = new GasPseudoPressure(gas);
calc.setNumberOfSteps(200); // Higher = more accurate

// m(200 bara) - m(10 bara)
double mP = calc.calculate(200.0, 10.0);

// With pressure units
double mPsia = calc.calculate(3000.0, 14.696, "psia");

// Single point (relative to atmospheric)
double mPat200 = calc.pseudoPressureAt(200.0); // bara
```

### Pseudopressure Profile

Generate a table of pseudopressure values across a pressure range:

```java
GasPseudoPressure calc = new GasPseudoPressure(gas);
double[][] profile = calc.pseudoPressureProfile(10.0, 300.0, 50);
// profile[0] = pressure array (bara)
// profile[1] = pseudopressure array
```

### Correlation-Based Calculation (No EOS Required)

Suitable when only bulk gas properties are available:

```java
// Parameters: P (psia), Pref (psia), T (°F), gamma_g, MW
double mP = GasPseudoPressure.calculateFromCorrelation(
    3000.0, 14.696, 200.0, 0.65, 16.04);

// Delta pseudopressure (absolute value)
double deltamP = GasPseudoPressure.deltaPseudoPressure(
    3000.0, 1500.0, 200.0, 0.65, 16.04);
```

### Python (via neqsim-python)

```python
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
GasPseudoPressure = jneqsim.pvtsimulation.util.GasPseudoPressure

# Define gas
gas = SystemSrkEos(373.15, 200.0)
gas.addComponent("methane", 0.90)
gas.addComponent("ethane", 0.05)
gas.addComponent("propane", 0.05)
gas.setMixingRule("classic")

# Calculate
calc = GasPseudoPressure(gas)
mP = calc.calculate(200.0, 10.0)
print(f"Pseudopressure: {mP:.2f} bara^2/cP")

# Profile
profile = calc.pseudoPressureProfile(10.0, 300.0, 20)
pressures = list(profile[0])
mP_values = list(profile[1])
```

---

## Gas Pseudocritical Properties

**Class:** `neqsim.pvtsimulation.util.GasPseudoCriticalProperties`

Pseudocritical temperature ($T_{pc}$) and pressure ($P_{pc}$) are required inputs for corresponding-states correlations (Z-factor, gas viscosity, compressibility). These are estimated from gas specific gravity when detailed composition is unavailable.

### Supported Correlations

| Correlation | Input | Best For |
|------------|-------|----------|
| **Standing (1981)** | $\gamma_g$ only | Sweet gases with low non-HC content |
| **Sutton (1985)** | $\gamma_g$ only | Wider range of gas compositions |
| **Piper-McCain-Corredor (2012)** | $\gamma_g$ + $y_{H_2S}$, $y_{CO_2}$, $y_{N_2}$ | Sour/acid gas systems |

### Acid Gas Correction

| Correction | Purpose |
|-----------|---------|
| **Wichert-Aziz (1972)** | Corrects $T_{pc}$ and $P_{pc}$ for H2S and CO2 content |

### Standing Correlation

Simple two-constant correlation for sweet natural gas:

$$
T_{pc} = 168 + 325 \gamma_g - 12.5 \gamma_g^2 \quad (\text{Rankine})
$$

$$
P_{pc} = 677 + 15 \gamma_g - 37.5 \gamma_g^2 \quad (\text{psia})
$$

```java
double tpc = GasPseudoCriticalProperties.pseudoCriticalTemperatureStanding(0.75);
double ppc = GasPseudoCriticalProperties.pseudoCriticalPressureStanding(0.75);
```

### Sutton Correlation

More accurate than Standing for a wider range of gas specific gravities (0.57 - 1.68):

$$
T_{pc} = 169.2 + 349.5 \gamma_g - 74.0 \gamma_g^2 \quad (\text{Rankine})
$$

$$
P_{pc} = 756.8 - 131.0 \gamma_g - 3.6 \gamma_g^2 \quad (\text{psia})
$$

```java
double tpc = GasPseudoCriticalProperties.pseudoCriticalTemperatureSutton(0.75);
double ppc = GasPseudoCriticalProperties.pseudoCriticalPressureSutton(0.75);

// SI units (K and bara)
double tpcK = GasPseudoCriticalProperties.pseudoCriticalTemperatureSuttonSI(0.75);
double ppcBara = GasPseudoCriticalProperties.pseudoCriticalPressureSuttonSI(0.75);
```

### Piper-McCain-Corredor Correlation

Uses non-hydrocarbon mole fractions for improved accuracy with sour gas:

```java
double gammaG = 0.80;
double yH2S = 0.10;
double yCO2 = 0.05;
double yN2 = 0.02;

double tpc = GasPseudoCriticalProperties.pseudoCriticalTemperaturePiper(
    gammaG, yH2S, yCO2, yN2);
double ppc = GasPseudoCriticalProperties.pseudoCriticalPressurePiper(
    gammaG, yH2S, yCO2, yN2);
```

### Wichert-Aziz Acid Gas Correction

Corrects pseudocritical properties from any base correlation for H2S and CO2:

$$
\varepsilon = 120 (A^{0.9} - A^{1.6}) + 15 (B^{0.5} - B^4)
$$

where $A = y_{H_2S} + y_{CO_2}$ and $B = y_{H_2S}$.

$$
T'_{pc} = T_{pc} - \varepsilon, \quad P'_{pc} = \frac{P_{pc} \cdot T'_{pc}}{T_{pc} + B(1 - B) \varepsilon}
$$

```java
// Step 1: Get base pseudocriticals (any correlation)
double tpc = GasPseudoCriticalProperties.pseudoCriticalTemperatureSutton(0.80);
double ppc = GasPseudoCriticalProperties.pseudoCriticalPressureSutton(0.80);

// Step 2: Apply Wichert-Aziz correction
double yH2S = 0.10;
double yCO2 = 0.05;
double[] corrected = GasPseudoCriticalProperties.wichertAzizCorrection(
    tpc, ppc, yH2S, yCO2);
double tpcCorrected = corrected[0]; // Rankine
double ppcCorrected = corrected[1]; // psia

// Step 3: Calculate reduced properties
double T = 660.0; // Rankine
double P = 2000.0; // psia
double tpr = GasPseudoCriticalProperties.pseudoReducedTemperature(T, tpcCorrected);
double ppr = GasPseudoCriticalProperties.pseudoReducedPressure(P, ppcCorrected);
```

### Full Workflow: Sour Gas Z-Factor via Pseudocritical Properties

```java
// Gas properties
double gammaG = 0.80;
double yH2S = 0.10;
double yCO2 = 0.05;

// 1. Pseudocritical properties (Sutton)
double tpc = GasPseudoCriticalProperties.pseudoCriticalTemperatureSutton(gammaG);
double ppc = GasPseudoCriticalProperties.pseudoCriticalPressureSutton(gammaG);

// 2. Acid gas correction (Wichert-Aziz)
double[] corrected = GasPseudoCriticalProperties.wichertAzizCorrection(
    tpc, ppc, yH2S, yCO2);

// 3. Reduced properties
double TF = 200.0; // °F
double P = 3000.0; // psia
double Tr = (TF + 459.67) / corrected[0];
double Pr = P / corrected[1];

// 4. Now use Tr, Pr in any Z-factor correlation
//    (e.g., Hall-Yarborough, Dranchuk-Abu-Kassem)
```

## Related Documentation

- [PVT Simulation Package](README.md)
- [Black Oil Correlations](blackoil_pvt_export.md)
- [PVT Lab Tests](pvt_lab_tests.md)
