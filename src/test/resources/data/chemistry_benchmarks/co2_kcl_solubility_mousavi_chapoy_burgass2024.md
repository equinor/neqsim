# CO2 solubility in aqueous KCl on an explicit salt-free basis

## Source and license

The 26 values in `co2_kcl_solubility_mousavi_chapoy_burgass2024.csv`
are transcribed from Tables A11-A13 of Ramin Mousavi, Antonin Chapoy and
Rod Burgass, “CO2 solubility in aqueous solution of salts: Experimental
study and thermodynamic modelling,” *Greenhouse Gases: Science and
Technology* 14 (2024), 791-828,
[doi:10.1002/ghg.2298](https://doi.org/10.1002/ghg.2298).

The article is distributed under the Creative Commons Attribution license.
The fixture contains only the factual numerical values required for a
reproducible comparison and cites the original source; it does not redistribute
article text, tables or figures.

Tables A11-A13 report 10, 15 and 22 mass percent KCl at 323.15, 373.15 and
423.15 K over 82.07-548.28 bar. The source mole fractions and displayed
uncertainties were multiplied by exactly 100 for the percentage-point report
used by the assessment. A stored uncertainty of `0.00` preserves the source
table's four-decimal rounding and must not be interpreted as zero experimental
uncertainty.

## Observable definition

Unlike the Burgass-Tohidi 1995 table, this source defines the reported
solubility explicitly as the mole fraction of CO2 in the **salt-free aqueous
phase**. The experimental section describes a static-analytical measurement:
CO2 and brine are equilibrated in a rocking cell, an aqueous sample is flashed
at laboratory temperature and atmospheric pressure, and released CO2 is
measured by gasometer. The amount of released CO2 and mass of sampled liquid
determine the reported solubility.

The assessment therefore treats the NeqSim molecular water-plus-CO2 mole
fraction as the like-for-like observable. Ion-inclusive and pseudo-salt values
are retained only as representation controls; their numerical differences from
the source are not experimental residuals.

## Reproduction semantics

Each state uses `SystemElectrolyteCPAstatoil`, mixing rule 10, the reported
total pressure, a finite CO2 reservoir and one final aqueous kilogram of water.
Feed water is compensated for evaporation. The assessment verifies two fluid
phases with the ions confined to the aqueous phase, component inventory, salt
mass fractions, the water basis, finite diagnostics and finite-reservoir
independence. The non-aqueous phase is identified by position because NeqSim
may label the CO2-rich phase as oil at high pressure (for example 40 MPa). The
molecular log-fugacity residual is reported in the CSV rather than gated: at
high pressure it is a model diagnostic, not a solver-tolerance check.

These high-temperature measurements are an independent dissolved-CO2
diagnostic and were not used to fit NeqSim in this increment. They separate the
aqueous CO2/water-activity path from hydrate guest occupancy, but do not replace
the low-temperature Dholabhai K10/K15 hydrate measurements or prove the 1995
Burgass reporting convention. No production parameter, equation, tolerance or
empirical temperature offset is changed.
