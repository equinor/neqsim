# Parallel Property Flash Calculations

## Overview

NeqSim provides parallel execution capabilities for property flash calculations, enabling efficient processing of large datasets by leveraging multiple CPU cores. This is particularly useful for:

- Processing time-series sensor data from real-time systems
- Running Monte Carlo simulations with varying conditions
- Batch processing of thermodynamic calculations for optimization studies
- High-throughput property table generation

## Methods

### `propertyFlashParallel`

Performs flash calculations in parallel across multiple CPU cores. Each calculation uses a cloned copy of the thermodynamic system to ensure thread safety.

#### Signature (Auto-detect threads)

```java
public CalculationResult propertyFlashParallel(
    List<Double> Spec1,           // Pressures in bar absolute
    List<Double> Spec2,           // Second specification (T, H, or S)
    int FlashMode,                // 1=PT, 2=PH, 3=PS
    List<String> components,      // Component names (null for static composition)
    List<List<Double>> onlineFractions  // Per-point compositions (null for static)
)
```

#### Signature (Custom thread count)

```java
public CalculationResult propertyFlashParallel(
    List<Double> Spec1,
    List<Double> Spec2,
    int FlashMode,
    List<String> components,
    List<List<Double>> onlineFractions,
    int numThreads                // Number of threads (0 = auto-detect)
)
```

### `propertyFlashBatch`

Processes calculations in batches to optimize memory usage for very large datasets. Internally uses `propertyFlashParallel` for each batch.

```java
public CalculationResult propertyFlashBatch(
    List<Double> Spec1,
    List<Double> Spec2,
    int FlashMode,
    List<String> components,
    List<List<Double>> onlineFractions,
    int batchSize                 // Points per batch (0 = auto-size)
)
```

## Flash Modes

| Mode | Type | Spec1 | Spec2 | Units |
|------|------|-------|-------|-------|
| 1 | PT | Pressure | Temperature | bar, K |
| 2 | PH | Pressure | Enthalpy | bar, J/mol |
| 3 | PS | Pressure | Entropy | bar, J/(mol·K) |

## Return Value: `CalculationResult`

```java
public class CalculationResult {
    public Double[][] fluidProperties;  // [numPoints][numProperties]
    public String[] calculationError;   // Error message per point (null if success)
}
```

The `fluidProperties` array contains all calculated thermodynamic properties for each input point.

### Property Indices

The `fluidProperties[i]` array contains values at these indices:

| Index | Property | Units |
|-------|----------|-------|
| 0 | Number of Phases | - |
| 1 | Pressure | Pa |
| 2 | Temperature | K |
| 3 | Mole Percent | % |
| 4 | Weight Percent | % |
| 5 | Molar Volume | m³/mol |
| 6 | Volume Percent | % |
| 7 | Density | kg/m³ |
| 8 | Z Factor | - |
| 9 | Molecular Weight | g/mol |
| 10 | Enthalpy | J/mol |
| 11 | Entropy | J/(mol·K) |
| 12 | Heat Capacity Cp | J/(mol·K) |
| 13 | Heat Capacity Cv | J/(mol·K) |
| 14 | Kappa (Cp/Cv) | - |
| 15 | JT Coefficient | K/Pa (may be NaN) |
| 16 | Sound Velocity | m/s (may be NaN) |
| 17 | Viscosity | Pa·s |
| 18 | Thermal Conductivity | W/(m·K) |
| 19+ | Phase-specific properties | (gas, oil, aqueous) |

Phase-specific properties (indices 19+) follow the same pattern for each phase (gas, oil, aqueous), with 16 properties per phase.

## Usage Examples

### Example 1: Basic Parallel PT Flash (Java)

```java
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.api.ioc.CalculationResult;
import java.util.ArrayList;
import java.util.List;

// Create fluid system
SystemInterface gas = new SystemSrkEos(273.15, 10.0);
gas.addComponent("methane", 0.85);
gas.addComponent("ethane", 0.10);
gas.addComponent("propane", 0.05);
gas.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(gas);

// Prepare 100 pressure-temperature points
List<Double> pressures = new ArrayList<>();
List<Double> temperatures = new ArrayList<>();
for (int i = 0; i < 100; i++) {
    pressures.add(10.0 + i * 0.5);      // 10 to 60 bar
    temperatures.add(273.15 + i * 1.0); // 273 to 373 K
}

// Run parallel calculations using all available CPU cores
CalculationResult result = ops.propertyFlashParallel(
    pressures, temperatures, 1, null, null
);

// Process results
for (int i = 0; i < result.fluidProperties.length; i++) {
    if (result.calculationError[i] == null) {
        System.out.printf("P=%.1f bar, T=%.1f K: density=%.4f kg/m3%n",
            pressures.get(i), temperatures.get(i),
            result.fluidProperties[i][7]);  // Index 7 is density
    }
}
```

### Example 2: Specifying Thread Count

```java
// Use exactly 4 threads
CalculationResult result = ops.propertyFlashParallel(
    pressures, temperatures, 1, null, null, 4
);

// Use single thread (sequential execution)
CalculationResult resultSeq = ops.propertyFlashParallel(
    pressures, temperatures, 1, null, null, 1
);
```

### Example 3: PH Flash with Enthalpy Specifications

```java
List<Double> pressures = new ArrayList<>();
List<Double> enthalpies = new ArrayList<>();

// Generate enthalpy values at known conditions
for (int i = 0; i < 50; i++) {
    double P = 20.0 + i;
    double T = 300.0 + i * 2;
    
    SystemInterface tempFluid = gas.clone();
    tempFluid.setPressure(P);
    tempFluid.setTemperature(T);
    ThermodynamicOperations tempOps = new ThermodynamicOperations(tempFluid);
    tempOps.TPflash();
    tempFluid.init(2);
    
    pressures.add(P);
    enthalpies.add(tempFluid.getEnthalpy("J/mol"));
}

// Run parallel PH flash
CalculationResult result = ops.propertyFlashParallel(
    pressures, enthalpies, 2, null, null
);
```

### Example 4: Varying Compositions per Point

```java
int numPoints = 50;
List<Double> pressures = new ArrayList<>();
List<Double> temperatures = new ArrayList<>();
List<String> components = Arrays.asList("methane", "ethane", "propane");
List<List<Double>> onlineFractions = new ArrayList<>();

// Initialize fraction lists
List<Double> methaneFracs = new ArrayList<>();
List<Double> ethaneFracs = new ArrayList<>();
List<Double> propaneFracs = new ArrayList<>();

for (int i = 0; i < numPoints; i++) {
    pressures.add(20.0 + i * 2.0);
    temperatures.add(300.0 + i * 2.0);
    
    // Varying compositions
    double methane = 0.80 + 0.002 * i;
    double ethane = 0.15 - 0.001 * i;
    double propane = 1.0 - methane - ethane;
    
    methaneFracs.add(methane);
    ethaneFracs.add(ethane);
    propaneFracs.add(propane);
}

onlineFractions.add(methaneFracs);
onlineFractions.add(ethaneFracs);
onlineFractions.add(propaneFracs);

CalculationResult result = ops.propertyFlashParallel(
    pressures, temperatures, 1, components, onlineFractions
);
```

### Example 5: Batch Processing for Very Large Datasets

```java
// For 10,000 points, process in batches of 500
int numPoints = 10000;
List<Double> pressures = new ArrayList<>();
List<Double> temperatures = new ArrayList<>();

for (int i = 0; i < numPoints; i++) {
    pressures.add(10.0 + (i % 50) * 1.0);
    temperatures.add(273.15 + (i / 50) * 1.0);
}

// Batch processing reduces peak memory usage
CalculationResult result = ops.propertyFlashBatch(
    pressures, temperatures, 1, null, null, 500
);
```

## Performance Considerations

### Thread Count Optimization

- **Default (0 or negative)**: Uses `Runtime.getRuntime().availableProcessors()`
- **For I/O-bound workloads**: May benefit from 2x CPU cores
- **For CPU-bound workloads**: Usually optimal at CPU core count
- **Single-threaded mode (1)**: Useful for debugging or when parallel overhead exceeds benefit

### Memory Usage

Each parallel calculation clones the thermodynamic system, which increases memory usage proportionally to thread count. For very large datasets:

1. Use `propertyFlashBatch` to limit concurrent calculations
2. Reduce batch size if memory is constrained
3. Consider processing data in chunks

### Speedup Expectations

Theoretical maximum speedup equals the number of CPU cores, but actual speedup depends on:

- Flash calculation complexity (CPA/GERG models take longer)
- System cloning overhead
- Memory bandwidth limitations
- JVM warm-up and GC pauses

Typical observed speedups:

| CPU Cores | Expected Speedup |
|-----------|-----------------|
| 4 | 2.5x - 3.5x |
| 8 | 4x - 6x |
| 16 | 6x - 10x |
| 32+ | 8x - 15x |

## Error Handling

The parallel implementation handles errors gracefully:

- Each calculation point is independent
- Errors at one point don't affect others
- Error messages are stored in `calculationError[i]`
- `null` error indicates successful calculation

```java
for (int i = 0; i < result.fluidProperties.length; i++) {
    if (result.calculationError[i] != null) {
        System.err.println("Error at point " + i + ": " + result.calculationError[i]);
    } else {
        // Process successful result
        processResult(result.fluidProperties[i]);
    }
}
```

## Thread Safety

The parallel implementation ensures thread safety by:

1. **Cloning systems**: Each thread works with its own copy of `SystemInterface`
2. **Independent operations**: Each `ThermodynamicOperations` instance is thread-local
3. **Immutable inputs**: Input lists are not modified
4. **Atomic result collection**: Results are safely aggregated via `CompletionService`

## Integration with NeqSimThreadPool

For advanced use cases, you can configure the global thread pool before running parallel flashes:

```java
import neqsim.util.NeqSimThreadPool;

// Configure thread pool before first use
NeqSimThreadPool.setPoolSize(8);
NeqSimThreadPool.setAllowCoreThreadTimeout(true);
NeqSimThreadPool.setKeepAliveTime(300, TimeUnit.SECONDS);

// Now parallel methods will use the configured pool
CalculationResult result = ops.propertyFlashParallel(pressures, temperatures, 1, null, null);
```

Note: The `propertyFlashParallel` method creates its own executor for each call to ensure clean shutdown, but you can modify the implementation to use `NeqSimThreadPool` for shared pool management.

## Python Integration via JPype

See the Jupyter notebook example for using parallel flash calculations from Python:

```python
from neqsim import jneqsim
from java.util import ArrayList

# Create fluid
gas = jneqsim.thermo.system.SystemSrkEos(273.15, 10.0)
gas.addComponent("methane", 0.85)
gas.addComponent("ethane", 0.10)
gas.addComponent("propane", 0.05)
gas.setMixingRule("classic")

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(gas)

# Create Java ArrayList for inputs
pressures = ArrayList()
temperatures = ArrayList()
for i in range(100):
    pressures.add(float(10.0 + i * 0.5))
    temperatures.add(float(273.15 + i))

# Run parallel calculation
result = ops.propertyFlashParallel(pressures, temperatures, 1, None, None)

# Access results
for i in range(len(result.fluidProperties)):
    if result.calculationError[i] is None:
        print(f"Point {i}: density = {result.fluidProperties[i][7]} kg/m3")  # Index 7
```

## Best Practices

1. **Use batch processing** for datasets > 1000 points to manage memory
2. **Validate inputs** before calling - check for NaN/null values
3. **Handle errors** for each point independently
4. **Warm up JVM** for benchmarking - run a few calculations before timing
5. **Profile your workload** to find optimal thread count for your hardware

## Large-Scale Processing (Millions of Calculations)

For very large datasets (millions to billions of calculations), use a streaming/chunked approach to manage memory efficiently.

### Key Considerations

| Factor | Recommendation |
|--------|----------------|
| **Memory** | 100M results × 70 properties × 8 bytes ≈ 56 GB - won't fit in memory |
| **Processing** | Use batch processing with parallel execution per batch |
| **I/O** | Write results to disk incrementally, don't accumulate in memory |
| **JVM Heap** | Set `-Xmx16g` or higher via `JAVA_OPTS` environment variable |

### Python Example: Processing 100 Million Points

```python
import numpy as np
import h5py  # or use parquet/csv for output
from neqsim import jneqsim

# Configuration
TOTAL_POINTS = 100_000_000
BATCH_SIZE = 10_000  # Process 10K points at a time
OUTPUT_FILE = "flash_results.h5"

# Create fluid once
gas = jneqsim.thermo.system.SystemSrkEos(273.15 + 25.0, 50.0)
gas.addComponent("methane", 0.85)
gas.addComponent("ethane", 0.10)
gas.addComponent("propane", 0.05)
gas.setMixingRule("classic")
ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(gas)

# Pre-allocate HDF5 file for results (memory-mapped)
num_properties = 70  # SystemProperties.nCols
with h5py.File(OUTPUT_FILE, 'w') as f:
    results_ds = f.create_dataset('properties', 
                                   shape=(TOTAL_POINTS, num_properties),
                                   dtype='float64',
                                   chunks=(BATCH_SIZE, num_properties))
    errors_ds = f.create_dataset('errors',
                                  shape=(TOTAL_POINTS,),
                                  dtype=h5py.string_dtype())

# Process in batches
num_batches = TOTAL_POINTS // BATCH_SIZE

for batch_idx in range(num_batches):
    start_idx = batch_idx * BATCH_SIZE
    end_idx = start_idx + BATCH_SIZE
    
    # Generate or load your input data for this batch
    pressures = list(np.random.uniform(10.0, 100.0, BATCH_SIZE))
    temperatures = list(np.random.uniform(250.0, 400.0, BATCH_SIZE))
    
    # Run parallel flash for this batch
    result = ops.propertyFlashParallel(pressures, temperatures, 1, None, None)
    
    # Write results to disk immediately
    with h5py.File(OUTPUT_FILE, 'a') as f:
        for i in range(BATCH_SIZE):
            if result.calculationError[i] is None:
                f['properties'][start_idx + i] = result.fluidProperties[i]
            f['errors'][start_idx + i] = result.calculationError[i] or ""
    
    # Progress reporting
    if batch_idx % 100 == 0:
        pct = (batch_idx + 1) / num_batches * 100
        print(f"Progress: {pct:.1f}% ({start_idx:,} / {TOTAL_POINTS:,})")
```

### Performance Optimizations

| Optimization | Recommendation |
|-------------|----------------|
| **Batch size** | 10,000 - 50,000 points per batch (balance memory vs overhead) |
| **Thread count** | Use default (all cores) or specify `numThreads = CPU_cores` |
| **JVM heap** | Set `-Xmx16g` or higher via `JAVA_OPTS` |
| **Output format** | HDF5 or Parquet (columnar, compressed) |
| **Input data** | Stream from file, don't pre-generate all points in memory |

### Estimated Execution Time

With a 16-core CPU and ~0.1ms per flash calculation:

| Configuration | Time for 100M Points |
|--------------|---------------------|
| Sequential (1 core) | ~2,778 hours (116 days) |
| Parallel (16 cores, ~4x speedup) | ~700 hours (29 days) |
| Parallel (64 cores, ~10x speedup) | ~280 hours (12 days) |

### Scaling Beyond a Single Machine

For truly massive scale, consider:

1. **Distributed computing** - Use Apache Spark or Dask to distribute across multiple machines
2. **Cloud computing** - Spin up multiple VMs on Azure/AWS for parallel processing
3. **Pre-computed lookup tables** - Generate property tables and use interpolation for real-time queries
4. **Reduced property set** - If you only need a few properties, modify the code to skip unnecessary calculations

