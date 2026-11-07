---
name: neqsim-jurisdiction-regulatory-basis
calculation_basis: "advisory"
version: "0.1.0"
description: "Identifies the country and regulatory regime of the evaluated asset and records the authorities, governing laws, regulation families, recognised standards and fiscal basis that apply, with Norway, UK, US, Brazil and EU profiles. USE WHEN: any study, design, safety, emissions, layout, mechanical-design or economics task evaluates an asset in a specific country, or the country is not stated and must be asked or declared as an assumption."
last_verified: "2026-11-07"
requires:
  python_packages: []
  java_packages: []
  env: []
  network: []
---

# Jurisdiction and Regulatory Basis

Engineering results are only acceptable in the country where the asset operates. This skill makes the agent identify that country first, load the matching public profile (authorities, laws, regulation families, recognised standards, fiscal note, official sources) and record what was and was not checked. It orients the study; it does not interpret law and never states compliance.

## When to Use

- A task names a country, sea area, field, licence or authority (Norway, NCS, UKCS, Gulf of Mexico, Brazil, Denmark ...).
- A task involves safety, relief and flare, fire protection, layout, emissions, discharges, pressure equipment, pipelines, well design, decommissioning or economics, where local law sets the acceptance criteria or the tax basis.
- The country is not stated: ask once, or record an explicit assumption and a data gap.

## Inputs

- `country`: country, area or code of the asset.
- `facility_scope`: for example offshore production platform, subsea tie-back, onshore terminal.
- `user_confirmed`: whether the user stated the jurisdiction.

## Outputs

- `jurisdiction_basis.v1` block: `status` (`profile-selected-not-verified` or `unknown-jurisdiction`), `profile` (authorities, laws, safety regulations, recognised standards, pressure-equipment route, fiscal note, data sources), `assumptions`, `data_gaps`.
- Copy `assumptions` and `data_gaps` into `results.json`, and name the regulation family behind every acceptance criterion in the report.

## Engineering Method

1. **Identify the regime.** Resolve the country with `resolve_profile`. Never default silently to Norway or to any country. If unknown, ask once; otherwise declare "local regulations not considered" as a data gap.
2. **Load the profile.** The profile lists who regulates what (safety, environment, resources, tax) and which regulation family governs the activity.
3. **Separate three layers in every requirement table.** (a) law and regulation (mandatory), (b) recognised standards the regulation points to, (c) company or project requirements, which may be stricter. Never relax a regulatory requirement because a company or vendor value is looser.
4. **Regime type changes the method.** Prescriptive regimes cite fixed limits; goal-setting regimes (Norway, UK) require demonstrating risk reduction, so report the demonstration basis (barriers, ALARP, safety case) rather than a single pass/fail number.
5. **Match the fiscal and emissions basis to the same jurisdiction.** Tax rates, CO2 tax or ETS, flaring consent and local-content rules come from the same profile; do not mix regimes inside one study.
6. **Verify at the source.** Fetch the current regulation text from the official source in the profile (for example lovdata.no, legislation.gov.uk, eCFR, gov.br/anp). Revision dates change; record the revision used.
7. **Escalate.** Statements of legal compliance, permits and consents are for the responsible authority and qualified staff; list them as open points.

Profiles shipped: `NO`, `GB`, `US`, `BR`, `EU` (EU is a baseline: add the member-state authority and transposition). Add other countries by extending `profiles.py` with a source for each entry.

## Python Usage Pattern

```python
from jurisdiction_basis import jurisdiction_block

block = jurisdiction_block("Norway", "offshore production platform", user_confirmed=True)
results["assumptions"].extend(block["assumptions"])
results["data_gaps"].extend(block["data_gaps"])
results["jurisdiction"] = block
```

## Validation Checklist

- [ ] Country stated by the user, or recorded as an assumption.
- [ ] Every acceptance criterion names its source layer: regulation, standard or company requirement.
- [ ] Regulation revision and source URL recorded; nothing quoted from memory.
- [ ] Tax, emissions and flaring basis belongs to the same jurisdiction as the safety basis.
- [ ] Unverified or unknown regime appears in `data_gaps`, not in conclusions.
- [ ] Compliance, permits and consents are left as open points for the authority.

## Common Mistakes

| Symptom | Cause | Fix |
| --- | --- | --- |
| Norwegian rules applied to a Brazilian asset | Default jurisdiction | Resolve the country first, ask if missing |
| Report says "compliant with regulation" | Profile used as proof | Use "screened against" and list unverified items |
| NORSOK value used as law in the UK | Standard mistaken for regulation | Check the UK goal-setting regime and ALARP demonstration |
| Old tax rate used | Rates quoted from memory | Fetch the current rate from the authority source |

## Limitations

- Public orientation profiles only; no clause text, limits or legal advice.
- Profiles cover five regimes. Other countries must be researched from the official source and recorded as an assumption.
- Regulation names and revisions change; the profile can lag the current text.

## Related NeqSim Functionality

- Feeds the task workflow (`neqsim-task-workflow` scoping question on standards and jurisdiction), `results.json` `assumptions` and `data_gaps`, and the standards-aware classes under `neqsim.process.mechanicaldesign` and `neqsim.process.safety`.
- Complements `neqsim-standards-lookup`, `neqsim-norwegian-continental-shelf-data` and `neqsim-field-economics` (fiscal regimes). It does not run calculations itself.

## References

- Norway: https://lovdata.no, https://www.havtil.no, https://www.sodir.no
- United Kingdom: https://www.legislation.gov.uk, https://www.hse.gov.uk/offshore, https://www.nstauthority.co.uk
- United States: https://www.ecfr.gov, https://www.bsee.gov, https://www.phmsa.dot.gov
- Brazil: https://www.gov.br/anp
- European Union: https://eur-lex.europa.eu
