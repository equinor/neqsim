---
title: Parallel Process Simulation with NeqSimThreadPool
description: NeqSim provides a global thread pool for running multiple process simulations concurrently. This enables significant performance improvements when running independent simulations, sensitivity analyses...
---

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

### Reporting Results in Completion Order

When running many simulations, you may want to process results as they finish rather than waiting for all to complete. Use the built-in `newCompletionService()` method:

```java
import java.util.concurrent.CompletionService;

// Create CompletionService using the convenience method
CompletionService<Integer> completionService = NeqSimThreadPool.newCompletionService();

// Submit all processes, returning their index when done
for (int i = 0; i < processes.size(); i++) {
    final int index = i;
    final ProcessSystem process = processes.get(i);
    completionService.submit(() -> {
        process.run();
        return index;  // Return the index so we know which one completed
    });
}

// Process results as they complete
for (int i = 0; i < numProcesses; i++) {
    // take() blocks until the next result is available
    Future<Integer> completedFuture = completionService.take();
    int completedIndex = completedFuture.get();
    
    ProcessSystem process = processes.get(completedIndex);
    Separator sep = (Separator) process.getUnit("Separator");
    double gasFlow = sep.getGasOutStream().getFlowRate("kg/hr");
    
    System.out.printf("Process %d completed: gas flow = %.2f kg/hr%n", 
        completedIndex, gasFlow);
}
```

### Polling for Completion (Non-blocking)

For non-blocking checks, poll futures with `isDone()`:

```java
boolean[] reported = new boolean[numProcesses];
int completedCount = 0;

while (completedCount < numProcesses) {
    for (int i = 0; i < numProcesses; i++) {
        if (!reported[i] && futures.get(i).isDone()) {
            // This one just completed
            ProcessSystem process = processes.get(i);
            System.out.printf("Process %d completed!%n", i);
            
            reported[i] = true;
            completedCount++;
        }
    }
    
    // Do other work here while waiting...
    Thread.sleep(10);
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

### Reporting Results in Completion Order (Python)

Use the `newCompletionService()` method to get results as they finish:

```python
from java.util.concurrent import Callable

# Create CompletionService using the convenience method
completion_service = NeqSimThreadPool.newCompletionService()

# Submit tasks that return their index when complete
@jpype.JImplements(Callable)
class IndexedSimulation:
    def __init__(self, index, process):
        self.index = index
        self.process = process
    
    @jpype.JOverride
    def call(self):
        self.process.run()
        return self.index

# Submit all processes
for i, process in enumerate(processes):
    completion_service.submit(IndexedSimulation(i, process))

# Get results in completion order
print("Results in completion order:")
for _ in range(len(processes)):
    completed_future = completion_service.take()  # Blocks until next completes
    index = completed_future.get()
    
    process = processes[index]
    sep = process.getUnit(f"Separator-{index}")
    gas_flow = sep.getGasOutStream().getFlowRate("kg/hr")
    
    print(f"  Process {index} completed: gas flow = {gas_flow:.2f} kg/hr")
```

### Polling for Completion (Python)

```python
# Track completion
reported = [False] * len(processes)
completed = 0

while completed < len(processes):
    for i, future in enumerate(futures):
        if not reported[i] and future.isDone():
            process = processes[i]
            sep = process.getUnit(f"Separator-{i}")
            gas_flow = sep.getGasOutStream().getFlowRate("kg/hr")
            
            print(f"Process {i} completed: gas flow = {gas_flow:.2f} kg/hr")
            
            reported[i] = True
            completed += 1
    
    # Do other work while waiting...
    import time
    time.sleep(0.01)
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
| `newCompletionService()` | Create a `CompletionService<T>` for completion-order results |
| `getPool()` | Get the underlying `ExecutorService` |
| `getPoolSize()` | Get current pool size |
| `setPoolSize(int size)` | Set pool size (recreates pool if needed) |
| `getDefaultPoolSize()` | Get default size (available processors) |
| `resetPoolSize()` | Reset to default size |
| `setMaxQueueCapacity(int)` | Set bounded queue capacity (0 = unbounded) |
| `getMaxQueueCapacity()` | Get current queue capacity setting |
| `setAllowCoreThreadTimeout(boolean)` | Enable/disable idle thread termination |
| `isAllowCoreThreadTimeout()` | Check if core thread timeout is enabled |
| `setKeepAliveTimeSeconds(long)` | Set idle thread keep-alive time |
| `getKeepAliveTimeSeconds()` | Get current keep-alive time |
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

## Advanced Configuration

### Bounded Queue for HPC

For extreme load scenarios (HPC clusters with thousands of simulations), you can limit the queue size to prevent memory exhaustion:

```java
// Set bounded queue with 10,000 task capacity
NeqSimThreadPool.setMaxQueueCapacity(10_000);

// Submit tasks - will throw RejectedExecutionException if queue overflows
try {
    for (int i = 0; i < numSimulations; i++) {
        process.runAsTask();
    }
} catch (RejectedExecutionException e) {
    System.err.println("Queue full - consider reducing batch size");
}

// Reset to unbounded queue
NeqSimThreadPool.setMaxQueueCapacity(0);
```

### Core Thread Timeout for Long-Running Processes

By default, threads in the pool stay alive forever waiting for new tasks. For long-running Python processes or memory-constrained environments, you can enable core thread timeout so idle threads are terminated after a period of inactivity:

```java
// Enable core thread timeout (threads die after being idle)
NeqSimThreadPool.setAllowCoreThreadTimeout(true);

// Optionally set custom keep-alive time (default is 600 seconds = 10 minutes)
NeqSimThreadPool.setKeepAliveTimeSeconds(300);  // 5 minutes

// Now idle threads will be terminated after 5 minutes
// This frees memory when the pool is not in use
```

Python example for long-running services:

```python
from neqsim.util import NeqSimThreadPool

# Enable core thread timeout for memory efficiency in long-running processes
NeqSimThreadPool.setAllowCoreThreadTimeout(True)
NeqSimThreadPool.setKeepAliveTimeSeconds(300)  # 5 minutes

# Now use the pool normally - idle threads will be cleaned up automatically
futures = [process.runAsTask() for process in batch]
for future in futures:
    future.get()

# After 5 minutes of no activity, all threads will be terminated
# New tasks will create new threads as needed
```

### Exception Handling

The thread pool includes an `UncaughtExceptionHandler` that logs any exceptions that escape thread execution. This prevents silent failures during simulations:

```java
// Exceptions are logged automatically
Future<?> future = NeqSimThreadPool.submit(() -> {
    // If this throws, it will be logged AND captured in the Future
    riskyOperation();
});

// Check for exceptions
try {
    future.get();
} catch (ExecutionException e) {
    System.err.println("Simulation failed: " + e.getCause().getMessage());
}
```

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
- Automatic exception logging

## Performance Comparison: runAsThread() vs runAsTask()

Benchmark running 20 process simulations across 3 iterations:

| Metric | runAsThread() | runAsTask() |
|--------|---------------|-------------|
| Run 1 (cold start) | 50 ms | 10 ms |
| Run 2 | 6 ms | 4 ms |
| Run 3 | 5 ms | 4 ms |
| **Average** | **20.3 ms** | **6.0 ms** |
| **Improvement** | - | **70% faster** |

### Why runAsTask() is Faster

1. **Thread reuse**: The thread pool creates threads once and reuses them, eliminating thread creation overhead on subsequent calls.

2. **Cold start**: The first run shows the biggest difference (50ms vs 10ms) because `runAsThread()` must create 20 new threads, while `runAsTask()` creates pool threads once.

3. **Bounded resources**: With 1000+ processes, `runAsThread()` would create 1000+ threads (potentially crashing), while `runAsTask()` queues tasks safely.

### API Comparison

| Feature | runAsThread() | runAsTask() |
|---------|---------------|-------------|
| Return type | `Thread` | `Future<?>` |
| Wait for completion | `thread.join()` | `future.get()` |
| Timeout support | Manual implementation | `future.get(timeout, unit)` |
| Cancellation | `thread.interrupt()` | `future.cancel(true)` |
| Check completion | `thread.isAlive()` | `future.isDone()` |
| Exception handling | Uncaught by default | Captured in Future + logged |
| Thread management | Unbounded (dangerous) | Bounded pool (safe) |

### Code Example

```java
// OLD WAY (deprecated) - creates new thread each time
List<Thread> threads = new ArrayList<>();
for (ProcessSystem process : processes) {
    Thread t = process.runAsThread();
    threads.add(t);
}
for (Thread t : threads) {
    t.join();  // No timeout support
}

// NEW WAY (recommended) - uses managed thread pool
List<Future<?>> futures = new ArrayList<>();
for (ProcessSystem process : processes) {
    Future<?> future = process.runAsTask();
    futures.add(future);
}
for (Future<?> future : futures) {
    future.get(60, TimeUnit.SECONDS);  // Built-in timeout
}
```
