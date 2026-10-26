---
name: neqsim-plant-layout-geometry
version: "1.0.0"
description: "Build and verify physical equipment, maintenance and access envelopes for onshore plots and offshore decks, with source-qualified constraints and honest feasibility. USE WHEN: placing equipment, comparing plant arrangements, checking layout clashes/clearance, or reserving maintenance and escape space."
last_verified: "2026-10-25"
---

# Physical layout geometry and checks

Use `layout_model` and `layout_engine` through the study workflow. Read
[the capability guide](../../../docs/development/PLANT_LAYOUT_STUDY.md) before interpreting a result.

## Coordinate and geometry contract

Metres internally; kilograms for mass. Keep origin, axes, north/heading and elevation datum explicit. CAD millimetre/metre
transforms need an exchange check. The first profile uses rectangular plots/decks and orthogonal box/envelope geometry.
Preserve fixed coordinates. Reject nonfinite/negative dimensions, inverted boxes, unknown tags/decks and duplicate IDs.
Separate physical, operational, maintenance and lifting envelopes; an object's own envelope is not a self-clash.

Independently check placement completeness, allowed deck/orientation, transformed physical bounds, deck boundaries/height,
3D physical/obstacle clashes, maintenance space, reserved escape corridors, supplied mass limits and source-qualified
separation rules. Inspect exact messages and units; a solver status does not replace independent checks.
Route connectivity requires geometric corridor evidence and known exits. It does not prove escape capacity, travel-time
adequacy or independence from common fire/gas exposure. Keep those requirements as specialist evidence gaps.

## Onshore profile

Include land/battery limits, roads/fire access, occupied buildings, wind, drainage/flooding, tanks/containment, pipe racks,
utilities, flare/vent exposure, construction transport, expansion and protected brownfield geometry. The box profile
does not solve terrain, drainage, firewater coverage or consequence calculations; retain source-backed constraints or
specialist handoffs for those matters.

## Offshore profile

Include decks/modules, structural interfaces, wellbay/risers, accommodation/refuge, lifesaving access, ventilation and
congestion, cranes/laydown/removal, dropped objects and fire/blast divisions. Require dry, operating and test mass/CoG
cases plus construction/maintenance cases where relevant. A supplied deck mass limit is not a structural assessment.
Floating facilities also require hull, heading, turret/mooring/riser, motion/drainage and stability interfaces. Do not
apply fixed-platform qualification to an FPSO automatically.

## Requirements and optimization

Standard-derived numbers require inspected text, document/edition/clause, applicability and an executable check with
evidence. Source access metadata alone leaves the requirement NOT_EVALUATED. Approved project screening assumptions
are identified separately. Do not import illustrative distances from issue discussions as normative requirements.
Distinguish hazardous-area classification under IEC 60079-10-1 from major-accident consequence/QRA assessments.

Feasibility precedes cost/length scoring. Never soften a required safety constraint into a weighted penalty. Record
backend, seed, grid, candidate/runtime limits and solver status; bounded search does not establish global optimality.
Use deliberate collision, maintenance, blocked-corridor, missing-evidence and rotation fixtures from the regression suite.
Unsupported irregular geometry, detailed piping/stress, hazard contours, structures and marine engineering need qualified
adapters and evidence before their checks can pass.
