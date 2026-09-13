---
title: Manifolds
description: Build and validate production gathering and distribution manifolds with current NeqSim APIs.
---

# Manifolds

`neqsim.process.equipment.manifold.Manifold` combines an internal mixer with an internal splitter. Use it when several inlet streams must be gathered and the mixed stream must then be divided between parallel trains or export routes.

The public model boundary is the manifold itself. Add inlet streams with `addStream(StreamInterface)`, define the outlets with `setSplitFactors(double[])`, run the unit, and read the mixed or split streams. The number of outlets is the length of the split-factor array; there is no separate split-count setter.

## Complete production-manifold example

This Java 8 program is compiled and executed from the Markdown source during the repository test suite. Assertions check standard-volume and mass conservation, outlet composition, current geometry methods, capacity-screen availability, and auto-sizing state.

```java
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.manifold.Manifold;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class ProductionManifoldExample {
  private static final Logger logger = LogManager.getLogger(ProductionManifoldExample.class);

  public static void main(String[] args) {
    SystemInterface wellAFluid = new SystemSrkEos(323.15, 80.0);
    wellAFluid.addComponent("methane", 90.0);
    wellAFluid.addComponent("ethane", 8.0);
    wellAFluid.addComponent("propane", 2.0);
    wellAFluid.setMixingRule("classic");

    SystemInterface wellBFluid = wellAFluid.clone();
    wellBFluid.setMolarComposition(new double[] {0.80, 0.15, 0.05});
    wellBFluid.setTemperature(313.15);
    wellBFluid.setPressure(78.0);

    Stream wellA = new Stream("Well A", wellAFluid);
    wellA.setFlowRate(3.0, "MSm3/day");
    wellA.run();

    Stream wellB = new Stream("Well B", wellBFluid);
    wellB.setFlowRate(2.0, "MSm3/day");
    wellB.run();

    Manifold manifold = new Manifold("PM-101");
    manifold.addStream(wellA);
    manifold.addStream(wellB);
    manifold.setSplitFactors(new double[] {0.60, 0.40});
    manifold.setHeaderInnerDiameter(304.8, "mm");
    manifold.setBranchInnerDiameter(202.7, "mm");
    manifold.run();

    StreamInterface mixed = manifold.getMixedStream();
    StreamInterface trainA = manifold.getSplitStream(0);
    StreamInterface trainB = manifold.getSplitStream(1);
    assert mixed != null;
    assert trainA != null;
    assert trainB != null;

    double inletStandardFlow = wellA.getFlowRate("MSm3/day") + wellB.getFlowRate("MSm3/day");
    double outletStandardFlow =
        trainA.getFlowRate("MSm3/day") + trainB.getFlowRate("MSm3/day");
    assert Math.abs(mixed.getFlowRate("MSm3/day") - inletStandardFlow) < 1.0e-6;
    assert Math.abs(outletStandardFlow - inletStandardFlow) < 1.0e-6;
    assert Math.abs(trainA.getFlowRate("MSm3/day") - 3.0) < 1.0e-6;
    assert Math.abs(trainB.getFlowRate("MSm3/day") - 2.0) < 1.0e-6;

    double trainAMethane = trainA.getFluid().getComponent(0).getx();
    double trainBMethane = trainB.getFluid().getComponent(0).getx();
    assert Math.abs(trainAMethane - trainBMethane) < 1.0e-12;

    double inletMassFlow = wellA.getFlowRate("kg/hr") + wellB.getFlowRate("kg/hr");
    assert Math.abs(manifold.getMassBalance("kg/hr")) < Math.max(1.0e-6, inletMassFlow * 1.0e-10);
    assert manifold.getHeaderVelocity() > 0.0;
    assert manifold.getBranchVelocity() > 0.0;

    Map<String, CapacityConstraint> constraints = manifold.getCapacityConstraints();
    assert constraints.containsKey("headerVelocity");
    assert constraints.containsKey("branchVelocity");
    assert constraints.containsKey("headerLOF");
    assert constraints.containsKey("headerFRMS");

    manifold.autoSize(1.20);
    assert manifold.isAutoSized();
    assert manifold.getHeaderInnerDiameter() > 0.0;
    assert manifold.getBranchInnerDiameter() > 0.0;

    logger.info(
        "Mixed flow {} MSm3/day; Train A {} MSm3/day; Train B {} MSm3/day; header ID {} mm; branch ID {} mm",
        mixed.getFlowRate("MSm3/day"),
        trainA.getFlowRate("MSm3/day"),
        trainB.getFlowRate("MSm3/day"),
        manifold.getHeaderInnerDiameter() * 1000.0,
        manifold.getBranchInnerDiameter() * 1000.0);
  }
}
```

The example deliberately runs the inlet streams before the manifold. In a flowsheet, add the inlet streams and manifold to a `ProcessSystem` in dependency order and let `ProcessSystem.run()` perform the same sequencing.

## Public API and behavior

| Task | Current API | Behavior |
| --- | --- | --- |
| Add a feed | `addStream(StreamInterface)` | Adds the stream to the internal mixer and refreshes the splitter connection. |
| Replace a feed | `replaceStream(int, StreamInterface)` | Replaces a zero-based mixer inlet, useful for controlled topology changes. |
| Configure outlets | `setSplitFactors(double[])` | Defines both the outlet count and split factors. |
| Inspect split factors | `getSplitFactors()` | Returns the configured factor array. |
| Read mixed flow | `getMixedStream()` | Returns the internal mixer outlet. |
| Read one outlet | `getSplitStream(int)` | Returns the selected split stream, or `null` for an invalid index. |
| Read all boundaries | `getInletStreams()`, `getOutletStreams()` | Exposes the manifold boundary without direct access to internal units. |
| Check balance | `getMassBalance(String)` | Returns outlet minus inlet flow in the requested unit. |
| Run | `run()` or `run(UUID)` | Runs the mixer, reconnects the splitter, and runs the split operation. |

Do not configure or run the internal mixer and splitter separately. Their implementation is encapsulated; use `getMixedStream()`, `getSplitStream(int)`, and the inlet/outlet list methods.

## Geometry, FIV, and capacity screening

Set process geometry with `setHeaderInnerDiameter(double[, String])`, `setHeaderWallThickness(double[, String])`, `setBranchInnerDiameter(double[, String])`, and `setBranchWallThickness(double[, String])`. Geometry without a unit string is in metres; supported unit strings for these overloads are `m`, `mm`, `in`, and `inch`.

After a successful run:

- `getHeaderVelocity()` and `getBranchVelocity()` report mixture velocities in m/s.
- `calculateHeaderLOF()` and `calculateBranchLOF()` expose likelihood-of-failure screening values.
- `calculateHeaderFRMS()` and `calculateBranchFRMS()` expose vibration-intensity screening values.
- `getCapacityConstraints()` returns named header/branch velocity, LOF, and FRMS constraints.
- `getBottleneckConstraint()`, `getMaxUtilization()`, and `isCapacityExceeded()` summarize enabled constraints.

Use `setMaxHeaderVelocityDesign(double)`, `setMaxBranchVelocityDesign(double)`, `setMaxLOFDesign(double)`, and `setMaxFRMSDesign(double)` only when the project has approved design limits. The defaults and embedded correlations are screening assumptions, not a substitute for piping stress, support, fatigue, slug-load, or vendor review.

The FIV and capacity outputs are screening outputs and are not a vibration qualification or proof of compliance with API RP 14E, ASME B31.3, DNV-ST-F101, or a company standard. Confirm units, geometry, phase behavior, load cases, correlation applicability, acceptance criteria, and accountable engineering approval independently.

## Auto-sizing boundary

`autoSize(double safetyFactor)` selects approximate standard inner diameters from the current simulated volume flow and updates the manifold's screening constraints. Call it only after all feeds and split factors are configured. Treat the selected diameters as an initial screening result; they do not establish pressure containment, schedule availability, erosion allowance, branch reinforcement, fatigue life, or constructability.

For a separate mechanical-design object, call `initMechanicalDesign()` before `getMechanicalDesign()`. The process manifold and mechanical-design calculations remain distinct validation layers.

## Integration guidance

- Keep inlet pressures reasonably aligned. Model upstream choking or control valves explicitly when pressure matching is part of the process definition.
- Split streams share the mixed composition; split factors route the mixed flow rather than performing component separation.
- A zero split factor can represent a closed route for scenario screening. Re-run downstream equipment after changing factors.
- Use `replaceStream(int, StreamInterface)` for controlled rewiring; do not mutate internal equipment references.
- Verify both standard-volume and mass balances when comparing alternate routing cases.

## Related documentation

- [Mixers and splitters](mixers_splitters)
- [Streams](streams)
- [Subsea systems](subsea_systems)
- [Manifold mechanical design](manifold_design)
- [Capacity constraint framework](../CAPACITY_CONSTRAINT_FRAMEWORK)
- [Process-system run status](../processmodel/run_status)
