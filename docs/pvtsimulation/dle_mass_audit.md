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
[synthetic JSON contract](../../devtools/pvt_examples/dle_mass_audit.json).
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

Intervals contain the fields `unit`, `lower` and `upper`. Supply finite
decimal strings in closed intervals. Endpoint exponents must be between −150
and 150. Use the same final differential residual-oil identity across the
sequence. Separator stock-tank oil and single-flash volumes have their own
definitions. Keep cooldown, nonisothermal transitions and other experiments in
the source record outside this check. A missing property can be omitted or
given as JSON null. A stage pressure remains required to identify ordering.

## Interpretation

Per cubic metre of final differential residual oil, let $B_o$ be the stage oil
volume ratio and $\rho_o$ its density. The observed oil mass lost between
adjacent stages is $\Delta m_o = B_{o,i-1}\rho_{o,i-1}-B_{o,i}\rho_{o,i}$.
With $\Delta R_s=R_{s,i-1}-R_{s,i}$ and released-gas molar mass $M_i$, the
implied standard molar volume is $\Delta R_s M_i / \Delta m_o$. The audit
reports it only when both loss and release intervals are strictly positive.

If the caller supplies the standard gas temperature $T_s$, absolute pressure
$p_s$, and a justified per-stage standard gas $Z_{s,i}$ interval, the signed
mass residual is $\Delta m_o - \Delta R_s M_i p_s / (Z_{s,i} R T_s)$.
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
