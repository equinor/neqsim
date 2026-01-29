# Well Production Allocation

Documentation for production allocation in commingled well systems.

## Table of Contents
- [Overview](#overview)
- [Allocation Methods](#allocation-methods)
- [WellProductionAllocator Class](#wellproductionallocator-class)
- [Usage Examples](#usage-examples)
- [Integration with AI Platforms](#integration-with-ai-platforms)
- [Best Practices](#best-practices)

---

## Overview

**Package:** `neqsim.process.equipment.well.allocation`

Production allocation is the process of distributing commingled production back to individual wells. This is essential for:
- Reservoir management and history matching
- Production accounting and revenue allocation
- Well performance monitoring
- Regulatory reporting

### Key Classes

| Class | Description |
|-------|-------------|
| `WellProductionAllocator` | Main allocation engine |
| `WellData` | Individual well data container |
| `AllocationResult` | Allocation calculation results |
| `AllocationMethod` | Enumeration of allocation methods |

---

## Allocation Methods

### Well Test Allocation

Uses periodic well test data to allocate commingled production. This is the most common method in the industry.

**Equation:**

$$Q_i^{oil} = Q_{total}^{oil} \cdot \frac{Q_{i,test}^{oil}}{\sum_j Q_{j,test}^{oil}}$$

where:
- $Q_i^{oil}$ = allocated oil rate for well i
- $Q_{total}^{oil}$ = total commingled oil rate
- $Q_{i,test}^{oil}$ = most recent test rate for well i

### VFM-Based Allocation

Uses Virtual Flow Meter (VFM) estimates for real-time allocation. VFMs typically use:
- Wellhead pressure and temperature
- Choke position
- Well model parameters

**Advantages:**
- Real-time estimates
- Higher frequency than well tests
- Can detect sudden changes

### Choke Model Allocation

Uses choke performance correlations to estimate individual well rates based on:
- Choke opening (%)
- Upstream/downstream pressure
- Fluid properties

**Typical Choke Equation:**

$$Q = C_v \cdot f(P_1, P_2, \rho, \gamma)$$

### Combined Method

Weighted combination of multiple methods for robust allocation:

$$Q_i = w_{test} \cdot Q_i^{test} + w_{vfm} \cdot Q_i^{vfm} + w_{choke} \cdot Q_i^{choke}$$

---

## WellProductionAllocator Class

### Creating an Allocator

```java
import neqsim.process.equipment.well.allocation.WellProductionAllocator;
import neqsim.process.equipment.well.allocation.WellProductionAllocator.AllocationMethod;
import neqsim.process.equipment.well.allocation.WellProductionAllocator.WellData;
import neqsim.process.equipment.well.allocation.AllocationResult;

// Create allocator
WellProductionAllocator allocator = new WellProductionAllocator("Field A Allocation");

// Set allocation method
allocator.setAllocationMethod(AllocationMethod.WELL_TEST);

// Set reconciliation tolerance (1% default)
allocator.setReconciliationTolerance(0.01);
```

### Adding Well Data

```java
// Add wells with test data
WellData well1 = new WellData("A-1H");
well1.setTestRates(1500.0, 2.5e6, 200.0);  // oil (bbl/d), gas (scf/d), water (bbl/d)
well1.setWellStream(wellStream1);
allocator.addWell(well1);

WellData well2 = new WellData("A-2H");
well2.setTestRates(2200.0, 3.8e6, 450.0);
well2.setWellStream(wellStream2);
allocator.addWell(well2);

WellData well3 = new WellData("A-3H");
well3.setTestRates(1800.0, 3.0e6, 350.0);
well3.setWellStream(wellStream3);
allocator.addWell(well3);
```

### Setting Commingled Production

```java
// Set total commingled rates (measured at manifold)
allocator.setCommingledOilRate(5400.0);      // bbl/d
allocator.setCommingledGasRate(9.0e6);       // scf/d
allocator.setCommingledWaterRate(980.0);     // bbl/d
```

### Running Allocation

```java
// Perform allocation
AllocationResult result = allocator.allocate();

// Get allocated rates
for (String wellName : result.getWellNames()) {
    System.out.println(wellName + ":");
    System.out.println("  Oil: " + result.getAllocatedOilRate(wellName) + " bbl/d");
    System.out.println("  Gas: " + result.getAllocatedGasRate(wellName) + " scf/d");
    System.out.println("  Water: " + result.getAllocatedWaterRate(wellName) + " bbl/d");
    System.out.println("  Oil fraction: " + result.getOilAllocationFactor(wellName));
}
```

---

## Usage Examples

### Basic Well Test Allocation

```java
import neqsim.process.equipment.well.allocation.*;

// Create allocator
WellProductionAllocator allocator = new WellProductionAllocator("Platform A");
allocator.setAllocationMethod(AllocationMethod.WELL_TEST);

// Add wells with most recent test data
WellData a1 = new WellData("A-1");
a1.setTestRates(1200.0, 2.0e6, 150.0);
allocator.addWell(a1);

WellData a2 = new WellData("A-2");
a2.setTestRates(1800.0, 3.2e6, 280.0);
allocator.addWell(a2);

WellData a3 = new WellData("A-3");
a3.setTestRates(900.0, 1.5e6, 120.0);
allocator.addWell(a3);

// Set measured commingled production
allocator.setCommingledOilRate(3800.0);
allocator.setCommingledGasRate(6.5e6);
allocator.setCommingledWaterRate(530.0);

// Allocate
AllocationResult result = allocator.allocate();

// Print results
System.out.println("Allocation Results:");
System.out.println("==================");
for (String well : result.getWellNames()) {
    System.out.printf("%s: Oil=%.0f bbl/d, Gas=%.2e scf/d, Water=%.0f bbl/d%n",
        well,
        result.getAllocatedOilRate(well),
        result.getAllocatedGasRate(well),
        result.getAllocatedWaterRate(well));
}

// Check reconciliation
System.out.println("\nReconciliation:");
System.out.println("Oil: " + result.getOilReconciliationError() + "%");
System.out.println("Gas: " + result.getGasReconciliationError() + "%");
System.out.println("Water: " + result.getWaterReconciliationError() + "%");
```

### VFM-Based Allocation

```java
// Create allocator with VFM method
WellProductionAllocator allocator = new WellProductionAllocator("Subsea Wells");
allocator.setAllocationMethod(AllocationMethod.VFM_BASED);

// Add wells with VFM estimates
WellData w1 = new WellData("Well-1");
w1.setVFMRates(2100.0, 4.2e6, 380.0);  // Real-time VFM estimates
w1.setChokePosition(45.0);  // % open
allocator.addWell(w1);

WellData w2 = new WellData("Well-2");
w2.setVFMRates(1850.0, 3.6e6, 290.0);
w2.setChokePosition(52.0);
allocator.addWell(w2);

// Set commingled (measured at topside)
allocator.setCommingledOilRate(3900.0);
allocator.setCommingledGasRate(7.7e6);
allocator.setCommingledWaterRate(650.0);

// Run allocation
AllocationResult result = allocator.allocate();
```

### Combined Allocation with Weights

```java
// Use combined method with custom weights
WellProductionAllocator allocator = new WellProductionAllocator("Field B");
allocator.setAllocationMethod(AllocationMethod.COMBINED);

// Set method weights
allocator.setMethodWeights(0.5, 0.3, 0.2);  // test, vfm, choke

// Add well with multiple data sources
WellData well = new WellData("B-1");
well.setTestRates(1500.0, 2.8e6, 200.0);     // From test
well.setVFMRates(1580.0, 2.9e6, 215.0);      // From VFM
well.setChokePosition(60.0);                  // For choke model
well.setReservoirPressure(2800.0);           // psia
well.setProductivityIndex(15.0);              // bbl/d/psi
allocator.addWell(well);

// ... add more wells ...

AllocationResult result = allocator.allocate();
```

---

## Integration with AI Platforms

### Real-Time Production Optimization

The WellProductionAllocator is designed for integration with AI optimization platforms:

```java
import neqsim.process.equipment.well.allocation.WellProductionAllocator;

// Integration with production optimization
public class ProductionOptimizationService {
    
    private WellProductionAllocator allocator;
    
    public Map<String, Double> getAllocatedRates(Map<String, Double[]> testData,
                                                  double[] commingledRates) {
        allocator = new WellProductionAllocator("Real-Time Allocation");
        
        // Add well data
        for (Map.Entry<String, Double[]> entry : testData.entrySet()) {
            WellData well = new WellData(entry.getKey());
            Double[] rates = entry.getValue();
            well.setTestRates(rates[0], rates[1], rates[2]);
            allocator.addWell(well);
        }
        
        // Set commingled rates
        allocator.setCommingledOilRate(commingledRates[0]);
        allocator.setCommingledGasRate(commingledRates[1]);
        allocator.setCommingledWaterRate(commingledRates[2]);
        
        // Allocate
        AllocationResult result = allocator.allocate();
        
        // Return as map for AI platform
        Map<String, Double> allocation = new HashMap<>();
        for (String wellName : result.getWellNames()) {
            allocation.put(wellName + "_oil", result.getAllocatedOilRate(wellName));
            allocation.put(wellName + "_gas", result.getAllocatedGasRate(wellName));
            allocation.put(wellName + "_water", result.getAllocatedWaterRate(wellName));
        }
        
        return allocation;
    }
}
```

### JSON Export for External Systems

```java
// Export allocation results as JSON
AllocationResult result = allocator.allocate();
String jsonReport = result.toJson();

// Example output:
// {
//   "timestamp": "2024-01-15T10:30:00Z",
//   "method": "WELL_TEST",
//   "wells": [
//     {
//       "name": "A-1",
//       "allocatedOil": 1234.5,
//       "allocatedGas": 2.1e6,
//       "allocatedWater": 156.7,
//       "allocationFactor": 0.325
//     },
//     ...
//   ],
//   "reconciliation": {
//     "oilError": 0.5,
//     "gasError": -0.3,
//     "waterError": 1.2
//   }
// }
```

---

## Best Practices

### Test Data Management

1. **Update frequency**: Well tests should be updated monthly or after significant changes
2. **Data quality**: Validate test data before using for allocation
3. **Outlier detection**: Flag wells with allocation factors >2x historical average

### Reconciliation

1. **Acceptable error**: Typically <2-3% for well-tested fields
2. **High errors indicate**:
   - Stale test data
   - Metering issues
   - Unaccounted production (theft, leaks)

### Method Selection

| Scenario | Recommended Method |
|----------|-------------------|
| Monthly accounting | WELL_TEST |
| Daily operations | VFM_BASED |
| High uncertainty | COMBINED |
| Simple fields | WELL_TEST |
| Complex subsea | VFM_BASED or COMBINED |

### Validation

```java
// Validate allocation results
if (Math.abs(result.getOilReconciliationError()) > 5.0) {
    logger.warn("High oil reconciliation error: {}%", 
        result.getOilReconciliationError());
}

// Check for negative allocations (indicates bad data)
for (String well : result.getWellNames()) {
    if (result.getAllocatedOilRate(well) < 0) {
        logger.error("Negative allocation for well: {}", well);
    }
}
```

---

## See Also

- [AI Platform Integration](../../integration/ai_platform_integration.md) - Full AI integration guide
- [Production Optimization](../../fielddevelopment/API_GUIDE.md) - Field development optimization
- [Wells](../wells.md) - Well equipment modeling
