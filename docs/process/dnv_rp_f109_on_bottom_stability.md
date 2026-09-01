---
title: DNV-RP-F109 On-Bottom Stability Screening
description: Typed, fail-closed vertical, absolute-static lateral, and external-response displacement screening for submarine pipelines, cables, and umbilicals.
---

# DNV-RP-F109 on-bottom stability screening

NeqSim provides a typed engineering kernel for transparent early-phase on-bottom stability
screening of submarine pipelines, cables, and umbilicals. The publisher identifies the current
basis as DNV-RP-F109, edition 2021-05, amended 2025-09, covering lateral and vertical stability.
See the [DNV standard page](https://www.dnv.com/energy/standards-guidelines/dnv-rp-f109-on-bottom-stability-design-of-submarine-pipelines/).

The implementation is deliberately bounded. It calculates a transparent absolute-static screen,
or checks displacement supplied by an externally validated generalized or dynamic response model.
It does not reproduce licensed generalized-design tables, generate dynamic response, qualify an
environmental or soil model, or establish conformity with DNV-RP-F109. Every calculated result is
returned as `CALCULATED_REVIEW_REQUIRED`, including a passing screen.

## Package integration

| Responsibility | Package/class |
| --- | --- |
| Typed workflow, readiness findings, edition and applicability gates | `neqsim.process.engineering.calculation.DnvRpF109OnBottomStabilityKernel` |
| Immutable global and per-load-case inputs | `neqsim.process.mechanicaldesign.subsea.DnvRpF109OnBottomStabilityInput` |
| Pure load and resistance calculation | `neqsim.process.mechanicaldesign.subsea.DnvRpF109OnBottomStabilityCalculator` |
| Immutable assessment and intermediate results | `DnvRpF109OnBottomStabilityAssessment` |
| Traceable limit-state result | `DnvRpF109StabilityCheck` |
| Standard discovery | `StandardType`, `StandardCatalog`, `StandardRequirementPackRegistry`, and `EquipmentDesignKernelRegistry` |

The calculation belongs in `mechanicaldesign.subsea` because it combines hydrodynamic loading,
submerged weight, and pipe-soil resistance for a seabed asset. The typed adapter remains in the
shared engineering-calculation package, consistent with other standards kernels.

## Implemented calculation boundary

For each load case, velocity and acceleration normal to the asset axis are calculated from the
caller-supplied directions:

\[
u_{c,n}=|u_c\sin\theta_c|,\quad
u_{w,n}=|u_w\sin\theta_w|,\quad
a_n=|a_w\sin\theta_w|
\]

The transparent screen conservatively adds the normal current and wave velocity magnitudes,
\(u_n=u_{c,n}+u_{w,n}\). With explicit project coefficients and reduction factors, it calculates:

\[
F_D=\frac{1}{2}f_H\rho C_D D u_n^2,\quad
F_I=f_H\rho C_M\frac{\pi D^2}{4}a_n,\quad
F_L=\frac{1}{2}f_V\rho C_L D u_n^2
\]

The factored demands and available lateral resistance are:

\[
H_d=\gamma_H(F_D+F_I),\quad
V_d=\gamma_VF_L,\quad
R_H=\mu\max(W_s-V_d,0)+R_p
\]

where \(W_s\) is actual submerged weight per unit length and \(R_p\) is a caller-supplied,
validated passive soil resistance. The kernel reports vertical utilization \(V_d/W_s\) and
absolute-static lateral utilization \(H_d/R_H\). For a static case it also reports:

\[
W_{s,required}=V_d+\frac{\max(H_d-R_p,0)}{\mu}
\]

The result includes specific gravity derived from submerged weight and displaced-water buoyancy.
Direction is measured relative to the asset axis: 0 degrees is axial and 90 degrees is normal.

For `EXTERNAL_RESPONSE_0_5D`, `EXTERNAL_RESPONSE_10D`, and
`EXTERNAL_RESPONSE_USER_DEFINED`, NeqSim does not generate displacement. It checks an externally
calculated displacement against 0.5 diameter, 10 diameters, or an explicit project limit. The
caller must affirm that the response model is within its validated range and provide a traceable
model basis; otherwise readiness fails closed. DNV describes its StableLines simplified methods
and PILSS dynamic analysis separately on the
[StableLines service page](https://www.dnv.com/services/engineering-analysis-of-pipelines-stablelines-2490/)
and [PILSS service page](https://www.dnv.com/services/pipeline-umbilical-and-cable-lateral-stability-software-pilss-241930/).

## Required inputs

There are no hidden numerical project defaults. A runnable case supplies:

- exact `StandardEdition` (`2021-05+AMD 2025-09`, without project amendments);
- asset and applicable NeqSim equipment type;
- hydrodynamic outside diameter, seawater density, gravitational acceleration, and a traceable
  engineering-basis reference;
- one or more uniquely identified design-condition load cases;
- actual submerged weight, current and wave kinematics and directions;
- drag, lift, and inertia coefficients plus horizontal and vertical load-reduction factors;
- soil friction, validated passive resistance, and horizontal and vertical safety factors;
- storm duration and oscillation count for case provenance and soil/response review; and
- for response routes, displacement, validation-range confirmation, response basis, and any
  project-defined displacement limit.

Storm duration and oscillation count are preserved in provenance but do not alter the transparent
static Morison calculation. They remain important when reviewing cyclic pipe-soil response or an
external generalized/dynamic analysis.

## Java example

```java
LoadCase operating = LoadCase.builder()
    .caseId("operating transverse storm")
    .submergedWeightNPerM(3200.0)
    .currentVelocityMPerS(0.6)
    .waveVelocityMPerS(1.2)
    .waveAccelerationMPerS2(0.8)
    .currentDirectionRelativeToPipeDeg(90.0)
    .waveDirectionRelativeToPipeDeg(90.0)
    .dragCoefficient(1.0)
    .liftCoefficient(0.8)
    .inertiaCoefficient(3.0)
    .horizontalLoadReductionFactor(0.8)
    .verticalLoadReductionFactor(0.7)
    .soilFrictionCoefficient(0.5)
    .passiveSoilResistanceNPerM(100.0)
    .horizontalSafetyFactor(1.1)
    .verticalSafetyFactor(1.1)
    .stormDurationHours(3.0)
    .oscillationCount(1000.0)
    .lateralMethod(LateralMethod.ABSOLUTE_STATIC)
    .build();

DnvRpF109OnBottomStabilityInput input = DnvRpF109OnBottomStabilityInput.builder()
    .edition(StandardEdition.defaultEdition(StandardType.DNV_RP_F109))
    .assetType(AssetType.PIPELINE)
    .equipmentType("Pipeline")
    .outsideDiameterM(0.508)
    .seawaterDensityKgM3(1025.0)
    .gravitationalAccelerationMPerS2(9.81)
    .engineeringBasis("Environmental ENV-1; geotechnical GEO-2; hydrodynamic HYD-3")
    .addLoadCase(operating)
    .build();

EngineeringCalculationResult<DnvRpF109OnBottomStabilityAssessment> result =
    new DnvRpF109OnBottomStabilityKernel().calculate(input, null);
```

Use the executed
[`dnv_rp_f109_on_bottom_stability.ipynb`](https://github.com/equinor/neqsim/blob/master/examples/notebooks/dnv_rp_f109_on_bottom_stability.ipynb)
notebook for load-case comparison, current-velocity sensitivity, and weight/soil-friction design
space. Replace its illustrative coefficients and resistance inputs with project-controlled values.

## Engineering use

A pass means only that the implemented arithmetic passes for the supplied inputs. Before a design
decision, independently verify at least the licensed RP edition, environmental statistics and
kinematics, hydrodynamic coefficients and reduction factors, seabed survey and pipe-soil model,
penetration and passive-resistance basis, cyclic degradation, free-span and crossing interactions,
displacement acceptability, adjacent assets, and installation and operating conditions.
