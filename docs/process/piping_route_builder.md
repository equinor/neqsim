---
title: Route-Level Piping Hydraulic Builder
description: Build route-level pipe hydraulic models from STID, E3D, P&ID, or stress-isometric line-list tables using PipingRouteBuilder and PipeBeggsAndBrills.
---

Use `PipingRouteBuilder` when a source document describes a piping route as a
line-list or route table: line numbers, from/to nodes, nominal size, wall
thickness, straight length, fittings, valves, elevations, and equipment nodes.
The builder converts those rows into a serial `ProcessSystem` with one
`PipeBeggsAndBrills` unit per segment and explicit material connection metadata.

For STID/E3D work, this is the preferred bridge between document extraction and
NeqSim hydraulics whenever the task is route pressure drop, upstream/downstream
piping screening, debottlenecking, or compressor suction/discharge line analysis.

## When To Use

Use this builder when the route is mostly serial and the extracted data includes
segment-by-segment geometry.

| Source data | Builder field |
|-------------|---------------|
| Line number, row id, or stress-isometric segment | `segmentId` |
| Upstream equipment/node/nozzle | `fromNode` |
| Downstream equipment/node/nozzle | `toNode` |
| Straight run length | `length`, `lengthUnit` |
| Nominal bore or calculated internal diameter | `nominalDiameter`, `diameterUnit` |
| Schedule/wall thickness | `setSegmentWallThickness(...)` |
| Elevation rise/drop | `setSegmentElevationChange(...)` |
| Pipe roughness or piping class assumption | `setSegmentPipeWallRoughness(...)` or default roughness |
| Valve, bend, tee, strainer, reducer K value | `addMinorLoss(...)` |

For branched or looped networks, use the route builder for each serial branch or
use the looped-network tools described in [Looped Pipeline Networks](equipment/looped_networks.md).

## What It Builds

For a standalone route pressure-drop study, calling `build(inletStream)` creates:

1. A `ProcessSystem` named `Piping route`.
2. The provided inlet stream as the first process unit.
3. One `PipeBeggsAndBrills` unit per route segment, in line-list order.
4. `ProcessConnection` entries between the inlet stream and each generated pipe.
5. Fittings on each pipe using equivalent length ratios.

For a route embedded in a larger process simulation, call
`addToProcessSystem(process, inletStream)`. This adds only the generated pipe
units to the existing process and returns the outlet stream from the last pipe.
Pass that returned stream directly to downstream equipment constructors.

Minor losses are stored as K values on the route segment, then converted to
equivalent length ratio using:

$$
\frac{L_e}{D} = \frac{K}{f_D}
$$

where $f_D$ is the Darcy friction factor assumption. The default is `0.02` and
can be changed with `setMinorLossFrictionFactor(...)`. This is a screening
assumption; record the source of K values and the selected friction factor in
the task notes and `results.json`.

## Java Example

The example below is exercised by
`neqsim.process.equipment.pipeline.routing.PipingRouteBuilderTest`.

```java
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.pipeline.routing.PipingRouteBuilder;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

SystemInterface gas = new SystemSrkEos(303.15, 50.0);
gas.addComponent("methane", 0.9);
gas.addComponent("ethane", 0.1);
gas.setMixingRule("classic");

Stream feed = new Stream("feed", gas);
feed.setFlowRate(10000.0, "kg/hr");
feed.setTemperature(30.0, "C");
feed.setPressure(50.0, "bara");
feed.run();

PipingRouteBuilder route = new PipingRouteBuilder()
    .setDefaultPipeWallRoughness(45.0, "micrometer")
    .setMinorLossFrictionFactor(0.02)
    .addSegment("ROUTE-001-S1", "Upstream manifold", "separator outlet", 120.0, "m", 0.508,
        "m")
    .setSegmentWallThickness("ROUTE-001-S1", 12.7, "mm")
    .addMinorLoss("ROUTE-001-S1", "gate valve", 0.15)
    .addSegment("ROUTE-001-S2", "separator outlet", "compressor scrubber", 65.0, "m", 0.508,
        "m")
    .setSegmentElevationChange("ROUTE-001-S2", 4.0, "m")
    .addMinorLoss("separator outlet->compressor scrubber", "long-radius bend", 0.20);

ProcessSystem process = route.build(feed);
process.run();

PipeBeggsAndBrills lastPipe = (PipeBeggsAndBrills) process
    .getUnit("Pipe ROUTE-001-S2 separator outlet to compressor scrubber");
double outletPressure = lastPipe.getOutletStream().getPressure("bara");
String routeJson = route.toJson();
```

## Use Inside A Larger ProcessSystem

`PipingRouteBuilder` accepts any `StreamInterface` as its route inlet. That can
be a feed stream or the outlet stream from upstream equipment. The returned
stream can feed the next unit operation in the same `ProcessSystem`.

```java
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.pipeline.routing.PipingRouteBuilder;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessConnection;
import neqsim.process.processmodel.ProcessSystem;

ProcessSystem process = new ProcessSystem("Full plant process");
// feed is an existing StreamInterface or upstream feed stream in the process.
process.add(feed);

PipingRouteBuilder route = new PipingRouteBuilder()
    .addSegment("S1", "Feed", "Route outlet", 50.0, "m", 0.20, "m")
    .addMinorLoss("S1", "check valve", 0.5);

StreamInterface routeOutlet = route.addToProcessSystem(process, feed);

Cooler downstreamCooler = new Cooler("Downstream cooler", routeOutlet);
downstreamCooler.setOutletTemperature(25.0, "C");
process.add(downstreamCooler);
process.connect(route.getSegment("S1").getPipeName(), "outlet", downstreamCooler.getName(),
    "inlet", ProcessConnection.ConnectionType.MATERIAL);

process.run();
```

When the inlet stream comes from upstream equipment, use the explicit metadata
overload so the process connection list retains the true source tag and port:

```java
StreamInterface routeOutlet = route.addToProcessSystem(
  process, hpSeparator.getGasOutStream(), "HP Separator", "gasOut");
```

The stream object performs the simulation wiring. The `ProcessConnection`
records are topology metadata for exports, graph inspection, DEXPI-style
handoffs, and agent traceability.

## Direct Line-List And CSV Import

Use `fromLineListRows(...)`, `addLineListRows(...)`, or `addLineListRow(...)`
when a STID/E3D/P&ID extraction already has one map per route row. Column names
are normalized, so common spreadsheet headers such as `Line Number`, `From Node`,
`Length [m]`, `Internal diameter [mm]`, `Wall thickness [mm]`, `NPS`, and
`Minor losses` can be used directly.

```java
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.equipment.pipeline.routing.PipingRouteBuilder;

Map<String, String> row = new LinkedHashMap<String, String>();
row.put("Line Number", "ROUTE-010-S1");
row.put("From Node", "Inlet manifold");
row.put("To Node", "Valve skid");
row.put("Length [m]", "120.0");
row.put("Internal diameter [mm]", "508.0");
row.put("Wall thickness [mm]", "12.7");
row.put("Elevation change [m]", "-2.0");
row.put("Roughness [micrometer]", "45.0");
row.put("Minor losses", "gate valve:0.15; long-radius bend=0.20");

PipingRouteBuilder route = PipingRouteBuilder.fromLineListRows(Arrays.asList(row));
```

For CSV exports, use `fromCsv(path)` for a UTF-8 file or `fromCsvLines(lines)`
when another extraction tool already has the file content in memory. The CSV
parser supports quoted cells, which is useful for fitting lists containing
commas or semicolon-separated K values.

```java
PipingRouteBuilder route = PipingRouteBuilder.fromCsv(csvPath);
```

The importer recognizes these common column families:

| Data | Example aliases |
|------|-----------------|
| Segment id | `segment_id`, `line_number`, `line_no`, `route_segment`, `tag` |
| From node | `from_node`, `upstream_node`, `source`, `from_equipment` |
| To node | `to_node`, `downstream_node`, `target`, `to_equipment` |
| Length | `length`, `length_m`, `straight_length`, `run_length` |
| Diameter | `internal_diameter`, `internal_diameter_mm`, `nominal_size`, `NPS` |
| Wall thickness | `wall_thickness`, `wall_thickness_mm`, `schedule_wall_thickness` |
| Elevation | `elevation_change`, `elevation_change_m`, `static_head` |
| Roughness | `roughness`, `roughness_mm`, `pipe_wall_roughness` |
| Minor losses | `minor_losses`, `fittings`, `valves`, `k_value`, `gate_valve_k` |

Unit-bearing headers such as `Length [m]` or `Internal diameter (mm)` are
accepted. Inline values such as `25 m` or `45 micrometer` are also accepted.
`NPS`, `nominal_size`, and `nominal_pipe_size` default to inches when no unit is
specified. Prefer a calculated internal diameter column when wall schedule,
corrosion allowance, or lining thickness is known.

## STID Extraction Handoff

When reading STID P&IDs, E3D exports, stress isometrics, or line-list Excel
files, extract route rows into a neutral handoff structure before creating the
builder. Keep source references so pressure-drop results can be traced back to
specific drawing rows.

```json
{
  "route_id": "generic compressor suction route",
  "source_documents": ["P-ID-001.pdf", "stress_iso_route_001.pdf"],
  "minor_loss_friction_factor": 0.02,
  "default_roughness_m": 0.000045,
  "segments": [
    {
      "segment_id": "ROUTE-001-S1",
      "from_node": "Upstream manifold",
      "to_node": "separator outlet",
      "length": 120.0,
      "length_unit": "m",
      "internal_diameter": 0.508,
      "diameter_unit": "m",
      "wall_thickness": 12.7,
      "wall_thickness_unit": "mm",
      "elevation_change": 0.0,
      "elevation_unit": "m",
      "minor_losses": [
        {"type": "gate valve", "k_value": 0.15}
      ],
      "source_ref": "stress_iso_route_001.pdf page 2 row S1"
    }
  ]
}
```

The neutral structure can be passed to `fromLineListRows(...)` row-by-row after
JSON parsing, or converted manually with `addSegment(...)`,
`setSegmentWallThickness(...)`, `setSegmentElevationChange(...)`, and
`addMinorLoss(...)` when custom preprocessing is needed.

## STID Workflow Checklist

- Save all source PDFs, Excel files, and extracted PNG pages in the task folder
  under `step1_scope_and_research/references/` and `figures/`.
- Extract line sizes, route order, fittings, valves, reducers, strainers, and
  elevations with source page/row references.
- Prefer calculated internal diameter from nominal size, schedule, corrosion
  allowance, and lining thickness. If only `NPS` is available, the importer
  treats it as inches and the assumption must be documented.
- Convert every fitting, valve, tee, reducer, and equipment-entry loss to a K
  value or equivalent `L/D` basis. Keep the K source in notes.
- For route-only checks, run `route.build(feedStream)`. For a route inside a
  plant model, use `route.addToProcessSystem(process, inletStream)` and pass the
  returned outlet stream to the downstream equipment.
- Run the resulting `ProcessSystem` against a plant snapshot when available.
  Report simulated pressure drop, measured pressure drop, and the deviation.
- Export `route.toJson()` into the task results or appendix so later agents can
  reuse the same route geometry.

## Limitations

- The builder models serial routes. For parallel branches and ring mains, build
  separate serial routes or use [Looped Pipeline Networks](equipment/looped_networks.md).
- `nominalDiameter` is treated as hydraulic internal diameter by the pipe model.
  Convert schedule and corrosion allowance externally when needed.
- The minor-loss conversion is a screening method. Use task-specific friction
  factors or detailed fittings data for final design checks.
- Mechanical integrity, wall thickness design, supports, and vibration checks are
  covered by [Topside Piping Design](topside_piping_design.md) and
  [Pipeline Mechanical Design](pipeline_mechanical_design.md).

## Related Documentation

- [Pipelines and Pipes](equipment/pipelines.md)
- [Pipe Fittings and Equivalent Length](PIPE_FITTINGS_EQUIVALENT_LENGTH.md)
- [Topside Piping Design](topside_piping_design.md)
- [Looped Pipeline Networks](equipment/looped_networks.md)
- [Process Extraction Skill Guide](../integration/skills_guide.md)
