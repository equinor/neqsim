---
title: "Solid-gas reaction equilibrium (FeS to siderite and H2S)"
description: "Standard-state equilibrium constants for heterogeneous mineral-water-gas reactions with condensed-phase pressure corrections and EOS fugacities, applied to FeS + H2O + CO2 = FeCO3 + H2S."
keywords: "mineral reaction, equilibrium constant, troilite, siderite, H2S, CO2, fugacity, Poynting, IAPWS-IF97, JANAF, reservoir souring"
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
