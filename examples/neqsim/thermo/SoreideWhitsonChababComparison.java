package neqsim.thermo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.mixingrule.SoreideWhitsonParameterization;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemSoreideWhitson;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Compares legacy and Chabab et al. (2019) Soreide-Whitson CO2-brine predictions. */
public final class SoreideWhitsonChababComparison {
  private static final Logger logger = LogManager.getLogger(SoreideWhitsonChababComparison.class);
  private static final double WATER_MOLAR_MASS_KG_PER_MOL = 0.01801528;

  /** Utility class; not instantiable. */
  private SoreideWhitsonChababComparison() {}

  /**
   * Run selected Chabab et al. (2019) Table 2 cases.
   *
   * @param args command-line arguments; not used
   */
  public static void main(String[] args) {
    double[][] cases = {
        {1.13, 323.02, 53.450, 0.01030},
        {1.13, 323.03, 100.350, 0.01510},
        {3.01, 342.82, 30.391, 0.00441},
        {3.01, 342.82, 100.910, 0.01057}};

    logger.info("molality [mol/kg water], T [K], P [bara], experiment xCO2, legacy xCO2, Chabab xCO2");
    for (double[] benchmarkCase : cases) {
      double legacy = calculate(benchmarkCase, SoreideWhitsonParameterization.LEGACY);
      double chabab = calculate(benchmarkCase, SoreideWhitsonParameterization.CHABAB_2019);
      logger.info("{}, {}, {}, {}, {}, {}", benchmarkCase[0], benchmarkCase[1], benchmarkCase[2], benchmarkCase[3],
          legacy, chabab);
    }
  }

  /**
   * Calculate an aqueous CO2 mole fraction for one benchmark condition.
   *
   * @param benchmarkCase molality, temperature, pressure, and experimental mole fraction
   * @param parameterization aqueous CO2-water parameterization
   * @return calculated aqueous CO2 mole fraction
   */
  private static double calculate(double[] benchmarkCase, SoreideWhitsonParameterization parameterization) {
    double waterMolesPerSecond = 1.0 / WATER_MOLAR_MASS_KG_PER_MOL;
    SystemSoreideWhitson system = new SystemSoreideWhitson(benchmarkCase[1], benchmarkCase[2]);
    system.addComponent("CO2", 5.0);
    system.addComponent("water", waterMolesPerSecond);
    system.addSalinity("NaCl", benchmarkCase[0], "mole/sec");
    system.setTotalFlowRate(waterMolesPerSecond + 5.0, "mole/sec");
    system.createDatabase(true);
    system.setMixingRule(11);
    system.setAqueousCO2Parameterization(parameterization);
    system.setMultiPhaseCheck(true);

    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    operations.TPflash();
    return system.getPhase(PhaseType.AQUEOUS).getComponent("CO2").getx();
  }
}
