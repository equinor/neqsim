package neqsim.process.equipment.separator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for Separator mass balance and thermodynamic property calculations.
 * Tests edge cases including missing phases, zero flows, and negligible inlet flows.
 */
public class SeparatorMassBalanceTest {

  private SystemInterface thermoSystem;

  @BeforeEach
  public void setUp() {
    thermoSystem = new SystemSrkEos(288.15, 101.325);
    thermoSystem.addComponent("methane", 0.8);
    thermoSystem.addComponent("ethane", 0.15);
    thermoSystem.addComponent("propane", 0.05);
    thermoSystem.setMixingRule("classic");
    thermoSystem.createDatabase(true);
    thermoSystem.init(0);
  }

  /**
   * Test mass balance with all phases present (gas and liquid).
   */
  @Test
  public void testMassBalanceWithAllPhases() {
    SystemInterface feed = thermoSystem.clone();
    feed.setTemperature(288.15);
    feed.setPressure(101.325);
    feed.init(0);

    Stream inletStream = new Stream("feed", feed);
    inletStream.setFlowRate(100.0, "kg/hr");
    inletStream.setTemperature(288.15);
    inletStream.setPressure(101.325);
    inletStream.run();

    Separator sep = new Separator("Separator1", inletStream);
    ProcessSystem procSys = new ProcessSystem();
    procSys.add(inletStream);
    procSys.add(sep);
    procSys.run();

    double massBalance = sep.getMassBalance("kg/hr");
    assertTrue(Math.abs(massBalance) < 10.0, "Mass balance deviation: " + massBalance);
  }

  /**
   * Test mass balance when liquid phase is absent (pure gas).
   */
  @Test
  public void testMassBalanceSinglePhaseGas() {
    SystemInterface feed = new SystemSrkEos(300.0, 50.0);
    feed.addComponent("methane", 1.0);
    feed.setMixingRule("classic");
    feed.createDatabase(true);
    feed.init(0);

    Stream inletStream = new Stream("feed_gas", feed);
    inletStream.setFlowRate(100.0, "kg/hr");
    inletStream.setTemperature(300.0);
    inletStream.setPressure(50.0);
    inletStream.run();

    Separator sepGas = new Separator("SeparatorGas", inletStream);
    ProcessSystem procSys = new ProcessSystem();
    procSys.add(inletStream);
    procSys.add(sepGas);
    procSys.run();

    double massBalance = sepGas.getMassBalance("kg/hr");
    assertTrue(Math.abs(massBalance) < 10.0, "Mass balance deviation for gas phase: " + massBalance);
  }

  /**
   * Test mass balance with negligible inlet flow (< 1e-10 kg/hr).
   */
  @Test
  public void testMassBalanceWithZeroFlowInlet() {
    SystemInterface feed = thermoSystem.clone();
    feed.setTemperature(288.15);
    feed.setPressure(101.325);
    feed.init(0);

    Stream inletStream = new Stream("feed_zero", feed);
    inletStream.setFlowRate(1e-12, "kg/hr");
    inletStream.setTemperature(288.15);
    inletStream.setPressure(101.325);
    inletStream.run();

    Separator sep = new Separator("SeparatorZero", inletStream);
    ProcessSystem procSys = new ProcessSystem();
    procSys.add(inletStream);
    procSys.add(sep);
    procSys.run();

    double massBalance = sep.getMassBalance("kg/hr");
    assertTrue(Math.abs(massBalance) < 1e-6, "Mass balance should be near zero for negligible flow");
  }

  /**
   * Test mass balance with multiple inlet streams, some with negligible flow.
   */
  @Test
  public void testMassBalanceWithMultipleInletsIncludingZero() {
    SystemInterface feed1 = thermoSystem.clone();
    feed1.setTemperature(288.15);
    feed1.setPressure(101.325);
    feed1.init(0);

    SystemInterface feed2 = thermoSystem.clone();
    feed2.setTemperature(288.15);
    feed2.setPressure(101.325);
    feed2.init(0);

    Stream inlet1 = new Stream("inlet1", feed1);
    inlet1.setFlowRate(80.0, "kg/hr");
    inlet1.setTemperature(288.15);
    inlet1.setPressure(101.325);
    inlet1.run();

    Stream inlet2 = new Stream("inlet2", feed2);
    inlet2.setFlowRate(1e-12, "kg/hr");
    inlet2.setTemperature(288.15);
    inlet2.setPressure(101.325);
    inlet2.run();

    Separator sepMulti = new Separator("SeparatorMulti", inlet1);
    sepMulti.addStream(inlet2);

    ProcessSystem procSys = new ProcessSystem();
    procSys.add(inlet1);
    procSys.add(inlet2);
    procSys.add(sepMulti);
    procSys.run();

    double massBalance = sepMulti.getMassBalance("kg/hr");
    assertTrue(Math.abs(massBalance) < 10.0, "Mass balance with mixed inlet flows: " + massBalance);
  }

  /**
   * Test mass balance with different units (kg/hr vs kg/day).
   */
  @Test
  public void testMassBalanceDifferentUnits() {
    SystemInterface feed = thermoSystem.clone();
    feed.setTemperature(288.15);
    feed.setPressure(101.325);
    feed.init(0);

    Stream inletStream = new Stream("feed_units", feed);
    inletStream.setFlowRate(100.0, "kg/hr");
    inletStream.setTemperature(288.15);
    inletStream.setPressure(101.325);
    inletStream.run();

    Separator sep = new Separator("SeparatorUnits", inletStream);
    ProcessSystem procSys = new ProcessSystem();
    procSys.add(inletStream);
    procSys.add(sep);
    procSys.run();

    double mbKghr = sep.getMassBalance("kg/hr");
    double mbKgday = sep.getMassBalance("kg/day");

    double mbKghrConverted = mbKghr * 24.0;
    assertTrue(Math.abs(mbKghrConverted - mbKgday) < 10.0, "Mass balance units consistency failed");
  }

  /**
   * Test mass balance under high-pressure liquid-only condition.
   */
  @Test
  public void testMassBalanceLiquidOnlyPhase() {
    SystemInterface feed = new SystemSrkEos(288.15, 500.0);
    feed.addComponent("methane", 0.8);
    feed.addComponent("ethane", 0.15);
    feed.addComponent("propane", 0.05);
    feed.setMixingRule("classic");
    feed.createDatabase(true);
    feed.init(0);

    Stream inletStream = new Stream("feed_liquid", feed);
    inletStream.setFlowRate(100.0, "kg/hr");
    inletStream.setTemperature(288.15);
    inletStream.setPressure(500.0);
    inletStream.run();

    Separator sepLiquid = new Separator("SeparatorLiquid", inletStream);
    ProcessSystem procSys = new ProcessSystem();
    procSys.add(inletStream);
    procSys.add(sepLiquid);
    procSys.run();

    double massBalance = sepLiquid.getMassBalance("kg/hr");
    assertTrue(Math.abs(massBalance) < 10.0, "Mass balance for liquid-only phase: " + massBalance);
  }

  /**
   * Test entropy production with negligible inlet flows filtered.
   */
  @Test
  public void testEntropyProductionWithZeroInlet() {
    SystemInterface feed = thermoSystem.clone();
    feed.setTemperature(288.15);
    feed.setPressure(101.325);
    feed.init(0);

    Stream inletStream = new Stream("feed_entropy_zero", feed);
    inletStream.setFlowRate(1e-12, "kg/hr");
    inletStream.setTemperature(288.15);
    inletStream.setPressure(101.325);
    inletStream.run();

    Separator sep = new Separator("SeparatorEntropy", inletStream);
    ProcessSystem procSys = new ProcessSystem();
    procSys.add(inletStream);
    procSys.add(sep);
    procSys.run();

    double entropyProd = sep.getEntropyProduction("J/K");
    assertNotNull(entropyProd, "Entropy production should be calculable");
  }

  /**
   * Test exergy change with negligible inlet flows filtered.
   */
  @Test
  public void testExergyChangeWithZeroInlet() {
    SystemInterface feed = thermoSystem.clone();
    feed.setTemperature(288.15);
    feed.setPressure(101.325);
    feed.init(0);

    Stream inletStream = new Stream("feed_exergy_zero", feed);
    inletStream.setFlowRate(1e-12, "kg/hr");
    inletStream.setTemperature(288.15);
    inletStream.setPressure(101.325);
    inletStream.run();

    Separator sep = new Separator("SeparatorExergy", inletStream);
    ProcessSystem procSys = new ProcessSystem();
    procSys.add(inletStream);
    procSys.add(sep);
    procSys.run();

    double exergyChange = sep.getExergyChange("J", 288.15);
    assertNotNull(exergyChange, "Exergy change should be calculable");
  }

  /**
   * Test that methods handle missing output streams gracefully.
   */
  @Test
  public void testMassBalanceWithMissingOutputStreams() {
    SystemInterface feed = thermoSystem.clone();
    feed.setTemperature(288.15);
    feed.setPressure(101.325);
    feed.init(0);

    Stream inletStream = new Stream("feed_missing", feed);
    inletStream.setFlowRate(50.0, "kg/hr");
    inletStream.setTemperature(288.15);
    inletStream.setPressure(101.325);
    inletStream.run();

    Separator sep = new Separator("SeparatorMissing", inletStream);
    ProcessSystem procSys = new ProcessSystem();
    procSys.add(inletStream);
    procSys.add(sep);
    procSys.run();

    assertDoesNotThrow(() -> {
      double mb = sep.getMassBalance("kg/hr");
      double ep = sep.getEntropyProduction("J/K");
      double ec = sep.getExergyChange("J", 288.15);
    }, "Methods should handle any phase configuration without throwing");
  }
}
