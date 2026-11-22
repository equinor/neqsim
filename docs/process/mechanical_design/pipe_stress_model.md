---
title: "Pipe stress analysis (ASME B31.3) with PipeStressModel"
description: "3D beam finite-element pipe stress analysis in NeqSim: B31.3 sustained, expansion, occasional and pressure checks with support lift-off, Coulomb friction, bend flexibility and nozzle loads."
---

# Pipe stress analysis (ASME B31.3) with PipeStressModel

`neqsim.process.mechanicaldesign.piping.PipeStressModel` analyses a routed process pipe from
nozzle to nozzle. It builds a 3D beam finite-element model with six degrees of freedom per node.
It solves the ASME B31.3 load cases and reports stress ratios, support states, restraint loads,
nozzle loads and displacements.

Use it when a line is routed, sized and at a known temperature, and you need more than a span or
flexibility screen. It covers stress against code allowables, which supports lift off or slide,
and what loads reach equipment nozzles. The older `designstandards.PipingStressAnalysis` class
gives single-formula helpers only; `PipeStressModel` solves the whole line.

## Model

| Item | Treatment |
| --- | --- |
| Elements | Euler-Bernoulli 3D beams (axial, two bending planes, torsion) |
| Bends | Long-radius elbows (radius factor 1.5 OD by default) at every direction change. The B31.3 flexibility factor $k = 1.65/h$ applies to the bend tangent elements, and the stress intensification factor is $i = 0.9/h^{2/3}$, with $h = t R / r_m^2$ |
| Tees | Welding-tee SIF $0.9/h^{2/3}$ with $h = 3.1\,t/r_m$ at nodes joining three or more elements |
| Anchors | All six DOF fixed, or springs (`setAnchorStiffness`) for nozzle flexibility |
| Rest supports | One-way vertical support that can lift off; Coulomb friction in the horizontal plane with stick/slip |
| Guides, line stops | Rest support plus a bilateral lateral (guide) or axial (line stop) stop |
| Weight | Pipe steel, contents and insulation per metre |
| Thermal | Free strain $\alpha\,(T_{op} - T_{inst})$ applied as an equivalent axial load $E A \alpha \Delta T$ |

## Load cases and checks

| Case | Loads | Supports | Check |
| --- | --- | --- | --- |
| OPE | Weight, thermal, pressure | Nonlinear: lift-off and friction iterated | Restraint and nozzle loads, displacements |
| SUS | Weight, pressure | Contact states from OPE, no friction; corroded wall | $S_L = \sqrt{(\lvert S_a\rvert + S_b)^2 + 4 S_t^2} \le S_h$ (B31.3 320) |
| EXP | OPE − SUS | — | $S_E = \sqrt{S_b^2 + 4 S_t^2} \le S_A = f(1.25 S_c + 1.25 S_h - S_L)$ (319.4.4, liberal Eq. 1b) |
| OCC | Horizontal seismic (g) and wind in x and in y | SUS states | $S_L + S_{occ} \le 1.33\,S_h$ (302.3.6) |
| Pressure design | Design pressure | — | $t_{req} = P D / (2 (S E W + P Y)) + c$ with $Y = 0.4$ and $E = W = 1$ (seamless pipe) (304.1.2) |

The operating case is solved with an active-set iteration: one support at a time is released when
it carries tension, or closed again when the pipe penetrates it. After that, sliding supports carry
$\mu N$ opposite to their movement, relaxed towards the new value, until the friction forces settle.
`Results.isConverged()` reports whether the iteration settled.

## Example

```java
PipeStressModel model = new PipeStressModel("20-PG-0101");
model.setPipe(0.2731, 0.0151, 0.003);              // OD, wall, corrosion allowance [m]
model.setMaterial(203.0e9, 0.3, 7850.0, 12.0e-6);  // E [Pa], Poisson, density, alpha [1/K]
model.setAllowables(138.0e6, 138.0e6, 1.0);        // S_h, S_c [Pa], range factor f
model.setContents(60.0, 0.0);                      // fluid density, insulation [N/m]
model.setConditions(95.0e5, 20.0, 140.0);          // design pressure [Pa g], T install, T operating [C]
model.setOccasionalLoads(0.1, 0.0, 0.7);           // seismic g, wind pressure [Pa], drag coefficient
model.addNode("N1", 0.0, 0.0, 0.0);
model.addNode("N2", 6.0, 0.0, 0.0);
model.addNode("N3", 12.0, 0.0, 0.0);
model.addNode("N4", 12.0, 8.0, 0.0);
model.addElement("N1", "N2");
model.addElement("N2", "N3");
model.addElement("N3", "N4");
PipeStressModel.Restraint nozzle =
    model.addRestraint("A1", "N1", PipeStressModel.RestraintType.ANCHOR, 0.0);
model.setNozzleAllowables(nozzle, 15000.0, 12000.0);  // resultant force [N] and moment [Nm]
model.addRestraint("A2", "N4", PipeStressModel.RestraintType.ANCHOR, 0.0);
model.addRestraint("S1", "N2", PipeStressModel.RestraintType.REST, 0.3);
model.addRestraint("S2", "N3", PipeStressModel.RestraintType.REST, 0.3);
PipeStressModel.Results res = model.run();

res.getStatus();                  // "pass" or "fail"
res.getMaxSustainedRatio();       // S_L / S_h
res.getMaxExpansionRatio();       // S_E / S_A
res.getLiftOff();                 // supports that lifted off in operation
res.getRestraint("A1").getNozzleStatus();
res.toJson();                     // full result for reports
```

This example runs in `PipeStressModelTest.documentationExample`.

## Validation

`PipeStressModelTest` checks the solver against closed-form results:

- a fixed-fixed beam under uniform weight (end moment $wL^2/12$, midspan deflection $wL^4/384EI$);
- the axial force $E A \alpha \Delta T$ of a straight pipe between anchors;
- free thermal growth $\alpha \Delta T L$ of a pipe with one free end;
- the friction force $\mu \sum N$ that reaches the anchor of a sliding line;
- lift-off of the support at the top of a hot riser;
- the B31.3 bend factors of an 8-inch standard elbow ($i = 2.32$, $k = 6.83$);
- the pressure design thickness and nozzle-load check;
- rejection of an unanchored, unstable model.

## Limitations

- Static, linear-elastic beams with small displacements. No response-spectrum, time-history or
  slug/surge dynamics; occasional loads are static equivalents.
- Mean thermal expansion coefficient and a single elastic modulus. There is no creep and no
  fatigue cycle spectrum: set the range factor $f$ for more than 7000 cycles.
- Nozzle loads are checked only against the allowables you supply (vendor data or API 610, API 617
  or WRC 297 results). Without allowables they are reported, not judged.
- Bends are modelled as flexible tangent elements, not curved elements. Branch connections use the
  welding-tee SIF only.
- This is a design-screening analysis. Critical lines still need a formal stress analysis by a
  qualified engineer against the project piping specification.

## Related

- [Mechanical design overview](../mechanical_design.md)
- The community `neqsim-asset-scene` skill builds a `PipeStressModel` for every routed line in a 3D
  scene (`analyse_pipe_stress`) and puts the results into the report.
