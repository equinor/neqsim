---
title: "Equipment Utilization via Mechanical Design"
description: "How to set design limits through the MechanicalDesign object so that capacity utilization can be calculated and reported for any NeqSim equipment type."
---

## Overview

This guide explains how to make **capacity utilization** available for *any* piece of
NeqSim equipment by setting design limits on its
[`MechanicalDesign`](../../src/main/java/neqsim/process/mechanicaldesign/MechanicalDesign.java)
object. It complements the
[Capacity Constraint Framework](CAPACITY_CONSTRAINT_FRAMEWORK.md), which describes the
constraint model and the equipment-specific strategy library.

## Why this exists

Many equipment types (compressors, separators, valves, pipelines, heat exchangers, pumps)
ship with rich, type-specific capacity constraints. Generic equipment, however, had no
uniform way to answer the simple question *"how close is this unit to its design limit?"*

The mechanical-design bridge fills that gap. You configure the design envelope where it
naturally belongs — on the equipment's `MechanicalDesign` — and a single opt-in call turns
those limits into capacity constraints that surface in:

- `equipment.getMaxUtilization()` / `getMaxUtilizationPercent()`
- `equipment.getBottleneckConstraint()`
- `ProcessSystem.getUtilizationSnapshotJson()` and `ProcessModel.getUtilizationSnapshotJson()`
- `ProcessAutomation.getUtilizationSnapshot()`

## Works out of the box, fully configurable

The capability is designed to need **no configuration to start** and to stay **fully
configurable** when you need more control:

- **Out of the box** — set a single design limit and read utilization in one line. No
  constraint objects, no enable flags, no boilerplate:

  ```java
  // After the equipment has run with live stream conditions:
  equipment.getMechanicalDesign().setMaxDesignPressureDrop(20.0); // any one limit
  double util = equipment.getMechanicalDesign().getMaxDesignUtilization(); // 0.0..1.0
  ```

- **Zero side effects by default** — if you set *no* design limit, nothing changes:
  `getMaxDesignUtilization()` returns `0.0` and `applyMechanicalDesignCapacityConstraints()`
  registers nothing. Existing simulations are unaffected (fully backward compatible).

- **Fully configurable** — set any subset of the nine supported limits (see
  [Supported design limits](#supported-design-limits)). Each limit is independent; only the
  ones you set are evaluated. Equipment-specific operating values are supplied by overridable
  `getOperating*` hooks, so you can extend coverage to any metric.

- **Integrates when you want it** — a single opt-in call,
  `applyMechanicalDesignCapacityConstraints()`, promotes the limits into the equipment's
  capacity-constraint system so they also show up in `getMaxUtilization()`,
  `getBottleneckConstraint()`, and the utilization snapshot used by `ProcessSystem`,
  `ProcessModel`, and `ProcessAutomation`.

## The two methods you need

The capability is delivered through two public methods.

On `MechanicalDesign`:

| Method | Returns |
| --- | --- |
| `getDesignUtilization()` | `Map<String, Double>` of metric name → utilization (current/limit) |
| `getMaxDesignUtilization()` | the maximum utilization across all configured design limits, or `0.0` |
| `getDesignCapacityConstraints()` | the derived `CapacityConstraint` list (used by the bridge) |

On every equipment (`ProcessEquipmentBaseClass`):

| Method | Returns |
| --- | --- |
| `applyMechanicalDesignCapacityConstraints()` | registers the derived constraints and returns how many were added |

## Supported design limits

Set any of the following on the equipment's `MechanicalDesign`. Only limits that are set to
a value greater than `0` **and** that have a finite operating value produce a constraint, so
unused limits never create misleading utilization.

<table>
<caption>Mechanical-design limits mapped to utilization metrics</caption>
<tr><th>MechanicalDesign setter</th><th>Utilization metric</th><th>Unit</th><th>Operating value source</th></tr>
<tr><td><code>setMaxDesignVolumeFlow</code></td><td>design volume flow</td><td>m3/hr</td><td>sum of inlet stream actual volume flow</td></tr>
<tr><td><code>setMaxDesignGassVolumeFlow</code></td><td>design gas volume flow</td><td>m3/hr</td><td>equipment hook (override <code>getOperatingGasVolumeFlow</code>)</td></tr>
<tr><td><code>setMaxDesignOilVolumeFlow</code></td><td>design oil volume flow</td><td>m3/hr</td><td>equipment hook (override <code>getOperatingOilVolumeFlow</code>)</td></tr>
<tr><td><code>setMaxDesignWaterVolumeFlow</code></td><td>design water volume flow</td><td>m3/hr</td><td>equipment hook (override <code>getOperatingWaterVolumeFlow</code>)</td></tr>
<tr><td><code>setMaxDesignPower</code></td><td>design power</td><td>kW</td><td>equipment hook (override <code>getOperatingPower</code>)</td></tr>
<tr><td><code>setMaxDesignDuty</code></td><td>design duty</td><td>kW</td><td>equipment hook (override <code>getOperatingDuty</code>)</td></tr>
<tr><td><code>setMaxDesignCv</code></td><td>design Cv</td><td>-</td><td>equipment hook (override <code>getOperatingCv</code>)</td></tr>
<tr><td><code>setMaxDesignVelocity</code></td><td>design velocity</td><td>m/s</td><td>equipment hook (override <code>getOperatingVelocity</code>)</td></tr>
<tr><td><code>setMaxDesignPressureDrop</code></td><td>design pressure drop</td><td>bara</td><td>absolute inlet-minus-outlet pressure</td></tr>
</table>

### Universal vs. hook metrics

Two metrics are computed **universally** for any equipment that has inlet/outlet streams:

- **design pressure drop** — `|P_in − P_out|` in bara, read from the first inlet and outlet
  streams.
- **design volume flow** — the sum of the inlet streams' actual volumetric flow in m³/hr.

The remaining metrics (gas/oil/water volume flow, power, duty, Cv, velocity) depend on
quantities that are equipment-specific or ambiguous in units across the codebase. They are
exposed as protected hook methods on `MechanicalDesign` that return `Double.NaN` by default.
Equipment subclasses (or specialised `MechanicalDesign` subclasses) may override the matching
`getOperating*` hook to feed a real value — using the documented unit convention (actual m³/hr,
kW, m/s). Until a hook is overridden, the corresponding design limit simply produces no
constraint, so utilization is never reported on an undefined basis.

## Worked example

```java
SystemInterface fluid = new SystemSrkEos(298.15, 60.0);
fluid.addComponent("methane", 0.9);
fluid.addComponent("ethane", 0.1);
fluid.setMixingRule("classic");

Stream feed = new Stream("feed", fluid);
feed.setFlowRate(1000.0, "kg/hr");
feed.setPressure(60.0, "bara");
feed.setTemperature(25.0, "C");

Heater heater = new Heater("heater", feed);
heater.setOutletPressure(50.0);     // 10 bara pressure drop
heater.setOutTemperature(30.0 + 273.15);

ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(heater);
process.run();

// 1) Configure the design envelope on the mechanical design
heater.getMechanicalDesign().setMaxDesignPressureDrop(20.0);   // bara

// 2) Read utilization directly from the mechanical design
double dpUtil = heater.getMechanicalDesign()
    .getDesignUtilization().get("design pressure drop");        // 10 / 20 = 0.5

// 3) Or surface it as an equipment capacity constraint (opt-in)
int added = heater.applyMechanicalDesignCapacityConstraints(); // 1
double maxUtil = heater.getMaxUtilization();                   // 0.5
```

## Workflow rules

1. **Set design limits first**, then run the process. Utilization needs live stream
   conditions, so call `applyMechanicalDesignCapacityConstraints()` after `process.run()`.
2. **Re-apply after changes.** The call is idempotent — derived constraints use stable names
   (for example `"design pressure drop"`), so re-invoking it overwrites the previous values
   instead of creating duplicates. Re-run it whenever design limits or operating conditions
   change.
3. **It never throws.** If the mechanical design cannot be read, the method registers nothing
   and returns `0`.
4. **Nothing is required.** With no design limits set, the feature is dormant and behavior is
   identical to before — set limits only for the metrics you care about.

## Caveat: cached vs. throwaway mechanical design

For equipment that overrides `getMechanicalDesign()` to return a *cached* design (separators,
pumps, valves, pipelines, heaters, coolers, heat exchangers, compressors), the limits you set
persist and the bridge works as shown above.

The base `ProcessEquipmentBaseClass.getMechanicalDesign()` returns a **fresh throwaway**
instance for equipment that does not cache one. For such equipment, set the limit and read
utilization through a single retained reference, e.g.:

```java
MechanicalDesign md = someEquipment.getMechanicalDesign();
md.setMaxDesignVolumeFlow(5000.0);
double util = md.getMaxDesignUtilization();
```

## Related documentation

- [Capacity Constraint Framework](CAPACITY_CONSTRAINT_FRAMEWORK.md)
- [Valve Mechanical Design](ValveMechanicalDesign.md)
- [Process Design Guide](process_design_guide.md)
