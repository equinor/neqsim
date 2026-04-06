---
name: neqsim-distillation-design
description: "Distillation column design rules for NeqSim. USE WHEN: setting up distillation columns, troubleshooting convergence, selecting internals (trays/packing), sizing columns, or analyzing column performance. Covers DistillationColumn setup, solver selection, feed tray optimization, reflux ratio, and internals selection per industry standards."
---

# Distillation Design Rules

Guide for distillation column modeling and design in NeqSim.

## Column Setup Pattern

```java
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

// Create feed
SystemInterface feed = new SystemSrkEos(273.15 + 50.0, 15.0);
feed.addComponent("methane", 0.10);
feed.addComponent("ethane", 0.25);
feed.addComponent("propane", 0.30);
feed.addComponent("n-butane", 0.20);
feed.addComponent("n-pentane", 0.15);
feed.setMixingRule("classic");

Stream feedStream = new Stream("feed", feed);
feedStream.setFlowRate(10000.0, "kg/hr");
feedStream.setTemperature(50.0, "C");
feedStream.setPressure(15.0, "bara");
feedStream.run();

// Create column (name, stages, hasCondenser, hasReboiler)
DistillationColumn column = new DistillationColumn("Deethanizer", 15, true, true);
column.addFeedStream(feedStream, 8);  // Feed on stage 8

// Specifications
column.setCondenserTemperature(273.15 - 30.0);  // Kelvin
column.getReboiler().setReboilerDuty(1e6);       // Watts
// OR set reflux ratio:
// column.getCondenser().setRefluxRatio(2.5);

column.run();
```

## Solver Selection

```java
// Standard (default) — Newton-Raphson stage-by-stage
column.setSolverType(DistillationColumn.SolverType.Standard);

// Inside-Out — faster for ideal/near-ideal systems
column.setSolverType(DistillationColumn.SolverType.INSIDE_OUT);

// Damped — for difficult convergence
column.setSolverType(DistillationColumn.SolverType.Standard);
// Adjust max iterations
column.setMaxNumberOfIterations(200);
```

### Solver Selection Guide

| System Type | Recommended Solver | Notes |
|------------|-------------------|-------|
| Ideal HC (demethanizer, deethanizer) | Inside-Out | Fast, robust |
| Non-ideal (alcohols, water) | Standard | Better for non-ideal K-values |
| Absorbers (no condenser/reboiler) | Standard | Inside-Out needs reflux |
| Wide-boiling (C1 to C20+) | Standard with damping | Increase iterations |
| Cryogenic (< -100°C) | Standard | Careful with phase identification |

## Column Specification Combinations

| Bottom Spec | Top Spec | Comment |
|------------|---------|---------|
| Reboiler duty | Condenser temperature | Most common |
| Reboiler duty | Reflux ratio | Alternative |
| Bottom product rate | Condenser temperature | Product-based |
| Bottom temperature | Reflux ratio | Direct T control |

```java
// Common specification patterns

// Pattern 1: Condenser T + Reboiler duty
column.setCondenserTemperature(273.15 - 30.0);
column.getReboiler().setReboilerDuty(1.5e6);

// Pattern 2: Reflux ratio + Reboiler duty
column.getCondenser().setRefluxRatio(3.0);
column.getReboiler().setReboilerDuty(2.0e6);

// Pattern 3: Product rates
column.getCondenser().setDistillateFlowRate(5000.0, "kg/hr");
column.getReboiler().setReboilerDuty(1.5e6);
```

## Reading Column Results

```java
column.run();

// Condenser and reboiler duties
double condenserDuty = column.getCondenser().getDuty();  // Watts
double reboilerDuty = column.getReboiler().getDuty();    // Watts

// Product streams
Stream overhead = (Stream) column.getGasOutStream();
Stream bottoms = (Stream) column.getLiquidOutStream();

// Stage temperatures and compositions
for (int stage = 0; stage < column.getNumberOfStages(); stage++) {
    double stageTemp = column.getTray(stage).getTemperature() - 273.15;
    // Composition on each stage
}

// Convergence metrics
int iterations = column.getLastIterationCount();
double massResidual = column.getLastMassResidual();
double energyResidual = column.getLastEnergyResidual();
```

## Feed Tray Location Rules

### Kirkbride Correlation

For binary or pseudo-binary separations:

$$
\log\left(\frac{N_R}{N_S}\right) = 0.206 \log\left[\left(\frac{B}{D}\right) \left(\frac{x_{HK,F}}{x_{LK,F}}\right)^2 \left(\frac{x_{LK,B}}{x_{HK,D}}\right)^2 \right]
$$

Where $N_R$ = rectifying stages, $N_S$ = stripping stages, $B/D$ = bottoms/distillate ratio.

### Rules of Thumb

| Column Type | Feed Tray (from top) | Notes |
|------------|---------------------|-------|
| Demethanizer | 40-60% of stages | Light key is very volatile |
| Deethanizer | 50-70% of stages | Moderate volatility |
| Depropanizer | 40-60% of stages | Balanced separation |
| Debutanizer | 50-60% of stages | Similar to depropanizer |
| Crude column | 60-80% of stages | Flash zone near bottom |

## Minimum Stages and Reflux

### Fenske Equation (Minimum Stages)

$$
N_{min} = \frac{\log\left(\frac{x_{LK,D}}{x_{HK,D}} \cdot \frac{x_{HK,B}}{x_{LK,B}}\right)}{\log(\alpha_{LK/HK})}
$$

### Underwood Equation (Minimum Reflux)

$$
R_{min} = \frac{1}{\alpha - 1}\left(\frac{x_D}{\alpha - \theta} - \frac{1 - x_D}{1 - \theta}\right)
$$

### Design Heuristics

- Actual stages ≈ 2 × minimum stages (Gilliland correlation)
- Actual reflux ≈ 1.2-1.5 × minimum reflux
- Stage efficiency: 50-70% for trays, 70-90% HETP/stage for packing

## Convergence Troubleshooting

| Problem | Solution |
|---------|----------|
| Column does not converge | Increase max iterations to 200-500 |
| Oscillating temperature profile | Reduce condenser/reboiler specs, use Standard solver |
| Wrong product split | Check feed tray location and specifications |
| Negative flows on stages | Too many stages or wrong specifications |
| Condenser too cold | Check if subcooled liquid is physical (binary dewpoint) |
| Reboiler too hot | May be decomposing — check component stability |

### Steps to Debug

1. Start with fewer stages (5-8) and get convergence
2. Gradually increase stages
3. Use liberal specifications first (higher reflux), then tighten
4. Check feed condition (vapor fraction) — subcooled feed may need enthalpy adjustment
5. Verify component K-values make physical sense at column conditions

## Column Sizing (Diameter)

### Souders-Brown Correlation

$$
V_{flood} = K_{SB} \sqrt{\frac{\rho_L - \rho_V}{\rho_V}}
$$

Where $K_{SB}$ = 0.03-0.07 m/s for trays, 0.02-0.05 for packing.

Design velocity = 70-85% of flooding.

```java
// After running column, get phase properties for sizing
SystemInterface topFluid = column.getTray(0).getFluid();
topFluid.initProperties();
double rhoV = topFluid.getPhase("gas").getDensity("kg/m3");
double rhoL = topFluid.getPhase("oil").getDensity("kg/m3");

double Ksb = 0.05;  // m/s for sieve trays
double Vflood = Ksb * Math.sqrt((rhoL - rhoV) / rhoV);
double Vdesign = 0.80 * Vflood;

double gasFlow = column.getGasOutStream().getFlowRate("m3/hr") / 3600.0;
double area = gasFlow / Vdesign;
double diameter = Math.sqrt(4.0 * area / Math.PI);
```

## Internals Selection

| Internals | When to Use | Typical HETP (m) |
|-----------|------------|-------------------|
| Sieve trays | General service, fouling | 0.5-0.7 |
| Valve trays | Variable turndown | 0.4-0.6 |
| Bubble cap trays | Low liquid rates | 0.5-0.8 |
| Random packing (Pall rings) | Low pressure drop, corrosive | 0.3-0.6 |
| Structured packing (Mellapak) | Vacuum, low ΔP | 0.2-0.5 |

## Common Pitfalls

1. **Feed flash**: Ensure feed is at correct T/P for column conditions
2. **Missing components**: All components in feed must be present in EOS
3. **Mixing rule**: Always set before column construction
4. **Heavy key in top / light key in bottom**: Small amounts are normal — zero means perfect separation (unrealistic)
5. **Column pressure profile**: Default is constant — set stage pressures for realistic profile
6. **Condenser type**: Total vs partial condenser changes mass balance
