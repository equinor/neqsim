---
name: neqsim-troubleshooting
description: "Troubleshooting playbook for common NeqSim failures. USE WHEN: a simulation fails to converge, produces unexpected results, throws exceptions, or gives zero/NaN values. Covers flash non-convergence, recycle divergence, equipment errors, phase identification issues, and numerical problems."
last_verified: "2026-07-04"
---

# NeqSim Troubleshooting Playbook

Ranked recovery strategies for common failure modes. Try steps in order — stop when the issue is resolved.

## Flash Non-Convergence

**Symptom:** `TPflash()` or other flash throws exception or returns wrong phase count.

| Step | Action | Why It Helps |
|------|--------|-------------|
| 1 | `fluid.setMultiPhaseCheck(true)` | Enables detection of liquid-liquid splits and 3-phase regions |
| 2 | Try a different EOS: `SystemPrEos` instead of `SystemSrkEos` | PR handles heavier components better near critical |
| 3 | Check if near critical point — if $T/T_c > 0.95$ and $P/P_c > 0.8$, use volume-translated EOS: `SystemSrkEosvolcor` | Standard cubic EOS has largest errors near critical |
| 4 | Add `fluid.init(0); fluid.init(1);` before flash to force re-initialization | Clears stale internal state from prior calculations |
| 5 | Slightly perturb T or P (1 K or 0.1 bar) and re-flash | Exact phase boundary conditions can trap the solver |
| 6 | For mixtures with water + hydrocarbons, use CPA: `SystemSrkCPAstatoil` with mixing rule `10` | SRK/PR cannot model hydrogen bonding — water phase behavior is wrong |
| 7 | Check component names against `src/main/resources/data/COMP.csv` | Misspelled component names silently fail or load wrong parameters |

## NaN / Zero Enthalpy After Clone + Re-Flash

**Symptom:** A reservoir/well stream flashes fine at source, but after passing
through `WellFlow`, `PipeBeggsAndBrills`, or an isenthalpic choke (or after
`clone()` + re-flash) it returns NaN enthalpy — often surfacing as NaN
compressor power or a non-finite separator duty downstream.

| Step | Action | Why It Helps |
|------|--------|-------------|
| 1 | Check `fluid.getEnthalpy()` is finite right after the source `TPflash()` + `initProperties()` | Confirms the problem appears only after re-flash, not at the source |
| 2 | If the fluid uses hand-built `addTBPfraction` pseudo-components, rebuild it from a real PVTsim/E300 characterization (`EclipseFluidReadWrite.read`) and apply composition via `setMolarComposition()` | Degenerate ideal-gas Cp coefficients on hand-built fractions lose enthalpy on re-flash; characterized fractions carry full Cp data |
| 3 | Enable `fluid.setMultiPhaseCheck(true)` before the choke | A trapped/undetected third phase at low pressure can yield a non-finite mix enthalpy |
| 4 | For reservoirs, keep a clean clone of the source fluid for the well stream (bypass `SimpleReservoir` recombination, which can corrupt enthalpy) | Recombined reservoir fluids can carry inconsistent enthalpy state |

> **Dynamic-depletion caveat:** `SimpleReservoir.runTransient()` rewrites each
> producer stream's fluid in place (via `setMolarComposition` / `setPressure`)
> with the depleted, recombination-corrected reservoir fluid. If a downstream
> topside `ProcessModel` is re-run *after* a reservoir transient step, those
> overwritten producer fluids can reintroduce NaN enthalpy through the inlet
> choke. For lifetime studies, prefer holding a rigorous **steady** facility
> snapshot at plateau for equipment loading and using an **analytical**
> plateau+decline production profile for lifetime volumes/economics, rather than
> re-running the topside off each transient depletion step.

## Recycle Non-Convergence

**Symptom:** `process.run()` completes but recycle did not converge, or throws after max iterations.

| Step | Action | Why It Helps |
|------|--------|-------------|
| 1 | Increase tolerance: `recycle.setTolerance(1e-3)` (default is ~1e-6) | Tight tolerance may be impossible for complex loops |
| 2 | Add damping: `recycle.setFlowAccelerationFactor(0.5)` | Prevents oscillation by under-relaxing composition updates |
| 3 | Provide a good initial estimate on the recycle stream (set T, P, flow, composition close to expected) | Poor initial guess causes divergence in early iterations |
| 4 | Run `process.run()` twice — first run gets close, second run converges | Sequential substitution needs a warm start |
| 5 | Check degrees of freedom — make sure the number of adjusters matches the number of specs | Over/under-specified systems cannot converge |
| 6 | Simplify the loop: temporarily remove non-essential equipment and add back one at a time | Isolates which unit causes instability |

## Adjuster Non-Convergence

**Symptom:** Adjuster fails to find the target value.

| Step | Action | Why It Helps |
|------|--------|-------------|
| 1 | Widen the search bounds: `adjuster.setMaxAdjustedValue()` / `setMinAdjustedValue()` | Target may be outside default search range |
| 2 | Set a reasonable initial value: `adjuster.setStartValue(initialGuess)` | Far-off starting point slows convergence |
| 3 | Increase max iterations: `adjuster.setMaxIterations(100)` | Complex systems need more iterations |
| 4 | Check that the adjusted variable actually affects the target (verify the physics) | Adjusting the wrong variable cannot converge |

## Zero or NaN Property Values

**Symptom:** `getDensity()`, `getViscosity()`, or `getThermalConductivity()` returns 0.0 or NaN.

| Step | Action | Why It Helps |
|------|--------|-------------|
| 1 | Call `fluid.initProperties()` after flash | **Most common cause.** `init(3)` does NOT initialize transport properties. `initProperties()` calls both `init(2)` + `initPhysicalProperties()` |
| 2 | Check `stream.getPropertyInitLevel()` / `process.getPropertyInitLevel()` | **Second most common cause in a flowsheet.** `Stream.PropertyInitLevel.DENSITY_ONLY` deliberately skips viscosity, thermal conductivity and diffusivity — they read back as `0.0`, not as an error. Set the level back to `FULL` (per stream, per `ProcessSystem`, or per `ProcessModel` area) and re-run |
| 3 | Check `fluid.getNumberOfPhases()` — property may be for a phase that doesn't exist | Requesting gas-phase viscosity when only liquid exists returns 0 |
| 4 | Use `fluid.hasPhaseType("gas")` before accessing gas-phase properties | Phase existence varies with conditions |
| 5 | For viscosity at very low pressures (<1 bara), check if the correlation is valid | Some viscosity models have limited pressure range |
| 6 | For mixtures with unusual components (mercury, H2S at trace levels), check if physical property parameters exist in the database | Missing Lennard-Jones or critical parameters give zero |

## Wrong JT / Isenthalpic Expansion Temperature

**Symptom:** Manual `PHflash()` on a cloned fluid gives wrong temperature after pressure reduction (Joule-Thomson expansion). Tested: 14.9°C error vs 1.7°C with correct method.

| Step | Action | Why It Helps |
|------|--------|-------------|
| 1 | **Use `ThrottlingValve` in a `ProcessSystem`** instead of manual `PHflash()` | The valve handles enthalpy bookkeeping correctly; manual `PHflash(H/n)` uses inconsistent enthalpy reference |
| 2 | Build a mini ProcessSystem: `Stream → ThrottlingValve → run()` | Only 4 lines of code, always gives correct JT temperature |
| 3 | If you must use PHflash, call it as `ops.PHflash(fluid.getEnthalpy())` without dividing by moles | PHflash expects the total system enthalpy at the reference state, not per-mole |
| 4 | Cross-check: compare valve result with `ops.PHflash()` — if they differ by >2°C, the PHflash call is wrong | ThrottlingValve is the ground truth for isenthalpic expansion |

**Correct pattern (Python):**
```python
proc = ProcessSystem()
feed = Stream('SG', fluid.clone())
feed.setFlowRate(flow, 'kg/hr')
feed.setTemperature(T, 'C')
feed.setPressure(P_in, 'bara')
proc.add(feed)
valve = ThrottlingValve('JT', feed)
valve.setOutletPressure(P_out)
proc.add(valve)
proc.run()
T_jt = float(valve.getOutletStream().getTemperature('C'))  # Correct JT temperature
```

## Phase Identification Issues

**Symptom:** Phase labeled "gas" but behavior is liquid-like, or vice versa.

| Step | Action | Why It Helps |
|------|--------|-------------|
| 1 | Check if `fluid.setMultiPhaseCheck(true)` was called | Without this, solver may miss a phase split |
| 2 | **If the fluid uses `addTBPfraction` / `addPlusFraction`, verify molar mass was passed in kg/mol, not g/mol** | See "Silent g/mol TBP unit error" below — the single most common cause of a bogus one-phase result |
| 3 | For CO2-rich systems near critical, check actual density — phase label may be misleading | CO2 near Tc=304K and Pc=74bar has ambiguous phase identity |
| 4 | Use `String.valueOf(phase.getType())` (`"GAS"` / `"OIL"` / `"AQUEOUS"`) rather than `getPhaseTypeName()` | `getPhaseTypeName()` can report `"gas"` for a liquid root; `getType()` is the reliable discriminator |
| 5 | Use `fluid.getPhase(0)` / `getPhase(1)` instead of `getPhase("gas")` if labels are unreliable | Phase index is always consistent even if label is wrong |
| 6 | Run `ops.calcPTphaseEnvelope()` to visualize phase boundaries | Shows whether operating point is in 1-phase or 2-phase region |
| 7 | For CO2 injection wells, use `CO2FlowCorrections.isDensePhase(system)` to check T/Tc and P/Pc | Distinguishes dense phase from conventional gas/liquid |
| 8 | For CO2-rich streams, use `CO2FlowCorrections.getReducedTemperature(system)` and `getReducedPressure(system)` | Quantifies proximity to critical point |

### Silent g/mol TBP unit error

`addTBPfraction(name, moles, molarMass, density)` and `addPlusFraction(...)`
expect molar mass in **kg/mol**. Passing g/mol throws no exception — the
characterization silently produces nonsense pseudo-component properties and the
flash collapses to one phase.

**Diagnostic tell:** `TPflash()` at standard conditions (15 °C, 1.01325 bara)
returns `getNumberOfPhases() == 1` with `getType() == GAS` but a density of
700–800 kg/m3. A gas at 1 atm cannot exceed a few kg/m3, so a "gas" phase with
liquid density means the pseudo-components are broken, not that the fluid is
single-phase.

**Confirm** by printing the pseudo-component properties — the broken case shows
`molarMass` ~1000x too large, `Tc` in the thousands of K, and `acentricFactor`
pinned at -0.99:

```java
for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
  ComponentInterface c = fluid.getComponent(i);
  logger.info("{} MW={} g/mol Tc={} K Pc={} bara omega={}", c.getName(),
      c.getMolarMass() * 1000.0, c.getTC(), c.getPC(), c.getAcentricFactor());
}
```

**Fix:** divide by 1000 at the call site —
`fluid.addTBPfraction("C10-C12", 0.054, 150.0 / 1000.0, 0.790);`

## Pipe Outlet Temperature Equals Ambient

**Symptom:** A `PipeBeggsAndBrills` tubing string or flowline always arrives at the
ambient / formation temperature, no matter the length, rate or insulation. Hydrate
margins, cooldown times and arrival temperatures all look pessimistic and insensitive.

| Step | Action | Why It Helps |
|------|--------|-------------|
| 1 | Call `pipe.setUseOverallHeatTransferCoefficient(true)` and `pipe.setHeatTransferCoefficient(U)` | Without it the pipe behaves as if `U` were infinite and equilibrates to ambient |
| 2 | Use screening `U` values: ~15 W/m²K cased/cemented well, ~20 W/m²K uninsulated subsea flowline, ~5 W/m²K wet-insulated | Gives physical wellhead and arrival temperatures |
| 3 | Do **not** rely on `setAdiabatic(true)` | It currently leaves the outlet temperature unchanged |
| 4 | Sanity-check the wellhead temperature against expectation before using it downstream | A gas well lifting from 62 °C over 1040 m should arrive near 50 °C, not 5 °C |

## CO2 Injection Well Issues

**Symptom:** CO2 wellbore model gives unexpected phase splits or impurity enrichment.

| Step | Action | Why It Helps |
|------|--------|-------------|
| 1 | Check if formation temperature gradient is set on `PipeBeggsAndBrills`: `pipe.setFormationTemperatureGradient(topC, gradientK, "C")` | Without this, pipe uses constant ambient temperature — misses geothermal heating |
| 2 | Use `CO2FlowCorrections.isCO2DominatedFluid(system)` to verify fluid is >50 mol% CO2 | CO2 correction factors only apply to CO2-dominated systems |
| 3 | After shutdown, use `TransientWellbore.runShutdownSimulation()` to model cooling transient — don't assume instantaneous equilibration | Wellbore cools exponentially over hours to formation temperature |
| 4 | Attach `ImpurityMonitor` to streams to track light gas enrichment (H2, N2, Ar) in gas phase | Enrichment factors of 5-15x can occur during phase splits |
| 5 | Set `setMultiPhaseCheck(true)` on CO2 fluids with impurities | CO2+H2+N2 mixtures can form unexpected two-phase regions |
| 6 | For wellbore elevation, use negative values for downward flow: `pipe.setElevation(-1300.0)` | Sign convention: negative elevation = flow goes downward |

## Distillation Column Non-Convergence

**Symptom:** Column solver fails after max iterations or produces unreasonable results.

| Step | Action | Why It Helps |
|------|--------|-------------|
| 1 | Try different solver: `column.setSolverType(DistillationColumn.SolverType.INSIDE_OUT)` | Inside-out solver is more robust than standard for many columns |
| 2 | Reduce number of stages and increase back | Start simple, validate, then refine |
| 3 | Check feed stage location — feed too high or low destabilizes | Rule of thumb: feed at ~40-60% of total stages from top |
| 4 | Adjust reflux ratio — start with a high ratio (>2x minimum) and reduce | High reflux is easier to converge |
| 5 | Check condenser/reboiler configuration matches the separation | Total condenser for liquid products, partial for vapor |

### Column runs hundreds of iterations / dominates ProcessModel runtime

**Symptom:** one column takes tens of seconds per solve and the outer
`ProcessModel` loop never finishes, even though `setMaxNumberOfIterations(10)`
was called.

| Step | Action | Why It Helps |
|------|--------|-------------|
| 1 | `column.setMaxNumberOfIterations(n, true)` (or `setHardIterationCap(true)`) | The 1-arg setter is only a SOFT floor: the effective budget is `max(n, 5*trays)` plus overflow expansion. Check with `getEffectiveMaxNumberOfIterations()` |
| 2 | `column.setRelaxationFactor(0.3)` | The adaptive damping controller clamps at `minSequentialRelaxation` (default 0.5). `setRelaxationFactor` lowers that floor, so damping below 0.5 now actually takes effect and breaks tray-temperature limit cycles. `setMinSequentialRelaxation` / `setMinInsideOutRelaxation` set it explicitly |
| 3 | `column.setTemperatureToleranceRelative(1e-3)` | The default absolute tolerance (~0.02-0.03 K) can be 10x tighter than the enclosing `ProcessModel` gate (1e-3 relative ≈ 0.27 K), so the column chases a residual the plant already accepts |

### Column reports solved() == true but the answer is wrong

**Symptom:** `solved()` is `true`, the residuals printed by
`getConvergenceDiagnostics()` look clean, but the product split, reboiler duty or
tray profile disagrees with every other solver (vapour traffic collapsing
mid-column is a giveaway).

| Step | Action | Why It Helps |
|------|--------|-------------|
| 1 | Read `per-tray material imbalance` in `getConvergenceDiagnostics()` (or `getLastTrayMaterialBalanceError()`) | Anything above `getTrayMaterialBalanceTolerance()` (default 0.02) means at least one tray does not close its own component balance, so the profile is not a solution even when the overall feed/product balance is closed. Do **not** use the MESH `material:` infinity norm for this — it is dominated by trace components |
| 2 | Cross-check with a second solver | Run the same feed through `DAMPED_SUBSTITUTION` and a simultaneous solver. Agreement within a few percent is evidence; a 30-50 % difference in product split means one of them is wrong |
| 3 | Check `getLastSolveStatus()` | `FALLBACK_PRODUCTS` means the products came from an overall feed flash, not the tray solution. `FAILED` means the solver rejected its own result |
| 4 | Do not loosen `setTemperatureTolerance` to force a pass | A loosened tolerance can be satisfied by the warm start in a single iteration and returns an unconverged profile that *looks* converged |

> `NAPHTALI_SANDHOLM` reports `Double.NaN` as its temperature residual because it
> has no successive-substitution sweep. That is intentional: for that solver the
> MESH residual vector is the convergence measure, and `solved()` requires the
> MESH gate to be active instead.

## ProcessModel Boundary Convergence

**Symptom:** `getConvergenceSummary()` reports a relative error but not where it
comes from — most confusingly `Flow rate: 1.00e+00`.

| Step | Action | Why It Helps |
|------|--------|-------------|
| 1 | `model.getWorstBoundaryStreamName("flow")` (also `"temperature"`, `"pressure"`) | Names the boundary stream that produced the reported maximum. The summary and `getConvergenceReportJson()` (`errors.flow.worstStream`) now include it |
| 2 | `model.getNonConvergedBoundaryStreamErrors()` | Every boundary stream outside tolerance, worst first, with `previousFlow` / `currentFlow` |
| 3 | Check `isFlowCollapsedToZero()` on the offender | A relative flow error of **exactly 1.0** means the stream went from non-zero to zero between outer passes — an upstream unit stopped producing it (failed run, closed splitter, bypassed train). That is an upstream fault, not a slowly converging recycle |
| 4 | Check `isFlowStartedFromZero()` | Mirror case: a seeded/low-flow stream starting up. Usually harmless, converges on the next pass |
| 5 | Check `getAbsoluteFlowChange()` on the offender | The decisive test. A large *relative* error on a tiny *absolute* change is numerical noise on a stagnant leg, not a process residual |

### Symptom: a stagnant dead leg dominates the convergence gate

`getConvergenceSummary()` reports e.g. `Flow rate: 6.56e-02 (gas export ht)` and
the model never converges, while the real residual sits on a large stream. The
gate is a **max over relative errors**, so `0.007 kg/hr` of wobble on a
`0.1 kg/hr` branch beats a genuine `443 kg/hr` residual on a `138 t/hr` export
stream (`3.2e-03`).

```java
// 1. Do not solve the stagnant section at all (units auto-bypass).
//    Manifold, ThrottlingValve, PipeBeggsAndBrills and MultiStreamHeatExchanger
//    honour this too, and still publish their outlet pressure at zero flow.
plant.get("HT injection process A").setSectionLowFlowThreshold(50.0, "kg/hr");

// 2. Keep the dead leg out of the plant convergence metric.
plant.setBoundaryFlowFloor(1.0);                      // drop sub-1 kg/hr streams

// 3. Converge on relative OR absolute flow change.
boolean ok = plant.runUntilConverged(15, 1e-3, 1.0);  // rel 1e-3 OR abs 1 kg/hr
```

Both filters also apply to `getNonConvergedBoundaryStreamErrors()`, so the
offender list stops being dominated by noise. `getConvergenceSummary()` prints
the absolute Δflow next to each relative error and a `Flow filters:` line when a
filter is active.

> **Prefer the self-configuring form.** `plant.runUntilConverged(maxIterations)`
> derives all three filters from the plant's own feed rate, so the hand-picked
> `1.0` / `1.0` / `50 kg/hr` numbers above are only needed when you must override
> it. See "Model grinds toward the last decade of the residual" below.

### Model grinds toward the last decade of the residual

**Symptom:** `converged=False` after the iteration cap, but the residual is
*small* — e.g. `Flow rate: 3.19e-03` against a `1e-3` gate, which is 434 kg/hr on
a 136 t/hr stream. That is **slow convergence, not a limit cycle**, and it is
usually the gate being tighter than the model is worth.

| Step | Action | Why It Helps |
|------|--------|-------------|
| 1 | Re-run with a larger `maxIterations` before touching damping | Confirms slow-but-real convergence. A limit cycle plateaus; slow convergence keeps creeping down |
| 2 | Do **not** call `setTolerance()` at all | With no explicit tolerance the model uses `DEFAULT_ENGINEERING_TOLERANCE` (1e-3 relative on flow, T and P) instead of the historical 1e-4. 1e-4 is far tighter than plant instrument or EOS uncertainty |
| 3 | Read `model.getAutoToleranceSummary()` | States the accuracy actually used. If the residual stalls (<10 % improvement over 5 outer passes) while below `getAutoToleranceCeiling()` (1e-2), the model accepts it and says so instead of grinding |
| 4 | `model.setAutoToleranceCeiling(5e-3)` | Tightens the loosest accuracy the model may settle for |
| 5 | `model.setAutoTolerance(false)` | Opts out entirely and restores the historical 1e-4 default |

```java
// Self-configuring: no tolerance, no noise filters, no per-plant numbers.
boolean ok = plant.runUntilConverged(60);
System.out.println(plant.getAutoTuningSummary());     // flow-noise filters chosen
System.out.println(plant.getAutoToleranceSummary());  // accuracy chosen/accepted
```

> **Gotcha:** any explicit `setTolerance()` / `setFlowTolerance()` /
> `runUntilConverged(n, tol)` marks the tolerance as user-owned and disables
> **both** the engineering default and the stall acceptance. If you set `1e-3`
> "to be helpful" you also switch off the feature that would have accepted a
> stalled `1.4e-3`.

> **Gotcha:** `setSectionLowFlowThreshold()` deactivates units for the remainder
> of the solve pass. Do not set it on a section that is legitimately dry only on
> the first recycle iteration (e.g. a JT valve on a separator liquid outlet) —
> it will never recover within that pass.

### "Converged: YES" but a unit is out of mass balance

The plant gate says every boundary stream is inside tolerance, yet
`checkMassBalance()` flags a downstream unit by ~1 %. The two disagree because
the link between the areas is **invisible to boundary detection**, not because
the tolerance is too loose.

`ProcessModel` discovers boundary streams through `getInletStreams()` /
`getOutletStreams()`. Equipment that stores its streams in its own private lists
and does not override those two methods falls back to the inherited two-port
`inStream`/`outStream` — often never assigned — and so reports **no**
connections. Such a link is dropped from both the convergence gate and the
incremental dirty-area propagation, so the consumer never gets re-run.

Diagnosis — do not start by loosening tolerances:

1. List the units that fail mass balance and trace what feeds them.
2. If **only** the units fed by one equipment type are unbalanced while every
   other unit is exactly `0.0000 %`, suspect missing stream introspection on
   that type.
3. Confirm with `unit.getInletStreams().size()` / `getOutletStreams().size()` —
   a multi-port unit reporting 0 or 1 is the bug.

```java
// Any multi-port equipment MUST expose all ports, or it is invisible to
// topology walks, DEXPI export, ProcessConnection and boundary detection.
List<StreamInterface> in = heatEx.getInletStreams();   // expect all feeds
List<StreamInterface> out = heatEx.getOutletStreams(); // expect all products
```

> Fixed for `MultiStreamHeatExchanger` in PR #2712; the two-stream
> `HeatExchanger` always overrode both. Apply the same override when adding new
> multi-port equipment.

## Process Equipment Errors

### Compressor: Negative or Unreasonable Power
| Step | Action |
|------|--------|
| 1 | Verify outlet pressure > inlet pressure |
| 2 | Check that inlet stream has a gas phase |
| 3 | Set polytropic/isentropic efficiency: `comp.setIsentropicEfficiency(0.75)` |

### Separator: No Phase Split
| Step | Action |
|------|--------|
| 1 | Verify fluid has components that can form two phases at the conditions |
| 2 | Run TPflash on the inlet fluid standalone to verify 2+ phases exist |
| 3 | For three-phase, use `ThreePhaseSeparator` and enable `setMultiPhaseCheck(true)` on the fluid |

### Heat Exchanger: Zero Duty
| Step | Action |
|------|--------|
| 1 | Verify outlet temperature/specification is set |
| 2 | Check that inlet and outlet temperatures are different |
| 3 | For `HeatExchanger` (two-stream), verify both streams are connected |

## Serialization / Copy Errors

**Symptom:** `equipment.copy()` or `ProcessSystem.copy()` throws `NotSerializableException`.

| Step | Action |
|------|--------|
| 1 | Check for non-serializable fields in custom equipment — mark them `transient` |
| 2 | Ensure all fields implement `Serializable` or are primitive types |
| 3 | For lambda expressions in fields, replace with anonymous inner classes |

## Performance Issues

**Symptom:** Simulation runs very slowly.

| Step | Action |
|------|--------|
| 1 | Reduce number of components — merge similar C6+ fractions using lumping |
| 2 | For Monte Carlo, cache expensive results (see `neqsim-notebook-patterns` skill) |
| 3 | Use `SystemSrkEos` instead of `SystemSrkCPAstatoil` if water interaction isn't critical |
| 4 | Reduce iteration limits for screening-level work |
| 5 | Profile with `System.currentTimeMillis()` around expensive operations |

## Common Exception Messages

| Exception | Likely Cause | Fix |
|-----------|-------------|-----|
| `ArrayIndexOutOfBoundsException` in phase | Component name with `+` character (e.g., "C20+") | Use "C20" without the `+` |
| `NullPointerException` in `getPhase("gas")` | No gas phase exists at conditions | Check `hasPhaseType("gas")` first |
| `ClassCastException` in equipment | Wrong stream type connection | Verify equipment constructors take `StreamInterface` |
| `java.sql.SQLException` | Component not in database | Check spelling, verify against COMP.csv |
| `StackOverflowError` in recycle | Infinite loop in process topology | Check for circular references without a Recycle unit |

## Phase Envelope Branch Labels Swapped

Load `neqsim-phase-envelope` for the canonical structured segment API, physical dew/bubble
classification, numerical-zero component behavior, and solver-change test protocol. Use this
section for symptom-driven recovery after following that workflow.

**Symptom**: When plotting a phase envelope calculated with `calcPTphaseEnvelope(true, 1.0)`,
the bubble and dew curves appear swapped — the "bubble" array has higher temperatures
(cricondentherm) which is physically the dew side, and vice versa.

**Cause**: The Michelsen continuation algorithm always starts with `isDewPhase=true`
regardless of the `bubblePointFirst` flag. When tracing from the bubble side first,
initial points go into the dew list. At the critical point the flag flips, so actual
dew-side data ends up in the bubble list.

**Fix**: Classify branches by physical reasoning, NOT by method names:
```python
# The DEW curve always contains the cricondentherm (maximum temperature)
if np.array(envelope.getBubblePointTemperatures()).max() > np.array(envelope.getDewPointTemperatures()).max():
    dew_T = np.array(envelope.getBubblePointTemperatures())  # swapped!
    bub_T = np.array(envelope.getDewPointTemperatures())     # swapped!
else:
    dew_T = np.array(envelope.getDewPointTemperatures())
    bub_T = np.array(envelope.getBubblePointTemperatures())
```

## When All Else Fails

1. **Simplify radically** — reduce to 2-3 components, remove equipment, test one unit at a time
2. **Check a known-good case** — run an existing test (e.g., `SeparatorTest`) to verify NeqSim works
3. **Compare with standalone flash** — take the inlet fluid, run TPflash manually, check phases
4. **Search existing tests** — `grep_search` for similar equipment/components in `src/test/java/neqsim/`
5. **Report the issue** — if it's a genuine NeqSim bug, file a GitHub issue with minimal reproducer
