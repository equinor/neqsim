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
- Unlimited-heat control. The independent finite-heat analytical test uses 10 W/m and is not
  represented as a full-cycle physical heat-transfer model.
- External interval: 0.5 s.
- Sequence: 120 s 150-to-10 bara gas-only blowdown; 120 s closed-outlet settling; 420 s
  same-composition inlet refill at 1000 kg/s with the outlet closed; 120 s closed hold.

## Predeclared gates

1. Both meshes complete 780 s without rejected publication, conservation failure, boundary-donor
   ambiguity, component negativity or EOS recovery failure.
2. Every accepted interval closes phase/total mass, named components and conservative total energy
   under the existing tolerances.
3. The cycle exercises positive oil inventory and actual same-gas inlet refill.
4. Final oil inventories on the two meshes remain finite, positive and within one order of
   magnitude (relative difference less than 100%). This broad numerical gate is not a predictive
   depletion band.
5. The separate frozen-target test converges under 0.2/0.1/0.05 s refinement to the independent
   exponential solution.

## Executed result

`refinement.csv` is the 2026-11-03 execution receipt. Both meshes passed. Peak oil differs by 0.322%;
final oil differs by 8.998% using the test's larger-value denominator. The opt-in source kernel
measured 2.665, 2.082 and 1.833 microseconds per cell in three
warmed batches on the same runner; these timings are diagnostic, not a CI threshold.

This is analytical and numerical-conservation qualification for an explicit parameter set. It is
not independent experimental rate validation, a calibrated field depletion forecast, a closed-wall
shutdown qualification, gelled-oil restart, severe-slugging validation or hydraulic pigging.
Finite available heat at the full-cycle scale can leave a far-from-equilibrium forced phase outside
the current conservative-recovery EOS branch; that fail-loud prerequisite remains open.
