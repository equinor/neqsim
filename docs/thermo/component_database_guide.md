# Pure Component Parameters Database (COMP)

This guide provides detailed documentation of the COMP database, which stores pure component parameters used by NeqSim's thermodynamic models. Understanding these parameters is essential for model selection, debugging, and extending NeqSim with new components.

## Table of Contents
- [Database Overview](#database-overview)
- [Database Location and Format](#database-location-and-format)
- [Parameter Categories](#parameter-categories)
  - [Basic Properties](#basic-properties)
  - [Critical Properties](#critical-properties)
  - [Vapor Pressure Parameters](#vapor-pressure-parameters)
  - [Ideal Gas Heat Capacity](#ideal-gas-heat-capacity)
  - [Liquid Phase Properties](#liquid-phase-properties)
  - [Transport Properties](#transport-properties)
  - [Equation of State Parameters](#equation-of-state-parameters)
  - [Association Parameters (CPA/SAFT)](#association-parameters-cpasaft)
  - [Hydrate Parameters](#hydrate-parameters)
  - [Thermodynamic Reference Data](#thermodynamic-reference-data)
- [Parameter Reference Table](#parameter-reference-table)
- [Link to Thermodynamic Models](#link-to-thermodynamic-models)
- [Component Types](#component-types)
- [Accessing Parameters in Code](#accessing-parameters-in-code)
- [Adding Custom Components](#adding-custom-components)
- [Related Database Tables](#related-database-tables)

---

## Database Overview

The **COMP** table is the primary pure component property database in NeqSim. It contains over 150 parameters per component, organized into functional groups that support different thermodynamic models and property calculations.

Key characteristics:
- **~260 components** in the standard database
- **CSV format** for easy maintenance and version control
- **Loaded at runtime** into an in-memory H2 database
- **Extensible** via temporary tables or database modification

---

## Database Location and Format

| Item | Value |
|------|-------|
| **File Path** | `src/main/resources/data/COMP.csv` |
| **Runtime Path** | `data/COMP.csv` (in JAR) |
| **Format** | CSV with header row |
| **Encoding** | UTF-8 |
| **Primary Key** | ID (integer) |
| **Lookup Key** | NAME (string, case-sensitive) |

---

## Parameter Categories

### Basic Properties

| Column | Description | Unit | Model Usage |
|--------|-------------|------|-------------|
| `ID` | Unique component identifier | - | Internal indexing |
| `NAME` | Component name for lookup | - | `addComponent("methane", ...)` |
| `CASnumber` | CAS Registry Number | - | Component identification |
| `COMPTYPE` | Component type classification | - | Model selection (see [Component Types](#component-types)) |
| `COMPINDEX` | Component index in database | - | Internal ordering |
| `FORMULA` | Chemical formula | - | Element calculations |
| `MOLARMASS` | Molar mass | g/mol | All models (stored internally as kg/mol) |

### Critical Properties

These parameters are fundamental to all cubic equations of state (SRK, PR, etc.).

| Column | Description | Unit | Model Usage |
|--------|-------------|------|-------------|
| `TC` | Critical temperature | °C | Converted to K internally: $T_c = T_{C,db} + 273.15$ |
| `PC` | Critical pressure | bara | SRK, PR, CPA EoS parameter a and b |
| `ACSFACT` | Acentric factor (ω) | - | Alpha function: $m = f(\omega)$ |
| `CRITVOL` | Critical molar volume | cm³/mol | Critical compressibility: $Z_c = \frac{P_c V_c}{R T_c}$ |
| `NORMBOIL` | Normal boiling point | °C | Stored as K internally |

**Model linkage:**
- **SRK EoS**: $a = 0.42748 \frac{R^2 T_c^2}{P_c}$, $b = 0.08664 \frac{R T_c}{P_c}$
- **PR EoS**: $a = 0.45724 \frac{R^2 T_c^2}{P_c}$, $b = 0.07780 \frac{R T_c}{P_c}$
- Alpha function: $\alpha(T) = [1 + m(1-\sqrt{T_r})]^2$ where $m = f(\omega)$

### Vapor Pressure Parameters

Parameters for Antoine-type vapor pressure correlations.

| Column | Description | Unit | Model Usage |
|--------|-------------|------|-------------|
| `AntoineVapPresLiqType` | Equation type | - | `pow10`, `log`, `exp`, `loglog` |
| `ANTOINEA` | Antoine A coefficient | - | Vapor pressure calculation |
| `ANTOINEB` | Antoine B coefficient | - | Vapor pressure calculation |
| `ANTOINEC` | Antoine C coefficient | - | Vapor pressure calculation |
| `ANTOINED` | Antoine D coefficient | - | Extended Antoine |
| `ANTOINEE` | Antoine E coefficient | - | Extended Antoine |
| `ANTOINESolidA` | Solid vapor pressure A | - | Sublimation pressure |
| `ANTOINESolidB` | Solid vapor pressure B | - | Sublimation pressure |
| `ANTOINESolidC` | Solid vapor pressure C | - | Sublimation pressure |

**Antoine equation forms:**
- `pow10`: $\log_{10}(P_{sat}) = A - \frac{B}{T + C}$ (P in mmHg, T in °C)
- `log`: $\ln(P_{sat}) = A + \frac{B}{T} + C \ln(T) + D T^E$
- `exp`: $P_{sat} = \exp(A - \frac{B}{T + C})$

### Ideal Gas Heat Capacity

Polynomial coefficients for ideal gas heat capacity: $C_p^{ig} = A + BT + CT^2 + DT^3 + ET^4$

| Column | Description | Unit |
|--------|-------------|------|
| `CPA` | Coefficient A | J/(mol·K) |
| `CPB` | Coefficient B | J/(mol·K²) |
| `CPC` | Coefficient C | J/(mol·K³) |
| `CPD` | Coefficient D | J/(mol·K⁴) |
| `CPE` | Coefficient E | J/(mol·K⁵) |
| `CPsolid1-5` | Solid phase Cp coefficients | J/(mol·K) |
| `CPliquid1-5` | Liquid phase Cp coefficients | J/(mol·K) |

**Usage:** Enthalpy, entropy, and Gibbs energy departure functions for all EoS models.

### Liquid Phase Properties

| Column | Description | Unit | Model Usage |
|--------|-------------|------|-------------|
| `LIQDENS` | Liquid density at standard conditions | g/cm³ | Density correlations |
| `RACKETZ` | Rackett compressibility factor | - | Rackett liquid density: $V = V_c Z_{RA}^{[1+(1-T_r)^{2/7}]}$ |
| `racketZCPA` | Rackett Z for CPA model | - | CPA volume correction |
| `volcorrSRK_T` | SRK volume translation | - | Péneloux correction: $V_{corr} = V_{EoS} - c$ |
| `volcorrCPA_T` | CPA volume translation | - | CPA Péneloux correction |
| `STDDENS` | Standard density | g/cm³ | Reference conditions |
| `LIQUIDDENSITYCOEFS1-5` | Liquid density correlation coefficients | - | Temperature-dependent density |

### Transport Properties

| Column | Description | Unit | Model Usage |
|--------|-------------|------|-------------|
| `DIPOLEMOMENT` | Dipole moment | Debye | Polar corrections |
| `VISCFACT` | Viscosity correction factor | - | Corresponding states |
| `LIQVISCMODEL` | Liquid viscosity model type | - | Model selection (1-4) |
| `LIQVISC1-4` | Liquid viscosity parameters | - | Andrade equation: $\ln(\eta) = A + B/T + C\ln(T) + DT$ |
| `LIQUIDCONDUCTIVITY1-3` | Liquid thermal conductivity | - | $k = A + BT + CT^2$ |
| `PARACHOR` | Parachor | - | Surface tension: $\sigma^{1/4} = P[\rho_L - \rho_V]$ |
| `PARACHOR_CPA` | Parachor for CPA model | - | CPA surface tension |
| `criticalViscosity` | Critical viscosity | Pa·s | Transport correlations |

### Equation of State Parameters

#### Attractive Term Parameters

| Column | Description | Unit | Model Usage |
|--------|-------------|------|-------------|
| `PVMODEL` | PV model type | - | `Classic` for standard EoS |
| `MC1`, `MC2`, `MC3` | Mathias-Copeman parameters (SRK) | - | Enhanced alpha function |
| `MCPR1`, `MCPR2`, `MCPR3` | Mathias-Copeman parameters (PR) | - | PR alpha function |
| `TwuCoon1-3` | Twu-Coon alpha function parameters | - | Twu-Coon attractive term |
| `SCHWARTZENTRUBER1-3` | Schwartzentruber parameters | - | Schwartzentruber EoS |
| `MC1Solid-MC3Solid` | Solid phase Mathias-Copeman | - | Solid fugacity |

**Mathias-Copeman alpha function:**
$$\alpha = [1 + c_1(1-\sqrt{T_r}) + c_2(1-\sqrt{T_r})^2 + c_3(1-\sqrt{T_r})^3]^2$$

#### Lennard-Jones Parameters

| Column | Description | Unit | Model Usage |
|--------|-------------|------|-------------|
| `LJDIAMETER` | LJ molecular diameter | Å | Gas viscosity, diffusion |
| `LJEPS` | LJ energy parameter | K | ε/k_B |
| `SphericalCoreRadius` | Hard-core radius | - | LJ potential |
| `LJDIAMETERHYDRATE` | LJ diameter for hydrates | Å | Hydrate equilibrium |
| `LJEPSHYDRATE` | LJ energy for hydrates | K | Hydrate cage interaction |

### Association Parameters (CPA/SAFT)

Parameters for Cubic-Plus-Association (CPA) and PC-SAFT models.

| Column | Description | Unit | Model Usage |
|--------|-------------|------|-------------|
| `associationsites` | Number of association sites | - | 0, 1, 2, 3, or 4 |
| `associationscheme` | Association scheme | - | `0`, `1A`, `2A`, `2B`, `3B`, `4C` |
| `associationenergy` | Association energy (ε^AB) | J/mol | CPA association term |
| `associationboundingvolume_SRK` | Association volume (β) for SRK-CPA | - | SRK-CPA |
| `associationboundingvolume_PR` | Association volume (β) for PR-CPA | - | PR-CPA |
| `aCPA_SRK` | CPA a parameter (SRK base) | Pa·m⁶/mol² | SRK-CPA |
| `bCPA_SRK` | CPA b parameter (SRK base) | m³/mol | SRK-CPA |
| `mCPA_SRK` | CPA m parameter (SRK base) | - | SRK-CPA |
| `aCPA_PR` | CPA a parameter (PR base) | Pa·m⁶/mol² | PR-CPA |
| `bCPA_PR` | CPA b parameter (PR base) | m³/mol | PR-CPA |
| `mCPA_PR` | CPA m parameter (PR base) | - | PR-CPA |

#### PC-SAFT Parameters

| Column | Description | Unit | Model Usage |
|--------|-------------|------|-------------|
| `mSAFT` | Number of segments | - | Chain length |
| `sigmaSAFT` | Segment diameter | Å | Hard-sphere term |
| `epsikSAFT` | Segment energy | K | ε/k_B |
| `associationboundingvolume_PCSAFT` | Association volume | - | PC-SAFT association |
| `associationenergy_PCSAFT` | Association energy | K | PC-SAFT association |

**Association schemes:**
| Scheme | Sites | Example Molecules |
|--------|-------|-------------------|
| `0` | 0 | Non-associating (hydrocarbons) |
| `1A` | 1 | HCl, aromatic compounds |
| `2A` | 2 | CO₂ (electron donor/acceptor) |
| `2B` | 2 | Alcohols (1 proton donor, 1 acceptor) |
| `3B` | 3 | Amines |
| `4C` | 4 | Water, glycols (2 donors, 2 acceptors) |

### Hydrate Parameters

Parameters for gas hydrate equilibrium calculations.

| Column | Description | Unit | Model Usage |
|--------|-------------|------|-------------|
| `HydrateFormer` | Hydrate-forming capability | - | `yes` or `no` |
| `HydrateA1Small`, `HydrateB1Small` | Type I small cage (512) | - | Langmuir constants |
| `HydrateA1Large`, `HydrateB1Large` | Type I large cage (51262) | - | Langmuir constants |
| `HydrateA2Small`, `HydrateB2Small` | Type II small cage (512) | - | Langmuir constants |
| `HydrateA2Large`, `HydrateB2Large` | Type II large cage (51264) | - | Langmuir constants |
| `A1_smallGF-B2_largeGF` | Graffis parameters | - | Alternative parameterization |
| `SphericalCoreRadiusHYDRATE` | Core radius for hydrates | - | Cavity occupation |

### Thermodynamic Reference Data

| Column | Description | Unit | Model Usage |
|--------|-------------|------|-------------|
| `Href` | Reference enthalpy | J/mol | Enthalpy calculations |
| `GIBBSENERGYOFFORMATION` | Gibbs energy of formation | J/mol | Chemical equilibrium |
| `ENTHALPYOFFORMATION` | Standard enthalpy of formation | J/mol | Reaction thermodynamics |
| `ABSOLUTEENTROPY` | Absolute entropy | J/(mol·K) | Entropy calculations |
| `HEATOFFUSION` | Heat of fusion | J/mol | Solid-liquid equilibrium |
| `Hsub` | Heat of sublimation | J/mol | Solid-vapor equilibrium |
| `TRIPLEPOINTTEMPERATURE` | Triple point temperature | K | Phase boundaries |
| `TRIPLEPOINTPRESSURE` | Triple point pressure | bar | Phase boundaries |
| `TRIPLEPOINTDENSITY` | Triple point density | kg/m³ | Reference state |
| `MELTINGPOINTTEMPERATURE` | Melting point | K | Solid calculations |

### Ionic and Electrolyte Parameters

| Column | Description | Unit | Model Usage |
|--------|-------------|------|-------------|
| `IONICCHARGE` | Ionic charge | - | Electrolyte models |
| `REFERENCESTATETYPE` | Reference state | - | `solvent` or `solute` |
| `DIELECTRICPARAMETER1-5` | Dielectric parameters | - | Electrolyte activity |
| `DeshMatIonicDiameter` | Debye-Hückel diameter | Å | Electrolyte models |
| `calcActivity` | Activity calculation flag | - | 0 or 1 |

### Henry's Law Parameters

| Column | Description | Unit |
|--------|-------------|------|
| `HenryCoef1` | Henry constant A | - |
| `HenryCoef2` | Henry constant B | - |
| `HenryCoef3` | Henry constant C | - |
| `HenryCoef4` | Henry constant D | - |

**Henry's law correlation:**
$$\ln(H) = A + \frac{B}{T} + C\ln(T) + DT$$

### Solid Phase Parameters

| Column | Description | Unit |
|--------|-------------|------|
| `SOLIDDENSITYCOEFS1-5` | Solid density coefficients | - |
| `HEATOFVAPORIZATIONCOEFS1-5` | Heat of vaporization coefficients | - |
| `waxformer` | Wax-forming component | - |

---

## Parameter Reference Table

Complete parameter list with units and typical values:

| Parameter | Unit | Example (methane) | Example (water) |
|-----------|------|-------------------|-----------------|
| `MOLARMASS` | g/mol | 16.043 | 18.015 |
| `TC` | °C | -82.59 | 374.15 |
| `PC` | bara | 45.99 | 220.89 |
| `ACSFACT` | - | 0.0115 | 0.344 |
| `CRITVOL` | cm³/mol | 99.0 | 56.0 |
| `NORMBOIL` | °C | -161.55 | 100.0 |
| `LIQDENS` | g/cm³ | 0.422 | 0.999 |
| `RACKETZ` | - | 0.0 | 0.0 |
| `DIPOLEMOMENT` | Debye | 0.0 | 1.8 |
| `associationsites` | - | 0 | 4 |
| `HydrateFormer` | - | yes | no |

---

## Link to Thermodynamic Models

### How Parameters Feed into Models

```
┌─────────────────────────────────────────────────────────────────┐
│                      COMP Database                              │
├─────────────────────────────────────────────────────────────────┤
│  TC, PC, ACSFACT  ──────────────> Cubic EoS (SRK, PR)          │
│  MC1, MC2, MC3    ──────────────> Mathias-Copeman α(T)         │
│  TwuCoon1-3       ──────────────> Twu-Coon α(T)                │
│  aCPA, bCPA, mCPA ──────────────> CPA EoS                      │
│  associationsites ──────────────> CPA/SAFT Association         │
│  mSAFT, σSAFT, εSAFT ──────────> PC-SAFT                       │
│  UNIFAC groups    ──────────────> Activity models              │
│  LJDIAMETER, LJEPS ─────────────> Transport properties         │
│  HydrateA/B params ─────────────> Hydrate equilibrium          │
│  CPA, CPB, CPC... ──────────────> Enthalpy/Entropy             │
│  ANTOINEA-E       ──────────────> Vapor pressure               │
└─────────────────────────────────────────────────────────────────┘
```

### Model-Parameter Mapping

| Model Class | Key Parameters |
|-------------|----------------|
| `SystemSrkEos` | TC, PC, ACSFACT, MC1-3, RACKETZ |
| `SystemPrEos` | TC, PC, ACSFACT, MCPR1-3 |
| `SystemSrkCPA` | aCPA_SRK, bCPA_SRK, mCPA_SRK, associationsites, associationenergy, associationboundingvolume_SRK |
| `SystemPrCPA` | aCPA_PR, bCPA_PR, mCPA_PR, associationboundingvolume_PR |
| `SystemPCSAFT` | mSAFT, sigmaSAFT, epsikSAFT, associationboundingvolume_PCSAFT |
| `SystemGERG2008Eos` | Uses internal GERG parameters, but TC/PC for initialization |
| `SystemUNIFAC` | TC, PC (for vapor), UNIFAC groups from UNIFACcomp table |

---

## Component Types

The `COMPTYPE` field classifies components for model selection:

| Type | Description | Examples |
|------|-------------|----------|
| `HC` | Hydrocarbon | methane, ethane, propane, benzene |
| `inert` | Inert gas | nitrogen, CO2, oxygen, argon |
| `ion` | Ionic species | Na+, Cl-, HCO3-, Ca++ |
| `amine` | Amine compounds | MDEA, MEA, DEA |
| `alcohol` | Alcohols | methanol, ethanol |
| `glycol` | Glycol compounds | MEG, DEG, TEG |
| `ice` | Ice/solid water | ice |
| `TBP` | TBP pseudo-component | Generated from characterization |
| `plus` | Plus fraction | C7+, C10+, etc. |

---

## Accessing Parameters in Code

### Reading Component Properties

```java
// Create a system and access component properties
SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
fluid.addComponent("methane", 1.0);
fluid.init(0);

// Access pure component parameters
ComponentInterface comp = fluid.getPhase(0).getComponent("methane");
double Tc = comp.getTC();           // Critical temperature [K]
double Pc = comp.getPC();           // Critical pressure [bara]
double omega = comp.getAcentricFactor();  // Acentric factor [-]
double Mw = comp.getMolarMass();    // Molar mass [kg/mol]
double Tb = comp.getNormalBoilingPoint(); // Normal boiling point [K]

System.out.println("Methane Tc = " + Tc + " K");
System.out.println("Methane Pc = " + Pc + " bara");
System.out.println("Methane ω = " + omega);
```

### Modifying Component Properties

```java
// Modify properties for sensitivity analysis
comp.setTC(190.6);  // Set new Tc in Kelvin
comp.setPC(46.0);   // Set new Pc in bara
comp.setAcentricFactor(0.012);

// Re-initialize to apply changes
fluid.init(0);
```

---

## Adding Custom Components

### Method 1: Database Modification

Add a new row to `COMP.csv` with all required parameters.

### Method 2: Runtime Addition

```java
// Add a pseudo-component with custom properties
SystemInterface fluid = new SystemSrkEos(298.15, 50.0);

// Add TBP fraction with molar mass and density
fluid.addTBPfraction("C7_custom", 0.1, 95.0, 0.72);  // name, moles, MW, SG

// Or add component and modify properties
fluid.addComponent("n-heptane", 1.0);
ComponentInterface comp = fluid.getPhase(0).getComponent("n-heptane");
comp.setTC(540.0);
comp.setPC(27.4);
comp.setAcentricFactor(0.35);

// Initialize database for binary parameters
fluid.createDatabase(true);
fluid.setMixingRule(2);
```

### Method 3: Temporary Tables

```java
// Enable temporary tables for session-specific components
NeqSimDataBase.setCreateTemporaryTables(true);

// Components added to "comptemp" table
fluid.getPhase(0).getComponent(0).insertComponentIntoDatabase("comptemp");

// Remember to disable after use
NeqSimDataBase.setCreateTemporaryTables(false);
```

---

## Related Database Tables

The COMP table works with several related tables:

| Table | Purpose | Key Columns |
|-------|---------|-------------|
| `INTER` | Binary interaction parameters (kij) | comp1, comp2, kij, model |
| `UNIFACcomp` | UNIFAC group assignments | compname, group, count |
| `UNIFACGroupParam` | UNIFAC group parameters | groupid, R, Q |
| `UNIFACInterParam*` | UNIFAC group interaction parameters | group1, group2, aij |
| `MBWR32param` | MBWR equation parameters | comp, coefficients |
| `AdsorptionParameters` | Adsorption isotherm parameters | comp, adsorbent, params |

---

## See Also

- [Fluid Creation Guide](fluid_creation_guide.md) - How to create fluids using these parameters
- [Mixing Rules Guide](mixing_rules_guide.md) - Binary interaction parameters (kij)
- [Mathematical Models](mathematical_models.md) - Model equations using these parameters

---

## References

1. Soave, G. (1972). Equilibrium constants from a modified Redlich-Kwong equation of state. Chemical Engineering Science, 27(6), 1197-1203.
2. Peng, D. Y., & Robinson, D. B. (1976). A new two-constant equation of state. Industrial & Engineering Chemistry Fundamentals, 15(1), 59-64.
3. Kontogeorgis, G. M., et al. (1999). An equation of state for associating fluids. Industrial & Engineering Chemistry Research, 38(10), 4073-4082.
4. Gross, J., & Sadowski, G. (2001). Perturbed-chain SAFT: An equation of state based on a perturbation theory for chain molecules. Industrial & Engineering Chemistry Research, 40(4), 1244-1260.
