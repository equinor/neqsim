---
title: "OLGA PVT Table and Hydrate Curve Generation"
description: "Generate OLGA .tab PVT property tables and hydrate equilibrium curves from any NeqSim fluid - dry gas, oil, gas condensate, three-phase gas/oil/water, dense-phase CO2. Covers the calculation stages and their configuration (explicit or envelope-refined grids, standard-condition insertion, produced water by water cut, salinity, IAPWS reference water properties, monotonic absent-phase fill), the quality report and Markdown table report, the phase-coverage rules OLGA enforces, and how to validate output by running it in OLGA."
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

## Configuring the table

The calculation has four stages, each configured on the generator before
`run()`:

1. **Fluid preparation** - optional produced water is added to a copy of the feed.
2. **Grid construction** - the pressure and temperature axes are built from a
   range or a list, optionally refined around the water-free phase envelope, and
   optionally given the standard state as a node.
3. **Node evaluation** - a TP flash at every node; each phase present is resolved
   by type and its properties are written. Aqueous properties can come from the
   IAPWS reference equations and be corrected for salinity.
4. **Absent-phase fill and checks** - nodes where a phase does not exist are
   filled, the standard ratios are calculated, and the quality report is built.

Both keyword generators share the grid, fill and report settings; the water
settings exist only on the three-phase generator.

| Setting | Method | Notes |
| --- | --- | --- |
| Equidistant axes | `setPressureRange(min, max, n)` (bara), `setTemperatureRange(min, max, n)` (**K**) | Default construction |
| Explicit axes | `setPressures(double[])`, `setTemperatures(double[])` | Sorted and de-duplicated; every combination is tabulated |
| Envelope refinement | `setEnvelopeRefinement(true, gridFactor)` | Range axes only; `gridFactor` 1-10, see below |
| Standard state on the grid | `setInsertStandardConditions(true)` | Off by default |
| Standard conditions | `setStandardConditions(T_K, P_bara)` | Default 288.15 K and 1.01325 bara; used for GOR, GLR, WC and standard densities |
| Absent-phase fill | `setMonotonicityCorrection(true)` | On by default; see *Absent phases* |
| Produced water | `setProducedWater(value, "watercut" / "mol/mol" / "mol%" / "wt%")` | Three-phase only; feed must be water free |
| Salinity | `setWaterSalinity(value, "mg/l" / "ppm" / "wt%" / "kg/kg")` | Three-phase only |
| Reference water equations | `setUseReferenceWaterProperties(true)` | Three-phase only; off by default |
| Fluid label | `setFluidLabel(label)` | Must equal the genkey `BRANCH FLUID=` |
| Table fluid | `getTableFluid()` | Feed including produced water, after `run()` |
| Checks and report | `getQualityReport()`, `writeTableReport(file)` | After `run()` |

Not available: a fixed-format (non-keyword) table, emulsion viscosity models and
GERG-2008 tables. Emulsion effects belong in the OLGA case (`WATEROPTIONS`).

A three-phase table for a wellstream with 30 % water cut and 35 000 mg/l
salinity:

```java
SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 40.0, 60.0);
fluid.addComponent("methane", 0.80);
fluid.addComponent("ethane", 0.06);
fluid.addComponent("propane", 0.04);
fluid.addComponent("n-heptane", 0.05);
fluid.addComponent("nC10", 0.05);
fluid.addComponent("water", 0.0);           // produced water is added by the generator
fluid.setMixingRule(10);
fluid.setMultiPhaseCheck(true);

OLGApropertyTableGeneratorWaterKeywordFormat generator =
    new OLGApropertyTableGeneratorWaterKeywordFormat(fluid);
generator.setFluidLabel("WELLSTREAM");
generator.setPressureRange(5.0, 250.0, 50);          // bara
generator.setTemperatureRange(253.15, 393.15, 50);   // K
generator.setEnvelopeRefinement(true, 5.0);
generator.setInsertStandardConditions(true);
generator.setProducedWater(30.0, "watercut");        // vol % of the standard liquid
generator.setWaterSalinity(35000.0, "mg/l");
generator.setUseReferenceWaterProperties(true);
generator.run();
generator.writeOLGAinpFile("wellstream.tab");
generator.writeTableReport("wellstream_table_report.md");
generator.getQualityReport().writeCsv("wellstream_table_qc.csv");
```

The feed passed to the constructor is not modified when produced water is added.
The water component may be present with zero moles, as an E300 file read with
added water has it; if it is absent the generator adds it and re-applies the
mixing rule.

### Produced water

| Specification | Meaning |
| --- | --- |
| `watercut` | Volume % water of the total liquid (oil plus water) at standard conditions; solved iteratively by standard-condition flashes, with the aqueous density from the same water model as the table (reference equations, salinity). Requires oil at standard conditions |
| `mol/mol` | Mol produced water per mol feed |
| `mol%` | Mol % water of feed plus produced water |
| `wt%` | Weight % water of feed plus produced water |

Produced water is the total water, including water dissolved in the gas and oil.
A feed that already contains water is rejected.

### Reference water equations

`setUseReferenceWaterProperties(true)` takes the aqueous columns (`ROWT`,
`DROWTDP`, `DROWTDT`, `CPWT`, `HWT`, `SEWT`, `VISWT`, `TCWT`) from
`neqsim.thermo.util.steam.LiquidWaterReference`:

| Property | Reference |
| --- | --- |
| Density, its P and T derivatives, Cp, enthalpy, entropy | IAPWS-IF97 Region 1 Gibbs function, evaluated as a liquid at any pressure |
| Viscosity | IAPWS R12-08, without the critical enhancement |
| Thermal conductivity | IAPWS R15-11, without the critical enhancement |

The equations are applied where the aqueous phase is at least 95 wt% water and
the state lies within 253.15-623.15 K and up to 1000 bara. States below 273.15 K
or below the saturation pressure are metastable extrapolations
(`LiquidWaterReference.isMetastable()`). Inhibitor-rich water (MEG, methanol)
keeps the equation-of-state values, because a pure-water reference is wrong
there; `getReferenceWaterNodeCount()` and `getReferenceWaterSkippedNodeCount()`
report the split. Enthalpy and entropy are shifted to the equation-of-state
reference at standard conditions, so the latent heat between water vapour in the
gas and liquid water stays consistent. Interfacial tensions are not changed:
gas-water tension depends strongly on the gas pressure and is not a pure-water
property.

### Salinity

`setWaterSalinity` corrects aqueous density, its pressure and temperature
derivatives, and viscosity with the NaCl brine correlations of Batzle and Wang
(1992), applied as brine/fresh-water ratios
(`neqsim.thermo.util.steam.BrineSalinityCorrection`). The ratio approach keeps
the accuracy of the underlying water model (equation of state or reference
equations). Salinity does not change phase amounts or compositions, the standard
water density includes it, and it cannot be combined with ionic components in the
feed. A concentration in mg/l is converted to a mass fraction at standard
conditions.

### Grid construction

**Envelope refinement.** `setEnvelopeRefinement(true, gridFactor)` traces the
phase envelope of the water-free feed. Along the pressure axis, if the
cricondenbar lies inside the range, the share
$f = \text{gridFactor}/(\text{gridFactor}+1)$ of the points is placed between the
lowest envelope pressure (or the range minimum) and the cricondenbar, and the rest
outside; the temperature axis does the same with the cricondentherm. Each part is
equidistant. An axis whose envelope maximum lies outside the range stays
equidistant. With `gridFactor = 5` a 50-point axis gets 42 points inside the
envelope. Use at least 50 points per axis so the outside part is not too coarse.

**Standard state on the grid.** `setInsertStandardConditions(true)` makes the
standard pressure and temperature grid nodes when they lie inside the range, so
OLGA reads the standard state without interpolation. A range axis is rebuilt with
one point fewer and the standard value inserted, keeping the point count; an
explicit axis gains one point. A standard value outside the range is not added.

**Water-free critical point.** `CRITICALPRESSURE`, `CRITICALTEMPERATURE` and the
envelope used for refinement are taken from the **water-free** composition: water
is removed when it is the last component and otherwise reduced to a trace. The
water dew line of a wet feed would otherwise dominate the envelope and move the
refined points away from the hydrocarbon two-phase region.

### Quality report and table report

`getQualityReport()` returns an `OlgaTableQualityReport` after `run()`:

- `isLoadable()` - every value finite, and densities, compressibilities,
  viscosities, heat capacities and conductivities positive;
- per column: minimum, maximum, non-finite and non-positive counts, monotonicity
  violations (density along pressure, enthalpy and entropy along temperature)
  split into existing and extrapolated nodes, and the largest relative jump
  between neighbouring nodes inside the phase region and across its boundary;
- `toJson()` for a machine-readable record and `writeCsv(file)` with every node
  (pressure, temperature, keyword, value, phase existence) for plotting.

Violations at existing nodes can be physical: a retrograde condensate liquid
becomes lighter with pressure as gas dissolves. They are reported, not changed.
Violations at extrapolated nodes indicate a fill defect.

`writeTableReport(file)` writes a Markdown engineering report with the fluid and
composition, the grid and how each axis was built, standard ratios and densities,
the water-free envelope, the water models with node counts, and the quality
table. Attach it to the study next to the `.tab` file.

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
4. **`BUBBLEPRESSURES` and `BUBBLETEMPERATURES` must describe matching saturation
   points.** The three-phase generator omits these optional headers for
   multicomponent fluids: a bubble flash of a feed containing free water can
   follow an inappropriate liquid branch. For a pure component, it writes only
   validated subcritical pressure/temperature pairs. Failed points are omitted,
   never replaced by zero. If no valid pure-component point is available, writing
   fails explicitly; include subcritical temperatures in the grid.

## Three-phase fractions and standard ratios

`OLGApropertyTableGeneratorWaterKeywordFormat` uses these definitions:

| Keyword | Definition |
| --- | --- |
| `RS` | Gas mass divided by gas plus oil phase mass at the grid state; the separate aqueous phase is excluded |
| `RSW` | Water mass fraction within the gas phase |
| `GOR` | Standard gas volume divided by standard oil volume |
| `GLR` | Standard gas volume divided by standard oil plus aqueous volume |
| `WC` | Standard aqueous volume divided by standard oil plus aqueous volume, as a fraction from 0 to 1 |

The standard flash uses the written `STDPRESSURE` (1 atm by default) and
`STDTEMPERATURE` (288.15 K by default; change both with `setStandardConditions`). Volumes are calculated from phase mass and physical density, including
any volume correction. `GOR` is `-999` when there is no standard oil, and `GLR` is
`-999` when there is no standard liquid. `WC` is zero when there is no standard
liquid. A gas/water state with no oil has `RS = 1`; a state with no gas has
`RS = 0`, including an aqueous-only state. These fractions are independent of the
placeholder properties used for absent phases.

This corrects earlier three-phase exports that included aqueous mass in `RS`,
copied `GOR` into `GLR`, omitted `WC`, and could write invalid bubble curves.
Regenerate affected `.tab` files before reusing them in OLGA. The regression
coverage checks the reported SRK-CPA wet-condensate case and standard-condition
phase volumes; a full OLGA pipeline run remains a separate validation step.

## Absent phases and how they are filled

A dry gas has no liquid anywhere, a dead oil has no gas, and a gas condensate has
only one phase outside its two-phase envelope. OLGA still expects a full gas
column and a full liquid column at every node.

The generators therefore:

1. resolve phases by **type** (`gas`, `oil`, `aqueous`) rather than by array
   position, so the gas column stays gas even at a single-phase node;
2. fill nodes where a phase is absent. With the monotonicity correction (the
   default), density is filled along pressure and enthalpy and entropy along
   temperature: a gap between two nodes where the phase exists is interpolated
   linearly, and nodes beyond the last existing node are extrapolated with the
   phase's own gradient there (`dRho/dP`, `Cp`, `Cp ln T`). The filled branch is
   therefore monotonic, continuous at the phase boundary and consistent with the
   `DRO*DP` and `CP*` columns. Remaining columns, and grid lines without any
   existing node, use nearest-neighbour extrapolation in grid-index space. With
   `setMonotonicityCorrection(false)` every column uses the nearest neighbour,
   which holds enthalpy flat with temperature in the absent region;
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
| Resolution | 30-50 pressures x 25-35 temperatures is usually enough; refine near the phase envelope with `setEnvelopeRefinement(true, 5.0)` rather than everywhere |
| Standard conditions | `setInsertStandardConditions(true)` puts 1 atm and 15 C on the grid so OLGA interpolates the standard state exactly |

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
