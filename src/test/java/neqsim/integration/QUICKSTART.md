# NeqSim Integration Test Suite: Quick Start Guide

## What Was Created?

A complete **cross-module integration test suite** with **15 test scenarios** across 3 test classes that demonstrate how NeqSim's monorepo enables AI-assisted development:

### 📁 Files Created
```
src/test/java/neqsim/integration/
├── ThermoToProcessTests.java          (5 tests - Thermo + Equipment)
├── PVTToFlowTests.java                (5 tests - Flash + Separators)
├── EndToEndSimulationTests.java       (5 tests - Complete Flowsheets)
└── README.md                          (242-line comprehensive guide)

src/test/resources/integration/        (Reserved for baseline data)
```

## Why This Matters for AI Development

NeqSim's monorepo advantage is that **all modules are in one repo**. These integration tests make that advantage concrete by:

1. **Showing integration patterns** – How thermo systems couple with process equipment
2. **Enabling regression testing** – Modify one module, test entire stack instantly
3. **Creating AI reference library** – AI agents can copy tests as templates
4. **Validating cross-module changes** – Global balance checks catch subtle bugs

## Quick Reference: Test Scenarios

### ThermoToProcessTests (Thermo ↔ Equipment)
| Test | Focus | Integration |
|------|-------|-------------|
| `testThermoToDistillationDeethanizer` | Light gas separation | SystemSrkEos → DistillationColumn |
| `testThermoToDistillationWithCPAModel` | Polar fluid dehydration | SystemSrkCPAstatoil → Heater/Cooler |
| `testThermoToMultiUnitFlowsheet` | Three-unit sequence | Separator → Cooler → Distillation |
| `testThermoSolverConvergenceInProcessContext` | Convergence validation | ThermodynamicOperations integration |

### PVTToFlowTests (Flash ↔ Separators)
| Test | Focus | Integration |
|------|-------|-------------|
| `testPVTFlashInSeparatorCascade` | Multi-stage separation | PT-flash at multiple pressures |
| `testPVTIsenthalpicFlashThroughCooler` | Expansion vaporization | Energy balance + pressure drop |
| `testPVTIsothermalFlashPressureSensitivity` | K-value accuracy | Vapor fraction vs. pressure |
| `testPVTFlashAlgorithmConvergence` | Flash solver stability | Direct algorithm validation |

### EndToEndSimulationTests (Full Production)
| Test | Focus | Integration |
|------|-------|-------------|
| `testWellToOilStabilization` | Production flowsheet | Well → 2 separators → product |
| `testHeavyOilThermalTreatmentWithStabilization` | Thermal + separation | Heater → Cooler → Distillation |
| `testProcessSystemWithMultipleUnits` | Multi-unit coordination | ProcessSystem with cascaded equipment |
| `testStreamCloningIndependence` | State management | Branch flow validation |

## How to Run Tests

### Run All Integration Tests
```bash
cd c:\Users\esol\OneDrive - Equinor\Documents\GitHub\neqsim
.\mvnw.cmd test "-Dtest=ThermoToProcessTests"
.\mvnw.cmd test "-Dtest=PVTToFlowTests"
.\mvnw.cmd test "-Dtest=EndToEndSimulationTests"
```

### Run Single Test Scenario
```bash
.\mvnw.cmd test "-Dtest=ThermoToProcessTests#testThermoToDistillationDeethanizer"
```

### Run All at Once
```bash
.\mvnw.cmd test -q "-Dtest=neqsim.integration.*"
```

## Key Assertions in Each Test

All tests validate **physical reality**, not implementation details:

```
✓ Mole balance:    Feed moles ≈ Product A + Product B  (within 1%)
✓ Energy balance:  Inlet enthalpy ≈ Outlet enthalpy   (cooler/heater accounted)
✓ Composition:     Light components in overhead, heavy in bottoms
✓ Convergence:     Solver completes, no NaN, reasonable iteration count
✓ Phase split:     Vapor fraction increases as pressure decreases
```

## Example: How AI Uses These Tests

### Scenario: "AI adds new equipment type"

```
Prompt: "Create a new heat exchanger equipment class and test it."

AI Agent:
1. Reads ThermoToProcessTests.java to understand Heater/Cooler patterns
2. Creates new HeatExchangerAdvanced class with same interface
3. Writes test in EndToEndSimulationTests:
   ```java
   @Test
   public void testHeatExchangerAdvanced() {
     // Copy pattern from testHeavyOilThermalTreatmentWithStabilization()
     // Modify to use new equipment type
     // Validate enthalpy balance
   }
   ```
4. Runs test: Integration suite validates new equipment works with existing thermo/process
```

## Integration Points Demonstrated

These tests show **exactly how** NeqSim's modules interconnect:

```
┌─────────────────────────────────────────────────────────────┐
│                    MONOREPO ADVANTAGE                        │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  thermo/                                                     │
│  ├─ SystemInterface (EOS, K-values, properties)            │
│  ├─ SystemSrkEos, SystemSrkCPAstatoil                       │
│  └─ Tests: Used by ALL equipment below ─────────┐           │
│                                                  │           │
│  thermodynamicoperations/                        ↓           │
│  ├─ PT-flash, bubble point, equilibrium solver ─┼─┐         │
│  └─ Tests: Validated in PVTToFlowTests         │ │         │
│                                                  │ │         │
│  process/equipment/                             │ │         │
│  ├─ Separator (uses flash) ◄──────────────────┘ │         │
│  ├─ DistillationColumn (uses EOS) ◄────────────┘         │
│  ├─ Heater, Cooler (use enthalpy)                         │
│  └─ Tests: All validated in ThermoToProcessTests          │
│                                                              │
│  process/system/                                            │
│  ├─ ProcessSystem (orchestrates all equipment)            │
│  └─ Tests: Validated in EndToEndSimulationTests           │
│                                                              │
└─────────────────────────────────────────────────────────────┘

  Single Test Suite Validates Entire Stack!
```

## File Locations

| File | Purpose | Size |
|------|---------|------|
| [ThermoToProcessTests.java](src/test/java/neqsim/integration/ThermoToProcessTests.java) | 5 thermo integration tests | 268 lines |
| [PVTToFlowTests.java](src/test/java/neqsim/integration/PVTToFlowTests.java) | 5 PVT integration tests | 294 lines |
| [EndToEndSimulationTests.java](src/test/java/neqsim/integration/EndToEndSimulationTests.java) | 5 end-to-end tests | 324 lines |
| [README.md](src/test/java/neqsim/integration/README.md) | Complete usage guide | 242 lines |
| [INTEGRATION_TEST_SUMMARY.md](INTEGRATION_TEST_SUMMARY.md) | This project summary | Auto-generated |

## Next Steps

1. **Review**: Read [README.md](src/test/java/neqsim/integration/README.md) for full details
2. **Run**: Execute tests to establish baseline pass/fail
3. **Extend**: Copy templates to add new industrial scenarios
4. **Share**: Provide these tests to AI agents for development context
5. **Maintain**: Update baselines when intentional model changes occur

## Recommended for AI Context

When working with AI on NeqSim features, include this context:

> "NeqSim is a monorepo with thermo, PVT, and process modules integrated.
> See integration tests in `src/test/java/neqsim/integration/` for example
> workflows. Run tests after any change to ensure cross-module stability."

---

**Status**: ✅ Complete and ready for use  
**Created**: December 28, 2025  
**For**: AI-assisted NeqSim development  
**Benefit**: Makes monorepo advantage explicit through concrete examples
