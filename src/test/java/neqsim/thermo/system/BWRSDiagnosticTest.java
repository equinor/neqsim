package neqsim.thermo.system;

import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class BWRSDiagnosticTest {

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

    System.out
        .println("=== JT Diagnostic: Methane at " + temperature + " K, " + pressure + " bar ===");
    System.out.println();

    String[] names = {"BWRS", "GERG", "PR"};
    SystemInterface[] systems = {bwrs, gerg, pr};
    double R = 8.3144621;

    for (int s = 0; s < 3; s++) {
      System.out.println("--- " + names[s] + " ---");
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

      System.out.println("  n=" + n + " T=" + T + " P=" + P);
      System.out.println("  Vm=" + Vm + " Vtot=" + Vtot);
      System.out.println("  F=" + F + " dFdT=" + dFdT);
      System.out.println("  dFdV=" + dFdV + " dFdVdV=" + dFdVdV);
      System.out.println("  dFdTdV=" + dFdTdV);
      System.out.println("  dPdVTn=" + dPdVTn + " dPdTVn=" + dPdTVn);
      System.out.println("  kT=" + kT + " Cp=" + cp);
      System.out.println("  H=" + H + " Hres=" + Hres);
      System.out.println("  Vm*n=" + (Vm * n));
      System.out.println("  T*dPdTVn/dPdVTn=" + (T * dPdTVn / dPdVTn));
      System.out.println("  dH/dP_T (analytical)=" + (Vm * n + T * dPdTVn / dPdVTn));
      System.out.println("  ANALYTICAL JT = " + analyticalJT);
      System.out.println("  NUMERICAL JT  = " + numericalJT);
      System.out.println();
    }
  }
}
