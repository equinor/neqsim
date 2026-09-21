---
title: Model-explicit release source terms
description: Explicit homogeneous-equilibrium, ideal-gas and legacy-screening release models with immutable thermodynamic stations and fail-closed diagnostics.
---

# Model-explicit release source terms

Use the [neutral source-term contract](source-term-contract) to export these results as
versioned JSON, newline-delimited frames or a reduced CSV time series.
Use [uncertainty ensembles](source-term-uncertainty) to propagate joint input cases without
discarding failures. The [implementation status](source-term-platform-status) records remaining work.

`ReleaseFlowModel` calculates an instantaneous short-opening source term from a NeqSim
fluid. It is independent of downstream safety software and process orchestration. Requests
defensively copy the fluid; results and station snapshots are immutable and serializable.
This is the first implementation layer of [NRC-3860](https://github.com/equinor/neqsim/pull/3861).

## Usage

The following example is executed by `ReleaseFlowModelTest.documentationExampleAndEnergyClosure`:

```java
SystemInterface gas = new SystemSrkEos(300.0, 50.0);
gas.addComponent("methane", 1.0);
gas.setMixingRule("classic");
ReleaseFlowModel model = new HomogeneousEquilibriumReleaseModel();
ReleaseFlowResult result = LeakModel.builder().fluid(gas).holeDiameter(0.01)
    .dischargeCoefficient(0.62).backPressure(101325.0).build()
    .calculateReleaseFlow(gas, model);
```

Import the types from `neqsim.process.safety.release` and `neqsim.thermo.system`.
Check `result.isUsable()` and inspect `getDiagnostics()` before accessing the release rate.
The rate getter throws on invalid or unsupported results; failure cannot look like zero flow.
For direct use, construct `ReleaseFlowRequest(fluid, diameterM, dischargeCoefficient,
backPressurePa)` and call the model's `calculate` method. Malformed request geometry throws
at construction; thermodynamic/model failures produce diagnostic results.

## Explicit model selection

Model selection is never inferred from phase count. Choose one implementation and retain its
identity, version and diagnostics with every frame:

| Model | Intended use | Required behavior |
|---|---|---|
| `HomogeneousEquilibriumReleaseModel` | Short-opening equilibrium gas, liquid and flashing calculations. | EOS/flash closure is strict; unsupported phase physics and failed required properties return no numeric payload. |
| `IdealGasReleaseModel` | Analytical gas checks and dilute-gas screening with constant $\gamma$. | Requires one gas phase plus finite NeqSim molar mass and $\gamma$; no property default or model fallback. |
| `LegacyScreeningReleaseModel` | Reproduce the historical `LeakModel.calculateMassFlowRate` rate during migration. | Always returns `VALID_WITH_WARNINGS` when usable and carries `SCREENING_ONLY`, unresolved-station and legacy-fallback diagnostics. |

For example:

```java
ReleaseFlowModel idealGas = new IdealGasReleaseModel();
ReleaseFlowModel legacyCompatibility = new LegacyScreeningReleaseModel();
ReleaseFlowResult analytical = idealGas.calculate(
    new ReleaseFlowRequest(gas, 0.01, 0.62, 101325.0));
```

The legacy adapter delegates the numeric rate to the historical scalar method, including its
documented density, molar-mass and heat-capacity-ratio fallback policy. It does not relabel that
behavior as strict physics. Because the legacy equation never solves throat or exit thermodynamics,
its opening snapshots alias the initialized upstream pressure and temperature. Consumers can detect
this limitation from `UNRESOLVED_STATIONS`; they must not interpret those snapshots as a nozzle
solution.

## Ideal-gas equations

The ideal-gas adapter uses the initialized mixture molar mass $M$ [kg/mol] and heat-capacity ratio
$\gamma$ [1] without replacement values. With $R=8.31446261815324$ J/(mol K), the critical pressure
ratio, isentropic station temperature, density and velocity are:

$$r_c=\left(\frac{2}{\gamma+1}\right)^{\gamma/(\gamma-1)},\quad \frac{T}{T_0}=\left(\frac{p}{p_0}\right)^{(\gamma-1)/\gamma}.$$

$$\rho=\frac{pM}{RT},\quad c_p=\frac{\gamma R}{(\gamma-1)M},\quad u=\sqrt{2c_p(T_0-T)},\quad \dot m=C_dA\rho u.$$

The accepted throat pressure is $p_0r_c$ when $p_b/p_0\le r_c$ and $p_b$ otherwise. For a choked
state, the analytical velocity equals $\sqrt{\gamma RT/M}$. Station enthalpy uses the upstream
NeqSim reference minus $u^2/2$ and station entropy retains the upstream reference. This preserves
relative energy/entropy consistency without presenting an arbitrary absolute ideal-gas reference
as new property data. The adapter supports one gas phase only and excludes reaction, forced phases,
solid/hydrate checks, real-gas departure, phase change, friction, heat transfer and depletion.

## Homogeneous-equilibrium equations and station semantics

The upstream TP flash establishes stagnation enthalpy $h_0$ [J/kg] and entropy $s_0$
[J/(kg K)]. Pure-fluid pressure samples use PS flashes to retain the saturation-quality
degree of freedom. Mixtures use cold TP flashes in a bracketed temperature root at fixed
specific entropy and inventory (target residual `1e-7 J/(kg K)`), following the equilibrium
acoustic API's approach. The selected numerical route is recorded in `ENTROPY_SOLVER`:

$$u(p)=\sqrt{2(h_0-h(p,s_0))},\quad G(p)=\rho(p,s_0)u(p),\quad \dot m=C_d A\max_{p_b\le p\le p_0}G(p).$$

Here $u$ is velocity [m/s], $G$ inviscid mass flux [kg/(m2 s)], $\rho$ EOS total density
[kg/m3], $C_d$ the supplied discharge coefficient, and $A=\pi d^2/4$ physical area [m2].
The coefficient multiplies effective flow area; it does not change the isentropic velocity.
Phase amounts are converted to mass fractions from phase mass, never from molar phase fraction.
EOS mass/volume density is used consistently with EOS enthalpy and entropy.

| Station | Definition |
|---|---|
| `UPSTREAM_STAGNATION` | Equilibrated reservoir state with zero velocity. |
| `THROAT_CRITICAL` | Maximum sampled/refined mass-flux state; receiving-pressure state when unchoked. |
| `ORIFICE_EXIT` | Explicit alias of the throat for the zero-length opening assumption. |
| `AMBIENT_EXPANDED` | Ideal isentropic state at receiving pressure; excludes shocks, entrainment and mixing. |

An interior maximum indicates choking. A 48-interval logarithmic pressure scan brackets
each resolved local maximum, then golden-section refinement reduces bracket width below
`1e-7 * upstreamPressure`. This finite search is not a proof that arbitrarily narrow maxima
cannot exist. Phase-boundary cases require resolution/sensitivity and benchmark evidence.

Every trial checks entropy closure (absolute `1e-5 J/(kg K)`), component conservation
(`1e-8` of total moles), phase composition normalization (`1e-8`), interphase log-fugacity
ratios (`1e-5`, for components above `1e-10` in both phases), and finite physical properties.
Negative enthalpy drops below `-1e-5 J/kg` fail; smaller roundoff is clipped to zero.

At a choked state the equilibrium acoustic API supplies an independent diagnostic. An unavailable
acoustic derivative or a Mach discrepancy greater than 5% produces `VALID_WITH_WARNINGS`,
with the reason retained. Acoustic speed is optional; required flash or closure failures
always invalidate the calculation. No ideal-gas property defaults or replacement model are used.

For upstream pressure at or below back pressure, the result is valid zero forward flow, with
`NO_FORWARD_FLOW`; there is no ambient-expanded station and no reverse-flow calculation.

## Applicability and evidence

| Case | Behavior |
|---|---|
| Nonreacting gas, liquid or equilibrium fluid mixture | Calculates the stated homogeneous-equilibrium model. |
| Flashing hydrocarbon liquid | Supports equilibrium phase redistribution with zero slip. |
| Forced phases, reactions, solid or hydrate checking enabled | `UNSUPPORTED`. |
| CO2 present below 216.592 K anywhere along expansion | Conservatively `UNSUPPORTED`; a separately assessed solid-capable model is required. |
| Full-bore pipe rupture, slip, delayed flashing, heat transfer or friction | Outside this model's physics. |
| Failed flash, unclosed inventory/entropy/fugacity or invalid density | `INVALID`, without a numeric release payload. |

The CO2 temperature guard is conservative: it is not a mixture solid-equilibrium boundary.
Turning off solid checks does not establish absence of solids. The caller remains responsible
for selecting a supported thermodynamic basis and checking phase-formation risks.

Evidence is software regression and analytical dilute-gas comparison, not independent
experimental qualification. Tests cover the ideal-gas choked limit (rate and pressure),
unchoked/no-flow limits, energy/entropy closure, flashing pure propane, a methane/ethane
gas mixture, input
immutability, serialization, invalid/unsupported inputs, and area/coefficient scaling.
No method is certified for a facility or safety decision by these tests.

A known regression case (80 mol% propane, 20 mol% n-butane, 300 K, 20 bara to 3 bara)
encounters a numerically unresolved entropy root near 8.23 bara. It intentionally returns
`INVALID`. The regression protects this fail-closed behavior; it is not a validated source
term for that mixture. Full mixture-flashing coverage needs further thermodynamic validation.

## Compatibility and extension

Existing scalar `LeakModel` methods and its lumped blowdown calculation keep their existing
screening behavior. `LegacyScreeningReleaseModel` is an opt-in compatibility adapter that proves
rate equality while exposing screening limitations in machine-readable diagnostics. The
`calculateReleaseFlow` method still requires explicit model selection and does not change legacy
blowdown equations. This prevents a model switch from silently relabeling old results. Use process
dynamics for time-dependent state evolution.

Additional models implement the serializable `ReleaseFlowModel` interface and return checked
`ReleaseFlowResult.success` or `.failure` objects with stable identity/version and diagnostic
codes. Station snapshots expose overall component mole and mass fractions and native phase
mass fractions in deterministic name order. A new physical model needs its own applicability
documentation and independent validation evidence.
