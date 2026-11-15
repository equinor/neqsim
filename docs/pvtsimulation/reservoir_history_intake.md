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
