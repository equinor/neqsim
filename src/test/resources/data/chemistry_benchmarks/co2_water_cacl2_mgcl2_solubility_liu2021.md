# Liu et al. (2021) low-salinity CaCl2/MgCl2 CO2 solubility subset

## Primary source and licence

- Liu et al. (2021), “Measurement of Solubility of CO2 in NaCl, CaCl2, MgCl2 and MgCl2 + CaCl2 Brines at
  Temperatures from 298 to 373 K and Pressures up to 20 MPa Using the Potentiometric Titration Method”,
  *Energies* 14, 7222, [DOI 10.3390/en14217222](https://doi.org/10.3390/en14217222).
- Article and supplementary data: CC BY 4.0.
- Supplement URL: <https://www.mdpi.com/1996-1073/14/21/7222/s1>.
- Downloaded ZIP SHA-256: `935090777389121ce093406ea8d85d053ed2a4b4a8f21f78c2ec021ca7a9286f`.
- Extracted `energies-1349243-supplementary.csv` SHA-256:
  `3d6ff6b6f38c291118edffa8ec5aa76004ceb972eb310446b0ed8f98f4d66d6f`.

The article reports 553 points, whereas the supplement contains 554 data rows. Thirty CaCl2 rows at 373 K are exact
duplicates (same pressure, salinity, temperature and solubility), leaving 524 unique records. The fixture keeps one copy
of each duplicated state and does not average or alter any reported value. The supplement labels the mixed family only
as `mgcl2+cacl2`; neither the article nor the supplement specifies the split between the salts. Those rows cannot be
mapped to an electroneutral model state without inventing a composition and are excluded.

## Predeclared subset

The 84 rows were selected before running NeqSim:

- twelve matched distilled-water references;
- 36 CaCl2 and 36 MgCl2 states at 1000, 10000 and 15000 mass ppm;
- 298, 333, 353 and 373 K;
- 20.4, 102.0 and 170.1 atm, converted with `1 atm = 0.101325 MPa`.

The pressure positions and source observable match the independent NaCl subset. Source salinity is interpreted as total
salt mass ppm of the starting brine: 1000, 10000 and 15000 ppm map to 0.1, 1.0 and 1.5 mass percent of the named salt.
The assessment maps the model result to

`n_CO2 / (m_water + m_salt + m_CO2)`.

No source value is corrected. This is important because the retained table contains finite values that contradict the
aggregate salting-out trend, including CaCl2 10000 ppm at 353 K and 102 atm (0.085 mol/kg solution) and MgCl2
15000 ppm at 333 K and 102 atm (0.101 mol/kg solution). These source-record anomalies are reported, not silently
discarded, fitted or replaced.

## Scientific role and identifiability boundary

The single-salt blocks are an identifiability screen, not a calibration set. With salt-level coefficients at one
temperature order, NaCl, CaCl2 and MgCl2 constrain only

- `W_Na + W_Cl`;
- `W_Ca + 2 W_Cl`;
- `W_Mg + 2 W_Cl`.

The three equations have rank three for four ion coefficients and retain one gauge degree of freedom. Repeating them for
`W0`, `W1` and `W2` does not remove that allocation ambiguity. They also do not distinguish CPA binary
interactions from Furst short-range terms without layer-isolation calculations. A production update therefore requires
an explicit, physically documented gauge or salt-pair formulation and a bounded fit that excludes source-record
anomalies by a predeclared source-only rule.

The Mousavi–Chapoy–Burgass Table A.24 mixed-brine series and independent hydrate families remain frozen holdouts. The
Liu mixed-salt block is not used as a holdout until its CaCl2/MgCl2 composition can be established from a primary
record. No model equation, production coefficient, experimental tolerance or temperature offset changes in this
increment.
