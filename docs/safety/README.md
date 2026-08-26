---
title: Safety Systems Documentation
description: Navigation for NeqSim process-safety, relief, ESD, HIPPS, risk, consequence, and barrier-analysis guides.
keywords: "safety, SIS, ESD, HIPPS, blowdown, depressurization, relief valve, PSV, API 520, API 521, fire case, source term, consequence, HAZOP, LOPA, alarm, trip"
---

Use this hub to choose the safety-analysis layer that matches the engineering question. The
[process-safety API overview](../process/safety/README.md) explains current equipment, logic,
scenario-runner, units, and lifecycle boundaries. The guides below provide focused workflows and
screening methods.

NeqSim results are engineering evidence, not approval. Project-specific hazards, design
conditions, standards editions, safeguard independence, uncertainty, acceptance criteria, and
accountable review remain mandatory.

## Start here

| Need | Guide |
| --- | --- |
| Choose equipment, logic, or scenario APIs | [Process-safety API overview](../process/safety/README.md) |
| Size or screen relief devices | [Relief-valve sizing screening](relief_valve_sizing_api.md) |
| Test ESD logic dynamically | [ESD dynamic testing workflow](esd_testing_workflow.md) |
| Model HIPPS voting and action | [HIPPS implementation](hipps_implementation.md) |
| Generate safety scenarios | [Safety scenario generation](../process/safety/scenario-generation.md) |
| Review releases and consequence inputs | [Release and dispersion scenarios](../process/safety/release-dispersion-scenarios.md) |
| Trace barriers and safety-critical elements | [Barrier management and SCE traceability](barrier_management.md) |

## Emergency shutdown and blowdown

| Document | Description |
| --- | --- |
| [ESD dynamic testing workflow](esd_testing_workflow.md) | Dynamic ESD testing with process logic, OperationalTagMap/tagreader evidence, and JSON criteria reports |
| [ESD and blowdown system](ESD_BLOWDOWN_SYSTEM.md) | Complete ESD and blowdown-system guide |
| [Pressure monitoring for ESD](PRESSURE_MONITORING_ESD.md) | Pressure monitoring and shutdown logic |
| [API 521 depressurization workflow](depressurization_per_API_521.md) | Dynamic depressurization screening and API 521 interpretation boundary |
| [Integrated safety systems](INTEGRATED_SAFETY_SYSTEMS.md) | Integrated shutdown, isolation, blowdown, and relief overview |

## HIPPS and safety-instrumented functions

| Document | Description |
| --- | --- |
| [HIPPS overview](HIPPS_SUMMARY.md) | HIPPS concepts, voting, and implementation map |
| [HIPPS implementation](hipps_implementation.md) | Detector, voting, logic, and final-action workflow |
| [HIPPS safety logic](hipps_safety_logic.md) | Safety-logic programming and state behavior |
| [SIS logic implementation](sis_logic_implementation.md) | Safety-instrumented-system logic |
| [NOG 070 SIL, STS-0131 gate, and ESD response time](nog070_sil_sts0131_esd.md) | Predetermined SIL, acceptance gate, and response-time budget |
| [Safety-chain integration tests](integration_safety_chain_tests.md) | Integrated safety-chain verification |

## Hazard identification, risk, and barriers

| Document | Description |
| --- | --- |
| [HAZOP guide](HAZOP.md) | Hazard-and-operability study structure and guidewords |
| [Automated HAZOP from STID and simulation](automated_hazop_from_stid.md) | STID/P&ID, plant data, simulation, HAZOP, barrier, and report workflow |
| [AI-HAZOP input-data format](ai_hazop_input_format.md) | Required process, deviation, design-condition, and limit-basis inputs |
| [FMEA guide](FMEA.md) | Failure-mode and effects analysis workflow |
| [Event and fault trees](event_fault_trees.md) | Event-tree and fault-tree modeling |
| [Layered safety architecture](layered_safety_architecture.md) | Defense-in-depth and protection-layer structure |
| [Barrier management and SCE traceability](barrier_management.md) | Evidence-linked PSFs, SCEs, performance standards, and analysis handoffs |
| [ISO 17776 MAH bow-tie and EI AVIFF FIV screening](mah_bowtie_fiv_screening.md) | Major-accident-hazard bow-tie and flow-induced-vibration screening |
| [Safety simulation roadmap](SAFETY_SIMULATION_ROADMAP.md) | Maintained capability and development roadmap |

## Standards and facility screening

| Document | Description |
| --- | --- |
| [API RP 14C SAFE chart and NORSOK P-002 compliance](api14c_norsok_p002.md) | SAFE chart, flare/blowdown/vent screening, and multi-vessel header load |
| [Flare flame, hazardous area, and PFP demand](flare_flame_hazardous_area_pfp.md) | API 537, IEC 60079-10-1, and API 521 screening |
| [Open-drain review with NeqSim evidence](open_drain_review.md) | NORSOK S-001 open-drain review using calculated leak and firewater loads |
| [Minimum design metal temperature assessment](mdmt_assessment.md) | MDMT evidence and low-temperature screening |

## Relief systems and trapped inventory

| Document | Description |
| --- | --- |
| [Relief-valve sizing screening](relief_valve_sizing_api.md) | Static gas, liquid, two-phase, and wetted-fire sizing APIs with explicit SI units |
| [PSV dynamic sizing example](psv_dynamic_sizing_example.md) | Pressure-safety-valve dynamic sizing workflow |
| [Rupture-disk dynamic behavior](rupture_disk_dynamic_behavior.md) | Burst, opening, flow, and reset-state behavior |
| [Trapped inventory calculator](trapped_inventory_calculator.md) | Evidence-linked isolation inventory for blowdown, flare-load, and MDMT screening |
| [Blocked-in liquid thermal expansion](blocked_in_liquid_thermal_expansion.md) | Isochoric pressure-rise and thermal-relief screening |
| [Trapped liquid fire rupture](trapped_liquid_fire_rupture.md) | Fire exposure, expansion, failure screening, PFP demand, and source-term handoff |
| [Vessel thermomechanical safety](vessel_thermomechanical_safety.md) | Blowdown, filling, boil-off, composite-wall conduction, and rupture models |

## Fire, release, and consequence

| Document | Description |
| --- | --- |
| [Fire-case blowdown capabilities](fire_blowdown_capabilities.md) | Fire-exposed blowdown simulation |
| [Fire heat-transfer enhancements](fire_heat_transfer_enhancements.md) | Fire heat flux and wall heat-transfer modeling |
| [Dispersion and consequence](dispersion_and_consequence.md) | Source-term, dispersion, radiation, and consequence-analysis handoff |
| [Release and dispersion scenarios](../process/safety/release-dispersion-scenarios.md) | Structured release scenarios with explicit inputs and limitations |
| [Vessel thermomechanical safety](vessel_thermomechanical_safety.md) | Dynamic PSV screening and thermomechanical response |
| [Trapped liquid fire rupture](trapped_liquid_fire_rupture.md) | Blocked-in liquid fire and rupture screening |

## Alarms and architecture

| Document | Description |
| --- | --- |
| [Alarm-system guide](alarm_system_guide.md) | Alarm configuration and behavior |
| [Alarm-triggered logic examples](alarm_triggered_logic_example.md) | Alarm-driven process logic |
| [Integrated safety systems](INTEGRATED_SAFETY_SYSTEMS.md) | Facility safety-system overview |
| [Layered safety architecture](layered_safety_architecture.md) | Independent protection layers and defense in depth |

## Related documentation

- [Process package overview](../process/README.md)
- [Process-safety API overview](../process/safety/README.md)
- [Controller devices](../process/controllers.md)
