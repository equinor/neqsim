---
title: Aqueous CO2 hydration temperature trajectory
description: Exact piecewise-isothermal propagation of the qualified neutral CO2(aq)/H2CO3 kinetic pair.
---

`AqueousCO2HydrationTrajectory` propagates the reversible neutral molecular pair

$$
\mathrm{CO_2(aq) + H_2O \rightleftharpoons H_2CO_3}
$$

through an ordered set of piecewise-isothermal segments. Each segment uses the exact analytical
`AqueousCO2HydrationKinetics.advance(...)` solution with the Soli and Byrne (2002) correlations,
[doi:10.1016/S0304-4203(02)00010-5](https://doi.org/10.1016/S0304-4203%2802%2900010-5).
No kinetic parameter is fitted or tuned by the trajectory helper.

## Ordered analytical propagation

For segment $i$ with duration $\Delta t_i$ and temperature $T_i$, the local rates are $k_H(T_i)$
and $k_D(T_i)$. The exact affine update is applied to the result of the preceding segment. This
preserves

$$
c_{\mathrm{CO_2(aq)}} + c_{\mathrm{H_2CO_3}}
$$

without a numerical timestep approximation. The result reports final pair concentrations, elapsed
time, segment count, temperature bounds, and the carbon-balance residual.

It also reports the dimensionless cumulative relaxation exposure

$$
\Theta = \sum_i \left(k_H(T_i) + k_D(T_i)\right)\Delta t_i.
$$

For repeated segments at one temperature, splitting a duration leaves the exact final state
unchanged and $\Theta$ equals the ordinary pair Damköhler number. For changing temperatures, the
pair equilibrium target also changes. Therefore $\exp(-\Theta)$ is **not** reported as one global
remaining-deviation fraction, and segment order must be preserved.

## Java and Python use

```java
double[] durationsSeconds = {0.04, 0.02};
double[] temperaturesK = {288.15, 305.65};

AqueousCO2HydrationTrajectory.TrajectoryResult result =
    AqueousCO2HydrationTrajectory.advance(
        1000.0, 0.0, durationsSeconds, temperaturesK);
```

The two concentration inputs and outputs use mol/m3. The duration and temperature arrays must be
non-null, non-empty, equal in length, and ordered consistently. A zero-duration segment is a valid
qualified no-op. Invalid or non-finite input fails closed. Python users call the same Java API
through JPype; no separate numerical implementation exists.

## Evidence and applicability boundary

The entire trajectory must remain within the published Soli-Byrne range of 288.15–305.65 K at
0.65 molal NaCl. A single out-of-range segment rejects the calculation. The primary source did not
qualify dense-phase CO2 pressure or other salinities.

This helper does not apply the van Eldik-Palmer pressure multipliers, combine datasets, select an
aqueous phase, calculate gas-to-water transfer or water dropout, run bicarbonate/carbonate
speciation or pH, reflash a fluid, integrate a transient pipeline, or calculate corrosion and scale.
It is a bounded neutral-pair trajectory diagnostic, not Northern Lights facility calibration or a
pipeline reaction source term.

See the [CO2 transport reaction-kinetics guide](co2_transport_reaction_kinetics) for provenance,
the electrolyte handoff, pressure-response limits, and the wider transport-coupling roadmap.
