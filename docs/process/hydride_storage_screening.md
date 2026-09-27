---
title: Solid Hydride Hydrogen Storage Screening
description: Connect open molecular or crystallographic material data to hydrogen gas fugacity and pressure-dependent desorption equilibrium in NeqSim.
---

# Solid hydride storage screening

`HydrideStorageScreening` bridges material-level calculations and process gas
thermodynamics. An atomistic tool such as ASE and Quantum ESPRESSO supplies
composition, crystal volume and a **reaction** enthalpy and entropy. NeqSim
supplies the gas composition, temperature, pressure, and hydrogen fugacity.
The utility does not perform density functional theory or predict hydride
chemistry from a formula alone.

For a hydride with $n_H$ hydrogen atoms per formula unit, hydrogen-free host
molar mass $M_{host}$, $Z$ formula units per cell, and unit-cell volume $V$:

$$C_{wt}=100\frac{n_H M_H}{M_{host}+n_H M_H}$$

$$\rho_{H_2}=\frac{Z n_H M_H}{N_A V}$$

The first result is wt% of a fully hydrogenated solid. The second is grams of
hydrogen per litre of ideal crystal, with $V$ converted from Å³ to litres.
These are theoretical upper limits. Neither accounts for bed porosity,
containers, incomplete conversion, cycling losses or usable operating window.

For a specified desorption reaction with unit solid activities, and reaction
enthalpy and entropy **per mole of released H₂**:

$$\Delta G_{des}=\Delta H_{des}-T\Delta S_{des}+RT\ln(f_{H_2}/1\,\mathrm{bara})$$

At equilibrium, $\Delta G_{des}=0$. The gas fugacity is
$f_{H_2}=\phi_{H_2}y_{H_2}P$, with absolute pressure $P$ in bara. Obtain
$\phi_{H_2}$ and $y_{H_2}$ from a NeqSim gas-phase calculation; a pressure
alone is an ideal-gas approximation. A positive $\Delta G_{des}$ suppresses
desorption for the **assumed** products, while a negative value favours it.

```java
import neqsim.thermo.util.hydrogen.HydrideStorageScreening;

double capacityWtPercent =
    HydrideStorageScreening.gravimetricCapacityPercent(6.94 + 10.81, 3);
double crystalDensityGPerL =
    HydrideStorageScreening.volumetricHydrogenDensityGPerL(3, 1, 29.45);
double entropyJPerMolH2K = 130.7;
double enthalpyJPerMolH2 = 457.7 * entropyJPerMolH2K;
double equilibriumTemperatureK = HydrideStorageScreening.equilibriumTemperatureK(
    10.0, enthalpyJPerMolH2, entropyJPerMolH2K);
```

The numerical methods in this example are exercised in
`HydrideStorageScreeningTest`. The LiBH₃ composition and 29.45 Å³ cell volume
are from Geldasa and Dejene (2026), [DOI:10.1007/s00894-026-06861-x](https://doi.org/10.1007/s00894-026-06861-x).
The illustrative 457.7 K reference temperature is their **LiCuH₃** Table 5
result. It sets a separate reaction-parameter example and must not be
assigned to LiBH₃. Their abstract instead reports 382.7 K for LiCuH₃;
the discrepancy needs resolution before quantitative material ranking.
Formation energy in eV/atom cannot be substituted for desorption enthalpy in
J/mol H₂. The identity and free energy of competing solid products must be
checked before treating any pressure curve as a realizable phase boundary.

Further limits: constant $\Delta H$/$\Delta S$, a 1 bara fugacity standard,
no temperature-dependent heat capacities, solid nonideality, phase
transitions, diffusion, kinetics, hysteresis or cycling. The result is a
thermodynamic screening calculation, not a validated material or vessel design.
