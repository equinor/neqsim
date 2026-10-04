---
name: neqsim-release-source-terms
description: "This skill guides agents through NeqSim release-source-term calculations. USE WHEN: estimating a leak or rupture rate, selecting an orifice or pipe-decompression model, building a time-resolved source term, or handing release conditions to fire or dispersion analysis. Anchors: ReleaseFlowRequest, ReleaseFlowResult, HomogeneousEquilibriumReleaseModel, ReleaseInventory, SourceTermSession."
last_verified: "2026-10-03"
---

# Release Source Terms

## When to use this

Use this skill to calculate an instantaneous release boundary condition or sample a release from a process inventory. First classify the opening as a short orifice, a finite-length pipe, or a depleting vessel/pipe inventory. Keep the selected model and its `ReleaseModelEvidence` with the result.

Use `neqsim-consequence-analysis` for downstream fire, dispersion, explosion, and fatality calculations. Use `neqsim-depressurization-mdmt` for vessel blowdown and MDMT. Use `neqsim-trapped-liquid-fire-rupture` for a pipe that heats and ruptures under fire. Those skills own their consequence workflows; this skill owns the source boundary only.

## Class map

| Class | Package | What it does | Verified API |
|---|---|---|---|
| `ReleaseFlowModel`, `ReleaseFlowRequest`, `ReleaseFlowResult` | `neqsim.process.safety.release` | Model-neutral instantaneous boundary and immutable status/evidence result | `calculate(ReleaseFlowRequest)`, `getStatus()`, `isUsable()`, `getMassFlowRateKgS()` |
| `LegacyScreeningReleaseModel`, `LeakModel` | `neqsim.process.safety.release` | Compatibility adapter for the historical leak-rate screening equation | `getModelId()`, `getEvidence()`; legacy model remains screening-level |
| `IdealGasReleaseModel` | `neqsim.process.safety.release` | Calorically perfect, single-gas short-orifice equation | `calculate(request)` |
| `IdealGasFannoPipeReleaseModel`, `RealGasFannoPipeReleaseModel` | `neqsim.process.safety.release` | Finite constant-area pipe release with Darcy friction; ideal-gas or EOS marching | `calculate(request)` |
| `HomogeneousEquilibriumReleaseModel` | `neqsim.process.safety.release` | EOS-backed, isentropic short-opening flashing with equilibrium phase transfer and zero slip | `calculate(request)`, `getEvidence()` |
| `DriftFluxHomogeneousEquilibriumReleaseModel`, `SlipCorrectedHomogeneousEquilibriumReleaseModel` | `neqsim.process.safety.release` | Sensitivity alternatives that modify homogeneous phase velocity/area behavior | `calculate(request)`, `getMaximumGasAreaFraction()` on drift-flux model |
| `FiniteRateDriftFluxReleaseModel`, `RanzMarshallFiniteRateReleaseModel`, `ComponentSelectiveFiniteRateReleaseModel` | `neqsim.process.safety.release` | Finite-rate phase transfer using declared residence/relaxation inputs; optional component-specific times | Constructors accept surface tension and model-specific time/provenance inputs; `calculate(request)` |
| `RanzMarshallMassTransferCorrelation` | `neqsim.process.safety.release` | Mass-transfer correlation used by finite-rate release models; not a standalone release-rate solver | `getApplicabilityMessage()`, `getMorphology()`, `getSauterMeanDiameterM()` |
| `ReleaseInventory` | `neqsim.process.safety.release` | Coupled vessel inventory and opening whose mass changes during native process transient execution | `run(UUID)`, `getReleaseRequest()`, `getReleaseResult()` |
| `IdealGasPipeDecompression`, `RealGasPipeDecompression` | `neqsim.process.safety.release` | Coupled one-dimensional pipe-inventory decompression | `run(UUID)`, `getReleaseResult()`, `getReleaseProvenance()` |
| `SourceTermSession`, `SourceTermFrame` | `neqsim.process.safety.release` | Samples one or more process sources into sequenced, fingerprinted JSON/CSV frames | `addSource(...)`, `addLongPipeSource(...)`, `capture(Map<String, UUID>)`, `toJson()`, `toCsvRow()` |
| `ReleaseModelEvidence`, `ReleaseSolidRiskAssessment` | `neqsim.process.safety.release` | Explicit applicability/validation manifest and solid/hydrate risk guard | `getEvidence()`, `getStatus()` |

### Model-selection ladder

1. **Legacy screening**: use only to reproduce the historical `LeakModel` equation. It is not a qualified physical release model.
2. **Ideal gas**: use for a dry, single gas phase when constant heat-capacity ratio and ideal-gas behavior are acceptable. The implementation requires usable gas properties; it does not supply missing gamma or molecular weight defaults.
3. **Real-gas pipe**: for a long, constant-area pipe with a declared length and Darcy factor, select `RealGasFannoPipeReleaseModel`. `IdealGasFannoPipeReleaseModel` is the simpler comparison case. Neither is a transient inventory solver.
4. **Homogeneous equilibrium (HEM)**: for a short opening where EOS phase change matters and equilibrium/zero-slip assumptions are accepted. It is not a pipe-friction, heat-transfer, solid-bearing, or delayed-flashing model.
5. **Drift-flux / finite-rate**: use as an explicit sensitivity when slip or phase-transfer lag may change the source. Supply measured or justified surface tension, residence/relaxation time, and component-specific time constants as required. These are not a substitute for a validated two-fluid rupture model.

Reject a model if the request has pipe geometry but the chosen model requires a short opening. The request constructor uses SI units: diameter m, receiving absolute pressure Pa, length m, and dimensionless discharge coefficient/Darcy friction factor. Fluid amount establishes composition; it is not the release rate.

## Build pattern

The smallest useful comparison is a one-shot HEM or ideal-gas calculation. Choose one model explicitly; do not silently fall back after an unsupported result.

```java
import neqsim.process.safety.release.HomogeneousEquilibriumReleaseModel;
import neqsim.process.safety.release.ReleaseFlowRequest;
import neqsim.process.safety.release.ReleaseFlowResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
fluid.addComponent("methane", 0.90);
fluid.addComponent("ethane", 0.08);
fluid.addComponent("CO2", 0.02);
fluid.setMixingRule("classic");
ReleaseFlowRequest request = new ReleaseFlowRequest(fluid, 0.010, 0.62, 101325.0);
ReleaseFlowResult result = new HomogeneousEquilibriumReleaseModel().calculate(request);
if (!result.isUsable()) {
  throw new IllegalStateException("Release calculation: " + result.getStatus());
}
double massFlowKgS = result.getMassFlowRateKgS();
```

Equivalent Python class lookup:

```python
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ReleaseFlowRequest = jneqsim.process.safety.release.ReleaseFlowRequest
HomogeneousEquilibriumReleaseModel = jneqsim.process.safety.release.HomogeneousEquilibriumReleaseModel
fluid = SystemSrkEos(300.0, 50.0)
fluid.addComponent("methane", 0.90)
fluid.addComponent("ethane", 0.08)
fluid.addComponent("CO2", 0.02)
fluid.setMixingRule("classic")
request = ReleaseFlowRequest(fluid, 0.010, 0.62, 101325.0)
result = HomogeneousEquilibriumReleaseModel().calculate(request)
if not result.isUsable():
    raise RuntimeError("Release calculation: " + str(result.getStatus()))
mass_flow_kg_s = result.getMassFlowRateKgS()
```

For a running process, create `SourceTermSession(scenarioId, process)` or `SourceTermSession(scenarioId, processModel)`, register an opening with `addSource(...)`, then call `capture(expectedAreaCalculations)` after the process step. Register `ReleaseInventory`/pipe-decompression equipment with `addInventorySource(...)` so the session does not calculate a second hypothetical withdrawal. The caller must serialize access to the source process.

## Result extraction

| Getter | Unit / meaning |
|---|---|
| `getStatus()` / `isUsable()` | `VALID`, `VALID_WITH_WARNINGS`, `INVALID`, or `UNSUPPORTED`; only usable results carry physical values |
| `getMassFlowRateKgS()` | kg/s; throws for unusable result rather than returning a misleading zero |
| `isChoked()` | Boolean classification of accepted opening flow |
| `getStations()` | `Station` to `ReleaseState`; pressure Pa, temperature K, density kg/m3, velocity m/s, mass flux kg/(m2 s) |
| `getDiagnostics()` | Assumptions, warnings, closure and unsupported-physics codes |
| `getEvidence()` | Applicability, known limitations and evidence references for the selected model |
| `SourceTermFrame.getSimulationTimeS()` | Process simulation time s, distinct from frame timestamp |
| `SourceTermFrame.toJson()` / `toCsvRow()` | Compact versioned frame or reduced time-series row; unavailable mass flow is empty, not zero |

Map the selected station and component/phase mass fractions into the input contract of the downstream consequence model. Keep original pressure/temperature units explicit during conversion; release request pressure is absolute Pa, while many process APIs use bara.

## Gotchas

- `ReleaseFlowResult` numerical validity is not engineering qualification. Inspect status, diagnostics, model identity/version and evidence before consuming any rate.
- HEM explicitly assumes equilibrium phase transfer, zero slip, no pipe friction, no heat transfer and no solid-bearing flow. It can return `UNSUPPORTED`/`INVALID`; do not read rate on failure.
- The pressure in `ReleaseFlowRequest` is absolute Pa. `backPressurePa` is not barg. The supplied fluid is cloned, and its total moles only set composition/state.
- Short-orifice models reject requests containing finite pipe length. For Fanno models, set both pipe length and Darcy factor positive; request validation requires both zero or both positive.
- `SourceTermSession` ordinary sources are hypothetical and do not withdraw inventory. Only `CoupledReleaseSource` equipment owns real inventory depletion. A session lock cannot protect concurrent edits through another process reference.
- `ReleaseInventory`/pipe equipment exports the end-of-step instantaneous rate; cumulative mass/energy balance belongs to the coupled equipment.
- Correlation and finite-rate inputs are caller supplied. Correlation availability or a good numerical closure is not evidence of field calibration.
- The model ladder is not an accuracy ranking. Select by physics required, then validate against an independent reference or test case.

## Validation / benchmarks

The source tree includes analytic ideal-gas choked/high-backpressure tests, HEM conservation and phase-flashing regressions, dilute real-gas Fanno comparison against the ideal-gas limit, and model-specific sensitivity tests. Use these as software regression evidence, not universal accuracy claims. At task level, check mass/energy/entropy closure, zero-forward-flow behavior, monotonic response to backpressure/diameter where expected, and sensitivity to model assumptions. Record the exact NeqSim version and a source-specific validation case.

There is no direct MCP runner for `ReleaseFlowModel` or `SourceTermSession` in `neqsim.mcp.runners` at this verification. Consequence MCP tools operate downstream; transfer a validated source frame explicitly. Do not present a risk or consequence estimate as a certified safety study.

## Related skills

- `neqsim-consequence-analysis` — fire, dispersion, explosion and QRA consequence calculations
- `neqsim-depressurization-mdmt` — vessel blowdown and MDMT
- `neqsim-trapped-liquid-fire-rupture` — fire-heated pipe rupture source terms
- `neqsim-process-safety` — barriers, HAZOP, LOPA/SIL and overpressure context
- `neqsim-flow-assurance` — hydrate and solid risk context