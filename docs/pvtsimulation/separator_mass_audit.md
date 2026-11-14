---
title: "Laboratory separator-train mass audit"
description: "Screen serial oil separator measurements against reported gas release, common stock-tank normalization and total mass conservation before EOS calibration."
keywords: "PVT, separator test, multi-stage separation, stock tank oil, solution GOR, gas release, formation volume factor, mass closure"
---

# Laboratory separator-train mass audit

Use `pvt_separator_mass.audit_separator_mass` for laboratory tests where oil
passes through a sequence of lower-pressure separators, stage gas is removed,
and the remaining oil becomes the feed for the next stage. Use the
[DLE audit](dle_mass_audit.md) for a differential liberation pressure series.
This source check does not run a flash or fit fluid parameters.

## Input and basis

Use protocol `SEPARATOR` and supply source references for the sample,
experiment, stock-tank oil, data-use terms and interpretation of the bounds.
The first row describes the incoming oil. Subsequent rows describe the oil
after each stage and the gas removed at that stage. The last row must be the
actual stock-tank oil reference used to normalize all volumes. Each row needs
an ID, a source reference and its own conditions. Absolute pressure intervals
must decrease without overlap. Temperature can vary between stages.

| Source field | Input unit |
| --- | --- |
| `pressure`, `temperature` | `bara`, `K` |
| `oil_volume_ratio` | `m3/m3 stock tank oil` |
| `oil_density` | `kg/m3` at the row conditions |
| `solution_gor`, `released_gas` | `Sm3/m3 stock tank oil` |
| `standard_gas_density` | `kg/Sm3` for the stage's released gas |
| `released_gas_molar_mass`, `standard_gas_z` | `kg/mol`, `1` for the released gas |
| `standard_gas.pressure`, `standard_gas.temperature` | `Pa` absolute, `K` |

Supply each quantity as a closed interval with `lower` and `upper` decimal
strings and the exact `unit` above. Missing observations can be omitted or
set to null. Distinguish derived oil densities from measured densities in
`oil_density_reference`. In `standard_gas_property_reference`, tie the
density or the molar mass and Z to the same gas cut and common standard gas
state used for the reported volumes. Use the declared standard-state Z for
that gas cut. Gas gravity alone provides no independent standard density
unless its reference density and the report's calculation basis are known.

## Residuals and source checks

Per cubic metre of final stock-tank oil, the oil mass after stage $i$ is
$B_i\rho_i$. The gas mass is $q_i\rho_{g,i}$, where $q_i$ is the independently
reported gas volume on the common standard basis. The gas density can be
reported directly or calculated as $M_ip_s/(Z_iRT_s)$. When both routes
exist, the audit checks their intersection and reports disagreements.

The stage mass and GOR residuals are
$B_{i-1}\rho_{i-1}-B_i\rho_i-q_i\rho_{g,i}$ and $R_{i-1}-R_i-q_i$.
Here $R_i$ represents the gas volume still to be removed along the reported
train. It is zero at the endpoint by definition even though the final oil
can contain dissolved light components. The final oil volume ratio is
one by definition. When a reported endpoint interval contains its exact
value, the audit uses that value. A missing or incompatible endpoint does
not supply a value for the corresponding balance. The raw observations and
their input SHA-256 are retained.

Whole-train residuals compare feed and final oil directly and sum the
reported gas withdrawals. Intermediate oil inventories cancel, so a whole
train may close with missing or disagreeing intermediate values. Review each
transition and the endpoint alongside the whole-train result. If oil mass
loss and gas volume are strictly positive, the audit reports their ratio as
an implied gas density for diagnosis. It is never fed back into a closure
check. A strictly increasing oil mass is incompatible with gas removal even
when the gas density is missing.

A compatible row encloses zero using outward-rounded interval propagation.
Input correlations can make the true feasible region smaller. Use actual
measurement uncertainties when available. Values derived from each other can
provide a bookkeeping check, with independent measurements required for a
physical validation claim.

## Reproduce the synthetic example

The [synthetic input](../../devtools/pvt_examples/separator_mass_audit.json)
contains a feed, an intermediate separator and a final stock tank. The
documented repository test reads this exact file, verifies the input
identity and checks both balances for every transition and the complete train.

```python
import json
from pathlib import Path

from pvt_separator_mass import audit_separator_mass

path = Path("devtools/pvt_examples/separator_mass_audit.json")
report = audit_separator_mass(json.loads(path.read_text(encoding="utf-8")))
print(report["whole_train"])
```

## Primary source screening

[ResLab 70-00286](https://manual.pvt.whitson.com/files/Volve-PVT-Data/01-PVT-data/2008-Reslab-15-9-F4.pdf)
documents a two-stage separator test for Volve well 15/9-F-4, MDT bottle
6103-MA. The checked copy had SHA-256
`f493b6ca4e56a6afc31089871a5325b671d5763066f3401e0f1862df016bc89f`.
Use printed page 29, Table 4.6.1, for the feed and serial stage data.
Pages 30–31 report the gas compositions and molar masses, and page 32
describes the final oil. The gas reference condition of 15 °C and 1.01325
bara is taken from the same report's standard-basis definition on page 17.
The feed density for this train is calculated as identified in the executive
summary. The single-stage experiment's measured feed density and its
different final oil belong to that other experiment. The bottle has reported
oil-based-mud contamination.

Under half-last-digit printing bounds, both stage GOR residuals and the
whole-train GOR residual enclosed zero. The first-stage oil mass loss was
61.70325–63.47355 kg per m³ of final oil. The final volume ratio printed
in Table 4.6.1 excluded one. The two gas cuts have molar masses but the
corresponding gas Z or independently reported standard density was not found.
Keep the terminal ratio and both gas-mass balances unresolved for admission
pending source clarification and gas-basis evidence. The data-use record
for the specific standalone archive object remains to be verified.
