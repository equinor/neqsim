---
title: Pull Request: Hardy Cross Looped Network Solver & O&G Review Update
description: This PR addresses a comprehensive review request for NeqSim's Oil & Gas design and operations capabilities. The original requirements were:
---

# Pull Request: Hardy Cross Looped Network Solver & O&G Review Update

**Date:** January 31, 2026  
**Author:** NeqSim Development Team  
**Type:** Feature Addition & Documentation Enhancement

---

## Background & Original Requirements

This PR addresses a comprehensive review request for NeqSim's Oil & Gas design and operations capabilities. The original requirements were:

### 1. Improve Documentation of Existing Features

The O&G Review document incorrectly marked several existing features as "missing" or "limited":

| Original Claim | Actual Status | Resolution |
|----------------|---------------|------------|
| ❌ Limited reservoir coupling | ✅ `ReservoirCouplingExporter` exists with VFP table export | Documented in Section 2.4 |
| ❌ No decline curve analysis | ✅ Can be implemented via `WellFlow` IPR models | Documented with examples |
| ❌ Limited well performance modeling | ✅ `WellFlow.java` has 5 IPR models (Vogel, Fetkovich, etc.) | Full documentation added |

### 2. Add Erosion-Corrosion Documentation

**Request:** Document API RP 14E erosional velocity for pipeline/choke integrity

**Resolution:** Found `ManifoldMechanicalDesignCalculator.java` already implements:
- Erosional velocity: $V_e = C / \sqrt{\rho}$
- Configurable C-factor (100-150)
- Gas, liquid, and multiphase velocity limits

### 3. Review Rotating Equipment Functionality

Original claims to investigate:

| Original Claim | Actual Status | Resolution |
|----------------|---------------|------------|
| ❌ Detailed bearing/seal gas modeling limited | ✅ `CompressorMechanicalLosses` exists (API 692/617) | Full documentation added |
| ❌ No comprehensive vibration analysis | ✅ `FlowInducedVibrationAnalyser` exists (LOF/FRMS) | Full documentation added |
| ❌ Limited fouling/degradation tracking | ✅ `ConditionBasedReliability` + `DegradationModel` exist | Documented with examples |
| ❌ No compressor washing effects | ✅ **NEW** `CompressorWashing` implemented | Online/offline wash, fouling model |
| ❌ Limited driver modeling | ✅ Full driver framework exists (Gas Turbine, Electric Motor, Steam Turbine) | Comprehensive documentation added |

### 4. Review Utilities & Safety Systems

Original claims to investigate:

| Original Claim | Actual Status | Resolution |
|----------------|---------------|------------|
| ❌ Fuel gas system modeling limited | ✅ **NEW** `FuelGasSystem` implemented | Full conditioning system |
| ❌ Flare system hydraulics basic | ✅ `FlareStack` has radiation models (Point Source, Chamberlain) | Full documentation added |
| ❌ Produced water treatment limited | ✅ `ProducedWaterTreatmentTrain` exists | Full documentation added |
| ❌ Chemical injection tracking | ⚠️ Partially exists via streams | Documented what works |
| ❌ HVAC/utility air not modeled | ✅ **NEW** `UtilityAirSystem` implemented | ISO 8573-1 air quality |
| ❌ Firewater system hydraulics | ⚠️ Can use pipe network solver | Documented approach |
| ❌ Power generation integration limited | ⚠️ Driver framework helps | Documented capabilities |

### 5. Check Documentation Links & Formatting

**Request:** Detect failing links, fix layout and formatting issues

**Resolution:**
- Reorganized Appendix B with verified links
- Added new Appendix C for new functionality
- Fixed table formatting throughout
- Added consistent section numbering

### 6. Structure New Functionality Documentation

**Request:** Add documentation for new features implemented

**Resolution:**
- Created comprehensive Appendix C: New Functionality Reference
- Documented Hardy Cross looped network solver
- Created example Jupyter notebook
- Updated REFERENCE_MANUAL_INDEX.md

---

## Summary

This PR introduces a new Hardy Cross looped pipeline network solver for handling ring mains, parallel pipelines, and complex gathering systems. It also includes a comprehensive update to the Oil & Gas Design Operations Review document with improved documentation of existing functionality.

---

## New Features

### 1. Hardy Cross Looped Pipeline Network Solver

A complete implementation of the classic Hardy Cross iterative method for solving looped pipe networks.

#### New Classes

| File | Description |
|------|-------------|
| [NetworkLoop.java](../src/main/java/neqsim/process/equipment/network/NetworkLoop.java) | Represents an independent loop with pipe members and flow directions |
| [LoopDetector.java](../src/main/java/neqsim/process/equipment/network/LoopDetector.java) | DFS spanning tree algorithm for detecting independent loops |
| [LoopedPipeNetwork.java](../src/main/java/neqsim/process/equipment/network/LoopedPipeNetwork.java) | Main network class with Hardy Cross solver |
| [LoopedPipeNetworkTest.java](../src/test/java/neqsim/process/equipment/network/LoopedPipeNetworkTest.java) | Unit tests for loop detection and solver |

### 2. Compressor Washing System (NEW)

Models compressor fouling and washing (online/offline) for performance recovery.

| File | Description |
|------|-------------|
| [CompressorWashing.java](../src/main/java/neqsim/process/equipment/compressor/CompressorWashing.java) | Fouling model with online/offline washing |
| [CompressorWashingTest.java](../src/test/java/neqsim/process/equipment/compressor/CompressorWashingTest.java) | Unit tests (18 tests) |

**Key Features:**
- **Fouling Types**: Salt, hydrocarbon, particulate, corrosion, biological
- **Washing Methods**: Online wet, offline soak, crank wash, chemical clean, dry ice blast
- **Performance Impact**: Head loss and efficiency degradation models
- **Wash Scheduling**: Interval estimation based on target performance

### 3. Utility Air System (NEW)

Models instrument air/plant air systems per ISO 8573-1.

| File | Description |
|------|-------------|
| [UtilityAirSystem.java](../src/main/java/neqsim/process/equipment/util/UtilityAirSystem.java) | Complete utility air system model |
| [UtilityAirSystemTest.java](../src/test/java/neqsim/process/equipment/util/UtilityAirSystemTest.java) | Unit tests (17 tests) |

**Key Features:**
- **Air Quality Classes**: ISO 8573-1 Classes 1-5 (breathing air to service air)
- **Compressor Types**: Rotary screw, reciprocating, centrifugal, scroll
- **Dryer Types**: Refrigerated, desiccant (heated/heatless), membrane, hybrid
- **Calculations**: Power consumption, dew point, condensate, receiver sizing

### 4. Fuel Gas System (NEW)

Models fuel gas conditioning for gas turbines, heaters, and other consumers.

| File | Description |
|------|-------------|
| [FuelGasSystem.java](../src/main/java/neqsim/process/equipment/util/FuelGasSystem.java) | Fuel gas conditioning system |
| [FuelGasSystemTest.java](../src/test/java/neqsim/process/equipment/util/FuelGasSystemTest.java) | Unit tests (18 tests) |

**Key Features:**
- **Consumer Types**: Gas turbine, fired heater, flare pilot, hot oil heater
- **Conditioning**: JT cooling calculation, heater duty, superheat control
- **Gas Quality**: Wobbe index, LHV, dew point calculation
- **Specifications**: Per API 618, NORSOK P-002, ISO 21789

---

### 5. Example Notebook

| File | Description |
|------|-------------|
| [LoopedPipelineNetworkExample.ipynb](examples/LoopedPipelineNetworkExample.ipynb) | Interactive Jupyter notebook with examples |

**Notebook Contents:**
- Triangle loop detection
- Distribution ring main
- Offshore subsea ring network
- Figure-8 network (two loops)
- NetworkLoop API demonstration
- JSON output for integration

---

## Documentation Updates

### 3. Oil & Gas Design Operations Review

Major update to [NEQSIM_OIL_GAS_DESIGN_OPERATIONS_REVIEW.md](NEQSIM_OIL_GAS_DESIGN_OPERATIONS_REVIEW):

#### Updated Sections

| Section | Changes |
|---------|---------|
| **2.1 Flow Assurance** | Added erosion-corrosion modeling with API RP 14E |
| **2.4 Field Development** | Documented WellFlow IPR models and ReservoirCouplingExporter |
| **3.2 Rotating Equipment** | Upgraded to "Good" with driver framework documentation |
| **3.3 Utilities** | Upgraded to "Moderate Coverage" with flare stack details |
| **4.1 High Priority** | Updated status of implemented features |
| **5.1 Onshore Apps** | Updated pipeline network rating |
| **7. Roadmap** | Added completion status indicators |
| **Appendix B** | Reorganized and expanded documentation links |
| **Appendix C** | NEW - New Functionality Reference |

#### New Documentation of Existing Features

**Well Performance (IPR) - WellFlow.java:**
- Production Index model
- Vogel correlation for oil wells
- Fetkovich correlation for gas wells
- Backpressure equation with non-Darcy term
- Table-driven IPR from well tests
- Multi-layer commingled wells

**Reservoir Coupling - ReservoirCouplingExporter.java:**
- VFP table generation (VFPPROD/VFPINJ)
- Eclipse 100/E300/Intersect formats
- Separator efficiency curves
- Compression curves
- Network deliverability constraints

**Erosion-Corrosion - ManifoldMechanicalDesignCalculator.java:**
- API RP 14E erosional velocity: Ve = C/√ρ
- Configurable C-factor (100-150)
- Gas, liquid, multiphase velocity limits

**Compressor Drivers:**
- GasTurbineDriver: Ambient derating, part-load efficiency, fuel consumption
- ElectricMotorDriver: VFD speed control, efficiency curves
- SteamTurbineDriver: Extraction/condensing modes

**Condition Monitoring:**
- FlowInducedVibrationAnalyser: LOF/FRMS methods
- ConditionBasedReliability: Health index, RUL estimation
- DegradationModel: Linear, Exponential, Weibull, ML-based

**Flare System - FlareStack.java:**
- Radiation models: Point Source, Chamberlain line-source
- Emissions tracking: CO₂, H₂O, NOx, SO₂, THC, CO
- Tip backpressure calculation
- Air/steam assist modeling

---

### 4. Reference Manual Index Update

Updated [REFERENCE_MANUAL_INDEX.md](REFERENCE_MANUAL_INDEX):

| Addition | Description |
|----------|-------------|
| O&G Design Review | Added to Chapter 1: Introduction |
| Looped Network Solver | Added link to enhancement proposal |
| Looped Network Tutorial | Added link to example notebook |

---

### 5. Pipeline Network Enhancement Proposal

Updated [PIPELINE_NETWORK_SOLVER_ENHANCEMENT.md](process/PIPELINE_NETWORK_SOLVER_ENHANCEMENT):

- Added "Implementation Status: ✅ COMPLETE" header
- Listed implemented classes with links
- Added key features summary

---

## Test Results

All **65** unit tests pass:

**Looped Network Solver:**
```
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
```

**Compressor Washing:**
```
Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
```

**Utility Air System:**
```
Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
```

**Fuel Gas System:**
```
Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
```

**Test Coverage:**
- `testTriangleLoopDetection` - Basic loop detection
- `testTwoLoopDetection` - Multiple independent loops
- `testTreeNetworkNoLoops` - Tree topology (no loops)
- `testRingMainNetworkCreation` - Ring main topology
- `testHardyCrossConvergence` - Solver convergence
- `testNetworkLoopClass` - Loop member management
- `testSolutionSummary` - Output format
- `testJsonOutput` - JSON serialization
- `testParallelPipes` - Parallel pipe loop detection
- `testSolverTypeSelection` - Solver configuration
- `testRelaxationFactor` - Parameter setting
- `testOffshoreRingNetwork` - Realistic offshore topology

---

## Files Changed

### New Files (11)

```
src/main/java/neqsim/process/equipment/network/
├── NetworkLoop.java
├── LoopDetector.java
└── LoopedPipeNetwork.java

src/main/java/neqsim/process/equipment/compressor/
└── CompressorWashing.java

src/main/java/neqsim/process/equipment/util/
├── UtilityAirSystem.java
└── FuelGasSystem.java

src/test/java/neqsim/process/equipment/network/
└── LoopedPipeNetworkTest.java

src/test/java/neqsim/process/equipment/compressor/
└── CompressorWashingTest.java

src/test/java/neqsim/process/equipment/util/
├── UtilityAirSystemTest.java
└── FuelGasSystemTest.java

docs/examples/
└── LoopedPipelineNetworkExample.ipynb
```

### Modified Files (3)

```
docs/
├── NEQSIM_OIL_GAS_DESIGN_OPERATIONS_REVIEW.md
├── REFERENCE_MANUAL_INDEX.md
└── process/PIPELINE_NETWORK_SOLVER_ENHANCEMENT.md
```

---

## Breaking Changes

None. This PR adds new functionality without modifying existing APIs.

---

## Migration Guide

No migration required. Existing code continues to work unchanged.

---

## Future Work

1. **Newton-Raphson Solver**: Implement simultaneous solution for large networks
2. **Transient Looped Networks**: Extend TwoFluidPipe for looped transient simulation
3. **OPC-UA Connector**: Real-time data integration
4. **MEG/TEG Tracking**: System-wide inhibitor tracking
5. **Chemical Injection System**: Detailed injection point tracking
6. **Firewater System**: Dedicated firewater network class

---

## Related Issues

- Enhancement request for looped network solver
- Documentation review for O&G design capabilities

---

## Reviewers

Please review:
1. Loop detection algorithm correctness
2. Hardy Cross convergence behavior
3. Documentation accuracy for existing features
4. Example notebook clarity

---

## Checklist

- [x] Code compiles without errors
- [x] All unit tests pass
- [x] JavaDoc added to new classes
- [x] Example notebook created
- [x] Documentation updated
- [x] Reference manual index updated
- [x] No breaking changes to existing APIs
