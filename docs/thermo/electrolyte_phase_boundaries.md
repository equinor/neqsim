---
title: "Electrolyte VLE and VLLE Phase Boundaries"
description: "Bracketed saturation-pressure and saturation-temperature calculations for Pitzer and electrolyte-EOS VLE/VLLE with scientific diagnostics."
---


NeqSim provides a bracketed saturation operation for electrolyte systems in which gas, oil and a
model-specific aqueous phase may coexist. It is intended for gas–aqueous VLE, oil–aqueous LLE and
gas–oil–aqueous VLLE appearance/disappearance calculations with `SystemPitzer` and electrolyte
EOS models.

## Why this operation is separate

The historical bubble- and dew-point algorithms assume two interchangeable phase slots. That
assumption is not valid for hybrid EOS–GE systems: `SystemPitzer` owns an SRK gas role, a Pitzer
aqueous role and an SRK oil role, and ions must remain in the aqueous role. The bracketed operation
does not reinterpret those roles. Each trial point runs the ordinary complete TP/VLLE flash on a
fresh clone and classifies the requested material phase at a phase-fraction threshold of `1e-10`.

This is an electrolyte-owned continuation layer, not a replacement for generic phase stability.
The underlying TP-flash and stability algorithms remain coordinated under issue
[#2937](https://github.com/equinor/neqsim/issues/2937).

## Java API

The caller supplies a bracket known to contain one appearance or disappearance boundary. Bounds
use kelvin for temperature and bara for pressure.

```java
SystemPitzer fluid = new SystemPitzer(313.15, 50.0);
fluid.addComponent("methane", 5.0);
fluid.addComponent("n-heptane", 2.0);
fluid.addComponent("water", 55.508);
fluid.addComponent("Na+", 1.0);
fluid.addComponent("Cl-", 1.0);
fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ElectrolytePhaseBoundaryResult gasBoundary =
    ops.electrolytePhaseBoundaryPressureFlash(
        PhaseType.GAS, 200.0, 400.0, 0.25, 20);
double pressureBara = gasBoundary.getBoundaryValue();
```

`SystemPitzer` automatically selects the bundled complete PHREEQC parameter topology. No parameter
dataset selection is required. The legacy dataset remains an explicit compatibility option only.
Missing interaction families still fail closed.

The equivalent constant-pressure temperature calculation is:

```java
ElectrolytePhaseBoundaryResult oilBoundary =
    ops.electrolytePhaseBoundaryTemperatureFlash(
        PhaseType.OIL, lowerTemperatureK, upperTemperatureK, toleranceK, 60);
```

The same methods work with a non-reactive `SystemElectrolyteCPAstatoil`; configure mixing rule 10 in
the usual model-specific order. Reactive Pitzer systems are supported after
`chemicalReactionInit()` and database initialization. Their complete hybrid TP-flash evaluations
enforce elemental conservation, electroneutrality and maximum absolute `ln(Q/K)` in addition to the
ordinary phase-equilibrium contract. Reactive electrolyte-EOS phase boundaries still fail closed:
the current generic multiphase path does not yet preserve feed elements while changing species
amounts. Pitzer coefficients are used only by the Pitzer aqueous phase and are never transferred
into electrolyte-CPA parameters or reaction constants.

## Python / JPype use

```python
from neqsim.thermo.phase import PhaseType
from neqsim.thermodynamicoperations import ThermodynamicOperations

ops = ThermodynamicOperations(fluid)
result = ops.electrolytePhaseBoundaryPressureFlash(
    PhaseType.GAS, 200.0, 400.0, 0.25, 20
)
print(result.getBoundaryValue(), result.getLowerTopology(), result.getUpperTopology())
```

## Result and acceptance contract

The immutable, serializable result reports:

- final lower and upper bounds and midpoint estimate;
- whether the requested phase is present at the lower bound;
- the target-present state retained in the supplied system;
- phase fraction, endpoint topologies, iterations and complete TP-flash evaluations;
- maximum component material-balance residual;
- maximum phase-composition and phase-fraction normalization residual;
- aqueous charge residual in mol/kg water;
- maximum ionic mole fraction outside the aqueous phase;
- maximum neutral-component cross-phase log-fugacity residual;
- maximum reactive element-balance residual; and
- maximum absolute natural-log reaction residual.

The operation fails closed if the endpoints do not have different target-phase classifications, the
bracket does not converge, or the retained state exceeds these direct gates:

| Gate | Tolerance |
|---|---:|
| Component material balance | `1e-7` mole fraction |
| Phase and beta normalization | `1e-10` |
| Aqueous electroneutrality | `1e-8 mol/kg water` |
| Ion leakage outside aqueous | `1e-30` mole fraction |
| Cross-phase neutral `ln(f)` closure | `1e-5` |
| Reactive elemental balance | `1e-8 mol` |
| Reactive maximum `|ln(Q/K)|` | `2e-6` |

Every trial first uses a fresh clone of the original feed. This makes repeated and cloned execution
deterministic and prevents a previous VLE or VLLE topology from silently becoming the primary seed.
If a hybrid EOS–GE state cannot initialize finite fugacity coefficients after a large specification
change, the operation first retries that point from a fresh clone of the converged target-present
endpoint. If that direct endpoint jump also fails, eight evenly spaced continuation flashes bridge
the specification change from the same converged endpoint. The actual attempted flash count includes
every fallback flash. After convergence, the already converged target-present endpoint is defensively
cloned into the supplied system; no final history-sensitive TP flash is repeated from the original
feed.

## Scientific basis and evidence boundary

The underlying equilibrium calculation follows the repository's established TP-flash stability and
phase-split implementation. The relevant primary algorithm references are Michelsen,
*Fluid Phase Equilibria* 9 (1982), [stability, DOI
10.1016/0378-3812(82)85001-2](https://doi.org/10.1016/0378-3812(82)85001-2) and
[phase split, DOI
10.1016/0378-3812(82)85002-4](https://doi.org/10.1016/0378-3812(82)85002-4).
The Pitzer aqueous activity convention follows Pitzer (1973),
[DOI 10.1021/j100621a026](https://doi.org/10.1021/j100621a026), while exact parameter
provenance, release identity, public-domain evidence and validation ranges are recorded in the
[Pitzer parameter provenance matrix](pitzer_parameter_provenance.md).

The gas–oil–NaCl–water and reactive gas–oil–water–CO2 boundary checks in this increment are
deterministic software regressions, not independent experimental VLE validation. They verify
topology, balance, charge, fugacity, reaction closure, model separation, serialization and
nearby-state behavior without refitting parameters.
Quantitative predictive claims require an independently sourced composition-specific VLE/VLLE
dataset within the chosen EOS and aqueous-parameter validity ranges.

## Performance and limits

A bisection normally requires two endpoint flashes and one complete TP flash per iteration. A failed
cold-start hybrid state first permits one direct converged-endpoint retry. If the direct retry also
fails after a large specification jump, the operation permits one bounded eight-step continuation.
Every attempted flash is reported explicitly, and the converged endpoint is retained without another
flash. The code is called only through the new API, so neutral PR, SRK, CPA and non-electrolyte TP
flashes have zero added runtime path or parameter lookup.

The bracket must contain one monotonic target-phase transition. Multiple disconnected phase
regions require separate brackets. The operation currently locates fluid-phase boundaries only;
hydrate and mineral-solid complementarity retain their dedicated operations. It does not create new
Pitzer parameters, reaction constants or electrolyte-EOS parameters. Reactive Pitzer calculations
use the model-selected reaction database. The unsupported reactive electrolyte-EOS boundary remains
a deliberate fail-closed dependency rather than silently mixing reaction or activity-model
semantics.
