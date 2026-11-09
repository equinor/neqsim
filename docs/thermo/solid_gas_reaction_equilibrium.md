---
title: "Solid-gas reaction equilibrium (FeS to siderite and H2S)"
description: "Standard-state equilibrium constants for heterogeneous mineral-water-gas reactions with condensed-phase pressure corrections and EOS fugacities, applied to FeS + H2O + CO2 = FeCO3 + H2S."
keywords: "mineral reaction, equilibrium constant, troilite, siderite, pyrite, H2S, CO2, fugacity, Poynting, IAPWS-IF97, JANAF, reservoir souring, original reservoir H2S, mineral buffer, Haaland correlation"
---

`neqsim.thermo.util.mineral` calculates the thermodynamic limit of heterogeneous reactions
between pure solids, liquid water and gases. The built-in case is

$$
\mathrm{FeS(troilite)} + \mathrm{H_2O(l)} + \mathrm{CO_2(g)} \rightleftharpoons
\mathrm{FeCO_3(siderite)} + \mathrm{H_2S(g)}
$$

which bounds how much H2S an iron-sulfide-bearing rock can release into a CO2-bearing fluid.

## Method

Each `StandardStateSpecies` carries phase-specific thermochemistry at $p^\circ = 1$ bar. The
apparent Gibbs energy uses the Benson-Helgeson convention,

$$
G_i(T) = \Delta_f H^\circ_{i,298} + \left[H_i(T) - H_{i,298}\right] - T S_i(T),
$$

so element terms cancel in any atom-balanced reaction (checked by `checkElementBalance()`).
The standard constant is $\ln K^\circ = -\sum_i \nu_i G_i / (RT)$. At pressure $P$ the
equilibrium gas fugacity quotient is

$$
\prod_{\mathrm{gas}} \left(\frac{f_i}{p^\circ}\right)^{\nu_i} =
K^\circ(T)\,\exp\!\left(-\frac{\sum_{\mathrm{cond}} \nu_j I_j(T,P)}{RT}\right)
\prod_{\mathrm{cond}} a_j^{-\nu_j},
\qquad I_j = \int_{p^\circ}^{P} V_j\,dp .
$$

Solids use constant molar volumes; liquid water uses the IAPWS-IF97 Region 1 Gibbs function.
Gas non-ideality is not added as a separate volume term: `solveGasExchange` takes the
fugacities $f_i = x_i \varphi_i P$ from the NeqSim equation of state of the supplied fluid and
adjusts the product-gas amount until the quotient is met. Total pressure cancels only in the
ideal-gas part when $\Delta\nu_{\mathrm{gas}} = 0$; fugacity coefficients still matter.

| Mode | Behaviour |
|------|-----------|
| `FIXED_REACTANT` | CO2 held constant; H2S adjusted (large CO2 inventory) |
| `CONSERVE_GAS_SUM` | CO2 converted to H2S one-for-one (closed system, excess solid) |

## Data

| Species | Source | Range |
|---------|--------|-------|
| Troilite FeS | NIST-JANAF (Chase, 1998), three Shomate segments with the 411 K and 598 K transitions; $V$ = 18.20 cm³/mol (Robie and Hemingway, 1995) | 298–1463 K |
| Siderite FeCO3 | Robie and Hemingway (1995): $\Delta_f H^\circ$ = −755.9 ± 5.5 kJ/mol, $S^\circ$ = 95.47 ± 0.2 J/(mol K), $V$ = 29.38 cm³/mol | 298–600 K |
| H2O(l) | NIST-JANAF; pressure term from IAPWS-IF97 | 298–500 K |
| CO2(g), H2S(g) | NIST-JANAF ideal gas | 298–1200/1400 K |

Below 298.15 K and above the fitted range the heat capacity is held constant and the result
carries a warning. Above 373 K the 1-bar liquid-water reference is metastable (IF97 Region 1
continuation), which is also reported.

## Example

```java
SolidGasReactionEquilibrium reaction = SolidGasReactionEquilibrium.ironSulfideToSiderite();
double log10K = reaction.getLog10K(298.15);

SystemInterface fluid = new SystemSrkEos(273.15 + 100.0, 500.0);
fluid.addComponent("methane", 0.95);
fluid.addComponent("CO2", 0.05);
fluid.addComponent("H2S", 1.0e-6);
fluid.setMixingRule("classic");

SolidGasReactionEquilibrium.GasExchangeResult result = reaction.solveGasExchange(fluid, "H2S",
    "CO2", SolidGasReactionEquilibrium.ExchangeMode.FIXED_REACTANT);
double h2sPpm = result.getProductMoleFraction() * 1.0e6;
List<String> warnings = result.getWarnings();
```

## Original H2S in reservoir fluids (mineral buffer)

In clastic reservoirs with pyrite and an Fe-bearing carbonate, H2S is buffered by

$$
4\,\mathrm{FeS_2} + \mathrm{CH_4} + 3\,\mathrm{CO_2} + 6\,\mathrm{H_2O} \rightleftharpoons
4\,\mathrm{FeCO_3} + 8\,\mathrm{H_2S}
$$

$$
8 \ln f_{\mathrm{H_2S}} = \ln K(T,P) + \ln f_{\mathrm{CH_4}} + 3 \ln f_{\mathrm{CO_2}}
+ 6 \ln a_{\mathrm{H_2O}} - 4 \ln a_{\mathrm{FeCO_3}},
\qquad y_{\mathrm{H_2S}} = \frac{f_{\mathrm{H_2S}}}{\varphi_{\mathrm{H_2S}} P}.
$$

The reaction enthalpy is about 55 kJ per mol H2S, matching the apparent enthalpy of the
Håland et al. (1999) correlation of H2S versus temperature in Norwegian sandstone reservoirs
(54 kJ/mol). Applied to 454 measured NCS reservoir-fluid compositions with SRK, the buffer gives
0.61 times the correlation without fitting; an Fe-carbonate activity of 0.38 (Fe in ankerite or a
carbonate solid solution) reproduces it within about 30 % from 60 to 180 °C. The phase rule
excludes the alternatives: at NCS CO2 levels siderite, not magnetite, is the stable Fe phase, and
pyrite, not pyrrhotite, is stable at the buffered H2S level.

`MineralH2SBuffers` holds the balanced reactions (pyrite–siderite, pyrite–pyrrhotite,
pyrite–magnetite, pyrrhotite–magnetite, pyrrhotite–siderite, anhydrite TSR and the
siderite–magnetite boundary). `SolidGasReactionEquilibrium.solveForGas(fluid, "H2S")` solves any
of them with the other gas fugacities from the fluid. `ReservoirH2SEstimator` wraps the
pyrite–siderite buffer with the NCS-calibrated activity, the stability checks and warnings for the
kinetic regime below about 70 °C and for thermochemical sulfate reduction.

```java
SystemInterface reservoirFluid = new SystemSrkEos(273.15 + 140.0, 550.0);
reservoirFluid.addComponent("CO2", 0.03);
reservoirFluid.addComponent("methane", 0.80);
reservoirFluid.addComponent("ethane", 0.08);
reservoirFluid.addComponent("propane", 0.04);
reservoirFluid.addComponent("n-hexane", 0.05);
reservoirFluid.setMixingRule("classic");

ReservoirH2SEstimator estimator = new ReservoirH2SEstimator();
estimator.setWaterActivity(0.97);
ReservoirH2SEstimator.Estimate estimate = estimator.estimate(reservoirFluid);
double originalH2sPpm = estimate.getH2SPpm();
boolean consistent = estimate.isBufferConsistent();
```

The thermochemical data alone (siderite enthalpy ±5.5 kJ/mol) give a factor of about 2.3 per
standard deviation in H2S; the composition spread of NCS fluids at a given temperature gives
about a factor 1.4. Values are the original, pre-production level: seawater-injection souring
(see `neqsim-reservoir-souring-correlation`) adds to it later.

## Verification

`SolidGasReactionEquilibriumTest` checks:

- element balance, Gibbs-Helmholtz identity and the van't Hoff slope;
- JANAF CO2 and H2S entropies against the independent USGS tables (within 0.2 J/(mol K));
- $\log_{10} K(298.15\ \mathrm{K}) = -3.05$ against a USGS-only sum of formation constants,
  $-3.18$ (difference mainly the troilite $\Delta_f H^\circ$, −101.67 versus −102.6 kJ/mol);
- the ideal-gas limit, solver convergence at 500 bar, mass conservation and water activity.

JANAF and USGS troilite entropies differ by 2–3 J/(mol K) between 400 and 500 K because the
compilations treat the 411 K transition differently.

## Limitations

- **Polymorph:** troilite only. Mackinawite, often the first sulfide formed in reservoirs, has
  no complete phase-specific Cp, entropy and volume set in the retrieved sources.
- **Uncertainty:** the siderite enthalpy uncertainty (±5.5 kJ/mol) alone gives about ±1 in
  $\log_{10} K$ near 25 °C; JANAF uncertainties are not quantified and are listed in the warnings.
- **Pressure:** condensed-phase volumes are constant (no thermal expansion or compressibility of
  the solids).
- **Scope:** the result is a thermodynamic limit. Kinetics, mineral availability and surface area,
  aqueous speciation and pH, and pyrrhotite non-stoichiometry are outside the model. Water
  activity of a brine must be supplied with `setActivity("water", a_w)`.

## References

- Chase, M. W. (1998). NIST-JANAF Thermochemical Tables, 4th ed. J. Phys. Chem. Ref. Data,
  Monograph 9.
- Robie, R. A., Hemingway, B. S. (1995). Thermodynamic properties of minerals and related
  substances at 298.15 K and 1 bar and at higher temperatures. USGS Bulletin 2131.
- IAPWS (2007). Revised Release on the IAPWS Industrial Formulation 1997 (IAPWS-IF97).
