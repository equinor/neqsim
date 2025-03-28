package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class AdjusterTest {
  @Test
  void testRun() {
    double wellheadpressure = 120.0;
    double bottomholepressure = 200.0;

    neqsim.thermo.system.SystemInterface fluid1 = neqsim.thermo.FluidCreator.create("light oil");
    fluid1.setMixingRule("classic");

    neqsim.process.equipment.stream.Stream stream1 =
        new neqsim.process.equipment.stream.Stream("light oil", fluid1.clone());
    stream1.setFlowRate(1.5, "MSm3/day");
    stream1.setPressure(bottomholepressure, "bara");
    stream1.setTemperature(75.0, "C");

    neqsim.process.equipment.pipeline.PipeBeggsAndBrills flowline1 =
        new neqsim.process.equipment.pipeline.PipeBeggsAndBrills("flowline", stream1);
    flowline1.setDiameter(0.25);
    flowline1.setPipeWallRoughness(15e-6);
    flowline1.setLength(1200);
    flowline1.setElevation(1200.0);
    flowline1.setNumberOfIncrements(20);

    neqsim.process.equipment.util.Adjuster adjuster =
        new neqsim.process.equipment.util.Adjuster("adjuster");
    adjuster.setTargetVariable(flowline1.getOutletStream(), "pressure", wellheadpressure, "bara");
    adjuster.setAdjustedVariable(stream1, "flow", "MSm3/day");
    adjuster.setMaxAdjustedValue(10.0);
    adjuster.setMinAdjustedValue(1);
    adjuster.setTolerance(1e-5);

    neqsim.process.processmodel.ProcessSystem process =
        new neqsim.process.processmodel.ProcessSystem();
    process.add(stream1);
    process.add(flowline1);
    process.add(adjuster);
    process.run();

    assertEquals(flowline1.getOutletStream().getPressure(), 120, 1);
    assertEquals(flowline1.getOutletStream().getFlowRate("MSm3/day"), 4.0, 0.1);
  }

  @Test
  void testRun2() {
    double wellheadpressure = 120.0;
    double bottomholepressure = 200.0;

    neqsim.thermo.system.SystemInterface fluid1 = neqsim.thermo.FluidCreator.create("light oil");
    fluid1.setMixingRule("classic");

    neqsim.process.equipment.stream.Stream stream1 =
        new neqsim.process.equipment.stream.Stream("light oil", fluid1.clone());
    stream1.setFlowRate(4.0, "MSm3/day");
    stream1.setPressure(170, "bara");
    stream1.setTemperature(75.0, "C");

    neqsim.process.equipment.pipeline.PipeBeggsAndBrills flowline1 =
        new neqsim.process.equipment.pipeline.PipeBeggsAndBrills("flowline", stream1);
    flowline1.setDiameter(0.25);
    flowline1.setPipeWallRoughness(15e-6);
    flowline1.setLength(1200);
    flowline1.setElevation(1200.0);
    flowline1.setNumberOfIncrements(20);

    neqsim.process.equipment.util.Adjuster adjuster =
        new neqsim.process.equipment.util.Adjuster("adjuster");
    adjuster.setTargetVariable(flowline1.getOutletStream(), "pressure", wellheadpressure, "bara");
    adjuster.setAdjustedVariable(stream1, "pressure", "bara");
    adjuster.setMaxAdjustedValue(260.0);
    adjuster.setMinAdjustedValue(50.1);
    adjuster.setTolerance(1e-5);

    neqsim.process.processmodel.ProcessSystem process =
        new neqsim.process.processmodel.ProcessSystem();
    process.add(stream1);
    process.add(flowline1);
    process.add(adjuster);
    process.run();

    assertEquals(flowline1.getOutletStream().getPressure(), 120, 1e-3);
    assertEquals(flowline1.getOutletStream().getFlowRate("MSm3/day"), 4.0, 1e-3);
    assertEquals(flowline1.getInletStream().getPressure(), 199.976882003305, 0.1);
  }
}
