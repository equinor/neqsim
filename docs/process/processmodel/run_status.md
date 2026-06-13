---
title: Run Status — Detecting and Diagnosing Failed Runs
description: How to detect whether a ProcessSystem or ProcessModel run succeeded and identify the failing unit using the structured RunStatus and UnitRunStatus API, including the schema-versioned JSON form for agents and reporting.
keywords: "RunStatus, UnitRunStatus, getRunStatus, run failure, failed unit, ProcessSystem, ProcessModel, diagnostics, agent"
---

# Run Status — Detecting and Diagnosing Failed Runs

When a process run fails, the failure surfaces as a `RuntimeException`. For interactive use
that is fine, but agents, optimizers, and batch drivers usually want a structured answer to
the question *"did the last run succeed, and if not, which unit failed and why?"* without
having to catch and parse the exception text.

`ProcessSystem` and `ProcessModel` expose a structured **`RunStatus`** that records a
per-unit outcome for each run.

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
  System.out.println("All " + status.getUnits().size() + " units ran.");
} else {
  System.out.println("Run failed at " + status.getFailedUnitName()
      + ": " + status.getFailedUnitError());
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
import neqsim.process.processmodel.UnitRunStatus;

ProcessModel plant = new ProcessModel();
plant.add("Separation", separationProcess);
plant.add("Compression", compressionProcess);
plant.run();

RunStatus status = plant.getRunStatus();
for (UnitRunStatus u : status.getUnits()) {
  System.out.println(u.getAreaName() + " / " + u.getUnitName()
      + " -> " + (u.isSuccess() ? "ok" : "FAILED: " + u.getErrorMessage()));
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

## Notes

- **Additive and backward compatible** — `run()` still throws on failure exactly as before.
  `RunStatus` is an extra, optional read after the run.
- The status object is `Serializable` and schema-versioned (`SCHEMA_VERSION = "1.0"`).
- Only the **first** failure is reported as the run's `failedUnitName` /
  `failedUnitError`; the full per-unit list remains available via `getUnits()`.

---

## Related Documentation

- [ProcessSystem](process_system) — building and running flowsheets
- [ProcessModel](process_model) — multi-area model coordination
- [Process Automation API](../../simulation/process_automation) — string-addressable variable access for agents
