---
title: "Heat Exchanger Thermal-Hydraulic Design"
description: "Comprehensive guide to shell-and-tube heat exchanger thermal-hydraulic design in NeqSim. Covers tube-side and shell-side heat transfer coefficients (Gnielinski, Kern, Bell-Delaware), overall U, pressure drops, LMTD correction factors, vibration screening, zone-by-zone analysis, and rating mode."
---

# Heat Exchanger Thermal-Hydraulic Design

NeqSim provides a complete thermal-hydraulic design toolkit for shell-and-tube heat
exchangers. The toolkit connects rigorous thermodynamic property predictions from
the process simulation to industry-standard heat transfer and pressure drop
correlations.

> **Two-Phase Services:** For condensation (Shah), boiling (Chen, Gungor-Winterton),
> two-phase pressure drop (Friedel, MSH), dynamic fouling (Ebert-Panchal),
> incremental zone analysis, and tube insert enhancement models, see the
> [Two-Phase Heat Transfer and Advanced Correlations](two_phase_heat_transfer) guide.

## Architecture Overview

The thermal design is built on five coordinated classes:

| Class | Purpose |
|-------|---------|
| `ThermalDesignCalculator` | Central calculator: tube/shell HTCs, overall U, pressure drops, zone analysis |
| `BellDelawareMethod` | Shell-side correlations: Kern method and Bell-Delaware J-factor corrections |
| `LMTDcorrectionFactor` | LMTD correction factor F_t for multi-pass exchangers (Bowman-Mueller-Nagle) |
| `VibrationAnalysis` | Flow-induced vibration screening per TEMA RCB-4.6 |
| `HeatTransferCoefficientCalculator` | Tube-side correlations: Gnielinski, Dittus-Boelter, condensation, evaporation |

All classes reside in `neqsim.process.mechanicaldesign.heatexchanger` except
`HeatTransferCoefficientCalculator` which is in `neqsim.fluidmechanics.flownode`.

### Integration Points

The thermal design integrates into the existing NeqSim equipment model at two levels:

1. **Mechanical design path** (`ShellAndTubeDesignCalculator`): when fluid properties
   are provided, the mechanical design automatically runs thermal calculations and
   vibration screening as part of `calcDesign()`.

2. **Rating mode** (`HeatExchanger`): the `HeatExchanger` process equipment can
   operate in RATING mode, computing the overall U from correlations and geometry
   instead of requiring a user-supplied UA value.

---

## Quick Start

### Standalone Thermal Calculation

```java
import neqsim.process.mechanicaldesign.heatexchanger.ThermalDesignCalculator;

ThermalDesignCalculator calc = new ThermalDesignCalculator();

// Geometry: 3/4" tubes, 500mm shell
calc.setTubeODm(0.01905);
calc.setTubeIDm(0.01483);
calc.setTubeLengthm(6.096);
calc.setTubeCount(100);
calc.setTubePasses(2);
calc.setTubePitchm(0.02381);
calc.setTriangularPitch(true);
calc.setShellIDm(0.5);
calc.setBaffleSpacingm(0.2);
calc.setBaffleCount(10);
calc.setBaffleCut(0.25);

// Tube side: cooling water
calc.setTubeSideFluid(998.0, 0.001, 4180.0, 0.60, 5.0, true);

// Shell side: process gas
calc.setShellSideFluid(50.0, 1.5e-5, 2200.0, 0.03, 3.0);

// Fouling resistances (m2*K/W)
calc.setFoulingTube(0.00018);
calc.setFoulingShell(0.00035);

// Run calculation
calc.calculate();

// Results
System.out.println("Tube-side HTC: " + calc.getTubeSideHTC() + " W/(m2*K)");
System.out.println("Shell-side HTC: " + calc.getShellSideHTC() + " W/(m2*K)");
System.out.println("Overall U: " + calc.getOverallU() + " W/(m2*K)");
System.out.println("Tube-side dP: " + calc.getTubeSidePressureDropBar() + " bar");
System.out.println("Shell-side dP: " + calc.getShellSidePressureDropBar() + " bar");
```

### Rating Mode in Process Simulation

```java
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.mechanicaldesign.heatexchanger.ThermalDesignCalculator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

SystemSrkEos hotFluid = new SystemSrkEos(273.15 + 100.0, 20.0);
hotFluid.addComponent("methane", 0.85);
hotFluid.addComponent("ethane", 0.10);
hotFluid.addComponent("propane", 0.05);
hotFluid.setMixingRule("classic");

Stream hotStream = new Stream("hot", hotFluid);
hotStream.setTemperature(100.0, "C");
hotStream.setFlowRate(1000.0, "kg/hr");

Stream coldStream = new Stream("cold", hotFluid.clone());
coldStream.setTemperature(20.0, "C");
coldStream.setFlowRate(500.0, "kg/hr");

HeatExchanger hx = new HeatExchanger("E-100", hotStream, coldStream);

// Configure rating calculator with exchanger geometry
ThermalDesignCalculator ratingCalc = new ThermalDesignCalculator();
ratingCalc.setTubeODm(0.01905);
ratingCalc.setTubeIDm(0.01483);
ratingCalc.setTubeLengthm(6.096);
ratingCalc.setTubeCount(100);
ratingCalc.setTubePasses(2);
ratingCalc.setTubePitchm(0.02381);
ratingCalc.setTriangularPitch(true);
ratingCalc.setShellIDm(0.5);
ratingCalc.setBaffleSpacingm(0.2);
ratingCalc.setBaffleCount(10);

hx.setRatingCalculator(ratingCalc);
hx.setRatingArea(50.0); // known heat transfer area in m2

ProcessSystem ps = new ProcessSystem();
ps.add(hotStream);
ps.add(coldStream);
ps.add(hx);
ps.run();

System.out.println("Computed U: " + hx.getRatingU() + " W/(m2*K)");
System.out.println("Hot out: " + hx.getOutStream(0).getTemperature("C") + " C");
```

---

## Tube-Side Heat Transfer Coefficient

The `ThermalDesignCalculator` delegates tube-side HTC calculation to the
established `HeatTransferCoefficientCalculator` correlations:

| Flow Regime | Correlation | Validity |
|-------------|-------------|----------|
| Turbulent (Re &gt; 4000) | Gnielinski | $\text{Nu} = \frac{(f/8)(\text{Re}-1000)\text{Pr}}{1 + 12.7\sqrt{f/8}(\text{Pr}^{2/3}-1)}$ |
| Turbulent fallback | Dittus-Boelter | $\text{Nu} = 0.023 \text{Re}^{0.8} \text{Pr}^n$ (n=0.4 heating, 0.3 cooling) |
| Condensation | Horizontal tube | Film condensation inside tubes |
| Evaporation | Nucleate boiling | Forced convective evaporation |

The tube-side velocity, Reynolds number, and pressure drop are computed from the
geometry (tube ID, count, passes) and fluid properties (density, viscosity, mass
flow rate).

**Tube-side pressure drop** uses the Fanning friction factor:

$$
\Delta P_{\text{tube}} = 4 f \frac{L}{d_i} \frac{\rho v^2}{2} \cdot N_p + 4.0 \cdot N_p \cdot \frac{\rho v^2}{2}
$$

where $N_p$ is the number of tube passes and the second term accounts for return losses.

---

## Shell-Side Heat Transfer Coefficient

Two methods are available, selected via `setShellSideMethod()`:

### Kern Method (Default)

The Kern method is a simplified approach suitable for preliminary design:

$$
h_s = 0.36 \frac{k}{D_e} \text{Re}^{0.55} \text{Pr}^{1/3} \left(\frac{\mu}{\mu_w}\right)^{0.14}
$$

where $D_e$ is the shell-side equivalent diameter, computed differently for
triangular and square pitch layouts.

```java
// Use Kern method (default)
calc.setShellSideMethod(ThermalDesignCalculator.ShellSideMethod.KERN);
```

### Bell-Delaware Method

The Bell-Delaware method provides a more accurate shell-side prediction by applying
five correction factors to the ideal crossflow HTC:

$$
h_s = h_{\text{ideal}} \cdot J_c \cdot J_l \cdot J_b \cdot J_s \cdot J_r
$$

| Factor | Name | Effect | Typical Range |
|--------|------|--------|---------------|
| $J_c$ | Baffle cut | Corrects for window vs crossflow zones | 0.55 - 1.15 |
| $J_l$ | Baffle leakage | Reduces HTC due to tube-baffle and shell-baffle clearances | 0.2 - 1.0 |
| $J_b$ | Bundle bypass | Accounts for flow between bundle and shell wall | 0.5 - 1.0 |
| $J_s$ | Unequal spacing | Corrects for inlet/outlet baffle spacing differences | 0.5 - 1.2 |
| $J_r$ | Adverse gradient | Laminar flow temperature gradient penalty | 0.4 - 1.0 |

```java
calc.setShellSideMethod(ThermalDesignCalculator.ShellSideMethod.BELL_DELAWARE);
calc.calculate();
```

The ideal crossflow HTC uses the Zhukauskas correlation for tube banks with
Reynolds-number-dependent coefficients for both staggered (triangular) and inline
(square) layouts.

#### Using Bell-Delaware Directly

For advanced use, the `BellDelawareMethod` utility class exposes all correlations
as static methods:

```java
import neqsim.process.mechanicaldesign.heatexchanger.BellDelawareMethod;

// Shell equivalent diameter (m)
double de = BellDelawareMethod.calcShellEquivDiameter(0.01905, 0.02381, true);

// Crossflow area (m2)
double aCross = BellDelawareMethod.calcCrossflowArea(0.5, 0.2, 0.01905, 0.02381);

// Kern shell-side HTC (W/(m2*K))
double hKern = BellDelawareMethod.calcKernShellSideHTC(
    50.0, de, 1.5e-5, 2200.0, 0.03, 1.5e-5);

// Bell-Delaware correction factors
double Jc = BellDelawareMethod.calcJc(0.25);
double Jl = BellDelawareMethod.calcJl(0.0004, 0.003, aCross, 100, 0.01905, 0.2);
double Jb = BellDelawareMethod.calcJb(0.005, aCross, true, 2, 15);
double Js = BellDelawareMethod.calcJs(0.2, 0.25, 0.25, 10);
double Jr = BellDelawareMethod.calcJr(20000.0, 15);
```

---

## Overall Heat Transfer Coefficient

The overall U is calculated using the resistance-in-series model including fouling
and tube wall conduction:

$$
\frac{1}{U_o} = \frac{1}{h_o} + R_{f,o} + \frac{d_o \ln(d_o/d_i)}{2 k_w} + R_{f,i} \frac{d_o}{d_i} + \frac{1}{h_i} \frac{d_o}{d_i}
$$

where:

- $h_o$ = shell-side HTC (W/m$^2$K)
- $h_i$ = tube-side HTC (W/m$^2$K)
- $R_{f,o}$, $R_{f,i}$ = shell-side and tube-side fouling resistances (m$^2$K/W)
- $k_w$ = tube wall thermal conductivity (W/mK)
- $d_o$, $d_i$ = tube outer and inner diameters (m)

Typical fouling resistances:

| Service | Fouling (m$^2$K/W) |
|---------|-------------------|
| Clean gas | 0.0001 - 0.0002 |
| Light hydrocarbons | 0.0002 - 0.0004 |
| Heavy hydrocarbons | 0.0004 - 0.0009 |
| Cooling water (treated) | 0.0002 - 0.0004 |
| Seawater | 0.0003 - 0.0006 |

---

## LMTD Correction Factor

For multi-pass exchangers, the effective mean temperature difference is:

$$
\Delta T_m = F_t \cdot \text{LMTD}_{\text{counterflow}}
$$

The `LMTDcorrectionFactor` class calculates $F_t$ using the Bowman-Mueller-Nagle
analytical formulas for 1-2N, 2-4, and N-shell-pass configurations.

### Dimensionless Parameters

$$
R = \frac{T_{h,in} - T_{h,out}}{T_{c,out} - T_{c,in}} \quad \text{(capacity ratio)}
$$

$$
P = \frac{T_{c,out} - T_{c,in}}{T_{h,in} - T_{c,in}} \quad \text{(thermal effectiveness)}
$$

### Usage

```java
import neqsim.process.mechanicaldesign.heatexchanger.LMTDcorrectionFactor;

// Temperatures: Hot 150->90, Cold 30->80
double Ft = LMTDcorrectionFactor.calcFt1ShellPass(150.0, 90.0, 30.0, 80.0);

// R and P parameters
double R = LMTDcorrectionFactor.calcR(150.0, 90.0, 30.0, 80.0);
double P = LMTDcorrectionFactor.calcP(150.0, 90.0, 30.0, 80.0);

// Multi-shell pass
double Ft2 = LMTDcorrectionFactor.calcFt(150.0, 90.0, 30.0, 80.0, 2);

// Determine required shell passes (Ft >= 0.75)
int passes = LMTDcorrectionFactor.requiredShellPasses(200.0, 50.0, 30.0, 170.0);
```

### Design Guidelines

- $F_t \geq 0.75$ is acceptable for design
- If $F_t \lt 0.75$ for 1 shell pass, increase the number of shell passes
- `requiredShellPasses()` automatically finds the minimum configuration
- Pure counterflow gives $F_t = 1.0$

---

## Flow-Induced Vibration Screening

The `VibrationAnalysis` class evaluates four vibration mechanisms per TEMA RCB-4.6:

| Mechanism | Criterion | Critical If |
|-----------|-----------|-------------|
| Vortex shedding | $f_{vs} / f_n$ ratio | 0.8 - 1.2 (near resonance) |
| Fluid-elastic instability | Connors: $V / (0.5 \cdot V_{crit})$ | &gt; 1.0 |
| Acoustic resonance | $f_{vs} / f_{acoustic}$ ratio | 0.8 - 1.2 |
| Turbulent buffeting | Random excitation | Assessed qualitatively |

### Complete Screening

```java
import neqsim.process.mechanicaldesign.heatexchanger.VibrationAnalysis;

VibrationAnalysis.VibrationResult result = VibrationAnalysis.performScreening(
    0.01905,  // tube OD (m)
    0.01483,  // tube ID (m)
    0.4,      // unsupported span (m)
    0.02381,  // tube pitch (m)
    200e9,    // Young's modulus (Pa) - carbon steel
    7800.0,   // tube material density (kg/m3)
    2.0,      // shell-side crossflow velocity (m/s)
    50.0,     // shell fluid density (kg/m3)
    800.0,    // tube fluid density (kg/m3)
    0.5,      // shell ID (m)
    340.0,    // sonic velocity in shell fluid (m/s)
    0.03,     // damping ratio
    true);    // triangular pitch

System.out.println(result.getSummary());
if (!result.passed) {
    System.out.println("REDESIGN NEEDED: vibration risk detected");
}
```

### Individual Calculations

```java
// Natural frequency (Hz) - beam vibration theory
double fn = VibrationAnalysis.calcNaturalFrequency(
    0.01905, 0.01483, 0.4, 200e9, 7800.0, 800.0, 50.0, "pinned");

// Vortex shedding frequency (Hz)
double fvs = VibrationAnalysis.calcVortexSheddingFrequency(2.0, 0.01905, 0.02381);

// Critical velocity for fluid-elastic instability (m/s)
double vCrit = VibrationAnalysis.calcCriticalVelocityConnors(
    fn, 0.01905, 0.03, 1.5, 50.0, true);

// Acoustic resonance frequency (Hz)
double fac = VibrationAnalysis.calcAcousticFrequency(0.5, 340.0, 1);
```

### End Conditions

The natural frequency depends on tube support boundary conditions:

| End Condition | Eigenvalue $C_n$ | When to Use |
|---------------|-------------------|-------------|
| `"pinned"` | 9.87 ($\pi^2$) | Default, conservative |
| `"fixed"` | 22.37 | Expanded or welded tubes |
| `"clamped-pinned"` | 15.42 | One end expanded, one supported |

---

## Zone-by-Zone Analysis

For services involving phase change (condensation or evaporation), the
`ThermalDesignCalculator` supports zone-by-zone analysis. Each zone can have
different fluid properties and HTC correlations:

```java
import neqsim.process.mechanicaldesign.heatexchanger.ThermalDesignCalculator;

ThermalDesignCalculator calc = new ThermalDesignCalculator();
// (configure geometry and fluid properties as above)

// Define zones for a condenser
ThermalDesignCalculator.ZoneDefinition zone1 = new ThermalDesignCalculator.ZoneDefinition();
zone1.zoneName = "desuperheating";
zone1.dutyFraction = 0.15;
zone1.totalDuty = 150000.0;   // W
zone1.lmtd = 40.0;            // K
zone1.tubeDensity = 5.0;
zone1.tubeViscosity = 1.2e-5;
zone1.tubeCp = 2100.0;
zone1.tubeConductivity = 0.025;
zone1.shellDensity = 998.0;
zone1.shellViscosity = 0.001;
zone1.shellCp = 4180.0;
zone1.shellConductivity = 0.60;

// Add more zones (condensing, subcooling) similarly
ThermalDesignCalculator.ZoneDefinition[] zones =
    new ThermalDesignCalculator.ZoneDefinition[] { zone1 /*, zone2, zone3 */ };

ThermalDesignCalculator.ZoneResult[] results = calc.calculateZones(zones);
for (ThermalDesignCalculator.ZoneResult zr : results) {
    System.out.println(zr.zoneName + ": U=" + zr.overallU + " area=" + zr.requiredArea);
}
```

---

## Full Mechanical Design Integration

When used through `HeatExchangerMechanicalDesign`, the thermal calculations are
triggered automatically. Fluid properties are extracted from the process streams:

```java
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.mechanicaldesign.heatexchanger.HeatExchangerMechanicalDesign;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

SystemSrkEos fluid = new SystemSrkEos(273.15 + 60.0, 20.0);
fluid.addComponent("methane", 120.0);
fluid.addComponent("ethane", 120.0);
fluid.addComponent("n-heptane", 3.0);
fluid.createDatabase(true);
fluid.setMixingRule(2);

Stream hot = new Stream("hot", fluid);
hot.setTemperature(100.0, "C");
hot.setFlowRate(1000.0, "kg/hr");

Stream cold = new Stream("cold", fluid.clone());
cold.setTemperature(20.0, "C");
cold.setFlowRate(310.0, "kg/hr");

HeatExchanger hx = new HeatExchanger("E-100", hot, cold);
hx.setUAvalue(1000.0);

ProcessSystem ps = new ProcessSystem();
ps.add(hot);
ps.add(cold);
ps.add(hx);
ps.run();

// Mechanical design runs thermal calc automatically
hx.initMechanicalDesign();
HeatExchangerMechanicalDesign design = hx.getMechanicalDesign();
design.calcDesign();

System.out.println("Selected type: " + design.getSelectedType());
System.out.println("Required area: " + design.getSelectedSizingResult().getRequiredArea());
System.out.println("Weight: " + design.getWeightTotal() + " kg");
```

---

## API Reference Summary

### ThermalDesignCalculator

| Method | Description |
|--------|-------------|
| `calculate()` | Runs complete thermal-hydraulic calculation |
| `getTubeSideHTC()` | Tube-side heat transfer coefficient (W/m$^2$K) |
| `getShellSideHTC()` | Shell-side heat transfer coefficient (W/m$^2$K) |
| `getOverallU()` | Overall heat transfer coefficient (W/m$^2$K) |
| `getTubeSidePressureDrop()` | Tube-side pressure drop (Pa) |
| `getTubeSidePressureDropBar()` | Tube-side pressure drop (bar) |
| `getShellSidePressureDrop()` | Shell-side pressure drop (Pa) |
| `getShellSidePressureDropBar()` | Shell-side pressure drop (bar) |
| `getTubeSideVelocity()` | Tube-side flow velocity (m/s) |
| `getShellSideVelocity()` | Shell-side flow velocity (m/s) |
| `getTubeSideRe()` | Tube-side Reynolds number |
| `getShellSideRe()` | Shell-side Reynolds number |
| `toMap()` | All results as a `Map` for JSON serialization |
| `calculateZones(List)` | Zone-by-zone analysis for phase-change services |

### LMTDcorrectionFactor

| Method | Description |
|--------|-------------|
| `calcFt1ShellPass(...)` | F_t for 1-2N configuration |
| `calcFt2ShellPass(...)` | F_t for 2-4 configuration |
| `calcFt(..., shellPasses)` | F_t for any number of shell passes |
| `calcFtFromRP(R, P, N)` | F_t from dimensionless R and P |
| `calcR(...)` | Capacity ratio R |
| `calcP(...)` | Thermal effectiveness P |
| `requiredShellPasses(...)` | Minimum shell passes for acceptable F_t |

### BellDelawareMethod

| Method | Description |
|--------|-------------|
| `calcIdealCrossflowHTC(...)` | Zhukauskas ideal tube-bank HTC |
| `calcKernShellSideHTC(...)` | Kern method shell-side HTC |
| `calcKernShellSidePressureDrop(...)` | Kern method shell-side dP |
| `calcShellEquivDiameter(...)` | Shell equivalent diameter for Kern |
| `calcCrossflowArea(...)` | Bundle crossflow area |
| `calcJc(baffleCut)` | Baffle cut correction |
| `calcJl(...)` | Baffle leakage correction |
| `calcJb(...)` | Bundle bypass correction |
| `calcJs(...)` | Unequal spacing correction |
| `calcJr(Re, rows)` | Adverse temperature gradient correction |
| `calcCorrectedHTC(...)` | HTC with all J-factors applied |

### VibrationAnalysis

| Method | Description |
|--------|-------------|
| `performScreening(...)` | Complete 4-mechanism vibration screening |
| `calcNaturalFrequency(...)` | Tube natural frequency (beam theory) |
| `calcVortexSheddingFrequency(...)` | Vortex shedding frequency |
| `calcCriticalVelocityConnors(...)` | Connors critical velocity |
| `calcAcousticFrequency(...)` | Shell acoustic resonance frequency |
| `calcEffectiveAcousticVelocity(...)` | Speed of sound with tube correction |

---

## Related Documentation

- [Heat Exchanger Equipment](../equipment/heat_exchangers) - Process simulation usage
- [Heat Exchanger Mechanical Design](../../wiki/heat_exchanger_mechanical_design) - Mechanical design overview
- [TEMA Standard Guide](tema_standard_guide) - TEMA classification and standards
