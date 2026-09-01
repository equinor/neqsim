---
title: Experimental CO2 impurity kinetic reactor
description: Scope, reaction network, numerical safeguards, and Java usage for trace-impurity kinetics in CO2 streams.
---

# Experimental CO2 impurity kinetic reactor

`CO2ImpurityKineticReactor` is an isothermal plug-flow equipment model for studying possible
trace-impurity reaction trends in CO2-rich streams. It applies a concentration-based Arrhenius
network over a specified residence time and returns a reacted NeqSim outlet stream.

> **Model status:** experimental. The default kinetic parameters are illustrative and are not a
> qualified design correlation. Calibrate and validate the model against representative laboratory
> data before using predicted conversion or acid/sulfur formation in engineering decisions.

## Reaction network

All reactions use balanced stoichiometry. R3b has the same net reaction as R1; H2S and NO2 affect
its rate as co-catalysts but are not consumed by R3b.

| ID | Net reaction | Role |
|---|---|---|
| R1 | $\mathrm{SO_2 + 0.5 O_2 + H_2O \rightarrow H_2SO_4}$ | Direct SO2 oxidation |
| R2 | $\mathrm{H_2S + 3 NO_2 \rightarrow SO_2 + H_2O + 3 NO}$ | H2S oxidation by NO2 |
| R3A | $\mathrm{SO_2 + NO_2 + H_2O \rightarrow H_2SO_4 + NO}$ | NO2-assisted SO2 oxidation |
| R3B | $\mathrm{SO_2 + 0.5 O_2 + H_2O \rightarrow H_2SO_4}$ | H2S/NO2 co-catalysed pathway |
| R4 | $\mathrm{2 NO + O_2 \rightarrow 2 NO_2}$ | NO oxidation |
| R5 | $\mathrm{3 NO_2 + H_2O \rightarrow 2 HNO_3 + NO}$ | Nitric-acid formation |
| R6 | $\mathrm{H_2S + 1.5 O_2 \rightarrow SO_2 + H_2O}$ | H2S oxidation by oxygen |
| R7 | $\mathrm{5 H_2S + 6 NO + 4 H_2O \rightarrow 6 NH_3 + 5 SO_2}$ | Reduced nitrogen/sulfur pathway |
| R8 | $\mathrm{H_2S + 0.5 O_2 \rightarrow \frac{1}{8}S_8 + H_2O}$ | Wall-material-dependent sulfur formation |

The Arrhenius expression is

$$k_j(T)=A_j\exp\left(-\frac{E_{a,j}}{RT}\right)$$

where $T$ is in K, $R=8.314462618\ \mathrm{J\,mol^{-1}\,K^{-1}}$, and $E_a$ is
stored in J/mol. Rate laws use activities formed by dividing each overall molar concentration by
the reference concentration $1\ \mathrm{kmol/m^3}$. Consequently, the configurable $A_j$ values
have units of $\mathrm{s^{-1}}$ in this implementation.

## Numerical and physical safeguards

- The inlet system is cloned; the input stream is not mutated.
- Each integration extent is limited by the available reactants.
- Balanced stoichiometric vectors conserve H, N, O, and S atoms.
- Component inventories are kept non-negative.
- Temperature, pressure, geometry, flow, residence time, material, and kinetic inputs are validated.
- Geometry-derived residence time uses density from a NeqSim TP flash of the actual inlet fluid.
- `carbon_steel` and `magnetite` use the R8CS parameter pair; `stainless_steel` and `inert` use R8SS.

The current reactor is isothermal and uses overall concentrations. It does not yet resolve
phase-specific reaction volumes, mass-transfer limitation, wall-area scaling, corrosion films,
heat release, precipitation feedback, or activity/fugacity coefficients in the kinetic driving
force. Those limitations must be considered when interpreting results.

## Java example

```java
SystemInterface fluid = new SystemSrkEos(298.15, 100.0);
fluid.addComponent("CO2", 1.0);
fluid.addComponent("H2S", 10.0e-6);
fluid.addComponent("SO2", 10.0e-6);
fluid.addComponent("NO2", 30.0e-6);
fluid.addComponent("oxygen", 100.0e-6);
fluid.addComponent("water", 50.0e-6);
fluid.setMixingRule("classic");

Stream feed = new Stream("CO2 feed", fluid);
feed.run();

CO2ImpurityKineticReactor reactor =
    new CO2ImpurityKineticReactor("CO2 impurity reactor", feed);
reactor.setMaterial("carbon_steel");
reactor.setResidenceTime(6.0 * 3600.0);
reactor.setReactionConstants("R2", 5.0e7, 28.0);
reactor.run();

SystemInterface reactedFluid = reactor.getOutletStream().getThermoSystem();
```

`setReactionConstants` accepts R1, R2, R3A, R3B, R4-R7, R8CS, and R8SS. `R8` updates the
parameter family selected by the current material. The activation-energy argument is in kJ/mol.

For a laboratory vessel, `setReactorGeometry(diameterCm, volumeMl, massFlowGPerHour)` makes
`run()` derive residence time from actual inlet-fluid density. `generateReactorReport()` reports
the same density, geometry, inventory, and residence-time basis.

## Validation expectations

Before engineering use, compare the selected network and parameters against independent data over
the intended pressure, temperature, impurity, water, phase, residence-time, and material ranges.
At minimum, verify elemental conservation, non-negative compositions, residence-time trends,
material trends, sensitivity to fitted parameters, and uncertainty in extrapolation.
