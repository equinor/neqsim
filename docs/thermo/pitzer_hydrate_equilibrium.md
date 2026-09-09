---
title: Pitzer hydrate equilibrium for brines
description: CO2 and general gas-hydrate onset calculations coupled to Pitzer aqueous activities, with explicit parameter coverage and model limits.
---

# Pitzer hydrate equilibrium for brines

`SystemPitzer` supports incipient hydrate temperature, pressure and equilibrium curves through
`ThermodynamicOperations`. This couples the existing Pitzer aqueous model and SRK fluid phases to
van der Waals–Platteeuw hydrate equilibrium. It calculates the onset boundary; it does not calculate
hydrate production, induction time, plugging probability or mud rheology.

## CO2 with NaCl, KCl and CaCl2

Add salts as charge-balanced ions. `addComponent` takes **moles**, not salt mass fraction or molarity.
For one kilogram of water, adding one mole of Na+ and one mole of Cl- specifies an initial NaCl
molality of 1 mol/kg water. Dissolved CO2 and water partitioning are recalculated in every fluid flash.

The PHREEQC catalog contains CO2 self/ion lambda terms and the chloride binary/mixed-ion parameters,
but lacks explicit CO2–cation–chloride zeta terms. `applyPhreeqcCo2ChlorideParameters` requires the
caller to supply these terms for every present cation. It supports Na+, K+, Ca++ and Mg++, with Cl-,
CO2 and water. Additional chemical species require a different, complete parameter setup.
The method does not change default catalog selection or replace missing coefficients silently.
Apply it on a fresh system before flashing or defining custom interaction families; it overwrites
the named rows and does not clear unrelated custom terms.

```java
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemPitzer;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermodynamicoperations.flashops.saturationops.PitzerHydrateFlash;

public class Co2BrineHydrateExample {
  private static final Logger logger = LogManager.getLogger(Co2BrineHydrateExample.class);

  public static void main(String[] args) throws Exception {
    SystemPitzer fluid = new SystemPitzer(280.0, 40.0); // K, bara
    fluid.addComponent("CO2", 10.0);
    fluid.addComponent("water", 1.0 / 0.01801528); // 1 kg water
    fluid.addComponent("Na+", 0.5);
    fluid.addComponent("K+", 0.3);
    fluid.addComponent("Ca++", 0.2);
    fluid.addComponent("Cl-", 1.2); // 0.5 + 0.3 + 2*0.2
    fluid.setMixingRule("classic"); // Pitzer uses SRK, not CPA mixing rule 10
    fluid.setMultiPhaseCheck(true);

    Map<String, Double> co2ChlorideZeta = new LinkedHashMap<String, Double>();
    // EXPLICIT SCREENING ASSUMPTION: these zeros are not measured/fitted coefficients.
    // Supply validated coefficients for the actual brine when available.
    co2ChlorideZeta.put("Na+", 0.0);
    co2ChlorideZeta.put("K+", 0.0);
    co2ChlorideZeta.put("Ca++", 0.0);
    fluid.applyPhreeqcCo2ChlorideParameters(co2ChlorideZeta);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.hydrateFormationTemperature(); // automatically prepares the incipient hydrate phase
    PitzerHydrateFlash result = (PitzerHydrateFlash) ops.getOperation();
    logger.info("Hydrate temperature={} C, aw={}, log fugacity residual={}",
        fluid.getTemperature("C"), result.getWaterActivity(), result.getResidual());

    // Reverse calculation: fixed T, solve P. Use a clone to retain the first result.
    SystemPitzer pressureCase = fluid.clone();
    pressureCase.setPressure(20.0);
    new ThermodynamicOperations(pressureCase).hydrateFormationPressure();
    logger.info("Hydrate pressure={} bara", pressureCase.getPressure());
  }
}
```

For single-salt 5 wt% NaCl on a 1 kg water basis, the salt formula-unit amount is
`0.05 / (0.95 * 0.05844)` mol. Mass percent here is salt/(water + salt), excluding gas.

## Curves and operating margin

```java
// fluid is the configured SystemPitzer above. The curve operation works on a clone.
ThermodynamicOperations curveOps = new ThermodynamicOperations(fluid);
curveOps.hydrateEquilibriumLine(30.0, 100.0);
double[][] curve = curveOps.getOperation().getPoints(0);
// curve[0]: temperature in K; curve[1]: pressure in bara; ten equally spaced pressures.
```

At a specified pressure, define subcooling as `T_hydrate - T_operating` in K. A positive value
indicates operation below the calculated boundary. This thermodynamic comparison does not predict
formation rate or account for kinetic inhibitors. Preserve the operating state separately because
the single-point operations update the system to equilibrium.
On a failed single-point solve, temperature, pressure and the multiphase setting are restored,
but phase properties must be reflashed before use. Work on a clone when preserving the operating
state is required.

For a user-selected grid, clone the configured fluid per pressure and call
`hydrateFormationTemperature()`. A failed Pitzer point raises an exception; the curve operation
does not return a stale temperature as an equilibrium point. Narrow search bounds when needed:

```java
PitzerHydrateFlash search = new PitzerHydrateFlash(fluid, false); // false: solve T; true: solve P
search.setTemperatureBounds(276.0, 290.0);
search.setPressureBounds(10.0, 200.0);
search.setStructure(0); // 0: stable structure, 1: sI, 2: sII
search.run();
```

The usual `hydrateFormationTemperature(1)` and `(2)` overloads select sI and sII. The no-argument
method selects the more stable structure. The double initial-guess overload uses the bounded
Pitzer search; it does not rely on a Newton step from that guess. Structure 0 in the existing
integer overload denotes **ice**, which this coupling does not support.

## Thermodynamic formulation

For each candidate structure s, the implemented residual is

$$r_s = \frac{\Delta\mu^{\mathrm{empty-liquid}}_s}{RT}
       - \sum_c \nu_{s,c}\ln\left(1+\sum_j C_{s,c,j}f_j\right) - \ln a_w.$$

`ComponentHydratePitzer` reuses the standard hydrate model's Langmuir constants and empty-lattice
chemical-potential expression. Guest fugacities come from the equilibrated Pitzer aqueous phase;
at fluid equilibrium they equal those in the guest-rich phase. This also permits a liquid-CO2
branch without assuming active phase zero is gas. Ions are excluded from cage occupancy.

Water fugacity on both sides uses the Pitzer solvent reference, so the pure-water vapor pressure
cancels. An SRK pure-water reference on only the hydrate side would create a spurious contribution
to the equilibrium condition. The existing lattice-minus-liquid volume difference provides the
pressure contribution; no unmatched pure-water Poynting factor is added.

A fluid TP flash is executed at every trial, and a bracketed search accepts only
`abs(log(f_water_hydrate/f_water_aqueous)) <= 1e-8`. Numerical convergence is separate from model
accuracy. Bounds and missing-parameter errors are exceptions, not successful equilibrium results.

## Scope and qualification

- Numerical bounds are at most 323.15 K and 1000 bara. The actual lower temperature is the largest
  lower fitted Henry-reference limit among present supported guests: **274.19 K for CO2** and
  **275.46 K for methane**, for example. These numerical limits do not establish accuracy over the
  entire range. A root below the applicable limit is reported as unavailable.
- Subzero drilling-fluid calculations need a separate, qualified low-temperature dissolved-gas
  reference. The existing insoluble fallback outside its fitted range is not used as a hydrate model.
- Pitzer parameters, CO2 solubility, hydrate lattice parameters and gas EOS each have their own
  evidence ranges. Caller-supplied chloride zeta values remain a manual, unqualified dataset even
  when a specific hydrate benchmark passes. There is no automatic promotion of scientific qualification.
- The example contains no carbonate speciation or alkalinity. For pH or reactive electrolyte work,
  use the existing `SystemPitzer.chemicalReactionInit()` workflow with a reaction-compatible complete
  parameter dataset; the narrow chloride helper rejects additional species.
- Oil-based mud, polymers, glycols, surfactants, barite and other drilling-fluid additives are not
  characterized by the salt-only example. Organic-inhibitor activities require their own parameters.
- Ice and salt precipitation, including salt hydrates in concentrated cold brines, are not solved
  simultaneously. Hydrate phase amounts require a separate coupled solid-equilibrium implementation.

General electrolyte activity, osmotic coefficient, chemistry and scale workflows remain available
through the existing Pitzer API. See [parameter provenance and coverage](pitzer_parameter_provenance.md)
and [electrolyte phase boundaries](electrolyte_phase_boundaries.md).

## Independent reference

Burgass, R., Chapoy, A., Askvik, K.M., Neeraas, B.O. and Li, X. (2023),
[CO2 hydrate formation in NaCl systems and undersaturated aqueous solutions](https://doi.org/10.2516/stet/2023005),
*Science and Technology for Energy Transition* 78, 8, Table 4, CC BY 4.0. The tests compare the
measured 5 wt% NaCl dissociation temperatures at 21.80, 25.99, 32.43 and 42.26 bara. The 1 K
engineering comparison tolerance is larger than the reported 0.4 K expanded temperature uncertainty.
It is not a claim of agreement within experimental uncertainty or validation of all drilling fluids.

With the explicit zero-zeta screening assumption used in the tests:

| Pressure (bara) | Measured temperature (K) | Calculated temperature (K) | Deviation (K) |
| --- | --- | --- | --- |
| 21.80 | 275.90 | 275.814 | -0.086 |
| 25.99 | 277.30 | 277.103 | -0.197 |
| 32.43 | 278.90 | 278.614 | -0.286 |
| 42.26 | 280.60 | 280.139 | -0.461 |

Maximum absolute deviation is 0.462 K (rounded upward). No parameters were fitted to these data.
The lower-pressure 5 wt% points in Table 4 are excluded from this comparison: the 11.49 bara point
is subzero, and the calculated root at 17.36 bara falls below the CO2 Henry-reference limit.
Those points require an extended aqueous reference and are not represented as passing predictions.
