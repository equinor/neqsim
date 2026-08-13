---
title: "ISO 17776 MAH Bow-Tie and EI AVIFF Flow-Induced Vibration Screening"
description: "Generate a major-accident-hazard bow-tie from a pre-built ISO 17776 catalogue and screen piping for flow-induced vibration and flow-induced pulsation using the Energy Institute AVIFF likelihood-of-failure method — MahBowTieBuilder, MahCatalogue, PipingFivScreening, FlowInducedVibrationAnalyser and FlowInducedPulsationScreening. Covers the fluid-viscosity factor FVF, dry-gas versus wet-gas behaviour, and dead-leg acoustic lock-in."
keywords: "ISO 17776, MAH, major accident hazard, bow-tie, threats, barriers, consequences, Energy Institute, AVIFF, flow induced vibration, FIV, flow induced pulsation, FIP, dead leg, side branch, acoustic resonance, lock-in, Strouhal, likelihood of failure, LOF, FVF, fluid viscosity factor, piping vibration"
---

# ISO 17776 MAH Bow-Tie and EI AVIFF Flow-Induced Vibration Screening

| Class | Purpose | Standard |
|-------|---------|----------|
| `MahCatalogue` | Pre-defined threats, consequences and barriers per MAH type | ISO 17776 |
| `MahBowTieBuilder` | Assemble a `BowTieModel` for a major-accident hazard | ISO 17776 |
| `PipingFivScreening` | Factored likelihood-of-failure screening for flow-induced vibration | Energy Institute AVIFF |
| `FivLikelihoodResult` | LOF score and likelihood band for one circuit | Energy Institute AVIFF |
| `FlowInducedVibrationAnalyser` | Rigorous LOF from a solved `PipeBeggsAndBrills` segment | Energy Institute AVIFF |
| `FlowInducedPulsationScreening` | Acoustic lock-in screening for closed side branches (dead legs) | Energy Institute AVIFF T2.6 |
| `FlowInducedPulsationResult` | Branch modes, lock-in envelopes and resonance velocities | Energy Institute AVIFF T2.6 |

Classes live under `neqsim.process.safety.hazid`,
`neqsim.process.safety.vibration` and `neqsim.process.measurementdevice`.

## MAH bow-tie from the ISO 17776 catalogue

`MahBowTieBuilder.build(MahType)` returns a fully populated
`BowTieModel` (threats on the left, consequences on the right, barriers in the
middle) for a standard major-accident-hazard type:

```java
import neqsim.process.safety.hazid.MahType;
import neqsim.process.safety.hazid.MahBowTieBuilder;
import neqsim.process.safety.hazid.MahCatalogue;
import neqsim.process.safety.risk.bowtie.BowTieModel;

BowTieModel bowtie = MahBowTieBuilder.build(MahType.TOPSIDE_HYDROCARBON_RELEASE);

String hazard = bowtie.getHazardId();
bowtie.getThreats();        // ≥ 4 threats, each with getFrequency()
bowtie.getConsequences();   // ≥ 3 consequences
bowtie.getBarriers();       // ≥ 5 barriers, each with getPfd()

// Inspect the raw catalogue entries directly
MahCatalogue.threatsFor(MahType.TOPSIDE_HYDROCARBON_RELEASE);
MahCatalogue.consequencesFor(MahType.TOPSIDE_HYDROCARBON_RELEASE);
MahCatalogue.barriersFor(MahType.TOPSIDE_HYDROCARBON_RELEASE);
```

Default threat frequency and barrier PFD are exposed as
`MahBowTieBuilder.DEFAULT_THREAT_FREQUENCY` and
`MahBowTieBuilder.DEFAULT_BARRIER_PFD`. `MahType` covers
`TOPSIDE_HYDROCARBON_RELEASE`, `RISER_LEAK`, `WELL_BLOWOUT`,
`STRUCTURAL_COLLAPSE`, `DROPPED_OBJECT`, `HELICOPTER_LOSS`, `SHIP_COLLISION`,
`FIRE_EXPLOSION`, `TOXIC_RELEASE`, `LOSS_OF_BUOYANCY`, and `EXTREME_WEATHER`,
each carrying a human-readable description.

## EI AVIFF flow-induced-vibration screening

`PipingFivScreening` computes an Energy Institute AVIFF likelihood-of-failure
(LOF) score for a piping circuit and maps it to a likelihood band. Use
`screenGas` or `screenLiquid` depending on the fluid:

```java
import neqsim.process.safety.vibration.PipingFivScreening;
import neqsim.process.safety.vibration.PipingFivLikelihood;
import neqsim.process.safety.vibration.FivLikelihoodResult;

// Gas circuit: tag, rho[kg/m3], v[m/s], D[m], wall t[m], nBranches, pulsation, support
FivLikelihoodResult gas = PipingFivScreening.screenGas(
    "Compressor discharge", 80.0, 30.0, 0.3, 0.006, 2, 4.0, 2.0);

double lof = gas.getLofScore();
PipingFivLikelihood band = gas.getLikelihood();   // LOW / MEDIUM / HIGH / VERY_HIGH
String json = gas.toJson();                        // contains "lofScore", "likelihood"

// Liquid circuit: tag, v[m/s], D[m], wall t[m], nBranches, support
FivLikelihoodResult liquid = PipingFivScreening.screenLiquid(
    "Pump discharge", 3.5, 0.15, 0.005, 1, 1.5);

// Map an arbitrary LOF score to a band
PipingFivLikelihood b = PipingFivScreening.bandFor(0.7);   // HIGH
```

The likelihood bands are `LOW` (< 0.3), `MEDIUM` (0.3–0.5), `HIGH` (0.5–1.0),
and `VERY_HIGH` (≥ 1.0). Invalid geometry (zero diameter, negative velocity)
throws `IllegalArgumentException`.

## Choosing the right vibration tool

NeqSim has three complementary vibration screenings. They answer different questions
and a "pass" on one does not clear the others:

| Class | Mechanism | Question it answers |
|---|---|---|
| `PipingFivScreening` | Main-line FIV, factored | Quick desktop LOF from density, velocity and D/t |
| `FlowInducedVibrationAnalyser` | Main-line FIV, rigorous | LOF from a solved `PipeBeggsAndBrills` segment, with the real mixture density, velocity and void fraction |
| `FlowInducedPulsationScreening` | Tonal FIP at closed side branches | Whether a dead leg can lock into acoustic resonance |
| `AcousticInducedVibrationScreening` | Broadband AIV | Sound power downstream of a pressure-reducing device |

## Rigorous main-line LOF from a solved pipe

`FlowInducedVibrationAnalyser` evaluates the AVIFF form

$$\mathrm{LOF} = \frac{\rho_m v_m^2\, F_{VF}}{F_v}, \qquad F_v = \alpha\left(\frac{D}{t}\right)^{\beta}$$

on a segment of a `PipeBeggsAndBrills` that has been run, so the mixture density,
mixture velocity and void fraction come from the flow solution rather than from
hand estimates.

The fluid-viscosity factor `F_VF` switches on the void fraction β:

| Void fraction β | `F_VF` |
|---|---|
| β < 0.2 | `0.2 + 4β` |
| 0.2 ≤ β ≤ 0.88 | `1.0` (liquid and general multiphase) |
| 0.88 < β ≤ 0.99 | `-27.882 β² + 45.545 β - 17.495` (wet gas) |
| β > 0.99 | `sqrt(μ_gas / 1 cP)` (gas dominated) |

> **Physical sanity check.** `F_VF` must *fall* as β goes to 1. The wet-gas branch
> reaches 0.268 at β = 0.99, so a single-phase gas must come out below that —
> a hydrocarbon gas at 0.012–0.018 cP gives about 0.11. Removing liquid from a
> wet-gas line **lowers** the vibration driver; at equal standard rate and pressure
> the wet-over-dry driver ratio for a rich gas at 40–50 bara is roughly 3 to 4.
> If a calculation reports the opposite, `F_VF` is being evaluated wrongly.
> `REFERENCE_VISCOSITY_CP` is exposed as a public constant so the branch can be
> reproduced independently. Note that `PipeBeggsAndBrills.getSegmentMixtureViscosity`
> returns **centipoise**, not Pa·s.

```java
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.measurementdevice.FlowInducedVibrationAnalyser;

PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("inlet pipe", feed);
pipe.setDiameter(0.3652);
pipe.setThickness(0.0206);      // REQUIRED: the LOF correlation divides by D/t
pipe.setLength(12.0);
pipe.setNumberOfIncrements(4);

FlowInducedVibrationAnalyser fiv = new FlowInducedVibrationAnalyser("LOF", pipe);
fiv.setMethod("LOF");
fiv.setSupportArrangement("Medium stiff");   // Stiff / Medium stiff / Medium / Flexible

process.add(feed);
process.add(pipe);
process.run();

double lof = fiv.getMeasuredValue("");
```

Omitting `setThickness` throws `IllegalStateException` rather than silently
returning `NaN`. The support arrangement is a qualitative stiffness category,
not a support spacing.

### Calibrated LOF ratios when the line size is unknown

A common situation is that a design LOF is quoted but the line list is not
available. For two operating points **on the same line** the pipe factor `F_v`
and the flow area cancel exactly, so

$$\frac{\mathrm{LOF}_2}{\mathrm{LOF}_1}
= \frac{(\rho_m v_m^2 F_{VF})_2}{(\rho_m v_m^2 F_{VF})_1}$$

is independent of diameter, wall thickness and support category. Reproduce the
stated design point with an assumed geometry, then report every other case as a
ratio to it. Confirm the cancellation numerically by re-running one case with a
different `setSupportArrangement(...)` — the calibrated ratio must not move.

## Flow-induced pulsation at closed side branches

`FlowInducedPulsationScreening` covers the tonal, acoustically resonant mechanism
that main-line FIV screening does not. Flow past the mouth of a dead leg sheds a
shear layer; when the shedding frequency falls within ±20 % of a standing acoustic
mode of the branch the two lock in and the branch self-excites.

The procedure is:

1. **Acoustic length** `L` — centreline distance from the tee to the *first acoustic
   boundary* (normally closed valve, blind, or a large volume such as a separator,
   cooler or KO drum). No end correction is applied.
2. **Eigenfrequencies** — `f_n = (2n+1)c/(4L)` for a `CLOSED` termination (R = +1),
   `f_n = (n+1)c/(2L)` for `OPEN` (R = −1), with n starting at 0.
3. **Excitation** — `f_s = Sr·U0/W_eff` where `W_eff = π·d_s/4 + r_eff` is the
   effective width of the branch mouth, **not** the branch diameter. `Sr = 0.37`
   is the recommended screening value for side-branch modes, `0.20` for the
   main-header mode.
4. **Resonance check** — possible when `0.8 f_n ≤ f_s ≤ 1.2 f_n`.

```java
import neqsim.process.safety.vibration.FlowInducedPulsationScreening;
import neqsim.process.safety.vibration.FlowInducedPulsationResult;

// name, acoustic length [m], branch ID [m], run ID [m], U0 [m/s], rho [kg/m3], c [m/s]
FlowInducedPulsationResult res = FlowInducedPulsationScreening.screen(
    "Closed cross-over stub", 3.0, 0.2477, 0.3652, 20.1, 46.2, 374.0);

boolean resonance = res.isAnyModeLockedIn();
double fs = res.getSheddingFrequencyHz();
for (FlowInducedPulsationResult.BranchMode m : res.getModes()) {
  // m.getModeIndex(), m.getFrequencyHz(), m.getEnvelopeLowHz(), m.getEnvelopeHighHz(),
  // m.isLockedIn(), m.getResonanceVelocityMPerS()
}

// Helpers for building length or velocity windows without running a full screening
double weff = FlowInducedPulsationScreening.effectiveWidth(0.2477, 0.0);
double f0 = FlowInducedPulsationScreening.eigenFrequency(
    0, 374.0, 3.0, FlowInducedPulsationScreening.AcousticTermination.CLOSED);
```

The full overload takes the edge radius, the termination, the Strouhal number and
the mode count.

### Why a wet-gas measurement campaign cannot clear dry-gas service

Main-line FIV *relaxes* when a line goes dry, but flow-induced pulsation moves the
other way. Even a small amount of a second phase affects not only the vortex
shedding but also the acoustic damping and the speed of sound in the branch, so
drying the gas removes three protections at once: the liquid that damped the
resonator, the slugging that disrupted the shear layer, and — for drains — the
liquid filling that set the branch sound speed near 850–1000 m/s instead of
~375 m/s. Run-pipe accelerometers are largely blind to branch pulsation, so a
clean main-line vibration record does not clear this mechanism.

Because the ±20 % envelope makes the resonant length windows narrow, per-branch
verdicts are highly sensitive to the acoustic length. Where as-built lengths are
not available, invert the criterion instead and report the **resonant length
window** per branch size:

$$L \in \left[(1-0.2)\frac{(2n+1)c}{4 f_s},\; (1+0.2)\frac{(2n+1)c}{4 f_s}\right]$$

which a walkdown can check directly with a tape measure.

## Verification

```bash
./mvnw test -Dtest=MahBowTieBuilderTest,PipingFivScreeningTest
./mvnw test -Dtest=FlowInducedVibrationAnalyserTest,FlowInducedPulsationScreeningTest
```

`FlowInducedPulsationScreeningTest` reproduces a published worked example: a 3 m
closed branch at c = 400 m/s gives f₀ = 33.3 Hz, f₁ = 100 Hz, f₂ = 166.7 Hz.
`FlowInducedVibrationAnalyserTest` asserts that dry-gas LOF stays below wet-gas
LOF at equal standard rate and pressure.

## Related Documentation

- [Event and Fault Trees](event_fault_trees.md)
- [Barrier Management and SCE Traceability](barrier_management.md)
- [Automated HAZOP from STID and Simulation](automated_hazop_from_stid.md)
