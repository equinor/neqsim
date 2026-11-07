# Low-temperature CO2 solubility in aqueous KCl

## Source and transcription

The four values in `co2_kcl_solubility_burgass_tohidi1995.csv` are transcribed
from Table 6.4 of Bahman Tohidi Kalorazi, *Gas Hydrate Equilibria in the
Presence of Electrolyte Solutions* (PhD thesis, Heriot-Watt University, 1995),
pages 156–157 of the archived PDF. The table attributes its experimental data
to R. Burgass, *Hydrate Phase Equilibria in the Presence of Electrolyte
Solutions* (MSc thesis, Heriot-Watt University, 1995). Tohidi's Table 5.3 also
identifies Burgass as the source of the CO2–KCl data.

The fixture preserves the printed KCl mass percent, temperature, pressure and
CO2 “mole %” values. Pressure is total pressure, in MPa. No values are digitized
from a graph and no model-derived values are stored in the fixture.

## Observable boundary

The accessible source does not define whether “mole %” includes electrolyte as
dissociated ions, one KCl formula unit, or excludes electrolyte from the mole
sum. NeqSim represents KCl as separate `K+` and `Cl-` components, so selecting
one of those denominators would add an unsupported semantic assumption.

The assessment therefore reports, without fitting or qualification:

1. the native aqueous CO2 mole percent including both ions;
2. a molecular-solvent mole percent using CO2 plus water;
3. a pseudo-salt mole percent using CO2, water and one KCl formula unit; and
4. CO2 molality per kilogram of aqueous water.

Residuals against the printed source value are retained for the first three
definitions. They are diagnostic alternatives, not independent measurements
and not experimental-accuracy acceptance criteria.

## Reproduction semantics

Each state uses `SystemElectrolyteCPAstatoil`, mixing rule 10, the source total
pressure, a finite CO2 gas reservoir and one final kilogram of aqueous water.
Feed water is compensated for evaporation. The test verifies gas/aqueous phase
presence, ion confinement, component inventory, molecular fugacity equality,
the water basis and finite reported values. It also repeats one state with a
smaller gas inventory to ensure the chosen reservoir does not control the
calculated dissolved CO2 result.

This source extends the diagnostic temperature range below 273.15 K. It does
not cover the exact Dholabhai K10/K15 recipes, the high-pressure hydrate points,
or mixed salts, and it does not qualify the production model.
