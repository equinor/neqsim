---
title: "OLGA PVT Table and Hydrate Curve Generation"
description: "Generate OLGA .tab PVT property tables and hydrate equilibrium curves from any NeqSim fluid - dry gas, oil, gas condensate, three-phase gas/oil/water, dense-phase CO2 - including the phase-coverage rules OLGA enforces, the keyword format, grid selection, hydrate curve export, and how to validate output by running it in OLGA."
---

NeqSim can export a fluid as an OLGA `.tab` PVT property table and as an OLGA
hydrate equilibrium curve, so an OLGA (or LedaFlow) case can use exactly the same
thermodynamics as a NeqSim process model. This page covers which generator to
use, what OLGA requires, and how to verify that the output actually loads.

## Choosing a generator

Both current generators live in
`neqsim.thermodynamicoperations.propertygenerator` and write the modern
`PVTTABLE` keyword format.

| Generator | Writes | Use for |
| --- | --- | --- |
| `OLGApropertyTableGeneratorKeywordFormat` | `PHASE = TWO` | Any fluid without a free water phase: dry gas, gas condensate, oil, dense-phase CO2 |
| `OLGApropertyTableGeneratorWaterKeywordFormat` | `PHASE = THREE` | Fluids where a separate aqueous phase matters: produced water, hydrate studies, MEG systems |

The three-phase generator also accepts a fluid with no water component at all; it
simply writes zero water content. Prefer the two-phase generator when there is no
water, because a three-phase table is larger and slower to build.

The older `OLGApropertyTableGenerator`, `OLGApropertyTableGeneratorWater`,
`OLGApropertyTableGeneratorWaterEven` and the two `...Students` variants write
legacy formats and are kept only for backwards compatibility.

## Minimal example

```java
SystemInterface fluid = new SystemSrkEos(273.15 + 40.0, 60.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.06);
fluid.addComponent("propane", 0.03);
fluid.addComponent("n-pentane", 0.03);
fluid.addComponent("n-heptane", 0.03);
fluid.setMixingRule("classic");

OLGApropertyTableGeneratorKeywordFormat generator =
    new OLGApropertyTableGeneratorKeywordFormat(fluid);
generator.setFluidLabel("EXPORTGAS");
generator.setPressureRange(5.0, 215.0, 44);          // bara
generator.setTemperatureRange(253.15, 333.15, 33);   // K
generator.run();
generator.writeOLGAinpFile("exportgas.tab");
```

The OLGA case then refers to the table and the label:

```
FILES PVTFILE="exportgas.tab"
...
 BRANCH FLUID="EXPORTGAS"
```

`setFluidLabel` must match `BRANCH FLUID=` exactly. When it is not set the label
defaults to `NewFluid`.

For a three-phase fluid, swap in the water generator and add a water component:

```java
fluid.addComponent("water", 0.12);
fluid.setMultiPhaseCheck(true);

OLGApropertyTableGeneratorWaterKeywordFormat generator =
    new OLGApropertyTableGeneratorWaterKeywordFormat(fluid);
```

## What OLGA requires of a table

These are the rules that actually cause OLGA to reject a file, in the order they
tend to bite:

1. **No zero densities.** OLGA aborts with
   `ERROR IN THE INPUT FILE: OIL DENSITY IS ZERO AT: PRES.= ... AND TEMP. = ...`
   if any `ROG`, `ROHL` or `ROWT` entry is zero. A flash only returns the phases
   that exist, so every node outside a phase's existence region needs a value
   anyway - see *Absent phases* below.
2. **No `NaN` or `Infinity`.** These are written literally by Java and OLGA
   cannot parse them.
3. **The grid must span the whole simulation.** OLGA stops with
   `PRESSURE ABOVE TABLE VALUES` or `TEMPERATURE BELOW TABLE VALUES` the moment a
   section leaves the tabulated range. Size the grid against the *expected*
   solution, not the boundary conditions: a long line with Joule-Thomson cooling
   arrives far colder than its inlet.
4. **`BUBBLEPRESSURES` and `BUBBLETEMPERATURES` must be the same length.** They
   are written as a paired array: one bubble-point pressure per grid temperature.

## Absent phases and how they are filled

A dry gas has no liquid anywhere, a dead oil has no gas, and a gas condensate has
only one phase outside its two-phase envelope. OLGA still expects a full gas
column and a full liquid column at every node.

The generators therefore:

1. resolve phases by **type** (`gas`, `oil`, `aqueous`) rather than by array
   position, so the gas column stays gas even at a single-phase node;
2. fill nodes where a phase is absent by nearest-neighbour extrapolation, in grid
   index space, from the nodes where it does exist;
3. fall back to a forced single-phase evaluation of the whole composition when a
   phase exists nowhere on the grid, and to a documented physical default when
   even that has no usable root.

The extrapolated branch is never used in a flow calculation, because the
corresponding phase mass fraction (`RS`, `RSW`) is zero there. It exists purely so
the table loads. Mass fractions are *not* extrapolated - a zero gas fraction is
physically correct and is written as zero.

## Choosing the grid

| Parameter | Guidance |
| --- | --- |
| Pressure range | From below the lowest arrival pressure to above the highest inlet pressure, with margin for the solver overshooting |
| Temperature range | From below the coldest expected temperature (include JT cooling and seabed ambient) to above the hottest inlet |
| Resolution | 30-50 pressures x 25-35 temperatures is usually enough; refine near the phase envelope rather than everywhere |

Use an **asymmetric** grid when testing generator changes: a square grid hides
index transposition bugs.

## Validating a generated table

A rule check (`Olga-<version>.exe -exitRC case.genkey`) does **not** read the PVT
file, so it proves nothing about the table. The only real test is to run a case
that uses it. A minimal flowpath with a source, a pressure node and a short pipe
is enough - if OLGA initialises and integrates, the table is loadable.

When benchmarking OLGA against NeqSim, also check that the two see the same
fluid:

- the table reproduces a direct NeqSim flash density at a few states;
- `SOURCE MASSFLOW` matches the NeqSim mass rate in kg/s, not the standard-volume
  rate;
- the inlet phase split matches. `SOURCE GASFRACTION` is a **mass** fraction and
  overrides the table equilibrium. When `WATERFRACTION` is also given,
  `GASFRACTION` is the gas mass fraction of the **hydrocarbon** part while
  `WATERFRACTION` is a fraction of the total. Read back the
  `MASS SOURCE INFORMATION` block in the `.out` file and check the reported
  gas/oil/water kg/s against the NeqSim flash before trusting any result.

## Hydrate curves

OLGA does not compute hydrate thermodynamics. It checks hydrate risk against a
**tabulated equilibrium curve**, and its built-in alternative is the
Hammerschmidt correlation - a crude inhibitor shift. Exporting the curve from
NeqSim gives OLGA the same rigorous hydrate model the NeqSim side of a study
uses, including a real MEG or methanol inhibited curve, so the two codes agree on
where the hydrate boundary sits.

```java
SystemInterface fluid = ...;                  // must contain water
OLGAhydrateCurveGenerator generator = new OLGAhydrateCurveGenerator(fluid);
generator.setCurveLabel("LINNORM_HYD");
generator.setPressureRange(10.0, 200.0, 12);  // bara
generator.run();
generator.writeOLGAinpFile("linnorm_hydrate_curve.inp");
System.out.println(generator.getHydrateCheckKeyword());
```

The generated block is a library-level keyword:

```
HYDRATECURVE LABEL = "LINNORM_HYD", \
             PRESSURE = (10.0000,27.2727,...) bara, \
             TEMPERATURE = (0.6871,8.9597,...) C
```

referenced from the flowpath by label - this line is what
`getHydrateCheckKeyword()` returns:

```
NETWORKCOMPONENT TYPE=FLOWPATH, TAG=FLOWPATH_1
 ...
 HYDRATECHECK HYDRATECURVE="LINNORM_HYD"
ENDNETWORKCOMPONENT
```

Notes:

- The fluid **must** contain a water component; the generator refuses a dry fluid
  rather than producing a meaningless curve.
- It works on a copy, so the caller's fluid keeps its pressure and temperature.
- Pressures where the hydrate flash does not converge are **dropped**, not written
  as zero: a zero temperature in the curve silently moves the hydrate boundary
  instead of failing.
- Values are written in plain fixed point. OLGA's parser is locale-independent and
  will not accept a comma decimal separator.
- For an inhibited curve, add MEG or methanol to the fluid before generating; the
  shift then comes from the NeqSim hydrate model rather than from OLGA's
  `HAMMERSCHMIDT` key.

Report the margin in OLGA with the `DTHYD` profile variable (hydrate temperature
minus section temperature); `DPHYD` is the pressure equivalent. On the Linnorm
export line with 15 m3/hr of free water, OLGA's hydrate temperature reproduced the
12-point NeqSim curve to within **0.012 K**, the residual being OLGA's own linear
interpolation between the supplied points.

## What cannot be generated from NeqSim

| Feature | OLGA input | Can NeqSim supply it? |
| --- | --- | --- |
| Hydrate curve | `HYDRATECURVE` + `HYDRATECHECK` | **Yes** - `OLGAhydrateCurveGenerator` |
| Hydrate kinetics | `HYDRATEKINETICS` scalars (`STRUCTURE`, `GASGUESTFRACTION`, ...) | Partly - NeqSim's hydrate flash gives structure sI/sII and guest occupancies as scalars |
| Wax deposition | `WAXDEPOSITION` tuning keys, wax thermodynamics inside the PVT table | Physics yes (`PhaseWax`, WAT, wax fraction), but the OLGA wax **table column format** is not implemented |
| Emulsion / inversion | `WATEROPTIONS` scalars: `INVERSIONWATERFRAC`, `WATERSLIP`, `ENTRAINMENTFACTOR`, `PHI100`, `EMAX` | No file to generate - these are case scalars. NeqSim can only supply calibration values |

There is no `WAXTABLE`, `WAXFILE` or `EMULSION` keyword in OLGA 2025.1; both were
checked against the rules engine.

## Related documentation

- [Flash Calculations Guide](../thermo/flash_calculations_guide.md)
- [Fluid Creation Guide](../thermo/fluid_creation_guide.md)
- [Pipeline and Flow Assurance](../process/index.md)
