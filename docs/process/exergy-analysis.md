---
title: "Exergy Analysis for Process Systems and Plant Models"
description: "Plant-wide exergy destruction analysis for NeqSim — identify thermodynamic inefficiency hotspots across ProcessSystem and multi-area ProcessModel flowsheets using the ExergyAnalysisReport API."
---

# Exergy Analysis

Exergy analysis quantifies the **thermodynamic inefficiency** (lost work) of each unit operation. Unlike energy balance alone, exergy destruction pinpoints *where* in the plant useful work is being wasted — the prime leverage points for efficiency improvement.

NeqSim provides a unified exergy API at three levels:

| Level | Class | Use When |
|-------|-------|----------|
| Single unit | `ProcessEquipmentInterface` | Check one piece of equipment |
| Flowsheet | `ProcessSystem` | One process area / single flowsheet |
| Multi-area plant | `ProcessModel` | Full plants composed of multiple `ProcessSystem` areas |

Results are returned as an [`ExergyAnalysisReport`](../../src/main/java/neqsim/process/util/exergy/ExergyAnalysisReport.java) — a structured, JSON-exportable object designed for large plants with hundreds of units.

---

## Theory (one equation)

For any control volume operating with surroundings at dead-state temperature $T_0$ (K):

$$
\dot{E}_{\text{destroyed}} \;=\; T_0 \cdot \dot{S}_{\text{gen}} \;\geq\; 0
$$

where $\dot{S}_{\text{gen}}$ is the entropy production rate of the unit. This is the Gouy–Stodola theorem and is **universal** for adiabatic equipment (compressors, expanders, valves, mixers, separators).

Exergy destruction is always non-negative; NeqSim clamps small negative numerical noise to zero.

**Caveat — heaters and coolers.** When the external heat source/sink temperature is not modelled, the default destruction uses only stream-side entropy production. This underestimates irreversibility caused by the finite temperature difference across the heat transfer surface. Model the utility side explicitly (e.g., using a heat exchanger with a utility stream) for a full picture.

---

## Quick start — single flowsheet

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.exergy.ExergyAnalysisReport;

ProcessSystem process = new ProcessSystem();
// ... build flowsheet, process.run() ...

process.setSurroundingTemperature(288.15); // dead-state T0 (K)

double exergyChange_kW      = process.getExergyChange("kW");
double exergyDestruction_kW = process.getExergyDestruction("kW");

ExergyAnalysisReport report = process.getExergyAnalysis();
System.out.println(report.toString("kW"));
```

The ranked table lists every unit in the flowsheet, sorted by destruction:

```text
Exergy analysis report (unit: kW)
==================================================================
#   Unit name              Type         Destruction   Change
------------------------------------------------------------------
1   MainCompressor         Compressor        42.18     -215.30
2   JT-Valve               ThrottlingValve   18.72        0.00
3   InterCooler            Cooler             5.31      -87.11
...
Total destruction: 66.21 kW
Total change:     -302.41 kW
```

Supported units: `J`, `kJ`, `MJ`, `W`, `kW`, `MW`.

---

## Plant-wide analysis — multi-area `ProcessModel`

Large plants are usually split into multiple `ProcessSystem` areas (separation, compression, utilities, export) and composed in a `ProcessModel`. The exergy report aggregates automatically, labelling each entry with its area name.

```java
import neqsim.process.processmodel.ProcessModel;

ProcessModel plant = new ProcessModel();
plant.add("Separation", separationArea);
plant.add("Compression", compressionArea);
plant.add("Export", exportArea);
plant.run();

ExergyAnalysisReport report = plant.getExergyAnalysis();

// Top-10 hotspots across the entire plant:
for (ExergyAnalysisReport.Entry e : report.getTopDestructionHotspots(10)) {
    System.out.printf("%-20s [%s]  %.2f kW%n",
        e.getName(), e.getArea(), e.getExergyDestructionJ() / 1000.0);
}

// Aggregate destruction by area:
Map<String, Double> byArea = report.getDestructionByArea("kW");

// Aggregate by equipment type:
Map<String, Double> byType = report.getDestructionByType("kW");

// Serialise for dashboards / reports:
String json = report.toJson();
```

`getTopDestructionHotspots(0)` or any `n ≤ 0` returns the full sorted list.

---

## API reference

### ProcessSystem

| Method | Purpose |
|--------|---------|
| `getExergyChange(String unit)` | Net exergy change using the system's surrounding temperature |
| `getExergyChange(String unit, double T0)` | Net exergy change at specified $T_0$ (K) |
| `getExergyDestruction(String unit)` | Total destruction across all units (= $T_0 \sum \dot{S}_{\text{gen}}$) |
| `getExergyDestruction(String unit, double T0)` | Same, at specified $T_0$ |
| `getExergyAnalysis()` | Build structured `ExergyAnalysisReport` |
| `getExergyAnalysis(double T0)` | Same, at specified $T_0$ |

### ProcessModel

| Method | Purpose |
|--------|---------|
| `getExergyChange(String unit)` | Plant-wide sum across all areas |
| `getExergyDestruction(String unit)` | Plant-wide destruction |
| `getExergyAnalysis()` | Combined report with `area` tag on every entry |

### ExergyAnalysisReport

| Method | Returns |
|--------|---------|
| `getEntries()` | All entries (unit, type, area, change, destruction) |
| `getTopDestructionHotspots(int n)` | Top-n entries sorted descending by destruction (`n ≤ 0` returns all) |
| `getTotalExergyDestruction(String unit)` | Sum of destruction, converted |
| `getTotalExergyChange(String unit)` | Sum of change, converted |
| `getDestructionByArea(String unit)` | Destruction grouped by area |
| `getDestructionByType(String unit)` | Destruction grouped by equipment type |
| `toString(String unit)` | Ranked human-readable table |
| `toJson()` | JSON string for dashboards / results.json |

### ProcessEquipmentInterface

Every equipment class now supports:

```java
double d_J  = unit.getExergyDestruction("J");
double d_kW = unit.getExergyDestruction("kW");
double d_T0 = unit.getExergyDestruction("kW", 293.15);
```

The default implementation uses $T_0 \cdot \dot{S}_{\text{gen}}$ from the equipment's entropy production, clamped at zero.

---

## Python example

```python
from neqsim import jneqsim

plant = jneqsim.process.processmodel.ProcessModel()
plant.add("Compression", compression)
plant.add("Export", export)
plant.run()

report = plant.getExergyAnalysis()
print(report.toString("kW"))

hotspots = list(report.getTopDestructionHotspots(5))
for e in hotspots:
    print(f"{e.getName():<20}  {e.getArea():<12}  {e.getExergyDestructionJ()/1000:.2f} kW")

# Export structured JSON (e.g. for results.json in the task-solving workflow)
with open("exergy.json", "w") as f:
    f.write(str(report.toJson()))
```

---

## Using exergy results in task reports

The JSON from `report.toJson()` is well-suited for embedding in task `results.json`:

```python
import json
results["exergy"] = {
    "T0_K": 288.15,
    "total_destruction_kW": plant.getExergyDestruction("kW"),
    "by_area_kW": dict(report.getDestructionByArea("kW")),
    "by_type_kW": dict(report.getDestructionByType("kW")),
    "hotspots": json.loads(str(report.toJson()))["entries"][:10],
}
```

Downstream the task report generator can render these as tables and tornado charts.

---

## Related documentation

- [Process Simulation Package](README.md)
- [ProcessModel for Multi-Area Plants](processmodel/)
- [Controllers and Logic](controllers.md)
- [Thermodynamic Operations](../thermodynamicoperations/index.md)
