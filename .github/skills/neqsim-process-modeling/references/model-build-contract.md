# Reusable process-model build contract

Use this checklist before construction, at base-case acceptance and at specialist
handoff. It is an artifact convention, not a new NeqSim API or certification level.
Follow the configured task workflow for locations; keep company data out of examples.

## 1. Model basis

Record in the task's existing scope/model-basis artifact:

| Field | Required evidence |
|---|---|
| Question and fidelity | Requested decisions/outputs; operating-point, capacity, optimization or transient study |
| Battery limits | External feeds, terminal products/drains/vents, utilities and excluded systems |
| Feed data | Composition basis (mole/mass; dry/wet), assay/heavy ends, water/salts/inhibitor basis, source and timestamp |
| Units | Absolute/gauge pressure; K/°C; mass/molar/actual/standard flow; reference conditions for standard volume |
| Thermodynamics | EOS, mixing rule, characterization, phase options, validity/benchmark for the target properties |
| Equipment modes | Fixed outlet pressure or map/speed; specified duty or UA; specified valve pressure or Cv/opening |
| Specifications | Product limits with units, test/reference basis and source; separate targets from design limits |
| Evidence | Measured, vendor, inherited, assumed or missing for every decision-critical input |
| Acceptance | Quantities, independent comparison, balance tolerances and criteria fixed before solving |

Normalize composition only when its basis and rounding justify it, and record the
original sum. Do not convert oil/water cut or GOR into component feeds without its
reference basis. Gauge pressure requires the local atmospheric reference. Unknown
standard-volume conditions are a gap, not an implicit conversion.

Choose the least complexity that answers the question. Fixed-efficiency compression
may estimate power; map-based capacity needs actual maps and driver/speed limits.
A cooler with a prescribed outlet temperature does not establish UA or utility
capacity. Fixed downstream pressure on a valve does not establish valve opening/Cv.
List which conclusions remain unsupported by the chosen representation.

## 2. Build and topology

- Separate input data, model construction, solution, validation and result extraction.
  Build a fresh model for each independent scenario, or prove a complete reset.
- Return the `ProcessSystem`/`ProcessModel`, named feeds, terminal products and
  equipment registries. Keep the builder callable outside a notebook cell.
- Use one process per area; combine areas in a `ProcessModel`. Document cross-area
  producer/consumer links and solve ordering.
- Maintain a connection table: stream ID, producer, consumer or external terminal,
  service/phase and area. A terminal may be inactive at zero flow; keep its identity.
- Branch through real splitter/manifold outlets. Clone independent mutable feed
  fluids; preserve live equipment outlet objects when connecting downstream units.
- Every oil, gas, aqueous, scrubber-liquid, purge, vent and drain outlet must have
  a destination. Account for it even if outside the requested product recovery.
- Return scrubber liquids to a physically appropriate pressure section. Include
  required letdown or pumping; never raise pressure with a temperature/pressure
  setter and interpret that as a real transfer without work/utility accounting.
- Check equipment names, duplicate stream counting, split fractions and pressure
  compatibility at junctions. Count external boundary flows once; internal recycle
  flows are excluded from the plant balance.

## 3. Solve progressively

1. Verify feeds and expected phase behavior; initialize required properties.
2. Run the once-through train and inspect each unit's pressure, phases and duties.
3. Close physical feedback via supported automatic tears (`makeRecycles()` or
   `setAutoRecycles(true)`), or documented explicit tears where unsupported.
4. Add adjusters/controls with one owner per manipulated variable. Avoid optimizing
   a variable already enforced by an adjuster or imposing incompatible outlet specs.
5. Use bounded `runUntilConverged(n)` for coupled flowsheets and check its boolean.
   Keep iteration/residual evidence; do not loosen tolerances to manufacture success.
6. Re-run accepted inputs to detect state drift. Verify a nearby operating point
   relevant to the requested decision; record numerical failures separately from
   feasible states. Restore and verify the base case afterward.

For MCP, inspect schema and response convergence/status fields. Missing evidence
remains incomplete; do not assume a successful transport response means a solved model.
Use `neqsim-troubleshooting` for recovery rather than silently dropping recycles,
phases or constraints. Record initialization and solver settings affecting results.

## 4. Physical acceptance

- **Total mass:** compare external feeds against every external product/drain/vent.
  For nonzero feed, require relative closure below 0.001 (0.1 percent), or a stricter
  stated task tolerance. Report absolute kg/s residual as well. Zero-feed cases need
  an explicit absolute tolerance; never divide by zero or label them validated by default.
- **Components:** compare component mass/molar flows on the same basis. Use both
  absolute and relative tolerances declared per quantity; a trace component needs
  a meaningful absolute bound. For reacting systems use elemental balance and
  account for reaction stoichiometry instead of requiring unchanged species flows.
- **Energy:** define signed inlet/outlet enthalpy, heat and shaft work consistently;
  compare their residual on a declared scale with stated absolute/relative tolerances.
  Include utilities/work crossing the boundary. Use the same enthalpy reference/EOS;
  do not compare enthalpies from unrelated fluid definitions. Missing evidence is
  incomplete, not zero energy error.
- **Phases/properties:** inspect gas/liquid/aqueous products, flash options and
  finite T/P/composition/density. Initialize transport properties before hydraulics
  or heat transfer. Verify whether near-zero outlets are physical or numerical.
- **Equipment:** inspect pressure changes, phase at compressor/pump suction,
  duty/work signs, utility temperature approach and relevant operating limits.
  Do not treat a converged but infeasible unit as an accepted case.
- **Independent evidence:** compare a relevant benchmark/analytical check or
  measured data, with its provenance and applicability. Conservation checks alone
  do not validate equilibrium, equipment correlations or phase-envelope accuracy.

## 5. Scenarios, optimization and handoff

Each case has an ID, changed inputs, status, units, constraint results and validation
record. Failed cases keep their errors and must not reuse the preceding case's
outputs. Apply product specs and equipment limits at every candidate and replay
the selected solution from a fresh model. Distinguish calculated capability from
missing constraints; unknown limits cannot produce a feasible plant verdict.

Hand off the following inside the existing task folder:

- Builder/notebook/script, exact NeqSim version/commit and execution instructions.
- Input basis and sources, fluid characterization and evidence gaps.
- Process/plant object or instructions to recreate it, named object registries and
  connection table, boundary streams, recycle/adjuster/control ownership.
- Accepted base-case stream table (flow, T, P, phases and composition) and equipment
  table (duty/power, modes and available capacity evidence), all with units.
- Total/component/energy validation, convergence, repeat/nearby-point checks,
  independent comparison, scenarios and failed cases.
- Remaining specialist questions: geometry/mechanical design, maps/controls,
  flow assurance, utility demand, emissions, relief/blowdown or economics.

Populate the existing `results.json` schema through `neqsim-task-workflow`; do not
invent mandatory fields in that schema. Keep the richer basis/connection/scenario
records in task artifacts referenced from the report. `process-model` builds this
handoff, `solve-process` packages the executed notebook, and `solve-task` coordinates
specialists and the final study without recreating disconnected models.
