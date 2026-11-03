---
title: Physical-inventory safeguarding design proposals
description: "Governed bridge from DYN-C029 routed-volume and isolation evidence plus operating cases to traceable instrumentation, control and safeguarding proposals."
---

# Physical-inventory safeguarding design proposals

`PhysicalInventorySafeguardingDesign` is the opt-in DYN-C032 bridge between the physical evidence exported by the
[plant layout study](../development/PLANT_LAYOUT_STUDY.md) and NeqSim's existing engineering, P&ID and DEXPI package.
It reads `plant_layout_physical_inventory.v1`, combines it with caller-supplied operating/design cases and an explicit
proposal policy, then emits deterministic pressure-measurement, control, alarm, trip, isolation-action and response-time
proposals. `PhysicalInventorySafeguardingPidBridge` materializes the result as the existing `PidDesignModel` and
`EngineeringRequirement` types; it does not create a second diagram or tag model.

The entry point is intentionally fail closed. The physical inventory must be `COMPLETE`, declare
`ready_for_dynamic_safeguarding_design=true`, retain controlled section/device provenance, include at least two boundary
devices per isolation section and supply every fail state. Missing geometry, volume, operating basis, fail state or range
candidate produces a blocker and no partial proposal.

## Quantitative contract

For each isolation section, the caller supplies a governing case with normal, maximum operating and design pressure,
inventory density, maximum inflow and an externally established process safety time. The proposal policy supplies the
range margin, the alarm/trip fractions within the available pressure window, response-budget fractions and reviewed
range candidates. No value is selected from a hidden default or standard clause.

For isolated volume $V$, declared density $\rho$ and governing inflow $\dot m$:

$$m_{inventory}=V\rho$$

$$t_{turnover}=\frac{m_{inventory}}{\dot m}$$

The turnover time is reported as physical context; it is not credited as process safety time. If $P_{max}$ is the
maximum operating pressure, $P_d$ the design pressure, and $f_A<f_T$ are the declared alarm and trip fractions:

$$P_{AH}=P_{max}+f_A(P_d-P_{max})$$

$$P_{HH}=P_{max}+f_T(P_d-P_{max})$$

The selected transmitter upper range is the smallest declared candidate covering
$P_d(1+f_{range})$. If no candidate covers it, the study blocks instead of clipping the range. Sensor, logic,
final-element and verification-reserve allocations must sum exactly to the declared process safety time. The output
records the floating-point closure residual.

## Example

```java
PhysicalInventorySafeguardingDesign.DesignPolicy policy =
    new PhysicalInventorySafeguardingDesign.DesignPolicy(
        "PID-SAFEGUARD-POLICY", "A", "Project design basis DB-001",
        0.10, 0.40, 0.75,
        0.20, 0.10, 0.50, 0.20,
        new double[] {50.0, 75.0, 100.0});

PhysicalInventorySafeguardingDesign.SectionOperatingBasis section =
    new PhysicalInventorySafeguardingDesign.SectionOperatingBasis(
        "SECTION-001", "V-101", "CASE-BLOCKED-OUTLET", "A",
        "Dynamic operating envelope DYN-001",
        40.0, 45.0, 60.0, 700.0, 70.0, 10.0);

List<PhysicalInventorySafeguardingDesign.StandardApplicability> standards =
    Arrays.asList(new PhysicalInventorySafeguardingDesign.StandardApplicability(
        "IEC 61511", "PROJECT_ADOPTED_EDITION_REQUIRED",
        "SIS lifecycle review; no SIL or voting inferred", "NOT_APPLIED",
        "Licensed project standard and risk assessment required",
        PhysicalInventorySafeguardingDesign.StandardEvidenceStatus
            .DETAILED_REQUIREMENTS_REVIEW_REQUIRED));

PhysicalInventorySafeguardingDesign.StudyInput input =
    new PhysicalInventorySafeguardingDesign.StudyInput(
        "PROJECT-001", "A", policy, Arrays.asList(section), standards);

PhysicalInventorySafeguardingDesign.StudyResult result =
    PhysicalInventorySafeguardingDesign.run(
        Paths.get("physical_inventory.json"), input);

if (!result.isReadyForDisciplineReview()) {
  throw new IllegalStateException(result.toJson());
}

PidDesignModel pid =
    PhysicalInventorySafeguardingPidBridge.materialize(result, "PROJECT-PID-PROPOSAL");
```

The result schema is `neqsim_physical_inventory_safeguarding_design.v1`. It includes a SHA-256 fingerprint of the
physical inventory and all controlled inputs. Any route/inventory revision, operating-case revision, policy change or
standards-register change invalidates that fingerprint and identifies the package for rerun.

## Traceability and proposal outputs

Every section carries a stable requirement, proposed blocked-outlet scenario, calculation result and proposed tags.
The P&ID bridge preserves these links on each instrument, controller, alarm, trip and physical boundary valve. Cause/effect
rows identify the PSHH cause and supplied boundary-device actions. Supplied fail states are retained as candidates, and
the opposite state is retained as an explicit review alternative with the trade-off rationale. The bridge never changes
the DYN-C029 physical register.

The proposed control methodology is pressure feedback control. Tuning, multivariable interaction, constraints,
disturbance rejection, actuator response and safe-state recovery remain executable dynamic-study work. Scenario status
is therefore `NOT_EXECUTED`; generating the proposal does not mark a blocked-outlet, utility-loss, sensor-fault or valve
failure case as passed.

## Standards and approval boundary

The study requires a caller-supplied applicability/edition/clause/evidence register. A row can record a project-adopted
edition, catalog metadata only, or the need for licensed detailed-requirements review. Numeric alarm/trip margins and
response allocations are project policy inputs, not inferred IEC/API/ISO/NORSOK clause rules.

Typical review scopes include ISO 10628 and ANSI/ISA-5.1 for diagrams/tags, IEC 61882 for HAZOP preparation, IEC 61511
for the SIS lifecycle, IEC 62682 for alarm management, API 520/521 for applicable relief/depressuring, ISA-5.06.01 and
ISA-TR5.9 for control documentation/tuning, and IEC 62381 for acceptance-test planning. Offshore-only ISO 10418,
ISO 13702 and NORSOK profiles must not be applied to an onshore project without an explicit applicability decision.

The output is `REVIEW_REQUIRED`, has `safetyCredit=false`, and is not fit for construction. Accountable hazard
completeness, scenario credibility, IPL/SIL/SRS decisions, voting and independence, detector coverage, vendor/site data,
relief/flare disposal adequacy, construction and operating approval remain external evidence and review.

## Qualification scope

The synthetic analytical regression uses one section with a 17 m, 2 m internal-diameter equivalent route and a
10 m3 equipment volume. It independently checks cylindrical pipe volume, isolated mass, turnover time, range selection,
alarm/trip interpolation, trip-to-design margin, response-budget closure, deterministic replay, revision invalidation,
P&ID trace connections and fail-closed behavior for incomplete inventory, unknown fail state and uncovered range.

That regression qualifies the implemented arithmetic and trace contract only. It does not qualify the operating/design
case, establish a process safety time, execute the proposed dynamic scenario, size a valve, verify relief/flare capacity,
or approve a control or safety design.
