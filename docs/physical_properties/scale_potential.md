# Scale Potential Calculation in NeqSim

## Introduction

Scale formation is a critical challenge in oil and gas production, water treatment, and geothermal systems. When water becomes supersaturated with certain minerals, precipitation (scaling) can occur on equipment surfaces, leading to reduced flow, equipment damage, and costly interventions.

NeqSim provides tools to predict **scale potential** by calculating the **saturation ratio (SR)** for various mineral salts. This document covers the theory, implementation, usage, and best practices for scale potential calculations.

## Theory

### Saturation Ratio

The **saturation ratio** (also called saturation index or relative solubility) is the key parameter for assessing scale potential:

$$SR = \frac{IAP}{K_{sp}}$$

Where:
- **IAP** = Ion Activity Product
- **K_sp** = Solubility Product Constant (thermodynamic equilibrium constant)

### Interpretation

| SR Value | State | Meaning |
|----------|-------|---------|
| SR < 1 | Undersaturated | Salt will dissolve; no scaling risk |
| SR = 1 | Saturated | Equilibrium; at solubility limit |
| SR > 1 | Supersaturated | Precipitation thermodynamically favored |
| SR >> 1 | Highly supersaturated | High scaling risk |

**Note:** SR > 1 indicates thermodynamic driving force for precipitation, but kinetics determine actual precipitation rate.

### Ion Activity Product (IAP)

For a salt dissociating as:

$$M_{\nu_+}A_{\nu_-} \rightleftharpoons \nu_+ M^{z+} + \nu_- A^{z-}$$

The Ion Activity Product is:

$$IAP = a_{M^{z+}}^{\nu_+} \cdot a_{A^{z-}}^{\nu_-} = (\gamma_+ m_+)^{\nu_+} \cdot (\gamma_- m_-)^{\nu_-}$$

Where:
- $a$ = ion activity
- $\gamma$ = activity coefficient
- $m$ = molality (mol/kg water)
- $\nu$ = stoichiometric coefficient

### Solubility Product (K_sp)

The solubility product is the equilibrium constant for salt dissolution. In NeqSim, K_sp is calculated from temperature-dependent correlations:

$$\ln(K_{sp}) = \frac{A}{T} + B + C \cdot \ln(T) + D \cdot T + \frac{E}{T^2}$$

Where T is temperature in Kelvin. The coefficients A, B, C, D, E are stored in the database.

## Common Scales in Oil & Gas

### Carbonate Scales

| Mineral | Formula | K_sp (25°C) | Conditions |
|---------|---------|-------------|------------|
| Calcite | CaCO3 | 10^-8.48 | Most common; pressure/CO2 dependent |
| Siderite | FeCO3 | 10^-10.89 | Iron-rich waters, CO2 systems |
| Magnesite | MgCO3 | 10^-7.46 | High Mg waters |
| Strontianite | SrCO3 | 10^-9.27 | Associated with barite |

### Sulfate Scales

| Mineral | Formula | K_sp (25°C) | Conditions |
|---------|---------|-------------|------------|
| Gypsum | CaSO4·2H2O | 10^-4.58 | T < 40°C, lower salinity |
| Anhydrite | CaSO4 | 10^-4.36 | T > 40°C, high salinity |
| Barite | BaSO4 | 10^-9.97 | Seawater mixing; very insoluble |
| Celestite | SrSO4 | 10^-6.63 | Associated with barite |

### Chloride Scales

| Mineral | Formula | K_sp (25°C) | Conditions |
|---------|---------|-------------|------------|
| Halite | NaCl | 10^+1.58 | Highly soluble; evaporative systems |
| Sylvite | KCl | 10^+0.85 | Very soluble |

### Sulfide Scales

| Mineral | Formula | K_sp (25°C) | Conditions |
|---------|---------|-------------|------------|
| Iron sulfide | FeS | varies | Sour (H2S) systems |

## Implementation in NeqSim

### Core Classes

1. **CheckScalePotential** (`neqsim.thermodynamicoperations.flashops.saturationops.CheckScalePotential`)
   - Main calculation class
   - Reads salt data from COMPSALT database
   - Calculates SR for all applicable salts

2. **COMPSALT.csv** (`src/main/resources/data/COMPSALT.csv`)
   - Database of salt properties
   - Contains ion pairs, stoichiometry, and K_sp correlation coefficients

### Database Structure

The COMPSALT.csv file contains:

| Column | Description | Example |
|--------|-------------|---------|
| ID | Unique identifier | 5 |
| SaltName | Mineral name | CaSO4_A |
| ion1 | Cation species | Ca++ |
| ion2 | Anion species | SO4-- |
| stoc1 | Cation stoichiometry | 1.0 |
| stoc2 | Anion stoichiometry | 1.0 |
| Kspwater | Coefficient A | -19966.8 |
| Kspwater2 | Coefficient B | 454.86 |
| Kspwater3 | Coefficient C | -69.84 |
| Kspwater4 | Coefficient D | 0.0 |
| Kspwater5 | Coefficient E | 0.0 |

### Supported Salts

NeqSim currently supports 21 salts:

| Category | Salts |
|----------|-------|
| Chlorides | NaCl, KCl, HgCl2 |
| Carbonates | CaCO3, FeCO3, MgCO3, BaCO3, SrCO3, Na2CO3, K2CO3 |
| Bicarbonates | NaHCO3, KHCO3, Mg(HCO3)2 |
| Sulfates | CaSO4_A, CaSO4_G, BaSO4, SrSO4 |
| Hydroxides | Mg(OH)2 |
| Sulfides | FeS |
| Complex | Hydromagnesite |

## Usage

### Basic Example

```java
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Create electrolyte system at 25°C, 1 bar
SystemInterface system = new SystemElectrolyteCPAstatoil(298.15, 1.0);

// Add water (1 kg basis)
system.addComponent("water", 1.0, "kg/sec");

// Add ions (molality = mol/kg water)
system.addComponent("Ca++", 0.01);   // 10 mmol/kg calcium
system.addComponent("SO4--", 0.01);  // 10 mmol/kg sulfate
system.addComponent("Na+", 0.5);     // 500 mmol/kg sodium
system.addComponent("Cl-", 0.5);     // 500 mmol/kg chloride

// Initialize system
system.chemicalReactionInit();
system.createDatabase(true);
system.setMixingRule(10);  // Electrolyte mixing rule

// Run flash calculation
ThermodynamicOperations ops = new ThermodynamicOperations(system);
ops.TPflash();
system.init(1);

// Calculate scale potential
int aqueousPhase = system.getPhaseNumberOfPhase("aqueous");
ops.checkScalePotential(aqueousPhase);

// Get results
String[][] results = ops.getResultTable();

// Print results (skip header row)
System.out.println("Salt\t\tSaturation Ratio");
for (int i = 1; i < results.length && results[i][0] != null && !results[i][0].isEmpty(); i++) {
    System.out.println(results[i][0] + "\t\t" + results[i][1]);
}
```

### Example Output

```
Salt            Saturation Ratio
NaCl            0.00607
CaSO4_A         1.864
CaSO4_G         3.115
```

### Interpreting Results

From the output above:
- **NaCl (SR = 0.006)**: Far from saturation, no halite scaling risk
- **CaSO4_A (SR = 1.86)**: Supersaturated, anhydrite may precipitate
- **CaSO4_G (SR = 3.12)**: Supersaturated, gypsum likely to precipitate

### Temperature Effects

```java
// Check scale potential at different temperatures
double[] temperatures = {283.15, 298.15, 323.15, 348.15, 373.15}; // 10-100°C

for (double T : temperatures) {
    SystemInterface sys = new SystemElectrolyteCPAstatoil(T, 1.0);
    sys.addComponent("water", 1.0, "kg/sec");
    sys.addComponent("Ca++", 0.01);
    sys.addComponent("SO4--", 0.01);
    
    sys.chemicalReactionInit();
    sys.createDatabase(true);
    sys.setMixingRule(10);
    
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();
    sys.init(1);
    
    int aqPhase = sys.getPhaseNumberOfPhase("aqueous");
    ops.checkScalePotential(aqPhase);
    
    // Extract CaSO4 results...
}
```

### Oilfield Brine Example

```java
// Typical formation water composition
SystemInterface brine = new SystemElectrolyteCPAstatoil(353.15, 100.0); // 80°C, 100 bar

brine.addComponent("water", 1.0, "kg/sec");
brine.addComponent("Na+", 2.5);      // 2500 mmol/kg
brine.addComponent("Cl-", 2.8);      // 2800 mmol/kg
brine.addComponent("Ca++", 0.025);   // 25 mmol/kg
brine.addComponent("Mg++", 0.015);   // 15 mmol/kg
brine.addComponent("Ba++", 0.0001);  // 0.1 mmol/kg
brine.addComponent("Sr++", 0.002);   // 2 mmol/kg
brine.addComponent("SO4--", 0.001);  // 1 mmol/kg (low - formation water)
brine.addComponent("HCO3-", 0.005);  // 5 mmol/kg

brine.chemicalReactionInit();
brine.createDatabase(true);
brine.setMixingRule(10);

ThermodynamicOperations ops = new ThermodynamicOperations(brine);
ops.TPflash();
brine.init(1);

int aqPhase = brine.getPhaseNumberOfPhase("aqueous");
ops.checkScalePotential(aqPhase);
```

## Algorithm Details

### Calculation Flow

1. **Load salt database** - Read COMPSALT.csv
2. **For each salt in database:**
   - Check if both ions are present in the system
   - Calculate K_sp from temperature correlation
   - Get ion mole fractions and convert to molality
   - Get activity coefficients from thermodynamic model
   - Calculate IAP = (γ₁·m₁)^ν₁ · (γ₂·m₂)^ν₂
   - Calculate SR = IAP / K_sp
3. **Return result table** with salt names and SR values

### Special Cases

#### NaCl
NaCl uses a polynomial correlation for K_sp instead of the standard ln(K_sp) form:
```java
ksp = -814.18 + 7.4685*T - 2.3262e-2*T² + 3.0536e-5*T³ - 1.4573e-8*T⁴
```

#### FeS (Iron Sulfide)
FeS calculation includes pH correction:
```java
ksp *= [H3O+]  // Accounts for H2S dissociation equilibrium
```

#### Hydromagnesite
Complex mineral 3MgCO₃·Mg(OH)₂·3H₂O requires special handling with water and hydroxide activities.

#### MEG Systems
When MEG (monoethylene glycol) is present, the algorithm temporarily replaces MEG with water for the calculation to maintain consistent molality basis.

## Accuracy and Limitations

### Concentration Ranges

| Concentration | Expected Accuracy | Notes |
|---------------|-------------------|-------|
| Dilute (< 0.1 mol/kg) | ±10-20% | Best accuracy range |
| Moderate (0.1-1.0 mol/kg) | ±20-50% | Good for most applications |
| Concentrated (> 1 mol/kg) | > 50% | Activity coefficients less accurate |

### Temperature Ranges

| Temperature | K_sp Accuracy | Notes |
|-------------|---------------|-------|
| 0-50°C | Good | Well-calibrated correlations |
| 50-100°C | Moderate | Some extrapolation |
| > 100°C | Variable | Validate against literature |

### Known Limitations

1. **Activity Coefficient Asymmetry**
   - At high ionic strength (> 1 mol/kg), the electrolyte-CPA model may give asymmetric activity coefficients for cations and anions
   - Example: γ(Na+) = 0.29, γ(Cl-) = 9.27 at NaCl saturation (6.15 mol/kg)
   - Literature expects γ± ≈ 0.65-0.70

2. **Pressure Effects**
   - Current correlations are for atmospheric pressure
   - High-pressure corrections not implemented
   - For deep-sea/downhole: consider pressure correction factor

3. **Complex Brines**
   - Multi-component brines may cause matrix singularity in chemical equilibrium solver
   - Simplify to dominant ions if errors occur

4. **Mixed-Solvent Systems**
   - MEG/water mixtures: algorithm applies water-based K_sp
   - Accuracy decreases at high MEG concentrations

## K_sp Correlation Sources

The K_sp correlations in COMPSALT.csv are derived from:

| Source | Salts | Reference |
|--------|-------|-----------|
| WATEQ4F | Most minerals | Nordstrom & Munoz (1990) |
| Langmuir | CaSO4_A, MgCO3 | Langmuir (1997) |
| Plummer & Busenberg | CaCO3 | Geochim. Cosmochim. Acta 46:1011 (1982) |
| NIST | KCl | Standard Reference Database 46 |
| Pitzer | NaCl | Activity Coefficients in Electrolyte Solutions (1991) |

## Best Practices

### 1. Use Appropriate Concentration Units

Always specify ion concentrations in **molality** (mol/kg water) for consistency:

```java
// Correct: explicit molality
system.addComponent("Ca++", 0.01);  // 0.01 mol/kg water

// For mass-based input, calculate molality
double CaPpm = 400;  // mg/L
double CaMolality = (CaPpm / 1000.0) / 40.08;  // Ca molar mass = 40.08
system.addComponent("Ca++", CaMolality);
```

### 2. Maintain Electroneutrality

Total positive charges should equal total negative charges:

```java
// Check charge balance
double positiveCharge = 2*[Ca++] + 2*[Mg++] + [Na+] + [K+];
double negativeCharge = [Cl-] + 2*[SO4--] + [HCO3-] + 2*[CO3--];
// These should be approximately equal
```

### 3. Include Major Ions

Include all major ions even if not interested in their scales:

```java
// Even if only checking CaSO4, include NaCl for ionic strength
system.addComponent("Na+", 0.5);
system.addComponent("Cl-", 0.5);
system.addComponent("Ca++", 0.01);
system.addComponent("SO4--", 0.01);
```

### 4. Validate at Known Conditions

Test the model at known saturation conditions:

```java
// At NaCl saturation (6.15 mol/kg), SR should be ~1.0
// At BaSO4 saturation (~1e-5 mol/kg), SR should be ~1.0
```

### 5. Consider Kinetics

SR > 1 means precipitation is **thermodynamically** possible, not that it will occur immediately:
- **Barite (BaSO4)**: Precipitates rapidly even at SR slightly > 1
- **Calcite (CaCO3)**: May require SR > 2-3 for rapid precipitation
- **Gypsum (CaSO4)**: Moderate kinetics

## Troubleshooting

### Common Issues

| Issue | Likely Cause | Solution |
|-------|--------------|----------|
| `Matrix is singular` error | Complex multi-ion system | Simplify to major ions |
| Very high SR (> 10^10) | Incorrect K_sp correlation | Check database values |
| SR always 0 | Ions not found in database | Check ion names (e.g., "Ca++" not "Ca2+") |
| No results returned | Ions not in system | Verify addComponent calls |
| Negative SR | Numerical error | Check activity coefficient calculation |

### Ion Name Convention

Use NeqSim ion naming convention:

| Ion | NeqSim Name | Common Alternatives (don't use) |
|-----|-------------|--------------------------------|
| Calcium | `Ca++` | Ca2+, Ca(2+) |
| Magnesium | `Mg++` | Mg2+, Mg(2+) |
| Sodium | `Na+` | Na(+), Na1+ |
| Barium | `Ba++` | Ba2+, Ba(2+) |
| Sulfate | `SO4--` | SO4(2-), SO42- |
| Carbonate | `CO3--` | CO3(2-), CO32- |
| Bicarbonate | `HCO3-` | HCO3(-), HCO31- |
| Chloride | `Cl-` | Cl(-), Cl1- |

## Verification Results

### K_sp Correlation Accuracy at 25°C (298.15 K)

All K_sp correlations have been verified against literature values:

| Salt | Calculated log₁₀(K_sp) | Literature log₁₀(K_sp) | Error | Source |
|------|------------------------|------------------------|-------|--------|
| CaSO4_A (Anhydrite) | -4.356 | -4.360 | 0.004 | Langmuir 1997 |
| CaSO4_G (Gypsum) | -4.581 | -4.580 | 0.001 | WATEQ4F |
| SrSO4 (Celestite) | -6.631 | -6.630 | 0.001 | WATEQ4F |
| BaSO4 (Barite) | -9.970 | -9.970 | 0.000 | WATEQ4F |
| KCl (Sylvite) | 0.851 | 0.850 | 0.001 | NIST |
| NaCl (Halite) | 1.582 | 1.580 | 0.002 | Pitzer 1991 |
| MgCO3 (Magnesite) | -7.491 | -7.460 | 0.031 | Langmuir 1997 |
| CaCO3 (Calcite) | -8.531 | -8.480 | 0.051 | Plummer 1982 |
| FeCO3 (Siderite) | -10.89 | -10.89 | 0.000 | WATEQ4F |
| Mg(OH)₂ (Brucite) | -11.16 | -11.16 | 0.000 | WATEQ4F |

**All errors are < 0.1 in log₁₀(K_sp), corresponding to < 25% error in K_sp.**

### Saturation Ratio Validation (BaSO4)

BaSO4 was tested at moderate concentrations where the electrolyte model is most accurate:

| Ba²⁺ Molality (mol/kg) | SO₄²⁻ Molality (mol/kg) | Calculated SR | Expected SR | Accuracy |
|------------------------|------------------------|---------------|-------------|----------|
| 5.0×10⁻⁶ | 5.0×10⁻⁶ | 0.21 | ~0.25 | ✓ Good |
| 1.0×10⁻⁵ | 1.0×10⁻⁵ | 0.85 | ~1.0 | ✓ Good |
| 2.0×10⁻⁵ | 2.0×10⁻⁵ | 3.38 | ~4.0 | ✓ Good |
| 5.0×10⁻⁵ | 5.0×10⁻⁵ | 21.1 | ~25 | ✓ Good |
| 1.0×10⁻⁴ | 1.0×10⁻⁴ | 84.5 | ~100 | ✓ Good |

The SR scales correctly with concentration squared (as expected for a 1:1 salt where SR ∝ [Ba²⁺][SO₄²⁻]).

### Activity Coefficient Verification

At moderate ionic strength (< 0.1 mol/kg), activity coefficients are accurate:

| System | Ionic Strength | Calculated γ± | Literature γ± | Error |
|--------|----------------|---------------|---------------|-------|
| NaCl 0.1 mol/kg | 0.1 | 0.778 | 0.778 | < 1% |
| CaCl₂ 0.01 mol/kg | 0.03 | 0.732 | 0.729 | < 1% |
| BaSO4 at saturation | ~2×10⁻⁵ | 0.92 | ~0.90 | ~2% |

**Note:** At high ionic strength (> 1 mol/kg), activity coefficients become less accurate due to model limitations.

## Recent Improvements (2025)

### Fixed K_sp Correlations

Several salt correlations were corrected:

| Salt | Previous Error | Status |
|------|----------------|--------|
| CaSO4_A (Anhydrite) | 56 orders of magnitude | ✅ Fixed |
| SrSO4 (Celestite) | 7 orders of magnitude | ✅ Fixed |
| KCl (Sylvite) | 2.6 orders of magnitude | ✅ Fixed |
| MgCO3 (Magnesite) | 2.3 orders of magnitude | ✅ Fixed |

### Bug Fixes

- **Array size**: Increased result table from 10 to 25 entries to handle all salts

## References

1. Nordstrom, D.K. and Munoz, J.L. (1990). *Geochemical Thermodynamics*, 2nd ed. Blackwell Scientific.

2. Langmuir, D. (1997). *Aqueous Environmental Geochemistry*. Prentice Hall.

3. Plummer, L.N. and Busenberg, E. (1982). "The solubilities of calcite, aragonite and vaterite in CO2-H2O solutions." *Geochimica et Cosmochimica Acta* 46:1011-1040.

4. Pitzer, K.S. (1991). *Activity Coefficients in Electrolyte Solutions*, 2nd ed. CRC Press.

5. NIST Standard Reference Database 46 - Critically Selected Stability Constants of Metal Complexes.

6. Appelo, C.A.J. and Postma, D. (2005). *Geochemistry, Groundwater and Pollution*, 2nd ed. Balkema.

## See Also

- [Electrolyte CPA Model](../thermo/ElectrolyteCPAModel.md)
- [Chemical Equilibrium](../chemicalreactions/README.md)
