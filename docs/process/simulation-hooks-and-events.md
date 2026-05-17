---
title: "Simulation Hooks, Events, and Auto-Validation"
description: "Guide to NeqSim's lifecycle hooks for ProcessSystem and ProcessModel — progress listeners, event bus integration, and auto-validation. Covers real-time monitoring, digital twin dashboards, Jupyter callbacks, and debugging convergence."
---

# Simulation Hooks, Events, and Auto-Validation

NeqSim provides three opt-in observability mechanisms for process simulations:

1. **Progress Listeners** — typed callback interfaces for fine-grained lifecycle monitoring
2. **ProcessEventBus** — publish-subscribe event bus for decoupled, cross-cutting event delivery
3. **Auto-Validation** — pre-run equipment/area validation with logged warnings

All three are **zero-overhead when disabled** (the default). Existing code is unaffected — no behavioral changes unless you explicitly opt in.

---

## Why Use Hooks?

| Use Case | Mechanism | Benefit |
|----------|-----------|---------|
| Jupyter live-plotting during simulation | `SimulationProgressListener` | See temperature, pressure, duty update after each unit |
| Digital twin dashboards | `ProcessEventBus` | Decouple UI from simulation engine — multiple subscribers |
| Convergence debugging | `ModelProgressListener` | Track iteration errors for multi-area `ProcessModel` |
| Early error detection | Auto-validation | Catch missing streams, unset parameters before run |
| AI/ML integration | `ProcessEventBus` | Feed real-time simulation events to optimization agents |
| Audit logging | `ProcessEventBus` | Record all simulation events with timestamps |
| CI/CD pipeline monitoring | Both | Assert convergence properties in automated tests |

---

## 1. ProcessSystem — SimulationProgressListener

`ProcessSystem.SimulationProgressListener` is a callback interface invoked during the execution of a single process flowsheet. It fires at the unit-operation level.

### Interface Methods

| Method | When It Fires | Default |
|--------|--------------|---------|
| `onSimulationStart(totalUnits)` | Once, before the first iteration | no-op |
| `onBeforeIteration(iterationNumber)` | Before each pass through units | no-op |
| `onBeforeUnit(unit, unitIndex, totalUnits, iterationNumber)` | Before each unit runs | no-op |
| `onUnitComplete(unit, unitIndex, totalUnits, iterationNumber)` | After each unit runs | **required** |
| `onIterationComplete(iterationNumber, converged, recycleError)` | After each full pass | no-op |
| `onSimulationComplete(totalIterations, converged)` | Once, after all iterations | no-op |
| `onUnitError(unit, exception)` | When a unit throws | returns `false` (abort) |

Only `onUnitComplete` is abstract — all other methods have safe defaults, so you only override what you need.

### Execution Order

```
onSimulationStart(totalUnits)
  │
  ├─ onBeforeIteration(1)
  │     ├─ onBeforeUnit(feed, 0, N, 1)
  │     │   └─ onUnitComplete(feed, 0, N, 1)
  │     ├─ onBeforeUnit(separator, 1, N, 1)
  │     │   └─ onUnitComplete(separator, 1, N, 1)
  │     └─ ...
  │     └─ onIterationComplete(1, converged, error)
  │
  ├─ onBeforeIteration(2)     ← only if recycles not converged
  │     └─ ...
  │
  └─ onSimulationComplete(totalIterations, converged)
```

### Java Example

```java
ProcessSystem process = new ProcessSystem("Gas Train");
// ... add equipment ...

process.setProgressListener(new ProcessSystem.SimulationProgressListener() {
    @Override
    public void onUnitComplete(ProcessEquipmentInterface unit,
            int unitIndex, int totalUnits, int iterationNumber) {
        System.out.printf("  [%d/%d] %s completed%n",
            unitIndex + 1, totalUnits, unit.getName());
    }

    @Override
    public void onIterationComplete(int iterationNumber,
            boolean converged, double recycleError) {
        System.out.printf("Iteration %d: converged=%b, error=%.2e%n",
            iterationNumber, converged, recycleError);
    }

    @Override
    public void onSimulationComplete(int totalIterations, boolean converged) {
        System.out.printf("Simulation done in %d iterations (converged=%b)%n",
            totalIterations, converged);
    }
});

process.run();  // Hooks fire automatically
```

### Python / Jupyter Example

```python
from neqsim import jneqsim

ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
Separator = jneqsim.process.equipment.separator.Separator
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos

# Build a simple process
fluid = SystemSrkEos(273.15 + 25.0, 50.0)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)
fluid.setMixingRule("classic")

feed = Stream("feed", fluid)
feed.setFlowRate(5000.0, "kg/hr")
sep = Separator("HP sep", feed)

process = ProcessSystem("Example")
process.add(feed)
process.add(sep)

# Option A: Full listener (implement the Java interface)
class MyListener(ProcessSystem.SimulationProgressListener):
    def onUnitComplete(self, unit, index, total, iteration):
        print(f"  [{index+1}/{total}] {unit.getName()} done")

    def onSimulationComplete(self, totalIter, converged):
        print(f"Done in {totalIter} iterations, converged={converged}")

process.setProgressListener(MyListener())
process.run()

# Option B: Simple callback (convenience method)
def on_complete(unit):
    temp = unit.getOutletStream().getTemperature("C")
    print(f"{unit.getName()}: T_out = {temp:.1f} °C")

process.runWithCallback(on_complete)
```

### Wired Execution Paths

The listener fires in **all** ProcessSystem execution methods:

| Method | Description |
|--------|-------------|
| `runWithProgress(id)` | Full progress monitoring with callbacks |
| `runSequential(id)` | Sequential unit execution |
| `runParallel(executionPlan, id)` | Parallel/level-based execution |
| `runTransient(dt, id)` | Dynamic/transient simulation |

Regular `run()` delegates to the optimal path automatically.

---

## 2. ProcessModel — ModelProgressListener

`ProcessModel.ModelProgressListener` is a callback interface for monitoring multi-area plant models. It fires at the process-area level during the outer convergence loop.

### Interface Methods

| Method | When It Fires | Default |
|--------|--------------|---------|
| `onModelStart(totalAreas)` | Once, before the first iteration | no-op |
| `onBeforeIteration(iterationNumber)` | Before each outer iteration | no-op |
| `onBeforeProcessArea(areaName, process, areaIndex, totalAreas, iterationNumber)` | Before running an area | no-op |
| `onProcessAreaComplete(areaName, process, areaIndex, totalAreas, iterationNumber)` | After running an area | **required** |
| `onIterationComplete(iterationNumber, converged, maxError)` | After each outer iteration | no-op |
| `onModelComplete(totalIterations, converged)` | Once, after model finishes | no-op |
| `onProcessAreaError(areaName, process, exception)` | When an area throws | returns `false` (abort) |

Only `onProcessAreaComplete` is abstract.

### Execution Order

```
onModelStart(totalAreas)
  │
  ├─ onBeforeIteration(1)
  │     ├─ onBeforeProcessArea("Separation", proc, 0, N, 1)
  │     │   └─ onProcessAreaComplete("Separation", proc, 0, N, 1)
  │     ├─ onBeforeProcessArea("Compression", proc, 1, N, 1)
  │     │   └─ onProcessAreaComplete("Compression", proc, 1, N, 1)
  │     └─ onIterationComplete(1, converged, maxError)
  │
  ├─ onBeforeIteration(2)     ← if not converged
  │     └─ ...
  │
  └─ onModelComplete(totalIterations, converged)
```

### Java Example

```java
ProcessModel plant = new ProcessModel();
plant.add("separation", separationProcess);
plant.add("compression", compressionProcess);
plant.add("dehydration", dehydrationProcess);

plant.setProgressListener(new ProcessModel.ModelProgressListener() {
    @Override
    public void onProcessAreaComplete(String areaName, ProcessSystem process,
            int areaIndex, int totalAreas, int iterationNumber) {
        System.out.printf("  Area '%s' completed (%d/%d) [iter %d]%n",
            areaName, areaIndex + 1, totalAreas, iterationNumber);
    }

    @Override
    public void onIterationComplete(int iterationNumber,
            boolean converged, double maxError) {
        System.out.printf("Outer iteration %d: converged=%b, error=%.2e%n",
            iterationNumber, converged, maxError);
    }

    @Override
    public void onModelComplete(int totalIterations, boolean converged) {
        System.out.printf("Plant model: %d iterations, converged=%b%n",
            totalIterations, converged);
    }
});

plant.run();
```

### Python / Jupyter Example

```python
ProcessModel = jneqsim.process.processmodel.ProcessModel

plant = ProcessModel()
plant.add("separation", sep_process)
plant.add("compression", comp_process)

class PlantListener(ProcessModel.ModelProgressListener):
    def onProcessAreaComplete(self, name, proc, idx, total, iteration):
        print(f"  [{idx+1}/{total}] Area '{name}' done (iter {iteration})")

    def onIterationComplete(self, iterNum, converged, maxErr):
        print(f"Iteration {iterNum}: error={maxErr:.2e}, converged={converged}")

    def onModelComplete(self, totalIter, converged):
        print(f"Plant converged={converged} in {totalIter} iterations")

plant.setProgressListener(PlantListener())
plant.run()
```

### Step Mode vs Continuous Mode

Both modes fire hooks:

- **Continuous mode** (default): convergence loop with `onBeforeIteration`/`onIterationComplete` for each pass
- **Step mode** (`setRunStep(true)`): runs each area once, fires `onBeforeProcessArea`/`onProcessAreaComplete` for each area

```java
plant.setRunStep(true);  // Single pass, still fires all hooks
plant.run();
```

---

## 3. ProcessEventBus — Decoupled Event Delivery

The `ProcessEventBus` is a singleton publish-subscribe event bus. Unlike listeners (which are 1:1), the event bus supports **multiple subscribers** and **type-based filtering**.

### Enabling Events

Events are **not published by default**. Opt in on each object:

```java
// ProcessSystem events
process.setPublishEvents(true);

// ProcessModel events (separate flag)
plant.setPublishEvents(true);
```

### Event Types

| EventType | Published When |
|-----------|---------------|
| `INFO` | Simulation start, area start |
| `SIMULATION_COMPLETE` | Simulation/model finished |
| `WARNING` | Model didn't converge, validation warning |
| `ERROR` | Area or unit threw an exception |

Each event carries:
- `eventId` — unique identifier
- `type` — EventType enum
- `source` — `"ProcessSystem"` or `"ProcessModel"`
- `description` — human-readable message
- `severity` — DEBUG / INFO / WARNING / ERROR / CRITICAL
- `timestamp` — `Instant` when the event was created
- `properties` — arbitrary key-value metadata map

### Subscribing to Events

```java
ProcessEventBus bus = ProcessEventBus.getInstance();

// Subscribe to ALL events
bus.subscribe(event -> {
    System.out.println(event.getSource() + ": " + event.getDescription());
});

// Subscribe to specific type only
bus.subscribe(ProcessEvent.EventType.ERROR, event -> {
    logger.error("Simulation error: " + event.getDescription());
});

// Subscribe to severity-based filter
bus.subscribe(ProcessEvent.Severity.WARNING, event -> {
    // Handle warnings and above
});
```

### Unsubscribing

```java
ProcessEventListener myListener = event -> { /* ... */ };
bus.subscribe(myListener);

// Later:
bus.unsubscribe(myListener);

// Or unsubscribe from specific type:
bus.unsubscribe(ProcessEvent.EventType.ERROR, myListener);
```

### Event History

The bus maintains a configurable history of recent events:

```java
bus.setMaxHistorySize(500);  // default 1000
List<ProcessEvent> history = bus.getEventHistory();
bus.clearHistory();
```

### Python Example

```python
from neqsim import jneqsim
ProcessEventBus = jneqsim.process.util.event.ProcessEventBus
ProcessEventListener = jneqsim.process.util.event.ProcessEventListener

bus = ProcessEventBus.getInstance()

class MyEventHandler(ProcessEventListener):
    def onEvent(self, event):
        print(f"[{event.getSeverity()}] {event.getSource()}: {event.getDescription()}")

handler = MyEventHandler()
bus.subscribe(handler)

process.setPublishEvents(True)
process.run()

bus.unsubscribe(handler)
```

### Thread Safety

`ProcessEventBus` uses `CopyOnWriteArrayList` for listener storage, making it safe for concurrent subscribe/unsubscribe from multiple threads. Event delivery is synchronous by default; enable asynchronous delivery with:

```java
bus.setAsyncDelivery(true);  // Events delivered on background thread
```

---

## 4. Auto-Validation

Pre-run validation catches configuration errors (missing streams, unset parameters, physically impossible conditions) before the simulation starts.

### Enabling

```java
// On ProcessSystem
process.setAutoValidate(true);

// On ProcessModel (validates ALL areas)
plant.setAutoValidate(true);
```

### Behavior

- **ProcessSystem**: calls `validateSetup()` on each equipment unit before the first iteration
- **ProcessModel**: calls `validateSetup()` on each `ProcessSystem` before the first iteration

Validation warnings are **logged** (via log4j2) but do **not abort** execution. When `publishEvents` is also enabled, validation warnings are published as `WARNING` events to the bus.

### Custom Validation

Equipment classes can override `validateSetup()` to add custom checks:

```java
@Override
public ValidationResult validateSetup() {
    ValidationResult result = new ValidationResult();
    if (getInletStream() == null) {
        result.addError("Inlet stream not connected");
    }
    if (getOutletPressure() <= 0) {
        result.addWarning("Outlet pressure not set — using inlet pressure");
    }
    return result;
}
```

---

## 5. Combining All Three

For maximum observability, combine listener + event bus + validation:

```java
ProcessModel plant = new ProcessModel();
plant.add("upstream", upstreamProcess);
plant.add("downstream", downstreamProcess);

// 1. Progress listener for structured callbacks
plant.setProgressListener(new ProcessModel.ModelProgressListener() {
    @Override
    public void onProcessAreaComplete(String areaName, ProcessSystem process,
            int areaIndex, int totalAreas, int iterationNumber) {
        System.out.printf("Area '%s' done%n", areaName);
    }

    @Override
    public void onModelComplete(int totalIterations, boolean converged) {
        System.out.printf("Plant: %d iterations, converged=%b%n",
            totalIterations, converged);
    }
});

// 2. Event bus for decoupled subscribers (dashboards, logging, AI agents)
plant.setPublishEvents(true);
ProcessEventBus.getInstance().subscribe(event -> {
    auditLog.record(event);  // Your audit system
});

// 3. Auto-validation for early error detection
plant.setAutoValidate(true);

plant.run();
```

---

## 6. Advantages & Design Decisions

### Zero Overhead by Default

All three mechanisms are **opt-in**. When disabled (the default):
- No listener null-checks add measurable cost (single `if` per unit/area)
- No events are created or published
- No validation is run

This means existing simulations have exactly the same performance as before.

### Backward Compatibility

- `SimulationProgressListener.onUnitComplete()` is the only abstract method — existing implementations continue to work
- `ModelProgressListener.onProcessAreaComplete()` is the only abstract method
- All new hooks have `default` implementations (Java 8 compatible)
- No behavioral changes to `run()` when hooks are not configured

### Exception Safety

All listener callbacks are wrapped in try-catch:
- A misbehaving listener **never crashes** the simulation
- Listener exceptions are logged as warnings and swallowed
- The simulation continues normally

### Separation of Concerns

| Need | Use |
|------|-----|
| "I want structured callbacks with typed parameters" | `ProgressListener` |
| "I want multiple independent consumers of events" | `ProcessEventBus` |
| "I want to catch config errors before wasting compute time" | `setAutoValidate(true)` |

---

## 7. API Reference Summary

### ProcessSystem

```java
// Listener
process.setProgressListener(listener);     // or null to disable
process.getProgressListener();

// Event bus
process.setPublishEvents(true);
process.isPublishEvents();

// Auto-validation
process.setAutoValidate(true);
process.isAutoValidate();

// Convenience callback
process.runWithCallback(unit -> { /* ... */ });
```

### ProcessModel

```java
// Listener
plant.setProgressListener(listener);       // or null to disable
plant.getProgressListener();

// Event bus
plant.setPublishEvents(true);
plant.isPublishEvents();

// Auto-validation
plant.setAutoValidate(true);
plant.isAutoValidate();
```

### ProcessEventBus

```java
ProcessEventBus bus = ProcessEventBus.getInstance();

bus.subscribe(listener);                              // Global
bus.subscribe(EventType.ERROR, listener);             // Type-filtered
bus.subscribe(Severity.WARNING, listener);            // Severity-filtered

bus.unsubscribe(listener);
bus.unsubscribe(EventType.ERROR, listener);

bus.publish(event);                                   // Manual publish
bus.getEventHistory();
bus.clearHistory();
bus.setMaxHistorySize(500);
bus.setAsyncDelivery(true);
```

### ProcessEvent

```java
// Factory methods
ProcessEvent.info("source", "description");
ProcessEvent.warning("source", "description");
ProcessEvent.alarm("source", "description");

// Accessors
event.getEventId();
event.getType();          // EventType enum
event.getSource();        // "ProcessSystem" or "ProcessModel"
event.getDescription();
event.getSeverity();      // Severity enum
event.getTimestamp();     // Instant
event.getProperties();    // Map<String, Object>
event.putProperty("key", value);
```
