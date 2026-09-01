---
title: DNV-ST-F101 Pipeline Screening
description: Fail-closed NeqSim screening for submarine-pipeline pressure and structural limit states.
---

# DNV-ST-F101 pipeline screening

NeqSim exposes a typed DNV-ST-F101:2021 screening kernel for early design studies. It keeps
pressure containment, collapse, propagation buckling, local-buckling load interaction, fatigue,
pressure cases, strength de-rating, safety class, ovality, fabrication route, and installation
strain as separate, traceable checks.

> This is not a conformity assessment or certification. A passing result always has status
> `CALCULATED_REVIEW_REQUIRED`. Use the licensed standard, project design basis, approved load-case
> method, installation analysis, fabrication records, and independent engineering verification for
> design approval.

## Why this is separate from the legacy calculator

`PipeMechanicalDesignCalculator.DNV_OS_F101` remains a legacy DNV-OS-F101 wall-thickness and
buckling screen. It does not implement the current standard as a complete load-case model.
`PipelineMechanicalDesign.calcDesign()` therefore fails closed when its string design code is
`DNV-ST-F101`; it will not fall back to ASME B31.8 or relabel the legacy calculation.

Use `DnvStF101PipelineDesignKernel` directly, or call
`PipelineMechanicalDesign.assessDnvStF101(input, context)`. The kernel is also discoverable through
`EquipmentDesignKernelRegistry.lookup(StandardType.DNV_ST_F101)`.

## Implemented screening checks

| Check | Inputs retained explicitly | Screening method | Boundary |
|---|---|---|---|
| Operating containment | Local operating and external pressure | Differential-pressure burst resistance | Not a clause-complete pressure-zone assessment |
| Incidental containment | Local incidental and external pressure | Separate burst utilization | Incidental pressure is not replaced by operating pressure |
| System test | Test and test-external pressure | Separate containment utilization | Mill, installation, and system-test procedures remain project-controlled |
| Collapse | Minimum internal pressure, external pressure, ovality, fabrication factor | Elastic-plastic collapse interaction | Requires approved geometry and fabrication data |
| Propagation buckling | External overpressure, thickness, material and fabrication factor | Propagation-pressure screen | Arrestor design remains external |
| Local buckling/load interaction | Axial force, bending, torsion, pressure | Conservative normalized interaction envelope | Replace with approved clause calculation or nonlinear analysis |
| Fatigue | Stress-range spectrum, S-N curve, SCF, DFF | Palmgren-Miner damage | Curve/detail/environment selection remains external |
| Ovality | Measured/specified and allowable ovality | Independent utilization; also enters collapse | Does not replace fabrication acceptance |
| Installation strain | Axial, bending and accumulated plastic strain | Strain accumulation utilization | Reel-, S- and J-lay history must come from installation analysis |

The characteristic wall used by the resistance screens is

$$
t_{char}=t_{nom}(1-f_{tol})-t_{corr}.
$$

Material strengths are multiplied by the supplied temperature/de-rating factor. Safety class and
the material resistance factor reduce the calculated resistance. NeqSim does not infer these
project-controlled values from operating temperature, installation method, or consequence class.

## Java example

```java
DnvStF101PipelineDesignInput input = DnvStF101PipelineDesignInput.builder()
    .safetyClass(DnvStF101PipelineDesignInput.SafetyClass.MEDIUM)
    .fabricationRoute(DnvStF101PipelineDesignInput.FabricationRoute.SEAMLESS)
    .geometry(0.508, 0.028, 0.003)
    .fabrication(0.125, 0.015, 0.030, 1.0)
    .material(450.0, 535.0, 207000.0, 0.30)
    .resistanceFactors(0.95, 1.0, 0.96, 1.15)
    .pressures(15.0, 16.5, 3.0, 0.2, 18.5, 3.0)
    .designLoads(1000.0, 1000.0, 250.0)
    .installationStrains(0.002, 0.005, 0.003, 0.025)
    .fatigueCurve(12.0, 3.0, 1.0, 3.0)
    .addFatigueBin(60.0, 100000.0)
    .addFatigueBin(80.0, 50000.0)
    .build();

EngineeringCalculationContext context = EngineeringCalculationContext.builder()
    .designCaseId("20-inch export line")
    .addStandardReference("DNV-ST-F101 2021 licensed project copy")
    .build();

EngineeringCalculationResult<DnvStF101PipelineAssessment> result =
    new DnvStF101PipelineDesignKernel().calculate(input, context);

if (result.getStatus() == EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED) {
  DnvStF101LimitStateCheck governing = result.getValue().getGoverningCheck();
  logger.info("Governing screen: {} utilization {}", governing.getLimitState(),
      governing.getUtilization());
}
```

All units are explicit in method names or builder documentation: metres, MPa, kN, kN·m, and strain
fractions. Supply zero explicitly when a reviewed load component is zero; an omitted value is a
readiness blocker.

## Readiness and result semantics

- Missing safety class, fabrication route, pressures, structural loads, fatigue data, ovality, or
  installation strain blocks execution.
- Unsupported editions and amendments block execution rather than falling back.
- A utilization above 1.0 is `FAIL`; it is not converted to a generic “unsafe” boolean.
- `areAllScreeningChecksPassing()` summarizes only implemented checks.
- `engineeringApprovalRequired` is always `true` in the result map.

## Example notebook

See `examples/notebooks/dnv_st_f101_pipeline_screening.ipynb` for a wall-thickness sweep,
safety-class comparison, ovality sensitivity, and the complete utilization table.

## Sources and verification basis

- [DNV-ST-F101 standard landing page](https://www.dnv.com/energy/standards-guidelines/dnv-st-f101-submarine-pipeline-systems/)
  identifies the 2021 edition and lifecycle scope.
- [DNV Pipeline Engineering Tool user manual](https://sesam.dnv.com/download/userdocumentation/pet-user-manual.pdf)
  publicly describes the expected separation of burst, collapse, propagation, combined loading,
  fatigue, ovality, and installation checks. It is an independent architecture reference, not the
  source of a current-edition conformity claim.

Equation choices and default screening factors must be checked against the licensed project copy
before use in engineering decisions.
