package neqsim.process.equipment.separator;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test for separator handling pure gas (no liquid phase).
 */
class PureGasSeparatorTest extends neqsim.NeqSimTest {
  /**
   * Test that separator works with pure gas input (no liquid phase). This tests both steady-state
   * and transient operation.
   */
  @Test
  void testPureGasSeparatorSteadyState() {
    SystemInterface fluid1 = new SystemSrkEos(278.15, 10.0);
    fluid1.addComponent("hydrogen", 1.0);
    fluid1.setMixingRule("classic");
    fluid1.init(0);

    Stream stream1 = new Stream("feed stream", fluid1);
    stream1.setTemperature(278.15, "K");
    stream1.setPressure(10.0, "bara");
    stream1.setFlowRate(100.0, "kg/hr");
    stream1.run();

    ThrottlingValve valve1 = new ThrottlingValve("control valve 1", stream1);
    valve1.setOutletPressure(3.0, "bara");
    valve1.run();

    Separator separator1 = new Separator("separator 1", valve1.getOutletStream());
    separator1.setSeparatorLength(4.0);
    separator1.setInternalDiameter(2.0);
    separator1.setLiquidLevel(0.000001);
    separator1.run();

    // Should have gas but no liquid
    org.junit.jupiter.api.Assertions.assertTrue(
        separator1.getGasOutStream().getFlowRate("kg/hr") > 0, "Gas flow should be positive");
    org.junit.jupiter.api.Assertions.assertEquals(0.0,
        separator1.getLiquidOutStream().getFlowRate("kg/hr"), 1e-6,
        "Liquid flow should be zero for pure gas");
    org.junit.jupiter.api.Assertions.assertEquals(0.0, separator1.getLiquidLevel(), 1e-6,
        "Liquid level should be zero for pure gas");

    // Verify mass balance
    double massBalance = separator1.getMassBalance("kg/hr");
    org.junit.jupiter.api.Assertions.assertEquals(0.0, massBalance, 1.0,
        "Mass balance should be close to zero");
  }

  /**
   * Test that separator works with pure gas in transient mode.
   */
  @Test
  void testPureGasSeparatorTransient() {
    SystemInterface fluid1 = new SystemSrkEos(278.15, 10.0);
    fluid1.addComponent("hydrogen", 1.0);
    fluid1.setMixingRule("classic");
    fluid1.init(0);

    Stream stream1 = new Stream("feed stream", fluid1);
    stream1.setTemperature(278.15, "K");
    stream1.setPressure(10.0, "bara");
    stream1.setFlowRate(100.0, "kg/hr");
    stream1.run();

    ThrottlingValve valve1 = new ThrottlingValve("control valve 1", stream1);
    valve1.setOutletPressure(3.0, "bara");
    valve1.run();

    Separator separator1 = new Separator("separator 1", valve1.getOutletStream());
    separator1.setSeparatorLength(4.0);
    separator1.setInternalDiameter(2.0);
    separator1.setLiquidLevel(0.000001);
    separator1.run();

    ThrottlingValve gasValve1 = new ThrottlingValve("gas valve 1", separator1.getGasOutStream());
    gasValve1.setOutletPressure(1.01325, "bara");
    gasValve1.run();

    Compressor compressor1 = new Compressor("compressor 1", gasValve1.getOutletStream());
    compressor1.setOutletPressure(5.0, "bara");
    compressor1.run();

    ThrottlingValve liquidValve1 =
        new ThrottlingValve("liq valve 1", separator1.getLiquidOutStream());
    liquidValve1.setOutletPressure(1.01325, "bara");
    liquidValve1.run();

    ProcessSystem process1 = new ProcessSystem("process 1");
    process1.add(valve1);
    process1.add(separator1);
    process1.add(gasValve1);
    process1.add(compressor1);
    process1.add(liquidValve1);
    process1.run();

    valve1.setCalculateSteadyState(false);
    separator1.setCalculateSteadyState(false);
    gasValve1.setCalculateSteadyState(false);
    liquidValve1.setCalculateSteadyState(false);

    process1.setTimeStep(1.0);

    // Run transient simulation - this should not crash
    for (int i = 0; i < 10; i++) {
      process1.runTransient();
      // Verify gas flow exists but no liquid
      org.junit.jupiter.api.Assertions
          .assertTrue(gasValve1.getOutletStream().getFlowRate("kg/hr") > 0);
      org.junit.jupiter.api.Assertions.assertEquals(0.0, separator1.getLiquidLevel(), 1e-6);
    }

    valve1.setPercentValveOpening(0.1);

    // Continue transient with reduced valve opening
    for (int i = 0; i < 10; i++) {
      process1.runTransient();
      // Should still work without crashing
      org.junit.jupiter.api.Assertions
          .assertTrue(gasValve1.getOutletStream().getFlowRate("kg/hr") >= 0);
    }
  }
}
