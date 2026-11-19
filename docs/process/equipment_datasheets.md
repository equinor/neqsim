---
title: "Equipment Datasheet Generator"
description: "Generate tagged engineering datasheets and a weight-coverage register from a NeqSim process simulation."
---

# Equipment Datasheet Generator

`neqsim.process.mechanicaldesign.EquipmentDatasheetGenerator` exports a preliminary
equipment register and one JSON datasheet per physical unit in a `ProcessSystem`.
It traverses process modules and records excluded simulation streams and solver
objects in `excludedSimulationObjects`. The material-stream tags on each sheet
can be joined to the corresponding line register or P&ID in the same revision.

## Generate the package

1. Solve the process and configure the mechanical design basis for each item.
2. Run the applicable equipment mechanical-design calculation. The datasheet
   generator reads the resulting design and process state. The engineer selects
   governing loads, design margins, materials, and operating cases before issuing
   the sheets.
3. Construct `EquipmentDatasheetGenerator` with the process. Set the project
   name, document-number prefix, and revision. Call `generateAllDatasheets()`
   for the complete JSON package, or `generateDatasheet(equipment, sequenceNumber)`
   for one item.
4. Reconcile the package against the P&ID equipment register. Render a controlled
   sheet for each tag. Carry the JSON, drawing reference, design-case ID,
   revision, calculation sources, and outstanding inputs into the handover.

The same Java methods are available in Python through `jneqsim`. The export is
read-only with respect to process conditions and does not execute the simulation
or mechanical sizing. Unsolved cases retain the equipment sheets and show an
operating-data hold.

## Sheet contents

| Section | Source |
| --- | --- |
| Identification | Process tag, equipment class, document number, project, and revision |
| Material connections | Inlet and outlet stream tags, absolute pressure, temperature, and mass flow from the solved case |
| Process performance | Equipment-specific fields where available, including separator geometry, compressor performance, and heater/cooler duty |
| Design basis | Available design pressure and temperature limits, corrosion allowance in mm, wall thickness where the unit is known, and the unit-labelled mechanical design snapshot |
| Weight | Total dry equipment estimate, separate vessel shell weight when available, normal operating weight when evaluated, and hydrotest weight status |
| Holds | Missing weights, unavailable data, and unresolved operating conditions |

The operating-property sections describe one solved case. For a min/normal/max
datasheet, generate the set for each case and reconcile the sheets by tag in the
controlled report. Use the authoritative equipment or piping model for port
duties and detailed nozzle data.

## Weight basis and completeness

- `weights.dryWeight_kg` comes from the mechanical design's total dry
  weight. `weights.vesselShellWeight_kg` records only the shell estimate and
  is already within the applicable dry total.
- `weights.operatingWeight_kg` uses the equipment mechanical response. For
  a sized horizontal separator with a gas/liquid equilibrium state, it adds
  the cylindrical-shell contents at the design normal liquid and interface
  levels to the dry weight. The contents estimate covers the cylindrical
  shell. Heads and attached piping inventory need separate allowances.
- A missing weight is JSON `null` and has a corresponding hold. Hydrotest
  weight requires an independently specified test and inventory basis.
- `weightSummary` gives a known subtotal and known/missing item counts by
  condition. A complete total appears only when every sheet has a basis for
  that condition. Review package inclusions before adding piping or
  structural allowances that may overlap the dry estimate.
- Check the governing purchase and lift weights against vendor data. Account
  for rigging, temporary steel, and installation allowances separately.

All generated sheets carry the `PRELIMINARY` issue status.

## Related documentation

- [Mechanical Design](mechanical_design.md)
- [Cost Estimation Framework](COST_ESTIMATION_FRAMEWORK.md)

## Mechanical datasheets

Use `generateMechanicalDatasheet(equipment, sequenceNumber)` for one equipment item
or `generateAllMechanicalDatasheets()` for the process register. Each
`MechanicalDatasheet` can export versioned JSON with `toJsonObject()` or `toJson()`
and a readable Markdown sheet with `toMarkdown()`. It reuses the equipment ports,
mechanical response, normalized geometry and design basis. Design and sizing
calculations run on the original configured equipment before the export.

The generic sheet covers the reference drawing, governing design cases and code
edition, design and operating conditions, geometry, materials, equipment weights,
site loads and supports, inspection, tests, and vendor documentation. Families with
typed models add construction and review fields for rotating equipment, heat
exchangers, valves, columns, pipelines, and separators. A separator adds shell
and head fields, the model liquid and interface levels, the internals and
auxiliary connections, and a process nozzle schedule. The calculated bore is a
process screening dimension. It is independent of the nominal flange size,
connection rating, material, orientation, local load checks and reinforcement.

Fields show a value, unit, status and source. **MODEL** and **ESTIMATE** are
preliminary. **CONFIGURED OR DEFAULT** requires confirmation against the
project design cases. **HOLD** identifies missing information. **SUPPLIED**
means the caller entered a controlled requirement. Enter controlled values
using `setRequirement(key, value, source)` with the key from the JSON field.
For a hydrotest weight estimate, supply an explicit test assembly dry mass,
filled volume and test-fluid density through `setHydrotestWeightBasis`. The
result is dry test assembly mass plus filled-volume mass. Confirm the separate
test pressure, test temperature, scope and acceptance basis before use.

Begin with the approved project inputs and current vendor or design
information. For vessels, enter the selected plate and head geometry, the
pressure boundary calculations, external pressure and minimum design metal
temperature, nozzle details and load checks. Use the project's governing
standard and edition. ASME BPVC Section VIII Division 1 describes pressure
vessel construction requirements. IOGP S-619 documents the role of a
procurement data sheet alongside the supplementary, information and quality
requirements. The NeqSim outputs carry the preliminary issue status.

- [ASME Boiler and Pressure Vessel Code](https://www.asme.org/codes-standards/bpvc-standards)
- [IOGP S-619 pressure vessel specification and document structure](https://www.iogp-jip33.org/wp-content/uploads/2021/09/Specification-for-Unfired-Fusion-Welded-Pressure-Vessels-S-619v2021-09.pdf)
