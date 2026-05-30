---
title: Low-Flow Section Bypass
description: Auto-bypass and manual deactivation of low-flow process sections in ProcessSystem and ProcessModel. Covers minimum-flow thresholds, section deactivation, ProcessModel convergence handling, and feed-flow configuration patterns for testosb-style parallel compressor trains.
---

# Low-Flow Section Bypass

NeqSim can automatically (or manually) bypass parts of a flowsheet that are
receiving negligible flow, so that turning off a parallel train, a recycle,
or a seasonal export route does not destabilise the rest of a
`ProcessSystem` or multi-area `ProcessModel`.

This is essential for full-platform models such as
[`task_solve/.../process_model.ipynb`](../../../task_solve/) where a duty
compressor train (for example `ht_injection_compressors`) is sometimes
inactive while export and recompression continue at full rate.

## Why low-flow bypass exists

Equipment routines (compressors, heaters, separators, columns) make
implicit assumptions that the inlet stream carries enough fluid for the
property model to behave numerically — for example dividing by mass flow,
solving an isentropic head curve, or computing a UA-based duty. When a
feed of `1e-20 kg/hr` is presented:

- A compressor curve evaluated at zero flow produces NaN head.
- A heater UA solver divides by zero in `Q = UA · LMTD`.
- A separator may settle on a degenerate single-phase solution that
  poisons downstream recycles.

Rather than introducing per-equipment guards everywhere, the framework
exposes one mechanism — a *minimum-flow* threshold per unit — and a small
amount of glue (`ProcessSystem.deactivateSection`,
`ProcessModel.deactivateSection`, sticky `isActive` state) that lets a
whole *section* be auto-bypassed cleanly.

## The three building blocks

### 1. `unit.setMinimumFlow(kgPerHour)` and `unit.isActive()`

Every `ProcessEquipmentBaseClass` carries a `minimumFlow` field
(default `1e-20 kg/hr`) and a transient `isActive` flag.

Equipment classes that have wired the bypass call
`checkAndHandleLowFlow(inlet, id)` (or an inline equivalent) at the top of
their `run()`:

- If `inlet.getFlowRate("kg/hr") < getMinimumFlow()` then `isActive(false)`,
  the outlet is left at zero, and `run()` returns immediately.
- Otherwise `isActive(true)` and normal execution proceeds.

Currently wired with inline auto-bypass:

| Equipment | Behaviour when bypassed |
|-----------|------------------------|
| `Splitter` | All split outlets forced to `0 kg/hr`. |
| `Separator` | Both gas and liquid outlets forced to `0 kg/hr`. |
| `Heater` (cooler / electric heater) | Outlet inherits inlet at the set pressure with `Q = 0`. |
| `Compressor` | Outlet inherits inlet at the set pressure with `power = 0`. |
| `Mixer` | Handles zero-flow inlets natively (no explicit guard needed). |

### 2. `ProcessSystem.setSectionLowFlowThreshold(threshold)`

Convenience: sets the same `minimumFlow` on every unit in a process area.
Typical usage on a process area that may or may not be on:

```java
ProcessSystem htTrain = buildCompressorTrain("ht_injection_compressors", htFeed, 250.0);
htTrain.setSectionLowFlowThreshold(1.0); // bypass entire train when feed < 1 kg/hr
```

The full plant can also be set in one call via
`ProcessModel.setSectionLowFlowThreshold(threshold)`.

### 3. Manual lock: `setLockedInactive(true)` / `deactivateSection(...)`

For "this train is definitively shut in", a *sticky* lock survives every
`run()` call regardless of the actual feed flow:

```java
plant.deactivateSection("ht_injection_compressors", "ht_K1");
// later
plant.activateSection("ht_injection_compressors", "ht_K1");
// or unlock everything
plant.activateAll();
```

`deactivateSection` walks downstream following two paths:

1. Explicit `ProcessConnection.MATERIAL` edges added via `process.connect(...)`.
2. Stream-wiring reachability (object-reference equality between an
   upstream unit's outlet `StreamInterface` and a downstream unit's inlet
   `StreamInterface`).

Both walks stop at a `Mixer` whose other inlet is still served by an
active source, so the active part of the flowsheet keeps running.

## Sticky-inactive vs transient-inactive

The framework distinguishes two states deliberately:

| State | Set by | Cleared by | Survives `ProcessSystem.run()` ? |
|-------|--------|------------|----------------------------------|
| Transient `isActive=false` | `checkAndHandleLowFlow` inside `unit.run()` | The next `unit.run()` call with sufficient flow | Yes — `resetActiveStates` only touches locked units |
| `lockedInactive=true` | `setLockedInactive(true)` / `deactivateSection` | `setLockedInactive(false)` / `activateSection` / `activateAll` | Yes — `resetActiveStates` forces `isActive=false` |

`ProcessSystem.resetActiveStates()` is invoked at the start of every
`run()` overload (`runSequential`, `runOptimized`, `runParallel`,
`runHybrid`, `runDataflow`, and the public `run(UUID)`). It deliberately
does **not** blanket-reset `isActive=true` on unlocked units — doing so
would clobber the transient bypass set by an earlier `run()` overload in
the same solve pass while the recalculation cache skips the unit (so its
own `run()` never fires to re-evaluate the inlet flow). The current
contract: unlocked units keep whatever `isActive` they had until their
`run()` is invoked, at which point the unit itself decides based on its
inlet.

If you want the *next* solve to reconsider a transiently-bypassed unit,
just increase the feed and call `process.run()` again — the scheduler
will invoke `unit.run()` because the recalculation cache invalidates on
upstream changes.

## How the scheduler skips inactive units

`ProcessSystem.runUnitProfiled` is the single chokepoint:

```java
if (unit.isLockedInactive() || !unit.isActive()) {
  unit.setCalculationIdentifier(id);
  return;
}
unit.run(id);
```

So both the manual lock and a transient low-flow bypass produce the same
effect — the unit appears solved (`calculationIdentifier` advances) but
no thermodynamic work happens.

## ProcessModel convergence

`ProcessModel.calculateConvergenceErrors` skips any boundary stream whose
magnitude is below `1e-9 kg/hr` before computing relative error, so a
bypassed section does **not** prevent the active areas from converging.

## Feed-flow configuration patterns

When you intentionally turn off a downstream section, you also have to
decide what the *feed* to that section should be. The patterns below
mirror real `testosb`-style platform models.

### Pattern A — `setFlowRates` with negative remainder (testosb)

A `Splitter` with `setFlowRates([...], unit)` accepts `-1.0` on exactly
one outlet to mean *"absorb the remainder"*:

```java
manifold.setFlowRates(new double[] {-1.0, smallFlow}, "MSm3/day");
htTrain.setSectionLowFlowThreshold(1.0);
```

Effect: export gets `(feed - smallFlow)`, the HT train gets `smallFlow`.
Set `smallFlow = 0.0` (or any value below the train threshold) to bypass
the train without changing piping. This is the pattern used in the
testosb `process_model.ipynb` reference notebook.

### Pattern B — Fixed split factors

```java
manifold.setSplitFactors(new double[] {1.0, 0.0});
```

The HT train sees exactly zero feed. Combine with
`htTrain.setSectionLowFlowThreshold(1.0)` so the train auto-bypasses
cleanly.

### Pattern C — Per-equipment minimum flow

```java
htK1.setMinimumFlow(1.0);
htIC.setMinimumFlow(1.0);
htK2.setMinimumFlow(1.0);
```

Useful when only one or two units in a section have numerical issues at
low flow. The rest of the train still runs.

### Pattern D — Per-area threshold

```java
htTrain.setSectionLowFlowThreshold(1.0);
```

Equivalent to applying Pattern C to every unit in `htTrain`.

### Pattern E — Manual deactivation (no feed change required)

```java
plant.deactivateSection("ht_injection_compressors", "ht_K1");
plant.run();
// ... later ...
plant.activateSection("ht_injection_compressors", "ht_K1");
plant.run();
```

The lock is sticky; subsequent `plant.run()` calls keep the section
bypassed regardless of upstream changes. Use this for "shut-in for
maintenance" scenarios.

## Drop-in snippet for `task_solve/testosb/process_model.ipynb`

The Oseberg / testosb model already wires the manifold using **Pattern A**:

```python
tex_gas_splitter.setFlowRates([-1.0, inp.injection_gas_rate_ht + 0.000001], "MSm3/day")
manifold_upstream_ht_injection_compressors.getUnit("manifold").setSplitFactors(
    [input_parameters.injection_gas_rate_ht_split_to_train_A, -1]
)
```

With `injection_gas_rate_ht = 0.001` MSm3/day and the A/B split at
`0.9999 / 0.0001`, train B receives ~1e-7 MSm3/day — numerically zero and
guaranteed to upset compressor curves and intercoolers if left
un-bypassed. Add two lines immediately **after** the trains are built and
**before** they are added to `oseberg_process`:

```python
# --- Auto-bypass the HT injection trains when their feed is effectively zero ---
# Threshold = 1 kg/hr (any train receiving less than this is skipped this run).
ht_injection_process_A.setSectionLowFlowThreshold(1.0)
ht_injection_process_B.setSectionLowFlowThreshold(1.0)
```

That single change makes the whole plant model robust across the entire
operating range of `injection_gas_rate_ht` (0 → design capacity) without
touching downstream wiring or recycles. Downstream consumers of
`ht_injection_process_A/B` (e.g. `exportgasprocess(...)`) see the
compressor outlet at the *inlet* state with zero flow, which mixers
already handle natively.

### Where to put each configuration pattern in the testosb notebook

| Goal | Pattern | Where in the notebook |
|------|---------|----------------------|
| Set the **fraction** of feed routed to HT injection | A (`setFlowRates([-1.0, x], "MSm3/day")`) | Already in `tex_process` builder (line ~3025) — just set `input_parameters.injection_gas_rate_ht` |
| Set the **A/B split** inside the HT manifold | B (`setSplitFactors`) | Already in cell at line ~4234 — set `input_parameters.injection_gas_rate_ht_split_to_train_A` |
| **Auto-bypass** an entire HT train when its feed is below threshold | D (`setSectionLowFlowThreshold`) | Add immediately after `ht_injection_process_A/B.run()` (cell at line ~4239–4242) |
| **Manually shut in** a train regardless of feed | E (`plant.deactivateSection`) | Add after `oseberg_process.add(...)` calls (line ~4827) and before `oseberg_process.run()` |

Example for full manual lock of train B:

```python
oseberg_process.deactivateSection("HT injection process B", "ht 1st stage compressor")
oseberg_process.run()
# Later, to re-enable:
oseberg_process.activateAll()
oseberg_process.run()
```

A unit test mirroring this exact dual-train + manifold structure lives in
[ProcessModelLowFlowBypassParallelTrainsTest.java](../../../src/test/java/neqsim/process/processmodel/ProcessModelLowFlowBypassParallelTrainsTest.java)
under `dualHtTrainsMirrorsOsebergTestosbNotebookStructure()`.

## End-to-end example (parallel compressor trains)

```java
SystemInterface feedFluid = new SystemSrkEos(298.15, 30.0);
feedFluid.addComponent("methane",  0.88);
feedFluid.addComponent("ethane",   0.08);
feedFluid.addComponent("propane",  0.04);
feedFluid.setMixingRule("classic");

Stream feed = new Stream("feed", feedFluid);
feed.setFlowRate(200_000.0, "kg/hr");
feed.setTemperature(298.15, "K");
feed.setPressure(30.0, "bara");
feed.run();

ProcessSystem manifoldArea = new ProcessSystem("manifold");
Splitter manifold = new Splitter("manifold", feed);
manifold.setSplitFactors(new double[] {1.0 - 1e-6, 1e-6}); // ~0% to HT train
manifoldArea.add(manifold);

ProcessSystem exportTrain = buildCompressorTrain("export", manifold.getSplitStream(0), 90.0);
ProcessSystem htTrain     = buildCompressorTrain("ht_injection_compressors",
                                                 manifold.getSplitStream(1), 250.0);
htTrain.setSectionLowFlowThreshold(1.0); // auto-bypass when feed < 1 kg/hr

ProcessModel plant = new ProcessModel();
plant.add("manifold", manifoldArea);
plant.add("export", exportTrain);
plant.add("ht_injection_compressors", htTrain);
plant.run();

// Verify: HT train is bypassed, export train ran normally.
assert !htTrain.getUnit("ht_injection_compressors_K1").isActive();
assert  exportTrain.getUnit("export_K2").isActive();
```

The runnable counterpart of this snippet lives in
[ProcessModelLowFlowBypassParallelTrainsTest.java](../../../src/test/java/neqsim/process/processmodel/ProcessModelLowFlowBypassParallelTrainsTest.java)
and the single-area suite in
[ProcessSystemLowFlowBypassTest.java](../../../src/test/java/neqsim/process/processmodel/ProcessSystemLowFlowBypassTest.java).

## Limitations and gotchas

- The bypass uses the **primary inlet** of each equipment. For multi-inlet
  units (mixers, multi-stream heat exchangers) the convention is "sum of
  active inlets"; in practice this is handled by upstream units already
  zeroing their outlets.
- The auto-bypass is currently wired for `Splitter`, `Separator`,
  `Heater`, `Compressor`. Pumps, valves, columns, and reactors will run
  normally at very low flow — for those, prefer manual
  `deactivateSection`.
- `setMinimumFlow` does not change `isActive` immediately; the next
  `unit.run(id)` re-evaluates and may bypass.
- After `activateSection`, the next `run()` will *not* automatically
  re-converge the previously-bypassed section if the recalculation cache
  thinks nothing changed. If unsure, mutate an upstream stream (e.g.
  `feed.setFlowRate(...)`) or call `process.clearCache()` if available.
- `ProcessModel.deactivateSection(unitName)` (no area name) deactivates
  in the first area that contains the name; use the two-argument overload
  `deactivateSection(areaName, unitName)` for deterministic behaviour
  when names overlap.

## Related documentation

- [ProcessModel guide](process_model.md) — multi-area flowsheets.
- [ProcessSystem guide](process_system.md) — single-area flowsheets.
- [Controllers](../controllers.md) — for adjusting feed splits at runtime.
- [Process automation API](../json_process_models_and_systems.md) — string-addressable
  access for setting `minimumFlow` programmatically.
