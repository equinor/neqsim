# Nitrogen/water primary-thesis source versions

This offline provenance capability preserves both the image-transcribed primary
thesis and the existing compiler fixtures. It supplies no HV parameters, fit,
measured overall feed, phase tie-line or empirical accuracy threshold. Existing
Java assessments continue to use the unchanged parent-directory compiler CSVs.

## Source and exact representations

Antonin Chapoy, *Phase behaviour in water/hydrocarbon mixtures involved in gas
production systems* (2004), [HAL pastel-00001202](https://pastel.hal.science/pastel-00001202).
The public author-uploaded PDF URL, byte count, SHA256, page locators and the
[primary image inspection receipt](https://github.com/equinor/neqsim/issues/4576#issuecomment-6597956920)
are retained in [provenance.json](provenance.json). The official HAL download was
access-denied; the public author copy was accessible. No PDF or artwork is redistributed.
These files contain factual experimental numbers, retaining printed strings and order,
without the thesis's calculated-model columns.

| Direction | Primary table | PDF/printed page (1-based) | Rows | Printed mole-fraction scale |
|---|---|---|---:|---|
| Water in nitrogen-rich gas | 6.6 | 162/161 | 35 | y_exp × 10^4; multiply entries by 10^-4 |
| Nitrogen in water-rich liquid | 6.16, nitrogen title | 190/189 | 52 | x_exp × 10^3; multiply entries by 10^-3 |

Table6.16 is also used for a different system elsewhere in the thesis: title and
page are part of its identity. Temperature is K and pressure absolute MPa.
Only MPa ×1000 → kPa and the stated mole-fraction scales are applied. No mass,
ppm, standard-volume or gauge conversion is made. The parent compiler fixture
DOIs are 10.1021/je049676q (gas) and 10.1021/je049869d (liquid).

## Complete comparison, without correction

Run `python3 devtools/audit_huron_vidal_nitrogen_sources.py --summary` from the
repository root, or omit `--summary` for every compared row. Both forms are
read-only, require only Python's standard library and are exercised in the CLI
regression test. [comparison.json](comparison.json) retains all87 row mappings,
original numeric strings, both temperatures/pressures/fractions, compiler U95,
signed compiler-minus-thesis differences and printed liquid AD_exp.

| Direction | Exact T/P joins | Exact T/P/fraction | Concentration differences at common T/P | Maximum absolute fraction difference over recorded mappings |
|---|---:|---:|---:|---:|
| Gas | 35/35 | 35/35 | 0 | 0 |
| Liquid | 50/52 | 9/52 | 41 | 5×10^-7 |

The two explicit diagnostic liquid mappings are thesis293.1K → compiler293.12K
at984kPa (thesis row25), and thesis363.00K → compiler363.02K at6842kPa (row52).
They remain flagged **not exact T/P**. The auditor uses these two recorded version
mappings only; any other unmatched state fails. It does not implement nearest-state
matching or modify either temperature. For example, thesis274.18K/3.006MPa gives
x=.0005112 while the compiler has x=.000511. Quantization is compatible with the
size of the concentration differences, but this does not identify an erroneous
source, recover the journal's digits or authorize adding precision.

## Distinct uncertainty and model boundaries

The thesis discussion at PDF189/printed188 gives approximately ±2.5% calibration
uncertainty in **moles of nitrogen**. It supplies no confidence level there and
is not automatically the complete uncertainty of liquid mole fraction. The
liquid `AD_exp_percent_as_printed` column is preserved without assigning an
uncertainty interpretation or confidence level. The existing compiler's expanded
95% absolute mole-fraction uncertainties remain separate and unchanged. Gas-author
uncertainty is not established by this table audit.

The thesis and journal/compiler observations have the same-author experimental
lineage. They are **source versions, not an independent new holdout**. Original
journal images, transcription/version history and full uncertainty budgets still
require reconciliation. A source-table match alone does not qualify the staged
PR-HV profile: original pure critical properties/alpha, pure-water pressure and
bidirectional mixture validation remain required. Thesis VPT/NDD, Henry-law,
CLASSICAL, ordinary NRTL or CPA coefficients are not HV coefficients.

This increment has no runtime EOS path or performance claim. Frozen native
`ThermodynamicModelTest`, independent derivative, strict balance/fugacity/history,
clone and multi-JVM rule2/rule4 performance evidence remains under the parent
assessment and other campaign protocols. No hydrate dissociation, ice/brine,
inhibitor/oil partition, methanol/MEG or extrapolation accuracy follows.

See [VALIDATION.md](VALIDATION.md) for executable gates and limitations.
