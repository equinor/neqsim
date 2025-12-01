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

  @Test
  public void testFlexibleAdjuster() {
    neqsim.thermo.system.SystemSrkEos testSystem =
        new neqsim.thermo.system.SystemSrkEos(298.15, 10.0);
    testSystem.addComponent("methane", 100.0);

    neqsim.process.equipment.stream.Stream inletStream =
        new neqsim.process.equipment.stream.Stream("inlet stream", testSystem);
    inletStream.setFlowRate(100.0, "kg/hr");
    inletStream.setTemperature(25.0, "C");
    inletStream.run();

    // We want to adjust the flow rate of the inlet stream
    // until a custom calculated value (Flow * Temperature in Kelvin) equals a target.
    // Target = 100.0 kg/hr * 300.0 K = 30000.0

    Adjuster adjuster = new Adjuster("Custom Adjuster");
    adjuster.setAdjustedVariable(inletStream, "flow", "kg/hr");
    adjuster.setTargetVariable(inletStream, "custom", 30000.0, "-");

    adjuster.setTargetValueCalculator((equipment) -> {
      neqsim.process.equipment.stream.Stream s = (neqsim.process.equipment.stream.Stream) equipment;
      return s.getFlowRate("kg/hr") * s.getTemperature("K");
    });

    // Set inlet temperature to 300 K to make math easy
    inletStream.setTemperature(300.0, "K");
    // Initial flow is 100. 100 * 300 = 30000. It should already be solved.
    // Let's change initial flow to 50. 50 * 300 = 15000.
    inletStream.setFlowRate(50.0, "kg/hr");

    neqsim.process.processmodel.ProcessSystem process =
        new neqsim.process.processmodel.ProcessSystem();
    process.add(inletStream);
    process.add(adjuster);
    process.run();

    assertEquals(100.0, inletStream.getFlowRate("kg/hr"), 0.1);
  }

  @Test
  public void testFlexibleAdjusterWithCustomSetter() {
    neqsim.thermo.system.SystemSrkEos testSystem =
        new neqsim.thermo.system.SystemSrkEos(298.15, 10.0);
    testSystem.addComponent("methane", 100.0);

    neqsim.process.equipment.stream.Stream inletStream =
        new neqsim.process.equipment.stream.Stream("inlet stream", testSystem);
    inletStream.setFlowRate(100.0, "kg/hr");
    inletStream.setTemperature(300.0, "K");

    // We want to adjust the temperature (via custom setter) to achieve a target flow * temperature
    // product.
    // Target = 30000.0
    // Flow is fixed at 100.0.
    // So target Temperature should be 300.0.
    // Let's start with Temperature = 200.0.
    inletStream.setTemperature(200.0, "K");

    Adjuster adjuster = new Adjuster("Custom Adjuster");
    // We still need to set adjustedVariable to something to pass internal checks, or we can rely on
    // our custom getter/setter.
    // The run() method checks: if (adjustedValueGetter != null) ...
    // So we don't strictly need setAdjustedVariable if we provide the getter/setter.
    // However, we need to pass the equipment to the constructor or set it.
    // setAdjustedVariable sets 'adjustedEquipment'.
    adjuster.setAdjustedVariable(inletStream, "temperature", "K");

    adjuster.setAdjustedValueGetter((equipment) -> {
      return ((neqsim.process.equipment.stream.Stream) equipment).getTemperature("K");
    });

    adjuster.setAdjustedValueSetter((equipment, val) -> {
      ((neqsim.process.equipment.stream.Stream) equipment).setTemperature(val, "K");
    });

    adjuster.setTargetVariable(inletStream, "custom", 30000.0, "-");
    adjuster.setTargetValueCalculator((equipment) -> {
      neqsim.process.equipment.stream.Stream s = (neqsim.process.equipment.stream.Stream) equipment;
      return s.getFlowRate("kg/hr") * s.getTemperature("K");
    });

    neqsim.process.processmodel.ProcessSystem process =
        new neqsim.process.processmodel.ProcessSystem();
    process.add(inletStream);
    process.add(adjuster);
    process.run();

    assertEquals(300.0, inletStream.getTemperature("K"), 0.1);
  }
}
