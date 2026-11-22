---
name: neqsim-standard-equipment-sizes
calculation_basis: "screening"
version: "0.1.0"
description: "Rounds calculated equipment sizes up to standard industry sizes - pipe NPS/DN, API 526 relief-valve orifices, ASME B16.5 flange classes, typical IEC motor ratings, exchanger tube lengths, vessel diameter steps - and defines the size-source precedence of project/company datasheet, vendor, then public standard. USE WHEN: a design or study produces a calculated diameter, area, power or rating that must be reported as a buildable standard size, or a report quotes an equipment size."
last_verified: "2026-11-07"
requires:
  python_packages: []
  java_packages: []
  env: []
  network: []
---

# Standard Equipment Sizes

A simulation returns a required diameter, area or power. Equipment is bought in standard sizes. This skill rounds a requirement up to a standard size, reports the margin, and records where the size came from, so a report never presents an unbuildable size as a design.

## When to Use

- A calculation gives a required line size, relief orifice area, motor power, flange pressure class, tube length or vessel diameter.
- A report or P&ID quotes equipment sizes.
- Company or vendor sizes are needed: use the size-source precedence below and the enterprise overlay.

## Inputs

- Required inside diameter (mm) with an assumed wall thickness (mm), required relief area (mm2), shaft power (kW) with margin, design pressure (barg), tube length (m), vessel inside diameter (mm) with fabrication step.

## Outputs

- Selected standard size, the calculated requirement, the margin to the requirement and a `size_basis` label: `company-standard`, `vendor`, `public-standard`, `typical-practice` or `assumed`.

## Engineering Method

1. **Source precedence.** Use (1) the project or company datasheet, line list and piping class, (2) vendor data for the selected package, (3) public standard tables from this skill, (4) typical practice, flagged as assumed. A higher source overrides a lower one; record which was used.
2. **Round up, never down.** Pick the smallest standard size that meets the requirement, then check the consequence: pressure drop, velocity and erosion for pipe, rated capacity and certified coefficient for relief valves, driver margin and starting for motors.
3. **Pipe.** `select_pipe_nps` uses ASME B36.10M outside diameters with an assumed wall. Process specifications often avoid NPS 1 1/4, 2 1/2, 3 1/2 and 5; use the project line-size list. Report NPS and DN together. Wall thickness comes from the piping class and corrosion allowance, not from this skill.
4. **Relief valves.** `select_api526_orifice` maps the required effective area to the API 526 letter D to T. An area above T needs several valves. The letter is not certified capacity: check the vendor certified coefficient and the sizing case.
5. **Flanges.** `lowest_b165_class_ambient` is an ambient group 1.1 carbon steel screen (classes 150 to 2500). Ratings fall with temperature and change with material group; use the ASME B16.5 table at design temperature, and B16.47 above NPS 24.
6. **Motors.** `next_motor_kw` rounds to a typical IEC series after a stated margin; confirm the project or vendor series and the frame.
7. **Exchangers and vessels.** Tube lengths follow the TEMA-style 8, 10, 12, 16 and 20 ft series (2.44 to 6.10 m); tube outside diameters 19.05 and 25.4 mm are common. Vessel diameter steps are a fabricator convention: pass the step from the specification.
8. **Local rules.** Jurisdiction can restrict sizes or add requirements (for example inspection access, pressure-equipment conformity). Load `neqsim-jurisdiction-regulatory-basis` and apply its regulation layer before the size is final.

## Python Usage Pattern

```python
from standard_equipment_sizes import select_pipe_nps, select_api526_orifice, next_motor_kw

line = select_pipe_nps(required_inside_diameter_mm=150.0, wall_thickness_mm=7.1)
psv = select_api526_orifice(required_area_mm2=1200.0)
motor_kw = next_motor_kw(required_shaft_kw=212.0, margin_fraction=0.10)
```

## Validation Checklist

- [ ] Every reported size is a standard size and carries a `size_basis`.
- [ ] Company or project sizes override public tables where they exist.
- [ ] Rounded size re-checked in the simulation (velocity, pressure drop, capacity, driver power).
- [ ] Relief letter backed by vendor certified capacity, not only the area grid.
- [ ] Flange class checked at design temperature and material group, not only ambient.

## Common Mistakes

| Symptom | Cause | Fix |
| --- | --- | --- |
| Report shows a 143 mm line | Calculated diameter reported as size | Round up to NPS/DN and recheck velocity |
| Relief valve letter treated as capacity | Area grid confused with certified rating | Use vendor certified coefficient |
| Class 150 selected at 200 C | Ambient screen used for design temperature | Use the B16.5 table at temperature |
| Public size used where company list exists | Source precedence skipped | Take the company line-size list and piping class |

## Limitations

- Tabulated for common sizes only; wall-thickness schedules, B16.5 temperature derating, TEMA shell sizes, tank sizes and vendor catalogues are not included.
- Motor series, tube lengths and vessel steps are typical practice, not a standard clause.
- Company and vendor sizes are not part of this public skill.

## Related NeqSim Functionality

- Java relief orifice grid: `neqsim.process.engineering.calculation.Api526OrificeSelectionKernel` and `neqsim.process.util.fire.ReliefValveSizing`.
- Mechanical design and line data: `neqsim.process.mechanicaldesign.pipeline.TopsidePipingMechanicalDesign`, `neqsim.process.engineering.LineDesignInput`, separator sizing through `SeparatorMechanicalDesign`.
- Used by `neqsim-relief-flare-network`, `neqsim-process-safety` and equipment cost estimation. This skill only rounds results; the physics stays in NeqSim.

## References

- ASME B36.10M / B36.19M welded and seamless pipe, ASME B16.5 and B16.47 flanges.
- API 526 flanged steel pressure-relief valves, API 520 and API 521.
- EN 10220 and ISO 6708 for DN series, IEC 60072-1 for motor ratings, TEMA standards for exchangers.
