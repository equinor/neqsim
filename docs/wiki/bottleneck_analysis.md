# Bottleneck Analysis and Capacity Utilization

NeqSim provides functionality to analyze capacity utilization and identify bottlenecks in a process simulation. This feature is useful for production optimization and debottlenecking studies.

## Overview

The bottleneck analysis identifies which unit operation in a process system is operating closest to its maximum design capacity. The analysis is based on the "utilization ratio," defined as:

$$
\text{Utilization} = \frac{\text{Current Duty}}{\text{Maximum Capacity}}
$$

The unit operation with the highest utilization ratio is considered the bottleneck.

## Key Concepts

### 1. Capacity Duty (`getCapacityDuty`)
The `getCapacityDuty()` method returns the current operating load of a unit operation. The definition of "duty" varies by equipment type:
- **Compressor**: Total power consumption (Watts).
- **Separator**: Gas outlet flow rate ($m^3/hr$).
- **Other Equipment**: Default is 0.0 (needs implementation for specific units).

### 2. Maximum Capacity (`getCapacityMax`)
The `getCapacityMax()` method returns the maximum design capacity of the equipment. This value is typically set in the equipment's mechanical design.
- **Compressor**: `maxDesignPower` (Watts).
- **Separator**: `maxDesignGassVolumeFlow` ($m^3/hr$).

### 3. Rest Capacity (`getRestCapacity`)
The `getRestCapacity()` method calculates the remaining available capacity:
$$
\text{Rest Capacity} = \text{Maximum Capacity} - \text{Current Duty}
$$

## Implementation Details

### ProcessEquipmentInterface
The `ProcessEquipmentInterface` defines the methods for capacity analysis:
```java
public double getCapacityDuty();
public double getCapacityMax();
public double getRestCapacity();
```

### ProcessSystem
The `ProcessSystem` class includes a method to identify the bottleneck:
```java
public ProcessEquipmentInterface getBottleneck();
```
This method iterates through all unit operations in the system and returns the one with the highest utilization ratio.

## Supported Equipment

Currently, the following equipment types support capacity analysis:

| Equipment | Duty Metric | Capacity Parameter |
|-----------|-------------|--------------------|
| **Compressor** | Power (W) | `MechanicalDesign.maxDesignPower` |
| **Separator** | Gas Flow ($m^3/hr$) | `MechanicalDesign.maxDesignGassVolumeFlow` |
| **Pump** | Power (W) | `MechanicalDesign.maxDesignPower` |
| **Heater** | Duty (W) | `MechanicalDesign.maxDesignDuty` |
| **Cooler** | Duty (W) | `MechanicalDesign.maxDesignDuty` |
| **ThrottlingValve** | Volume Flow ($m^3/hr$) | `MechanicalDesign.maxDesignVolumeFlow` |
| **Pipeline** | Volume Flow ($m^3/hr$) | `MechanicalDesign.maxDesignVolumeFlow` |

## Example Usage

The following example demonstrates how to set up a simulation, define capacities, and identify the bottleneck.

```java
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

public class BottleneckExample {
    public static void main(String[] args) {
        // 1. Create System
        SystemSrkEos testSystem = new SystemSrkEos(298.15, 10.0);
        testSystem.addComponent("methane", 100.0);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);

        Stream inletStream = new Stream("inlet stream", testSystem);
        inletStream.setFlowRate(100.0, "MSm3/day");
        inletStream.setTemperature(20.0, "C");
        inletStream.setPressure(10.0, "bara");

        // 2. Create Equipment and Set Capacities
        Separator separator = new Separator("separator", inletStream);
        // Set Separator Capacity (e.g., 200 m3/hr)
        separator.getMechanicalDesign().setMaxDesignGassVolumeFlow(200.0); 

        Compressor compressor = new Compressor("compressor", separator.getGasOutStream());
        compressor.setOutletPressure(50.0);
        // Set Compressor Capacity (e.g., 5 MW)
        compressor.getMechanicalDesign().maxDesignPower = 5000000.0; 

        // 3. Run Simulation
        ProcessSystem process = new ProcessSystem();
        process.add(inletStream);
        process.add(separator);
        process.add(compressor);
        process.run();

        // 4. Analyze Results
        System.out.println("Separator Duty: " + separator.getCapacityDuty());
        System.out.println("Separator Max: " + separator.getCapacityMax());
        System.out.println("Compressor Duty: " + compressor.getCapacityDuty());
        System.out.println("Compressor Max: " + compressor.getCapacityMax());

        if (process.getBottleneck() != null) {
            System.out.println("Bottleneck: " + process.getBottleneck().getName());
            double utilization = process.getBottleneck().getCapacityDuty() / process.getBottleneck().getCapacityMax();
            System.out.println("Utilization: " + (utilization * 100) + "%");
        } else {
            System.out.println("No bottleneck found (or capacity not set)");
        }
        
        System.out.println("Compressor Rest Capacity: " + compressor.getRestCapacity());
    }
}
```

## Extending to Other Equipment

To support capacity analysis for other equipment types (e.g., Pumps, Heat Exchangers), implement the `getCapacityDuty()` and `getCapacityMax()` methods in the respective classes. Ensure that the units for duty and capacity are consistent (e.g., both in Watts or both in kg/hr).

## Production Optimization

The bottleneck analysis feature is a powerful tool for optimizing production. By identifying the limiting constraint in a process, you can maximize throughput or identify the most effective upgrades (debottlenecking).

### Optimization Workflow

1.  **Define Objective**: Typically, the goal is to maximize the feed flow rate.
2.  **Identify Constraints**: The constraints are the maximum capacities of all equipment in the system.
3.  **Iterative Solver**:
    *   Run the simulation with an initial feed rate.
    *   Check `process.getBottleneck()`.
    *   If the bottleneck utilization is < 100%, increase the feed rate.
    *   If the bottleneck utilization is > 100%, decrease the feed rate.
    *   Repeat until the bottleneck utilization is exactly 100% (or a safety margin, e.g., 95%).

### Example: Maximizing Production

This example demonstrates a simple optimization loop to find the maximum possible feed rate before hitting a bottleneck.

```java
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.processmodel.ProcessSystem;

public class OptimizationExample {
    public static void optimizeProduction(ProcessSystem process, Stream inletStream) {
        double low = 0.0;
        double high = 1000.0; // Assume a high upper bound for flow rate
        double tolerance = 0.01; // Convergence tolerance

        while ((high - low) > tolerance) {
            double mid = (low + high) / 2.0;
            inletStream.setFlowRate(mid, "MSm3/day");
            process.run();

            ProcessEquipmentInterface bottleneck = process.getBottleneck();
            double maxUtilization = 0.0;
            
            if (bottleneck != null) {
                maxUtilization = bottleneck.getCapacityDuty() / bottleneck.getCapacityMax();
            }

            if (maxUtilization > 1.0) {
                high = mid; // Too high, reduce flow
            } else {
                low = mid; // Safe, try increasing flow
            }
        }

        System.out.println("Maximum Production Rate: " + low + " MSm3/day");
        System.out.println("Limiting Factor: " + process.getBottleneck().getName());
    }
}
```

### Debottlenecking Studies

Once the bottleneck is identified (e.g., a compressor), you can simulate a "debottlenecking" project:
1.  Increase the capacity of the bottleneck equipment (e.g., `compressor.getMechanicalDesign().maxDesignPower = newPower`).
2.  Re-run the optimization loop.
3.  Identify the *new* bottleneck and the new maximum production rate.
4.  Calculate the ROI of the upgrade based on the increased production.

