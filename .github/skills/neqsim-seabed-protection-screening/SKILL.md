---
name: neqsim-seabed-protection-screening
calculation_basis: "hybrid"
version: "0.1.0"
description: "Educational subsea pipeline trenching, burial and rock-dump screening driven by route and map-layer data: samples seabed substrate, fishing intensity, ice scour and no-rock zones along a route, finds crossings of existing pipelines, selects a trenching method or rock dump per segment, sizes trench depth, rock volume and tonnage, crossing and free-span supports, indicative durations and a burial hand-off for NeqSim cooldown and thermal models. USE WHEN: a flow-assurance, SURF or tie-back study needs burial, trenching or rock-dump scope, cover for hazards, thermal effect of burial, or rock quantities before DNV-RP-F111, F110, F105, F109 or F114 assessments and a contractor estimate."
last_verified: "2026-11-06"
requires:
  python_packages: []
  java_packages: []
  env: []
  network: []
---

# Seabed Protection Screening: Trenching, Burial and Rock Dump

A flowline or export pipeline is rarely just laid on the seabed. Where trawl gear,
anchors, dropped objects, ice keels or upheaval buckling threaten it, the line is
trenched, buried, rock-covered or supported, and that decision moves three things
the rest of a study depends on: the schedule (trenching rate, not lay rate, often
sets the offshore duration), the cost and rock tonnage, and the **thermal
behaviour** of the line - a buried line cools down much more slowly than an exposed
one, so hydrate margin and no-touch time change with the burial state.

This skill makes that scope visible early. It samples public or project map layers
along the route, assigns a hazard-driven cover requirement per segment, decides
whether the soil allows trenching or forces a rock dump, sizes the rock, and hands
the resulting burial state to NeqSim thermal and cooldown models. Every default is
a recorded placeholder, and every missing input is a reported data gap.

It is a screening tool. It does not perform a trawl interference, upheaval
buckling, free-span, on-bottom stability or pipe-soil assessment; those are the
downstream standards listed under References.

## When to Use

- A tie-back, export line or SURF study needs a first estimate of trenching and
  rock-dump scope along a route.
- A map layer (seabed substrate, vessel or fishing density, ice scour, protected
  area, existing pipelines) should decide where protection is needed.
- Crossings of existing pipelines and cables need rock quantities.
- Free-span candidates from survey processing need support quantities.
- A cooldown or hydrate-margin study must reflect which parts of the line are
  buried, rock-covered or exposed.
- Two routes or burial philosophies (trench everywhere versus rock only at
  crossings) are compared on trench length, rock tonnes and days.

## When *Not* to Use

- As a trawl-gear interaction, upheaval-buckling, free-span, stability or
  pipe-soil assessment. It supplies geometry and quantities, not code checks.
- To select a trenching contractor or tool. Capability and rates are placeholders.
- As a geotechnical interpretation. Surface substrate classes are not a strength
  profile and not a trenchability statement.
- For pre-lay route survey, boulder clearance or shore-approach design.
- On confidential layers in a public workspace. Inputs must be public, synthetic
  or approved for open-source use; enterprise map access belongs to the enterprise
  workflow.

## Inputs

| Input | Meaning |
|---|---|
| `build_route(lonlat, spacing_m)` | Route vertices in WGS84 degrees, densified into KP stations |
| polygon layers (GeoJSON) | Substrate, fishing or vessel intensity, ice scour, no-rock zones, geotechnical zones |
| line layers (GeoJSON) | Existing pipelines and cables, with optional outer diameter |
| `PipeBasis.outer_diameter_m` | Pipe outer diameter including coating |
| segment attributes | `fishing_intensity`, `soil_class`, `undrained_shear_strength_kpa`, `ice_scour_depth_m`, `anchor_penetration_m`, `cover_required_m`, `dropped_object_cover_m`, `stabilisation_cover_m`, `uplift_force_n_per_m`, `no_rock` |
| `existing_cover_intervals` | Surveyed burial intervals from `neqsim-pipeline-survey-processing` |
| `span_candidates`, `allowable_span_m` | Free-span candidates and the allowable span from a DNV-RP-F105 assessment |
| `ScreeningAssumptions` | Cover per fishing intensity, trencher envelopes and rates, rock geometry and density, spillage, rock placement rate, conductivities |

## Outputs

| Output | Contents |
|---|---|
| `ProtectionPlan.segments` | Per segment: controlling hazard, required cover, existing cover, strategy, trench method and depth, rock thickness, volume and tonnes, trench days, thermal condition and resistance |
| `strategy` | `none`, `adequate_existing_cover`, `trench`, `trench_and_rock` or `rock_dump` |
| `crossings` | Rock berm height, length, volume and tonnes per crossing |
| `span_supports` | Number of supports and rock per free-span candidate |
| roll-ups | Trench length by method, rock-dump length, rock volume and tonnes, trench days, rock days, buried length fraction, exposed length |
| `assumptions`, `data_gaps`, `warnings` | Every placeholder used, missing evidence, and restrictions such as rock needed in a no-rock zone |
| `to_neqsim_burial_segments` | Merged route sections with `buried`, burial depth to pipe centre and soil conductivity for a NeqSim pipe model |
| `plan_protection_data_package` | Read-only request plan for public map services (no network access by the module) |

## Engineering Method

**Map layers to segments.** The route is densified into stations with chainage.
Polygon layers are sampled at every station by point-in-polygon with holes honoured.
Where polygons overlap, the value ranked highest in `rank_order` wins, so a high
fishing-intensity polygon is never hidden by a low one. Stations with identical
attributes are merged into segments with boundaries midway between stations, so a
polygon edge is located to within half a station spacing. Line layers are
intersected with the route to give crossing KP, angle and crossed outer diameter.
The geometry is a local equirectangular frame - adequate for screening, not a
positioning or datum authority.

**Required cover.** Burial hazards (trawl by fishing-intensity class, ice scour
depth plus a margin, anchor penetration, a project cover requirement) each give a
cover above the pipe top that any covering medium can satisfy; the largest one
controls and is named in the result. Rock-only hazards - dropped-object rock cover,
on-bottom stabilisation cover and **upheaval buckling** - can only be met by rock
weight and override burial. The uplift case solves the berm geometry for the rock
above the pipe top whose submerged weight resists the supplied uplift force times
the factor of safety: with crest width `w`, side slope `n` and pipe diameter `D`,
the berm height `H` satisfies `n H^2 + w H = (w + n D) D + F*SF / gamma'`, and the
rock cover is `H - D`.

**Trenchability.** Soil is normalised to a class (soft, firm, stiff or very stiff
clay, sand, gravel or coarse and mixed sediment, rock). Each method in the
preference order must pass a class check - clay against the undrained shear strength,
sand and gravel against flags - and is then compared with the trench depth
`D + cover + allowance`. A method that reaches the depth gives **trench**. If the
best capable method falls short, the trench is taken to its limit and rock tops up
the remainder (**trench and rock**). Rock substrate, an unrecognised soil class, or no
capable method gives **rock dump**. A placeholder strength is used for clays with no
measured strength and is reported as a data gap.

**Existing cover.** Surveyed burial intervals count only where they cover a segment
end to end; any gap makes the segment's existing cover zero, because the
unburied stretch is where the hazard acts.

**Rock quantities.** Rock sits in a trapezoidal mound of crest width and side slope.
For a surface-laid pipe the area is `(w + nH)H` less the pipe section with
`H = D + cover`; for a trenched pipe the mound covers only what the trench does not
and the trench void is filled when rock replaces natural backfill. Volume is area
times length; tonnes apply the placed bulk density `(1 - porosity) x solid density` and
a spillage factor. Rock vessel days appear only when a placement rate is supplied.

**Crossings and spans.** A crossing berm is built to the crossed outer diameter, a
separation, the pipe and its cover, over a berm length plus the slopes. Free-span
candidates longer than the allowable span get `ceil(length / allowable) - 1` supports
sized to the span gap.

**Thermal hand-off.** Each segment is `exposed`, `buried` or `rock_covered`. Buried
and rock-covered sections carry the depth to the pipe centre and a shape-factor
thermal resistance that matches `PipeSurroundingEnvironment.calcBuriedPipeThermalResistance`.
`to_neqsim_burial_segments` merges adjacent sections in the same state so that a
NeqSim model can be split into pipes with `setBuried`, `setBurialDepth` and
`setSoilConductivity`, while exposed sections keep the seawater film coefficient.
Run the cooldown or hydrate-margin check for the **protected** state, and for the
**unprotected** state to see how much of the margin the burial buys.

## Python Usage Pattern

```python
from seabed_protection_screening import (
    PipeBasis, ScreeningAssumptions, SeabedProtectionScreener, assemble_segments,
    build_route, find_line_crossings, plan_protection_data_package,
    read_geojson, sample_polygon_layer, to_neqsim_burial_segments,
)

route = build_route([(2.00, 60.00), (2.12, 60.01), (2.25, 60.00)], spacing_m=250.0)
requests = plan_protection_data_package(1.9, 59.9, 2.35, 60.1)   # fetch with your own read-only adapter

substrate = read_geojson("substrate.geojson")
fishing = read_geojson("fishing.geojson")
pipelines = read_geojson("pipelines.geojson")

soil = sample_polygon_layer(route, substrate, "substrate")
level = sample_polygon_layer(route, fishing, "level", default="none",
                             rank_order=["none", "low", "medium", "high"])
crossings = find_line_crossings(route, pipelines, outer_diameter_attribute="od_in",
                                outer_diameter_unit_m=0.0254)
segments = assemble_segments(route, {"soil_class": soil, "fishing_intensity": level})

screener = SeabedProtectionScreener(
    PipeBasis(0.42, "16in-flowline"),
    ScreeningAssumptions(rock_rate_tonnes_per_day=6000.0),   # contractor figure, not a default
)
plan = screener.assess(segments, crossings=crossings, existing_cover_intervals=[])

print(plan.rock_tonnes, plan.trench_days, plan.buried_length_fraction)
sections = to_neqsim_burial_segments(plan, 0.42)
# from neqsim import jneqsim   # one TwoFluidPipe / PipeBeggsAndBrills per section
# pipe.setBuried(True); pipe.setBurialDepth(section["burial_depth_m"])
# pipe.setSoilConductivity(section["soil_conductivity_w_mk"])
```

See `examples/basic_seabed_protection_screening.py` for a runnable version on
synthetic layers.

## Using Map Data and Other Tools

| Need | Source or tool |
|---|---|
| Seabed substrate | EMODnet Geology seabed substrate (WMS); project geotechnical zones for strength |
| Fishing and vessel exposure | EMODnet Human Activities vessel density (WFS); project fishing-effort data |
| Existing pipelines and cables | Norwegian Offshore Directorate FactMaps (ArcGIS REST); EMODnet Human Activities |
| Bathymetry and slope | EMODnet Bathymetry, with `neqsim-bathymetry-profile-screening` |
| As-built cover and spans | `neqsim-pipeline-survey-processing` (`buried_intervals`, `span_candidates`) |
| Route and layout | `neqsim-surf-field-layout-design`, `neqsim-pipe-route-profile` |
| Thermal consequence | `neqsim-surf-cooldown-screening`, `neqsim-hydrate-margin-check` |
| Code checks | NeqSim `DnvRpF105FreeSpanScreeningKernel`, `DnvRpF109OnBottomStabilityKernel`, `DnvRpF114PipeSoilInteractionScreeningKernel` |

Enterprise map services (internal GIS, subsea infrastructure registers, STID) are
read by the enterprise agents and supply the same GeoJSON layers to this skill.

## Validation Checklist

- Every `assumptions` entry is replaced with, or accepted against, the project
  design basis; none is quoted as a standard value.
- Fishing-intensity classes come from the project's trawl assessment, not from
  vessel density alone.
- Soil classes and strengths trace to geotechnical data; every placeholder-strength
  data gap is closed or carried into the report.
- Segment boundaries are checked against the layer edges at the station spacing used.
- Crossings are checked against the as-built crossing register, including cables.
- Rock-only requirements (dropped object, stabilisation, upheaval) come from their
  own assessments, with uplift force and factor of safety stated.
- Warnings about `no_rock` zones are resolved by rerouting, a different method or
  an agreed exception.
- Trench days and rock days use contractor rates; mobilisation, weather, pre-lay
  clearance and post-works survey are added separately.
- The burial hand-off is run in both protected and unprotected states and the
  hydrate and cooldown margin difference is reported.

## Common Mistakes

- Treating vessel density as a trawl frequency.
- Reading a surface substrate class as a trenchability or strength statement.
- Letting a lower-ranked overlapping polygon hide the more onerous one.
- Counting existing cover as adequate when the survey covers only part of a segment.
- Using one burial state for the whole line in a cooldown model.
- Assuming natural backfill restores full cover in a trench that was cut through
  stiff or granular soil without a backfill assessment.
- Quoting rock tonnes without the spillage factor, or as if they included
  mobilisation and surveys.
- Forgetting that rock-only hazards override burial, so trenching a line that needs
  an upheaval-buckling berm does not remove the rock.
- Reading the placeholder trencher rates as contractor capability.

## Limitations

- Screening only; it does not establish adequacy or compliance with any standard.
- Trencher capability, rates, fishing-intensity cover and rock geometry are
  placeholders; the module cannot know the project basis.
- No geotechnical modelling: trenchability is a class and strength check, with no
  soil layering, boulders, cobbles or trench stability.
- Natural backfill is assumed to restore the trench cover; self-burial, scour and
  backfill lag are not modelled.
- Rock cover for upheaval buckling is a force balance on the berm weight; the uplift
  force itself must come from an upheaval-buckling assessment.
- Rock cost is not computed; unit costs are project data.
- Geometry is a local planar projection with no datum transformation.
- The module performs no file or network access beyond reading GeoJSON the caller
  names; map requests are planned, never sent.

## References

- NeqSim: https://github.com/equinor/neqsim
- NeqSim Community Skills: https://github.com/equinor/neqsim-community-skills
- Companion skills: `neqsim-pipeline-survey-processing`, `neqsim-pipe-route-profile`,
  `neqsim-bathymetry-profile-screening`, `neqsim-surf-field-layout-design`,
  `neqsim-surf-cooldown-screening`, `neqsim-hydrate-margin-check`,
  `neqsim-subsea-layout-geometry`
- NeqSim thermal handoff: `neqsim.fluidmechanics.geometrydefinitions.surrounding.PipeSurroundingEnvironment`,
  `neqsim.process.equipment.pipeline.PipeLineInterface` (`setBuried`, `setBurialDepth`,
  `setSoilConductivity`)
- Public standards for the downstream assessments: DNV-RP-F111 (interference between
  trawl gear and pipelines), DNV-RP-F110 (global buckling of submarine pipelines),
  DNV-RP-F105 (free spanning pipelines), DNV-RP-F109 (on-bottom stability),
  DNV-RP-F114 (pipe-soil interaction), DNV-ST-F101 (submarine pipeline systems).
- Open map services: EMODnet Geology, EMODnet Human Activities, EMODnet Bathymetry,
  Norwegian Offshore Directorate FactMaps.
