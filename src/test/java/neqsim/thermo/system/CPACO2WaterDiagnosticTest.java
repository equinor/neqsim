package neqsim.thermo.system;

import org.junit.jupiter.api.Test;

import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Diagnostic test to measure current CPA CO2-water predictions at multiple conditions against
 * published experimental data. Results are printed to stdout for analysis.
 *
 * <p>
 * Reference data sources:
 * </p>
 * <ul>
 * <li>Wiebe &amp; Gaddy (1940) - CO2 solubility in water 12-100C</li>
 * <li>Todheide &amp; Franck (1963) - high T,P data</li>
 * <li>King et al. (1992) - CO2 solubility in water</li>
 * <li>Bamberger et al. (2000) - CO2 solubility 50-80C</li>
 * <li>Duan &amp; Sun (2003) - comprehensive model</li>
 * </ul>
 *
 * @author Copilot
 * @version 1.0
 */
class CPACO2WaterDiagnosticTest extends neqsim.NeqSimTest {

  /**
   * Comprehensive benchmark: CO2 solubility in aqueous phase at multiple T,P conditions.
   */
  @Test
  void diagnosticCO2SolubilityMultipleConditions() {
    System.out.println("=== CPA CO2-Water Solubility Benchmark ===");
    System.out.println("Current INTER.csv: cpakij_SRK=-0.27686, cpakijT_SRK=0.001121, "
        + "betaCross=0.075, epsCross=0");
    System.out.println();

    // Reference experimental data: {T(C), P(bar), x_CO2_exp}
    // Sources: Wiebe & Gaddy (1940), Bamberger et al. (2000), Duan & Sun (2003)
    double[][] refData = {
        // T(C), P(bar), x_CO2_experimental
        {25.0, 25.0, 0.0133}, // Wiebe & Gaddy
        {25.0, 50.0, 0.0196}, // Wiebe & Gaddy
        {25.0, 100.0, 0.0234}, // Wiebe & Gaddy
        {25.0, 200.0, 0.0262}, // Wiebe & Gaddy
        {40.0, 50.0, 0.0145}, // Bamberger et al.
        {40.0, 100.0, 0.0195}, // Bamberger et al.
        {50.0, 50.0, 0.0123}, // Bamberger et al.
        {50.0, 100.0, 0.0178}, // Bamberger et al.
        {60.0, 50.0, 0.0110}, // Bamberger et al.
        {60.0, 100.0, 0.0170}, // Bamberger et al.
        {80.0, 100.0, 0.0155}, // Duan & Sun model
        {80.0, 200.0, 0.0230}, // Duan & Sun model
        {100.0, 100.0, 0.0145}, // Duan & Sun model
        {100.0, 200.0, 0.0230}, // Duan & Sun model
    };

    System.out.printf("%-8s %-8s %-12s %-12s %-12s %-10s%n", "T(C)", "P(bar)", "x_CO2_exp",
        "x_CO2_CPA", "error(%)", "kij_eff");
    System.out
        .println("--------------------------------------------------------------" + "--------");

    double sumAbsError = 0.0;
    int count = 0;

    for (double[] row : refData) {
      double tempC = row[0];
      double pressure = row[1];
      double xCO2exp = row[2];

      double tempK = 273.15 + tempC;

      SystemInterface system = new SystemSrkCPAstatoil(tempK, pressure);
      system.addComponent("CO2", 0.3);
      system.addComponent("water", 0.7);
      system.setMixingRule(10);
      system.setMultiPhaseCheck(true);

      ThermodynamicOperations ops = new ThermodynamicOperations(system);
      try {
        ops.TPflash();
      } catch (Exception e) {
        System.out.printf("%-8.0f %-8.0f FLASH FAILED: %s%n", tempC, pressure, e.getMessage());
        continue;
      }

      double co2InAq = getAqueousCO2(system);

      // Effective kij at this temperature
      // kij = cpakij_SRK + cpakijT_SRK * T (for intparamTType=0)
      // or = cpakij_SRK + cpakijT_SRK / T (for intparamTType=1)
      // Need to check which type is used
      double kijLinear = -0.27686 + 0.001121 * tempK;
      double kijInverse = -0.27686 + 0.001121 / tempK;

      double errorPct = (co2InAq - xCO2exp) / xCO2exp * 100.0;
      sumAbsError += Math.abs(errorPct);
      count++;

      System.out.printf("%-8.0f %-8.0f %-12.5f %-12.5f %-12.1f %-10.4f%n", tempC, pressure, xCO2exp,
          co2InAq, errorPct, kijLinear);
    }

    System.out
        .println("--------------------------------------------------------------" + "--------");
    System.out.printf("Mean absolute error: %.1f%%%n", sumAbsError / count);
    System.out.println();

    // Also print water in CO2-rich phase
    System.out.println("=== Water in CO2-rich Phase ===");
    double[][] waterRefData = {
        // T(C), P(bar), y_H2O_experimental
        {25.0, 100.0, 0.0027}, // King et al.
        {50.0, 100.0, 0.0058}, // King et al.
        {80.0, 100.0, 0.0156}, // estimated
        {100.0, 100.0, 0.0310}, // estimated
    };

    System.out.printf("%-8s %-8s %-12s %-12s %-12s%n", "T(C)", "P(bar)", "y_H2O_exp", "y_H2O_CPA",
        "error(%)");
    System.out.println("--------------------------------------------------");

    for (double[] row : waterRefData) {
      double tempC = row[0];
      double pressure = row[1];
      double yH2Oexp = row[2];

      double tempK = 273.15 + tempC;

      SystemInterface system = new SystemSrkCPAstatoil(tempK, pressure);
      system.addComponent("CO2", 0.7);
      system.addComponent("water", 0.3);
      system.setMixingRule(10);
      system.setMultiPhaseCheck(true);

      ThermodynamicOperations ops = new ThermodynamicOperations(system);
      try {
        ops.TPflash();
      } catch (Exception e) {
        System.out.printf("%-8.0f %-8.0f FLASH FAILED%n", tempC, pressure);
        continue;
      }

      double waterInCO2 = getCO2PhaseWater(system);
      double errorPct = (waterInCO2 - yH2Oexp) / yH2Oexp * 100.0;

      System.out.printf("%-8.0f %-8.0f %-12.5f %-12.5f %-12.1f%n", tempC, pressure, yH2Oexp,
          waterInCO2, errorPct);
    }
  }

  /**
   * Gets CO2 mole fraction in the aqueous/liquid phase.
   *
   * @param system the flashed system
   * @return CO2 mole fraction in aqueous phase
   */
  private double getAqueousCO2(SystemInterface system) {
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      String pt = system.getPhase(i).getType().toString().toLowerCase();
      if (pt.contains("aqueous")) {
        if (system.getPhase(i).hasComponent("CO2")) {
          return system.getPhase(i).getComponent("CO2").getx();
        }
      }
    }
    // fallback: look for liquid phase
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      String pt = system.getPhase(i).getType().toString().toLowerCase();
      if (pt.contains("liquid")) {
        if (system.getPhase(i).hasComponent("CO2")) {
          return system.getPhase(i).getComponent("CO2").getx();
        }
      }
    }
    return 0.0;
  }

  /**
   * Gets water mole fraction in the CO2-rich (non-aqueous) phase.
   *
   * @param system the flashed system
   * @return water mole fraction in CO2-rich phase
   */
  private double getCO2PhaseWater(SystemInterface system) {
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      String pt = system.getPhase(i).getType().toString().toLowerCase();
      if (pt.contains("gas") || pt.contains("oil")) {
        if (system.getPhase(i).hasComponent("water")) {
          return system.getPhase(i).getComponent("water").getx();
        }
      }
    }
    return 0.0;
  }
}
