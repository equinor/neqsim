---
title: DNV-RP-F104 CO2 pipeline transport-envelope screening
description: Edition-aware, fail-closed composition and pressure-temperature margin screening for CO2 pipelines.
---

# DNV-RP-F104 CO2 pipeline transport-envelope screening

`DnvRpF104Co2PipelineEnvelopeScreeningKernel` provides a narrow deterministic screen for the
current `DNV-RP-F104 2021-02+AMD:2021-09` basis. It compares a project composition and ordered
pressure-temperature profile with caller-controlled composition limits, a maximum allowable
operating pressure (MAOP), design temperatures, and externally derived single-phase pressure
boundaries. Every result has `SCREENING` maturity and requires engineering review. It is not a DNV
conformity decision and does not reproduce licensed criteria.

DNV describes F104 as a framework for the design, construction, and operation of onshore and
offshore CO2 pipelines, with emphasis on structural assessment and supplementation of referenced
pipeline standards. The [DNV-RP-F104 publisher page](https://www.dnv.com/energy/standards-guidelines/dnv-rp-f104-design-and-operation-of-carbon-dioxide-pipelines/)
lists edition `2021-02`, amended `2021-09`. Use the purchased document and project-controlled
requirements register for applicability, criteria, composition specification, thermodynamic basis,
materials, fracture control, construction, operation, safety, and requalification.

## Calculation boundary

For actual and project-limiting mole fractions, NeqSim reports:

$$
m_{CO_2}=x_{CO_2,actual}-x_{CO_2,min}
$$

$$
m_{water}=x_{water,max}-x_{water,actual}.
$$

At each ordered profile point $i$, the caller supplies operating pressure $P_i$, temperature $T_i$,
and an externally established minimum pressure boundary $P_{single,i}$ for the intended
single-phase transport region. The kernel reports four transparent margins:

$$
m_{single,i}=P_i-P_{single,i}, \qquad m_{MAOP,i}=P_{MAOP}-P_i
$$

$$
m_{T,min,i}=T_i-T_{design,min}, \qquad m_{T,max,i}=T_{design,max}-T_i.
$$

The minimum-pressure interpretation is not a universal phase classifier. The caller must verify
that pressure above each supplied boundary represents the intended single-phase region for the
project composition, temperature, path, EOS, and uncertainty basis. NeqSim deliberately does not
substitute the pure-CO2 critical point for a composition-specific phase envelope.

## Runnable Java example

```java
StandardEdition edition = StandardEdition.defaultEdition(StandardType.DNV_RP_F104);
DnvRpF104Co2PipelineEnvelopeScreeningKernel.Input input =
    DnvRpF104Co2PipelineEnvelopeScreeningKernel.Input
        .builder(edition, "Pipeline")
        .co2MoleFraction(0.98)
        .minimumCo2MoleFraction(0.97)
        .waterMoleFraction(0.0001)
        .maximumWaterMoleFraction(0.0002)
        .otherImpuritiesWithinProjectSpecification(true)
        .designMinimumTemperatureK(273.15)
        .designMaximumTemperatureK(323.15)
        .maximumAllowableOperatingPressurePaAbsolute(15.0e6)
        .addOperatingPoint(new DnvRpF104Co2PipelineEnvelopeScreeningKernel.OperatingPoint(
            "inlet", 0.0, 14.0e6, 293.15, 10.0e6))
        .addOperatingPoint(new DnvRpF104Co2PipelineEnvelopeScreeningKernel.OperatingPoint(
            "outlet", 100000.0, 10.5e6, 283.0, 9.5e6))
        .co2PipelineApplicabilityVerified(true)
        .compositionAndSpecificationVerified(true)
        .thermodynamicModelVerified(true)
        .singlePhaseBoundaryInterpretationVerified(true)
        .operatingProfileVerified(true)
        .pressureTemperatureLimitsVerified(true)
        .materialsCorrosionAndFractureBasisVerified(true)
        .safetyConstructionOperationsAndRequalificationReviewed(true)
        .build();

EngineeringCalculationResult<DnvRpF104Co2PipelineEnvelopeAssessment> result =
    new DnvRpF104Co2PipelineEnvelopeScreeningKernel().calculate(input, null);
Map<String, Object> report = result.getValue().toMap();
```

For these demonstration inputs, the CO2 fraction margin is `0.01 mole fraction`, the water margin
is `0.0001 mole fraction`, and the minimum pressure margin above the caller-controlled single-phase
boundary is `1.0 MPa`. These are project-controlled demonstration values and deterministic
regression data, not limits or benchmark values taken from F104.

The
[executed notebook](https://github.com/equinor/neqsim/blob/master/examples/notebooks/dnv_rp_f104_co2_pipeline_envelope_kernel.ipynb)
uses the same API, plots the operating profile against the supplied phase boundary and MAOP,
retains a below-boundary case as a visible finding, and demonstrates fail-closed behavior when the
thermodynamic evidence flag is absent.

## Fail-closed evidence boundary

Calculation is blocked unless all of the following are explicit and internally valid:

- exact unamended `2021-02+AMD:2021-09` edition and catalogued pipeline/riser applicability;
- mole-fraction inputs between zero and one and ordered, positive design temperatures;
- positive absolute MAOP and at least one profile point;
- unique labels, finite non-negative and strictly increasing distances, and positive absolute
  pressure, temperature, and externally supplied phase-boundary values at every point;
- external verification of F104 applicability, composition/specification, thermodynamic model,
  the minimum-pressure interpretation of the supplied single-phase boundary, operating profile,
  and pressure-temperature limits; and
- confirmation that materials/corrosion/fracture and safety/construction/operation/requalification
  bases have been reviewed outside this narrow calculation.

A negative composition or operating-envelope margin is a visible calculated finding, not a hidden
readiness blocker. Verification flags are attestations: controlled calculations, specifications,
records, uncertainties, and accountable approvals remain external evidence.

## Requirement-pack routing

F104 spans more than one equipment calculation. Its `StandardRequirementPack` therefore maps six
existing NeqSim capabilities with explicit boundaries:

| Capability | NeqSim implementation | Boundary |
| --- | --- | --- |
| CO2 phase behaviour | `ThermodynamicOperations` | Property/phase calculation; composition, EOS validation, uncertainty, and acceptance are external |
| Pipeline hydraulics | `PipeBeggsAndBrills` | Hydraulic/thermal model; CO2 validation and transients are external |
| Dense-CO2 corrosion | `DensePhaseCO2Corrosion` | Legacy heuristic screen only; embedded typical values are not F104 criteria |
| Mechanical design | `PipeMechanicalDesignCalculator` | Preliminary containment screen; F104 and DNV-ST-F101 load cases remain external |
| Impurity monitoring | `ImpurityMonitor` | Model composition monitor; analyser and response requirements remain external |
| Release consequence | `GasDispersionAnalyzer` | Generic dispersion calculation; CO2 release physics, exposure, QRA, and response remain external |

Discover the immutable mapping independently of the typed kernel:

```java
StandardSelection selection = StandardSelection.strictRequirements(StandardType.DNV_RP_F104);
StandardRequirementPack requirements = StandardRegistry.requireRequirementPack(selection);
```

The pack is a capability map, not a clause register or statement of coverage.

## Not implemented

The kernel and pack do not calculate or approve:

- F104 composition limits, impurity interaction limits, EOS selection, or licensed acceptance
  criteria;
- decompression-wave or running-ductile-fracture models, crack arrest, toughness, or fracture
  control;
- material selection/qualification, corrosion control, water specification, hydrate/solid CO2,
  impurity reactions, or contamination management;
- route selection, geohazards, crossings, on-bottom stability, free spans, upheaval/lateral buckling,
  third-party loads, or pipeline-system structural design;
- construction, welding, NDT, testing, commissioning, conversion, operation, integrity management,
  repair, life extension, or requalification;
- release source terms, dispersion, exposure, QRA, emergency response, or environmental impact; or
- conformity assessment, certification, regulatory approval, or accountable engineering approval.

`DensePhaseCO2Corrosion` and `CO2FlowCorrections.isDensePhase(...)` remain useful legacy heuristics
when their limitations are acceptable. Their embedded typical impurity thresholds and pure-CO2
critical-point check are not current-edition F104 evidence and must not populate this kernel's
verified project boundaries without an independent controlled basis.

## Separation from DNV-ST-F101

F104 supplements referenced pipeline standards; this screen does not replace pipeline-system
structural design. DNV-ST-F101 pressure containment, collapse, propagation buckling, local buckling,
load interaction, fatigue, incidental/test pressure, de-rating, safety class, ovality, fabrication
route, and installation-strain checks remain independently required.
