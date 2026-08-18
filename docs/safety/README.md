---
title: Safety Systems Documentation
description: Documentation for safety-related features and systems in NeqSim.
---

Documentation for safety-related features and systems in NeqSim.

---

## Overview

This folder contains guides for implementing safety systems in process simulations, including Emergency Shutdown (ESD), High Integrity Pressure Protection Systems (HIPPS), blowdown analysis, and alarm management.

---

## Documentation Index

### Emergency Shutdown (ESD)

| Document | Description |
|----------|-------------|
| [ESD Dynamic Testing Workflow](esd_testing_workflow.md) | Dynamic ESD testing with process logic, OperationalTagMap/tagreader evidence, and JSON criteria reports |
| [ESD_BLOWDOWN_SYSTEM.md](ESD_BLOWDOWN_SYSTEM.md) | Complete ESD and blowdown system guide |
| [PRESSURE_MONITORING_ESD.md](PRESSURE_MONITORING_ESD.md) | Pressure monitoring for ESD |

### HIPPS (High Integrity Pressure Protection)

| Document | Description |
|----------|-------------|
| [HIPPS_SUMMARY.md](HIPPS_SUMMARY.md) | HIPPS overview and summary |
| [hipps_implementation.md](hipps_implementation.md) | HIPPS implementation details |
| [hipps_safety_logic.md](hipps_safety_logic.md) | HIPPS safety logic programming |

### Safety Architecture

| Document | Description |
|----------|-------------|
| [INTEGRATED_SAFETY_SYSTEMS.md](INTEGRATED_SAFETY_SYSTEMS.md) | Integrated safety systems overview |
| [layered_safety_architecture.md](layered_safety_architecture.md) | Layered safety architecture (defense in depth) |
| [sis_logic_implementation.md](sis_logic_implementation.md) | Safety Instrumented Systems (SIS) logic |
| [integration_safety_chain_tests.md](integration_safety_chain_tests.md) | Safety chain integration testing |
| [SAFETY_SIMULATION_ROADMAP.md](SAFETY_SIMULATION_ROADMAP.md) | Safety simulation development roadmap |

### Standards Compliance and Hazard Screening

| Document | Description |
|----------|-------------|
| [NOG 070 SIL, STS-0131 Gate and ESD Response Time](nog070_sil_sts0131_esd.md) | Pre-determined SIL (NOG 070), aggregated STS-0131 acceptance gate, and IEC 61511 ESD response-time budget |
| [API 14C SAFE Chart and NORSOK P-002 Compliance](api14c_norsok_p002.md) | API RP 14C / ISO 10418 SAFE chart, NORSOK P-002 flare/blowdown/vent screening, and coupled multi-vessel blowdown header load |
| [ISO 17776 MAH Bow-Tie and EI AVIFF FIV Screening](mah_bowtie_fiv_screening.md) | Major-accident-hazard bow-tie from the ISO 17776 catalogue and Energy Institute AVIFF flow-induced-vibration screening |
| [Flare Flame, Hazardous Area and PFP Demand](flare_flame_hazardous_area_pfp.md) | API 537 flare flame/radiation/noise, IEC 60079-10-1 hazardous-area zoning, and API 521 passive-fire-protection demand |
| [Automated HAZOP from STID and Simulation](automated_hazop_from_stid.md) | End-to-end STID/P&ID, plant data, NeqSim simulation, HAZOP, barrier, and report workflow |
| [AI-HAZOP Input Data Format](ai_hazop_input_format.md) | Required input-data format for a P&ID Safety Analyser / AI-HAZOP front-end: process model JSON, per-deviation `runHazopScenario` request, DEXPI design-conditions, limit-basis policy, and blocked-outlet overpressure screening |
| [Open Drain Review with NeqSim Evidence](open_drain_review.md) | NORSOK S-001 Clause 9 review using NeqSim-calculated liquid leak rate, firewater load, density, pressure, and drain capacity plus STID/tagreader evidence |
| [Barrier Management and SCE Traceability](barrier_management.md) | Evidence-linked PSFs, SCEs, performance standards, and safety-analysis handoffs |

### Fire and Thermal Protection

| Document | Description |
|----------|-------------|
| [fire_blowdown_capabilities.md](fire_blowdown_capabilities.md) | Fire case blowdown simulation |
| [fire_heat_transfer_enhancements.md](fire_heat_transfer_enhancements.md) | Fire heat transfer modeling |
| [Vessel Thermomechanical Safety](vessel_thermomechanical_safety.md) | Transient non-equilibrium blowdown, fast filling, cryogenic boil-off, composite-wall conduction, and fire/blowdown wall rupture |
| [Trapped Liquid Fire Rupture](trapped_liquid_fire_rupture.md) | Blocked-in liquid segment fire rupture screening with material, flange, PFP, and source-term handoff |
| [Blocked-In Liquid Thermal Expansion](blocked_in_liquid_thermal_expansion.md) | Isochoric pressure-rise screening (API 521 §4.4.12) for blocked-in liquid segments, independent of fire exposure |

### Relief Systems

| Document | Description |
|----------|-------------|
| [Relief-Valve Sizing Screening](relief_valve_sizing_api.md) | Static gas, liquid, two-phase, and wetted-surface fire screening APIs with explicit SI units and qualification limits |
| [Trapped Inventory Calculator](trapped_inventory_calculator.md) | Evidence-linked trapped inventory for isolation, blowdown, flare-load, and MDMT screening |
| [Trapped Liquid Fire Rupture](trapped_liquid_fire_rupture.md) | Fire exposure, thermal expansion, pipe/flange failure screening, PFP demand, and source-term handoff |
| [Blocked-In Liquid Thermal Expansion](blocked_in_liquid_thermal_expansion.md) | Isochoric pressure-rise screening and simplified beta/kappa relation for blocked-in liquid thermal relief screening |
| [psv_dynamic_sizing_example.md](psv_dynamic_sizing_example.md) | Pressure Safety Valve dynamic sizing |
| [Vessel Thermomechanical Safety](vessel_thermomechanical_safety.md) | Dynamic PSV sizing vs steady-state API 521 conservatism, plus blowdown, filling, boil-off, and rupture models |
| [rupture_disk_dynamic_behavior.md](rupture_disk_dynamic_behavior.md) | Rupture disk dynamic behavior |

### Alarms

| Document | Description |
|----------|-------------|
| [alarm_system_guide.md](alarm_system_guide.md) | Alarm system implementation guide |
| [alarm_triggered_logic_example.md](alarm_triggered_logic_example.md) | Alarm-triggered logic examples |

---

## Related Documentation

- [Process Package](../process/) - Process simulation overview
- [Process Safety](../process/safety/) - Safety equipment classes
- [Controllers](../process/controllers.md) - Controller devices

