---
name: neqsim-reservoir-h2s-mineral-buffer
description: "Original (pre-souring) H2S in reservoir fluids from mineral buffering: pyrite + Fe-carbonate with the CH4/CO2 of the fluid fixes the H2S fugacity, which explains the exponential rise of H2S with reservoir temperature (Haaland 1999 correlation, about 54 kJ/mol). USE WHEN: a task asks what H2S to expect in a new reservoir or discovery, why H2S rises with temperature, whether a measured H2S is natural or souring, or which mineral reaction controls H2S."
version: "0.1.0"
calculation_basis: "neqsim-java"
required_packages:
  - "neqsim>=3.29.0 (neqsim.thermo.util.mineral: SolidGasReactionEquilibrium, MineralH2SBuffers, ReservoirH2SEstimator)"
---

# Original reservoir H2S from mineral buffering

## When to use

- Expected H2S in a new well, discovery or field before production (design basis, materials, scavenger sizing).
- Explaining why H2S in sandstone reservoirs rises from about 0.1 ppm at 50 °C to about 30 ppm at 190 °C.
- Deciding whether a measured H2S is the natural level or souring from injection water: compare with the buffer at
  the same temperature, pressure and CO2.
- Testing a proposed mineral reaction (for example FeS + H2O + CO2 = FeCO3 + H2S) against the phase rule.

Not for: production souring from seawater injection (use `neqsim-reservoir-souring-correlation`), separator-gas to
wellstream conversion (use `neqsim-h2s-phase-partitioning`), or sour carbonate reservoirs with active thermochemical
sulfate reduction (percent-level H2S, kinetically controlled).

## Physics

In clastic reservoirs with excess iron minerals, H2S is not set by how much sulfur was generated but by equilibrium
with the stable Fe minerals. With CH4/CO2 as the redox pair of the fluid:

```
4 FeS2 + CH4 + 3 CO2 + 6 H2O = 4 FeCO3 + 8 H2S
8 ln f(H2S) = ln K(T,P) + ln f(CH4) + 3 ln f(CO2) + 6 ln a(H2O) - 4 ln a(FeCO3)
y(H2S) = f(H2S) / (phi(H2S) P)
```

- dH per mol H2S is about 55 kJ/mol, the same as the apparent enthalpy of the Håland et al. (1999) correlation
  (ln ppm = 17.58 - 6478/T, digitized from the published curve, 70-190 °C). This is why H2S doubles about every 18 °C.
- A second contribution is CO2, which itself rises with temperature (about 0.3 mol % at 80 °C to about 4 % at 160 °C
  in Norwegian shelf fluids, Smith and Ehrenberg 1989). Higher pressure and lower phi(H2S) at depth partly offset it.
  Typical split of the rise 80 to 160 °C (x55): K(T) +3.5, CO2 +1.6, pressure -0.9, phi -0.3 (ln units).
- Phase rule: at shelf CO2 levels siderite is stable versus magnetite (median 1.6 log units margin) and pyrite versus
  pyrrhotite, so pyrite + Fe-carbonate is the buffering pair. Reactions with pyrrhotite (troilite) or magnetite are
  metastable: FeS + H2O + CO2 = FeCO3 + H2S gives about 10x too much H2S and too flat a trend.
- Sulfate reduction (bacterial below about 80 °C, thermochemical above about 120 °C) supplies sulfur but its activation
  energies (120-210 kJ/mol) are far too steep to set the ppm baseline; with iron in excess the minerals take up the
  surplus. Without iron (carbonates) or with active TSR, H2S can exceed the buffer by orders of magnitude.

## Calculation (NeqSim Java, Python via jpype)

```python
from neqsim import jneqsim
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
Estimator = jneqsim.thermo.util.mineral.ReservoirH2SEstimator

fluid = SystemSrkEos(273.15 + 140.0, 550.0)   # reservoir T [K] and P [bara]
for name, z in [("CO2", 0.03), ("methane", 0.80), ("ethane", 0.08), ("propane", 0.04), ("n-hexane", 0.05)]:
    fluid.addComponent(name, z)
fluid.setMixingRule("classic")

est = Estimator()                 # Fe-carbonate activity 0.38 (calibrated to the Haaland curve)
est.setWaterActivity(0.97)        # formation brine
e = est.estimate(fluid)
print(e.getH2SPpm(), e.isBufferConsistent(), list(e.getWarnings()))
```

- Use the measured reservoir-fluid composition (with C7+ as a TBP fraction) at reservoir T and P; the estimate is ppm
  mol in the whole reservoir fluid. Gas and oil follow the same fugacity law.
- `setFeCarbonateActivity(1.0)` gives the pure-siderite, uncalibrated value (0.61x the correlation on shelf fluids).
- Other buffers: `MineralH2SBuffers.all()` with `SolidGasReactionEquilibrium.solveForGas(fluid, "H2S")`.

## Uncertainty and validation

- Siderite dfH (±5.5 kJ/mol, USGS Bulletin 2131) dominates: about x2.3 per standard deviation in H2S. Fe-carbonate
  activity 1 to 0.15: x0.6 to x1.6 around the calibrated value. Composition and pressure spread at one temperature:
  about x1.4. Water activity and EOS (SRK vs PR, median +4 %): small.
- Monte Carlo at 130-150 °C on shelf fluids: P10/P50/P90 about 2 / 7.5 / 24 ppm (correlation: 6.6 ppm).
- Validate against measured wellsite (DST) H2S. Laboratory PVT compositions usually report H2S as 0 because ppm H2S is
  lost to sample cylinders and steel; do not use them as H2S measurements.

## Gotchas

| Gotcha | Handling |
| --- | --- |
| Below about 70 °C | Equilibrium is slow; measured H2S is often lower; the estimator warns. |
| CO2 below the siderite-magnetite boundary | Buffer not valid (`isBufferConsistent()` false); magnetite/chlorite controls. |
| Carbonate reservoir with anhydrite above about 120 °C | TSR can exceed the buffer; set `setSulfateMineralsPresent(true)` for the warning. |
| Measured value after water injection | Natural baseline plus souring; subtract the buffer value before fitting souring. |
| Units | Estimate is mol ppm in the reservoir fluid, not in separator gas; convert with `neqsim-h2s-phase-partitioning`. |

## References

- Håland, K., Barrufet, M.A., Rønningsen, H.P., Meisingset, K.K. (1999). An empirical correlation between reservoir
  temperature and the concentration of hydrogen sulfide. SPE-50763-MS. https://doi.org/10.2118/50763-MS
- Smith, J.T., Ehrenberg, S.N. (1989). Correlation of carbon dioxide abundance with temperature in clastic
  hydrocarbon reservoirs: relationship to inorganic chemical equilibrium. Marine and Petroleum Geology 6, 129-135.
- Robie, R.A., Hemingway, B.S. (1995). USGS Bulletin 2131. Chase, M.W. (1998). NIST-JANAF Thermochemical Tables.
- Machel, H.G. (2001). Bacterial and thermochemical sulfate reduction in diagenetic settings. Sedimentary Geology 140.
