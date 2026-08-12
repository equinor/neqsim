---
title: ISO 5167 orifice-plate metering
description: Edition-aware, fail-closed use of NeqSim's ISO 5167-2 concentric orifice-plate flow calculation.
---

# ISO 5167 orifice-plate metering

NeqSim's `Iso5167OrificeMeteringKernel` exposes the existing Reader-Harris/Gallagher
orifice equations through the common typed engineering-calculation workflow. The registered
calculation basis is the unamended `ISO-5167-2:2022` edition, used with the general requirements
of `ISO-5167-1:2022`.

The ISO catalog lists both parts separately. Part 1 is a companion requirements basis and has no
standalone calculation. Part 2 has the registered kernel because NeqSim implements concentric
orifice-plate flow with corner, flange, and D/D/2 pressure tappings.

## What the kernel calculates

For a supplied operating point, the kernel iterates the existing
`Orifice.calculateDischargeCoefficient(...)` implementation and reports:

- beta ratio and differential/pressure ratio;
- Reader-Harris/Gallagher discharge coefficient;
- expansibility and velocity-of-approach factors;
- mass flow and upstream actual volumetric flow;
- pipe Reynolds number; and
- estimated permanent pressure loss.

Fluid service is explicit. `LIQUID` uses an expansibility factor of exactly one.
`GAS_OR_VAPOUR` applies the existing compressible-flow expansibility equation and therefore
requires an isentropic exponent. This removes an ambiguity in the legacy convenience method without
changing its public behavior.

## Fail-closed applicability

The calculation is blocked unless all of the following are established:

| Basis | Implemented gate |
| --- | --- |
| Edition | Unamended ISO 5167-2:2022 |
| Equipment | `Orifice` |
| Flow | Single phase, full circular conduit, non-pulsating, subsonic throughout the meter |
| Pipe inside diameter | 0.05 m to 1.0 m |
| Beta ratio | 0.10 to 0.75 implemented screening envelope |
| Pipe Reynolds number | At least 5,000 after iteration |
| Pressure | Positive absolute pressures with upstream above downstream |
| Properties | Positive upstream density and dynamic viscosity; gas/vapour kappa above one |
| Installation | Caller attests that plate geometry, tappings, straight lengths, and installation were checked externally |

The installation flag records an attestation; NeqSim does not inspect an installed meter. Keep the
plate inspection, bore at flowing conditions, tapping geometry, upstream/downstream piping,
calibration, and data-quality evidence with the engineering record.

## Java example

```java
StandardEdition edition = StandardEdition.defaultEdition(StandardType.ISO_5167_2);
Iso5167OrificeMeteringKernel.Input input = Iso5167OrificeMeteringKernel.Input
    .builder(edition, "Orifice")
    .serviceType(Iso5167OrificeMeteringKernel.ServiceType.GAS_OR_VAPOUR)
    .tapType(Iso5167OrificeMeteringKernel.TapType.FLANGE)
    .pipeInternalDiameterM(0.100)
    .orificeBoreDiameterM(0.050)
    .upstreamPressurePaAbsolute(500000.0)
    .downstreamPressurePaAbsolute(480000.0)
    .upstreamDensityKgPerM3(5.5)
    .upstreamDynamicViscosityPaS(1.2e-5)
    .isentropicExponent(1.30)
    .singlePhase(true)
    .conduitRunningFull(true)
    .subsonicThroughoutMeter(true)
    .pulsatingFlow(false)
    .geometryAndInstallationVerified(true)
    .build();

EngineeringCalculationResult<Iso5167OrificeMeteringAssessment> result =
    new Iso5167OrificeMeteringKernel().calculate(input, null);
if (result.getStatus() != EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED) {
  throw new IllegalStateException(result.getReadiness().toMap().toString());
}
Iso5167OrificeMeteringAssessment assessment = result.getValue();
double massFlowKgPerS = assessment.getMassFlowRateKgPerS();
```

The executable Python/JPype workflow is in
[`examples/notebooks/iso_5167_orifice_metering_kernel.ipynb`](https://github.com/equinor/neqsim/blob/master/examples/notebooks/iso_5167_orifice_metering_kernel.ipynb).

## Relationship to existing NeqSim APIs

- Use `Iso5167OrificeMeteringKernel` for an explicit ISO 5167-2:2022 basis with immutable inputs,
  edition checks, applicability gates, and a typed review-required result.
- Keep `Orifice` as the process-equipment and transient-simulation API. Its static equation helpers
  remain the numerical basis of the kernel.
- Use `Standard_AGA3` when the governing basis is AGA Report No. 3/API MPMS 14.3 and thermodynamic
  properties should be derived from a NeqSim fluid. Do not relabel an AGA/API calculation as ISO
  5167 evidence.
- `GpsaOrificeCalculator` remains a simplified GPSA/service calculator and is not an exact-edition
  ISO kernel.

## Engineering boundary

The result is `SCREENING` and always requires engineering review. It does not determine or certify
plate manufacture and condition, tapping or straight-length conformity, pulsation effects,
two-phase behavior, compressible choking, thermal expansion of dimensions, uncertainty,
transmitter calibration, sampling, data reconciliation, fiscal allocation, or custody-transfer
acceptance. Use the purchased standards and project metering procedure for those decisions.

Publisher lifecycle sources checked on 2026-08-02:

- [ISO 5167-1:2022](https://www.iso.org/standard/79179.html)
- [ISO 5167-2:2022](https://www.iso.org/standard/79180.html)
