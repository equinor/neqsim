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
  relative-volume resolution. Representable trace wedges retain their leading-edge depth directly,
  without a liquid-inventory floor or subtractive centre/range cancellation.
- Available evaporation heat: 100 W/m. This remains an explicit kinetic screening limit.
- Wall/seabed screen: 26 mm steel, 7850 kg/m3, 500 J/(kg K), 4 W/(m2 K), 277.15 K environment and
  an explicit 277.15 K initial wall profile. These inputs are not calibrated field data.
- External interval: 0.5 s.
- Sequence: 120 s 150-to-10 bara gas-only blowdown; 120 s closed-outlet settling; 420 s
  same-composition inlet refill at 1000 kg/s with the outlet closed; 120 s closed hold.

### Common physical route under refinement

The physical route is the original 12-segment polyline on the 5 km line. Its vertex elevations
are `-300 - 55 sin²(pi x/L) - 12 sin²(3 pi x/L)` m, with the final 1250 m level at -333.5 m.
Refined meshes split these same straight segments by linear interpolation. The geometry regression
checks matching vertices on 12/24/48 cells, inserted midpoints and the fixed outlet extent.

The earlier "last three cells" outlet rule changed the level section from 1250 m at -333.5 m
to 625 m at -318.297 m under refinement. Its
[original receipt](legacy-varying-outlet-refinement.csv) therefore compares different physical
pipes. Fixing only the outlet while resampling the sine curve still changes intermediate route
elevations by up to 2.148 m relative to the original straight segments; that
[controlled diagnostic](fixed-outlet-curved-route-refinement.csv) gives 13.58%/13.23% peak/final
oil differences. Neither comparison isolates solver spatial error. The final comparison refines
the same polyline and retains the original 10% peak/final gates and conservation tolerances.

The `maximum_interface_area_m2` column is the largest **single-cell** interface area during the
cycle. It is an extensive local diagnostic and is not a mesh-convergence target.

## Predeclared gates

1. Both meshes complete 780 s without rejected publication, conservation failure, boundary-donor
   ambiguity, component negativity or EOS recovery failure.
2. Every accepted interval closes phase/total mass, named components and conservative total energy
   under the existing tolerances.
3. The cycle exercises positive oil inventory and actual same-gas inlet refill.
4. The finite heat limit activates on both meshes. Peak and final oil inventories must each agree
   within 10% on the unchanged 12/24-cell pair. Interface area remains finite, wall state changes,
   and the accepted cycles use no trace-area fallback. This numerical gate is not a predictive
   depletion band.
5. The separate frozen-target test converges under 0.2/0.1/0.05 s refinement to the independent
   exponential solution.
6. The refill event records unfiltered inlet-pressure extrema, peak-to-peak pressure, adjacent-sample
   rate, total/excess variation and direction reversals with an explicit Pa gate. These are numerical
   ringing diagnostics, not an experimental surge band.

## Executed result

`refinement.csv` is the latest 2026-11-05 execution receipt on the common physical polyline.
The [validation record](fix-validation.json) retains exact source identities and test outcomes.
Both meshes complete every accepted interval with zero trace-area fallback, but the unchanged
10% spatial gate still fails: peak oil differs by 11.21% and final oil by 10.86%, using the
larger-value denominator.

| Metric | 12 cells | 24 cells |
| --- | ---: | ---: |
| Peak oil, kg | 8,329.992 | 9,381.383 |
| Final oil, kg | 8,115.392 | 9,104.582 |
| Maximum single-cell interface area, m2 | 187.874 | 101.951 |
| Maximum wall-temperature change, K | 43.222 | 44.444 |
| Minimum heat-limit scale | 1.089e-3 | 7.855e-4 |
| Refill pressure peak-to-peak, Pa | 9,973,068.9 | 10,030,852.2 |
| Maximum adjacent-sample pressure rate, Pa/s | 278,344.1 | 536,217.5 |
| Ringing ratio | 0.03746 | 0.05752 |
| Reversals at 100 Pa | 8 | 12 |

The heat-limit scales prove that the declared 100 W/m limit is active. Refill pressure range
differs by 0.58%, while maximum adjacent rate differs by 48.09% and ringing ratio by 34.87%.
These unfiltered metrics remain numerical diagnostics, not an experimental surge band.
The source-kernel benchmark remains diagnostic rather than a platform-independent CI threshold.

This is analytical and numerical-conservation qualification for an explicit parameter set. It is
not independent experimental rate/thermal validation, a converged restart surge prediction, a
calibrated field depletion forecast, gelled-oil restart, severe-slugging validation or hydraulic
pigging. The full finite-heat cycle crosses the dense-root diagnostic-label transition without
clipping mass, changing the configured phase root or weakening the positive-compressibility/Jacobian
guards. Its retained inventory is a result of explicit heat/area assumptions, not a predictive
depletion forecast.

The remaining 11.21%/10.86% oil-inventory differences and unconverged pressure-rate/ringing
metrics keep the physical restart gate open. Required follow-up is conservative
mesh-insensitive pool-area/source coupling before timestep refinement, repeated cycles,
restart-opening pressure/outlet-surge bands, independent public data and the unchanged five-second
riser matrix. Moving-pig volume/flux/work coupling is withheld because it is a separate high-risk
moving-boundary algorithm whose inlet state would otherwise inherit this failed restart gate.

