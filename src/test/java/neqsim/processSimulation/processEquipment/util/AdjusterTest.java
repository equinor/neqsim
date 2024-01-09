package neqsim.processSimulation.processEquipment.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class AdjusterTest {
  @Test
  void testRun() {

    double wellheadpressure = 150.0;
    double bottomholepressure = 200.0;

    neqsim.thermo.system.SystemInterface fluid1 = neqsim.thermo.FluidCreator.create("light oil");
    fluid1.setMixingRule("classic");

    neqsim.processSimulation.processEquipment.stream.Stream stream1 =
        new neqsim.processSimulation.processEquipment.stream.Stream(fluid1.clone());
    stream1.setFlowRate(1.5, "MSm3/day");
    stream1.setPressure(bottomholepressure, "bara");
    stream1.setTemperature(75.0, "C");

    neqsim.processSimulation.processEquipment.pipeline.PipeBeggsAndBrills flowline1 =
        new neqsim.processSimulation.processEquipment.pipeline.PipeBeggsAndBrills(stream1);
    flowline1.setDiameter(0.25);
    flowline1.setPipeWallRoughness(15e-6);
    flowline1.setLength(1200);
    flowline1.setElevation(1200.0);
    flowline1.setNumberOfIncrements(20);


    neqsim.processSimulation.processEquipment.util.Adjuster adjuster =
        new neqsim.processSimulation.processEquipment.util.Adjuster("adjuster");
    adjuster.setTargetVariable(flowline1.getOutletStream(), "pressure", wellheadpressure, "bara");
    adjuster.setAdjustedVariable(stream1, "flow", "MSm3/day");
    adjuster.setMaxAdjustedValue(200.0);
    adjuster.setMinAdjustedValue(0.1);
    adjuster.setTolerance(1e-5);

    neqsim.processSimulation.processSystem.ProcessSystem process =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    process.add(stream1);
    process.add(flowline1);
    process.add(adjuster);
    process.run();

    assertEquals(flowline1.getOutletStream().getPressure(), 150, 1e-3);
    assertEquals(flowline1.getOutletStream().getFlowRate("MSm3/day"), 4.101310260394316, 1e-3);
  }

  @Test
  void testRun2() {

    double wellheadpressure = 150.0;
    double bottomholepressure = 200.0;

    neqsim.thermo.system.SystemInterface fluid1 = neqsim.thermo.FluidCreator.create("light oil");
    fluid1.setMixingRule("classic");

    neqsim.processSimulation.processEquipment.stream.Stream stream1 =
        new neqsim.processSimulation.processEquipment.stream.Stream(fluid1.clone());
    stream1.setFlowRate(1.5, "MSm3/day");
    stream1.setPressure(bottomholepressure, "bara");
    stream1.setTemperature(75.0, "C");

    neqsim.processSimulation.processEquipment.pipeline.PipeBeggsAndBrills flowline1 =
        new neqsim.processSimulation.processEquipment.pipeline.PipeBeggsAndBrills(stream1);
    flowline1.setDiameter(0.25);
    flowline1.setPipeWallRoughness(15e-6);
    flowline1.setLength(1200);
    flowline1.setElevation(1200.0);
    flowline1.setNumberOfIncrements(20);


    neqsim.processSimulation.processEquipment.util.Adjuster adjuster =
        new neqsim.processSimulation.processEquipment.util.Adjuster("adjuster");
    adjuster.setTargetVariable(flowline1.getOutletStream(), "pressure", wellheadpressure, "bara");
    adjuster.setAdjustedVariable(stream1, "pressure", "bara");
    adjuster.setMaxAdjustedValue(220.0);
    adjuster.setMinAdjustedValue(50.1);
    adjuster.setTolerance(1e-5);

    neqsim.processSimulation.processSystem.ProcessSystem process =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    process.add(stream1);
    process.add(flowline1);
    process.add(adjuster);
    process.run();

    assertEquals(flowline1.getOutletStream().getPressure(), 150, 1e-3);
    assertEquals(flowline1.getOutletStream().getFlowRate("MSm3/day"), 1.5, 1e-3);
  }
}
