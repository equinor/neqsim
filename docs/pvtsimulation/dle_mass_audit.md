---
title: "Differential liberation laboratory mass audit"
description: "Screen primary DLE measurements for necessary adjacent-stage oil and gas mass consistency, with explicit residual-oil provenance and standard gas intervals."
keywords: "DLE, differential liberation, differential vaporization, laboratory PVT, mass closure, solution GOR, oil formation volume factor, residual oil"
---

# Differential liberation laboratory mass audit

Use `pvt_dle_mass.audit_dle_mass` to screen an isothermal differential
liberation experiment with no material addition and gas as the sole removed
material. This audit uses laboratory observations before EOS fitting. A
compatible row satisfies a necessary interval overlap. Measured accuracy and
holdout acceptance require separate evidence.

## Basis and inputs

Supply `protocol: DLE` and identify the sample, experiment, source, data-use
terms, final differential residual oil and meaning of the input bounds. See the
[synthetic JSON contract](../../devtools/pvt_examples/dle_mass_audit.json)
with both gas-volume routes.
Use one experiment temperature in K. Stage pressures are absolute in bara and
decrease with nonoverlapping pressure intervals. Every stage needs a unique ID
and a source reference. Put the current stage's released-gas molar mass and
standard-state compressibility factor on the current stage.

| Quantity | Exact input unit |
| --- | --- |
| Oil formation volume factor | `m3/m3 residual oil` |
| Oil density at stage conditions | `kg/m3` |
| Solution gas-oil ratio | `Sm3/m3 residual oil` |
| Liberated gas molar mass | `kg/mol` |
| Standard gas temperature and pressure | `K` and `Pa` absolute |
| Stage gas Z at the standard conditions | `1` |
| Gas formation volume factor at the current cell state | `m3 cell/Sm3 gas` |
| Gas Z at the current cell state | `1` |

Intervals contain the fields `unit`, `lower` and `upper`. Supply finite
decimal strings in closed intervals. Endpoint exponents must be between −150
and 150. Use the same final differential residual-oil identity across the
sequence. Separator stock-tank oil and single-flash volumes have their own
definitions. Keep cooldown, nonisothermal transitions and other experiments in
the source record outside this check. A missing property can be omitted or
given as JSON null. A stage pressure remains required to identify ordering.

To use the cell route, supply `gas_volume_factor` and `cell_gas_z` on the
withdrawal stage with a `cell_gas_basis_reference` that ties both measurements
to the same released gas and identifies the shared standard-volume convention
for the formation factor and solution GOR. Keep cell pressure absolute and use
the experiment temperature. With $B_g$ in cell m³ per standard m³, the reported
standard molar volume is $Z_{cell}RT_{cell}/(p_{cell}B_g)$. This calculation
can use the reported volume ratio even when a standalone standard-state
compressibility factor is unavailable. A partial cell route cannot supply a
molar volume.

When both routes are available, the report keeps each interval. Disjoint
intervals are incompatible and have no common mass-residual interval. If they
overlap, the mass check uses their intersection. This is an interval
consistency screen even if both routes derive from related laboratory records.

## Interpretation

Per cubic metre of final differential residual oil, let $B_o$ be the stage oil
volume ratio and $\rho_o$ its density. The observed oil mass lost between
adjacent stages is $\Delta m_o = B_{o,i-1}\rho_{o,i-1}-B_{o,i}\rho_{o,i}$.
With $\Delta R_s=R_{s,i-1}-R_{s,i}$ and released-gas molar mass $M_i$, the
implied standard molar volume is $\Delta R_s M_i / \Delta m_o$. The audit
reports it only when both loss and release intervals are strictly positive.

For the standard-state route the molar volume is $Z_{s,i}RT_s/p_s$, where
$p_s$ is absolute and the stage gas $Z_{s,i}$ is supplied at that state.
With an available molar-volume interval $V_{m,s}$, the signed mass residual
is $\Delta m_o - \Delta R_s M_i / V_{m,s}$.
The residual uses a gas release intersected with the nonnegative physical
range. A strictly negative loss or release, or a residual interval excluding
zero, is incompatible. Implied molar volumes without a declared gas basis,
missing transitions, and zero or indeterminate loss or release can remain
unresolved. All transitions and the original input map are retained along
with a canonical input SHA-256.

Bounds are rounded outward using decimal arithmetic. Independent interval
propagation permits combinations that may be impossible when measurements
are correlated. Use reported uncertainties where available. If only printing
precision is known, describe the result as a printing-precision screen.

## Executable example

From the repository root, use the installed devtools environment:

```python
import json
from pathlib import Path
from pvt_dle_mass import audit_dle_mass

source = Path("devtools/pvt_examples/dle_mass_audit.json")
report = audit_dle_mass(json.loads(source.read_text(encoding="utf-8")))
print([transition["status"] for transition in report["transitions"]])
```

The explicit synthetic example reports `["compatible"]`.
`test_pvt_dle_mass.py` runs this example and compares the computed bounds with
independently enumerated exact-rational interval vertices.

## Primary source screening example

The published copy of [PROLAB 93.104](https://manual.pvt.whitson.com/files/Volve-PVT-Data/01-PVT-data/1993-PROLAB-15-9-19SR-PL037.pdf)
contains the 106 °C differential vaporization of bottle TS-29-06 from
Volve 15/9-19-SR DST 1. Printed pages 8–9 identify the isothermal volumetric
series and evolved-gas analyses. The final residual-oil basis refers to 15 °C
and atmospheric pressure. The separately reported 15 °C cooldown is outside
the isothermal stage audit.

The linked PDF was checked at SHA-256
`be97500f63318bf8243918ef417810b31a424a52796d84d09a30d0507a8b211a`.
Under half-last-digit printing bounds, eight complete transitions had mass
residual intervals containing zero when cell pressure was treated as absolute
and reported gas Z and volume factors were treated as describing the same
withdrawn gas and standard-volume basis as the reported GOR. The widest
absolute endpoint was about 3.54 kg per m³ of final residual oil.
The last isothermal stage has no reported solution GOR and remains unresolved.
These are conditional source-consistency results. The source conventions and
measurement uncertainty still need confirmation for EOS benchmark admission.
The repository example contains original synthetic data.
