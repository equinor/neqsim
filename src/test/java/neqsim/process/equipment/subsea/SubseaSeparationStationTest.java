package neqsim.process.equipment.subsea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Tests for {@link SubseaSeparationStation}.
 *
 * @author NeqSim
 * @version 1.0
 */
public class SubseaSeparationStationTest {
  /**
   * Wet well stream with gas, oil and water.
   *
   * @return inlet stream
   */
  private static Stream wellStream() {
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 60.0, 80.0);
    fluid.addComponent("methane", 40.0);
    fluid.addComponent("ethane", 4.0);
    fluid.addComponent("propane", 3.0);
    fluid.addComponent("n-heptane", 25.0);
    fluid.addComponent("nC10", 10.0);
    fluid.addComponent("water", 40.0);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    Stream s = new Stream("well", fluid);
    s.setFlowRate(100000.0, "kg/hr");
    s.run();
    return s;
  }

  /** Ideal separation sends nearly all water to the water outlet and conserves mass. */
  @Test
  void idealSeparationRemovesWater() {
    SubseaSeparationStation sss = new SubseaSeparationStation("SSS", wellStream());
    sss.run();
    assertTrue(sss.getWaterRemovedFraction() > 0.95);
    assertTrue(sss.getWaterMassFractionInLiquidOut() < 0.05);
    assertEquals(0.0, sss.getMassBalance("kg/hr") / 100000.0, 1e-3);
    assertEquals(3, sss.getOutletStreams().size());
    assertEquals(0.0, sss.getTotalPowerKW(), 0.0);
  }

  /** Carry-over fractions keep part of the water in the liquid stream. */
  @Test
  void carryOverLeavesWaterInLiquid() {
    SubseaSeparationStation ideal = new SubseaSeparationStation("A", wellStream());
    ideal.run();
    SubseaSeparationStation poor = new SubseaSeparationStation("B", wellStream());
    poor.setWaterRemovalEfficiency(0.7);
    poor.run();
    assertTrue(poor.getWaterRemovedFraction() < ideal.getWaterRemovedFraction() - 0.1,
        "removed ideal=" + ideal.getWaterRemovedFraction() + " poor=" + poor.getWaterRemovedFraction());
    assertTrue(poor.getWaterMassFractionInLiquidOut() > ideal.getWaterMassFractionInLiquidOut() + 0.02,
        "in liquid ideal=" + ideal.getWaterMassFractionInLiquidOut() + " poor="
            + poor.getWaterMassFractionInLiquidOut());
  }

  /** Boosting the liquid and the water draws pump power and raises the outlet pressures. */
  @Test
  void boostingDrawsPower() {
    SubseaSeparationStation sss = new SubseaSeparationStation("SSS", wellStream());
    sss.setPressureDrop(2.0);
    sss.setLiquidExportPressure(120.0);
    sss.setWaterInjectionPressure(200.0);
    sss.run();
    assertEquals(120.0, sss.getLiquidOutStream().getPressure(), 1e-3);
    assertEquals(200.0, sss.getWaterOutStream().getPressure(), 1e-3);
    assertEquals(78.0, sss.getGasOutStream().getPressure(), 1e-3);
    assertTrue(sss.getLiquidPumpPowerKW() > 0.0);
    assertTrue(sss.getWaterPumpPowerKW() > 0.0);
    assertEquals(sss.getLiquidPumpPowerKW() + sss.getWaterPumpPowerKW(), sss.getTotalPowerKW(), 1e-9);
  }

  /** Invalid input is rejected. */
  @Test
  void rejectsInvalidInput() {
    assertThrows(IllegalArgumentException.class, () -> new SubseaSeparationStation("x", null));
    SubseaSeparationStation sss = new SubseaSeparationStation("SSS", wellStream());
    assertThrows(IllegalArgumentException.class, () -> sss.setWaterRemovalEfficiency(1.2));
    assertThrows(IllegalArgumentException.class, () -> sss.setPumpEfficiency(0.0));
    assertTrue(sss.validateSetup().isValid());
  }
}
