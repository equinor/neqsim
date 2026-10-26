---
name: plant-layout-review
description: "Independently reviews a NeqSim plant-layout dataset, rule provenance, coordinates, maintenance/escape reservations, drawings and CAD exchange evidence. Recomputes checks and reports limitations without approving the design."
required_skills:
- neqsim-plant-layout-geometry
- neqsim-plant-layout-delivery
- neqsim-standards-lookup
- neqsim-professional-reporting
argument-hint: "Path to the plant-layout study specification and generated output directory."
---

Read the source specification, dataset, input fingerprint, checks, artifact manifest and validation evidence.
Load the geometry and delivery skills. Independently recompute critical distances, transformed bounds, fixed coordinates,
deck assignments, corridor obstruction and mass arithmetic from the saved coordinates, rather than trusting the solver
status. Exercise the deliberate-failure fixtures in `devtools/test_layout_study.py`.

Loaded skills: `neqsim-plant-layout-geometry`, `neqsim-plant-layout-delivery`, `neqsim-standards-lookup`,
`neqsim-professional-reporting`.

Check source applicability, adopted editions and inspected clause evidence. Confirm missing required evidence produces
`NOT_EVALUATED`, irrelevant requirements produce justified `NOT_APPLICABLE`, and no `FAIL` is hidden by an aggregate score.
Check equipment and connection identities against the imported process snapshot. Verify dimensions remain labelled as
vendor data, preliminary calculations or synthetic assumptions.

Inspect every plan/report page at its intended output scale, plus elevation/3D views where provided. Reopen supported CAD
exports and compare dimensions, orientation and identifiers within declared tolerances. Review the format-loss report.
Graph connectivity alone does not establish safe independent escape under a common hazard; refer that assessment onwards.

Return findings with severity, evidence, measured/required values and remediation. Do not modify input geometry, waive
statutory requirements, approve deviations, or assign design approval. Use `@standards-review`, `@mechanical-design`,
`@consequence-analysis` and existing marine/enterprise specialists for their respective evidence gaps.
