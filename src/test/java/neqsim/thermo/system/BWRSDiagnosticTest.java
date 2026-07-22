package neqsim.thermo.system;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class BWRSDiagnosticTest {
  private static final Logger logger = LogManager.getLogger(BWRSDiagnosticTest.class);

  @Test
  public void jtDiagnosticAt10bar() {
    double pressure = 10.0;
    double temperature = 298.15;

    // BWRS
    SystemInterface bwrs = new SystemBWRSEos(temperature, pressure);
    bwrs.addComponent("methane", 1.0);
    bwrs.createDatabase(true);
    bwrs.setMixingRule(2);
    new ThermodynamicOperations(bwrs).TPflash();
    bwrs.initProperties();

    // GERG
    SystemInterface gerg = new SystemGERG2008Eos(temperature, pressure);
    gerg.addComponent("methane", 1.0);
    gerg.createDatabase(true);
    gerg.setMixingRule(2);
    new ThermodynamicOperations(gerg).TPflash();
    gerg.initProperties();

    // PR
    SystemInterface pr = new SystemPrEos(temperature, pressure);
    pr.addComponent("methane", 1.0);
    pr.createDatabase(true);
    pr.setMixingRule(2);
    new ThermodynamicOperations(pr).TPflash();
    pr.initProperties();

    System.out.println("=== JT Diagnostic: Methane at " + temperature + " K, " + pressure + " bar ===");

    String[] names = { "BWRS", "GERG", "PR" };
    SystemInterface[] systems = { bwrs, gerg, pr };
    double R = 8.3144621;

    for (int s = 0; s < 3; s++) {
      logger.info("--- " + names[s] + " ---");
      neqsim.thermo.phase.PhaseInterface phase = systems[s].getPhase(0);
      double n = phase.getNumberOfMolesInPhase();
      double T = phase.getTemperature();
      double P = phase.getPressure();
      double Vm = phase.getMolarVolume();
      double Vtot = phase.getTotalVolume();
      double dPdVTn = phase.getdPdVTn();
      double dPdTVn = phase.getdPdTVn();
      double kT = phase.getIsothermalCompressibility();
      double cp = phase.getCp();
      double H = phase.getEnthalpy();
      double Hres = phase.getHresTP();
      double F = 0, dFdT = 0, dFdTdV = 0, dFdV = 0, dFdVdV = 0;
      if (phase instanceof neqsim.thermo.phase.PhaseEos) {
        neqsim.thermo.phase.PhaseEos phaseEos = (neqsim.thermo.phase.PhaseEos) phase;
        F = phaseEos.getF();
        dFdT = phaseEos.dFdT();
        dFdTdV = phaseEos.dFdTdV();
        dFdV = phaseEos.dFdV();
        dFdVdV = phaseEos.dFdVdV();
      }

      // Analytical JT (PhaseEos formula)
      double analyticalJT = -1.0 / cp * (Vm * n + T * dPdTVn / dPdVTn);

      // Numerical JT (the override)
      double numericalJT = phase.getJouleThomsonCoefficient();

      logger.info("  n=" + n + " T=" + T + " P=" + P);
      logger.info("  Vm=" + Vm + " Vtot=" + Vtot);
      logger.info("  F=" + F + " dFdT=" + dFdT);
      logger.info("  dFdV=" + dFdV + " dFdVdV=" + dFdVdV);
      logger.info("  dFdTdV=" + dFdTdV);
      logger.info("  dPdVTn=" + dPdVTn + " dPdTVn=" + dPdTVn);
      logger.info("  kT=" + kT + " Cp=" + cp);
      logger.info("  H=" + H + " Hres=" + Hres);
      logger.info("  Vm*n=" + (Vm * n));
      logger.info("  T*dPdTVn/dPdVTn=" + (T * dPdTVn / dPdVTn));
      logger.info("  dH/dP_T (analytical)=" + (Vm * n + T * dPdTVn / dPdVTn));
      logger.info("  ANALYTICAL JT = " + analyticalJT);
      logger.info("  NUMERICAL JT  = " + numericalJT);

    }
  }
}
