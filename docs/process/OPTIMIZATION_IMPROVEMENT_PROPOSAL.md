---
title: Production Optimization Improvement Proposal
description: "This document outlines improvements to NeqSim's production optimization framework."
---

# Production Optimization Improvement Proposal

## Executive Summary

This document outlines improvements to NeqSim's production optimization framework to support:
1. **Streamlined equipment constraint configuration**
2. **Automated process design from specifications**
3. **Integrated design-to-optimization workflow**

**Status: Phase 1 Implementation Complete ✅ | Phase 3 Multi-Objective Optimization Complete ✅**

---

## Implementation Status

### Completed ✅

| Component | Package | Status |
|-----------|---------|--------|
| `AutoSizeable` interface | `neqsim.process.design` | ✅ Complete |
| `DesignSpecification` builder | `neqsim.process.design` | ✅ Complete |
| `ProcessTemplate` interface | `neqsim.process.design` | ✅ Complete |
| `ProcessBasis` class | `neqsim.process.design` | ✅ Complete |
| `EquipmentConstraintRegistry` | `neqsim.process.design` | ✅ Complete |
| `DesignOptimizer` workflow | `neqsim.process.design` | ✅ Complete |
| `DesignResult` container | `neqsim.process.design` | ✅ Complete |
| `ThreeStageSeparationTemplate` | `neqsim.process.design.template` | ✅ Complete |
| Separator AutoSizeable | `neqsim.process.equipment.separator` | ✅ Complete |
| ThreePhaseSeparator AutoSizeable | `neqsim.process.equipment.separator` | ✅ Complete |
| GasScrubber AutoSizeable | `neqsim.process.equipment.separator` | ✅ Complete |
| ThrottlingValve AutoSizeable | `neqsim.process.equipment.valve` | ✅ Complete |
| PipeBeggsAndBrills AutoSizeable | `neqsim.process.equipment.pipeline` | ✅ Complete |
| Heater AutoSizeable | `neqsim.process.equipment.heatexchanger` | ✅ Complete |
| Cooler AutoSizeable | `neqsim.process.equipment.heatexchanger` | ✅ Complete |
| HeatExchanger AutoSizeable | `neqsim.process.equipment.heatexchanger` | ✅ Complete |
| Manifold AutoSizeable | `neqsim.process.equipment.manifold` | ✅ Complete |

### Documentation

- [DESIGN_FRAMEWORK.md](DESIGN_FRAMEWORK) - Complete documentation with examples
- Integration with MechanicalDesign and TechnicalRequirements database

---

## Current State Assessment

### Strengths ✅

| Feature | Status | Notes |
|---------|--------|-------|
| Multi-constraint framework | ✅ Complete | `CapacityConstrainedEquipment` interface |
| Compressor constraints | ✅ Complete | Surge, speed, power, stonewall |
| Separator K-factor | ✅ Complete | Souders-Brown coefficient |
| Heater/Cooler duty | ✅ Complete | `setMaxDesignDuty()` |
| Valve Cv-based | ✅ Complete | Opening % as capacity metric |
| Pipeline velocity | ✅ Complete | Via mechanical design |
| FIV analysis | ✅ Complete | LOF and FRMS methods |
| Mechanical design data | ✅ Complete | CSV files with TR support |
| **AutoSizeable interface** | ✅ **NEW** | Auto-size from flow conditions |
| **DesignSpecification builder** | ✅ **NEW** | Standardized equipment config |
| **ProcessTemplate system** | ✅ **NEW** | Reusable process configurations |
| **EquipmentConstraintRegistry** | ✅ **NEW** | Default constraint templates |
| **DesignOptimizer workflow** | ✅ **NEW** | Integrated design-to-optimize |

### Remaining Gaps 🔧

| Gap | Impact | Priority | Status |
|-----|--------|----------|--------|
| ~~Inconsistent capacity interface~~ | ~~Medium~~ | ~~High~~ | ✅ Solved |
| ~~No auto-sizing integration~~ | ~~High~~ | ~~High~~ | ✅ Solved |
| ~~Manual equipment configuration~~ | ~~Medium~~ | ~~Medium~~ | ✅ Solved |
| ~~No process templates~~ | ~~High~~ | ~~High~~ | ✅ Solved |
| ~~No multi-objective optimization~~ | ~~Medium~~ | ~~Low~~ | ✅ Solved |
| ~~Limited pump support~~ | ~~Low~~ | ~~Medium~~ | ✅ Solved |
| More process templates needed | Medium | Medium | Pending |

---

## Implemented Improvements

### 1. Unified Design Specification Interface

**Problem**: Equipment configuration is scattered - some use `initMechanicalDesign()`, some have direct setters, some need custom constraints.

**Solution**: Create a `DesignSpecification` class that standardizes configuration:

```java
public class DesignSpecification {
    private String equipmentName;
    private Map<String, Double> designParameters;  // K-factor, Cv, maxVelocity, etc.
    private Map<String, Double> operatingLimits;   // max duty, max opening, etc.
    private String materialGrade;
    private String designStandard;
    private String trDocument;  // Company TR reference
    
    // Builder pattern
    public static DesignSpecification forSeparator(String name) { ... }
    public static DesignSpecification forCompressor(String name) { ... }
    public static DesignSpecification forPipeline(String name) { ... }
    public static DesignSpecification forValve(String name) { ... }
    
    // Apply to equipment
    public void applyTo(ProcessEquipmentInterface equipment);
}
```

**Usage:**
```java
DesignSpecification.forSeparator("20-VA-01")
    .setKFactor(0.08)
    .setDiameter(3.0, "m")
    .setLength(8.0, "m")
    .setMaterial("316L")
    .setStandard("ASME-VIII")
    .applyTo(separator);
```

### 2. Auto-Sizing from Design Basis

**Problem**: Currently, equipment dimensions must be manually specified. In practice, design engineers size equipment based on flow rates and design criteria.

**Solution**: Add `autoSize()` methods that calculate dimensions from flow requirements:

```java
public interface AutoSizeable {
    /**
     * Auto-size equipment based on connected stream and design criteria.
     * @param safetyFactor typically 1.1-1.3
     */
    void autoSize(double safetyFactor);
    
    /**
     * Auto-size using company-specific design standards.
     */
    void autoSize(String companyStandard, String trDocument);
    
    /**
     * Get sizing report after auto-sizing.
     */
    String getSizingReport();
}

// Implementation for Separator
public class Separator implements AutoSizeable {
    @Override
    public void autoSize(double safetyFactor) {
        // Calculate required K-factor from inlet gas properties
        double gasRate = inletStream.getFlowRate("Sm3/day");
        double gasDensity = inletStream.getGasDensity();
        double liquidDensity = inletStream.getLiquidDensity();
        
        // Apply Souders-Brown correlation
        double vMax = designKFactor * Math.sqrt((liquidDensity - gasDensity) / gasDensity);
        double requiredArea = gasRate / (vMax * 3600);
        double diameter = Math.sqrt(4 * requiredArea / Math.PI) * safetyFactor;
        
        setInternalDiameter(diameter);
        // ... calculate length from L/D ratio ...
    }
}
```

### 3. Process Template System

**Problem**: No way to define standard process configurations that can be instantiated with different fluids/conditions.

**Solution**: Create `ProcessTemplate` classes for common configurations:

```java
public interface ProcessTemplate {
    ProcessSystem instantiate(SystemInterface fluid, ProcessBasis basis);
    List<String> getRequiredParameters();
    void validate(ProcessBasis basis);
}

public class ProcessBasis {
    private double designFlowRate;
    private String flowRateUnit;
    private double designPressure;
    private double designTemperature;
    private double turndown;  // e.g., 0.3 for 30% turndown
    private double maxCapacity;  // e.g., 1.2 for 120% design
    private String companyStandard;
}

// Example templates
public class ThreeStageSeparationTemplate implements ProcessTemplate {
    @Override
    public ProcessSystem instantiate(SystemInterface fluid, ProcessBasis basis) {
        ProcessSystem process = new ProcessSystem();
        
        // Create feed stream
        Stream feed = new Stream("feed", fluid.clone());
        feed.setFlowRate(basis.getDesignFlowRate(), basis.getFlowRateUnit());
        process.add(feed);
        
        // HP Separator - auto-sized
        ThreePhaseSeparator hpSep = new ThreePhaseSeparator("HP-Separator", feed);
        hpSep.autoSize(1.2);  // 20% safety factor
        process.add(hpSep);
        
        // Pressure letdown valve - auto-sized Cv
        ThrottlingValve valve1 = new ThrottlingValve("HP-MP-Valve", hpSep.getOilOutStream());
        valve1.setOutletPressure(10.0, "bara");
        valve1.autoSizeCv(0.7);  // Size for 70% opening at design
        process.add(valve1);
        
        // ... continue with MP and LP separators ...
        
        return process;
    }
}
```

### 4. Integrated Design-Optimize Workflow

**Solution**: Create `DesignOptimizer` that combines sizing and optimization:

```java
public class DesignOptimizer {
    
    /**
     * Design and optimize a process from specifications.
     */
    public DesignResult designAndOptimize(
            ProcessTemplate template,
            SystemInterface fluid,
            ProcessBasis basis,
            OptimizationObjective objective) {
        
        // Step 1: Instantiate process from template
        ProcessSystem process = template.instantiate(fluid, basis);
        
        // Step 2: Auto-size all equipment
        autoSizeAll(process, basis);
        
        // Step 3: Apply design standards
        applyDesignStandards(process, basis.getCompanyStandard());
        
        // Step 4: Run baseline simulation
        process.run();
        
        // Step 5: Create constraints from design
        List<OptimizationConstraint> constraints = 
            extractConstraintsFromDesign(process);
        
        // Step 6: Optimize
        ProductionOptimizer optimizer = new ProductionOptimizer();
        OptimizationResult result = optimizer.optimize(
            process, getFeedStream(process), 
            createConfig(basis), 
            Collections.singletonList(objective),
            constraints);
        
        return new DesignResult(process, result, generateReport(process));
    }
    
    /**
     * Auto-size all equipment that implements AutoSizeable.
     */
    private void autoSizeAll(ProcessSystem process, ProcessBasis basis) {
        for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
            if (unit instanceof AutoSizeable) {
                ((AutoSizeable) unit).autoSize(basis.getSafetyFactor());
            }
        }
    }
    
    /**
     * Extract optimization constraints from design values.
     */
    private List<OptimizationConstraint> extractConstraintsFromDesign(
            ProcessSystem process) {
        List<OptimizationConstraint> constraints = new ArrayList<>();
        
        for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
            // Use CapacityConstrainedEquipment if available
            if (unit instanceof CapacityConstrainedEquipment) {
                CapacityConstrainedEquipment cce = (CapacityConstrainedEquipment) unit;
                for (CapacityConstraint cc : cce.getCapacityConstraints().values()) {
                    constraints.add(createConstraint(unit.getName(), cc));
                }
            }
            // Add FIV constraint for pipelines
            if (unit instanceof PipeBeggsAndBrills) {
                constraints.add(createFIVConstraint((PipeBeggsAndBrills) unit));
            }
        }
        return constraints;
    }
}
```

### 5. Equipment Registry with Default Constraints

**Problem**: Each equipment type needs custom handling in `determineCapacityRule()`.

**Solution**: Create an equipment registry with default constraint configurations:

```java
public class EquipmentConstraintRegistry {
    private static final Map<Class<?>, ConstraintConfig> DEFAULTS = new HashMap<>();
    
    static {
        // Separator defaults
        DEFAULTS.put(Separator.class, new ConstraintConfig()
            .addConstraint("gasLoadFactor", 
                sep -> ((Separator) sep).getGasLoadFactor(),
                sep -> ((Separator) sep).getDesignGasLoadFactor(),
                0.90)  // 90% utilization limit
            .addConstraint("liquidResidenceTime",
                sep -> ((Separator) sep).getLiquidResidenceTime(),
                sep -> ((Separator) sep).getDesignLiquidResidenceTime(),
                0.80));  // 80% utilization limit
        
        // Compressor defaults - use CapacityConstrainedEquipment
        DEFAULTS.put(Compressor.class, ConstraintConfig.fromCapacityConstraints(0.95));
        
        // Pipeline defaults
        DEFAULTS.put(PipeBeggsAndBrills.class, new ConstraintConfig()
            .addConstraint("velocity",
                pipe -> ((PipeBeggsAndBrills) pipe).getOutletSuperficialVelocity(),
                pipe -> ((PipeBeggsAndBrills) pipe).getMechanicalDesign().getMaxDesignVelocity(),
                0.85)  // 85% of erosional velocity
            .addConstraint("fivLOF",
                pipe -> calculateLOF((PipeBeggsAndBrills) pipe),
                pipe -> 1.0,  // LOF limit
                0.70));  // 70% of LOF limit
        
        // Valve defaults
        DEFAULTS.put(ThrottlingValve.class, new ConstraintConfig()
            .addConstraint("valveOpening",
                v -> ((ThrottlingValve) v).getPercentValveOpening(),
                v -> ((ThrottlingValve) v).getMaximumValveOpening(),
                0.90));  // 90% max opening
    }
    
    public static ConstraintConfig getDefaults(Class<?> equipmentClass) {
        return DEFAULTS.get(equipmentClass);
    }
}
```

### 6. Extend CapacityConstrainedEquipment to More Equipment

**Current**: Only Compressor and Separator implement `CapacityConstrainedEquipment`.

**Recommendation**: Implement for:
- `ThrottlingValve` - valve opening, Cv utilization
- `PipeBeggsAndBrills` - velocity, FIV LOF
- `Heater`/`Cooler` - duty utilization
- `Pump` - NPSH margin, power
- `HeatExchanger` - approach temperature, UA

---

## Implementation Roadmap

### Phase 1: Standardization ✅ COMPLETE
1. ✅ Implemented `DesignSpecification` class with builder pattern
2. ✅ Created `EquipmentConstraintRegistry` singleton with default templates
3. ✅ Implemented `AutoSizeable` interface
4. ✅ Added `autoSize()` to Separator, ThrottlingValve, PipeBeggsAndBrills
5. ✅ Integrated Separator autoSize with MechanicalDesign and company standards (TechnicalRequirements_Process.csv)
6. ✅ Created `ProcessTemplate` interface
7. ✅ Implemented `ThreeStageSeparationTemplate`
8. ✅ Created `ProcessBasis` with fluent builder
9. ✅ Created `DesignResult` container
10. ✅ Created `DesignOptimizer` workflow manager
11. ✅ Full test coverage in `DesignFrameworkTest` (13 tests passing)

**Location**: `neqsim.process.design` package  
**Documentation**: [DESIGN_FRAMEWORK.md](DESIGN_FRAMEWORK)

### Phase 2: Extended Equipment Support ✅ COMPLETE
1. ✅ Add `CapacityConstrainedEquipment` to Heater/Cooler
2. ✅ Add `AutoSizeable` to Heater, Cooler, HeatExchanger, Manifold
3. ✅ Extended design standards database with pump and manifold standards
4. 🔧 Implement more process templates (gas compression, dehydration) - pending

### Phase 3: Multi-Objective Optimization ✅ Completed
Implementation provides Pareto optimization for competing objectives like throughput vs energy consumption.

**Components Implemented:**
- `ObjectiveFunction` interface with `Direction` enum (MINIMIZE/MAXIMIZE)
- `StandardObjective` enum with common objectives (throughput, power, heating/cooling duty, total energy)
- `ParetoSolution` with dominance checking and distance calculations
- `ParetoFront` collection with knee point detection and JSON export
- `MultiObjectiveOptimizer` with three methods:
  - Weighted-sum scalarization
  - Epsilon-constraint method
  - Sampling-based Pareto generation

**Package:** `neqsim.process.util.optimizer`

**Documentation:** See [Multi-Objective Optimization Guide](optimization/multi-objective-optimization)

---

## Example: Implemented Workflow ✅

The following workflow is now fully functional:

```java
import neqsim.process.design.*;
import neqsim.process.design.template.ThreeStageSeparationTemplate;
import neqsim.thermo.system.SystemSrkEos;

// Define fluid
SystemInterface wellFluid = new SystemSrkEos(60.0, 33.0);
wellFluid.addComponent("methane", 0.7);
wellFluid.addComponent("ethane", 0.1);
wellFluid.addComponent("propane", 0.1);
wellFluid.addComponent("nC10", 0.1);
wellFluid.setMixingRule("classic");

// Define process basis with company standards
ProcessBasis basis = new ProcessBasis.Builder()
    .designFlowRate(5000.0, "Sm3/hr")
    .designPressure(33.0, "bara")
    .designTemperature(60.0, "C")
    .turndown(0.3)
    .maxCapacity(1.2)
    .companyStandard("Equinor")
    .trDocument("TR1414")
    .safetyFactor(1.2)
    .build();

// Create process from template
ProcessTemplate template = new ThreeStageSeparationTemplate();
template.validate(basis);  // Check all required parameters
ProcessSystem process = template.instantiate(wellFluid, basis);

// Design and optimize in one call
DesignOptimizer optimizer = new DesignOptimizer();
DesignResult result = optimizer.designAndOptimize(process, basis);

// Access results
ProcessSystem optimizedProcess = result.getProcess();
double maxRate = result.getOptimalRate();
String bottleneck = result.getBottleneck();
Map<String, Double> sizingResults = result.getSizingResults();

System.out.println("Optimal rate: " + maxRate + " Sm3/hr");
System.out.println("Bottleneck: " + bottleneck);
System.out.println("Sizing results: " + sizingResults);

// Auto-size individual equipment with company standards
Separator separator = (Separator) process.getUnit("HP-Separator");
separator.autoSize("Equinor", "TR2000");  // Connects to MechanicalDesign
System.out.println(separator.getSizingReport());
```

**See also:**
- [DESIGN_FRAMEWORK.md](DESIGN_FRAMEWORK) - Complete API documentation
- Package `neqsim.process.design` - Source code and JavaDoc

---

## Conclusion

NeqSim now has a comprehensive design-to-optimization framework with the Phase 1 implementation complete:

### ✅ Implemented
1. **AutoSizeable Interface**: Equipment can auto-size based on flow requirements and company standards
2. **DesignSpecification Builder**: Standardized equipment configuration with factory methods
3. **ProcessTemplate System**: Reusable process configurations (ThreeStageSeparationTemplate)
4. **EquipmentConstraintRegistry**: Default constraint templates for all equipment types
5. **DesignOptimizer**: End-to-end workflow from template to optimized process
6. **MechanicalDesign Integration**: AutoSizeable connects to TR documents and company standards

### 🔧 Remaining Work
1. **Extended Equipment**: Add AutoSizeable to Pump, HeatExchanger
2. **More Templates**: Gas compression, dehydration, CO2 capture
3. **Multi-Objective**: Pareto optimization support

See [DESIGN_FRAMEWORK.md](DESIGN_FRAMEWORK) for complete API documentation and usage examples.

