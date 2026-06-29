package neqsim.process.mechanicaldesign.pump;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PumpHydraulicsNpshCalculator}.
 */
public class PumpHydraulicsNpshCalculatorTest {
  /**
   * A flooded suction with low vapour pressure should have ample NPSH margin and no cavitation.
   */
  @Test
  void testHealthySuction() {
    PumpHydraulicsNpshCalculator calc = new PumpHydraulicsNpshCalculator();
    calc.setDutyPoint(100.0, 80.0, 850.0, 0.72);
    calc.setSuctionConditions(5.0, 1.0, 3.0, 0.5, 3.0, 1.0);
    calc.calcHydraulics();

    assertTrue(calc.getNpshAvailable() > 0.0, "NPSHa should be positive");
    assertTrue(calc.getNpshMargin() > 1.0, "Margin should exceed the threshold");
    assertTrue(!calc.isCavitationRisk(), "No cavitation risk expected");
    assertNotNull(calc.toJson());
  }

  /**
   * A suction near the vapour pressure should flag cavitation risk.
   */
  @Test
  void testCavitationRisk() {
    PumpHydraulicsNpshCalculator calc = new PumpHydraulicsNpshCalculator();
    calc.setDutyPoint(100.0, 80.0, 850.0, 0.72);
    calc.setSuctionConditions(1.05, 1.0, 0.5, 1.0, 3.0, 1.0);
    calc.calcHydraulics();

    assertTrue(calc.getNpshMargin() < 1.0, "Margin should be below the threshold");
    assertTrue(calc.isCavitationRisk(), "Cavitation risk should be flagged");
  }

  /**
   * Brake power should exceed hydraulic power and scale with head.
   */
  @Test
  void testPowerCalculation() {
    PumpHydraulicsNpshCalculator calc = new PumpHydraulicsNpshCalculator();
    calc.setDutyPoint(120.0, 100.0, 900.0, 0.70);
    calc.setSuctionConditions(4.0, 1.0, 2.0, 0.5, 3.0, 1.0);
    calc.calcHydraulics();

    assertTrue(calc.getHydraulicPower() > 0.0, "Hydraulic power should be positive");
    assertTrue(calc.getBrakePower() > calc.getHydraulicPower(),
        "Brake power should exceed hydraulic power");
  }

  /**
   * The {@code fromPump} bridge should populate density, flow, head and suction pressure from a run
   * pump.
   */
  @Test
  void testFromProcessPump() {
    neqsim.thermo.system.SystemSrkEos fluid = new neqsim.thermo.system.SystemSrkEos(298.15, 3.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule("classic");
    neqsim.process.equipment.stream.Stream feed =
        new neqsim.process.equipment.stream.Stream("feed", fluid);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(3.0, "bara");
    neqsim.process.equipment.pump.Pump pump = new neqsim.process.equipment.pump.Pump("pump", feed);
    pump.setOutletPressure(15.0, "bara");
    neqsim.process.processmodel.ProcessSystem process =
        new neqsim.process.processmodel.ProcessSystem();
    process.add(feed);
    process.add(pump);
    process.run();
    feed.getFluid().initProperties();

    PumpHydraulicsNpshCalculator calc = new PumpHydraulicsNpshCalculator();
    calc.fromPump(pump);
    calc.calcHydraulics();

    assertTrue(calc.getHydraulicPower() > 0.0,
        "Hydraulic power should be positive for a pressure-raising pump");
    assertTrue(calc.getBrakePower() > 0.0,
        "Brake power should be positive for a pressure-raising pump");
    assertNotNull(calc.toJson());
  }
}
