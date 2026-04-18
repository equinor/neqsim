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
| 2 | Check `fluid.getNumberOfPhases()` — property may be for a phase that doesn't exist | Requesting gas-phase viscosity when only liquid exists returns 0 |
| 3 | Use `fluid.hasPhaseType("gas")` before accessing gas-phase properties | Phase existence varies with conditions |
| 4 | For viscosity at very low pressures (<1 bara), check if the correlation is valid | Some viscosity models have limited pressure range |
| 5 | For mixtures with unusual components (mercury, H2S at trace levels), check if physical property parameters exist in the database | Missing Lennard-Jones or critical parameters give zero |

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
| 2 | For CO2-rich systems near critical, check actual density — phase label may be misleading | CO2 near Tc=304K and Pc=74bar has ambiguous phase identity |
| 3 | Use `fluid.getPhase(0)` / `getPhase(1)` instead of `getPhase("gas")` if labels are unreliable | Phase index is always consistent even if label is wrong |
| 4 | Run `ops.calcPTphaseEnvelope()` to visualize phase boundaries | Shows whether operating point is in 1-phase or 2-phase region |
| 5 | For CO2 injection wells, use `CO2FlowCorrections.isDensePhase(system)` to check T/Tc and P/Pc | Distinguishes dense phase from conventional gas/liquid |
| 6 | For CO2-rich streams, use `CO2FlowCorrections.getReducedTemperature(system)` and `getReducedPressure(system)` | Quantifies proximity to critical point |

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

## When All Else Fails

1. **Simplify radically** — reduce to 2-3 components, remove equipment, test one unit at a time
2. **Check a known-good case** — run an existing test (e.g., `SeparatorTest`) to verify NeqSim works
3. **Compare with standalone flash** — take the inlet fluid, run TPflash manually, check phases
4. **Search existing tests** — `grep_search` for similar equipment/components in `src/test/java/neqsim/`
5. **Report the issue** — if it's a genuine NeqSim bug, file a GitHub issue with minimal reproducer
