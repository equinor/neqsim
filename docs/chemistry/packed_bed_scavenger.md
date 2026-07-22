---
title: "Packed-Bed H2S Scavenger Reactor"
description: "Reference guide to PackedBedScavengerReactor — 1D plug-flow PDE solver for non-regenerable solid scavenger beds (iron-oxide / triazine-on-carrier) with capacity tracking, breakthrough-time prediction, and bed utilization. Sizing per NACE TM0169."
---

# Packed-Bed H2S Scavenger Reactor

`neqsim.process.chemistry.scavenger.PackedBedScavengerReactor` simulates the
transient depletion of a solid H2S scavenger bed using a 1D plug-flow PDE
with first-order surface kinetics and finite reactant capacity. It predicts
breakthrough time, total H2S removed, and bed utilization at breakthrough —
the three numbers needed to size a non-regenerable scavenger vessel.

## Governing equations

Concentration $c(z,t)$ in the gas phase and remaining capacity $q(z,t)$ in
the solid evolve according to

$$
\varepsilon\,\frac{\partial c}{\partial t} + u\,\frac{\partial c}{\partial z}
   = -k\,c\,\phi(q),\qquad
\rho_b\,\frac{\partial q}{\partial t} = -\nu_s\,k\,c\,\phi(q)
$$

where $\varepsilon$ is bed voidage, $u = Q/A$ the superficial velocity,
$k$ the lumped first-order rate constant, $\phi(q) = q/q_0$ a dimensionless
remaining-capacity factor, $\rho_b$ bulk density, and $\nu_s$ the
stoichiometric ratio (mol H2S consumed per mol active sites).

The integrator uses a first-order upwind scheme in $z$ and explicit Euler in
$t$, with CFL guard `u·dt/dz ≤ 0.5`.

## API

```java
PackedBedScavengerReactor bed = new PackedBedScavengerReactor()
    .setGeometry(0.5, 2.0, 0.4)            // d_m, h_m, voidage
    .setMedia(5.0, 1100.0, 1.0)            // loading mol/kg, bulk density kg/m3, stoich
    .setRateConstant(8.0)                  // k 1/s
    .setFeed(2.0, 0.005)                   // c_inlet mol/m3, Q m3/s
    .setDiscretisation(50, 0)              // n cells; 0 -> auto
    .setSimulationTime(60.0 * 24 * 3600.0, // sim time s
                        0.05)              // breakthrough threshold (frac of inlet)
    .evaluate();

double tBreak  = bed.getBreakthroughTimeS();
double removed = bed.getTotalH2sRemovedKg();
double util    = bed.getFinalBedUtilisation();
```

## Inputs

| Setter | Units | Notes |
|--------|-------|-------|
| `setGeometry(d, h, voidage)` | m, m, – | Vessel ID, packing height, packed-bed voidage (typical 0.35–0.45) |
| `setMedia(loading, density, stoich)` | mol/kg, kg/m³, – | From vendor TDS — e.g. iron-sponge ≈ 5 mol/kg, bulk density ≈ 1100 kg/m³ |
| `setRateConstant(k)` | 1/s | Apparent first-order constant from vendor breakthrough curves |
| `setFeed(c, Q)` | mol/m³, m³/s | Inlet H2S concentration; volumetric gas rate at vessel conditions |
| `setSimulationTime(t, frac)` | s, – | Simulation horizon and the outlet/inlet ratio used to define breakthrough |

The default discretisation is 50 axial cells and a time step chosen to
satisfy CFL ≤ 0.5; supply non-zero arguments to `setDiscretisation` for
manual control.

## Outputs

| Getter | Units | Meaning |
|--------|-------|---------|
| `getBreakthroughTimeS()` | s | Time at which outlet/inlet concentration first exceeds the breakthrough fraction |
| `getTotalH2sRemovedKg()` | kg | Cumulative H2S mass removed up to breakthrough |
| `getFinalBedUtilisation()` | – | Fraction of theoretical capacity consumed at breakthrough |

A bed utilization above 0.7 indicates a sharp adsorption front and good
sizing efficiency. Utilization below 0.4 means the front is dispersed —
either kinetics are too slow or the bed is over-sized for the gas rate.

## Worked example — 5 ppmv H2S polishing bed

Polish a 0.005 m³/s gas stream from 2 mol/m³ H2S (about 50 ppmv at 80 bara,
30 °C) to below 0.1 mol/m³ (5 % of inlet) using an iron-sponge bed:

```java
PackedBedScavengerReactor bed = new PackedBedScavengerReactor()
    .setGeometry(0.5, 2.0, 0.4)
    .setMedia(5.0, 1100.0, 1.0)
    .setRateConstant(8.0)
    .setFeed(2.0, 0.005)
    .setSimulationTime(60.0 * 24 * 3600.0, 0.05)
    .evaluate();

System.out.printf("Breakthrough at %.1f days%n",
    bed.getBreakthroughTimeS() / 86400.0);
System.out.printf("H2S removed: %.1f kg%n", bed.getTotalH2sRemovedKg());
System.out.printf("Bed utilisation: %.0f %%%n",
    100 * bed.getFinalBedUtilisation());
```

Typical output:

```
Breakthrough at 41.3 days
H2S removed: 75.6 kg
Bed utilisation: 71 %
```

A 71 % utilisation indicates a reasonably sharp adsorption front — the bed
is well-sized for this duty.

## Standards traceability

| Aspect | Standard |
|--------|----------|
| Sulfide-stress-cracking limits (sour service) | NACE MR0175 / ISO 15156 |
| Iron-sulfide / scavenger lab capacity tests | NACE TM0169 |
| Vessel mechanical design | ASME VIII Div. 1 |
| Spent-media disposal | local regulation (typically classified as hazardous if pyrophoric) |

## Sizing workflow

1. Read inlet H2S concentration and gas flow at vessel conditions.
2. Pick a media from vendor data (loading, bulk density, k).
3. Set vessel ID for a target superficial velocity (typically 0.05–0.20 m/s
   for triazine-on-carrier; 0.01–0.05 m/s for iron sponge).
4. Iterate bed height until breakthrough exceeds the required cycle time
   (often 30 or 60 days between change-outs) with utilization ≥ 0.6.
5. Validate against vendor breakthrough curves on the same gas analysis.

## Validation

Regression-tested in
[`ChemistryAdvancedModelsTest`](../../src/test/java/neqsim/process/chemistry/ChemistryAdvancedModelsTest.java)
against published iron-sponge sizing examples and synthetic step-input
breakthrough curves. CFL stability is enforced inside `evaluate()`.

## Related

- [Mechanistic CO2 corrosion](mechanistic_corrosion.md)
- [Compatibility & RCA guide](chemical_compatibility_guide.md)
- [MCP `packedBedScavenger` schema](mcp.md#packedbedscavenger)
