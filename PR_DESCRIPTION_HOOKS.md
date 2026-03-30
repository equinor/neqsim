# Simulation Lifecycle Hooks for ProcessSystem and ProcessModel

## Summary

Adds **opt-in lifecycle hooks** to `ProcessSystem` and `ProcessModel` — three complementary observability mechanisms that give users real-time visibility into simulation execution without any behavioral changes to existing code.

| Mechanism | Purpose | Scope |
|-----------|---------|-------|
| **SimulationProgressListener** | Typed callbacks at the unit-operation level | `ProcessSystem` |
| **ModelProgressListener** | Typed callbacks at the process-area level | `ProcessModel` |
| **ProcessEventBus integration** | Decoupled publish-subscribe events | Both |
| **Auto-validation** | Pre-run equipment/area validation | Both |

All mechanisms are **disabled by default** with zero overhead — existing simulations are completely unaffected.

## Motivation

NeqSim already had the building blocks (`ProcessEventBus`, `ProcessEvent`, `ProcessEventListener`, `SimulationProgressListener` stub, `validateSetup()` on equipment) but none were wired into the actual execution paths. This PR connects them:

- `ProcessEventBus` had 0 call sites — now fires during `run()` on both `ProcessSystem` and `ProcessModel`
- `SimulationProgressListener` existed but only `onUnitComplete` was defined — expanded with 6 new lifecycle hooks
- `validateSetup()` was defined on equipment but never auto-invoked — now runs pre-simulation when opted in
- `ProcessModel` had no observability at all — new `ModelProgressListener` with 7 area-level hooks

## What's New

### 1. ProcessSystem — Expanded SimulationProgressListener

The existing `onUnitComplete` callback is joined by 6 new hooks (all with safe `default` implementations for backward compatibility):

| Hook | When It Fires |
|------|--------------|
| `onSimulationStart(totalUnits)` | Once, before the first iteration |
| `onBeforeIteration(iterationNumber)` | Before each pass through all units |
| `onBeforeUnit(unit, unitIndex, totalUnits, iterationNumber)` | Before each unit runs |
| `onUnitComplete(unit, unitIndex, totalUnits, iterationNumber)` | After each unit runs *(existing)* |
| `onIterationComplete(iterationNumber, converged, recycleError)` | After each full pass |
| `onSimulationComplete(totalIterations, converged)` | Once, after all iterations |
| `onUnitError(unit, exception)` | When a unit throws (return true to continue) |

**Wired into all 4 execution paths:** `runWithProgress()`, `runSequential()`, `runParallel()`, `runTransient()`.

### 2. ProcessModel — New ModelProgressListener

Brand new callback interface for multi-area plant models:

| Hook | When It Fires |
|------|--------------|
| `onModelStart(totalAreas)` | Once, before first iteration |
| `onBeforeIteration(iterationNumber)` | Before each outer convergence iteration |
| `onBeforeProcessArea(name, process, idx, total, iter)` | Before running each area |
| `onProcessAreaComplete(name, process, idx, total, iter)` | After running each area |
| `onIterationComplete(iterationNumber, converged, maxError)` | After each outer iteration |
| `onModelComplete(totalIterations, converged)` | Once, after model finishes |
| `onProcessAreaError(name, process, exception)` | When an area throws |

**Wired into both modes:** continuous (convergence loop) and step (`setRunStep(true)`).

### 3. ProcessEventBus Wiring

Both `ProcessSystem` and `ProcessModel` now publish events to the singleton `ProcessEventBus` when opted in:

```java
process.setPublishEvents(true);  // ProcessSystem events
plant.setPublishEvents(true);    // ProcessModel events
```

Published event types: `INFO` (start), `SIMULATION_COMPLETE` (end), `WARNING` (non-convergence, validation), `ERROR` (exceptions).

Multiple independent subscribers can listen without knowing about each other — ideal for dashboards, audit logs, AI optimization agents.

### 4. Auto-Validation

Pre-run validation catches configuration errors before wasting compute time:

```java
process.setAutoValidate(true);  // Validates each equipment unit
plant.setAutoValidate(true);    // Validates each ProcessSystem area
```

Validation warnings are logged but do **not** abort execution. When `publishEvents` is also enabled, warnings are published as `WARNING` events.

### 5. Convenience Callback

Simple callback wrapper for Python/Jupyter where implementing a full interface is cumbersome:

```java
process.runWithCallback(unit -> {
    System.out.println("Completed: " + unit.getName());
});
```

## Usage Examples

### Java — ProcessSystem

```java
process.setProgressListener(new ProcessSystem.SimulationProgressListener() {
    @Override
    public void onUnitComplete(ProcessEquipmentInterface unit,
            int unitIndex, int totalUnits, int iterationNumber) {
        System.out.printf("  [%d/%d] %s done%n", unitIndex + 1, totalUnits, unit.getName());
    }

    @Override
    public void onSimulationComplete(int totalIterations, boolean converged) {
        System.out.printf("Done in %d iterations (converged=%b)%n", totalIterations, converged);
    }
});
process.run();
```

### Java — ProcessModel

```java
plant.setProgressListener(new ProcessModel.ModelProgressListener() {
    @Override
    public void onProcessAreaComplete(String areaName, ProcessSystem process,
            int areaIndex, int totalAreas, int iterationNumber) {
        System.out.printf("  Area '%s' completed [iter %d]%n", areaName, iterationNumber);
    }

    @Override
    public void onModelComplete(int totalIterations, boolean converged) {
        System.out.printf("Plant: %d iterations, converged=%b%n", totalIterations, converged);
    }
});
plant.run();
```

### Python / Jupyter

```python
from neqsim import jneqsim
ProcessSystem = jneqsim.process.processmodel.ProcessSystem

class MyListener(ProcessSystem.SimulationProgressListener):
    def onUnitComplete(self, unit, index, total, iteration):
        print(f"  [{index+1}/{total}] {unit.getName()} done")

    def onSimulationComplete(self, totalIter, converged):
        print(f"Simulation: {totalIter} iters, converged={converged}")

process.setProgressListener(MyListener())
process.run()
```

### Event Bus (decoupled monitoring)

```java
ProcessEventBus bus = ProcessEventBus.getInstance();
bus.subscribe(ProcessEvent.EventType.ERROR, event -> {
    alertSystem.send("Simulation error: " + event.getDescription());
});

process.setPublishEvents(true);
process.run();
```

## Design Decisions

### Zero Overhead When Disabled

All three mechanisms check a boolean flag or null reference before doing any work. When disabled (the default), the cost is a single `if` per unit/area — unmeasurable in practice.

### Exception Safety

All listener callbacks are wrapped in try-catch. A misbehaving listener **never crashes** the simulation — exceptions are logged as warnings and swallowed.

### Backward Compatibility

- Only `onUnitComplete` (ProcessSystem) and `onProcessAreaComplete` (ProcessModel) are abstract
- All other hooks have safe `default` implementations (Java 8 compatible)
- No behavioral changes when hooks are not configured
- Serialization unaffected — listener fields are `transient`

### ProcessModel Hook Execution

When a `ModelProgressListener` is set, `ProcessModel.run()` uses sequential area execution with before/after callbacks instead of parallel execution. This ensures deterministic callback ordering. When no listener is set and `publishEvents` is false, the original parallel execution path is used.

## Test Coverage

| Test Class | Tests | What's Tested |
|-----------|-------|---------------|
| `SimulationHooksTest` | 9 | ProcessSystem lifecycle hooks, event bus wiring, auto-validation, backward compatibility |
| `ProcessModelHooksTest` | 10 | ProcessModel lifecycle hooks, step/continuous modes, event bus, auto-validation, flag setters |

All tests pass. No regressions in `ProcessSystemTest`, `SeparatorTest`, `CompressorTest`, `MixerTest`, `StreamTest`.

## Files Changed

### Modified Java Classes

| File | Changes |
|------|---------|
| `ProcessSystem.java` | Expanded `SimulationProgressListener` with 6 new hooks; wired event bus + auto-validation into `runWithProgress()`, `runSequential()`, `runParallel()`, `runTransient()`; added `setPublishEvents()`, `setAutoValidate()`, `runWithCallback()` |
| `ProcessModel.java` | Added `ModelProgressListener` interface with 7 hooks; wired event bus + auto-validation into `run()` (both step and continuous modes); added `runAllProcessesWithHooks()`, `publishModelEvent()`, `runModelAutoValidation()`, and 7 private notify helpers |
| `ProcessEvent.java` | Changed `generateId()` from private to public static (needed by event publishers) |

### New Test Classes

| File | Tests |
|------|-------|
| `SimulationHooksTest.java` | 9 tests covering all ProcessSystem hook scenarios |
| `ProcessModelHooksTest.java` | 10 tests covering all ProcessModel hook scenarios |

### New Documentation

| File | Description |
|------|-------------|
| `docs/process/simulation-hooks-and-events.md` | Comprehensive user guide with Java and Python examples |
| `docs/REFERENCE_MANUAL_INDEX.md` | Added entry for the hooks guide |
| `docs/process/README.md` | Added link to the hooks guide |

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    ProcessModel                          │
│   ┌───────────┐   ┌───────────┐   ┌───────────┐        │
│   │  Area A   │   │  Area B   │   │  Area C   │        │
│   │(ProcSystem)│   │(ProcSystem)│   │(ProcSystem)│       │
│   └───────────┘   └───────────┘   └───────────┘        │
│        │                │                │               │
│   ModelProgressListener callbacks:                       │
│   onBeforeProcessArea → [run area] → onProcessAreaComplete
│                                                          │
│   ProcessEventBus ← publishModelEvent()                  │
└─────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────┐
│                   ProcessSystem                          │
│   ┌──────┐   ┌──────────┐   ┌──────────┐               │
│   │ Feed │──▶│Separator │──▶│Compressor│               │
│   └──────┘   └──────────┘   └──────────┘               │
│       │            │              │                      │
│  SimulationProgressListener callbacks:                   │
│  onBeforeUnit → [run unit] → onUnitComplete              │
│                                                          │
│  ProcessEventBus ← publishEvent()                        │
└─────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────┐
│                  ProcessEventBus (singleton)              │
│                                                          │
│  Subscriber A: Dashboard UI                              │
│  Subscriber B: Audit Logger                              │
│  Subscriber C: AI Optimization Agent                     │
│  Subscriber D: Unit Test Assertions                      │
└─────────────────────────────────────────────────────────┘
```

## Checklist

- [x] All code compiles with Java 8
- [x] All new hooks have `default` implementations (backward compatible)
- [x] Listener fields marked `transient` (serialization safe)
- [x] All callbacks wrapped in try-catch (exception safe)
- [x] Zero overhead when disabled (boolean/null checks only)
- [x] 19 new tests (9 + 10), all passing
- [x] No regressions in existing test suites
- [x] Documentation with Java and Python examples
- [x] Reference manual index updated
