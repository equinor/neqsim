package neqsim.thermo;

import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Diagnostic test to understand fugacity coefficient issues.
 */
public class FugacityDiagnosticTest {

  /**
   * Test fugacity coefficient calculation for CO2-H2O system.
   * 
   * <p>
   * Creates two identical systems but forces one to gas and one to liquid phase type to see the
   * effect on fugacity coefficients.
   * </p>
   */
  @Test
  public void testFugacityCoefficientPhaseTypes() {
    double T = 273.15 + 25.0; // 25 Celsius
    double P = 10.0; // 10 bar

    // Gas-like composition (mostly CO2)
    System.out.println("=== GAS-LIKE COMPOSITION (mostly CO2) ===");
    testCompositionBothPhaseTypes(0.96, 0.04, T, P, "Gas-like");

    // Liquid-like composition (mostly H2O)
    System.out.println("\n=== LIQUID-LIKE COMPOSITION (mostly H2O) ===");
    testCompositionBothPhaseTypes(0.087, 0.913, T, P, "Liquid-like");
  }

  private void testCompositionBothPhaseTypes(double xCO2, double xH2O, double T, double P,
      String label) {
    // Test as GAS
    System.out.println("\n--- " + label + " as GAS phase ---");
    SystemInterface gasSystem = new SystemSrkEos(T, P);
    gasSystem.addComponent("CO2", xCO2);
    gasSystem.addComponent("water", xH2O);
    gasSystem.setMixingRule("classic");
    gasSystem.setNumberOfPhases(1);
    gasSystem.setMaxNumberOfPhases(1);
    gasSystem.setForcePhaseTypes(true);
    gasSystem.init(0);
    gasSystem.setPhaseType(0, PhaseType.GAS);
    gasSystem.init(3);

    printPhaseDetails(gasSystem, "GAS");

    // Test as LIQUID
    System.out.println("\n--- " + label + " as LIQUID phase ---");
    SystemInterface liqSystem = new SystemSrkEos(T, P);
    liqSystem.addComponent("CO2", xCO2);
    liqSystem.addComponent("water", xH2O);
    liqSystem.setMixingRule("classic");
    liqSystem.setNumberOfPhases(1);
    liqSystem.setMaxNumberOfPhases(1);
    liqSystem.setForcePhaseTypes(true);
    liqSystem.init(0);
    liqSystem.setPhaseType(0, PhaseType.LIQUID);
    liqSystem.init(3);

    printPhaseDetails(liqSystem, "LIQUID");
  }

  private void printPhaseDetails(SystemInterface system, String phaseLabel) {
    double P = system.getPressure();
    double T = system.getTemperature();
    double R = 8.314462618;

    System.out.println("T = " + T + " K, P = " + P + " bar");

    double molarVolume = system.getPhase(0).getMolarVolume();
    double Z = P * molarVolume / (R * T);
    System.out.println("Molar Volume = " + molarVolume + " m3/kmol");
    System.out.println("Z-factor = " + Z);

    System.out.println("Phase type (actual): " + system.getPhase(0).getPhaseTypeName());

    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      String name = system.getComponent(i).getName();
      double x = system.getPhase(0).getComponent(i).getx();
      double phi = system.getPhase(0).getComponent(i).getFugacityCoefficient();
      double fugacity = phi * x * P;

      // Get intermediate values
      double Ai = 0.0;
      double Bi = 0.0;
      double dFdN = 0.0;
      double logFromVolume = 0.0;

      try {
        neqsim.thermo.component.ComponentEosInterface compEos =
            (neqsim.thermo.component.ComponentEosInterface) system.getPhase(0).getComponent(i);
        Ai = compEos.getAi();
        Bi = compEos.getBi();
        dFdN = compEos.dFdN(system.getPhase(0), system.getNumberOfComponents(), T, P);
        logFromVolume = Math.log(P * molarVolume / (R * T));
      } catch (Exception e) {
        System.out.println("  Error getting component details: " + e.getMessage());
      }

      System.out.println("  " + name + ": x = " + x);
      System.out.println("    Ai = " + Ai + ", Bi = " + Bi);
      System.out.println("    dFdN = " + dFdN);
      System.out.println("    log(PV/RT) = " + logFromVolume);
      System.out.println("    ln(phi) = dFdN - log(PV/RT) = " + (dFdN - logFromVolume));
      System.out.println("    phi = " + phi);
      System.out.println("    fugacity = phi * x * P = " + fugacity + " bar");
    }
  }

  /**
   * Test a standard VLE flash to see reference values.
   */
  @Test
  public void testStandardFlash() {
    System.out.println("=== STANDARD FLASH REFERENCE ===");
    SystemInterface system = new SystemSrkEos(273.15 + 25.0, 10.0);
    system.addComponent("CO2", 0.5);
    system.addComponent("water", 0.5);
    system.setMixingRule("classic");

    neqsim.thermodynamicoperations.ThermodynamicOperations ops =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(system);
    ops.TPflash();

    System.out.println("Number of phases: " + system.getNumberOfPhases());
    for (int p = 0; p < system.getNumberOfPhases(); p++) {
      System.out.println("\nPhase " + p + " (" + system.getPhase(p).getPhaseTypeName() + "):");
      System.out.println("  Molar volume = " + system.getPhase(p).getMolarVolume() + " m3/kmol");
      double Z = system.getPressure() * system.getPhase(p).getMolarVolume()
          / (8.314462618 * system.getTemperature());
      System.out.println("  Z = " + Z);

      for (int i = 0; i < system.getNumberOfComponents(); i++) {
        String name = system.getComponent(i).getName();
        double x = system.getPhase(p).getComponent(i).getx();
        double phi = system.getPhase(p).getComponent(i).getFugacityCoefficient();
        double f = phi * x * system.getPressure();
        System.out.println("  " + name + ": x = " + x + ", phi = " + phi + ", f = " + f + " bar");
      }
    }
  }
}
