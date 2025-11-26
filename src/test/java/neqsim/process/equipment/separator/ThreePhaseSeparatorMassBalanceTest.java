package neqsim.process.equipment.separator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for ThreePhaseSeparator getMassBalance method to verify it handles edge cases
 * correctly, including missing phases and zero flow streams.
 */
public class ThreePhaseSeparatorMassBalanceTest {
  static SystemInterface testSystem;

  @BeforeAll
  static void setUpBeforeClass() {
    testSystem = new SystemSrkEos(298.15, 10.0);
    testSystem.addComponent("water", 0.1);
    testSystem.addComponent("methane", 0.7);
    testSystem.addComponent("ethane", 0.1);
    testSystem.addComponent("propane", 0.1);
    testSystem.setMixingRule("classic");
    testSystem.setMultiPhaseCheck(true);
  }

  /**
   * Test mass balance with normal three-phase split where all phases are present.
   */
  @Test
  void testMassBalanceWithAllPhases() {
    SystemInterface sys = testSystem.clone();
    Stream feedStream = new Stream("feed", sys);
    feedStream.setFlowRate(100.0, "kg/hr");
    feedStream.setTemperature(25.0, "C");
    feedStream.setPressure(10.0, "bara");
    feedStream.run();

    ThreePhaseSeparator sep = new ThreePhaseSeparator("three-phase sep", feedStream);
    sep.run();

    double massBalance = sep.getMassBalance("kg/hr");
    // Mass balance should be close to zero (inlet = outlet)
    assertTrue(Math.abs(massBalance) < 1.0,
        "Mass balance should be near zero, got: " + massBalance);
  }

  /**
   * Test mass balance when one or more phases don't exist (e.g., no aqueous phase in pure gas).
   */
  @Test
  void testMassBalanceWithMissingPhase() {
    // Create a system with only hydrocarbon components (no water)
    SystemInterface sysNoWater = new SystemSrkEos(298.15, 10.0);
    sysNoWater.addComponent("methane", 0.6);
    sysNoWater.addComponent("ethane", 0.4);
    sysNoWater.setMixingRule("classic");
    sysNoWater.setMultiPhaseCheck(true);

    Stream feedStream = new Stream("feed", sysNoWater);
    feedStream.setFlowRate(50.0, "kg/hr");
    feedStream.setTemperature(25.0, "C");
    feedStream.setPressure(10.0, "bara");
    feedStream.run();

    ThreePhaseSeparator sep = new ThreePhaseSeparator("sep no aqueous", feedStream);
    sep.run();

    double massBalance = sep.getMassBalance("kg/hr");
    // Even with missing aqueous phase, mass balance should be acceptable
    assertTrue(Math.abs(massBalance) < 1.0,
        "Mass balance should be near zero even with missing phase, got: " + massBalance);
  }

  /**
   * Test mass balance with single phase system (all gas).
   */
  @Test
  void testMassBalanceSinglePhaseGas() {
    // Create a pure gas system at conditions where it stays single phase
    SystemInterface gasSys = new SystemSrkEos(298.15, 2.0);
    gasSys.addComponent("methane", 1.0);
    gasSys.setMixingRule("classic");
    gasSys.setMultiPhaseCheck(true);

    Stream feedStream = new Stream("feed", gasSys);
    feedStream.setFlowRate(100.0, "kg/hr");
    feedStream.setTemperature(25.0, "C");
    feedStream.setPressure(2.0, "bara");
    feedStream.run();

    ThreePhaseSeparator sep = new ThreePhaseSeparator("single phase sep", feedStream);
    sep.run();

    double massBalance = sep.getMassBalance("kg/hr");
    // Single phase - all should go to gas outlet
    assertTrue(Math.abs(massBalance) < 1.0,
        "Single phase gas mass balance should be near zero, got: " + massBalance);
  }

  /**
   * Test mass balance with zero flow inlet stream - should not crash.
   */
  @Test
  void testMassBalanceWithZeroFlowInlet() {
    SystemInterface sys = testSystem.clone();
    Stream feedStream = new Stream("feed", sys);
    feedStream.setFlowRate(1e-12, "kg/hr"); // Negligible flow
    feedStream.setTemperature(25.0, "C");
    feedStream.setPressure(10.0, "bara");
    feedStream.run();

    ThreePhaseSeparator sep = new ThreePhaseSeparator("sep zero flow", feedStream);
    sep.run();

    // Should not throw exception
    double massBalance = sep.getMassBalance("kg/hr");
    assertTrue(!Double.isNaN(massBalance) && !Double.isInfinite(massBalance),
        "Mass balance should be a valid number even with zero flow");
  }

  /**
   * Test mass balance with multiple inlet streams, some with zero flow.
   */
  @Test
  void testMassBalanceWithMultipleInletsIncludingZero() {
    SystemInterface sys1 = testSystem.clone();
    SystemInterface sys2 = testSystem.clone();

    Stream feed1 = new Stream("feed1", sys1);
    feed1.setFlowRate(80.0, "kg/hr");
    feed1.setTemperature(25.0, "C");
    feed1.setPressure(10.0, "bara");
    feed1.run();

    Stream feed2 = new Stream("feed2", sys2);
    feed2.setFlowRate(1e-12, "kg/hr"); // Negligible
    feed2.setTemperature(25.0, "C");
    feed2.setPressure(10.0, "bara");
    feed2.run();

    ThreePhaseSeparator sep = new ThreePhaseSeparator("multi inlet sep");
    sep.addStream(feed1);
    sep.addStream(feed2);
    sep.run();

    double massBalance = sep.getMassBalance("kg/hr");
    // Should account for non-negligible flow (80 kg/hr) and filter out negligible flow
    assertTrue(Math.abs(massBalance) < 1.0,
        "Mass balance with multiple inlets should be near zero, got: " + massBalance);
  }

  /**
   * Test mass balance in different units.
   */
  @Test
  void testMassBalanceDifferentUnits() {
    SystemInterface sys = testSystem.clone();
    Stream feedStream = new Stream("feed", sys);
    feedStream.setFlowRate(100.0, "kg/hr");
    feedStream.setTemperature(25.0, "C");
    feedStream.setPressure(10.0, "bara");
    feedStream.run();

    ThreePhaseSeparator sep = new ThreePhaseSeparator("sep units", feedStream);
    sep.run();

    double massBalanceKgHr = sep.getMassBalance("kg/hr");
    double massBalanceKgDay = sep.getMassBalance("kg/day");

    // Conversions should be consistent (24 hours per day)
    double kgDayFromKgHr = massBalanceKgHr * 24.0;
    assertEquals(massBalanceKgDay, kgDayFromKgHr, 0.01,
        "Mass balance units should convert correctly");
  }

  /**
   * Test mass balance with high pressure (only liquid phase).
   */
  @Test
  void testMassBalanceLiquidOnlyPhase() {
    // At very high pressure, system should be nearly all liquid
    SystemInterface sys = testSystem.clone();
    Stream feedStream = new Stream("feed", sys);
    feedStream.setFlowRate(100.0, "kg/hr");
    feedStream.setTemperature(50.0, "C");
    feedStream.setPressure(500.0, "bara");
    feedStream.run();

    ThreePhaseSeparator sep = new ThreePhaseSeparator("high pressure sep", feedStream);
    sep.run();

    double massBalance = sep.getMassBalance("kg/hr");
    assertTrue(Math.abs(massBalance) < 1.0,
        "High pressure (liquid only) mass balance should be near zero, got: " + massBalance);
  }
}
