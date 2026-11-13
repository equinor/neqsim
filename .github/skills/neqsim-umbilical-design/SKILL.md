---
name: neqsim-umbilical-design
calculation_basis: "screening"
version: "0.1.0"
description: "Subsea umbilical sizing and drawings with the NeqSim umbilical physics: tube wall, cross-section and weights, armour from catenary tension, chemical and hydraulic line friction and response, power cable with charging current, design checks, figures and study hand-off. USE WHEN: a SURF or tie-back task needs an umbilical sized and drawn instead of a cost-library line item, or chemical injection, hydraulic response, power step-out or bend radius must be answered."
last_verified: "2026-11-13"
requires:
  python_packages: ["matplotlib"]
  java_packages: ["neqsim"]
  env: []
  network: []
---

# Umbilical Design and Visualisation

Use this skill to turn "a 15 km control umbilical" into a sized, checked and drawn design with
numbers that trace to NeqSim. It wraps the NeqSim umbilical physics (`Umbilical`,
`UmbilicalMechanicalDesign` and the hydraulics, electrical and structural calculators) and adds the
Python side that studies need: a spec that can be filled from a study basis, circle-packed
cross-section drawings, charts, captions, key numbers and a `HANDOFF.md`.

## When to Use

- A SURF or tie-back study has an umbilical only as a cost-library length and it must now be
  designed: diameter, weight, armour, bend radius, chemical and hydraulic line sizes, power cable.
- A flow-assurance hand-off gives inhibitor demand (MEG, methanol, scale inhibitor) and the chemical
  line must be checked for friction drop and delivery pressure.
- A control-system question asks how long a hydraulic line takes to respond over the step-out.
- A power cable in the umbilical must be checked for voltage drop, loss and charging current.
- A report needs a cross-section drawing, a tension/bend-radius view or an "umbilical on a page".

Do not use it for dynamic riser sections, fatigue, torsion balance, crush or installation analysis,
or for a vendor-qualified design.

## Inputs

A plain dictionary (`UmbilicalSpec.from_dict`) or the `umbilical` block of a study
`COMMON_BASIS.json` (`UmbilicalSpec.from_common_basis`):

| Key | Meaning | Unit |
| --- | --- | --- |
| `length_km` or `length_m` | umbilical length | km or m |
| `water_depth_m` | water depth at the subsea end | m |
| `lines[]` | `name`, `kind` (`hydraulic` or `chemical`), `inner_diameter_mm`, `design_pressure_bar`, `design_flow_m3_per_hr` | mm, bar, m3/h |
| `cables[]` | `name`, `cores`, `voltage_v`, optional `conductor_area_mm2` | V, mm2 |
| `fibers[]` | `name`, `count` | - |
| `power_load_mw`, `power_factor` | load at the subsea end | MW |
| `seawater_temperature_c` | line fluid temperature | C |
| `touchdown_offset_m`, `max_installation_tension_kn` | catenary layback and vessel limit | m, kN |
| `evidence` | status per input (`user`, `screening default`, or an enterprise source) | - |

Missing length or depth is an error, never a silent default.

## Outputs

- `DesignResult` (the `toJson()` of `UmbilicalMechanicalDesign`): per-element geometry and
  hydraulics, `calculatedResults`, `designChecks`, weights, `crossSection`, `hydraulicCurves`,
  `catenaryProfile`, `powerTransmission` and `powerSweep`.
- Figures (PNG, 200 dpi, white background, title, basis version and source on every figure):
  `fig1_cross_section`, `fig2_hydraulics`, `fig3_mechanical`, `fig4_electrical`,
  `fig5_design_checks`, `fig6_umbilical_on_a_page` (A3, also PDF).
- `data/key_numbers.json` (value, unit, source per number), `data/umbilical_design.json`, and a
  `HANDOFF.md` with ready-to-paste captions, findings, input evidence, screening defaults and open
  items.

## Engineering Method

All physics is in NeqSim Java; the screening defaults are listed in the hand-off.

| Topic | Method |
| --- | --- |
| Tube wall, internal pressure | `t = P ID / (2 (S + P Y - P))`, `S = 0.6 SMYS`, `Y = 0.4` (ASME B31.3 form) |
| Tube wall, external pressure | DNV-ST-F101 combined collapse `(pc - pel)(pc^2 - pp^2) = pc pel pp f0 D/t`, ovality 0.5 %, safety factor 1.5 at hydrostatic pressure of an empty tube |
| Cross-section | element outer diameters packed with a circle-packing efficiency (0.5 to 0.8), inner sheath, helical armour wires (`n = pi D cos(a) / d_w`), outer sheath; weights from material densities, fluid fill and seawater displacement |
| Line friction | Darcy-Weisbach, laminar `64/Re`, Haaland turbulent, blend 2300 to 4000; fluid properties by line name (water-glycol, MEG, methanol, chemical) or `UmbilicalFluid.fromNeqSim` |
| Line response | distributed resistance-compliance line, `tau = 4 R C / pi^2`, exact series for the closed far end, acoustic transit `L/c` as a lower bound, tube-wall compliance included |
| Power cable | exact long-line two-port, `R_ac = R_dc (1 + Ys)` (IEC 60287-1-1), coaxial capacitance, trefoil reactance; charging current and no-load voltage rise |
| Tension and bend | free-hanging catenary with horizontal tangent at touchdown, `T_top = w (a + h)`, bend radius at touchdown `a = H / w`; armour layers chosen as the smallest count whose capacity covers the amplified tension including its own weight |
| Checks | self-sinking, hoop stress, collapse, armour capacity, touchdown radius against minimum bend radius, vessel tension limit, line friction below half the line design pressure |

The Python cross-section is an independent circle packing. `packing_ratio` (packed bundle diameter
over NeqSim's bundle diameter) above about 1.1 is reported as a finding.

## Python Usage Pattern

```python
from umbilical_design import UmbilicalSpec, run_design, write_handoff, FigureMeta

spec = UmbilicalSpec.from_dict({
    "name": "Control umbilical",
    "length_km": 15.0,
    "water_depth_m": 100.0,
    "lines": [
        {"name": "LP hydraulic supply", "kind": "hydraulic", "inner_diameter_mm": 9.5, "design_pressure_bar": 345},
        {"name": "MEG", "kind": "chemical", "inner_diameter_mm": 25.4, "design_pressure_bar": 345,
         "design_flow_m3_per_hr": 0.5},
    ],
    "cables": [{"name": "Power", "cores": 3, "voltage_v": 3300, "conductor_area_mm2": 16}],
    "fibers": [{"name": "Fibre", "count": 12}],
    "power_load_mw": 0.05,
})
result = run_design(spec)            # needs NeqSim with the umbilical physics
write_handoff(result, "umbilical", spec, FigureMeta("Control umbilical", basis_version="2026-11-13 02:17"))
```

Without a JVM, render from a saved result: `python -m umbilical_design render --result
data/umbilical_design.json --out out --basis-version "..."`. In a NeqSim source checkout pass the dev
namespace: `run_design(spec, ns=neqsim_init(project_root=...))`. Direct Java use:
`Umbilical u = new Umbilical("x"); u.addChemicalLine("MEG", 25.4, 345.0);
u.setInjectionRate("MEG", 0.5, "m3/hr"); u.getMechanicalDesign().calcDesign();`.

## Using it in a study

1. Add an `umbilical` block to the study basis (length, depth, lines, cables) with the evidence
   status of each input. Take chemical rates from the flow-assurance hand-off and the host
   interface (hydraulic supply, power, number of spare tubes) from the design basis.
2. `run_design`, then `write_handoff` into the study folder of the umbilical workstream.
3. Copy `key_numbers.json` into the study `results.json` so that report numbers are traceable.
4. Use the PNG captions in the report. The cross-section, charts and one-pager are not flow
   diagrams; a control/chemical/power schematic of the umbilical functions is one and must pass
   `neqsim pfd-check` before it is used.
5. State open items from the hand-off (J-tube pull-in, host HPU and power interface, spares) in the
   decision list; do not close them with this screening.

## Validation Checklist

- [ ] Length and water depth come from the route and bathymetry, not from a cost-library item.
- [ ] Every line has a fluid by name or from NeqSim, and a design flow where friction matters.
- [ ] Hand-off lists which inputs are `screening default` and which come from evidence.
- [ ] The `lineFrictionDropWithinPressureBudget` check was read for every chemical line.
- [ ] Hydraulic 90 % response time was compared with the valve response requirement.
- [ ] Power result was compared with the subsea equipment voltage window including charging current.
- [ ] Touchdown radius was compared with the minimum bend radius.
- [ ] Figures show the basis version; no figure was edited by hand.
- [ ] Results are labelled screening, not construction design.

## Common Mistakes

| Symptom | Cause | Fix |
| --- | --- | --- |
| Chemical line looks fine but cannot deliver | no design flow set, so friction was not checked | set `design_flow_m3_per_hr` from the flow-assurance demand |
| MEG line needs hundreds of bar | cold MEG is 40 to 50 mPa s at 4 C and laminar friction scales with `L / D^4` | increase the inner diameter or reduce the rate; dilute inhibitor lowers viscosity |
| Collapse sets the wall in deep water | external pressure on an empty tube | check the tube is kept pressurised in service, or accept the thicker wall |
| Power cable result negative voltage drop | charging current raises the receiving voltage at light load | compare with the equipment voltage window; consider shunt reactors for long step-out |
| `RuntimeError: ... predates the umbilical physics` | older NeqSim on the classpath | use a newer NeqSim or load a saved result |
| Layout finding on packing ratio | NeqSim packing efficiency optimistic for this mix | enlarge the bundle or re-check element sizes |

## Limitations

- Static, screening-level design: no dynamic riser analysis, fatigue, torsion balance, crush,
  installation or burial design.
- Generic fluid properties unless a NeqSim fluid is supplied; temperature is one value along the
  line.
- Armour, tube and insulation data are defaults until replaced by the vendor data sheet.
- No proximity or armour loss and no dielectric loss in the power cable.
- Not a replacement for vendor design, qualification or review by a qualified engineer.

## Related NeqSim Functionality

- `neqsim.process.equipment.subsea.Umbilical`, `UmbilicalFluid`
- `neqsim.process.mechanicaldesign.subsea.UmbilicalMechanicalDesign`,
  `UmbilicalHydraulicsCalculator`, `UmbilicalElectricalCalculator`,
  `UmbilicalStructuralCalculator`
- `neqsim.process.mechanicaldesign.subsea.UmbilicalFunctionalDesign` - actuation time, optical budget,
  weight ratio and J-tube pull-in tension checks
- `neqsim.process.mechanicaldesign.subsea.SubseaCostEstimator#calculateUmbilicalCost` - cost
  estimate for the same umbilical

## References

- NeqSim repository: https://github.com/equinor/neqsim
- API Spec 17E / ISO 13628-5, Subsea umbilicals (public scope background).
- DNV-ST-F101, Submarine pipeline systems (collapse equation).
- IEC 60287-1-1, Electric cables: current rating, skin effect.
