---
name: neqsim-compressor-antisurge-recycle
calculation_basis: "neqsim-java"
version: 0.4.0
description: "Anti-surge recycle for centrifugal compressors in NeqSim: chart generation, AntiSurgeRecycleCalculator, dynamic AntiSurgeController, CompressorAntiSurgeApplication binding, MinimumSpeedRecycleControllerStructure split-range control, speed-solve convergence and recycle power/CO2 savings. USE WHEN: a task must protect a compressor from surge with a recycle loop, coordinate speed and recycle at minimum speed, or value avoided recycle."
last_verified: "2026-10-18"
requires:
  python_packages: []
  java_packages:
    - neqsim
  env: []
  network: false
---

# Compressor Anti-Surge Recycle Setup

This skill explains how to protect a centrifugal compressor against surge in a
NeqSim `ProcessSystem` by adding a recycle (spill-back) loop. It covers the
preferred steady-state helper (`AntiSurgeRecycleCalculator`), the dynamic
reverse-acting PI controller (`AntiSurgeController`), and the application-level
supervisor (`CompressorAntiSurgeApplication`) that can bind directly to real
NeqSim topology objects such as a compressor, hot recycle valve, cold recycle
valve, cooler, mixer, recycle blocks, and `ProcessSystem`. It also explains how
to **auto-generate a compressor chart with surge and stonewall curves** when no
vendor chart is available, because every anti-surge option needs a surge limit
to act against.

The pure-Python helper (`AntiSurgeRecycleModel`) is an educational planning aid
that mirrors NeqSim's proportional anti-surge step so an agent can pre-estimate
the recycle flow and decide whether a chart must be generated first. Real
control design must use the validated NeqSim equipment classes below and a
qualified rotating-equipment review (API 617 / API 692).

## When to Use

Use this skill when:

- A NeqSim compressor operating point can fall to the left of its surge line at
  low throughput (turndown, start-up, trips) and needs recycle protection.
- You must build the anti-surge recycle topology (surge curve, recycle stream,
  discharge splitter or recycle branch, anti-surge valve, `Recycle`, and
  optional cooler/mixer).
- You need an executable dynamic application model where
  `CompressorAntiSurgeApplication` writes hot/cold recycle valve openings and
  optional compressor speed runback to real NeqSim units.
- **No vendor compressor chart is provided** and a chart with surge and
  stonewall curves must be generated from the compressor's design point.
- You want a screening estimate of the recycle flow required to keep the
  compressor off surge before running the full simulation.

Do not use this skill as a substitute for vendor performance maps, anti-surge
controller tuning, dynamic surge/transient analysis, or rotating-equipment
design review.

## Inputs

- Compressor inlet (suction) volumetric flow at the operating point (m3/h).
- Surge-limit flow at the operating head and speed (m3/h). When no vendor chart
  is available, this comes from the generated surge curve.
- Whether a vendor compressor chart is provided (`chart_provided`).
- Existing recycle flow, if any (m3/h).
- For chart generation: a design speed, the number of speed lines, and
  optionally impeller diameter / number of stages for advanced corrections.

## Outputs

- `needs_chart_generation` — whether a chart must be generated before control.
- `in_surge` — whether the operating point is at or below the surge flow.
- `surge_margin_fraction` — `(inlet_flow - surge_flow) / surge_flow`.
- `recommended_recycle_flow` — screening recycle flow to add at suction (m3/h).
- `total_suction_flow` — inlet flow plus recommended recycle (m3/h).
- `recycle_warning` — `ok`, `recycle`, or `surge`.
- Assumptions and limitations.

## Engineering Method

A centrifugal compressor surges when the inlet volumetric flow drops below the
surge flow at the current head/speed. Anti-surge control opens a recycle line
from discharge back to suction to keep the **total** suction flow above the
surge limit plus a margin.

For steady-state recycle sizing and initialization, prefer
`AntiSurgeRecycleCalculator` for a charted compressor. It runs the compressor,
checks natural inlet flow against the surge-control line, and iterates a cooled
recycle stream until the target distance-to-surge margin is met. Use the older
splitter-based `Calculator` / `AntiSurgeCalculator` pattern only when you need
to reproduce an existing flowsheet that already uses a discharge splitter.

The legacy splitter-based topology is:

1. A **surge curve** on the compressor chart provides the surge flow versus head.
2. A low-flow **recycle stream** is added into the compressor suction.
3. A **splitter** on the compressor discharge creates a forward branch
   (`getSplitStream(0)`) and a recycle branch (`getSplitStream(1)`).
4. An **anti-surge `Calculator`** (legacy name-prefix API) or typed
  `AntiSurgeCalculator` reads the compressor and writes the splitter recycle
  flow each iteration. Internally it compares the inlet flow to the surge flow:
  far from surge (`inlet_flow > 1.2 * surge_flow`) it drives recycle to a
  minimum; otherwise it adds a proportional, capped step
  `0.5 * (surge_flow - inlet_flow)` to the recycle branch.
5. An **anti-surge valve** on the recycle branch drops the discharge pressure
   back to suction pressure.
6. A **`Recycle`** unit closes the loop, feeding the valve outlet back into the
   placeholder suction recycle stream.

### Generating a Compressor Chart When None Is Given

The anti-surge loop needs a surge limit, so if no vendor chart exists a chart
must be generated first. NeqSim builds a chart from the compressor's design
operating point and **automatically populates both the surge curve and the
stonewall curve**:

- `Compressor.generateCompressorChart("normal curves", numberOfSpeeds)` builds a
  multi-speed chart whose lowest-flow points form the surge curve and whose
  highest-flow points form the stonewall curve.
- `CompressorChartGenerator` gives finer control (chart type, Reynolds/Mach
  corrections, multistage surge correction, impeller diameter) and also returns
  a chart with surge and stonewall curves attached.

After generation, the surge flow at the operating head is available through
`Compressor.getSurgeFlowRate()` and the distance to surge through
`Compressor.getDistanceToSurge()`.

The `AntiSurgeRecycleModel` mirrors step 4 so you can estimate the recycle flow
and flag whether chart generation (`needs_chart_generation`) is required before
wiring the loop.

## Python Usage Pattern

Screening estimate of the recycle flow (no NeqSim required):

```python
from compressor_antisurge_recycle import AntiSurgeRecycleModel

model = AntiSurgeRecycleModel()
plan = model.plan(
    inlet_flow=4200.0,   # m3/h at suction
    surge_flow=5000.0,   # m3/h surge limit at operating head
    chart_provided=True,
    current_recycle=0.0,
)
print(plan.recommended_recycle_flow, plan.recycle_warning)
```

Generate a compressor chart (with surge and stonewall curves) when none is
given, using NeqSim through the `neqsim` package:

```python
from neqsim import jneqsim

# `compressor` is an existing neqsim.process.equipment.compressor.Compressor
compressor.setSpeed(8000.0)   # design speed (rpm)
compressor.run()              # solve the design point first
compressor.generateCompressorChart("normal curves", 5)  # 5 speed lines

# Surge and stonewall curves are now populated automatically.
chart = compressor.getCompressorChart()
surge_flow = compressor.getSurgeFlowRate()
distance_to_surge = compressor.getDistanceToSurge()
```

Finer control with the generator (optional corrections):

```python
from neqsim import jneqsim

generator = jneqsim.process.equipment.compressor.CompressorChartGenerator(compressor)
generator.setChartType("interpolate and extrapolate")
generator.enableAdvancedCorrections(numberOfStages)  # Reynolds + Mach + multistage
chart = generator.generateCompressorChart("normal curves", 5)
compressor.setCompressorChart(chart)
```

Wire the anti-surge recycle loop (generic data, after a chart exists):

```python
from neqsim import jneqsim

splitter_pkg = jneqsim.process.equipment.splitter
valve_pkg = jneqsim.process.equipment.valve
util_pkg = jneqsim.process.equipment.util

suction_pressure = compressor.getInletStream().getPressure("bara")

# 2. Low-flow placeholder recycle stream into the compressor suction.
recycle_gas = compressor.getInletStream().clone()
recycle_gas.setName("anti surge recycle gas")
recycle_gas.setFlowRate(1.0, "kg/hr")
recycle_gas.run()
process.add(recycle_gas)

# 3. Splitter on the compressor discharge: branch 0 forward, branch 1 recycle.
gas_splitter = splitter_pkg.Splitter("anti surge splitter", compressor.getOutletStream(), 2)
gas_splitter.run()
process.add(gas_splitter)

# 4. Anti-surge Calculator (name MUST start with "anti surge calculator").
anti_surge_calc = util_pkg.Calculator("anti surge calculator 1")
anti_surge_calc.addInputVariable(compressor)
anti_surge_calc.setOutputVariable(gas_splitter)
process.add(anti_surge_calc)

# 5. Anti-surge valve on the recycle branch back to suction pressure.
anti_surge_valve = valve_pkg.ThrottlingValve("anti surge valve", gas_splitter.getSplitStream(1))
anti_surge_valve.setOutletPressure(suction_pressure, "bara")
anti_surge_valve.run()
process.add(anti_surge_valve)

# 6. Recycle unit closes the loop into the placeholder suction stream.
recycle = util_pkg.Recycle("recycle anti surge")
recycle.addStream(anti_surge_valve.getOutletStream())
recycle.setOutletStream(recycle_gas)
recycle.setTolerance(1e-2)
process.add(recycle)

process.run()
```

The forward process continues on `gas_splitter.getSplitStream(0)`.

Preferred steady-state helper for a charted compressor:

```java
AntiSurgeRecycleCalculator calc = new AntiSurgeRecycleCalculator(compressor, suctionStream);
calc.setSurgeControlMargin(0.10);
calc.setRecycleCoolerTemperature(35.0, "C");
AntiSurgeRecycleCalculator.Result result = calc.solve();
```

## Dynamic Anti-Surge Control (AntiSurgeController)

The `Calculator`-driven loop above is a **steady-state** recycle solver. For a
**dynamic (transient)** anti-surge response, NeqSim provides a dedicated
reverse-acting PI controller, `AntiSurgeController`
(`neqsim.process.controllerdevice.AntiSurgeController`), that reads
`Compressor.getDistanceToSurge()` and drives a recycle `ThrottlingValve` open as
the margin falls below the set point, then closes it again on recovery.

```python
from neqsim import jneqsim

controllerdevice = jneqsim.process.controllerdevice

# `recycle_valve` is the anti-surge ThrottlingValve on the recycle branch.
asc = controllerdevice.AntiSurgeController("anti-surge", compressor, recycle_valve)
asc.setSurgeMarginSetPoint(0.10)   # protect a 10% distance-to-surge margin
asc.setProportionalGain(400.0)     # percent opening per unit margin error
asc.setIntegralTime(20.0)          # s
asc.setOpeningRange(0.0, 100.0)    # valve opening clamp (%) with anti-windup
asc.setActive(True)
recycle_valve.addController("anti-surge", asc)
```

Control law each transient step: `error = setPoint - distanceToSurge`,
`integral += Kp/Ti * error * dt`, `opening = clamp(Kp*error + integral)` with
anti-windup. A reproducible benchmark,
`neqsim.process.util.scenario.AntiSurgeDynamicBenchmark`, drives the real
controller against a transparent first-order gas-path surrogate
(`m_next = m - disturbance*dt + authority*(opening/100)*dt`) and is the preferred
way to verify or tune the control law without solver fragility.

**Critical gotchas when building a full dynamic recycle flowsheet:**

- A fixed-factor `Splitter` (`setSplitFactors([0.97, 0.03])`) **pins the recycle
  fraction** in dynamic mode, so the anti-surge valve has no authority over the
  actual recycle flow — it can reach 100% open with no effect. Let the recycle
  flow be set by the valve (`Cv`/resistance), or keep the steady-state
  `Calculator` pattern.
- Once the operating point crosses left of the surge line,
  `getDistanceToSurge()` **clamps at -1.0 and the steady solver cannot recover**;
  a flowsheet driven into deep surge will not self-heal even after the inlet is
  reopened. Apply gradual/ramped disturbances and keep the machine off deep surge.
- Aggressive proportional gain can slam the recycle valve to minimum opening,
  starve a stream, and trigger an SRK flash `NaN`
  (`PhaseSrkEos:molarVolume ... NaN`). Keep gains moderate.

Dynamic controller tuning and transient surge analysis still require qualified
rotating-equipment review (API 617 / API 692).

## Application-Level Dynamic Topology Binding

For realistic dynamic studies with coordinated pressure, speed, hot recycle, and
cold recycle behavior, use `CompressorAntiSurgeApplication`. It is a
deterministic supervisory scan layer that can write directly to real NeqSim
objects and then advance the bound process one transient step.

```java
CompressorAntiSurgeApplication application = new CompressorAntiSurgeApplication("export compression");
CompressorAntiSurgeApplication.StageApplication stage = application.addStage("K-101");

CompressorAntiSurgeApplication.TopologyBinding binding = stage.bindTopology(
  process,
  compressor,
  hotRecycleValve,
  coldRecycleValve,
  recycleCooler,
  suctionMixer,
  hotRecycle,
  coldRecycle);
binding.enableSpeedControl(95.0, 25.0, 8500.0, 12500.0, 150.0);

application.setRunningMode();
application.runDynamicStep(null, 0.25);
```

When the scan input is `null`, the stage reads the bound compressor margin and
inlet flow where available, falls back to the stage design basis where needed,
writes the hot and cold recycle valve openings, applies the optional compressor
speed command/runback, and advances the bound `ProcessSystem` with
`runTransient()`. Keep `Recycle` blocks algebraic unless the specific class has
transient inventory support; the valve, compressor, cooler, mixer, and any
volume-capable equipment carry the dynamic response.

`CompressorAntiSurgeApplication` is still a simulation and advisory layer. Its
certification status remains `NOT_CERTIFIED_FOR_PROTECTION`; use it for
engineering studies, training, digital twins, and commissioning evidence, not as
a certified machinery-protection package.

## MCP `runProcess` Physical Topology Contract

Use two distinct JSON surfaces deliberately:

- Compressor property `antiSurge` configures the compressor's embedded
  screening control. It does not create or certify a physical recycle path.
- Root-level `antiSurgeSystems` binds
  `CompressorAntiSurgeApplication` to named units that already exist in the
  submitted `process` topology. It never synthesizes hidden valves, coolers,
  mixers, or recycle blocks.

A single-area cold-recycle declaration has this shape:

```json
{
  "autoSizing": {"enabled": true},
  "antiSurgeSystems": [
    {
      "name": "Export anti-surge",
      "stages": [
        {
          "name": "Stage 1",
          "compressor": "Comp",
          "suctionMixer": "Suction Mixer",
          "aftercooler": "Aftercooler",
          "coldRecycleValve": "Cold ASV",
          "coldRecycle": "Cold Recycle",
          "recycleDesign": {
            "controlMargin": 0.10,
            "valvePressureDrop": 10.0,
            "pipingVolume": 1.0,
            "requiredResponseTime": 5.0
          },
          "speedControl": {
            "dischargePressureSetPoint": 40.0,
            "minimumSpeed": 5000.0,
            "maximumSpeed": 15000.0,
            "speedGain": 40.0,
            "recycleRunbackRate": 80.0
          }
        }
      ]
    }
  ]
}
```

The referenced names must resolve to the expected concrete types:
`Compressor`, `Mixer`, optional `Cooler`, `ThrottlingValve`, and `Recycle`.
At least one complete hot or cold recycle path is required. For a multi-area
`ProcessModel`, every system also requires an `area` matching a submitted area;
unqualified and unknown-area definitions return bounded per-system failures.

Every stage needs an active surge curve. Supply `properties.compressorChart`,
enable `autoSizing`, or set `generateScreeningMap: true` on the stage. The
response reports truthful map provenance:

- `submitted`: chart was present in the submitted canonical process JSON.
- `auto_sized_screening`: `autoSizing` generated the chart earlier in the run.
- `stage_generated_screening`: the anti-surge stage generated the chart.

The latter two set `screeningGradeMap: true`. They are estimates, not vendor
maps. `generatedScreeningMapCount` counts all configured stages using a
screening-grade map, regardless of which generation path created it.

The response `antiSurgeSystems` object contains configured/failed counts,
per-stage physical binding and map provenance, optional speed-control status,
and `runCommissioningChecks()` evidence. Its certification status is always
`NOT_CERTIFIED_FOR_PROTECTION`. The canonical `processDefinition` preserves the
declaration, and the generated Python replays that canonical post-design JSON.

When a canonical response is replayed, its exported compressor chart is now an
explicitly submitted chart for that new request. Therefore its provenance is
`submitted`; replay does not generate another map. This makes JSON round trips
stable while preserving the original run's generation evidence in that run's
response.

## Compressor Pressure-Target Solves

For `Compressor.setSolveSpeed(true)`, check `isSpeedSolveConverged()` after `run()`
before accepting an optimization point. Read actual pressure from the outlet
stream and the signed pressure error in bar from `getSpeedSolvePressureResidual()`.
`getOutletPressure()` retains the requested setpoint, including after failure.
The returned state uses the fixed-speed forward calculation, including anti-surge
flow changes; these can leave the requested pressure unmet. `setLimitSpeed(false)`
allows extrapolation, so pressure convergence does not establish mechanical
feasibility. Check speed and operating-envelope constraints separately.
`ProductionOptimizer` treats `Compressor.SpeedSolveException` as an infeasible
trial and continues searching without evaluating stale outputs; unrelated
exceptions propagate. Its hard `compressorSpeedSolve` constraint also rejects
returned unconverged results even with capacity analysis enabled. Keep this gate
when integrating chart-based compressor models into optimizers. See the
[compressor curves guide](https://equinor.github.io/neqsim/process/equipment/compressor_curves.html)
and `CompressorSpeedSolveConsistencyTest` for the tested contract.

## Minimum-Speed Recycle Coordination (`MinimumSpeedRecycleControllerStructure`)

`neqsim.process.controllerdevice.structure.MinimumSpeedRecycleControllerStructure`
coordinates pressure control between compressor speed and recycle-valve opening when
the compressor reaches its minimum speed. Use it to stop opposing speed demand and
recycle-valve opening in a compressor pressure loop (control-optimization tasks).

1. **Split-range output:** the pressure controller output is divided at a configurable
   transition (e.g. 75 %).
2. **Speed command range:** above the transition (75 % to 105 %) recycle addition is
   zero and speed rises linearly from minimum (75 %) to maximum (100 %).
3. **Inverse recycle addition:** below the transition (0 % to 75 %) speed is held at
   minimum while recycle addition increases as pressure output falls.
4. **Latch on entry:** on lower-range entry the previously selected recycle command is
   latched and pressure-derived recycle is added to that baseline.
5. **High-selector protection:** independent anti-surge and suction-pressure demands
   join a high selector, $R_{selected} = \max(R_{latch} + R_{add}, R_{AS}, R_{suction})$,
   so protection authority is never suppressed.
6. **Unwind before speed increase:** on rising pressure, pressure-derived recycle
   unwinds to the latched baseline before speed increases.
7. **Dynamic saturation floor:** a lower output limit (`latched * 74 / 100`) prevents
   integral windup.

```java
import neqsim.process.controllerdevice.ControllerDeviceInterface;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.controllerdevice.structure.MinimumSpeedRecycleControllerStructure;

ControllerDeviceInterface pressureController = new ControllerDeviceBaseClass("PIC-0205A");
ControllerDeviceInterface antiSurgeController = new ControllerDeviceBaseClass("UIC-0231");
ControllerDeviceInterface suctionController = new ControllerDeviceBaseClass("PIC-0131");

// (pressureCtrl, antiSurgeCtrl, suctionCtrl, transitionPct, maxOutputPct, minSpeedPct, maxSpeedPct)
MinimumSpeedRecycleControllerStructure coordStructure = new MinimumSpeedRecycleControllerStructure(
    pressureController, antiSurgeController, suctionController, 75.0, 105.0, 75.0, 100.0);

// In the transient loop or update step:
coordStructure.update(pressureControllerOutput, antiSurgeOutput, suctionPressureOutput);

double speedCommand = coordStructure.getSpeedOutput();             // % speed
double recycleAddition = coordStructure.getRecycleAddition();      // % recycle addition
double recycleCommand = coordStructure.getLatchedRecycleOutput();  // % latched baseline
```

## Energy and CO2 Savings from Avoided Recycle

When recycle opens uncoordinatedly while the compressor runs at minimum or elevated
speed, extra gas is re-compressed without increasing net forward production.

1. Shaft power: $P_{shaft} = \dfrac{\dot{m}_{net}\, h_{poly}}{\eta_p (1 - \alpha_{recycle})}$,
   with recycle fraction $0 \le \alpha < 1$.
2. Power penalty: $\Delta P = P_0\,\dfrac{\alpha}{1 - \alpha}$.
3. Fuel and CO2: $\Delta \dot{m}_{fuel} = \dfrac{\Delta P}{\eta_{driver}\,LHV}$ and
   $\Delta \dot{m}_{CO2} = \Delta \dot{m}_{fuel}\, e_{CO2}$.
4. Annual saving (MNOK/yr) $= \dfrac{\Delta m_{CO2,yr}\,\mathrm{Tax}_{CO2} + \Delta m_{fuel,yr}\,\mathrm{Value}_{gas}}{10^6}$.

Recipe: build the recompressor with a fixed net feed, then sweep the recycle fraction
by setting the suction flow to `net / (1 - alpha)` and re-running stream and compressor;
the power difference to $\alpha = 0$ is the penalty. Quantify annual MWh, fuel-gas
tonnes, CO2 tonnes and cost over the operating hours, and compare the dynamic
pressure, speed, recycle, power and CO2 time series.

## Fixed Minimum Flow Without a Numerical Recycle Loop

For steady-state screening with a prescribed minimum suction flow and a return cooled
to the net-feed suction state, use
`neqsim.process.equipment.compressor.MinimumFlowSpill(name, netFeed)`.
Set `setMinimumInletFlow(value, unit)` and configure `getCompressor()`.
Add the assembly once to the `ProcessSystem`; the net forward outlet is
`getOutletStream()`, while `getSpillStream()` is an internal discharge spill.
`getSpillFlow(unit)` uses suction conditions even for actual volume.
Use `getPower(unit)` for gross compression and `getSpillCoolingDuty(unit)` for
ideal heat rejection. The assembly delegates capacity constraints to its compressor.
Disable internal AntiSurge flow modification. Use physical valve, cooler and recycle
equipment for composition changes, condensation, finite cooler approach or dynamics.
See the [compressor equipment guide](https://equinor.github.io/neqsim/process/equipment/compressors.html)
and `MinimumFlowSpillTest` for the executed synthetic material and energy checks.

## Validation Checklist

- The compressor has a chart with an active surge curve before the loop runs;
  if not, generate one and confirm `getSurgeCurve().isActive()` is true.
- For steady-state recycle initialization, use `AntiSurgeRecycleCalculator` when
  practical; if using the legacy splitter path, the anti-surge `Calculator` name
  starts with `"anti surge calculator"` or the typed `AntiSurgeCalculator` is
  used.
- For the legacy splitter path, the `Calculator` input is the `Compressor` and
  the output is the discharge `Splitter`.
- The anti-surge valve sits on `getSplitStream(1)` and drops to suction pressure.
- The `Recycle` outlet stream is the placeholder suction recycle stream.
- After `process.run()`, the total suction flow exceeds the surge flow plus the
  intended margin (`getDistanceToSurge()` is positive).
- Recycle convergence tolerance is set (for example `setTolerance(1e-2)`).
- For `CompressorAntiSurgeApplication` topology binding, hot/cold recycle valves
  are real `ThrottlingValve` units, speed-control limits are bounded, and
  `runDynamicStep(...)` is used only after the process has a converged initial
  state.
- For MCP `runProcess`, use root-level `antiSurgeSystems` only after all named
  physical units are present in `process`; require `area` for every multi-area
  system and inspect `configuredCount`, `failedCount`, `mapProvenance`,
  `screeningGradeMap`, commissioning checks, and certification status.

## Common Mistakes

- Naming the legacy calculator anything that does not start with
  `"anti surge calculator"` — the legacy anti-surge logic then never triggers.
- Wiring the anti-surge valve to the forward branch `getSplitStream(0)` instead
  of the recycle branch `getSplitStream(1)`.
- Running the loop without a surge curve, so there is no surge flow to compare
  against (generate a chart first when no vendor chart is given).
- Using a zero or negative placeholder recycle flow that the `Recycle` cannot
  converge from; start from a small positive flow (for example 1 kg/hr).
- Reading the surge flow at the wrong head or speed — the surge flow must match
  the operating head and speed.
- Treating the generated chart as a vendor-validated map; generated charts are
  estimates for modelling, not design certification.
- For dynamic studies, leaving a fixed-factor `Splitter` on the recycle branch so
  the `AntiSurgeController` valve has no authority over the recycle flow.
- Driving a dynamic recycle flowsheet straight into deep surge, where
  `getDistanceToSurge()` clamps at -1.0 and the steady solver cannot recover.
- Loading a VENDOR map into the default chart. `Compressor`'s default `"fan law"`
  `CompressorChart` fits one quadratic of H/N^2 vs Q/N through all speed lines,
  which on a real vendor map shifts discharge pressure by 10-15 % at the outer
  speed lines (and moves the surge flow with it). Call
  `compressor.setCompressorChartType("interpolate and extrapolate")` BEFORE
  `getCompressorChart().setCurves(...)`; `Pump.setPumpChartType(...)` has the same
  option. Units for `setCurves`: actual inlet m3/hr, head `kJ/kg` or `meter`,
  polytropic efficiency in PERCENT.
- Checking anti-surge valve capacity with the default `ThrottlingValve` sizing.
  The default method uses xT = 0.137 without choking and under-rates a globe
  anti-surge valve by a factor ~2 at pressure ratio 4. Set
  `getMechanicalDesign().setValveSizingStandard("IEC 60534")`, then on
  `getValveSizingMethod()` call `setxT(0.70)`, `setFL(0.90)` (or the vendor's
  values) and `setAllowChoked(True)` before `setCv(cv, "US")`.
- Setting the control-line margin from habit instead of the machine's anti-surge
  write-up. Vendor controllers store the surge-control line as a function
  generator in (Q^2, DP/Ps) coordinates; the flow margin is
  sqrt(Q^2_SCL / Q^2_SLL) - 1 at equal DP/Ps (~20 % in Q^2 = ~10 % in flow is
  typical), then `compressor.setSurgeControlMargin(margin)`.

## Limitations

- Screening logic only; it does not tune an anti-surge controller or set
  recycle valve `Cv`, response time, or surge control line offset.
- The Python helper does not perform dynamic surge, ESD, or transient recycle
  analysis; for dynamic response use NeqSim's `AntiSurgeController` /
  `AntiSurgeDynamicBenchmark` under qualified review.
- Generated compressor charts are model estimates, not vendor performance maps.
- It does not replace API 617 / API 692 rotating-equipment design and review.
- Results require qualified human review before any design or operating use.

## Multi-Body Compressor Trains on One Shaft (common speed)

When several compressor bodies sit on **one driver shaft** (a single gas turbine
or motor, often through a gearbox) they must all turn at the **same speed**. A
2- or 3-body recompression string is the classic case. Model it with
`neqsim.process.equipment.compressor.CompressorShaft`, NOT by letting each body
solve its own speed.

**Degrees of freedom.** A shared shaft has exactly **one** mechanical degree of
freedom — the common speed — and exactly **one** controlled target: the string's
**final discharge pressure**. Every intermediate inter-body pressure is a
*result*, not a spec, and must be allowed to **float** off the charts.

| Approach | DOF accounting | Verdict |
|---|---|---|
| Fix every stage outlet pressure **and** set a common speed | over-constrained | ❌ non-physical |
| Adjust ONE common speed → hit the final discharge; intermediates float | 1 variable, 1 target | ✅ correct |

**Pattern.** Put each body in fixed-speed, chart-forward mode and iterate the one
common speed until the reference (last) body hits the target discharge:

```python
CompressorShaft = jneqsim.process.equipment.compressor.CompressorShaft
shaft = CompressorShaft("recompression shaft (single GT)")
shaft.addCompressor(body1)   # LP body (lowest suction)
shaft.addCompressor(body2)
shaft.addCompressor(body3)   # HP body = reference
shaft.setSpeedBounds(8000.0, 16000.0)
# re-run the whole flowsheet between speed guesses so inter-body streams,
# scrubbers and mixers update:
run_proxy = jpype.JProxy("java.lang.Runnable", dict(run=lambda: process.run()))
shaft.solveSpeed(body3, 49.0, "bara", run_proxy)   # one speed -> 49 bara at HP discharge
rpm = shaft.getSpeed()
mw = shaft.getTotalPower() / 1e6
```

**Shared pressure nodes.** Where another unit ties into an interstage (e.g. a
2nd-stage separator gas that joins the recompressor between bodies), model it as
a **pressure equality**, not a second spec: slave that unit's pressure to the
floating interstage discharge (a setter each iteration) or drop a small adapting
valve there. This removes a DOF rather than adding one. A `Mixer` at the join
already resolves to the lowest inlet pressure, so a small let-down is automatic.

**Fixed-/single-speed drivers.** If the driver is a constant-speed motor (no
variable-speed drive) speed is **not** a DOF — do **not** iterate it. Use
`shaft.runAtFixedSpeed(rpm, run_proxy)`: the discharge floats off the chart at
the locked speed and any pressure spec is met by anti-surge recycle, suction
throttling, or inlet guide vanes — never by moving speed. Inlet guide vanes are a
**first-class** fixed-speed control on `Compressor`:
`comp.setInletGuideVaneOpening(f)` (`f=1` fully open) or `comp.setGuideVaneAngle(deg)`
closes the vanes to reduce head and efficiency **and** lower the surge flow (via a
configurable `InletGuideVaneModel`), so `getDistanceToSurge()` reflects the shifted
surge line — distinct from moving speed. If you have vendor **IGV-position** maps
(a full chart per vane position), attach a `CompressorChartIGV` family with
`comp.setInletGuideVaneChart(family)`; it interpolates the positions into the active
chart at each opening (and bypasses the parametric model). Use the parametric
`InletGuideVaneModel` for screening / fixed-speed discharge control, and
`CompressorChartIGV` to supply real per-position vendor maps.

**Single body.** A `CompressorShaft` with one compressor also works: `solveSpeed`
just finds that one machine's speed for its discharge target. So export /
re-injection / gas-lift machines (one body each on their own shaft) use the same
API — separate shafts keep separate speeds.

**Anti-surge coexists.** The per-body anti-surge loops (recycle splitter, valve,
`Recycle`, `AntiSurgeCalculator`) stay attached and keep protecting each body;
they adjust *recycle flow*, while the shaft sets *speed*. Apply the shaft solve
**after** the charts and anti-surge are active. `solveSpeed` uses a bracketed
false-position (Illinois) secant — the speed↔discharge map is smooth and
monotonic (higher speed → higher discharge), so it converges superlinearly. Each
iteration re-solves the whole flowsheet, so on a large multi-train plant make the
shaft solve **opt-in** and solve each train's shaft in turn.

**Parallel machines and multi-stage strings.** Real duties often use two parallel
bodies (A/B) rather than one machine: split the feed 50/50, give each body its own
anti-surge loop, and commingle with a `Mixer`. Splitting one large machine into two
halves the per-machine gas load (a suction scrubber that reads ~168 % of design on
one body drops to ~80 % on two). For a multi-stage duty (e.g. re-injection),
commingle the parallel 1st-stage discharges then feed a common 2nd stage at an
interstage pressure. Route a recompression train's HP discharge back to the gas
header, not straight into the export suction.

**Process-integrated control (`CompressorShaftCalculator`).** Instead of the
external `solveSpeed` callback, add a `CompressorShaftCalculator` to the
`ProcessSystem`; it converges the one common speed **inside** `process.run()`
(one damped secant step per pass, like `AntiSurgeCalculator`) so the shaft speed
converges together with the recycles in a single run — no separate full-field
solves. Add it **after** the compressor bodies / anti-surge.

```python
CompressorShaftCalculator = jneqsim.process.equipment.util.CompressorShaftCalculator
shaft = CompressorShaft("recompression shaft (single GT)")
shaft.addCompressor(body1); shaft.addCompressor(body2); shaft.addCompressor(body3)
shaftCalc = CompressorShaftCalculator("shaft speed", shaft, body3, 49.0, "bara")
shaftCalc.setSpeedBounds(8000.0, 16000.0)
process.add(shaftCalc)
process.run()          # shaft speed converges with the recycles
rpm = shaftCalc.getSpeed()
```

**Feasibility result + pressure control (eCalc-style).** A single-speed string
cannot make an arbitrary pressure — the max-speed curve is a ceiling and the
min-speed curve is a floor. Both solvers **saturate and flag** instead of
crashing, and expose a `CompressorShaft.SolveResult` (read with
`getLastSolveResult()`, or the shortcut `isFeasible()`). The two speed-bound
bracket evaluations give the **min-/max-achievable** discharge pressures for free.

```python
shaft.solveSpeed(body3, 49.0, "bara", run_proxy)
r = shaft.getLastSolveResult()
if not r.isFeasible():
    # r.getStatus(): PRESSURE_ABOVE_MAX_SPEED / PRESSURE_BELOW_MIN_SPEED / OVER_POWER / STONEWALL / SURGE
    ceiling = r.getMaxAchievablePressure()   # most the string can make
# too-low target -> shed the surplus head instead of flagging infeasible:
PressureControl = jneqsim.process.equipment.compressor.CompressorShaft.PressureControl
shaft.setPressureControl(PressureControl.DOWNSTREAM_CHOKE)   # or UPSTREAM_CHOKE / ASV_RECYCLE
```

`CompressorShaftCalculator` carries the same `getLastSolveResult()` /
`isFeasible()` / `setPressureControl(...)`, so an optimizer or `evaluate()` loop
can gate on `shaftCalc.isFeasible()` directly. Downstream, `Mixer.isPressureMismatch()`
flags where an unmet pressure collapses a commingling node to the lowest inlet —
the signal that an upstream machine missed spec. A worked end-to-end example
(three-stage separation + shared-shaft recompression + control) is in the NeqSim
repo notebook `examples/notebooks/CompressorShaft_ThreeStageSeparation.ipynb`.

## Related NeqSim Functionality

- `neqsim.process.equipment.compressor.CompressorShaft` — groups several
  compressor bodies on one shaft at a single common speed;
  `addCompressor(...)`, `solveSpeed(reference, targetP, unit, runnable)` (iterate
  one speed to the final discharge, intermediates float),
  `runAtFixedSpeed(rpm, runnable)` (constant-speed drivers), `setSpeed(...)`,
  `setSpeedBounds(...)`, `getSpeed()`, `getTotalPower()`.
- `neqsim.process.equipment.compressor.Compressor` —
  `generateCompressorChart(...)`, `getCompressorChart()`,
  `getSurgeFlowRate()`, `getDistanceToSurge()`, `setSpeed(...)`.
- `neqsim.process.equipment.compressor.CompressorChart` —
  `getSurgeCurve()`, `getStoneWallCurve()`, `generateSurgeCurve()`,
  `generateStoneWallCurve()`, `checkStoneWall(...)`.
- `neqsim.process.equipment.compressor.CompressorChartGenerator` —
  `setChartType(...)`, `enableAdvancedCorrections(...)`,
  `generateCompressorChart(...)`.
- `neqsim.process.equipment.compressor.AntiSurgeRecycleCalculator` — preferred
  steady-state recycle helper for charted compressors
  (`setSurgeControlMargin`, `setRecycleCoolerTemperature`, `solve`).
- `neqsim.process.equipment.compressor.CompressorAntiSurgeApplication` —
  application-level supervisor with `StageApplication.bindTopology(...)`,
  `TopologyBinding.enableSpeedControl(...)`, `scan(...)`, and
  `runDynamicStep(...)` for direct writeback to real hot/cold recycle valves and
  compressor speed.
- `neqsim.process.equipment.splitter.Splitter` — discharge split into forward
  and recycle branches.
- `neqsim.process.equipment.util.Calculator` — steady-state anti-surge engine
  (`runAntiSurgeCalc`) triggered by the `"anti surge calculator"` name prefix.
- `neqsim.process.controllerdevice.AntiSurgeController` — dynamic reverse-acting
  PI controller on `getDistanceToSurge()` driving a recycle valve
  (`setSurgeMarginSetPoint`, `setProportionalGain`, `setIntegralTime`,
  `setOpeningRange`, `setActive`).
- `neqsim.process.util.scenario.AntiSurgeDynamicBenchmark` — reproducible
  surrogate benchmark for verifying/tuning the dynamic control law.
- `neqsim.process.equipment.valve.ThrottlingValve` — anti-surge recycle valve.
- `neqsim.process.equipment.util.Recycle` — closes the recycle loop.

The `total_suction_flow` (net forward flow + recycle) computed here is the
**actual compressor throughput**. When you go on to compute polytropic
head/efficiency or shaft power from measured conditions, use this actual
throughput — not the metered net/export flow. Using net flow while the recycle is
open understates suction volume and power and mis-locates the operating point on
the surge map (a common error when diagnosing compressor degradation at
turndown).

In Python these classes are reachable through the `neqsim` package (for example
`from neqsim import jneqsim`).

## References

- NeqSim: https://github.com/equinor/neqsim
- NeqSim Community Skills: https://github.com/equinor/neqsim-community-skills
- API Standard 617 — Axial and Centrifugal Compressors.
- API Standard 692 — Surge Control for Centrifugal Compressors.
