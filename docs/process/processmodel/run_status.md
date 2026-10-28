---
title: Run Status — Detecting and Diagnosing Failed Runs
description: How to detect whether a ProcessSystem or ProcessModel run succeeded and identify the failing unit using the structured RunStatus and UnitRunStatus API, including the schema-versioned JSON form for agents and reporting.
keywords: "RunStatus, UnitRunStatus, getRunStatus, run failure, failed unit, ProcessSystem, ProcessModel, diagnostics, agent"
---

When a process run fails, the failure surfaces as a `RuntimeException`. For interactive use
that is fine, but agents, optimizers, and batch drivers usually want a structured answer to
the question *"did the last run succeed, and if not, which unit failed and why?"* without
having to catch and parse the exception text.

`ProcessSystem` and `ProcessModel` expose a structured **`RunStatus`** that records a
per-unit outcome for each run.

The Java fragments below assume a class-level Log4j2 logger, for example:
`private static final Logger logger = LogManager.getLogger(YourClass.class);`.

## Quick Start

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.RunStatus;

ProcessSystem process = buildProcess();
try {
  process.run();
} catch (RuntimeException ex) {
  // run() still throws on failure — that behavior is unchanged.
}

RunStatus status = process.getRunStatus();
if (status.isSuccess()) {
  logger.info("All {} units ran.", status.getUnits().size());
} else {
  logger.info("Run failed at {}: {}", status.getFailedUnitName(),
      status.getFailedUnitError());
}
```

The status is **always populated** after a run — successful or not — so you can inspect it
whether or not you wrapped `run()` in a `try`/`catch`.

## RunStatus API

| Method | Description |
|--------|-------------|
| `isCompleted()` | True once a run has finished (successfully or not) |
| `isSuccess()` | True if the run completed with no unit failure |
| `getFailedUnitName()` | Name of the first unit that failed, or `null` |
| `getFailedUnitError()` | Error message of the first failed unit, or `null` |
| `getUnits()` | Unmodifiable list of per-unit `UnitRunStatus` entries |
| `toJson()` / `toJsonObject()` | Schema-versioned JSON representation |

### UnitRunStatus

Each entry in `getUnits()` is a `UnitRunStatus` describing one unit operation:

| Method | Description |
|--------|-------------|
| `getUnitName()` | Unit operation name |
| `getUnitType()` | Unit operation type (simple class name) |
| `isSuccess()` | Whether this unit ran successfully |
| `getErrorMessage()` | Error message if this unit failed, else `null` |
| `getAreaName()` | Owning process area name (set by `ProcessModel`), else `null` |

## ProcessModel Aggregation

For a multi-area `ProcessModel`, `getRunStatus()` aggregates the per-unit status across all
areas, tagging each `UnitRunStatus` with its area name via `getAreaName()`. This lets an
agent pinpoint not just the failing unit but the area it lives in.

```java
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.RunStatus;
import neqsim.process.processmodel.UnitRunStatus;

ProcessModel plant = new ProcessModel();
plant.add("Separation", separationProcess);
plant.add("Compression", compressionProcess);
plant.run();

RunStatus status = plant.getRunStatus();
for (UnitRunStatus u : status.getUnits()) {
  logger.info("{} / {} -> {}", u.getAreaName(), u.getUnitName(),
      u.isSuccess() ? "ok" : "FAILED: " + u.getErrorMessage());
}
```

## JSON Form

`getRunStatusJson()` (on both `ProcessSystem` and `ProcessModel`) returns a
schema-versioned payload suitable for logging or handing to an external agent:

```java
String json = process.getRunStatusJson();
```

```json
{
  "schemaVersion": "1.0",
  "completed": true,
  "success": false,
  "failedUnitName": "Compressor",
  "failedUnitError": "fluid is not converged",
  "unitCount": 3,
  "units": [
    { "unitName": "feed", "unitType": "Stream", "success": true, "errorMessage": null },
    { "unitName": "HP Sep", "unitType": "Separator", "success": true, "errorMessage": null },
    { "unitName": "Compressor", "unitType": "Compressor", "success": false,
      "errorMessage": "fluid is not converged" }
  ]
}
```

For a `ProcessModel`, each unit entry additionally carries an `areaName` field.

## Solver Watchdog: Time Budget, Stall Detection and Cooperative Stop

A non-converging recycle flowsheet or a very slow unit can occupy a thread for minutes.
`ProcessSystem` and `ProcessModel` therefore offer an **opt-in watchdog**. All features are
**off by default**, so existing models run exactly as before.

```java
ProcessSystem process = buildProcess();
process.setMaxRunTimeSeconds(30.0);   // wall-clock budget of one run() / runUntilConverged()
process.setStallDetection(8, 1.0e-3); // 8 iterations without 0.1 % residual improvement
process.run();                        // returns normally when the watchdog trips

RunStatus status = process.getRunStatus();
if (status.isTimedOut() || status.isStalled() || status.isStopped()) {
  logger.info("{} at unit {}: {}", status.getTerminationReason(), status.getBudgetUnitName(),
      status.getBudgetReason());
}
RunDiagnostics diag = process.getLastRunDiagnostics();
logger.info("elapsed {} s, outcome {}", diag.getElapsedSeconds(), diag.getOutcome());
```

| Method | Description |
|--------|-------------|
| `setMaxRunTimeSeconds(double)` | Wall-clock limit of one run; zero or negative disables it |
| `setStallDetection(int window, double minRelImprovement)` | Stops with `STALLED` when the worst tolerance-normalised recycle or adjuster residual (outer boundary residual for `ProcessModel`) does not improve for `window` consecutive iterations |
| `requestStop()` | Thread-safe cooperative cancellation from another thread; the run reports `STOPPED` |
| `getRunStatus()` | `getTerminationReason()` is `TIMED_OUT`, `STALLED` or `STOPPED`; `getBudgetUnitName()` and `getBudgetReason()` give the unit that was running and why |
| `getLastRunDiagnostics()` | `RunDiagnostics` with elapsed time, outcome, unit, pass count and residual history (`toJson()`) |

Notes:

- The budget is **cooperative**: it is checked between unit operations and between recycle,
  adjuster and (for `ProcessModel`) outer iterations, and one budget spans all passes of
  `runUntilConverged(n)` and all areas of a `ProcessModel`. A single unit operation that never
  returns cannot be interrupted.
- A tripped watchdog is not a failure: `run()` returns normally, `isConverged()` is false and
  the last computed state of the units is retained.
- A `requestStop()` made while no run is active is discarded when the next run starts.

## Notes

- **Additive and backward compatible** — `run()` still throws on failure exactly as before.
  `RunStatus` is an extra, optional read after the run.
- The status object is `Serializable` and schema-versioned (`SCHEMA_VERSION = "1.0"`).
- Only the **first** failure is reported as the run's `failedUnitName` /
  `failedUnitError`; the full per-unit list remains available via `getUnits()`.

---

## Related Documentation

- [ProcessSystem](process_system.md) — building and running flowsheets
- [ProcessModel](process_model.md) — multi-area model coordination
- [Process Automation API](../../simulation/process_automation.md) — string-addressable variable access for agents
