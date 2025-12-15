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
    assertEquals(flowline1.getOutletStream().getFlowRate("MSm3/day"), 4.0, 0.5);
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
    assertEquals(flowline1.getInletStream().getPressure(), 198.08052788226698, 2.0);
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
    adjuster.setAdjustedVariable(inletStream);

    adjuster.setAdjustedValueGetter((equipment) -> {
      return ((neqsim.process.equipment.stream.Stream) equipment).getTemperature("K");
    });

    adjuster.setAdjustedValueSetter((equipment, val) -> {
      ((neqsim.process.equipment.stream.Stream) equipment).setTemperature(val, "K");
    });

    adjuster.setTargetVariable(inletStream);
    adjuster.setTargetValue(30000.0);
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

  @Test
  void testFunctionalInterfaceSupport() {
    // Create a simple system
    neqsim.thermo.system.SystemSrkEos fluid = new neqsim.thermo.system.SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 100.0); // 100 moles

    neqsim.process.equipment.stream.Stream feed =
        new neqsim.process.equipment.stream.Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr"); // Total flow 1000 kg/hr

    neqsim.process.equipment.splitter.Splitter splitter =
        new neqsim.process.equipment.splitter.Splitter("splitter", feed);
    splitter.setSplitNumber(2);
    splitter.setSplitFactors(new double[] {0.5, 0.5});

    // Initial guess: split 50/50
    neqsim.process.equipment.stream.Stream stream1 =
        new neqsim.process.equipment.stream.Stream("stream1", splitter.getSplitStream(0));
    neqsim.process.equipment.stream.Stream stream2 =
        new neqsim.process.equipment.stream.Stream("stream2", splitter.getSplitStream(1));

    // We want to adjust stream 2 flow rate (via splitter)
    // so that stream 1 flow rate becomes 800 kg/hr.
    // Since total is 1000, stream 2 should become 200.

    Adjuster adjuster = new Adjuster("adjuster");

    // Set the target value (Setpoint)
    adjuster.setTargetValue(800.0);
    adjuster.setMinAdjustedValue(0.0); // Flow rate cannot be negative
    adjuster.setMaxAdjustedValue(1000.0); // Flow rate cannot exceed feed

    // Set the logic using functional interfaces

    // Setter: Adjusts the flow rate of the second outlet (index 1) of the splitter
    // Note: Splitter.setFlowRates takes an array of flow rates. -1 means calculated.
    adjuster.setAdjustedValueSetter((val) -> {
      splitter.setFlowRates(new double[] {-1, val}, "kg/hr");
    });

    // Getter: Gets the current flow rate of the second outlet (index 1)
    adjuster.setAdjustedValueGetter(() -> {
      return splitter.getSplitStream(1).getFlowRate("kg/hr");
    });

    // Target Calculator: Gets the flow rate of stream 1 (the measured variable)
    adjuster.setTargetValueCalculator(() -> {
      return stream1.getFlowRate("kg/hr");
    });

    neqsim.process.processmodel.ProcessSystem process =
        new neqsim.process.processmodel.ProcessSystem();
    process.add(feed);
    process.add(splitter);
    process.add(stream1);
    process.add(stream2);
    process.add(adjuster);

    process.run();

    // Verify results
    assertEquals(800.0, stream1.getFlowRate("kg/hr"), 1.0);
    assertEquals(200.0, stream2.getFlowRate("kg/hr"), 1.0);
  }
}
