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
