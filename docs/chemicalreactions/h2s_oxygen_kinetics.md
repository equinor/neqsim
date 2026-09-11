---
title: Aqueous H2S oxidation screening
description: Primary-source, fail-closed screening for abiotic total-sulfide oxidation by air-saturated oxygen.
---

`AqueousHydrogenSulfideOxidationKinetics` implements the simplified correlation published by
Millero, Hubinger, Fernandez, and Garnett (1987),
[doi:10.1021/es00159a003](https://doi.org/10.1021/es00159a003), for loss of total dissolved
sulfide in air-saturated water, seawater, and NaCl solutions:

$$
-\frac{d[\mathrm{H_2S}]_T}{dt}
  = k[\mathrm{O_2}][\mathrm{H_2S}]_T,
$$

$$
\log_{10} k = 10.50 + 0.16\,\mathrm{pH}
  - \frac{3000}{T} + 0.44\sqrt{I}.
$$

The original source reports `k` on a kg-water mol-1 h-1 basis. Temperature is in K and
ionic strength is supplied on the mol/kg-water scale. The implementation reproduces the source
coefficient `0.44`. A later review by Luther et al. (2011) transcribes `0.49` in its
summary equation; that secondary value is not used.

## Evidence and validity boundary

The simplified source equation is accepted only inside its published range:

- temperature 278.15–338.15 K;
- pH 4–8;
- ionic strength 0–6 mol/kg water;
- aqueous, air-saturated water/NaCl/seawater experiments;
- initial total sulfide about 25 +/- 5 micromol/kg water;
- atmospheric-pressure evidence.

Every numerical input must be finite. A temperature, pH, or ionic strength outside these limits
throws an `IllegalArgumentException` instead of extrapolating. The source reports a standard
deviation of `0.18` in `log10(k)`. `secondOrderRateConstantRange(...)` returns the
corresponding multiplicative interval, using a factor of `10^0.18 = 1.51356`. This interval is
fit scatter, not complete predictive uncertainty.

The source also reports nominal half-times at 298.15 K and pH 8 of 50 +/- 16 h in water and
26 +/- 9 h in seawater. These observations are contextual checks, not independent validation data,
because they were used in developing the same source analysis.

## Constant-oxygen screening

The caller must establish an air-saturated dissolved-oxygen molality independently.
`pseudoFirstOrderRateConstant(...)` calculates `k[O2]` and
`screenAirSaturatedExposure(...)` applies the exact constant-oxygen solution:

$$
f_{\mathrm{remaining}} = \exp(-k[\mathrm{O_2}]t).
$$

For the illustrative inputs 298.15 K, pH 8, ionic strength 0.723 mol/kg water, and dissolved
oxygen 250 micromol/kg water, the correlation gives `k = 123.6175 kg water/(mol h)` and
a nominal half-life of `22.4288 h`. The oxygen value is an example input, not a new
solubility correlation or a source benchmark.

The analytical update is deterministic, gives exactly one remaining fraction at zero time, remains
bounded from zero to one, and avoids timestep error. It reports total-sulfide loss only. It does not
assign products or consume oxygen, because the source correlation does not supply a complete
product stoichiometry for that purpose.

## Residence-time range

`screenResidenceTimeRange(...)` compares a caller-provided aqueous residence time with the
Millero pseudo-first-order chemical time over the published fit-scatter interval:

$
\tau = \frac{1}{k[\mathrm{O_2}]}, \qquad
\mathrm{Da} = \frac{t_{\mathrm{res}}}{\tau}
             = k[\mathrm{O_2}]t_{\mathrm{res}}.
$

The immutable result reports lower, nominal, and upper pseudo-first-order rates, chemical times,
Damkohler numbers, and remaining fractions. Lower and upper refer to the source's
one-standard-deviation `log10(k)` fit interval. At the illustrative nominal half-life of
`22.4288 h`, the nominal Damkohler number is `ln(2)` and the nominal remaining fraction is
exactly `0.5`; the lower-rate result retains more sulfide and the upper-rate result retains less.

A zero residence time is an exact identity. Increasing residence time monotonically reduces every
reported remaining fraction. Non-finite inputs or any multiplication/reciprocal overflow fail
closed. The method deliberately reports continuous Damkohler evidence and does not add categorical
reaction/transport thresholds. A caller may compare this diagnostic with an independently
established transport time, but that does not constitute a pipeline source-term coupling or
high-pressure qualification.

## Target-time inversion

`timeToRemainingFractionRange(...)` inverts the same constant-oxygen first-order solution for a
requested remaining-total-sulfide fraction `f`:

$
E_{\mathrm{required}} = -\ln f, \qquad
t_{\mathrm{required}} = \frac{-\ln f}{k[\mathrm{O_2}]}.
$

The immutable result reports the target fraction, required dimensionless exposure, lower/nominal/
upper pseudo-first-order rates, and the shortest, nominal, and longest required times. The shortest
time uses the upper fit-scatter rate; the longest uses the lower rate. For the illustrative state
above, a target fraction of `0.5` reproduces the nominal half-life of `22.4288 h`. Substituting
each reported time and its corresponding rate back into `exp(-k[O2]t)` recovers the requested
fraction.

A target fraction of exactly one returns zero required exposure and zero time. The valid target
interval is `(0, 1]`; exact zero is rejected because the first-order model approaches zero only
as time tends to infinity. Stricter targets require monotonically longer times. Non-finite inputs,
underflowed rates, or a required-time overflow fail closed.

This inverse calculation is a screening diagnostic, not an equipment-sizing guarantee. It retains
the same air-saturated constant-oxygen, atmospheric-pressure, fit-scatter, and unidentified-product
limitations. It does not establish a pipeline residence time, mass-transfer rate, or high-pressure
reaction extent.

## Piecewise exposure trajectory

`AqueousHydrogenSulfideOxidationTrajectory.advance(...)` propagates the same correlation through
a non-empty ordered list of constant-state segments. For segment `i`, it computes

$
E_i = k_i[\mathrm{O_2}]_i\Delta t_i, \qquad
[\mathrm{H_2S}]_{T,n} = [\mathrm{H_2S}]_{T,0}
\exp\left(-\sum_{i=1}^{n}E_i\right).
$

This analytical composition of exponentials has no numerical timestep error. Segment splitting at
an unchanged state therefore leaves the result invariant. The implementation preserves source
order and reports each segment's second-order rate, pseudo-first-order rate, individual exposure,
and cumulative exposure for audit. Zero-duration segments are accepted as exact identity steps.

Initial total sulfide must be within the source experiment interval of 20–30 micromol/kg water.
Every segment independently applies the temperature, pH, ionic-strength, and caller-supplied
air-saturated-O2 gates above. A pressure value is deliberately not part of the segment contract.

The reported `0.18` scatter in `log10(k)` is propagated as one common multiplicative correlation
envelope across the trajectory. The result reports nominal, lower-rate, and upper-rate cumulative
exposures and final molalities. Treating this as one systematic envelope avoids implying that
successive segments contain independent experimental errors. It remains fit scatter rather than a
complete uncertainty model.

The trajectory reports total-sulfide inventory closure as initial minus final minus reacted
molality. It retains the same constant-oxygen and unidentified-product boundary as the single-state
screen. Within this first-order screening model, only cumulative exposure controls the final
fraction; the ordered diagnostics do not introduce path-dependent chemistry.

## Per-segment inventory evidence

Each immutable `SegmentResult` also exposes inlet, outlet, and reacted total-sulfide molality
for the lower-rate, nominal, and upper-rate paths. For each path `r` and segment `i`,

$
c_{r,i,\mathrm{out}} = c_0\exp(-E_{r,i,\mathrm{cumulative}}), \qquad
c_{r,i,\mathrm{reacted}} = c_{r,i,\mathrm{in}}-c_{r,i,\mathrm{out}}.
$

The first inlet equals the supplied initial total-sulfide molality. Every later inlet is exactly
the preceding segment outlet, so each segment closes as `inlet = outlet + reacted` and the
reacted increments telescope to the trajectory's initial-minus-final inventory. A zero-duration
segment has identical inlet and outlet and reports zero reaction. Splitting an unchanged segment
preserves both final inventory and the sum of reacted increments.

At every segment outlet, the lower-rate path retains the most total sulfide and the upper-rate
path retains the least. Over the complete shared trajectory, those paths respectively give the
least and most total reacted sulfide. A single segment's reacted increment need not follow that
ordering after different prior depletion. These values are analytical bookkeeping for total
dissolved sulfide under the same constant-oxygen assumption. They do not define oxygen
consumption, sulfur products, stoichiometric source terms, or a pipeline control-volume coupling.

## Endpoint loss-rate evidence

Each segment also reports the instantaneous total-sulfide loss rate at its inlet and outlet for
the lower-rate, nominal, and upper-rate paths:

$
r_{r,i,\mathrm{in}} = k_{r,i}[\mathrm{O_2}]_i c_{r,i,\mathrm{in}}, \qquad
r_{r,i,\mathrm{out}} = k_{r,i}[\mathrm{O_2}]_i c_{r,i,\mathrm{out}}.
$

The rate unit is mol total sulfide/(kg water h). The local retention factor is

$
R_{r,i}=\exp\left(-k_{r,i}[\mathrm{O_2}]_i\Delta t_i\right),
\qquad c_{r,i,\mathrm{out}}=c_{r,i,\mathrm{in}}R_{r,i}.
$

For a positive-duration segment, the outlet loss rate cannot exceed its inlet loss rate. A
zero-duration segment gives exactly `R = 1` and equal endpoint rates. Multiplying local retention
factors across unchanged-state segment subdivisions recovers the unsplit retention and final
inventory.

These endpoint rates are differential screening evidence evaluated under the source's
constant-oxygen assumption. By themselves they are not a time-averaged control-volume source, a
conversion to molar flow, oxygen consumption, or product stoichiometry.

## Segment-mean loss-rate evidence

For each lower-rate, nominal, and upper-rate path, `SegmentResult` also exposes the analytical
mean total-sulfide loss rate over the segment. For positive duration,

$
\overline{r}_{r,i}
=\frac{c_{r,i,\mathrm{in}}-c_{r,i,\mathrm{out}}}{\Delta t_i}
=\frac{c_{r,i,\mathrm{reacted}}}{\Delta t_i}.
$

Its unit is mol total sulfide/(kg water h). Because the differential rate decays monotonically
within a constant-state segment, the mean is bounded by the endpoint rates:

$
r_{r,i,\mathrm{out}}\leq\overline{r}_{r,i}\leq r_{r,i,\mathrm{in}}.
$

Multiplying the mean by segment duration exactly recovers the reacted molality. For a zero-duration
segment the quotient would be undefined, so the API returns its exact continuous limit
`k[O2] c_in`; this equals both endpoint rates and remains finite. For an unchanged-state segment
split into subsegments, duration-weighted mean rates recover the unsplit reacted inventory and
unsplit mean rate.

This is a time average on a molality basis, not yet a volumetric or molar-flow control-volume
source. Creating a pipeline source term requires separately qualified water inventory,
phase transfer, oxygen consumption, reaction products, energy, pressure, and numerical coupling.

## Explicit water-inventory projection

`AqueousHydrogenSulfideOxidationWaterInventoryProjection.project(...)` converts one immutable
segment result from the molality basis to dimensional total-sulfide loss evidence using a
caller-supplied liquid-water inventory `m_w` in kg:

$
\dot n_{r,i}=\overline{r}_{r,i}m_w, \qquad
n_{r,i,\mathrm{reacted}}=c_{r,i,\mathrm{reacted}}m_w.
$

The immutable result reports lower-rate, nominal, and upper-rate mean loss in mol/h and mol/s,
together with reacted total sulfide in mol. The water inventory must be finite and strictly
positive. For positive segment duration, each path closes exactly as

$
\dot n_{r,i}\Delta t_i=n_{r,i,\mathrm{reacted}}.
$

At zero duration, reacted amount is exactly zero while the mean-loss output preserves the existing
finite differential limit. Dimensional values scale linearly with water inventory. If an unchanged
state is subdivided while the same water inventory is used for each subsegment, the reacted-mole
increments sum to the unsplit result.

The supplied water inventory is explicit evidence, not a holdup or free-water prediction. This
projection is an unsigned total-sulfide loss potential, not a component source applied to a control
volume. It does not infer water dropout, consume O2, assign products or selectivity, calculate
reaction heat, pressure or phase transfer, mutate pipeline/transient state, or update sulfur
deposition and wall inventories. Those steps require separately qualified inputs and must compose
with the existing pipeline and sulfur-deposition implementations.


## Constant-water trajectory projection

The overloaded
`AqueousHydrogenSulfideOxidationWaterInventoryProjection.project(...)` applies one
caller-supplied constant liquid-water inventory to a complete immutable trajectory. It preserves
the source order and returns every existing per-segment dimensional projection together with
cumulative lower-rate, nominal, and upper-rate reacted total-sulfide amounts.

For each fit-scatter path `r`, the segment increments telescope at constant water inventory:

$
n_{r,\mathrm{reacted}}
= \sum_i n_{r,i,\mathrm{reacted}}
= m_w(c_0-c_{r,n,\mathrm{out}}).
$

The result exposes the difference between the summed increments and this endpoint identity as a
closure residual in mol. A one-segment trajectory exactly reuses the existing segment projection,
an unchanged-state subdivision preserves cumulative reacted moles, and a trajectory containing
only a zero-duration segment reports exactly zero reaction. The segment list is defensively
immutable. A null trajectory, non-finite or non-positive inventory, numerical underflow, or
non-finite accumulation fails closed.

The constant inventory is an explicit caller assumption; it is not a calculated pipeline holdup,
water-dropout history, or moving material-parcel model. The cumulative amounts remain unsigned
total-sulfide-loss evidence. They do not consume O2, assign sulfur products, calculate heat or
phase transfer, create signed component sources, mutate pipeline/transient state, or update S8,
FeS, wall, or sulfur-deposition inventories.

## Piecewise target crossing

`AqueousHydrogenSulfideOxidationTrajectory.timeToRemainingFractionRange(...)` locates where a
requested remaining-total-sulfide fraction is first reached inside a finite ordered segment list.
It reuses the segment rates and the same required exposure as the single-state inverse:

$E_{\mathrm{target}}=-\ln f.$

For each lower, nominal, and upper fit-scatter path, cumulative exposure is summed through complete
segments. Inside the crossing segment, the remaining exposure is divided by that segment's
pseudo-first-order rate. The immutable result reports:

- the requested fraction and required exposure;
- shortest, nominal, and longest elapsed crossing times;
- the source-order segment index for each crossing;
- the total duration supplied by the caller.

The upper-rate path gives the shortest time and the lower-rate path gives the longest. A target
fraction of one crosses at exact time zero in segment zero. An unchanged-state segment split cannot
change any reported crossing time. Substituting each result back into the corresponding piecewise
cumulative exposure recovers `-ln(f)`.

The target interval remains `(0, 1]`. Every lower, nominal, and upper path must reach the target
within the supplied finite trajectory. If even the slow lower-rate path does not reach it, the
method throws an `IllegalArgumentException`; it does not extend the final state, extrapolate a
residence time, or return infinity.

This diagnostic identifies a crossing within caller-defined aqueous screening segments. It does
not locate a position in a pipeline, calculate flow residence time, size equipment, or couple the
reaction to phase behavior, mass transfer, oxygen depletion, or a transient solver.

## Scientific stop boundary

This capability does not:

- calculate pH, H2S/HS- speciation, activities, or charge balance;
- calculate oxygen solubility or verify that the supplied oxygen value is air saturated;
- qualify elevated pressure, dense-phase CO2, free-water appearance, or gas-to-water transfer;
- bind the correlation to experimental R1–R8 constants;
- calculate corrosion, sulfur products, reaction heat, or material response;
- mutate a thermodynamic system, run a flash, add a transient or pipeline source term, or expose an
  MCP workflow.

Electrolyte pH/speciation remains owned by issue #3144. TP flash, dynamics, pipeline coupling, and
MCP publication remain coordinated with #2937, #2911, the pipeline roadmap, and #3153. Applying
this atmospheric aqueous correlation to a Northern-Lights-type high-pressure CO2 pipeline requires
separate phase, pressure, mass-transfer, and composition evidence.
