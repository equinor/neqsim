---
title: "Reservoir history source intake"
description: "Screen dated cumulative production, injection and field-average pressure evidence before assembling a compositional tank history."
keywords: "material balance, stock tank, pressure datum, field history, cumulative production, injection, held-out pressure, source audit"
---

## Source evidence for a tank history

Use `reservoir_history_intake.audit_reservoir_history` on cumulative
source records before constructing a
[compositional tank history](reservoir_material_balance.md#compositional-measured-history-replay).
The audit retains every input row and its canonical SHA-256. Review the
pressure eligibility and flow windows independently.

- Use protocol `CUMULATIVE_RESERVOIR_HISTORY` and supply reservoir,
  source, data-use, uncertainty and reporting-cutoff references.
- Declare each physically cumulative stream by name, direction
  (`production` or `injection`), fixed unit and volume or amount basis.
  Preserve standard conditions and stock-tank identity with the basis.
  Supply nonnegative interval endpoints as decimal strings in the declared
  unit. Explicit zero differs from an omitted value.
- Preserve source order. Row IDs are unique and each row has a source
  reference. Dates must be ISO calendar dates on one daily reporting
  convention. The audit reports elapsed calendar days between adjacent
  accepted dates. An invalid, ambiguous or out-of-order date blocks
  the affected windows. Use a separately qualified time-alignment
  workflow for subdaily histories, local-time changes or monthly totals
  without a verified cutoff.
- A pressure row requires source reference, kind, unit and bounds.
  Eligibility requires `field_average`, absolute bar (`bara`), the same
  explicit datum reference as the study, positive documented sigma
  bounds in `bar`, and a `calibration` or `holdout` role. Preserve
  local shut-in, wellhead, flowing bottomhole, simulated and unclassified
  pressures with their original kind. Supply a separately traced conversion
  before using gauge or psi readings in a tank fit.

The increment interval is $[C_{1,L}-C_{0,U}, C_{1,U}-C_{0,L}]$, with
outward rounding. A strictly negative upper bound is incompatible
with unreconciled cumulative production or injection. A range spanning
negative and nonnegative values is unresolved. An exact zero increment
is eligible. Both dated endpoints and all declared stream increments
must pass the flow screen. No gap is bridged and no increment is clipped.

Use measurement uncertainty and known correlations when available. The
audit propagates independent endpoint bounds, so overlapping cumulative
intervals can yield unresolved increments even when a separate measurement
confirms no flow. Record counter resets, historical corrections and stream
reconciliations separately, with a traceable relationship to the raw values.
The screen supplies neither component moles nor phase withdrawals. A
complete history still requires composition, separator and metering
evidence, plus an explicit choice of fit and holdout cases.

## Run the synthetic example

The [JSON fixture](../../devtools/pvt_examples/reservoir_history_intake.json)
covers independent pressure roles, two named directions, a leap day and
an exact zero injection interval. Run from the repository root using the
installed devtools environment.

```python
import json
from pathlib import Path

from reservoir_history_intake import audit_reservoir_history

path = Path("devtools/pvt_examples/reservoir_history_intake.json")
report = audit_reservoir_history(json.loads(path.read_text(encoding="utf-8")))
print([window["eligible_for_flow_handoff"] for window in report["windows"]])
```

## Published field source screen

Havlena and Odeh's [1964 field study](https://doi.org/10.2118/869-PA),
Table 1, gives dated pressure, cumulative oil, water production and water
injection, cumulative GOR and $B_t$/$B_g$ for the Guico D4 sand. Preserve
reported pressure in psig, oil and water totals in million barrels on
the published basis, and GOR in standard gas volume per oil volume on
the published basis. The table does not supply a named-well component
history, a pressure uncertainty series or a laboratory EOS. The cumulative
water-production decrease across the onset-of-injection period encloses
−0.030 to −0.028 million barrels under half-last-digit bounds. A February
date is invalid as printed. Keep these source
conflicts visible. Field-level cumulative GOR and $B_t$/$B_g$ are useful for
the published classical balance. They do not supply an independently
observed compositional production or separator holdout. The repository
fixture uses original synthetic input.

Tables 3 and 4 of the same paper are a separate undersaturated oil case
with volumetrically averaged, datum-corrected pressure for the Virginia
Hills Main and Hope Creek zones. Their respective datum depths are 5,617
and 5,587 ft subsea. Keep them separate. Intermediate pressures were
obtained from pressure-versus-production plots. The tables can inform a
conventional material-balance benchmark. They lack documented pressure
sigma and held-out produced composition for the compositional match.

## Named component amounts from total moles

Use the optional `audit_component_moles` audit after admitting the dated
cumulative history. Each end row supplies `interval_compositions` keyed by
stream name. Each composition requires a source reference, the same
`basis_reference` as its total-mole stream, and a complete `components`
mapping with nonnegative `mol/mol` bounds. Set `averaging` to
`mole_weighted` for a cumulative mole-weighted composition or `constant`
when composition is constant throughout the interval. A time-average
composition requires a separately established mole-weighted conversion.

The audit checks whether the composition bounds intersect the unit simplex.
For each component it tightens the marginal range using all other component
bounds and the requirement that their sum is one. It multiplies these
ranges by the admitted total-mole increment with outward decimal rounding.
The output preserves each named stream and its production or injection
direction. Missing composition, unavailable total moles, invalid dates
and ambiguous withdrawal increments block the affected component handoff.
An incompatible composition sum is reported without changing source values.
These are marginal bounds with shared total and composition constraints.
Selecting every upper bound simultaneously generally violates conservation.

The [synthetic component fixture](../../devtools/pvt_examples/reservoir_component_intake.json)
contains a 10–11 mol production interval and 2 mol water injection.
The methane input range 0.8–1.0 contracts to 0.8–0.9 because the ethane
fraction is 0.1–0.2. Component mole ranges are therefore methane 8.0–9.9,
ethane 1.0–2.2 and water 2. This fixture establishes independent arithmetic
consistency. Laboratory sample/experiment provenance, separator-to-total-mole
conversion and measured field accuracy require their own evidence.

```python
import json
from pathlib import Path

from reservoir_history_intake import audit_component_moles

path = Path("devtools/pvt_examples/reservoir_component_intake.json")
report = audit_component_moles(json.loads(path.read_text(encoding="utf-8")))
stream = report["windows"][0]["component_streams"]["well-A gas"]
print(list(stream["component_moles"]))
```

Use feasible component amounts when constructing the existing
`CompositionalReservoirHistory.Step` named production and injection ledgers.
This audit returns bounds for that selection. It does not choose nominal
amounts or create tank trials from independent marginal endpoints.

## Select one conserved history scenario

Use `select_component_history(study, scenario)` for an explicit candidate to
the existing `CompositionalReservoirHistory.Step` boundary. The caller supplies
every interval's stream total, complete mole fractions, average pressure and
pressure sigma. Bind the scenario to the audit input SHA-256, give it a
provenance reference, and declare the complete component order. The
[synthetic selection](../../devtools/pvt_examples/reservoir_component_selection.json)
is an executable input for the component fixture above.

Selections must satisfy the original decimal bounds, including exact
cumulative differences and mole fractions summing exactly to one. The validator
also checks shared cumulative endpoints across all intervals. It retains the
possible initial cumulative-counter interval after intersecting every selected
increment with its original counter bounds. This prevents adjacent intervals
from consuming inconsistent choices of the same uncertain boundary reading.
Ineligible pressure kinds, missing sigma, missing streams and source-identity
changes reject the whole scenario.

For the example, selecting 10 mol production with fractions 0.85 methane
and 0.15 ethane gives 8.5 and 1.5 mol. The independently named pure-water
injection contributes 2 mol. Output contains the original source and selected
scenario with their hashes, each stream's total and component amounts, and
produced/injected arrays in the supplied order. Durations use the accepted
calendar windows in seconds. End-pressure calibration/holdout roles follow
the source. The initial reservoir fluid, pressure and laboratory model still
come from the independently calibrated PVT handoff.

```python
import json
from pathlib import Path

from reservoir_history_intake import select_component_history

folder = Path("devtools/pvt_examples")
source = json.loads((folder / "reservoir_component_intake.json").read_text())
scenario = json.loads((folder / "reservoir_component_selection.json").read_text())
selected = select_component_history(source, scenario)
print(selected["steps"][0]["produced_moles"])
```

Component amounts and selected pressures are serialized as exact decimal
strings. Convert them to doubles at the Java interface and retain the
original component order. The complete source component set must also match
the accepted EOS component set required by the Java history. Reconcile lumped
source species and missing constituents before this handoff. That interface conversion has floating-point
roundoff. The selected produced composition remains a prescribed history
input. Use the existing phase-withdrawal forward mode for predicted produced
composition and GOR.

This validator admits a caller-selected feasible scenario. A posterior grid
still requires explicit joint prior weights and complete forward-model
qualification. Feasible source accounting alone does not establish reservoir
pressure accuracy, a PVT model fit or uncertainty coverage.
