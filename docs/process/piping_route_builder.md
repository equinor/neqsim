---
title: Route-Level Piping Hydraulic Builder
description: Build route-level pipe hydraulic models from STID, E3D, P&ID, or stress-isometric line-list tables using PipingRouteBuilder, PipeBeggsAndBrills, and water-hammer screening handoffs.
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

Then convert each row with `addSegment(...)`, `setSegmentWallThickness(...)`,
`setSegmentElevationChange(...)`, and `addMinorLoss(...)`.

## Water-Hammer Screening Handoff

The same extracted route can also feed fast hydraulic-surge screening with
`WaterHammerStudy` or MCP `runWaterHammer`. Use this handoff when the route is
liquid-filled or liquid-rich and the initiating event is a fast ESD closure, pump
trip, check-valve slam, or sudden flow change.

`PipingRouteBuilder` remains the steady or slow-transient hydraulic model. The
water-hammer runner aggregates the route into an equivalent single line, adds
screening equivalent length for `minorLosses`, and reports a warning when segment
diameters vary.

```json
{
  "studyName": "generic ESD closure screening",
  "components": {"water": 1.0},
  "temperature_C": 20.0,
  "pressure_bara": 45.0,
  "flowRate": {"value": 120000.0, "unit": "kg/hr"},
  "designPressure_bara": 95.0,
  "stidRoute": {
    "segments": [
      {
        "segmentId": "ROUTE-001-S1",
        "length_m": 120.0,
        "diameter_m": 0.2032,
        "wallThickness_m": 0.0127,
        "roughness_m": 4.6e-5,
        "elevationChange_m": 1.0,
        "minorLosses": [
          {"type": "gate valve", "kValue": 0.15},
          {"type": "long-radius bend", "kValue": 0.20}
        ],
        "sourceRef": "generic stress-isometric row S1"
      }
    ]
  },
  "fieldData": {
    "inletPressure_bara": 46.0,
    "inletTemperature_C": 19.0,
    "flowRate_kg_hr": 118000.0,
    "valveOpening": 1.0
  },
  "eventSchedule": [
    {
      "type": "VALVE_CLOSURE",
      "startTime_s": 0.10,
      "duration_s": 0.15,
      "startOpening": 1.0,
      "endOpening": 0.0
    }
  ]
}
```

Report both the steady pressure-drop result and the surge-screening result when
available. That pairing gives reviewers a quick check of normal hydraulic duty,
transient pressure envelope, and the evidence trail back to STID/tagreader inputs.

## STID Workflow Checklist

- Save all source PDFs, Excel files, and extracted PNG pages in the task folder
  under `step1_scope_and_research/references/` and `figures/`.
- Extract line sizes, route order, fittings, valves, reducers, strainers, and
  elevations with source page/row references.
- Convert nominal pipe size and schedule to internal diameter before calling
  `addSegment(...)`. If only nominal size is available, document the internal
  diameter assumption.
- Convert every fitting, valve, tee, reducer, and equipment-entry loss to a K
  value or equivalent `L/D` basis. Keep the K source in notes.
- For route-only checks, run `route.build(feedStream)`. For a route inside a
  plant model, use `route.addToProcessSystem(process, inletStream)` and pass the
  returned outlet stream to the downstream equipment.
- Run the resulting `ProcessSystem` against a plant snapshot when available.
  Report simulated pressure drop, measured pressure drop, and the deviation.
- For fast valve closure or pump-trip cases, send the same route and field-data
  snapshot to `WaterHammerStudy` or MCP `runWaterHammer` and report the pressure
  envelope and design-pressure margin.
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
- [Water Hammer Simulation](../wiki/water_hammer_implementation.md)
- [Process Extraction Skill Guide](../integration/skills_guide.md)
