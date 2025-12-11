# Parallel Process Simulation with NeqSimThreadPool

NeqSim provides a global thread pool for running multiple process simulations concurrently. This enables significant performance improvements when running independent simulations, sensitivity analyses, or optimization studies.

## Overview

The `NeqSimThreadPool` class provides a managed thread pool that:

- Uses daemon threads (won't prevent JVM shutdown)
- Defaults to using all available CPU cores
- Supports configurable pool size
- Provides both `Future<?>` and `Callable<T>` interfaces for flexible result handling

## Java Usage

### Basic Usage - Running Multiple Processes in Parallel

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.util.NeqSimThreadPool;
import java.util.concurrent.Future;
import java.util.List;
import java.util.ArrayList;

// Create multiple independent process systems
List<ProcessSystem> processes = new ArrayList<>();
for (int i = 0; i < 20; i++) {
    ProcessSystem process = createYourProcess(i);  // Your process setup
    processes.add(process);
}

// Submit all processes to run in parallel
List<Future<?>> futures = new ArrayList<>();
for (ProcessSystem process : processes) {
    Future<?> future = process.runAsTask();  // Non-blocking, returns immediately
    futures.add(future);
}

// Wait for all to complete
for (Future<?> future : futures) {
    future.get();  // Blocks until this task completes
}

// All processes are now complete - access results
for (ProcessSystem process : processes) {
    double result = process.getUnit("MySeparator").getOutletStream().getFlowRate("kg/hr");
    System.out.println("Result: " + result);
}
```

### Using Callable for Direct Result Return

```java
import neqsim.util.NeqSimThreadPool;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

// Define a task that returns a result
Callable<Double> simulationTask = () -> {
    ProcessSystem process = createProcess();
    process.run();
    return process.getUnit("Separator").getOutletStream().getFlowRate("kg/hr");
};

// Submit and get result
Future<Double> future = NeqSimThreadPool.submit(simulationTask);
Double flowRate = future.get();  // Returns the result directly
```

### Configuring the Thread Pool

```java
import neqsim.util.NeqSimThreadPool;

// Get current pool size (defaults to available processors)
int currentSize = NeqSimThreadPool.getPoolSize();

// Set custom pool size (e.g., for HPC clusters)
NeqSimThreadPool.setPoolSize(32);

// Reset to default (number of available processors)
NeqSimThreadPool.resetPoolSize();

// Shutdown pool when application exits (optional - uses daemon threads)
NeqSimThreadPool.shutdown();
```

### Complete Example - Sensitivity Analysis

```java
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.NeqSimThreadPool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

public class ParallelSensitivityAnalysis {
    
    public static void main(String[] args) throws Exception {
        // Define pressure range for sensitivity study
        double[] pressures = {10, 20, 30, 40, 50, 60, 70, 80};
        
        List<ProcessSystem> processes = new ArrayList<>();
        List<Future<?>> futures = new ArrayList<>();
        
        // Create and submit all processes
        for (int i = 0; i < pressures.length; i++) {
            ProcessSystem process = createProcess(i, pressures[i]);
            processes.add(process);
            futures.add(process.runAsTask());
        }
        
        // Wait for completion and collect results
        for (int i = 0; i < futures.size(); i++) {
            futures.get(i).get();
            
            ProcessSystem process = processes.get(i);
            Separator sep = (Separator) process.getUnit("Separator");
            double gasFlow = sep.getGasOutStream().getFlowRate("kg/hr");
            double liquidFlow = sep.getLiquidOutStream().getFlowRate("kg/hr");
            
            System.out.printf("P=%.0f bar: Gas=%.2f kg/hr, Liquid=%.2f kg/hr%n",
                pressures[i], gasFlow, liquidFlow);
        }
    }
    
    private static ProcessSystem createProcess(int id, double feedPressure) {
        SystemInterface fluid = new SystemSrkEos(298.15, feedPressure);
        fluid.addComponent("methane", 0.8);
        fluid.addComponent("ethane", 0.12);
        fluid.addComponent("propane", 0.05);
        fluid.addComponent("n-butane", 0.03);
        fluid.setMixingRule("classic");
        
        ProcessSystem process = new ProcessSystem();
        
        Stream feed = new Stream("Feed", fluid);
        feed.setFlowRate(1000.0, "kg/hr");
        feed.setTemperature(25.0, "C");
        feed.setPressure(feedPressure, "bara");
        
        ThrottlingValve valve = new ThrottlingValve("Valve", feed);
        valve.setOutletPressure(5.0);
        
        Separator separator = new Separator("Separator", valve.getOutletStream());
        
        process.add(feed);
        process.add(valve);
        process.add(separator);
        
        return process;
    }
}
```

## Python Usage (via JPype)

### Setup

```python
import jpype
import jpype.imports
from jpype.types import *

# Start JVM with NeqSim
neqsim_path = "/path/to/neqsim.jar"
jpype.startJVM(classpath=[neqsim_path])

# Import Java classes
from neqsim.process.processmodel import ProcessSystem
from neqsim.process.equipment.stream import Stream
from neqsim.process.equipment.separator import Separator
from neqsim.process.equipment.valve import ThrottlingValve
from neqsim.thermo.system import SystemSrkEos
from neqsim.util import NeqSimThreadPool
from java.util.concurrent import TimeUnit
```

### Basic Parallel Execution

```python
def create_process(process_id, pressure):
    """Create a simple process system."""
    fluid = SystemSrkEos(298.15, pressure)
    fluid.addComponent("methane", 0.8)
    fluid.addComponent("ethane", 0.12)
    fluid.addComponent("propane", 0.05)
    fluid.addComponent("n-butane", 0.03)
    fluid.setMixingRule("classic")
    
    process = ProcessSystem()
    process.setName(f"Process-{process_id}")
    
    feed = Stream(f"Feed-{process_id}", fluid)
    feed.setFlowRate(1000.0, "kg/hr")
    feed.setTemperature(25.0, "C")
    feed.setPressure(pressure, "bara")
    
    valve = ThrottlingValve(f"Valve-{process_id}", feed)
    valve.setOutletPressure(5.0)
    
    separator = Separator(f"Separator-{process_id}", valve.getOutletStream())
    
    process.add(feed)
    process.add(valve)
    process.add(separator)
    
    return process

# Create multiple processes
pressures = [20, 30, 40, 50, 60, 70, 80, 90]
processes = [create_process(i, p) for i, p in enumerate(pressures)]

# Submit all to thread pool
futures = [process.runAsTask() for process in processes]

# Wait for all to complete
for future in futures:
    future.get()  # Blocks until complete

# Collect results
results = []
for i, process in enumerate(processes):
    sep = process.getUnit(f"Separator-{i}")
    gas_flow = sep.getGasOutStream().getFlowRate("kg/hr")
    liquid_flow = sep.getLiquidOutStream().getFlowRate("kg/hr")
    results.append({
        'pressure': pressures[i],
        'gas_flow': gas_flow,
        'liquid_flow': liquid_flow
    })

# Display results
for r in results:
    print(f"P={r['pressure']} bar: Gas={r['gas_flow']:.2f}, Liquid={r['liquid_flow']:.2f} kg/hr")
```

### Using Callable for Direct Results

```python
from java.util.concurrent import Callable

# Create a Java Callable using JPype's @JImplements decorator
@jpype.JImplements(Callable)
class SimulationTask:
    def __init__(self, pressure):
        self.pressure = pressure
    
    @jpype.JOverride
    def call(self):
        process = create_process(0, self.pressure)
        process.run()
        sep = process.getUnit("Separator-0")
        return float(sep.getGasOutStream().getFlowRate("kg/hr"))

# Submit callable tasks
tasks = [SimulationTask(p) for p in [20, 40, 60, 80]]
futures = [NeqSimThreadPool.submit(task) for task in tasks]

# Get results directly
results = [future.get() for future in futures]
print("Gas flows:", results)
```

### Configuring Thread Pool from Python

```python
# Check default pool size
print(f"Default pool size: {NeqSimThreadPool.getDefaultPoolSize()}")
print(f"Current pool size: {NeqSimThreadPool.getPoolSize()}")

# Set custom pool size for HPC
NeqSimThreadPool.setPoolSize(64)

# Reset to default
NeqSimThreadPool.resetPoolSize()

# Check pool status
print(f"Pool shutdown: {NeqSimThreadPool.isShutdown()}")
print(f"Pool terminated: {NeqSimThreadPool.isTerminated()}")
```

### Advanced: Parallel Monte Carlo Simulation

```python
import random
import numpy as np

def run_monte_carlo(n_samples=100, n_parallel=20):
    """Run Monte Carlo simulation with parallel process execution."""
    
    results = []
    
    # Process in batches
    for batch_start in range(0, n_samples, n_parallel):
        batch_end = min(batch_start + n_parallel, n_samples)
        batch_size = batch_end - batch_start
        
        # Create processes with random parameters
        processes = []
        params = []
        for i in range(batch_size):
            pressure = random.uniform(20, 80)
            temperature = random.uniform(20, 40)
            params.append({'pressure': pressure, 'temperature': temperature})
            
            process = create_process_with_temp(i, pressure, temperature)
            processes.append(process)
        
        # Run batch in parallel
        futures = [p.runAsTask() for p in processes]
        for f in futures:
            f.get()
        
        # Collect results
        for i, process in enumerate(processes):
            sep = process.getUnit(f"Separator-{i}")
            results.append({
                **params[i],
                'gas_flow': sep.getGasOutStream().getFlowRate("kg/hr")
            })
    
    return results

# Run simulation
mc_results = run_monte_carlo(n_samples=200, n_parallel=20)

# Analyze results
gas_flows = [r['gas_flow'] for r in mc_results]
print(f"Mean gas flow: {np.mean(gas_flows):.2f} kg/hr")
print(f"Std deviation: {np.std(gas_flows):.2f} kg/hr")
```

### Timeout Handling

```python
from java.util.concurrent import TimeoutException

futures = [process.runAsTask() for process in processes]

for i, future in enumerate(futures):
    try:
        # Wait with timeout (60 seconds)
        future.get(60, TimeUnit.SECONDS)
    except TimeoutException:
        print(f"Process {i} timed out!")
        future.cancel(True)  # Cancel the task
```

## Performance Tips

1. **Pool Size**: The default pool size equals available CPU cores. For I/O-bound tasks, you may increase it. For CPU-intensive calculations, the default is usually optimal.

2. **Independent Processes**: Ensure each process has its own fluid system (use `clone()` or create new). Shared state between processes causes race conditions.

3. **Batch Processing**: For very large numbers of simulations (1000+), process in batches to manage memory:
   ```python
   batch_size = 50
   for i in range(0, n_total, batch_size):
       batch = create_batch(i, batch_size)
       run_parallel(batch)
       collect_results(batch)
       # batch goes out of scope, allowing GC
   ```

4. **Result Collection**: Collect results immediately after `future.get()` to allow process objects to be garbage collected.

## API Reference

### NeqSimThreadPool

| Method | Description |
|--------|-------------|
| `submit(Runnable task)` | Submit a task, returns `Future<?>` |
| `submit(Callable<T> task)` | Submit a task with result, returns `Future<T>` |
| `execute(Runnable task)` | Fire-and-forget execution |
| `getPool()` | Get the underlying `ExecutorService` |
| `getPoolSize()` | Get current pool size |
| `setPoolSize(int size)` | Set pool size (recreates pool if needed) |
| `getDefaultPoolSize()` | Get default size (available processors) |
| `resetPoolSize()` | Reset to default size |
| `shutdown()` | Orderly shutdown |
| `shutdownNow()` | Immediate shutdown |
| `shutdownAndAwait(timeout, unit)` | Shutdown and wait for completion |
| `isShutdown()` | Check if pool is shutdown |
| `isTerminated()` | Check if all tasks completed |

### ProcessSystem, ProcessModule, ProcessModel

| Method | Description |
|--------|-------------|
| `runAsTask()` | Submit to thread pool, returns `Future<?>` |
| `runAsThread()` | **Deprecated** - Creates unmanaged thread |

## Migration from runAsThread()

The `runAsThread()` method is now deprecated. Migrate as follows:

```java
// Old way (deprecated)
Thread thread = process.runAsThread();
thread.join();

// New way (recommended)
Future<?> future = process.runAsTask();
future.get();
```

Benefits of `runAsTask()`:
- Managed thread pool (no thread explosion)
- Better resource utilization
- `Future` API for cancellation and timeout
- Consistent with modern Java concurrency patterns
