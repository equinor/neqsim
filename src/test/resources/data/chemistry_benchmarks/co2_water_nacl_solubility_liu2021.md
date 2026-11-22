# Liu et al. (2021) low-salinity water/NaCl CO2 solubility subset

## Primary source and licence

- Liu et al. (2021), “Measurement of Solubility of CO2 in NaCl, CaCl2, MgCl2 and MgCl2 + CaCl2 Brines at
  Temperatures from 298 to 373 K and Pressures up to 20 MPa Using the Potentiometric Titration Method”,
  *Energies* 14, 7222, [DOI 10.3390/en14217222](https://doi.org/10.3390/en14217222).
- Article and supplementary data: CC BY 4.0.
- Supplement URL: <https://www.mdpi.com/1996-1073/14/21/7222/s1>.
- Downloaded ZIP SHA-256: `935090777389121ce093406ea8d85d053ed2a4b4a8f21f78c2ec021ca7a9286f`.
- Extracted `energies-1349243-supplementary.csv` SHA-256:
  `3d6ff6b6f38c291118edffa8ec5aa76004ceb972eb310446b0ed8f98f4d66d6f`.

The article reports 553 total points, while the supplied CSV has 554 data rows. The difference is retained as a source
record discrepancy rather than silently reconciled. The present subset uses only the distilled-water and NaCl blocks;
the apparent duplicate CaCl2 373 K block cannot affect it.

## Predeclared subset

The 48 rows were selected before running NeqSim:

- four source temperatures: 298, 333, 353 and 373 K;
- distilled water plus 1000, 10000 and 15000 mass ppm NaCl;
- 20.4, 102.0 and 170.1 atm, converted exactly with `1 atm = 0.101325 MPa`.

These are the second, sixth and ninth common pressure positions. The 1 atm rows are excluded because the source reports
its largest relative measurement error near atmospheric pressure. The uneven 200 atm endpoint is also excluded. No
point was selected or discarded using a NeqSim result. The source reports relative errors of 0.9–7.8%; these are study-
level method errors, not pointwise tolerances and are not used as acceptance bands.

The source observable is moles of dissolved CO2 per kilogram of titrated solution. The assessment maps NeqSim's final
aqueous state onto the same mass basis:

`n_CO2 / (m_water + m_NaCl + m_CO2)`.

Source salinity is interpreted as NaCl mass ppm of the starting brine, so 1000, 10000 and 15000 ppm map to 0.1, 1.0 and
1.5 mass percent NaCl. No temperature shift, pressure adjustment or source-value correction is applied.

## Scientific role and identifiability boundary

This source family is independent of the dense Mousavi–Chapoy–Burgass (2024) data. It is a no-fit low-concentration
check of the pure-water reference and first-order NaCl salting response. The Table A24 mixed-brine series and all
independent hydrate endpoints remain frozen holdouts and are not used to choose this subset, a parameter or a model.

Electroneutral NaCl states identify only the salt-level Na+/Cl- response. If separate ion coefficients `W0`, `W1` and
`W2` are considered, the data can at most identify the three temperature-polynomial sums `Wk_Na + Wk_Cl`; they cannot
identify six individual-ion allocations. The maximum salinity is about 0.26 molal, so the subset also does not identify
high-concentration curvature. Without layer-isolation controls it cannot uniquely attribute a response to CPA binary
interaction parameters versus Furst short-range `Wij` terms.

The assessment therefore reports source/model solubility, matched pure-water values, apparent salt effects, water
activity, guest fugacity, the exact dissolved-CO2 decomposition identity, component closure and four constrained-root
spot checks. It changes no production equation or parameter, performs no fit and establishes no accuracy qualification.
