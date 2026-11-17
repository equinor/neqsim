# CO2 solubility in the Mousavi-Chapoy-Burgass four-salt brine

## Source and license

The 18 factual measurements in
`co2_mixed_brine_solubility_mousavi_chapoy_burgass2024_table_a24.csv` are
transcribed from Table A24 of Ramin Mousavi, Antonin Chapoy and Rod Burgass,
“CO2 solubility in aqueous solution of salts: Experimental study and
thermodynamic modelling,” *Greenhouse Gases: Science and Technology* 14
(2024), 791-828,
[doi:10.1002/ghg.2298](https://doi.org/10.1002/ghg.2298).

The article is distributed under Creative Commons Attribution 4.0. The fixture
retains only the source pressure, temperature, salt-free aqueous CO2 mole
fraction and displayed `0.0001` mole-fraction uncertainty needed for a
reproducible comparison. The uncertainty is reported as evidence and is not
used as a product-accuracy tolerance.

## Preparation basis

Table 5 reports 258.13 g NaCl, 40.09 g CaCl2, 31.05 g MgCl2 and 3.48 g KCl per
nominal litre of water, together with rounded anhydrous mass fractions of 19.4,
2.3, 1.1 and 0.3 percent. Chapter 3 section 3.2.1 of Ramin Mousavi Belfeh
Teymouri, *Phase equilibria measurements and modelling of CO2-rich
fluids/brine systems*, Heriot-Watt University PhD thesis,
[handle 10399/3409](http://hdl.handle.net/10399/3409), identifies the weighed
calcium chloride as the dihydrate and magnesium chloride as the hexahydrate.
The thesis is used only to disambiguate reagent hydration; no numerical fixture
row is copied from it.

Using 1.000 kg as the nominal litre-water preparation basis and formula masses
18.01528, 58.44277, 74.5513, 110.984 and 95.211 g/mol for water, NaCl, KCl,
CaCl2 and MgCl2 gives 1.0263339402421 kg final water. Per kilogram of that final
water, the formula-unit molalities are:

| Formula unit | mol/kg final water |
|---|---:|
| NaCl | 4.303472167068 |
| KCl | 0.045481557865 |
| CaCl2 | 0.265697223919 |
| MgCl2 | 0.148809209564 |

Chloride and positive charge equivalents both equal 5.177966591898 mol/kg and
the ionic strength is 5.592473025381 mol/kg. The calculated source-equivalent
anhydrous mass fractions are 19.3682235978 percent NaCl, 0.2611142375 percent
KCl, 2.2708444670 percent CaCl2 and 1.0910808571 percent MgCl2. They reconcile
the reported one-decimal values. This reconciliation supports the nominal
1.000 kg interpretation; it is not a density-derived replacement for the
source preparation.

## Reproduction and qualification boundary

`MixedBrineCO2SolubilityMousaviChapoyBurgass2024AssessmentTest` uses
`SystemElectrolyteCPAstatoil` with mixing rule 10 and explicit `Na+`, `K+`,
`Ca++`, `Mg++` and stoichiometric `Cl-`. Crystal water is added to the solvent
inventory; hydrate formulae are preparation bookkeeping, not aqueous species.
The assessment retains one final aqueous kilogram of water, component and
charge closure, ion confinement, default and constrained root diagnostics, a
matched pure-water comparison, water activity, dissolved molecular CO2,
CO2-rich-phase fugacity and the exact log-space contribution identity. The
published rounded mass fractions are evaluated only as a composition-sensitivity
control.

The measured rows are independent aqueous-leg evidence. They do not qualify the
hydrate model, establish a parameter attribution, authorize an empirical
temperature offset, or justify a fitted interaction coefficient or relaxed
tolerance. The existing Dholabhai K10/K15 hydrate, NaCl/CaCl2/mixed-salt,
Burgass, Carroll, He-Morse, Yasunishi and water-activity controls remain separate.
