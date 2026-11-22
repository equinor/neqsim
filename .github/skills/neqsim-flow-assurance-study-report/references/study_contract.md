# Flow-assurance study contract

Schema `flow_assurance_study.v1` (`dataset/flow_assurance_study.json`). One dataset feeds the report,
the drawings, the facts and the validation. Volatile keys (validation, drawings, artifacts, timings,
cache flags, creation time) are excluded from `dataset_hash`.

| Key | Content |
|-----|---------|
| `meta`, `document`, `study` | tool versions, NeqSim version, document id/revision, objective, decisions, maturity |
| `conventions` | units (bara, degC, K, m, kg/s), standard conditions, chainage direction and measure |
| `sources` | every source with an input class (`synthetic`, `schematic`, `measured`, ...) |
| `fluids`, `solver` | composition basis, EOS per use (SRK hydraulics, CPA hydrate), increments, tolerances |
| `route` | nodes, segments (derived length, dz, U inner, steel mass), paths, junction, sink, transitions, status |
| `topology_variants`, `cases` | resolved active segments and sources per variant; case definitions |
| `results[case]` | status, segments, streams, profiles per path (chainage, elevation, p, T, holdup, velocities, hydrate T and margin), extrema, cooldown, findings |
| `criteria` | per criterion: quantity, operator, value, unit, source, status per case, governing case/location |
| `events`, `threats`, `mitigations` | event status (calculated/open/external_open/external_supplied); threat status; mitigation vs baseline deltas |
| `benchmarks`, `mesh_sensitivity`, `qualification` | analytic and independent checks, mesh gate, method evidence matrix |
| `assumptions`, `exclusions`, `data_gaps`, `findings` | always filled; nothing hidden |
| `facts` | numbers quoted in the executive summary, regenerated from the dataset |

## Token kinds

`[[res:CASE:path|fmt|unit=u]]`, `[[ext:CASE:extremum:field|fmt]]`, `[[crit:ID:field]]` or
`[[crit:ID:CASE:field]]`, `[[seg:SEG:path|fmt|unit=u]]`, `[[segres:CASE:SEG:key|fmt]]`,
`[[bm:ID:field]]`, `[[fact:name]]`, `[[meta:path]]`, `[[basis:path]]`. A unit request converts from the
unit implied by the key suffix (`_bara`, `_c`, `_m`, `_kg_s`, ...); incompatible units are an error
(`FA-DOC-REFS`), a missing value prints `unavailable`, never zero. IDs match `[A-Za-z0-9_-]+`.

## Criteria semantics

`PASS`/`FAIL` against the stated limit at the governing location over the whole route; `n/a` for cases
where the criterion does not apply (never shown as a pass). The governing case and chainage are reported
with the margin.

## Readiness

`NOT_READY`: any validation FAIL. `SCREENING`: otherwise. `REVIEWED`: the basis requests it AND a named
reviewer plus record are supplied AND no synthetic input, open threat, data gap or warning remains.
The banner and every drawing title block carry the label; a label change triggers a second build.

## External transient hand-off

Each open transient event writes `handoff/EVT-*.json` with the topology, boundary conditions, required
outputs and comparison criteria. `--external-results DIR` attaches `<event>.json` results; the event is
then `external_supplied` and keeps the supplier's qualification note, never a qualified status.
`fa_handoff.two_fluid_profile(response, segments)` maps a `TwoFluidPipeResponse` profile onto route
segments by chainage and keeps unavailable quantities `None`.
