package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Benchmark test for CPA CO2-water mutual solubility.
 *
 * <p>
 * Documents the current CPA BIP performance for CO2-water at various temperatures. Reference data
 * from Wiebe &amp; Gaddy (1940), King et al. (1992), and Duan &amp; Sun (2003).
 * </p>
 *
 * <p>
 * Current CPA parameters (INTER.csv):
 * cpakij_SRK = -0.27686, cpakijT_SRK = 0.001121, cpaBetaCross = 0.075, cpaEpsCross = 0.
 * </p>
 *
 * @author Copilot
 * @version 1.0
 */
class CPACO2WaterBenchmarkTest extends neqsim.NeqSimTest {
  /** Logger instance. */
  private static final Logger logger = LogManager.getLogger(CPACO2WaterBenchmarkTest.class);

  /**
   * Test CO2 solubility in water at 25C and various pressures.
   *
   * <p>
   * Reference: Wiebe &amp; Gaddy (1940) — CO2 mole fraction in liquid at 25C.
   * At 100 bar: x_CO2 ~ 0.023; at 50 bar: x_CO2 ~ 0.019.
   * </p>
   */
  @Test
  void testCO2SolubilityInWater25C() {
    double temperature = 273.15 + 25.0;
    double pressure = 100.0;

    SystemInterface system = new SystemSrkCPAstatoil(temperature, pressure);
    system.addComponent("CO2", 0.3);
    system.addComponent("water", 0.7);
    system.setMixingRule(10);
    system.setMultiPhaseCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();

    double co2InLiquid = 0.0;
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      if (system.getPhase(i).getType().toString().toLowerCase().contains("aqueous")
          || system.getPhase(i).getType().toString().toLowerCase().contains("liquid")) {
        if (system.getPhase(i).hasComponent("CO2")) {
          co2InLiquid = system.getPhase(i).getComponent("CO2").getx();
        }
        break;
      }
    }

    logger.info("CO2 solubility in water at 25C, 100 bar: x_CO2 = " + co2InLiquid);
    logger.info("Reference (Wiebe & Gaddy 1940): x_CO2 ~ 0.023");

    // Accept wide range to document current behavior
    assertTrue(co2InLiquid > 0.005,
        "CO2 solubility should be > 0.005 at 25C, 100 bar but was " + co2InLiquid);
    assertTrue(co2InLiquid < 0.05,
        "CO2 solubility should be < 0.05 at 25C, 100 bar but was " + co2InLiquid);
  }

  /**
   * Test CO2 solubility in water at 60C and 100 bar.
   *
   * <p>
   * Reference: x_CO2 ~ 0.017 at 60C, 100 bar.
   * This is the condition where current BIPs are known to under-predict.
   * </p>
   */
  @Test
  void testCO2SolubilityInWater60C() {
    double temperature = 273.15 + 60.0;
    double pressure = 100.0;

    SystemInterface system = new SystemSrkCPAstatoil(temperature, pressure);
    system.addComponent("CO2", 0.3);
    system.addComponent("water", 0.7);
    system.setMixingRule(10);
    system.setMultiPhaseCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();

    double co2InLiquid = 0.0;
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      String phaseType = system.getPhase(i).getType().toString().toLowerCase();
      if (phaseType.contains("aqueous") || phaseType.contains("liquid")) {
        if (system.getPhase(i).hasComponent("CO2")) {
          co2InLiquid = system.getPhase(i).getComponent("CO2").getx();
        }
        break;
      }
    }

    logger.info("CO2 solubility in water at 60C, 100 bar: x_CO2 = " + co2InLiquid);
    logger.info("Reference: x_CO2 ~ 0.017");

    // Document current behavior — known to under-predict at elevated T
    assertTrue(co2InLiquid > 0.003,
        "CO2 solubility should be > 0.003 at 60C, 100 bar but was " + co2InLiquid);
    assertTrue(co2InLiquid < 0.04,
        "CO2 solubility should be < 0.04 at 60C, 100 bar but was " + co2InLiquid);
  }

  /**
   * Test water content in CO2-rich phase at 25C, 100 bar.
   *
   * <p>
   * Reference: y_H2O ~ 0.002-0.005 at 25C, 100 bar.
   * </p>
   */
  @Test
  void testWaterInCO2Phase25C() {
    double temperature = 273.15 + 25.0;
    double pressure = 100.0;

    SystemInterface system = new SystemSrkCPAstatoil(temperature, pressure);
    system.addComponent("CO2", 0.7);
    system.addComponent("water", 0.3);
    system.setMixingRule(10);
    system.setMultiPhaseCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();

    double waterInCO2 = 0.0;
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      String phaseType = system.getPhase(i).getType().toString().toLowerCase();
      if (phaseType.contains("gas") || phaseType.contains("oil")) {
        if (system.getPhase(i).hasComponent("water")) {
          waterInCO2 = system.getPhase(i).getComponent("water").getx();
        }
        break;
      }
    }

    logger.info("Water in CO2-rich phase at 25C, 100 bar: y_H2O = " + waterInCO2);
    logger.info("Reference: y_H2O ~ 0.002-0.005");

    assertTrue(waterInCO2 > 0.0001,
        "Water in CO2 should be > 0.0001 at 25C, 100 bar but was " + waterInCO2);
    assertTrue(waterInCO2 < 0.02,
        "Water in CO2 should be < 0.02 at 25C, 100 bar but was " + waterInCO2);
  }
}
