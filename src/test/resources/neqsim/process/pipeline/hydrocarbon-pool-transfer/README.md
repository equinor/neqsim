# Hydrocarbon-pool transfer qualification

This directory records the predeclared analytical and public synthetic full-cycle evidence for
`HydrocarbonPoolTransfer`.

The original fixed-width implementation was executed at
`2e0baa86b93ab912f414a6aea7d185e6fe08c59c`. The 2026-11-03 continuation reruns the same event
sequence with the hydrostatic-area and wall-inventory path from exact base
`b9278492083b9b3d7241739c58d1c010ad13f4d1`; the exact published candidate head is recorded in the
#2907/#3298 ledger.

## Declared model inputs

- Public six-component rich-gas fixture: methane/ethane/propane/n-butane/n-pentane/n-heptane =
  0.916/0.050/0.020/0.007/0.003/0.004 mole fraction.
- 5 km, 12- and 24-cell three-sag terrain; 1.0148 m internal diameter.
- Component transfer coefficient: 0.05 m/s for every component.
- Component evaporation enthalpies: 510/490/430/380/350/320 kJ/kg.
- Interface area: volume-preserving horizontal-surface reconstruction in each inclined cell. A
  positive equilibrium oil target supplies the unseeded appearance area; a reported local
  stratified chord is used only when retained trace volume is below the inclined reconstruction's
  relative-volume resolution.
- Available evaporation heat: 100 W/m. This remains an explicit kinetic screening limit.
- Wall/seabed screen: 26 mm steel, 7850 kg/m3, 500 J/(kg K), 4 W/(m2 K), 277.15 K environment and
  an explicit 277.15 K initial wall profile. These inputs are not calibrated field data.
- External interval: 0.5 s.
- Sequence: 120 s 150-to-10 bara gas-only blowdown; 120 s closed-outlet settling; 420 s
  same-composition inlet refill at 1000 kg/s with the outlet closed; 120 s closed hold.

## Predeclared gates

1. Both meshes complete 780 s without rejected publication, conservation failure, boundary-donor
   ambiguity, component negativity or EOS recovery failure.
2. Every accepted interval closes phase/total mass, named components and conservative total energy
   under the existing tolerances.
3. The cycle exercises positive oil inventory and actual same-gas inlet refill.
4. The finite heat limit activates on both meshes. Final oil inventories remain finite, positive
   and within one order of magnitude (relative difference less than 100%). Interface area remains
   finite, wall state changes, and every sub-resolution fallback is explicit. This broad numerical
   gate is not a predictive depletion band.
5. The separate frozen-target test converges under 0.2/0.1/0.05 s refinement to the independent
   exponential solution.

## Executed result

`refinement.csv` is the latest 2026-11-03 execution receipt. Both meshes passed. Peak oil is
8,330.683/6,429.568 kg and final oil is 8,116.001/6,144.303 kg on 12/24 cells; the spatial
differences are 22.82% and 24.29% using the larger-value denominator. Maximum resolved interface
area is 187.876/101.227 m2 and maximum wall-temperature change is 43.223/45.037 K. Both meshes
exercise the reported trace-area fallback. Minimum heat scales are 1.089e-3/8.093e-4, proving that
the declared 100 W/m limit is active. The source-kernel benchmark remains diagnostic rather than a
platform-independent CI threshold; hydrostatic reconstruction adds work and must be profiled at
production cell counts.

This is analytical and numerical-conservation qualification for an explicit parameter set. It is
not independent experimental rate/thermal validation, a converged restart surge prediction, a
calibrated field depletion forecast, gelled-oil restart, severe-slugging validation or hydraulic
pigging. The full finite-heat cycle crosses the dense-root diagnostic-label transition without
clipping mass, changing the configured phase root or weakening the positive-compressibility/Jacobian
guards. Its retained inventory is a result of explicit heat/area assumptions, not a predictive
depletion forecast.

The coarse/refined area difference and 22-24% oil-inventory difference keep the physical restart
gate open. Required follow-up includes timestep refinement, tighter spatial convergence, repeated
cycles, restart-opening pressure/outlet-surge bands, independent public data and the unchanged
five-second riser matrix. Moving-pig volume/flux/work coupling is a separate high-risk algorithm.

