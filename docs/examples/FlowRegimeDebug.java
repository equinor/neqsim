package examples;

import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Debug flow regime detection - why is ANNULAR predicted at low velocities?
 */
public class FlowRegimeDebug {
  private static final Logger logger = LogManager.getLogger(FlowRegimeDebug.class);

  private static final double GRAVITY = 9.81;

  public static void main(String[] args) {
    logger.info("================================================================");
    logger.info("  Flow Regime Detection Debug");
    logger.info("  Why ANNULAR at low velocities?");
    logger.info("================================================================\n");

    double diameter = 0.3; // m
    double area = Math.PI * diameter * diameter / 4.0;
    double temperature = 40.0;
    double pressure = 50.0;

    double[] flowRates = {1.0, 5.0, 20.0, 60.0, 100.0};

    logger.info("Two-Phase Fluid (Gas + Oil) Analysis:");
    logger.info("======================================\n");

    for (double flowRate : flowRates) {
      SystemInterface fluid = createTwoPhaseFluid(temperature, pressure);

      Stream inlet = new Stream("inlet", fluid);
      inlet.setFlowRate(flowRate, "kg/sec");
      inlet.setTemperature(temperature, "C");
      inlet.setPressure(pressure, "bara");
      inlet.run();

      SystemInterface sys = inlet.getFluid();

      // Get phase properties
      double rho_G = sys.getPhase(0).getDensity("kg/m3");
      double rho_L = sys.hasPhaseType("oil") ? sys.getPhase("oil").getDensity("kg/m3")
          : (sys.getNumberOfPhases() > 1 ? sys.getPhase(1).getDensity("kg/m3") : 800);
      double sigma = sys.getInterphaseProperties().getSurfaceTension(0, 1); // N/m

      // Get flow rates
      double Q_G = sys.getPhase(0).getFlowRate("m3/sec");
      double Q_L = 0;
      for (int i = 1; i < sys.getNumberOfPhases(); i++) {
        Q_L += sys.getPhase(i).getFlowRate("m3/sec");
      }

      // Superficial velocities
      double U_SG = Q_G / area;
      double U_SL = Q_L / area;
      double U_M = U_SG + U_SL;

      // Critical gas velocity for annular flow (Taitel-Dukler criterion)
      double deltaRho = rho_L - rho_G;
      double U_SG_crit = 3.1 * Math.pow(sigma * GRAVITY * deltaRho / (rho_G * rho_G), 0.25);

      // Froude number
      double Fr = U_SG * Math.sqrt(rho_G / (deltaRho * GRAVITY * diameter));

      // Is annular?
      boolean isAnnular = U_SG > U_SG_crit;

      logger.info("{}", String.format("Flow Rate: %.0f kg/s", flowRate));
      logger.info("{}", String.format("  Gas density:     %.1f kg/m3", rho_G));
      logger.info("{}", String.format("  Liquid density:  %.1f kg/m3", rho_L));
      logger.info("{}", String.format("  Surface tension: %.4f N/m", sigma));
      logger.info("{}", String.format("  Gas vol flow:    %.4f m3/s", Q_G));
      logger.info("{}", String.format("  Liq vol flow:    %.6f m3/s", Q_L));
      logger.info("{}", String.format("  U_SG (gas):      %.3f m/s", U_SG));
      logger.info("{}", String.format("  U_SL (liquid):   %.4f m/s", U_SL));
      logger.info("{}", String.format("  U_SG_crit:       %.3f m/s  (Taitel-Dukler annular threshold)",
          U_SG_crit));
      logger.info("{}", String.format("  U_SG > U_SG_crit: %s  => %s", isAnnular ? "YES" : "NO",
          isAnnular ? "ANNULAR" : "NOT ANNULAR (check stratified/slug)"));
      logger.info("{}", String.format("  Froude number:   %.3f", Fr));
      logger.info("{}", String.format("  GVF (gas vol fraction): %.1f%%", 100 * Q_G / (Q_G + Q_L)));
      logger.info("");
    }

    logger.info("\n================================================================");
    logger.info("  EXPLANATION");
    logger.info("================================================================");
    logger.info("");
    logger.info("The Taitel-Dukler criterion for ANNULAR flow is:");
    logger.info("  U_SG > 3.1 * (sigma * g * (rho_L - rho_G) / rho_G^2)^0.25");
    logger.info("");
    logger.info("This threshold depends on:");
    logger.info("  - Surface tension (sigma)");
    logger.info("  - Density difference (rho_L - rho_G)");
    logger.info("  - Gas density (rho_G) - CRITICAL: appears squared in denominator!");
    logger.info("");
    logger.info("At 50 bara, the gas is DENSE (~35-40 kg/m3), which:");
    logger.info("  1. Lowers the critical velocity (U_SG_crit)");
    logger.info("  2. Makes annular flow easier to achieve");
    logger.info("");
    logger.info("The gas-dominant fluid (70% methane) has very little liquid,");
    logger.info("so even at 1 kg/s total, the gas velocity exceeds the threshold.");
    logger.info("");
    logger.info("For STRATIFIED flow to be predicted, you need:");
    logger.info("  - Lower pressure (lower gas density)");
    logger.info("  - More liquid content (higher liquid fraction)");
    logger.info("  - Lower gas velocity (U_SG < U_SG_crit)");
  }

  private static SystemInterface createTwoPhaseFluid(double tempC, double pressure) {
    SystemInterface fluid = new SystemSrkEos(tempC + 273.15, pressure);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.03);
    fluid.addComponent("n-pentane", 0.05);
    fluid.addComponent("n-heptane", 0.05);
    fluid.addComponent("n-octane", 0.04);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }
}
