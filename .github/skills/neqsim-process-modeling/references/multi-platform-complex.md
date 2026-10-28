# Multi-platform complex model pattern (one ProcessModel per platform package)

Use this reference when one study needs several platforms (or several plants) that
exchange streams, for example a field centre with a hub platform, satellite
platforms and a shared gas-export and compression chain. It generalises a
five-platform well-to-export build (ProcessPilot style, Python on top of NeqSim). The
pattern is generic; nothing here depends on a particular field. For a single
platform use `neqsim-platform-modeling`; for the study workflow around a finished
model use `neqsim-future-operation-study`.

## 1. Package layout

| Package | Content | Rule |
|---|---|---|
| `common` | fluid factory, `BoundaryStream` / `BoundaryRegistry`, unit helpers, utilisation register, compressor map and limits loader, plant-data readers | build and freeze first; every platform imports from it, none duplicates it |
| one package per platform | one `ProcessModel` holding one `ProcessSystem` per process area (`plant.add("area", ps)`); typed input workbook; typed result extraction | never put a `ProcessModel` inside a `ProcessSystem`; cross-area streams by object reference; loops closed with `Recycle` |
| `integrator` | owns the platform simulators, the boundary registry, tear streams, year/case driver, plant balance, bottleneck list | built last; supports `coupled` and `decoupled` modes |

**Common fluid basis.** Build every fluid only through the common factory, with one
component list (water last), one EOS and one binary-interaction offset for all
platforms. Keep a mapping between the design-study component list (for example 17
components) and the model list (for example 26 + water): lump on the way in,
distribute on the way out with a stored split table, and record the mapping error.
Embed tuned parameters in the package; never read them from the task folder at run
time. Re-tune any platform set that was tuned with a different offset and record it.

## 2. Boundary-stream contract

`BoundaryStream` carries: id, from/to platform, medium, P (bara), T (degC), mass flow
(kg/h), mole fractions on the common basis (sum 1), `source` (`standalone`, `coupled`,
`table`) and a note. Floor flows at a small positive value (for example 1e-3 kg/h) so
no stream carries exactly zero. Every platform simulator implements:

```text
export_boundaries() -> {id: BoundaryStream}      after a run: outgoing streams
import_boundaries(streams)                         update the imports area; no rebuild
default_imports()                                  stand-alone tables, so each package runs alone
kpis()                                             flat dict of key numbers (validation, results.json)
set_year(y, case) / set_throughput_scale(f)
get_utilization()                                  tag, service, quantity, value, limit, utilisation, basis
get_mass_balance()                                 (kg/h, percent)
```

Boundary ids are defined once in the basis file; builders may add ids and must list
them in the package README. A contract checker script (every package implements every
method, ids match) belongs in CI.

## 3. Integrator

- Close loops that cross platforms with **tear streams** on the boundary registry and a
  Wegstein or relaxation fixed point; report pass count and residual per boundary.
- Loop gain across platforms can exceed 1 (flash gas recycled between two simulators).
  Compare the one-way result with the converged loop before blaming composition.
- Keep a plant balance (feed minus export, injection, fuel, flare, water) with a
  tolerance, and report it per run.
- Keep two modes: **decoupled** (each platform from `default_imports()`, the basis of
  its calibration) and **coupled** (integrator). Report both scorecards; the list of
  items that pass decoupled and fail coupled is the integration finding.
- Process-level convergence: use bounded `runUntilConverged(n)`, check
  `getRunStatus()` and open recycles, and treat a stalled run as a result
  (see section 6), not as a number.

## 4. Validation design

- **Two windows.** Calibrate on window A. Score window B untouched (hold-out) with no
  retuning on B. A third item class is allowed only if it was never fitted.
- **Calibrated flag.** Every scored row carries `calibrated` yes/no; a fitted item
  never counts as independent validation. Report "passed / scored" for all rows and for
  the independent subset separately.
- **Forbidden tags.** Classify each candidate tag as `validation_target`,
  `calibration_input`, or `forbidden`. Forbidden: flow transmitters contaminated by
  recycle (anti-surge lines, spill-back), counters and accumulators, flat-line, no-data,
  stale or bad-quality tags, and tags with a suspected unit or label error. Resolve a
  suspected unit error against a physical balance before using the tag in either role.
- **Stream-day vs calendar-day.** Window results are calendar-day means; design-basis
  and yearly feed files are stream-day. Carry the production efficiency per year
  (turnaround years differ), state `rate_basis` on every table and provide both
  bases from `kpis()`.
- **Setpoint philosophy.** In window mode, setpoints are measured; efficiency and
  heat transfer are calibrated to measured outlet temperatures; unmeasured items go to
  an `assumptions` sheet, never silently into code.
- Pair each scorecard with an open-issues list ranked by effect on the plant answer.

## 5. Year sweep and sweep to Monte Carlo

1. Run each (year, case, part) as its own job in a spawned worker with a wall-clock
   **watchdog**; a stalled or dead worker is terminated and the row gets
   `status = unsolved: timeout | error | worker died` plus the reason. Persist finished
   jobs and make the sweep resumable. Where the NeqSim version provides an opt-in run
   budget on `ProcessSystem` (with a stalled run status), use it for the same purpose
   inside the worker; otherwise the process-level watchdog is the portable mechanism.
2. A relaxed re-solve (for example dropping an unreachable minimum) is a separate,
   labelled status (`solved, X RELAXED`), never a silent success.
3. Keep every variant sweep (solver option, fidelity) under a suffix; never overwrite
   the default table. Compare variants row by row and state which statements move.
4. **Pre-solved sweep to Monte Carlo.** Do not call the flowsheet inside the Monte
   Carlo loop. Solve the model at one-at-a-time low/base/high inputs (the tornado
   runs), fit an additive (or quadratic) surrogate on those solves, then sample the
   surrogate (for example 20 000 draws, inputs normal with the 80 % interval equal to
   low-high). Report P10/P50/P90 and the contribution ranking, and state what the
   surrogate does not cover (interactions, design-basis changes).

## 6. Design-basis compliance register

Turn the design basis into a register: requirement, source clause, status
(`compliant` / `deviates` / `not modelled`), model hook (public parameter), effect and
justification. Support a **strict** configuration that reproduces the design basis
exactly (pressures, efficiencies, rates) and run it before the best-estimate model;
document internal contradictions in the basis and the choice made. Rank deviations by
impact on the plant answer, not by count.

## 7. Benchmarking against a consultant or vendor report

Digitise the charts (calibrate axes on gridlines, store points with a unit and an
uncertainty), map operating-mode definitions (rich/lean gas, stable/unstable oil, which
volumes are counted as oil, water and gas, stream-day or calendar-day) before comparing
numbers, compare at the same basis, and tabulate what the report models that you do not and the
reverse. Ask each difference one question: basis, data or physics.

## 8. Resumable agents

Long builds are cancelled by every new user message. Each study or builder agent writes
a `STATUS.md` (done, todo, last command, key numbers) about every 10 minutes, commits
early, writes scratch under the task folder, and resumes from the file. A status file
with explicit "todo next" lines is the hand-off.
