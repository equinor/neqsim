# Professional Paper Golden Path

This guide is the shortest route through PaperLab when the goal is a professional,
submission-ready scientific paper rather than a quick draft. It complements
`WALKTHROUGH.md` by showing where the specialist agents and quality gates fit into
the formal workflow in `workflows/new_paper.yaml`.

## What The Golden Path Adds

The standard PaperLab loop already creates a plan, benchmarks, validation, draft,
formatting, and audit package. The professional path adds six gates that make a
paper more reviewer-ready:

| Gate | Agent | Purpose | Required artifacts |
|---|---|---|---|
| Opportunity mining | `paper-opportunity-miner` | Rank publishable NeqSim opportunities | `publication_opportunities.md`, `opportunity_rankings.json` |
| Journal positioning | `journal-fit-strategist` | Pick venue, audience, novelty angle | `journal_fit_report.md`, `journal_ranking.json`, `positioning_brief.md` |
| Hypothesis design | `hypothesis-benchmark-compiler` | Make every research question falsifiable | `hypothesis_matrix.json`, `benchmark_design.md`, `statistical_test_plan.md` |
| Reference data | `dataset-and-reference-curator` | Curate benchmark and validation data | `reference_data_manifest.json`, `reference_data/` |
| Derivation check | `mathematical-derivation-verifier` | Audit equations, units, assumptions, code linkage | `derivation_audit.json`, `derivation_verification_report.md` |
| Reviewer and replication | `reviewer-simulator-panel`, `replication-package-engineer` | Find reviewer objections and package reproducibility | `required_fixes.json`, `reproducibility_manifest.json` |

## Recommended Command Flow

```powershell
cd neqsim-paperlab

# 1. Discover and position the paper before creating the final plan.
python paperflow.py scan --literature --top 10
python paperflow.py list-journals

# 2. Create the paper project once the opportunity and target venue are chosen.
python paperflow.py new "Open title to refine" --journal fluid_phase_equilibria --topic topic_slug

# 3. Complete the specialist gates declared in workflows/new_paper.yaml.
#    Use the named agents to produce the artifacts listed in the table above.

# 4. Run the computational core.
python paperflow.py benchmark papers/topic_slug_2026/
python paperflow.py figures papers/topic_slug_2026/
python paperflow.py draft papers/topic_slug_2026/

# 5. Iterate until manuscript checks and specialist gates are clean.
python paperflow.py iterate papers/topic_slug_2026/ --check all
python paperflow.py audit papers/topic_slug_2026/

# 6. Format, validate, render, and prepare submission.
python paperflow.py format papers/topic_slug_2026/ --journal fluid_phase_equilibria
python paperflow.py validate-figures papers/topic_slug_2026/ --journal fluid_phase_equilibria
python paperflow.py validate-bib papers/topic_slug_2026/
python paperflow.py render papers/topic_slug_2026/
```

## Release Criteria

A PaperLab paper is ready for submission only when all of these are true:

- `refs.bib` passes validation and contains the field-defining sources.
- `hypothesis_matrix.json` maps every research question to metrics and tests.
- `reference_data_manifest.json` records source, units, uncertainty, and transformations.
- `derivation_audit.json` has no unresolved BLOCKER items.
- `results/raw/`, `results/summary.json`, and `validation_report.md` support every quantitative claim.
- `required_fixes.json` has no unresolved BLOCKER items from simulated review.
- `reproducibility_manifest.json` can regenerate each manuscript table and figure or documents why not.
- The final `audit_report.md` confirms claim traceability, seeds, commit hashes, and figure regeneration.

## When To Use A Shorter Path

Use the shorter walkthrough path for exploratory drafts, internal notes, and early
benchmark design. Switch to this golden path before claiming novelty, submitting
to a journal, publishing a preprint, or promoting a paper into a book chapter.