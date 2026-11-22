---
title: Pitzer hydrate equilibrium for brines
description: CO2 and general gas-hydrate onset calculations coupled to Pitzer aqueous activities, with explicit parameter coverage and model limits.
---

`SystemPitzer` supports incipient hydrate temperature, pressure and equilibrium curves through
`ThermodynamicOperations`. This couples the existing Pitzer aqueous model and SRK fluid phases to
van der Waals–Platteeuw hydrate equilibrium. It calculates the onset boundary; it does not calculate
hydrate production, induction time, plugging probability or mud rheology.

## CO2 with NaCl, KCl, CaCl2 and MgCl2

Add salts as charge-balanced ions. `addComponent` takes **moles**, not salt mass fraction or molarity.
For one kilogram of water, adding one mole of Na+ and one mole of Cl- specifies an initial NaCl
molality of 1 mol/kg water. Dissolved CO2 and water partitioning are recalculated in every fluid flash.

The PHREEQC catalog contains CO2 self/ion lambda terms and the chloride binary/mixed-ion parameters,
but lacks explicit CO2–cation–chloride zeta terms. `applyPhreeqcCo2ChlorideParameters` requires the
caller to supply these terms for every present cation. It supports Na+, K+, Ca++ and Mg++, with Cl-,
CO2 and water. Additional chemical species require a different, complete parameter setup.
For mixtures containing both K+ and Mg++, the pinned catalog also lacks a K-Mg theta row.
Use `applyPhreeqcCo2ChlorideParameters(co2ChlorideZeta, potassiumMagnesiumTheta)` to supply
that constant explicitly; the one-argument method rejects this mixture. The additional theta
is in kg/mol and must be finite. Its value is a caller assumption unless supported by independent data.
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

For another concentration, change the salt and water mass fractions before converting to moles.
For mixed salts, divide each salt mass by the **total water mass**, then divide by that salt's
molar mass. CaCl2 and MgCl2 each contribute two moles of Cl- per mole of salt. The test fixture
uses anhydrous molar masses of 0.05844, 0.0745513, 0.11098 and 0.095211 kg/mol for NaCl, KCl,
CaCl2 and MgCl2, respectively. A molarity in mol/L is not a molality in mol/kg water.

When calculating a salt-induced temperature shift, use the same neutral interaction parameters
for the fresh and saline cases. Retain zero-amount ions in the fresh case and apply the same
explicit dataset. Otherwise switching to a different default CO2 self-interaction model can
produce an apparent salt effect even as the salt concentration tends to zero.

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

Within the selected bracket, the search reuses both endpoint residuals for a
secant prediction and bisects at least every second trial. Non-finite, rounded
or out-of-bracket predictions also use bisection. Every accepted trial still
executes a complete fluid TP flash; no water-activity or guest-fugacity result
is reused at a different state. Pressure searches preserve the first crossing
found by scanning upward from the lower pressure bound. The residual tolerance,
search bounds, structure selection and parameter coverage are unchanged.

## MEG (monoethylene glycol)

Add `"MEG"` as a component. `PitzerMegParameters` (dataset `neqsim-pitzer-meg-nacl-v1`) loads automatically
with either the PHREEQC catalog or the legacy parameter table. MEG is a neutral solute on the molality scale:

$$\phi = 1 + \lambda_{MM}(T)\,m + 2\mu_{MMM}(T)\,m^2,\qquad \ln a_w = -M_w m \phi,$$

with $\lambda$ and $\mu$ in the PHREEQC six-term form (terms 0 and 1 used, $T_r$ = 298.15 K). MEG's molality
Henry reference is $M_w\,p^{sat}_{MEG}(T)$, which keeps it in the aqueous phase; it sets only the small MEG
volatility, not the water activity. Before this dataset existed, MEG had no Henry reference. It went to the SRK gas
phase (6.5 mol% at 10 °C and 100 bara) and the water activity stayed at 1, so the hydrate temperature was not
suppressed at all.

```java
SystemPitzer fluid = new SystemPitzer(285.0, 100.0);
fluid.addComponent("methane", 86.0);
fluid.addComponent("ethane", 7.0);
fluid.addComponent("propane", 3.0);
fluid.addComponent("water", 1.0 / 0.01801528);        // 1 kg water
fluid.addComponent("MEG", 0.3 / 0.7 / 0.06207);       // 30 wt% MEG, salt-free basis
fluid.addComponent("Na+", 0.05 / 0.95 / 0.05844);     // optional NaCl
fluid.addComponent("Cl-", 0.05 / 0.95 / 0.05844);
fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);
new ThermodynamicOperations(fluid).hydrateFormationTemperature();
```

**Regression data and fit (NeqSim, 21 Nov 2026).**

| Term | Data | Fit quality |
|---|---|---|
| $\lambda_{MM}$ = 3.0491e-3 + 71.86(1/T − 1/T_r); $\mu_{MMM}$ = −2.9662e-4 − 1.5291(1/T − 1/T_r) | Tsurko et al. (2026), *J. Chem. Eng. Data* 71, 942–959: freezing points (Table 2, 2–17.6 mol/kg, 233–269 K; $a_w$ from ice equilibrium, Koop et al. 2000) and isopiestic $a_w$ at 298.15 K (Table 44, 1.1–20.7 mol/kg) | 298 K: abs($\Delta a_w$) ≤ 0.0043. Freezing: abs($\Delta T_f$) ≤ 0.45 K up to 10 mol/kg, ≤ 1.7 K at 12–17.6 mol/kg |
| $\lambda_{M,Na^+}$ = 0.14781 (constant), $\lambda_{M,Cl^-}$ = 0, $\zeta_{M,Na,Cl}$ = 0 | Ternary melting-point equation of Benson et al. (2010), *Cryobiology* 61, 352–356, EG:NaCl mass ratio 5–45, total solute 5–30 wt% (NaCl ≤ 1.22 mol/kg), evaluated with NeqSim's NaCl parameters | rms 0.70 K, max 1.80 K in freezing temperature |

Only the sum $\lambda_{M,Na}+\lambda_{M,Cl}$ is identifiable from single-salt water activities; by convention all of it
is assigned to Na+. A fitted $\zeta$ lowered the rms to 0.48 K but drifted 1.5 K from e-CPA at 10 wt% NaCl
(outside the data), so it was not adopted.

**Hydrate cross-check against `SystemElectrolyteCPAstatoil`** (gas C1–C4 + CO2, no N2, 1 kg water basis,
21 Nov 2026):

| P (bara) | MEG wt% | NaCl wt% | e-CPA (°C) | Pitzer (°C) |
|---|---|---|---|---|
| 100 | 20 | 0 | 15.15 | 15.38 |
| 100 | 20 | 5 | 12.14 | 11.82 |
| 100 | 30 | 3 | 9.18 | 8.75 |
| 200 | 10 | 10 | 15.32 | 15.23 |

With gas + oil + 30 wt% MEG, Pitzer's MEG suppression agrees with CPA within 0.3 K at 50–250 bara. These are
model-to-model comparisons, not validation against hydrate measurements.

**Scope.** MEG molality up to `PitzerMegParameters.MAX_MEG_MOLALITY` = 17.6 mol/kg (about 52 wt%); the hydrate
flash throws above it (the fitted polynomial is not monotonic beyond the data). Na+ and Cl- are the only ions with
MEG rows; K+, Ca++ or Mg++ with MEG fail the neutral coverage audit with a diagnostic. MEG with methanol or another
liquid co-solvent fails closed. Dissolved gases and hydrocarbons get explicit zero rows with MEG (and zero self, ion
and mu rows where no other dataset defines them); `PhasePitzer.getMegAssumedZeroRows()` lists them. Activating the
neutral MEG rows also switches the dissolved-gas Henry reference to IAPWS G7-04 for supported gases, so methane
solubility in the brine becomes non-zero. The Henry-range lower limit below still applies, so with nitrogen present
the hydrate search cannot go below 278.12 K: many MEG cases then need e-CPA.

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
- Oil-based mud, polymers, glycols other than MEG, surfactants, barite and other drilling-fluid additives are not
  characterized. MEG has its own dataset (see above); methanol currently enters the brine with an ideal
  activity coefficient and no Pitzer parameters, so treat Pitzer methanol results as screening only.
- Ice and salt precipitation, including salt hydrates in concentrated cold brines, are not solved
  simultaneously. Hydrate phase amounts require a separate coupled solid-equilibrium implementation.

General electrolyte activity, osmotic coefficient, chemistry and scale workflows remain available
through the existing Pitzer API. See [parameter provenance and coverage](pitzer_parameter_provenance.md)
and [electrolyte phase boundaries](electrolyte_phase_boundaries.md).

## Independent references and concentration coverage

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

The extended fixture retains **all 57 measured points** in Burgass Table 4 and
Jibril, Burgass, Chapoy and Ahmadi (2025),
[CO2 and CO2-Rich Stream Hydrate Formation in Equilibrium with Brines in the Context of CO2 Injection](https://doi.org/10.1021/acs.jced.5c00173),
Tables 4–5 (CC BY 4.0). The latter contains NaCl/KCl/CaCl2/MgCl2 brines labeled 11.83, 13.65,
21.44 and 27.30 wt% total salt. Reported compositions are used directly, including the rounded
11.83 wt% brine's 100.01 total mass parts. All missing zeta terms and K-Mg theta are explicitly
zero in this assessment; none were fitted to the measurements.

| Brine | Calculated reference points | Pressure range of those points (bara) | Maximum absolute temperature error (K) | Within 1 K |
| --- | --- | --- | --- | --- |
| NaCl, 5 wt% | 4 | 21.80–42.26 | 0.462 | 4 |
| NaCl, 10 wt% | 1 | 24.79 | 0.019 | 1 |
| NaCl, 15 wt% | 1 | 194.77 | 0.871 | 1 |
| Four-chloride brine, 11.83 wt% label | 5 | 76.26–341.98 | 1.901 | 3 |
| Four-chloride brine, 13.65 wt% | 4 | 188.02–340.74 | 1.901 | 0 |

Thus **9 of 15 calculated temperatures meet the existing 1 K criterion; 6 do not**. The mixed-brine
errors are negative: the model underpredicts hydrate temperature, which can understate thermodynamic
hydrate risk. The 1 K criterion was retained, not widened to classify these cases as successful.
The 0.22 K expanded uncertainty reported by Jibril is smaller than these mixed-brine errors.
The single 10 and 15 wt% NaCl points do not establish accuracy across a continuous pressure/concentration range.

Of the remaining 42 measurements, 35 are below 274.19 K, 5 involve salt precipitation at initial
25 wt% NaCl, and 2 yield no bracket within the Henry-reference range (5 wt% NaCl at 17.36 bara
and the 13.65 wt% mixed brine at 30.06 bara). None are counted as passing predictions.
There is also one fluid-phase mismatch: the source reports vapor CO2 at the 5 wt% NaCl,
42.26 bara point, whereas SRK selects liquid CO2 at the calculated temperature. The temperature
comparison alone therefore does not validate phase identity near that transition.

A separate two-point MgCl2 inhibition comparison uses the measured 0.96 and 1.57 K suppressions
at 30 bara reported by [Ren et al. (2025)](https://doi.org/10.1021/acs.energyfuels.5c02264).
Their supporting-information Table S2 converts the 200 and 400 mM solutions to 1.90 and
3.81 wt%. This checks suppression using a consistent fresh-water reference; it does not qualify
absolute MgCl2 equilibrium curves. Calculated suppressions are 0.678 and 1.457 K, with maximum
absolute deviation 0.283 K (rounded upward). Standalone KCl and CaCl2 curves still lack independent
experimental temperature comparisons in this implementation.

The numerical concentration sweep covers each of NaCl, KCl, CaCl2 and MgCl2 at 0, 1, 2.5, 5,
7.5, 10, 15 and 20 wt%, at 30, 100 and 300 bara. **80 of 96 cases converge**, with decreasing
water activity and hydrate temperature as salt increases; 16 report no root within the supported
temperature range. This checks numerical behavior and ion conservation, not experimental accuracy
or solubility limits. At 30 bara all four salts converge through 10 wt%; higher concentrations
depend on salt and pressure. Concentrated and cold drilling fluids still require additional qualification.

Run `PitzerHydrateSalinityValidationTest` with `-DexcludedTestGroups=benchmark` to include these
slower tests. It writes every outcome to `target/pitzer-hydrate-reference-assessment.csv` and
`target/pitzer-hydrate-concentration-grid.csv`. The data and transcription notes are in
`src/test/resources/data/chemistry_benchmarks/pitzer_co2_hydrate_dissociation.csv` and
`pitzer_co2_hydrate_references.md`. A passing regression run preserves the documented accuracy
gaps; it does not mean that all reference rows passed scientific validation.
