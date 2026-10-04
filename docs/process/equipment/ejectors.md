---
title: Ejector Equipment
description: Source-backed ejector setup, results, sizing diagnostics, and engineering limits.
---

NeqSim's `Ejector` mixes a high-pressure motive stream with a lower-pressure suction
stream and flashes the combined stream to a specified discharge pressure. Use it for
screening and process integration, then qualify the selected geometry and operating
envelope against vendor data and accountable engineering review.

## Model and input contract

The current implementation performs one quasi-one-dimensional energy, momentum, and
diffuser-recovery calculation. Although ejector literature distinguishes
constant-pressure mixing (CPM) and constant-area mixing (CAM), the public `Ejector` API
does not expose a CPM/CAM model selector. Do not describe a run as a selected CPM or CAM
calculation.

Use these input conventions:

- temperature in K when constructing the fluid, or an explicit temperature unit on a
  stream setter;
- absolute pressure in bara;
- mass flow in `kg/sec` or another explicit supported mass-flow unit;
- efficiencies as fractions from zero to one; and
- connection lengths and design velocities in m and m/s.

The motive and suction fluids must use compatible component definitions and mixing
rules. The discharge pressure is a required operating target. A manually supplied
mixing pressure is limited to the suction pressure by the implementation.

## Executable gas-ejector example

The following complete Java 8 program is compiled and executed by the documentation
test. Run with assertions enabled, for example `java -ea EjectorProcessExample`.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ejector.Ejector;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.ejector.EjectorMechanicalDesign;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public final class EjectorProcessExample {
  private static final Logger logger = LogManager.getLogger(EjectorProcessExample.class);

  private EjectorProcessExample() {}

  public static void main(String[] args) {
    SystemInterface motiveFluid = new SystemSrkEos(293.15, 10.0);
    motiveFluid.addComponent("methane", 1.0);
    motiveFluid.createDatabase(true);
    motiveFluid.setMixingRule(2);

    Stream motive = new Stream("motive gas", motiveFluid);
    motive.setFlowRate(0.50, "kg/sec");

    SystemInterface suctionFluid = new SystemSrkEos(293.15, 1.0);
    suctionFluid.addComponent("methane", 1.0);
    suctionFluid.createDatabase(true);
    suctionFluid.setMixingRule(2);

    Stream suction = new Stream("suction gas", suctionFluid);
    suction.setFlowRate(0.15, "kg/sec");

    Ejector ejector = new Ejector("gas booster", motive, suction);
    ejector.setDischargePressure(3.0);
    ejector.setEfficiencyIsentropic(0.75);
    ejector.setSuctionNozzleEfficiency(0.90);
    ejector.setMixingEfficiency(0.85);
    ejector.setDiffuserEfficiency(0.80);

    ProcessSystem process = new ProcessSystem();
    process.add(motive);
    process.add(suction);
    process.add(ejector);
    process.run();

    StreamInterface discharge = ejector.getMixedStream();
    EjectorMechanicalDesign design = ejector.getMechanicalDesign();
    double entrainmentRatio = ejector.getEntrainmentRatio();
    double compressionRatio = ejector.getCompressionRatio();
    double expansionRatio = ejector.getExpansionRatio();

    assert Math.abs(discharge.getPressure("bara") - 3.0) < 1.0e-4;
    assert Math.abs(ejector.getMassBalance("kg/sec")) < 1.0e-6;
    assert Math.abs(entrainmentRatio - 0.30) < 1.0e-10;
    assert Math.abs(compressionRatio - 3.0) < 1.0e-2;
    assert Math.abs(expansionRatio - 10.0 / 3.0) < 1.0e-2;
    assert design.getMotiveNozzleThroatArea() > 0.0;
    assert design.getMixingChamberArea() > 0.0;
    assert design.getDiffuserOutletArea() > 0.0;

    logger.info(
        "Pd={} bara, ER={}, CR={}, expansion ratio={}",
        discharge.getPressure("bara"),
        entrainmentRatio,
        compressionRatio,
        expansionRatio);
  }
}
```

The example uses prescribed inlet mass flows. Therefore the entrainment ratio is the
identity

$$ER=\frac{\dot m_{suction}}{\dot m_{motive}}$$

and is not solved from ejector geometry or a vendor capacity curve.

## Results and mechanical design

After `run()`, inspect:

| Quantity | Method | Interpretation |
|---|---|---|
| Discharge stream | `getMixedStream()` | Combined stream at the specified discharge pressure |
| Entrainment ratio | `getEntrainmentRatio()` | Prescribed suction mass flow divided by prescribed motive mass flow |
| Compression ratio | `getCompressionRatio()` | Discharge absolute pressure divided by suction absolute pressure |
| Expansion ratio | `getExpansionRatio()` | Motive absolute pressure divided by discharge absolute pressure |
| Critical back pressure | `getCriticalBackPressure()` | Internal thermodynamic screening estimate in bara |
| Area ratio | `getAreaRatio()` | Calculated mixing area divided by motive-nozzle throat area |
| Mach diagnostics | `getMotiveNozzleMach()`, `getSuctionMach()`, `getMixingMach()` | Calculated local velocity divided by estimated speed of sound |
| Breakdown flag | `isInBreakdown()` | True when the specified discharge exceeds the calculated critical back pressure |

`getMechanicalDesign()` exposes calculated areas, velocities, characteristic lengths,
and volumes. These values are preliminary process-model sizing outputs. They are not a
fabrication drawing, materials assessment, code check, nozzle-stress analysis, or vendor
guarantee.

## Performance-curve semantics

`generatePerformanceCurve(minPd, maxPd, points)` reruns the same ejector object at a
series of discharge pressures. Each returned row is
`[dischargePressureBara, entrainmentRatio, compressionRatio]`.

Because the inlet stream flow rates remain fixed, the reported entrainment ratio also
remains fixed unless the caller changes an inlet flow. The method does not solve the
suction capacity available at each back pressure and must not be presented as a
predictive vendor performance map. Failed pressure points are logged and omitted, so
callers must check the returned row count. The original discharge pressure is restored
and the ejector is rerun before the method returns; do not operate the same mutable
ejector concurrently.

## Engineering limits

- Efficiency defaults and suggested ranges are modelling assumptions, not universal
  equipment constants.
- The calculated critical back pressure is an internal screening estimate based on the
  mixed-flow stagnation enthalpy and diffuser efficiency. Validate it against vendor or
  experimental curves.
- Choking flags and Mach numbers are diagnostics from the quasi-one-dimensional model;
  they do not replace nozzle-profile, shock, condensation, or multiphase analysis.
- Steam ejectors, condensing service, liquid entrainment, fouling, erosion, acoustic
  limits, turndown, start-up, and off-design stability need service-specific evidence.
- Final selection requires vendor confirmation and independent process and mechanical
  review over the full operating envelope.

## Related documentation

- [Compressors](compressors) — rotating-equipment compression alternatives
- [Streams](streams) — stream construction and units
- [Process equipment](./) — equipment guide index
