---
title: Interfacial Properties
description: Calculate and validate gas-oil, gas-aqueous, and oil-aqueous interfacial tension with the current NeqSim API.
keywords: surface tension, interfacial tension, parachor, gradient theory, cDFT, adsorption
---

NeqSim exposes surface- and interfacial-tension calculations through
`SystemInterface.getInterphaseProperties()`. The calculation belongs to the flashed
thermodynamic state: establish the expected phases first, initialize properties, select a
model, and then evaluate the interface.

Parachor and Firozabadi-Ramley initialize the configured physical mass-density model
for the requested phases on each evaluation. This supports calls directly after a
flash and refreshes finite density caches left by an earlier state. It does not
initialize viscosity, conductivity or diffusion, and it does not replace a failed
density model with an EOS estimate. Use `initProperties()` when those additional
properties are needed.

Cloning a fluid preserves its interfacial model selections and numerical parameters,
with independent models bound to the clone's bulk phases. Model changes and flashes
on the clone do not change the original's interfacial calculations. A copied gradient
model must complete its own calculation before exposing density or pressure profiles.

Process models now receive the clone's calculated tension where they previously could
have used an initialization-error fallback. The three-phase pipeline regression, for
example, uses 13.5265 mN/m instead of a 20 mN/m fallback. Its annular film closure now
approaches the intermittent branch continuously at the unchanged bridging limit; see
the [two-fluid model description](../process/TWOFLUIDPIPE_MODEL.md).

For implementation review, see the
[thermodynamic initialization skill](../../.github/skills/neqsim-thermodynamic-initialization/SKILL.md).

## Units and phase identity

`getSurfaceTension(int, int)` and the `SystemInterface.getInterfacialTension(...)`
facade return **N/m**. Convert explicitly when reporting mN/m:

```java
double sigmaNPerM = fluid.getInterfacialTension("gas", "oil");
double sigmaMilliNPerM = sigmaNPerM * 1000.0;
```

The current `getSurfaceTension(int, int, String unit)` implementation does not convert
the value: it returns the same N/m result for every unit string. Do not use that overload
for unit conversion.

Phase numbers are flash results, not stable labels. Resolve them from phase names and
pass the gas phase first for gas-oil and gas-aqueous calculations. The lower-level
dispatcher recognizes the ordered pairs gas-oil and gas-aqueous; reversing either pair
routes to the liquid-liquid model.

```java
if (!fluid.hasPhaseType("gas") || !fluid.hasPhaseType("oil")) {
  throw new IllegalStateException("The flashed state does not contain gas and oil phases");
}

int gas = fluid.getPhaseNumberOfPhase("gas");
int oil = fluid.getPhaseNumberOfPhase("oil");
double sigmaNPerM =
    fluid.getInterphaseProperties().getSurfaceTension(gas, oil);

if (!Double.isFinite(sigmaNPerM) || sigmaNPerM < 0.0) {
  throw new IllegalStateException("Invalid gas-oil interfacial tension");
}
```

The named facade fails closed: `getInterfacialTension("gas", "aqueous")` returns
`Double.NaN` if either requested phase is absent. Check `Double.isFinite(...)` before an
IFT value enters equipment sizing, optimization, or a control calculation.

## Complete gas-oil workflow

This pure-component bubble-point example has deterministic gas and oil phase identities
and exercises the same workflow as the executable documentation regression.

```java
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

SystemInterface fluid = new SystemPrEos(120.0, 1.0); // K, bara
fluid.addComponent("methane", 1.0);
fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);

ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
try {
  operations.bubblePointPressureFlash(false);
} catch (Exception exception) {
  throw new IllegalStateException("Bubble-point flash failed", exception);
}
fluid.initProperties();

if (!fluid.hasPhaseType("gas") || !fluid.hasPhaseType("oil")) {
  throw new IllegalStateException("Expected gas and oil at the bubble point");
}

fluid.getInterphaseProperties()
    .setInterfacialTensionModel("gas", "oil", "Parachor");
int gas = fluid.getPhaseNumberOfPhase("gas");
int oil = fluid.getPhaseNumberOfPhase("oil");
double sigmaNPerM =
    fluid.getInterphaseProperties().getSurfaceTension(gas, oil);
double sigmaMilliNPerM = sigmaNPerM * 1000.0;
```

For a pressure or temperature sweep, flash and confirm the required phase pair at every
state. A single-phase result is not an IFT value and should be recorded separately.

## Selecting a model

### Named selector

The named selector has the form:

```java
fluid.getInterphaseProperties()
    .setInterfacialTensionModel("gas", "oil", "Full Gradient Theory");
```

Use these exact, case-sensitive model names:

| Accepted name | Implementation |
|---|---|
| `Parachor`, `Weinaug-Katz` | `ParachorSurfaceTension` |
| `Full Gradient Theory` | `GTSurfaceTension` |
| `Simple Gradient Theory` | `GTSurfaceTensionSimple` |
| `Linear Gradient Theory` | `LGTSurfaceTension` |
| `cDFT`, `Classical DFT` | `CDFTSurfaceTension` |
| `Firozabadi Ramley` | `FirozabadiRamleyInterfaceTension` |

The accepted interface names are also exact and ordered: `("gas", "oil")`,
`("gas", "aqueous")`, and `("oil", "aqueous")`. An unknown model name currently
constructs a Parachor model, while an unknown or reversed interface pair leaves the
selected interface unchanged. Validate configuration strings before calling the API;
silent fallback is unsuitable for traceable engineering calculations.

### Numbered model sets

`setInterfacialTensionModel(int)` initializes all three interface models as one set. It
does not select a model from pressure, temperature, or composition.

| Set | Gas-oil | Gas-aqueous | Oil-aqueous |
|---:|---|---|---|
| 0 | Parachor | Parachor | Firozabadi-Ramley |
| 1 | Full GT | Simple GT | Simple GT |
| 2 | LGT | LGT | LGT |
| 3 | Parachor | Parachor | Firozabadi-Ramley |
| 4 | Simple GT | Parachor | LGT |
| 5 | Parachor | Parachor | Firozabadi-Ramley |

Set 0 is the initialized default. Values outside 0-5 select Parachor for all three
interfaces. Prefer the named selector when one interface needs an explicit, auditable
model choice.

## Model boundaries

### Parachor (Macleod-Sugden)

The Parachor model relates surface tension to the phase-density/composition contrast and
component parachors. It is the default gas-liquid model and is computationally useful
for screening. The result is only as reliable as the equilibrium state, equation of
state, mixing rule, and component parachor data. CPA components, including
electrolyte CPA subclasses, read the `PARACHOR_CPA` database column; other
components read `PARACHOR`.

### Gradient-theory models

Full, simple, and linear gradient-theory implementations use progressively different
approximations to the interfacial density profile. Full Gradient Theory is the most
detailed of these selectors and generally the most computationally demanding. Do not
interpret the selector name as a validated accuracy guarantee: benchmark the chosen
equation of state and influence parameters against data in the intended range.

Simple Gradient Theory solves the transposed density-gradient system through
`LinearAlgebraOperations` with EJML, and uses Commons Math for the inner Newton
system. Simple and Linear Gradient Theory select the component with the largest
absolute bulk molar-density contrast as the density-path reference. Selecting a
trace high-boiling component, such as water in the gas-oil pair of a three-phase
fluid, can otherwise follow a different density branch. Simple GT uses a relative
density-update convergence tolerance of $10^{-12}$, above round-off in the
chemical-potential derivatives. Both density-path models also check that the last
point approaches the requested second bulk phase within two grid intervals of the
larger bulk total molar density.

### Classical density functional theory

`cDFT` and `Classical DFT` are aliases for `CDFTSurfaceTension`. The model is available
for pure fluids and mixtures with non-aqueous interfaces. Its dispersive cubic-EOS
functional is not qualified for aqueous interfaces: those requests use the logged
correlation fallback described below. Treat cDFT as an explicit model selection and
validate its numerical result for the fluid and state.

### Firozabadi-Ramley

The default oil-aqueous selector is `Firozabadi Ramley`. Use the canonical
`("oil", "aqueous")` interface order. Confirm that the flashed state contains both
liquid phases before evaluating it.

### Invalid states, incomplete profiles and fallback

All six model implementations validate the active phase pair and finite, positive
bulk temperature, pressure, molar volume and molar mass before calculating. Bulk
compositions and cubic-EOS densities beyond the covolume are also rejected.
Invalid or identical indices in a multiphase fluid raise `IllegalArgumentException`; invalid
bulk states raise `IllegalStateException`. The shared dispatcher also rejects
non-finite or negative results. For compatibility, an indexed calculation on a
single-phase system returns zero for no interface; the named system facade still
returns `NaN` for a missing requested phase.

An unsuccessful density-profile calculation never returns a partial integral or
converts a negative result to zero. Simple GT and Linear GT discard their density,
pressure and position profiles when any step fails. Their profile getters, and the
Simple GT derivative getter, then raise `IllegalStateException`, including after a
previous successful calculation. Simple GT influence-parameter fitting requires a
completed GT profile and cannot fit the fallback correlation.

| Selected model | Response to a failed calculation |
|---|---|
| Simple GT, Linear GT, Full GT | Warn with the selected model and failure reason, then calculate the default correlation for the actual phase pair |
| cDFT | Same fallback for invalid/non-finite/negative results, invalid numerical parameters, or an aqueous interface |
| Parachor, Firozabadi-Ramley | Reject invalid physical density or a non-finite/negative correlation result with an exception |

The fallback is **Parachor for an interface containing gas** and
**Firozabadi-Ramley for two liquid phases**. It uses the requested bulk phase
indices, including when a third phase is present. If the fallback also fails, the
calculation raises an exception. A fallback value is a correlation estimate, not a
converged density-functional or gradient-theory result. Preserve the warning in
engineering calculation records.

Explicitly selecting Parachor for oil-water retains that correlation's formula.
Component contributions can cancel strongly for this interface, producing very low
values even when the arithmetic is finite. Prefer the default liquid-liquid model
and qualify predictions against relevant measurements; a finite value alone is not
an accuracy check.

## Three-phase calculations

Resolve all three phase numbers by name and preserve the dispatcher order:

```java
if (!(fluid.hasPhaseType("gas")
    && fluid.hasPhaseType("oil")
    && fluid.hasPhaseType("aqueous"))) {
  throw new IllegalStateException("Expected gas, oil, and aqueous phases");
}

int gas = fluid.getPhaseNumberOfPhase("gas");
int oil = fluid.getPhaseNumberOfPhase("oil");
int aqueous = fluid.getPhaseNumberOfPhase("aqueous");

double gasOil = fluid.getInterphaseProperties().getSurfaceTension(gas, oil);
double gasWater = fluid.getInterphaseProperties().getSurfaceTension(gas, aqueous);
double oilWater = fluid.getInterphaseProperties().getSurfaceTension(oil, aqueous);
```

Do not enumerate arbitrary `i, j` combinations and assume dispatch is symmetric.

The numerical regressions exercise every named model and model sets 0-5 with both
SRK and PR, independent gas-oil, gas-water and oil-water fluids, and all three
interfaces in one methane/heptane/water fluid. They check phase identity,
repeatability, unchanged bulk states, useful magnitude, fallback selection and
nearby-temperature trends. Existing hydrocarbon GT snapshots and cDFT literature
benchmarks provide additional coverage.

For the synthetic three-phase SRK regression (0.5 methane, 0.3 n-heptane,
0.2 water on a molar basis; mixing rule 2; 10 bara), default model set 0 gives:

| Temperature (K) | Gas-oil (mN/m) | Gas-water (mN/m) | Oil-water (mN/m) |
|---:|---:|---:|---:|
| 300 | 19.2871 | 71.0670 | 50.0621 |
| 320 | 17.3438 | 65.8114 | 46.1660 |

These are reproducibility and trend checks on a synthetic EOS case, not measured
data or a universal accuracy qualification of every selector.

## Adsorption is a separate subsystem

Solid adsorption models are exposed by the same interphase-properties object, but they
do not calculate fluid-fluid IFT. Select an isotherm explicitly when the default DRA
potential-theory model is not intended:

```java
import neqsim.physicalproperties.interfaceproperties.solidadsorption.IsothermType;

fluid.getInterphaseProperties().initAdsorption(IsothermType.LANGMUIR);
fluid.getInterphaseProperties().setSolidAdsorbentMaterial("Zeolite 13X");
fluid.getInterphaseProperties().calcAdsorption();
```

Supported enum values are `DRA`, `LANGMUIR`, `EXTENDED_LANGMUIR`, `FREUNDLICH`, `BET`,
and `SIPS`. Material identifiers, fitted parameters, units, and data provenance must be
part of the simulation input and validation record. See [Adsorption isotherms](../thermo/adsorption_isotherms.md)
for model equations and parameter APIs.

## Engineering validation checklist

- Confirm the flash produced the intended phases at every state.
- Resolve phase numbers from phase names; preserve gas-first and oil-aqueous ordering.
- Record the exact model name or numbered model set and its parameter provenance.
- Treat the API result as N/m and convert explicitly for reports.
- Reject `NaN`, infinite, or negative results before downstream use.
- Compare against measurements or an accepted correlation over the operating envelope.
- Check sensitivity to equation of state, mixing rule, composition, and near-critical
  phase disappearance.

The methods and model names above are anchored to
[`InterfaceProperties.java`](../../src/main/java/neqsim/physicalproperties/interfaceproperties/InterfaceProperties.java)
and [`SystemThermo.java`](../../src/main/java/neqsim/thermo/system/SystemThermo.java).
