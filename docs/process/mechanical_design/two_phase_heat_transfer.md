---
title: "Two-Phase Heat Transfer and Advanced Correlations"
description: "Guide to two-phase (condensation, boiling) heat transfer correlations, two-phase pressure drop methods, dynamic fouling models, incremental zone analysis, and tube insert enhancement models in NeqSim. Covers Shah, Chen, Gungor-Winterton, Friedel, Muller-Steinhagen-Heck, Ebert-Panchal, Kern-Seaton, and Manglik-Bergles correlations."
---

# Two-Phase Heat Transfer and Advanced Correlations

NeqSim provides a suite of advanced correlations for heat exchanger services that
go beyond single-phase convection. These cover condensation, boiling, two-phase
pressure drop, dynamic fouling prediction, HTRI-style incremental zone analysis,
and tube insert enhancement modelling.

All classes reside in `neqsim.process.mechanicaldesign.heatexchanger`.

## Table of Contents

- [Architecture](#architecture)
- [Condensation — Shah Correlation](#condensation--shah-correlation)
- [Boiling — Chen and Gungor-Winterton](#boiling--chen-and-gungor-winterton)
- [Two-Phase Pressure Drop](#two-phase-pressure-drop)
- [Dynamic Fouling Models](#dynamic-fouling-models)
- [Incremental Zone Analysis](#incremental-zone-analysis)
- [Tube Insert Enhancement](#tube-insert-enhancement)
- [Cross-Reference](#cross-reference)

---

## Architecture

| Class | Purpose | Key Correlations |
|-------|---------|-----------------|
| `ShahCondensation` | In-tube and vertical-tube condensation HTC | Shah (1979, 2009, 2017) |
| `BoilingHeatTransfer` | Two-phase boiling HTC (nucleate + forced convective) | Chen (1966), Gungor-Winterton (1986/1987) |
| `TwoPhasePressureDrop` | Two-phase frictional, gravitational, and acceleration pressure drop | Friedel (1979), Muller-Steinhagen-Heck (1986) |
| `FoulingModel` | Time-dependent fouling with threshold prediction | Ebert-Panchal (1997), Kern-Seaton (1959) |
| `IncrementalZoneAnalysis` | Zone-by-zone incremental rating (HTRI-style) | Dispatches to phase-regime-specific correlations |
| `TubeInsertModel` | Enhancement device modelling | Twisted tape, wire matrix (HiTRAN), coiled wire |

These classes are designed as building blocks. They can be used:

1. **Standalone** — for quick correlation lookups with known fluid properties.
2. **With `ThermalDesignCalculator`** — integrated into the existing shell-and-tube
   design workflow.
3. **With `IncrementalZoneAnalysis`** — for rigorous zone-by-zone rating of phase-change
   services.

---

## Condensation — Shah Correlation

The `ShahCondensation` class implements the Shah (1979) general correlation for film
condensation inside horizontal tubes, with the Shah (2017) extension for vertical tubes.

### Theory

The local condensation coefficient at vapor quality $x$ is:

$$
h_{\text{cond}} = h_{\text{lo}} \left[ (1 - x)^{0.8} + \frac{3.8\, x^{0.76}\, (1-x)^{0.04}}{P_r^{0.38}} \right]
$$

where:

- $h_{\text{lo}}$ is the liquid-only Dittus-Boelter coefficient (total mass flowing as liquid)
- $P_r = P / P_{\text{crit}}$ is the reduced pressure
- $x$ is the local vapor quality (mass fraction of vapor)

### Validity

- Horizontal tubes, Re$_{\text{lo}}$ > 350
- 0.002 < $P_r$ < 0.44
- Validated against refrigerants, hydrocarbons, water, and organic fluids

### Usage

```java
import neqsim.process.mechanicaldesign.heatexchanger.ShahCondensation;

// Liquid-only HTC from Dittus-Boelter
double hLo = ShahCondensation.calcLiquidOnlyHTC(
    300.0,    // massFlux G (kg/(m2*s))
    0.01905,  // tube ID (m)
    1147.0,   // liquid density (kg/m3)
    1.65e-4,  // liquid viscosity (Pa*s)
    1480.0,   // liquid Cp (J/(kg*K))
    0.078);   // liquid conductivity (W/(m*K))

// Local condensation HTC at 50% quality, Pr = 0.1
double hLocal = ShahCondensation.calcLocalHTC(hLo, 0.5, 0.1);

// Average HTC over a quality range (Simpson's rule integration)
double hAvg = ShahCondensation.calcAverageHTC(hLo, 0.1, 0.9, 0.1, 20);

// Vertical tube with gravity correction
double hVert = ShahCondensation.calcVerticalTubeHTC(
    hLo, 0.5, 0.1, 300.0, 0.01905, 50.0, 1147.0);
```

---

## Boiling — Chen and Gungor-Winterton

The `BoilingHeatTransfer` class provides two widely-used correlations for
forced convective boiling inside tubes.

### Chen (1966) Superposition Model

The Chen correlation decomposes the two-phase boiling coefficient into a
macroscopic (forced convective) and microscopic (nucleate boiling) component:

$$
h_{\text{tp}} = h_{\text{mac}} \cdot F + h_{\text{mic}} \cdot S
$$

where:

- $h_{\text{mac}}$ = liquid-only Dittus-Boelter coefficient
- $F$ = Chen enhancement factor (function of Martinelli parameter $X_{tt}$)
- $h_{\text{mic}}$ = Forster-Zuber nucleate boiling coefficient
- $S$ = Chen suppression factor (function of two-phase Reynolds number)

The Martinelli parameter captures the liquid-vapor interaction:

$$
X_{tt} = \left(\frac{1-x}{x}\right)^{0.9} \left(\frac{\rho_v}{\rho_l}\right)^{0.5} \left(\frac{\mu_l}{\mu_v}\right)^{0.1}
$$

### Gungor-Winterton (1986/1987) Simplified

A simpler alternative that does not require wall superheat or surface tension:

$$
h_{\text{tp}} = h_l \left[ 1 + 3000\, \text{Bo}^{0.86} + 1.12 \left(\frac{x}{1-x}\right)^{0.75} \left(\frac{\rho_l}{\rho_v}\right)^{0.41} \right]
$$

where Bo = $q / (G \cdot h_{fg})$ is the boiling number.

### Usage

```java
import neqsim.process.mechanicaldesign.heatexchanger.BoilingHeatTransfer;

// Chen correlation (needs wall superheat and surface tension)
double hChen = BoilingHeatTransfer.calcChenHTC(
    300.0,    // mass flux G (kg/(m2*s))
    0.3,      // vapor quality
    0.01905,  // tube ID (m)
    1147.0,   // liquid density (kg/m3)
    50.1,     // vapor density (kg/m3)
    1.65e-4,  // liquid viscosity (Pa*s)
    1.25e-5,  // vapor viscosity (Pa*s)
    1480.0,   // liquid Cp (J/(kg*K))
    0.078,    // liquid conductivity (W/(m*K))
    0.006,    // surface tension (N/m)
    163000.0, // heat of vaporization (J/kg)
    5.0,      // wall superheat (K)
    50000.0); // dP_sat corresponding to wall superheat (Pa)

// Gungor-Winterton (simpler — no surface tension needed)
double hGW = BoilingHeatTransfer.calcGungorWintertonHTC(
    300.0, 0.3, 0.01905, 1147.0, 50.1, 1.65e-4,
    1480.0, 0.078, 20000.0, 163000.0);
//                 ^^^^^^^ heat flux (W/m2)

// Martinelli parameter and enhancement/suppression factors
double Xtt = BoilingHeatTransfer.calcMartinelliParameter(
    0.3, 1147.0, 50.1, 1.65e-4, 1.25e-5);
double F = BoilingHeatTransfer.calcChenEnhancementFactor(Xtt);
double S = BoilingHeatTransfer.calcChenSuppressionFactor(50000.0);

// Average HTC over quality range (Simpson's rule)
double hAvg = BoilingHeatTransfer.calcAverageHTC(
    300.0, 0.01905, 1147.0, 50.1, 1.65e-4,
    1480.0, 0.078, 20000.0, 163000.0, 0.1, 0.9, 20);
```

### When to Use Which

| Scenario | Recommended Correlation |
|----------|------------------------|
| Detailed design with known wall conditions | Chen (1966) |
| Preliminary design / screening | Gungor-Winterton (1987) |
| High-pressure systems (Pr > 0.2) | Gungor-Winterton (1987) |
| Kettle reboiler / pool boiling | Neither (use Mostinski or Gorenflo) |

---

## Two-Phase Pressure Drop

The `TwoPhasePressureDrop` class provides two-phase frictional pressure drop
correlations plus gravitational and acceleration components.

### Friedel (1979) Correlation

The most widely validated two-phase friction multiplier method:

$$
\phi_{\text{lo}}^2 = E + \frac{3.24\, F\, H}{\text{Fr}^{0.045}\, \text{We}^{0.035}}
$$

where:

- $E = (1-x)^2 + x^2 \cdot (\rho_l f_{\text{vo}}) / (\rho_v f_{\text{lo}})$
- $F = x^{0.78} (1-x)^{0.224}$
- $H = (\rho_l/\rho_v)^{0.91} (\mu_v/\mu_l)^{0.19} (1-\mu_v/\mu_l)^{0.7}$
- Fr and We are the Froude and Weber numbers based on homogeneous density

### Muller-Steinhagen-Heck (1986) Correlation

A simpler alternative well-suited for design screening:

$$
\frac{dP}{dz} = G^\prime (1-x)^{1/3} + B \cdot x^3
$$

where $G^\prime = A + 2(B-A)x$, with $A$ and $B$ being the all-liquid and all-vapor
single-phase pressure drops respectively.

### Usage

```java
import neqsim.process.mechanicaldesign.heatexchanger.TwoPhasePressureDrop;

// Friedel gradient (Pa/m)
double dPdz = TwoPhasePressureDrop.calcFriedelGradient(
    300.0, 0.5, 0.01905, 1147.0, 50.1, 1.65e-4, 1.25e-5, 0.006);

// Friedel total pressure drop over a tube length (Pa)
double dP = TwoPhasePressureDrop.calcFriedelPressureDrop(
    300.0, 0.5, 0.01905, 6.0, 1147.0, 50.1, 1.65e-4, 1.25e-5, 0.006);

// Average over quality range (condenser/evaporator with changing quality)
double dPavg = TwoPhasePressureDrop.calcFriedelAveragePressureDrop(
    300.0, 0.9, 0.1, 0.01905, 6.0, 1147.0, 50.1,
    1.65e-4, 1.25e-5, 0.006, 20);

// Muller-Steinhagen-Heck gradient (Pa/m) — simpler, no surface tension needed
double dPdzMSH = TwoPhasePressureDrop.calcMullerSteinhagenHeckGradient(
    300.0, 0.5, 0.01905, 1147.0, 50.1, 1.65e-4, 1.25e-5);

// Gravitational component (Pa/m) — for vertical/inclined tubes
double dPgrav = TwoPhasePressureDrop.calcGravitationalGradient(0.3, 1147.0, 50.1);

// Acceleration pressure drop due to quality change (Pa)
double dPacc = TwoPhasePressureDrop.calcAccelerationPressureDrop(
    300.0, 0.1, 0.9, 1147.0, 50.1);
```

### When to Use Which

| Scenario | Recommended Method |
|----------|-------------------|
| Detailed design / rating | Friedel (1979) |
| Preliminary design / screening | Muller-Steinhagen-Heck (1986) |
| Vertical tubes / risers | Include gravitational component |
| Condensers / evaporators | Include acceleration component |

---

## Dynamic Fouling Models

The `FoulingModel` class replaces fixed TEMA fouling factors with time-dependent
models that predict fouling build-up and cleaning intervals.

### Model Types

| Model | Description | Use Case |
|-------|-------------|----------|
| `FIXED` | Constant resistance (TEMA default) | Preliminary design |
| `EBERT_PANCHAL` | Threshold model with deposition/removal | Crude oil preheat trains |
| `KERN_SEATON` | Asymptotic approach to steady-state | Cooling water, clean services |

### Ebert-Panchal (1997) Threshold Model

Models fouling as a competition between deposition and removal:

$$
\frac{dR_f}{dt} = \alpha\, \text{Re}^{\beta}\, \exp\!\left(\frac{-E_a}{R\, T_w}\right) - \gamma\, \tau_w\, R_f
$$

where:

- $\alpha$ and $\beta$ control the deposition rate
- $E_a$ is the activation energy (J/mol) controlling temperature dependence of chemical fouling
- $\gamma$ controls the removal rate via wall shear stress $\tau_w$
- The process reaches an asymptotic resistance: $R_{f,\infty} = \text{deposition rate} / (\gamma \tau_w)$

Key capabilities:

- **Threshold temperature**: wall temperature below which no significant fouling occurs
- **Threshold velocity**: flow velocity above which fouling is suppressed
- **Cleaning interval prediction**: time to reach a target fouling resistance
- **Dynamic time-stepping**: Euler integration for transient monitoring

### Kern-Seaton (1959) Asymptotic Model

A simpler model for monotonic fouling approach to steady state:

$$
R_f(t) = R_{f,\text{max}} \left(1 - e^{-t/\tau}\right)
$$

where $R_{f,\text{max}}$ is the asymptotic fouling resistance and $\tau$ is the time
constant.

### Usage

```java
import neqsim.process.mechanicaldesign.heatexchanger.FoulingModel;

// Factory methods for common services
FoulingModel crudeModel = FoulingModel.createCrudeOilModel();
FoulingModel heavyCrude = FoulingModel.createHeavyCrudeModel();
FoulingModel coolingWater = FoulingModel.createCoolingWaterModel(0.0003, 2000.0);

// Configure operating conditions
crudeModel.updateConditions(
    1.5,      // velocity (m/s)
    800.0,    // density (kg/m3)
    0.001,    // viscosity (Pa*s)
    523.15,   // wall temperature (K) = 250 degC
    0.01905); // tube ID (m)

// Fouling rate at current conditions (m2*K/(W*hr))
double rate = crudeModel.calcEbertPanchalFoulingRate(0.0);

// Fouling resistance after 1000 hours
double rf = crudeModel.calcEbertPanchalResistance(1000.0);

// Threshold analysis
double tThreshold = crudeModel.calcThresholdTemperature(1e-10);
double vThreshold = crudeModel.calcThresholdVelocity();

// Predict cleaning interval (hours to reach target Rf)
double cleaningInterval = crudeModel.predictTimeToFouling(0.0005);

// Dynamic time-stepping
crudeModel.reset();
for (int step = 0; step < 100; step++) {
    crudeModel.advanceTime(10.0);  // 10-hour steps
}
double currentRf = crudeModel.getFoulingResistance();

// JSON report
String json = crudeModel.toJson();
```

### Design Implications

| Fouling Rate | Implication |
|-------------|-------------|
| $dR_f/dt$ > 0 | Net fouling — wall temperature exceeds threshold |
| $dR_f/dt$ = 0 | Threshold condition — deposition equals removal |
| $dR_f/dt$ < 0 | Self-cleaning — high velocity removes deposits |

---

## Incremental Zone Analysis

The `IncrementalZoneAnalysis` class implements HTRI-style zone-by-zone analysis for
heat exchangers with phase change. Instead of using a single overall HTC, the
exchanger is divided into zones with different fluid properties and phase regimes.

### Phase Regimes

Each zone is assigned a `PhaseRegime` that determines which HTC correlation is used:

| Regime | Tube-Side Correlation | When Used |
|--------|----------------------|-----------|
| `VAPOR` | Gnielinski / Dittus-Boelter | Superheated vapour |
| `LIQUID` | Gnielinski / Dittus-Boelter | Subcooled liquid |
| `CONDENSING` | Shah (1979) | Condensation (vapor quality decreasing) |
| `EVAPORATING` | Gungor-Winterton (1987) | Evaporation (vapor quality increasing) |
| `TWO_PHASE` | Average of liquid-only and vapour-only | Unmapped two-phase |

### Usage

```java
import neqsim.process.mechanicaldesign.heatexchanger.IncrementalZoneAnalysis;
import neqsim.process.mechanicaldesign.heatexchanger.IncrementalZoneAnalysis.IncrementalZone;
import neqsim.process.mechanicaldesign.heatexchanger.IncrementalZoneAnalysis.PhaseRegime;

IncrementalZoneAnalysis analysis = new IncrementalZoneAnalysis();

// Configure exchanger geometry
analysis.setTubeODm(0.01905);
analysis.setTubeIDm(0.01483);
analysis.setTubeCount(100);
analysis.setTubePitchm(0.02381);
analysis.setTriangularPitch(true);
analysis.setShellIDm(0.5);
analysis.setBaffleSpacingm(0.2);
analysis.setFoulingTube(0.00018);
analysis.setFoulingShell(0.00035);

// Add zones (e.g., for a condenser: desuperheating -> condensing -> subcooling)
IncrementalZone desuper = new IncrementalZone();
desuper.zoneName = "Desuperheating";
desuper.regime = PhaseRegime.VAPOR;
desuper.tubeInletTemp = 120.0 + 273.15;
desuper.tubeOutletTemp = 80.0 + 273.15;
desuper.shellInletTemp = 25.0 + 273.15;
desuper.shellOutletTemp = 35.0 + 273.15;
desuper.tubeMassFlux = 300.0;
desuper.tubeDensity = 50.0;
desuper.tubeViscosity = 1.25e-5;
desuper.tubeCp = 2200.0;
desuper.tubeConductivity = 0.03;
desuper.shellDensity = 998.0;
desuper.shellViscosity = 0.001;
desuper.shellCp = 4180.0;
desuper.shellConductivity = 0.60;
desuper.shellVelocity = 1.0;
desuper.zoneDuty = 50000.0;
analysis.addZone(desuper);

// Add condensing zone, subcooling zone, etc. ...
// (set regime = PhaseRegime.CONDENSING for condensation zones)

// Run analysis
analysis.calculate();

// Results per zone and total
System.out.println("Total area: " + analysis.getTotalArea() + " m2");
System.out.println("Total tube dP: " + analysis.getTotalTubePressureDrop() + " Pa");
System.out.println("Total shell dP: " + analysis.getTotalShellPressureDrop() + " Pa");

// JSON report with per-zone breakdown
String jsonReport = analysis.toJson();
```

### Key Features

- **Automatic correlation dispatch**: HTC correlation is selected based on `PhaseRegime`
- **Two-phase pressure drop**: Friedel correlation is used automatically for condensing
  and evaporating zones
- **Shell-side**: Kern method applied to each zone independently
- **LMTD per zone**: Calculated from zone inlet/outlet temperatures
- **Incremental area summation**: Total area = sum of zone areas

---

## Tube Insert Enhancement

The `TubeInsertModel` class models the effect of tube-side enhancement devices on
heat transfer and pressure drop. These are commonly used for debottlenecking
existing heat exchangers.

### Insert Types

| Type | Description | Typical Enhancement (h) | Typical Penalty (f) |
|------|-------------|------------------------|---------------------|
| Twisted tape | Helical swirl generator | 1.3 - 2.5x | 1.5 - 4x |
| Wire matrix (HiTRAN) | Turbulence promoter | 2 - 5x | 3 - 8x |
| Coiled wire | Surface roughness element | 1.2 - 2x | 1.3 - 3x |

### Performance Evaluation Criteria

The net benefit of an insert is measured by the PEC at constant pumping power:

$$
\text{PEC} = \frac{h_{\text{enh}} / h_{\text{plain}}}{(f_{\text{enh}} / f_{\text{plain}})^{1/3}}
$$

PEC > 1.0 means the insert provides net benefit at constant pumping power.

### Usage

```java
import neqsim.process.mechanicaldesign.heatexchanger.TubeInsertModel;

// Factory methods for each insert type
TubeInsertModel tape = TubeInsertModel.createTwistedTape(4.0);   // twist ratio y = 4
TubeInsertModel wire = TubeInsertModel.createWireMatrix(0.5);    // density = 0.5
TubeInsertModel coil = TubeInsertModel.createCoiledWire(0.03, 45.0); // e/D, angle

// Enhancement and penalty ratios
double Re = 30000;
double Pr = 5.0;
double hRatio = tape.getHeatTransferEnhancementRatio(Re, Pr);  // e.g. 1.8
double fRatio = tape.getPressureDropPenaltyRatio(Re);           // e.g. 2.5
double pec = tape.getPerformanceEvaluationCriteria(Re, Pr);     // e.g. 1.33

// Apply enhancement to plain-tube results
double plainHTC = 2000.0;        // W/(m2*K)
double plainDP = 5000.0;         // Pa
double[] enhanced = tape.applyEnhancement(plainHTC, plainDP, Re, Pr);
double enhancedHTC = enhanced[0]; // W/(m2*K)
double enhancedDP = enhanced[1];  // Pa

// JSON report
String json = tape.toJson(Re, Pr);
```

### Twisted Tape Parameters

The twist ratio $y = H / (2D)$ controls the enhancement intensity:

| Twist Ratio $y$ | Enhancement Level | Application |
|-----------------|-------------------|-------------|
| 2.5 - 3.5 | High (aggressive swirl) | Viscous fluids, laminar |
| 4.0 - 6.0 | Medium | General debottlenecking |
| 7.0 - 10.0 | Low (gentle swirl) | Low DP budget |

### Wire Matrix (HiTRAN) Parameters

| Matrix Density | Enhancement Level | Application |
|----------------|-------------------|-------------|
| 0.3 - 0.4 | Moderate | Clean services |
| 0.5 - 0.6 | High | Typical retrofit |
| 0.7+ | Very high | Maximum heat transfer |

---

## Cross-Reference

- [Thermal-Hydraulic Design Guide](thermal_hydraulic_design) — Single-phase tube/shell
  HTC, overall U, LMTD correction, vibration screening
- [Heat Exchanger Equipment](../equipment/heat_exchangers) — Process equipment classes
  (`Heater`, `Cooler`, `HeatExchanger`)
- [Heat Exchanger Mechanical Design](../../wiki/heat_exchanger_mechanical_design) — Sizing
  and mechanical design (`HeatExchangerMechanicalDesign`)
- [TEMA Standard Guide](tema_standard_guide) — TEMA designations and standards
- [Heat Integration (Pinch Analysis)](../equipment/heat_integration) — Multi-stream heat
  exchanger network synthesis
