---
name: neqsim-subsea-equipment-placement
calculation_basis: "screening"
version: "0.1.0"
description: "Constraint-based siting of subsea equipment and routes (drill centres, templates, manifolds, PLETs, tie-ins): screens and ranks sites against clearances, existing lines and crossings, exclusion zones, seabed slope, fishing exposure and well reach, and checks the route. USE WHEN: a tie-back or SURF study must decide where equipment goes, or defend a placement against map data, standards and installation constraints."
last_verified: "2026-11-08"
requires:
  python_packages: []
  java_packages: []
  env: []
  network: []
---

# Subsea Equipment Placement

Where a template, manifold, PLET or tie-in goes decides step-out, crossings, installation risk and well reach, and it is very expensive to change after the wells are drilled. This skill turns that decision into a **reproducible screening**: candidate sites and routes are checked against every constraint that map data and the design basis can express, ranked, and reported with the status of each limit.

It is the placement counterpart of `neqsim-surf-field-layout-design` (which creates geometry from well counts) and `neqsim-subsea-layout-geometry` (which measures an existing layout). Use it first, then hand the chosen sites and routes to `neqsim-pipe-route-profile`, `neqsim-seabed-protection-screening` and `neqsim-surf-design-study`.

## When to Use

- A tie-back or SURF study must place the drill centre, template, manifold, PLET or riser base, or compare tie-in points on a host or a nearby template.
- A candidate position must be defended against existing pipelines, cables, structures, safety and caution zones, seabed slope and depth, and fishing exposure.
- Well reach (horizontal reach, reach-to-TVD ratio) limits where a drill centre can stand relative to the reservoir targets.
- A route must be screened for crossings (number, angle, position), parallel clearance, zone entry, slope and straight lengths at the ends.

Do **not** use it for detailed route engineering, foundation or structural design, anti-collision, geohazard interpretation or installation analysis. Those are the hand-offs listed under Limitations.

## Inputs

All coordinates are local east/north metres about a stated origin (`to_local` converts WGS84; accurate below about 50 km).

| Input | Contents |
| --- | --- |
| `host` and `structures` | position, kind, and a clearance `Limit` per structure |
| `lines` | existing or planned pipelines, flowlines, umbilicals, cables as polylines with status and service |
| `zones` | polygons with type, active flag and `hard` (new equipment may not enter) |
| `targets` | reservoir targets: horizontal position and TVD below the seabed |
| `depth_fn` | water depth (positive down) as a function of x, y, from a bathymetry grid |
| `fishing_fn` | optional exposure measure from AIS or fisheries data |
| `limits` | a `Limit(value, unit, source, basis)` per criterion (below) |

Limit keys: `zone_clearance_m`, `structure_clearance_m`, `line_clearance_m`, `max_slope_deg`, `max_water_depth_m`, `max_fishing_intensity`, `max_horizontal_reach_m`, `max_erd_ratio`, `max_stepout_m`, `max_route_length_m`, `max_crossings`, `min_crossing_angle_deg`, `min_straight_end_m`.

**No limit is built in.** A limit with `basis` APPROVED or SUPPLIED and a source gives PASS or FAIL. A limit that is ASSUMED, missing or without a source gives `NEEDS_REVIEW` with the indicative outcome, so a screening number is never read as compliance. Take limit values from the project design basis, the operator's requirements and the licensed standards (see Standards and where each rule comes from); this skill never invents them.

## Outputs

- `screen_site(...)`: one row per check (value, unit, limit, basis, source, status, indicative), a verdict (`PASS`, `NEEDS_REVIEW`, `FAIL`), `hard_fail` and `indicative_fail` lists.
- `screen_route(...)`: length, crossing table (line, status, chainage, angle), parallel clearance, hard-zone entry, slope, straight end lengths, verdict.
- `rank_sites(...)`: sites ordered by a declared weighted score (mean target reach, host step-out, crossings, penalty per indicative failure), hard failures last.
- `to_geojson(...)`: WGS84 points with verdicts for a GIS or map figure.

## Engineering Method

1. **Frame the decision.** Which equipment, which targets and which host or tie-in options. Record the coordinate frame, datum and the source and date of every map layer. Positions in public or commercial map layers are screening grade.
2. **Collect the constraint layers** (use the map skills for data): existing pipelines and cables with status, structures and their exclusion radii, active safety, exclusion, caution and contaminated-area polygons, bathymetry, seabed features, fishing density, planned developments. Treat "status unknown" as a gap, never as clear.
3. **Hard constraints first.** Exclude sites inside an active hard zone, closer than the supplied clearance to a structure or line, or beyond the supplied reach, slope or depth limit. Report each exclusion with its check ID.
4. **Generate candidates** (`candidate_grid` over the reservoir outline or the corridor) and **screen** them (`screen_site`). Keep the check table for every finalist, not only the winner.
5. **Rank** with `rank_sites`. The default weights are a declared assumption; show the ranking at two or three weight sets and report where the winner changes (robustness crossover). Prefer a site that stays first over a range of weights to one that wins only at one.
6. **Screen the route** from each finalist to its tie-in (`screen_route`): crossings (each crossing needs a design, and installation near existing lines needs a risk assessment), parallel runs, zone entry, slope, and straight lengths at both ends. A pipeline or umbilical end should have a straight section before the structure (see DNV-ST-F101 3.3.6.4); how long is a project limit.
7. **Cross-check against the siting considerations** below and record each as `OK`, `ACTION` or `GAP`.
8. **Hand off** the chosen coordinates, the check tables and the open gaps; the layout is not final until the survey, geotechnical and installation work confirm it.

### Siting considerations (checklist)

| Topic | What to confirm | Typical source of evidence |
| --- | --- | --- |
| Reach | Horizontal reach and reach-to-TVD ratio to every target, including the far end of laterals and the injector | reservoir model, well planning (`neqsim-erd-reach-screening`) |
| Host and safety zones | Distance to the host safety zone, to exclusion, caution and contaminated areas; no hard-zone entry except at the terminal | map zone layers, operator rules |
| Existing infrastructure | Clearance to structures and lines; crossing count, angle and type; spare capacity, slots and tie-in points on reused lines | map layers, infrastructure registers |
| Seabed | Slope within the footprint, depth range, boulders, pockmarks, sand waves, unstable slopes, gas seepage areas, soil strength for the foundation | bathymetry, route and site survey, geotechnical data |
| Third-party activity | Fishing and shipping density, anchoring, dropped-object exposure, need for overtrawlable or protective structures | AIS-based density maps, standards on loads |
| Installation | Lay direction and corridor, lay-down and start-up areas, crossing procedures, rig and anchor patterns for the drilling unit, vessel access, tolerances to existing equipment | installation requirements, constructability study |
| Operations | ROV access, intervention and retrieval envelopes, expansion and tie-in spool geometry, future tie-ins and expansion slots | operations philosophy |
| Interfaces | Well-to-manifold tolerances (thermal growth, position, orientation, subsidence), pull-in loads on wellheads | system interface requirements |

### Standards and where each rule comes from

Read the licensed text before citing a clause. Only the clause topic is recorded here; limit values belong in the project or company layer.

| Topic | Source clause |
| --- | --- |
| Route selection considers facilities, third-party activity, expected future operations; in-line assemblies avoid curved sections; straight length ahead of target boxes | DNV-ST-F101 3.3.6 |
| Route survey: whole route, sufficient corridor width, conflicts with installations, obstructions, slopes, sand waves, pockmarks; geohazard studies where indicated | DNV-ST-F101 3.3.7 |
| Seabed properties for response and stability | DNV-ST-F101 3.3.8, DNV-RP-F114 (pipe-soil) |
| Pipelay in congested areas, near installations, at crossings: positioning accuracy, ROV monitoring, touchdown monitoring | DNV-ST-F101 10.6.1.12 to 10.6.1.14 |
| Dropped-object and fishing-gear loads: field-specific early-phase assessment; loads from the project basis or NORSOK U-001 | ISO 13628-1 6.7; NORSOK U-001 5.3.3 and 5.4 |
| Fishing activity densities from AIS for interaction probability and return period, reported as GIS density charts | NORSOK U-001 5.4.2 |
| Design for installation and constructability: lay direction, interference with other seabed equipment, structure type trade-offs | ISO 13628-1 6.11 |
| Well-to-template interface tolerances and pull-in loads | ISO 13628-1 6.5 |
| Foundations and seabed interface of structures; installation stages | NORSOK U-001 7.3 and 7.4 |
| Free span, on-bottom stability, global buckling | DNV-RP-F105, F109, F110 (hand-off) |

Company or operator requirements (installation tolerances, crossing practice, safety-zone rules, tie-in philosophy) take precedence and are added by the enterprise layer. Resolve the country first with `neqsim-jurisdiction-regulatory-basis`: safety-zone and approval rules differ by regime.

## Python Usage Pattern

```python
from subsea_equipment_placement import (
    Limit, Line, PlacementBasis, Structure, Target, Zone,
    candidate_grid, rank_sites, screen_route, to_local,
)

origin = (2.6117, 60.2893)                                   # lon, lat
xy = lambda lon, lat: to_local(lon, lat, *origin)
approved = lambda v, u="m": Limit(v, u, "project design basis rev A", "SUPPLIED")

basis = PlacementBasis(
    host=Structure("HOST", xy(2.7954, 60.3897), "host"),
    lines=[Line("Existing gas export", [xy(2.69, 60.36), xy(2.74, 60.42)], "pipeline", "existing", "gas")],
    zones=[Zone("HOST-500m", [xy(2.78, 60.385), xy(2.79, 60.385), xy(2.79, 60.395), xy(2.78, 60.395)], "Safety Zone")],
    targets=[Target("T1", (-1800.0, 0.0), 3100.0), Target("T2", (1800.0, 0.0), 3100.0)],
    depth_fn=lambda x, y: 100.0,                             # replace with a bathymetry interpolator
    limits={"zone_clearance_m": approved(50.0), "max_slope_deg": Limit(3.0, "deg", "", "ASSUMED")},
)
sites = candidate_grid((0.0, 0.0), 600.0, 100.0)
ranked = rank_sites(sites, basis, route_fn=lambda s: [s, basis.host.point])
best = ranked[0]
route = screen_route([best["site"], basis.host.point], basis, terminal_zone_ids=["HOST-500m"])
```

Verified by `tests/test_placement.py`.

### Limits file and gap register

Keep every limit in one document instead of in code. `limits_template()` (or `write_limits_template(path)`) lists all limit keys with unit, direction and a `NONE` basis; the design-basis owner fills in value, source and basis. `load_limits(path_or_dict)` validates it (known key, finite number, matching unit, basis in `APPROVED`/`SUPPLIED`/`ASSUMED`/`NONE`, and a source for any `APPROVED` or `SUPPLIED` value) and returns the `limits` mapping for `PlacementBasis`. `limits_gap_register(limits)` returns one row per limit, `CLOSED` or `OPEN`, with what normally closes each open one. Put the register in every report so an assumed screening value reads as a gap, not a requirement. A `min_water_depth_m` limit adds a `DEPTH_MIN` check next to `DEPTH`.

```python
from subsea_equipment_placement import limits_gap_register, load_limits, write_limits_template

write_limits_template("placement_limits.json")           # fill in, then:
basis.limits = load_limits("placement_limits.json")
open_gaps = [r for r in limits_gap_register(basis.limits) if r["status"] == "OPEN"]
```

Verified by `tests/test_limits.py`.

## Validation Checklist

- [ ] The coordinate frame, datum and the date and source of every map layer are recorded; map positions are marked screening grade.
- [ ] Every limit has a value, unit, source and basis; ASSUMED limits are listed as gaps and the results labelled NEEDS_REVIEW.
- [ ] Active zones, existing lines, structures and planned developments are all in the basis; missing status is a gap.
- [ ] The ranking was repeated at other weights and the winner's robustness stated.
- [ ] Each crossing is listed with line, status, chainage and angle and has a design or a hand-off.
- [ ] Route and site were checked against the siting checklist (every row `OK`, `ACTION` or `GAP`).
- [ ] Seabed slope came from a bathymetry grid of stated resolution, not from a depth contour or a single value.
- [ ] A specialist (survey, geotechnical, marine operations) has reviewed the final position.

## Common Mistakes

- Treating a distance to an existing line as a clearance when the line's true position is uncertain: map layers and some survey data are shifted; add the position uncertainty to the limit or confirm with the owner.
- Reading `NEEDS_REVIEW` with an indicative PASS as compliant.
- Ranking on step-out alone: a short route that crosses a major export line may cost more than a longer clear one.
- Placing at the reservoir centroid without checking reach to the far target, the injector and the laterals.
- Using a coarse depth grid for slope: a 250 m grid cannot show a 100 m footprint slope. State the resolution.
- Forgetting future tie-ins, the rig approach and anchor corridors, and the lay-down area for the pipelay start-up.

## Limitations

Screening geometry on straight segments in a local frame: no obstacle-avoiding routing, no geohazard interpretation, no foundation, structural, dropped-object or trawl-load calculation, no installation or vessel analysis, no anti-collision, no probabilistic fishing or ship-impact assessment. Map data may be restricted, incomplete or shifted. The ranking weights are declared assumptions. Hand-offs: `neqsim-seabed-protection-screening` (burial, rock, crossings design scope), `neqsim-pipe-route-profile` (profiles), `neqsim-erd-reach-screening` and `neqsim-well-trajectory-anticollision` (wells), `neqsim-surf-design-study` (hydraulics and design checks), specialists for geotechnical, geohazard, installation and fishing-interaction work.

## Related NeqSim Functionality

Skills: `neqsim-surf-field-layout-design`, `neqsim-subsea-layout-geometry`, `neqsim-seabed-protection-screening`, `neqsim-pipe-route-profile`, `neqsim-bathymetry-profile-screening`, `neqsim-field-layout-import`, `neqsim-subsea-slot-tee-reuse-inventory`, `neqsim-step-out-screening`, `neqsim-tie-in-pipeline-concept`, `neqsim-erd-reach-screening`, `neqsim-jurisdiction-regulatory-basis`, `neqsim-surf-design-study`.
Agents: `subsea-equipment-placement-agent` (coordinator), `surf-layout-design-agent`, `subsea-layout-screening-agent`, `subsea-infrastructure-capacity-agent`, `export-tie-in-concept-agent`.

## Human Review Requirements

A subsea engineer, a marine survey or geotechnical specialist and the operator's installation and operations leads confirm the position, the clearances and the crossings before any commitment. Map positions are for information.
