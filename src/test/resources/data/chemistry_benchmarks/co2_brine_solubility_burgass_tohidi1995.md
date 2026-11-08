# CO2 solubility in aqueous KCl and mixed brines

## Source and transcription

The twenty values in `co2_brine_solubility_burgass_tohidi1995.csv` are
transcribed from Tables 6.4 and 6.5 of Bahman Tohidi Kalorazi, *Gas Hydrate
Equilibria in the Presence of Electrolyte Solutions* (PhD thesis, Heriot-Watt
University, 1995), pages 156–158 of the archived PDF. The tables attribute the
experimental data to R. Burgass, *Hydrate Phase Equilibria in the Presence of
Electrolyte Solutions* (MSc thesis, Heriot-Watt University, 1995). Tohidi's
Table 5.3 also identifies Burgass as the source of the CO2–KCl data.

Table 6.4 supplies sixteen KCl states: twelve measurements at 5–20 mass % and
298–348 K plus the four 7/14 mass % low-temperature states previously retained.
Table 6.5 supplies four NaCl/KCl/CaCl2 combinations. The thesis states that the
Table 6.5 mixed-salt measurements were not used for parameter optimization, so
they are retained as source-defined holdouts. The fixture preserves the printed
salt mass percents, temperature, total pressure in MPa and CO2 “mole %”. No
values are digitized from a graph and no model-derived values are stored.

The archived thesis was consulted through the Heriot-Watt Research Portal. The
fixture contains only the factual numeric transcription required to reproduce
the comparison; it does not redistribute thesis pages or figures.

## Observable boundary

The accessible source does not define whether “mole %” includes electrolyte as
dissociated ions, salt formula units, or excludes electrolyte from the mole
sum. NeqSim represents the salts as separate ions, so selecting one of those
denominators would add an unsupported semantic assumption.

The assessment therefore reports, without fitting or qualification:

1. the native aqueous CO2 mole percent including both ions;
2. a molecular-solvent mole percent using CO2 plus water;
3. a pseudo-salt mole percent using CO2, water and one formula unit of each
   dissolved salt; and
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

This source spans 266.5–348.4 K and includes mixed salts, but it is a dissolved
CO2 diagnostic rather than a hydrate-equilibrium benchmark. It does not define
the exact Dholabhai K10/K15 hydrate recipes or the high-pressure hydrate points,
and it does not qualify the production model.
