package neqsim.process.equipment.separator;

import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Tests the withdrawal streams published by a dynamic three-phase separator.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ThreePhaseSeparatorTransientOutletTest {
  /**
   * A balanced first step preserves phase identity, volumetric flow and downstream gas-valve capacity.
   */
  @Test
  void retainsPhasePropertiesAtUnchangedOperatingPoint() {
    SystemInterface fluid = new SystemSrkCPAstatoil(318.15, 15.0);
    fluid.addComponent("methane", 48.0);
    fluid.addComponent("ethane", 5.0);
    fluid.addComponent("propane", 3.0);
    fluid.addComponent("n-butane", 2.0);
    fluid.addComponent("n-heptane", 12.0);
    fluid.addComponent("water", 30.0);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(18000.0, "kg/hr");
    feed.run();
    ThreePhaseSeparator separator = new ThreePhaseSeparator("V-101", feed);
    separator.setOrientation("horizontal");
    separator.setInternalDiameter(1.6);
    separator.setSeparatorLength(5.0);
    separator.setOilLevel(0.85);
    separator.run();

    StreamInterface gas = separator.getGasOutStream();
    StreamInterface[] draws = {gas, separator.getOilOutStream(), separator.getWaterOutStream()};
    String[] types = {"gas", "oil", "aqueous"};
    double[] massRates = new double[draws.length];
    double[] volumeRates = new double[draws.length];
    for (int i = 0; i < draws.length; i++) {
      massRates[i] = draws[i].getFlowRate("kg/hr");
      volumeRates[i] = draws[i].getFlowRate("m3/hr");
    }
    double gasZ = gas.getFluid().getZ();
    ThrottlingValve valve = new ThrottlingValve("PV-101", gas);
    valve.setOutletPressure(10.0, "bara");
    valve.setKv(50.0);
    valve.run();
    double capacity = valve.calculateMolarFlow();

    separator.setCalculateSteadyState(false);
    separator.runTransient(2.0, UUID.randomUUID());

    Assertions.assertEquals(15.0, separator.getThermoSystem().getPressure("bara"), 1.0e-6);
    for (int i = 0; i < draws.length; i++) {
      SystemInterface draw = draws[i].getFluid();
      Assertions.assertEquals(1, draw.getNumberOfPhases(), types[i]);
      Assertions.assertEquals(types[i], draw.getPhase(0).getPhaseTypeName());
      Assertions.assertEquals(massRates[i], draws[i].getFlowRate("kg/hr"), massRates[i] * 1.0e-9);
      Assertions.assertEquals(volumeRates[i], draws[i].getFlowRate("m3/hr"), volumeRates[i] * 1.0e-7);
    }
    Assertions.assertEquals(gasZ, gas.getFluid().getZ(), 1.0e-7);
    Assertions.assertEquals(capacity, valve.calculateMolarFlow(), capacity * 1.0e-7);
  }
}
