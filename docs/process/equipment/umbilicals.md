---
title: Umbilicals
description: Subsea umbilical in NeqSim - cross-section and weights, tube burst and collapse, armour and catenary tension, chemical and hydraulic line hydraulics, power cable, design checks, and the figure and hand-off skills.
---

An umbilical is a bundle of hydraulic tubes, chemical injection tubes, electrical cables and
optical fibres inside armour and sheaths. NeqSim sizes and checks it from the element list, the
route length and the water depth.

## Table of Contents
- [Classes](#classes)
- [Quick start](#quick-start)
- [Physics](#physics)
- [Design checks](#design-checks)
- [Results as JSON](#results-as-json)
- [Figures and hand-off](#figures-and-hand-off)
- [Defaults and limitations](#defaults-and-limitations)
- [Design standards](#design-standards)
- [Related documentation](#related-documentation)

---

## Classes

**Location:** `neqsim.process.equipment.subsea` and `neqsim.process.mechanicaldesign.subsea`

| Class | Description |
|-------|-------------|
| `Umbilical` | Equipment: element list, geometry, weights, line hydraulics, power cable |
| `UmbilicalFluid` | Density, temperature-dependent viscosity and bulk modulus of a line fluid; presets or `fromNeqSim(...)` |
| `UmbilicalMechanicalDesign` | Tube wall, armour layers, catenary tension, design checks, JSON |
| `UmbilicalHydraulicsCalculator` | Friction pressure drop, hydrostatic head, flow for a pressure budget, pressure-step response |
| `UmbilicalElectricalCalculator` | Conductor resistance, skin effect, three-phase cable as a distributed line, optical budget |
| `UmbilicalStructuralCalculator` | Burst and collapse wall, armour capacity, catenary, element packing |
| `UmbilicalFunctionalDesign` | Static functional checks: actuation time, optical budget, weight ratio, J-tube pull-in |

---

## Quick start

```java
import neqsim.process.equipment.subsea.Umbilical;
import neqsim.process.mechanicaldesign.subsea.UmbilicalMechanicalDesign;

Umbilical umbilical = new Umbilical("Control umbilical");
umbilical.setLength(15000.0); // m
umbilical.setWaterDepth(100.0); // m

umbilical.addHydraulicLine("LP hydraulic supply", 9.5, 345.0); // ID mm, design pressure bar
umbilical.addHydraulicLine("HP hydraulic supply", 6.35, 690.0);
umbilical.addChemicalLine("MEG", 25.4, 345.0);
umbilical.addElectricalCable("Power", 3, 3300.0, 16.0); // cores, V, conductor mm2
umbilical.addFiberOptic("Fibre", 12);

umbilical.setInjectionRate("MEG", 0.5, "m3/hr");
umbilical.setPowerLoad(0.05); // MW at the subsea end

double megDropBar = umbilical.calculatePressureDrop("MEG");
double response90 = umbilical.calculateLineResponse("LP hydraulic supply", 0.9).getTimeToFractionS();
double voltageDropPercent = umbilical.calculateVoltageDrop();

UmbilicalMechanicalDesign design = umbilical.getMechanicalDesign();
design.calcDesign();
boolean acceptable = design.isDesignAcceptable();
String json = design.toJson();
```

Element lines get a fluid from their name (water-glycol for hydraulic lines, MEG, methanol, or a
generic chemical). Use `addChemicalLine(name, id, pressure, UmbilicalFluid)` to set another one, for
example `UmbilicalFluid.fromNeqSim("Lean MEG", system, 277.15, 200.0)` from a flashed NeqSim fluid.

---

## Physics

| Topic | Method |
|-------|--------|
| Tube wall, internal pressure | `t = P ID / (2 (S + P Y - P))` with `S = 0.6 SMYS`, `Y = 0.4` |
| Tube wall, external pressure | DNV-ST-F101 collapse `(pc - pel)(pc^2 - pp^2) = pc pel pp f0 D/t`, ovality 0.5 %, safety factor 1.5, empty tube at hydrostatic pressure |
| Cross-section | Element outer diameters packed with a circle-packing efficiency, inner sheath, helical armour wires (`n = pi D cos(a) / d_w`), outer sheath |
| Weights | Per element from material density and fluid fill; weight in air (empty and filled) and in seawater (filled minus displaced seawater) |
| Line friction | Darcy-Weisbach: `64/Re` laminar, Haaland turbulent, linear blend 2300 to 4000 |
| Hydrostatic head | `rho g h` with the water depth as the elevation difference between topside and seabed |
| Line response | Distributed resistance-compliance line with `R = 128 mu L / (pi D^4)` and the compliance of fluid and tube wall; time constant `4 R C / pi^2`; the closed-end step response is solved with the exact series; the acoustic transit time `L / c` is the lower bound |
| Power cable | Exact long-line two-port with `R_ac = R_dc (1 + Ys)` (IEC 60287-1-1), coaxial capacitance, trefoil reactance; gives sending voltage, loss, voltage drop, charging current and no-load rise |
| Catenary | Free hanging with horizontal tangent at touchdown: `T_top = w (a + h)`, bend radius at touchdown `a = H / w`, suspended length `sqrt(h^2 + 2 a h)` |
| Armour | Smallest layer count whose capacity (`n A_w sigma cos(alpha)` per layer) covers the amplified top tension including the weight of those layers |

Typical consequences: cold MEG is 40 to 50 mPa s at 4 C, so a chemical line over tens of kilometres
is laminar and its friction drop scales with `L / D^4`; hydraulic lines over 15 km need minutes, not
seconds, for the far end to follow a topside pressure step; a long power cable has a charging
current that can raise the receiving voltage at light load.

---

## Design checks

`UmbilicalMechanicalDesign.getDesignChecks()` returns pass or fail for:

| Check | Meaning |
|-------|---------|
| `selfSinking` | Submerged weight is positive |
| `tubeBurstUtilizationBelow1` | Hoop stress at design pressure within the allowable |
| `tubeCollapseUtilizationBelow1` | Collapse pressure over the safety factor covers hydrostatic pressure |
| `armorCapacityCoversDesignTension` | Armour capacity covers the amplified top tension |
| `touchdownBendRadiusAboveMBR` | Touchdown radius above the minimum bend radius |
| `installationTensionWithinVesselLimit` | Design tension within `setMaxInstallationTensionKN` |
| `lineFrictionDropWithinPressureBudget` | Friction drop at design flow below `setMaxFrictionDropFraction` (default 0.5) of the line design pressure |

---

## Results as JSON

`toJson()` contains the sections `configuration`, `elements`, `calculatedResults`, `elementResults`
(per element geometry, friction drop, response time), `powerTransmission`, `designChecks`,
`weight`, `costEstimation`, and the data that drawings and charts need: `crossSection`,
`hydraulicCurves`, `catenaryProfile` and `powerSweep`.

---

## Figures and hand-off

The community skill `neqsim-umbilical-design` (agent `umbilical-design-agent`) turns the JSON into
the cross-section drawing, the line hydraulics, tension and bend radius, power cable and design
check charts, an "umbilical on a page", captions, key numbers and a `HANDOFF.md` with the evidence
status of every input and the open items. The enterprise overlay `enterprise-umbilical-design`
adds STID and governing-document evidence and a policy verdict.

---

## Defaults and limitations

Screening defaults (replace by the vendor data sheet): super duplex tube with SMYS 550 MPa, usage
factor 0.6, collapse safety factor 1.5; armour wires 4 mm at 15 degrees with 1400 MPa tensile
strength and 40 % allowable; inner sheath 2 mm, outer sheath 5 mm; touchdown offset half the water
depth; tension amplification 1.3; conductor 90 C. Setters exist on `Umbilical`
(`setTubeDesignBasis`, `setArmorWireStrength`, `setArmorWireDiameterMm`, `setTouchdownOffsetM`,
`setTensionAmplificationFactor`, `setConductorTemperatureC`, ...).

Not included: dynamic riser analysis, fatigue, torsion balance, crush, installation and burial
design, proximity and armour loss and dielectric loss in the power cable, temperature variation
along the line. For a free-standing power cable model see `SubseaPowerCable`.

---

## Design standards

| Standard | Title |
|----------|-------|
| API Spec 17E / ISO 13628-5 | Subsea umbilicals |
| API RP 17E | Subsea umbilicals |
| DNV-ST-F101 | Submarine pipeline systems (collapse of steel tubes) |
| IEC 60287-1-1 | Electric cables: current rating, skin effect |

---

## Related documentation

- [Subsea Trees](subsea_trees) - Christmas tree modeling
- [Subsea Manifolds](subsea_manifolds) - Manifold modeling
- [Subsea Boosters](subsea_boosters) - Boosting systems
- [SURF design study](../../development/SURF_DESIGN_STUDY) - umbilical in the SURF design workflow
