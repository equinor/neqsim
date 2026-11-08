---
name: neqsim-jurisdiction-regulatory-basis
calculation_basis: "advisory"
version: "0.2.0"
description: "Identifies the country and regulatory regime of the evaluated asset and records authorities, governing laws, regulation families, recognised standards and fiscal basis, with NO, GB, US, BR, AO, CA and EU profiles and a per-topic well and SURF register. USE WHEN: any study, design, safety, emissions, layout, well-planning, SURF or economics task evaluates an asset in a specific country, or the country is not stated and must be asked or declared as an assumption."
last_verified: "2026-11-08"
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

Profiles shipped: `NO`, `GB`, `US`, `BR`, `AO`, `CA`, `EU` (EU is a baseline: add the member-state authority and transposition; `AO` and `CA` are orientation profiles with lower confidence). Add other countries by extending `profiles.py` and `activity_register.py` with a source for each entry.

## Well and SURF design basis

`activity_basis(country, ("well", "surf"))` adds the instruments to check per design topic, so the design agents know which regulation family drives each decision before they choose a method.

| Topic key | What it covers |
| --- | --- |
| `well_design_integrity` | well design, barrier envelopes, integrity management |
| `well_control_bop` | BOP, well control, drilling and well facilities |
| `subsea_equipment` | wellheads, trees, manifolds, controls, production safety systems |
| `flowlines_pipelines` | rigid flowlines and pipelines |
| `risers` | steel and flexible risers, riser-to-host connection |
| `flexibles_umbilicals_cables` | flexible pipe, umbilicals, power cables |
| `materials_sour_chemicals` | materials, sour service, chemical use |
| `flaring_venting_discharges` | well test and process flaring, venting, discharges |
| `decommissioning_pa` | plugging and abandonment, decommissioning |
| `approvals_verification` | consents, notifications, safety case, independent verification |

Each row has `layer` (`law`, `regulation`, `guidance`, `standard-referenced`, `contract`) and `status`: `verified-public-source` (title and structure read at the official source on `verified_on`; today the 30 CFR Part 250 subparts and the ANP regime description) or `orientation-verify`. Topics without a row appear in `coverage_gaps` and `data_gaps`; they are never filled from memory.

How the regime changes the design method:

- **Goal-setting regimes (Norway, UK, Brazil)** accept a justified code but demand a demonstrated barrier or risk argument; record the demonstration basis, not one pass/fail number.
- **Prescriptive elements (US)** fix part of the design in law, for example internal-pressure wall thickness for lease pipelines and risers; a more permissive code (API RP 1111) needs the authority's alternate-compliance route.
- **Contract-based regimes (Angola)** put technical requirements in the block contract and operator procedures; for a non-operated asset the operator design basis governs and the partner reviews against its own requirements.
- **Ask the operator and authority interface early**: who operates, which consents precede spud and installation, and whether independent verification is statutory.

```python
from jurisdiction_basis import activity_basis

basis = activity_basis("Brazil", ("well", "surf"), facility_scope="pre-salt FPSO tie-back", user_confirmed=True, operator="Equinor")
results["jurisdiction"] = basis
results["assumptions"].extend(basis["assumptions"])
results["data_gaps"].extend(basis["data_gaps"])
for topic, item in basis["activities"]["surf"].items():
    for row in item["instruments"]:
        print(topic, "|", row["layer"], "|", row["instrument"], "|", row["status"])
```

Company requirements (for example operator TR, GL and FR documents with country-specific sections) are not held here; enterprise overlays add them as the third layer. Well and SURF skills call this function in their scoping step: `neqsim-integrated-well-planning`, `neqsim-well-design-study`, `neqsim-surf-design-study`, `neqsim-surf-field-layout-design`.

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
- Profiles cover seven regimes and the register is orientation data with few verified rows. Other countries must be researched from the official source and recorded as an assumption.
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
