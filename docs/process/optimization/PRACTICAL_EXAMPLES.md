---
title: Process Optimization Practical Examples
description: This document provides practical examples for using the optimizer plugin architecture with process simulations, including both Java and Python code samples.
---

# Process Optimization Practical Examples

> **New to process optimization?** Start with the [Optimization Overview](OPTIMIZATION_OVERVIEW.md) to understand when to use which optimizer.

This document provides practical examples for using the optimizer plugin architecture with process simulations, including both Java and Python code samples.

## Related Documentation

| Document | Description |
|----------|-------------|
| [Optimization Overview](OPTIMIZATION_OVERVIEW.md) | When to use which optimizer |
| [Optimizer Plugin Architecture](OPTIMIZER_PLUGIN_ARCHITECTURE.md) | ProcessOptimizationEngine API |
| [Production Optimization Guide](../../examples/PRODUCTION_OPTIMIZATION_GUIDE.md) | ProductionOptimizer examples |
| [External Optimizer Integration](../../integration/EXTERNAL_OPTIMIZER_INTEGRATION.md) | Python/SciPy integration |

## Table of Contents

- [Java Examples](#java-examples)
  - [Simple Throughput Optimization](#simple-throughput-optimization)
  - [Multi-Equipment Process](#multi-equipment-process)
  - [Constraint Monitoring Dashboard](#constraint-monitoring-dashboard)
  - [Eclipse VFP Table Generation](#eclipse-vfp-table-generation)
- [Python Examples (via JPype)](#python-examples-via-jpype)
  - [Basic Process Optimization](#basic-process-optimization)
  - [Lift Curve Generation](#lift-curve-generation)
  - [Equipment Constraint Analysis](#equipment-constraint-analysis)

---

## Java Examples

### Simple Throughput Optimization

Find the maximum throughput for a simple gas compression system:

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.util.optimizer.ProcessOptimizationEngine;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemInterface;

public class SimpleThroughputOptimization {
    public static void main(String[] args) {
        // Create gas composition
        SystemInterface gas = new SystemSrkEos(288.15, 50.0);
        gas.addComponent("methane", 0.85);
        gas.addComponent("ethane", 0.10);
        gas.addComponent("propane", 0.05);
        gas.setMixingRule("classic");
        
        // Create process equipment
        Stream feed = new Stream("feed", gas);
        feed.setFlowRate(50000, "kg/hr");
        feed.setPressure(50.0, "bara");
        feed.setTemperature(288.15, "K");
        
        Compressor compressor = new Compressor("Export Compressor", feed);
        compressor.setOutletPressure(150.0);
        compressor.setPolytropicEfficiency(0.78);
        
        Cooler aftercooler = new Cooler("Aftercooler", compressor.getOutletStream());
        aftercooler.setOutTemperature(313.15);
        
        // Build process
        ProcessSystem process = new ProcessSystem();
        process.add(feed);
        process.add(compressor);
        process.add(aftercooler);
        process.run();
        
        // Create optimization engine
        ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);
        
        // Find maximum throughput
        ProcessOptimizationEngine.OptimizationResult result = 
            engine.findMaximumThroughput(
                50.0,      // inlet pressure (bara)
                150.0,     // outlet pressure (bara)
                10000.0,   // min flow rate (kg/hr)
                200000.0   // max flow rate (kg/hr)
            );
        
        // Print results
        System.out.println("=== Optimization Results ===");
        System.out.println("Maximum throughput: " + result.getOptimalFlowRate() + " kg/hr");
        System.out.println("Feasible: " + result.isFeasible());
        System.out.println("Bottleneck: " + result.getBottleneckEquipment());
        System.out.println("Total power: " + result.getTotalPower() + " kW");
        
        // Print constraint violations if any
        if (!result.getConstraintViolations().isEmpty()) {
            System.out.println("\nConstraint violations:");
            for (String violation : result.getConstraintViolations()) {
                System.out.println("  - " + violation);
            }
        }
    }
}
```

### Multi-Equipment Process

Optimize a full oil and gas processing facility:

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.separator.*;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.heatexchanger.*;
import neqsim.process.util.optimizer.ProcessOptimizationEngine;
import neqsim.thermo.system.SystemSrkEos;

public class MultiEquipmentOptimization {
    public static void main(String[] args) {
        // Create wellstream fluid
        SystemInterface wellFluid = new SystemSrkEos(330.0, 80.0);
        wellFluid.addComponent("nitrogen", 0.005);
        wellFluid.addComponent("CO2", 0.02);
        wellFluid.addComponent("methane", 0.60);
        wellFluid.addComponent("ethane", 0.08);
        wellFluid.addComponent("propane", 0.05);
        wellFluid.addComponent("n-butane", 0.03);
        wellFluid.addComponent("n-pentane", 0.02);
        wellFluid.addComponent("nC10", 0.12);
        wellFluid.addComponent("water", 0.075);
        wellFluid.setMixingRule("classic");
        wellFluid.setMultiPhaseCheck(true);
        
        // Create production train
        Stream wellStream = new Stream("Well Stream", wellFluid);
        wellStream.setFlowRate(100000, "kg/hr");
        wellStream.setPressure(80.0, "bara");
        wellStream.setTemperature(330.0, "K");
        
        // HP Separator
        ThreePhaseSeparator hpSeparator = new ThreePhaseSeparator("HP Separator", wellStream);
        
        // Gas treatment train
        Heater gasHeater = new Heater("Gas Heater", hpSeparator.getGasOutStream());
        gasHeater.setOutTemperature(320.0);
        
        Compressor stage1 = new Compressor("1st Stage Compressor", gasHeater.getOutletStream());
        stage1.setOutletPressure(120.0);
        stage1.setPolytropicEfficiency(0.78);
        
        Cooler intercooler = new Cooler("Intercooler", stage1.getOutletStream());
        intercooler.setOutTemperature(313.15);
        
        Compressor stage2 = new Compressor("2nd Stage Compressor", intercooler.getOutletStream());
        stage2.setOutletPressure(180.0);
        stage2.setPolytropicEfficiency(0.76);
        
        Cooler aftercooler = new Cooler("Aftercooler", stage2.getOutletStream());
        aftercooler.setOutTemperature(313.15);
        
        // Oil treatment train
        Heater oilHeater = new Heater("Oil Heater", hpSeparator.getOilOutStream());
        oilHeater.setOutTemperature(340.0);
        
        Separator lpSeparator = new Separator("LP Separator", oilHeater.getOutletStream());
        lpSeparator.setInternalDiameter(2.0);
        
        Pump exportPump = new Pump("Export Pump", lpSeparator.getLiquidOutStream());
        exportPump.setOutletPressure(20.0);
        
        // Build process
        ProcessSystem process = new ProcessSystem();
        process.add(wellStream);
        process.add(hpSeparator);
        process.add(gasHeater);
        process.add(stage1);
        process.add(intercooler);
        process.add(stage2);
        process.add(aftercooler);
        process.add(oilHeater);
        process.add(lpSeparator);
        process.add(exportPump);
        process.run();
        
        // Create optimization engine
        ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);
        
        // Evaluate current constraints
        ProcessOptimizationEngine.ConstraintReport report = engine.evaluateAllConstraints();
        
        System.out.println("=== Equipment Utilization Summary ===\n");
        for (ProcessOptimizationEngine.EquipmentConstraintStatus status : 
                report.getEquipmentStatuses()) {
            String warningFlag = status.isWithinLimits() ? "✓" : "⚠";
            System.out.printf("%s %s: %.1f%% utilization\n",
                warningFlag,
                status.getEquipmentName(),
                status.getUtilization() * 100);
            
            // Show bottleneck constraint for each equipment
            if (status.getBottleneckConstraint() != null) {
                System.out.printf("   Bottleneck: %s\n", status.getBottleneckConstraint());
            }
        }
        
        // Find bottleneck
        System.out.println("\n=== Process Bottleneck ===");
        String bottleneck = engine.findBottleneckEquipment();
        System.out.println("Bottleneck equipment: " + bottleneck);
        
        // Find maximum throughput
        ProcessOptimizationEngine.OptimizationResult result = 
            engine.findMaximumThroughput(80.0, 180.0, 50000.0, 300000.0);
        
        System.out.println("\n=== Maximum Throughput ===");
        System.out.printf("Maximum rate: %.0f kg/hr (%.0f%% of current)\n",
            result.getOptimalFlowRate(),
            result.getOptimalFlowRate() / 100000.0 * 100);
        System.out.println("Limited by: " + result.getBottleneckEquipment());
        System.out.printf("Total compression power: %.1f MW\n", result.getTotalPower() / 1000.0);
    }
}
```

### Constraint Monitoring Dashboard

Create a real-time monitoring dashboard for equipment constraints:

```java
import neqsim.process.equipment.capacity.*;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.ProcessEquipmentInterface;
import java.util.*;

public class ConstraintMonitoringDashboard {
    
    private final ProcessSystem process;
    private final EquipmentCapacityStrategyRegistry registry;
    
    public ConstraintMonitoringDashboard(ProcessSystem process) {
        this.process = process;
        this.registry = EquipmentCapacityStrategyRegistry.getInstance();
    }
    
    /**
     * Generate constraint status report for all equipment.
     */
    public void printConstraintReport() {
        System.out.println("╔═══════════════════════════════════════════════════════════════════╗");
        System.out.println("║              EQUIPMENT CONSTRAINT STATUS DASHBOARD                 ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════════╣");
        
        for (int i = 0; i < process.getUnitOperations().size(); i++) {
            ProcessEquipmentInterface equipment = 
                (ProcessEquipmentInterface) process.getUnitOperations().get(i);
            
            EquipmentCapacityStrategy strategy = registry.findStrategy(equipment);
            if (strategy == null) {
                continue;  // Skip equipment without strategy
            }
            
            Map<String, CapacityConstraint> constraints = strategy.getConstraints(equipment);
            if (constraints.isEmpty()) {
                continue;
            }
            
            // Equipment header
            double maxUtil = strategy.evaluateCapacity(equipment);
            String status = maxUtil <= 0.9 ? "🟢" : (maxUtil <= 1.0 ? "🟡" : "🔴");
            System.out.printf("║ %s %-30s  Max Utilization: %6.1f%%        ║\n",
                status, equipment.getName(), maxUtil * 100);
            System.out.println("╟───────────────────────────────────────────────────────────────────╢");
            
            // Individual constraints
            for (CapacityConstraint c : constraints.values()) {
                String bar = createUtilizationBar(c.getUtilization());
                String typeChar = getConstraintTypeChar(c.getType());
                System.out.printf("║   %s %-20s %8.2f/%-8.2f %-4s %s  ║\n",
                    typeChar,
                    c.getName(),
                    c.getCurrentValue(),
                    c.getDesignValue(),
                    c.getUnit(),
                    bar);
            }
            System.out.println("╟───────────────────────────────────────────────────────────────────╢");
        }
        System.out.println("╚═══════════════════════════════════════════════════════════════════╝");
        
        // Legend
        System.out.println("\nLegend: [H]=HARD limit  [S]=SOFT limit  [D]=DESIGN limit");
        System.out.println("        🟢=OK  🟡=Warning (>90%)  🔴=Exceeded (>100%)");
    }
    
    private String createUtilizationBar(double utilization) {
        int barLength = 15;
        int filled = (int) Math.min(utilization * barLength, barLength);
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < barLength; i++) {
            if (i < filled) {
                if (utilization > 1.0) {
                    bar.append("█");  // Over limit
                } else if (i >= barLength * 0.9) {
                    bar.append("▓");  // Warning zone
                } else {
                    bar.append("░");  // Normal
                }
            } else {
                bar.append(" ");
            }
        }
        bar.append(String.format("] %5.1f%%", utilization * 100));
        return bar.toString();
    }
    
    private String getConstraintTypeChar(CapacityConstraint.ConstraintType type) {
        switch (type) {
            case HARD: return "[H]";
            case SOFT: return "[S]";
            case DESIGN: return "[D]";
            default: return "[ ]";
        }
    }
    
    /**
     * Get equipment that should be investigated for debottlenecking.
     */
    public List<String> getDebottleneckingCandidates() {
        List<String> candidates = new ArrayList<>();
        
        for (int i = 0; i < process.getUnitOperations().size(); i++) {
            ProcessEquipmentInterface equipment = 
                (ProcessEquipmentInterface) process.getUnitOperations().get(i);
            
            EquipmentCapacityStrategy strategy = registry.findStrategy(equipment);
            if (strategy != null) {
                double utilization = strategy.evaluateCapacity(equipment);
                if (utilization > 0.85) {
                    candidates.add(String.format("%s (%.1f%%)", 
                        equipment.getName(), utilization * 100));
                }
            }
        }
        
        return candidates;
    }
}
```

### Eclipse VFP Table Generation

Generate VFP tables for reservoir simulation:

```java
import neqsim.process.util.optimizer.EclipseVFPExporter;
import neqsim.process.processmodel.ProcessSystem;
import java.nio.file.*;

public class VFPTableGeneration {
    public static void main(String[] args) throws Exception {
        // Create process system (as in previous examples)
        ProcessSystem process = createGasExportProcess();
        
        // Create VFP exporter
        EclipseVFPExporter exporter = new EclipseVFPExporter(process);
        exporter.setTableNumber(1);
        
        // Define parameter ranges
        double[] thp = {20.0, 30.0, 40.0, 50.0, 60.0};           // THP (bara)
        double[] wfr = {0.0, 0.1, 0.2, 0.3, 0.5};                // Water fraction
        double[] gfr = {100.0, 200.0, 500.0, 1000.0, 2000.0};    // GOR (Sm3/Sm3)
        double[] alq = {0.0};                                     // No artificial lift
        double[] flowRates = {
            5000.0, 10000.0, 20000.0, 50000.0, 
            100000.0, 150000.0, 200000.0
        };  // Flow rates (kg/hr)
        
        // Generate VFPPROD table
        String vfpTable = exporter.generateVFPPROD(
            thp, wfr, gfr, alq, flowRates,
            "bara", "kg/hr"
        );
        
        // Write to file
        Path outputPath = Paths.get("VFPPROD_PLATFORM.INC");
        Files.writeString(outputPath, vfpTable);
        System.out.println("VFP table written to: " + outputPath.toAbsolutePath());
        
        // Print summary
        System.out.println("\n=== VFP Table Summary ===");
        System.out.println("Table number: 1");
        System.out.println("THP points: " + thp.length);
        System.out.println("Water fraction points: " + wfr.length);
        System.out.println("GOR points: " + gfr.length);
        System.out.println("Flow rate points: " + flowRates.length);
        System.out.println("Total BHP calculations: " + 
            (thp.length * wfr.length * gfr.length * flowRates.length));
    }
    
    private static ProcessSystem createGasExportProcess() {
        // ... create process as in previous examples ...
        return new ProcessSystem();
    }
}
```

---

## Python Examples (via JPype)

### Basic Process Optimization

Using NeqSim from Python with the direct Java API:

```python
import jpype
import jpype.imports
from jpype.types import *

# Start JVM (if not already started)
if not jpype.isJVMStarted():
    jpype.startJVM(classpath=['path/to/neqsim.jar'])

# Import Java classes
from neqsim.thermo.system import SystemSrkEos
from neqsim.process.processmodel import ProcessSystem
from neqsim.process.equipment.stream import Stream
from neqsim.process.equipment.compressor import Compressor
from neqsim.process.equipment.heatexchanger import Cooler
from neqsim.process.util.optimizer import ProcessOptimizationEngine


def create_compression_process():
    """Create a simple gas compression process."""
    
    # Create gas composition
    gas = SystemSrkEos(288.15, 50.0)
    gas.addComponent("methane", 0.85)
    gas.addComponent("ethane", 0.10)
    gas.addComponent("propane", 0.05)
    gas.setMixingRule("classic")
    
    # Create feed stream
    feed = Stream("feed", gas)
    feed.setFlowRate(50000, "kg/hr")
    feed.setPressure(50.0, "bara")
    feed.setTemperature(288.15, "K")
    
    # Create compressor
    compressor = Compressor("Export Compressor", feed)
    compressor.setOutletPressure(150.0)
    compressor.setPolytropicEfficiency(0.78)
    
    # Create aftercooler
    aftercooler = Cooler("Aftercooler", compressor.getOutletStream())
    aftercooler.setOutTemperature(313.15)
    
    # Build process
    process = ProcessSystem()
    process.add(feed)
    process.add(compressor)
    process.add(aftercooler)
    process.run()
    
    return process


def optimize_throughput(process):
    """Find maximum throughput for the process."""
    
    # Create optimization engine
    engine = ProcessOptimizationEngine(process)
    
    # Find maximum throughput
    result = engine.findMaximumThroughput(
        50.0,      # inlet pressure (bara)
        150.0,     # outlet pressure (bara)
        10000.0,   # min flow rate (kg/hr)
        200000.0   # max flow rate (kg/hr)
    )
    
    # Extract results
    return {
        'optimal_flow_rate': result.getOptimalFlowRate(),
        'feasible': result.isFeasible(),
        'bottleneck': result.getBottleneckEquipment(),
        'total_power': result.getTotalPower(),
        'constraint_violations': list(result.getConstraintViolations())
    }


def evaluate_constraints(process):
    """Evaluate all equipment constraints."""
    
    engine = ProcessOptimizationEngine(process)
    report = engine.evaluateAllConstraints()
    
    results = []
    for status in report.getEquipmentStatuses():
        equipment_data = {
            'name': status.getEquipmentName(),
            'type': status.getEquipmentType(),
            'utilization': status.getUtilization(),
            'within_limits': status.isWithinLimits(),
            'bottleneck_constraint': status.getBottleneckConstraint()
        }
        
        # Get individual constraints
        constraints = []
        for constraint in status.getConstraints():
            constraints.append({
                'name': constraint.getName(),
                'current_value': constraint.getCurrentValue(),
                'design_value': constraint.getDesignValue(),
                'unit': constraint.getUnit(),
                'utilization_percent': constraint.getUtilizationPercent()
            })
        equipment_data['constraints'] = constraints
        results.append(equipment_data)
    
    return results


# Main execution
if __name__ == "__main__":
    # Create process
    process = create_compression_process()
    
    # Optimize throughput
    print("=== Throughput Optimization ===")
    opt_result = optimize_throughput(process)
    print(f"Maximum throughput: {opt_result['optimal_flow_rate']:.0f} kg/hr")
    print(f"Bottleneck: {opt_result['bottleneck']}")
    print(f"Total power: {opt_result['total_power']:.1f} kW")
    
    # Evaluate constraints
    print("\n=== Equipment Constraints ===")
    constraint_report = evaluate_constraints(process)
    for eq in constraint_report:
        status = "✓" if eq['within_limits'] else "⚠"
        print(f"{status} {eq['name']}: {eq['utilization']*100:.1f}% utilization")
        for c in eq['constraints']:
            print(f"   - {c['name']}: {c['current_value']:.2f}/{c['design_value']:.2f} "
                  f"{c['unit']} ({c['utilization_percent']:.1f}%)")
```

### Lift Curve Generation

Generate lift curves and export to pandas DataFrame:

```python
import jpype
import jpype.imports
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

# ... JVM startup code ...

from neqsim.process.util.optimizer import ProcessOptimizationEngine, EclipseVFPExporter


def generate_lift_curve_data(process, 
                              inlet_pressures,
                              outlet_pressures,
                              flow_rates):
    """Generate lift curve data for a range of conditions."""
    
    engine = ProcessOptimizationEngine(process)
    
    results = []
    for p_in in inlet_pressures:
        for p_out in outlet_pressures:
            for q in flow_rates:
                # Try to run at this operating point
                try:
                    # Update process conditions
                    feed = process.getUnit("feed")
                    feed.setPressure(p_in, "bara")
                    feed.setFlowRate(q, "kg/hr")
                    
                    compressor = process.getUnit("Export Compressor")
                    compressor.setOutletPressure(p_out)
                    
                    process.run()
                    
                    # Evaluate constraints
                    report = engine.evaluateAllConstraints()
                    
                    # Get compressor data
                    comp_power = compressor.getPower()
                    comp_efficiency = compressor.getPolytropicEfficiency()
                    
                    # Check feasibility
                    feasible = not report.hasViolations()
                    bottleneck = report.getBottleneckEquipment() if report.hasViolations() else None
                    
                    results.append({
                        'inlet_pressure': p_in,
                        'outlet_pressure': p_out,
                        'flow_rate': q,
                        'power': comp_power,
                        'efficiency': comp_efficiency,
                        'feasible': feasible,
                        'bottleneck': bottleneck,
                        'overall_utilization': report.getOverallUtilization()
                    })
                    
                except Exception as e:
                    results.append({
                        'inlet_pressure': p_in,
                        'outlet_pressure': p_out,
                        'flow_rate': q,
                        'power': np.nan,
                        'efficiency': np.nan,
                        'feasible': False,
                        'bottleneck': str(e),
                        'overall_utilization': np.nan
                    })
    
    return pd.DataFrame(results)


def plot_operating_envelope(df):
    """Plot the equipment operating envelope from lift curve data."""
    
    fig, axes = plt.subplots(2, 2, figsize=(12, 10))
    
    # Plot 1: Flow rate vs Power (colored by feasibility)
    ax1 = axes[0, 0]
    colors = ['green' if f else 'red' for f in df['feasible']]
    ax1.scatter(df['flow_rate'], df['power'], c=colors, alpha=0.6)
    ax1.set_xlabel('Flow Rate (kg/hr)')
    ax1.set_ylabel('Power (kW)')
    ax1.set_title('Power vs Flow Rate')
    ax1.grid(True, alpha=0.3)
    
    # Plot 2: Inlet Pressure vs Max Flow (envelope)
    ax2 = axes[0, 1]
    feasible_df = df[df['feasible']]
    max_flow_by_pin = feasible_df.groupby('inlet_pressure')['flow_rate'].max()
    ax2.plot(max_flow_by_pin.index, max_flow_by_pin.values, 'b-o', linewidth=2)
    ax2.fill_between(max_flow_by_pin.index, 0, max_flow_by_pin.values, alpha=0.3)
    ax2.set_xlabel('Inlet Pressure (bara)')
    ax2.set_ylabel('Maximum Flow Rate (kg/hr)')
    ax2.set_title('Operating Envelope')
    ax2.grid(True, alpha=0.3)
    
    # Plot 3: Flow rate vs Utilization
    ax3 = axes[1, 0]
    ax3.scatter(df['flow_rate'], df['overall_utilization'] * 100, alpha=0.6)
    ax3.axhline(y=100, color='r', linestyle='--', label='100% Utilization')
    ax3.axhline(y=90, color='orange', linestyle='--', label='90% Warning')
    ax3.set_xlabel('Flow Rate (kg/hr)')
    ax3.set_ylabel('Overall Utilization (%)')
    ax3.set_title('Utilization vs Flow Rate')
    ax3.legend()
    ax3.grid(True, alpha=0.3)
    
    # Plot 4: Bottleneck distribution
    ax4 = axes[1, 1]
    bottleneck_counts = df[~df['feasible']]['bottleneck'].value_counts()
    if len(bottleneck_counts) > 0:
        ax4.pie(bottleneck_counts.values, labels=bottleneck_counts.index, autopct='%1.1f%%')
        ax4.set_title('Bottleneck Distribution (Infeasible Cases)')
    else:
        ax4.text(0.5, 0.5, 'All cases feasible', ha='center', va='center')
        ax4.set_title('Bottleneck Distribution')
    
    plt.tight_layout()
    plt.savefig('operating_envelope.png', dpi=150)
    plt.show()
    
    return fig


# Main execution
if __name__ == "__main__":
    # Create process
    process = create_compression_process()
    
    # Define parameter ranges
    inlet_pressures = np.linspace(40, 80, 5)
    outlet_pressures = [150.0]  # Fixed outlet
    flow_rates = np.linspace(20000, 150000, 10)
    
    # Generate lift curve data
    print("Generating lift curve data...")
    lift_curve_df = generate_lift_curve_data(
        process, inlet_pressures, outlet_pressures, flow_rates
    )
    
    # Save to CSV
    lift_curve_df.to_csv('lift_curve_data.csv', index=False)
    print(f"Saved {len(lift_curve_df)} data points to lift_curve_data.csv")
    
    # Print summary
    feasible_count = lift_curve_df['feasible'].sum()
    print(f"\nFeasible operating points: {feasible_count}/{len(lift_curve_df)}")
    
    # Plot
    plot_operating_envelope(lift_curve_df)
```

### Equipment Constraint Analysis

Detailed analysis of equipment constraints with visualization:

```python
import jpype
import jpype.imports
import pandas as pd
import matplotlib.pyplot as plt

# ... JVM startup code ...

from neqsim.process.equipment.capacity import EquipmentCapacityStrategyRegistry


def analyze_equipment_constraints(process):
    """Detailed analysis of equipment constraints."""
    
    registry = EquipmentCapacityStrategyRegistry.getInstance()
    
    all_constraints = []
    
    for i in range(process.getUnitOperations().size()):
        equipment = process.getUnitOperations().get(i)
        strategy = registry.findStrategy(equipment)
        
        if strategy is None:
            continue
            
        constraints = strategy.getConstraints(equipment)
        
        for name, constraint in constraints.items():
            all_constraints.append({
                'equipment': str(equipment.getName()),
                'constraint': str(name),
                'type': str(constraint.getType()),
                'current': constraint.getCurrentValue(),
                'design': constraint.getDesignValue(),
                'max': constraint.getMaxValue() if constraint.getMaxValue() > 0 else constraint.getDesignValue() * 1.1,
                'min': constraint.getMinValue(),
                'unit': str(constraint.getUnit()),
                'utilization': constraint.getUtilization(),
                'violated': constraint.isViolated()
            })
    
    return pd.DataFrame(all_constraints)


def plot_constraint_dashboard(df):
    """Create a visual dashboard of constraint status."""
    
    # Group by equipment
    equipment_list = df['equipment'].unique()
    n_equipment = len(equipment_list)
    
    fig, axes = plt.subplots(n_equipment, 1, figsize=(12, 3 * n_equipment))
    if n_equipment == 1:
        axes = [axes]
    
    for i, equipment in enumerate(equipment_list):
        ax = axes[i]
        eq_df = df[df['equipment'] == equipment]
        
        # Create horizontal bar chart
        constraints = eq_df['constraint'].values
        utilizations = eq_df['utilization'].values * 100
        violations = eq_df['violated'].values
        
        colors = ['red' if v else ('orange' if u > 90 else 'green') 
                  for u, v in zip(utilizations, violations)]
        
        y_pos = range(len(constraints))
        bars = ax.barh(y_pos, utilizations, color=colors, alpha=0.7)
        
        # Add reference lines
        ax.axvline(x=100, color='red', linestyle='--', linewidth=2, label='Limit')
        ax.axvline(x=90, color='orange', linestyle='--', linewidth=1, label='Warning')
        
        # Labels
        ax.set_yticks(y_pos)
        ax.set_yticklabels(constraints)
        ax.set_xlabel('Utilization (%)')
        ax.set_title(f'{equipment}')
        ax.set_xlim(0, max(120, max(utilizations) * 1.1))
        
        # Add value labels
        for bar, util in zip(bars, utilizations):
            ax.text(bar.get_width() + 2, bar.get_y() + bar.get_height()/2,
                   f'{util:.1f}%', va='center')
        
        ax.grid(True, alpha=0.3, axis='x')
    
    plt.tight_layout()
    plt.savefig('constraint_dashboard.png', dpi=150)
    plt.show()
    
    return fig


# Main execution
if __name__ == "__main__":
    # Create and run process
    process = create_compression_process()
    
    # Analyze constraints
    print("Analyzing equipment constraints...")
    constraint_df = analyze_equipment_constraints(process)
    
    # Print summary table
    print("\n=== Constraint Summary ===")
    print(constraint_df[['equipment', 'constraint', 'type', 'utilization', 'violated']]
          .to_string(index=False))
    
    # Save to CSV
    constraint_df.to_csv('constraint_analysis.csv', index=False)
    
    # Identify critical constraints
    critical = constraint_df[constraint_df['violated']]
    if len(critical) > 0:
        print("\n⚠ CRITICAL CONSTRAINTS:")
        for _, row in critical.iterrows():
            print(f"  - {row['equipment']}/{row['constraint']}: "
                  f"{row['current']:.2f} > {row['design']:.2f} {row['unit']}")
    
    # Identify near-limit constraints
    near_limit = constraint_df[(constraint_df['utilization'] > 0.9) & (~constraint_df['violated'])]
    if len(near_limit) > 0:
        print("\n⚡ NEAR LIMIT (>90%):")
        for _, row in near_limit.iterrows():
            print(f"  - {row['equipment']}/{row['constraint']}: "
                  f"{row['utilization']*100:.1f}%")
    
    # Plot dashboard
    plot_constraint_dashboard(constraint_df)
```

---

## See Also

- [Optimizer Plugin Architecture](OPTIMIZER_PLUGIN_ARCHITECTURE.md) - Full API documentation
- [Capacity Constraint Framework](../CAPACITY_CONSTRAINT_FRAMEWORK.md) - Core constraint system
- [NeqSim Python Guide](https://github.com/equinor/neqsim-python) - Python integration guide
