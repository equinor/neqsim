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
- Interface area: `forStratifiedFlow(...)` extends the local circular holdup chord along cell
  arclength. A positive equilibrium oil target supplies unseeded appearance area. The horizontal
  search bracket retains positive trace geometry down to `1e-300` holdup without an inventory
  floor. The historical `forHydrostaticPool(...)` records remain separate stationary models.
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

## Executed flowing-model result

`refinement.csv` is the 2026-11-05 flowing-area execution receipt on the common physical
polyline. The [validation record](fix-validation.json) identifies the exact source blobs,
area assumption, tests and limits. Both meshes complete every accepted interval with zero
trace-area fallback. The original 10% peak/final inventory gates and conservation tolerances
remain unchanged.

Peak oil differs by **2.478%** and final oil by **2.576%**, using the larger-value denominator.

| Metric | 12 cells | 24 cells |
| --- | ---: | ---: |
| Peak oil, kg | 24452.2551 | 25073.6019 |
| Final oil, kg | 24121.5545 | 24759.3861 |
| Maximum single-cell interface area, m2 | 188.131134 | 97.5983368 |
| Maximum wall-temperature change, K | 42.8647005 | 44.2354846 |
| Minimum heat-limit scale | 0.00105912011 | 0.000906119962 |
| Refill pressure peak-to-peak, Pa | 10063252.9 | 10132457.7 |
| Maximum adjacent-sample pressure rate, Pa/s | 271534.482 | 525257.438 |
| Ringing ratio | 0.023274119 | 0.0379533502 |
| Reversals at 100 Pa | 6 | 10 |

The finite heat-limit scales prove that the declared 100 W/m limit activates. Pressure-rate
and ringing metrics are unfiltered numerical diagnostics; they are not a qualified surge band.

## Area-model correction and retained failed evidence

The [common-route stationary-puddle receipt](common-route-hydrostatic-refinement.csv) reproduces
the original 11.21% peak and 10.86% final oil differences. That model remains unqualified for
the flowing cycle. The new factory is an explicit change of area assumption, not evidence that
the original stationary-puddle trajectory has converged. Coefficients, heat supply, physical
terrain, event schedule, 12/24-cell pair, external interval and acceptance limits are retained.

A uniform 400 m, unit-diameter state at 0.01 holdup and 0.03 rad has 13.990 m2 static-puddle
area as one cell and 34.626 m2 as eight cells: each numerical cell creates a separate horizontal
puddle. `StratifiedPoolTransferTest` instead checks the flowing chord against an independent
circular-segment identity, uniform/nonuniform subdivision of nonzero component sources, finite
heat limits, unseeded appearance and serialization. Replacing the flowing factory with the static
factory fails the new partition control. Neither area assumption is calibrated field data.

The first flowing-cycle replay exposed a second defect: a fixed horizontal bisection bracket
could not resolve very small positive trace depths. Only that bracket is scaled with the
circular-segment depth power. Independent `1e-30` through `1e-300` controls preserve positive
volume, depth and chord area under the unchanged `1e-9` relative-volume gate. All 222 independent
pressure-moment controls retain their numerical values; source identity remains strictly pinned.

## Remaining qualification

This is analytical and declared-parameter numerical conservation/inventory evidence. It does
not establish independent experimental rate/thermal accuracy, pressure-rate/ringing convergence,
a restart-opening surge band, repeated-cycle behavior, severe slugging, field depletion or
hydraulic pigging. The dense-root, positive-compressibility and Jacobian guards remain active.
Moving-pig fluid-volume, face-flux and pressure-work coupling is still withheld. The separate
five-second riser matrix and physical restart acceptance remain open.
