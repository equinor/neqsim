---
name: neqsim-plant-layout-delivery
version: "1.0.0"
description: "Produce and inspect source-traceable plot/deck drawings, coordinate/check schedules, layout reports and optional CAD exports from the canonical physical layout dataset. USE WHEN: a plant layout needs engineering drawings, CAD exchange, professional reporting or independent deliverable review."
last_verified: "2026-10-25"
---

# Plant layout drawings, CAD and reports

Drive `layout_drawings`, `layout_cad` and `layout_report` through `neqsim layout-study`.
Reuse existing drawing/report conventions; preserve the same project/revision/tags/coordinates across every artifact.
The supported geometry and adapter limitations are documented in
[the implementation guide](../../../docs/development/PLANT_LAYOUT_STUDY.md).

## Required package

Retain the input snapshot, canonical alternatives/check dataset, source/fingerprint evidence, coordinate and findings
schedules, plot/deck plans and the report. Draw separate readable escape, maintenance/lifting and hazard-interface
overlays where modeled. Include north/heading, scale, dimensions/elevations, units, tags, legend, revision/title blocks
and maturity. Describe omitted services and geometry explicitly.

Generate PDF/HTML and supported editable report formats from dataset values. Show every required failure or missing
assessment, standards access/applicability, assumptions and specialist handoffs. Compare feasible modeled alternatives
using named proxy metrics; do not present a centerline proxy as installed piping quantities or cost.

Optional STEP/GLB/DXF exports require adapters to be available. Record tool version, exported objects/metadata,
unit transforms and unsupported properties. Retain editable parametric/model source and a format coverage/loss report.
A tessellated GLB view is not a substitute for the canonical geometry or a full plant-design authoring format.

## Verification

Render and inspect every drawing/report page, including whole-sheet and detail views at intended plotted scale.
Fix clipped or overlapping text, missing tags, inconsistent units/coordinates/revisions and unreadable legends in the
generator, then regenerate. Keep check/coordinate schedules machine-readable, and record SHA-256 for deliverables.
Reopen supported exchange output and compare critical dimensions, orientation, object count/identities and bounds within
declared tolerances. Record adapters not executed rather than claiming an exchange passed.

Use `@plant-layout-review` for independent checking, and existing standards/mechanical/consequence specialists for
unresolved engineering evidence. Passing a software or visual check does not promote a SCREENING study to approval.
Keep project data, licensed rules and company title blocks in the task/enterprise layer.
