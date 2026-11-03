# Hydrocarbon-pool transfer qualification

This directory records the predeclared analytical and public synthetic full-cycle evidence for
`HydrocarbonPoolTransfer`.

## Declared model inputs

- Public six-component rich-gas fixture: methane/ethane/propane/n-butane/n-pentane/n-heptane =
  0.916/0.050/0.020/0.007/0.003/0.004 mole fraction.
- 5 km, 12- and 24-cell three-sag terrain; 1.0148 m internal diameter.
- Component transfer coefficient: 0.05 m/s for every component.
- Component evaporation enthalpies: 510/490/430/380/350/320 kJ/kg.
- Interface width: 0.5 pipe diameters.
- Available evaporation heat: 100 W/m. This is an explicit screening input, not a calibrated
  wall/seabed heat-transfer model.
- External interval: 0.5 s.
- Sequence: 120 s 150-to-10 bara gas-only blowdown; 120 s closed-outlet settling; 420 s
  same-composition inlet refill at 1000 kg/s with the outlet closed; 120 s closed hold.

## Predeclared gates

1. Both meshes complete 780 s without rejected publication, conservation failure, boundary-donor
   ambiguity, component negativity or EOS recovery failure.
2. Every accepted interval closes phase/total mass, named components and conservative total energy
   under the existing tolerances.
3. The cycle exercises positive oil inventory and actual same-gas inlet refill.
4. The finite heat limit activates on both meshes. Final oil inventories remain finite, positive and within one order of
   magnitude (relative difference less than 100%). This broad numerical gate is not a predictive
   depletion band.
5. The separate frozen-target test converges under 0.2/0.1/0.05 s refinement to the independent
   exponential solution.

## Executed result

`refinement.csv` is the 2026-11-03 execution receipt. Both meshes passed. Peak oil is
68,360.504/69,780.185 kg and final oil is 68,002.640/69,437.627 kg on 12/24 cells; the spatial
differences are 2.035% and 2.067% using the larger-value denominator. Minimum heat scales are
2.306e-4/1.653e-4, proving that the declared 100 W/m limit is active. The opt-in source kernel
measured 2.665, 2.082 and 1.833 microseconds per cell in three
warmed batches on the same runner; these timings are diagnostic, not a CI threshold.

This is analytical and numerical-conservation qualification for an explicit parameter set. It is
not independent experimental rate validation, a calibrated field depletion forecast, gelled-oil
restart, severe-slugging validation or hydraulic pigging. The full finite-heat cycle now crosses the
dense-root diagnostic-label transition without clipping mass, changing the configured phase root or
weakening the positive-compressibility/Jacobian guards. Its large retained inventory is a result of
the explicit heat assumption, not a predictive depletion forecast.

