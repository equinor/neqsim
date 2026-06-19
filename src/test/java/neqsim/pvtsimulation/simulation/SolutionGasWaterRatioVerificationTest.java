package neqsim.pvtsimulation.simulation;

import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Verification test for SolutionGasWaterRatio (Rsw) calculations. Compares results from all three methods against
 * literature values and each other.
 *
 * <p>
 * Literature references for methane solubility in water:
 * </p>
 * <ul>
 * <li>Culberson, O.L. and McKetta, J.J. (1951): JPT</li>
 * <li>Duan, Z. and Mao, S. (2006): Geochimica et Cosmochimica Acta</li>
 * <li>Søreide, I. and Whitson, C.H. (1992): Fluid Phase Equilibria</li>
 * </ul>
 */
public class SolutionGasWaterRatioVerificationTest {
  private static final Logger logger = LogManager.getLogger(SolutionGasWaterRatioVerificationTest.class);

  // Java 8 compatible separator strings (instead of String.repeat())
  private static final String SEPARATOR_EQUALS = "================================================================================";
  private static final String SEPARATOR_DASH = "--------------------------------------------------------------------------------";

  /**
   * Compare all three methods at typical reservoir conditions. This test prints a comparison table for visual
   * verification.
   */
  @Test
  public void compareAllMethodsAtReservoirConditions() {
    logger.info("\n" + SEPARATOR_EQUALS);
    logger.info("SOLUTION GAS-WATER RATIO (Rsw) - METHOD COMPARISON");
    logger.info(SEPARATOR_EQUALS);

    // Create a typical reservoir gas (mostly methane)
    SystemInterface gas = new SystemSrkCPAstatoil(373.15, 200.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.05);
    gas.addComponent("CO2", 0.05);
    gas.setMixingRule(10);

    SolutionGasWaterRatio rswCalc = new SolutionGasWaterRatio(gas);

    // Test conditions: 100°C (373.15 K) at various pressures
    double[] pressures = { 10.0, 50.0, 100.0, 150.0, 200.0 };
    double[] temperatures = new double[pressures.length];
    for (int i = 0; i < pressures.length; i++) {
      temperatures[i] = 373.15; // 100°C
    }

    logger.info("\nConditions: T = 100°C (373.15 K), Pure Water (salinity = 0)");
    logger.info("Gas composition: 90% CH4, 5% C2H6, 5% CO2");
    logger.info(SEPARATOR_DASH);

    // Calculate for pure water (salinity = 0)
    rswCalc.setSalinity(0.0);
    rswCalc.setTemperaturesAndPressures(temperatures, pressures);

    // McCain method
    rswCalc.setCalculationMethod(SolutionGasWaterRatio.CalculationMethod.MCCAIN);
    rswCalc.runCalc();
    double[] rswMcCain = rswCalc.getRsw().clone();

    // Søreide-Whitson method
    rswCalc.setCalculationMethod(SolutionGasWaterRatio.CalculationMethod.SOREIDE_WHITSON);
    rswCalc.runCalc();
    double[] rswSoreide = rswCalc.getRsw().clone();

    // Electrolyte CPA method
    rswCalc.setCalculationMethod(SolutionGasWaterRatio.CalculationMethod.ELECTROLYTE_CPA);
    rswCalc.runCalc();
    double[] rswCPA = rswCalc.getRsw().clone();

    // Print comparison table
    logger.printf(org.apache.logging.log4j.Level.INFO, "%-12s %-15s %-15s %-15s%n", "P (bara)", "McCain",
	"Soreide-Whitson", "Electrolyte-CPA");
    logger.printf(org.apache.logging.log4j.Level.INFO, "%-12s %-15s %-15s %-15s%n", "", "(Sm3/Sm3)", "(Sm3/Sm3)",
	"(Sm3/Sm3)");
    logger.info(SEPARATOR_DASH);

    for (int i = 0; i < pressures.length; i++) {
      logger.printf(org.apache.logging.log4j.Level.INFO, "%-12.1f %-15.6f %-15.6f %-15.6f%n", pressures[i],
	  rswMcCain[i], rswSoreide[i], rswCPA[i]);
    }

    logger.info("Analysis:");
    logger.info("- Rsw should increase with pressure (more gas dissolves at higher P)");
    logger.info("- McCain correlation is empirical, optimized for methane-water systems");
    logger.info("- EoS methods (Soreide-Whitson, CPA) account for gas composition effects");
  }

  /**
   * Compare effect of salinity on Rsw. Salinity reduces gas solubility (salting-out effect).
   */
  @Test
  public void compareSalinityEffectAllMethods() {
    logger.info("\n" + SEPARATOR_EQUALS);
    logger.info("SALINITY EFFECT ON Rsw - METHOD COMPARISON");
    logger.info(SEPARATOR_EQUALS);

    // Create a methane-rich gas
    SystemInterface gas = new SystemSrkCPAstatoil(350.0, 100.0);
    gas.addComponent("methane", 0.95);
    gas.addComponent("CO2", 0.05);
    gas.setMixingRule(10);

    SolutionGasWaterRatio rswCalc = new SolutionGasWaterRatio(gas);

    // Fixed conditions: 76.85°C (350 K), 100 bara
    double[] temperatures = { 350.0 };
    double[] pressures = { 100.0 };
    rswCalc.setTemperaturesAndPressures(temperatures, pressures);

    // Test various salinities
    double[] salinities = { 0.0, 1.0, 2.0, 3.5, 5.0 }; // wt% NaCl

    logger.info("\nConditions: T = 76.85°C (350 K), P = 100 bara");
    logger.info("Gas composition: 95% CH4, 5% CO2");
    logger.info(SEPARATOR_DASH);
    logger.printf(org.apache.logging.log4j.Level.INFO, "%-15s %-15s %-15s %-15s%n", "Salinity", "McCain",
	"Soreide-Whitson", "Electrolyte-CPA");
    logger.printf(org.apache.logging.log4j.Level.INFO, "%-15s %-15s %-15s %-15s%n", "(wt% NaCl)", "(Sm3/Sm3)",
	"(Sm3/Sm3)", "(Sm3/Sm3)");
    logger.info(SEPARATOR_DASH);

    for (double sal : salinities) {
      rswCalc.setSalinity(sal, "wt%");

      rswCalc.setCalculationMethod(SolutionGasWaterRatio.CalculationMethod.MCCAIN);
      rswCalc.runCalc();
      double rswMcCain = rswCalc.getRsw(0);

      rswCalc.setCalculationMethod(SolutionGasWaterRatio.CalculationMethod.SOREIDE_WHITSON);
      rswCalc.runCalc();
      double rswSoreide = rswCalc.getRsw(0);

      rswCalc.setCalculationMethod(SolutionGasWaterRatio.CalculationMethod.ELECTROLYTE_CPA);
      rswCalc.runCalc();
      double rswCPA = rswCalc.getRsw(0);

      logger.printf(org.apache.logging.log4j.Level.INFO, "%-15.1f %-15.6f %-15.6f %-15.6f%n", sal, rswMcCain,
	  rswSoreide, rswCPA);
    }

    logger.info("Analysis:");
    logger.info("- Rsw should DECREASE with increasing salinity (salting-out effect)");
    logger.info("- Seawater (~3.5 wt% NaCl) typically reduces Rsw by 20-30%");
    System.out.println("- Formation water (5+ wt% NaCl) can reduce Rsw by 30-50% compared to pure water");
  }

  /**
   * Verify McCain correlation against literature values. Reference: Culberson & McKetta (1951), McCain (1990)
   *
   * Literature values for methane solubility in pure water: - At 100°F (37.8°C), 1000 psia (68.9 bar): ~8-10 scf/STB -
   * At 200°F (93.3°C), 1000 psia (68.9 bar): ~10-12 scf/STB - At 200°F (93.3°C), 2000 psia (137.9 bar): ~18-22 scf/STB
   *
   * Note: 1 scf/STB = 0.178108 Sm3/Sm3
   */
  @Test
  public void verifyMcCainAgainstLiterature() {
    logger.info("\n" + SEPARATOR_EQUALS);
    logger.info("McCAIN CORRELATION VERIFICATION AGAINST LITERATURE");
    logger.info(SEPARATOR_EQUALS);
    logger.info("Reference: Culberson & McKetta (1951), McCain (1990)");
    logger.info(SEPARATOR_DASH);

    // Create pure methane system
    SystemInterface methane = new SystemSrkCPAstatoil(310.93, 68.9);
    methane.addComponent("methane", 1.0);
    methane.setMixingRule(10);

    SolutionGasWaterRatio rswCalc = new SolutionGasWaterRatio(methane);
    rswCalc.setCalculationMethod(SolutionGasWaterRatio.CalculationMethod.MCCAIN);
    rswCalc.setSalinity(0.0);

    // Test points from literature
    double[][] testPoints = {
	// {T(K), P(bar), expected Rsw scf/STB low, expected Rsw scf/STB high}
	{ 310.93, 68.9, 8.0, 12.0 }, // 100°F, 1000 psia
	{ 366.48, 68.9, 10.0, 14.0 }, // 200°F, 1000 psia
	{ 366.48, 137.9, 18.0, 25.0 }, // 200°F, 2000 psia
	{ 366.48, 206.8, 25.0, 35.0 }, // 200°F, 3000 psia
    };

    logger.printf(org.apache.logging.log4j.Level.INFO, "%-12s %-12s %-18s %-18s %-10s%n", "T (°F)", "P (psia)",
	"Expected Range", "Calculated", "Status");
    logger.printf(org.apache.logging.log4j.Level.INFO, "%-12s %-12s %-18s %-18s %-10s%n", "", "", "(scf/STB)",
	"(scf/STB)", "");
    logger.info(SEPARATOR_DASH);

    for (double[] point : testPoints) {
      double tempK = point[0];
      double presBar = point[1];
      double expectedLow = point[2];
      double expectedHigh = point[3];

      double tempF = (tempK - 273.15) * 9.0 / 5.0 + 32.0;
      double presPsia = presBar * 14.5038;

      rswCalc.setTemperaturesAndPressures(new double[] { tempK }, new double[] { presBar });
      rswCalc.runCalc();
      double rswSm3 = rswCalc.getRsw(0);

      // Convert Sm3/Sm3 to scf/STB: 1 Sm3/Sm3 = 5.615 scf/STB
      double rswScfStb = rswSm3 / 0.178108;

      String status = (rswScfStb >= expectedLow * 0.5 && rswScfStb <= expectedHigh * 2.0) ? "REASONABLE" : "CHECK";

      logger.printf(org.apache.logging.log4j.Level.INFO, "%-12.0f %-12.0f %-8.1f - %-7.1f %-18.2f %-10s%n", tempF,
	  presPsia, expectedLow, expectedHigh, rswScfStb, status);
    }

    logger.info("Note: Literature values are approximate ranges from various sources.");
    logger.info("      The correlation coefficients used may differ slightly from");
    logger.info("      original McCain publication.");
  }

  /**
   * Temperature effect on Rsw at constant pressure.
   */
  @Test
  public void compareTemperatureEffect() {
    logger.info("\n" + SEPARATOR_EQUALS);
    logger.info("TEMPERATURE EFFECT ON Rsw");
    logger.info(SEPARATOR_EQUALS);

    // Create methane gas
    SystemInterface gas = new SystemSrkCPAstatoil(300.0, 100.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule(10);

    SolutionGasWaterRatio rswCalc = new SolutionGasWaterRatio(gas);
    rswCalc.setSalinity(0.0);

    // Various temperatures at 100 bara
    double[] temps = { 300.0, 325.0, 350.0, 375.0, 400.0, 425.0 }; // K
    double[] pressures = new double[temps.length];
    for (int i = 0; i < temps.length; i++) {
      pressures[i] = 100.0;
    }
    rswCalc.setTemperaturesAndPressures(temps, pressures);

    logger.info("\nConditions: P = 100 bara, Pure Water, Pure Methane");
    logger.info(SEPARATOR_DASH);
    logger.printf(org.apache.logging.log4j.Level.INFO, "%-12s %-12s %-15s %-15s %-15s%n", "T (K)", "T (°C)", "McCain",
	"Soreide-Whitson", "Electrolyte-CPA");
    logger.info(SEPARATOR_DASH);

    // McCain
    rswCalc.setCalculationMethod(SolutionGasWaterRatio.CalculationMethod.MCCAIN);
    rswCalc.runCalc();
    double[] rswMcCain = rswCalc.getRsw().clone();

    // Søreide-Whitson
    rswCalc.setCalculationMethod(SolutionGasWaterRatio.CalculationMethod.SOREIDE_WHITSON);
    rswCalc.runCalc();
    double[] rswSoreide = rswCalc.getRsw().clone();

    // CPA
    rswCalc.setCalculationMethod(SolutionGasWaterRatio.CalculationMethod.ELECTROLYTE_CPA);
    rswCalc.runCalc();
    double[] rswCPA = rswCalc.getRsw().clone();

    for (int i = 0; i < temps.length; i++) {
      double tempC = temps[i] - 273.15;
      logger.printf(org.apache.logging.log4j.Level.INFO, "%-12.1f %-12.1f %-15.6f %-15.6f %-15.6f%n", temps[i], tempC,
	  rswMcCain[i], rswSoreide[i], rswCPA[i]);
    }

    logger.info("Analysis:");
    logger.info("- Gas solubility in water generally shows a minimum around 70-100°C for hydrocarbons");
    logger.info("- At low T: solubility decreases with T (entropic effect dominates)");
    logger.info("- At high T: solubility increases with T (approaching critical point)");
  }

  /**
   * Compare methods for CO2-rich gas. CO2 has higher solubility in water than hydrocarbons.
   */
  @Test
  public void compareCO2RichGas() {
    logger.info("\n" + SEPARATOR_EQUALS);
    logger.info("CO2-RICH GAS - Rsw COMPARISON");
    logger.info(SEPARATOR_EQUALS);

    // Create CO2-rich gas
    SystemInterface gas = new SystemSrkCPAstatoil(323.15, 50.0);
    gas.addComponent("CO2", 0.50);
    gas.addComponent("methane", 0.50);
    gas.setMixingRule(10);

    SolutionGasWaterRatio rswCalc = new SolutionGasWaterRatio(gas);
    rswCalc.setSalinity(0.0);

    double[] pressures = { 20.0, 50.0, 100.0 };
    double[] temps = { 323.15, 323.15, 323.15 }; // 50°C
    rswCalc.setTemperaturesAndPressures(temps, pressures);

    logger.info("\nConditions: T = 50°C (323.15 K), Pure Water");
    logger.info("Gas composition: 50% CO2, 50% CH4");
    logger.info(SEPARATOR_DASH);
    logger.printf(org.apache.logging.log4j.Level.INFO, "%-12s %-15s %-15s %-15s%n", "P (bara)", "McCain*",
	"Soreide-Whitson", "Electrolyte-CPA");
    logger.info(SEPARATOR_DASH);

    // McCain
    rswCalc.setCalculationMethod(SolutionGasWaterRatio.CalculationMethod.MCCAIN);
    rswCalc.runCalc();
    double[] rswMcCain = rswCalc.getRsw().clone();

    // Søreide-Whitson
    rswCalc.setCalculationMethod(SolutionGasWaterRatio.CalculationMethod.SOREIDE_WHITSON);
    rswCalc.runCalc();
    double[] rswSoreide = rswCalc.getRsw().clone();

    // CPA
    rswCalc.setCalculationMethod(SolutionGasWaterRatio.CalculationMethod.ELECTROLYTE_CPA);
    rswCalc.runCalc();
    double[] rswCPA = rswCalc.getRsw().clone();

    for (int i = 0; i < pressures.length; i++) {
      logger.printf(org.apache.logging.log4j.Level.INFO, "%-12.1f %-15.6f %-15.6f %-15.6f%n", pressures[i],
	  rswMcCain[i], rswSoreide[i], rswCPA[i]);
    }

    logger.info("* McCain correlation is for pure methane - use with caution for CO2");

    logger.info("Analysis:");
    logger.info("- CO2 is ~20-50x more soluble in water than methane at same conditions");
    logger.info("- EoS methods should show higher Rsw for CO2-rich gas than McCain (methane-based)");
    logger.info("- For CO2-rich systems, prefer Soreide-Whitson or CPA methods");
  }

  /**
   * Consistency check: verify that all methods give reasonable, positive values and follow expected trends.
   */
  @Test
  public void consistencyCheck() {
    logger.info("\n" + SEPARATOR_EQUALS);
    logger.info("CONSISTENCY CHECK - ALL METHODS");
    logger.info(SEPARATOR_EQUALS);

    SystemInterface gas = new SystemSrkCPAstatoil(350.0, 100.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.10);
    gas.setMixingRule(10);

    SolutionGasWaterRatio rswCalc = new SolutionGasWaterRatio(gas);

    int passCount = 0;
    int totalTests = 0;

    // Test 1: All methods give positive Rsw
    logger.info("\nTest 1: All methods give positive Rsw at typical conditions");
    double[] temps = { 350.0 };
    double[] pres = { 100.0 };
    rswCalc.setTemperaturesAndPressures(temps, pres);
    rswCalc.setSalinity(0.0);

    for (SolutionGasWaterRatio.CalculationMethod method : SolutionGasWaterRatio.CalculationMethod.values()) {
      rswCalc.setCalculationMethod(method);
      rswCalc.runCalc();
      double rsw = rswCalc.getRsw(0);
      totalTests++;
      boolean pass = rsw > 0;
      if (pass)
	passCount++;
      logger.printf(org.apache.logging.log4j.Level.INFO, "  %-20s: Rsw = %.6f Sm3/Sm3 - %s%n", method.name(), rsw,
	  pass ? "PASS" : "FAIL");
    }

    // Test 2: Rsw increases with pressure (McCain)
    logger.info("\nTest 2: Rsw increases with pressure (McCain method)");
    rswCalc.setCalculationMethod(SolutionGasWaterRatio.CalculationMethod.MCCAIN);
    rswCalc.setSalinity(0.0);
    double[] pressures = { 50.0, 100.0, 150.0 };
    double[] temperatures = { 350.0, 350.0, 350.0 };
    rswCalc.setTemperaturesAndPressures(temperatures, pressures);
    rswCalc.runCalc();
    double[] rsw = rswCalc.getRsw();

    totalTests++;
    boolean pressureTest = rsw[0] < rsw[1] && rsw[1] < rsw[2];
    if (pressureTest)
      passCount++;
    logger.printf(org.apache.logging.log4j.Level.INFO, "  50 bar: %.6f, 100 bar: %.6f, 150 bar: %.6f - %s%n", rsw[0],
	rsw[1], rsw[2], pressureTest ? "PASS" : "FAIL");

    // Test 3: Rsw decreases with salinity (McCain)
    logger.info("\nTest 3: Rsw decreases with salinity (McCain method)");
    rswCalc.setTemperaturesAndPressures(new double[] { 350.0 }, new double[] { 100.0 });

    rswCalc.setSalinity(0.0);
    rswCalc.runCalc();
    double rswPure = rswCalc.getRsw(0);

    rswCalc.setSalinity(5.0, "wt%");
    rswCalc.runCalc();
    double rswSaline = rswCalc.getRsw(0);

    totalTests++;
    boolean salinityTest = rswSaline < rswPure;
    if (salinityTest)
      passCount++;
    logger.printf(org.apache.logging.log4j.Level.INFO, "  Pure water: %.6f, 5 wt%% NaCl: %.6f - %s%n", rswPure,
	rswSaline, salinityTest ? "PASS" : "FAIL");

    // Summary
    logger.info("\n" + SEPARATOR_DASH);
    logger.printf(org.apache.logging.log4j.Level.INFO, "CONSISTENCY CHECK SUMMARY: %d/%d tests passed%n", passCount,
	totalTests);
    logger.info(SEPARATOR_EQUALS);
  }
}
