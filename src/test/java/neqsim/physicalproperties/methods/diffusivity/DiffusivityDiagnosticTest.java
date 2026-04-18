package neqsim.physicalproperties.methods.diffusivity;

import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Diagnostic test to verify database parameter values and units.
 */
public class DiffusivityDiagnosticTest {

  @Test
  void printComponentProperties() {
    SystemInterface sys = new SystemSrkEos(298.15, 1.01325);
    sys.addComponent("methane", 0.5);
    sys.addComponent("nitrogen", 0.3);
    sys.addComponent("CO2", 0.1);
    sys.addComponent("water", 0.1);
    sys.createDatabase(true);
    sys.setMixingRule(2);
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();
    sys.initPhysicalProperties();

    System.out.println("\n=== Component Database Properties ===");
    for (int i = 0; i < sys.getNumberOfComponents(); i++) {
      System.out.println("\nComponent: " + sys.getComponent(i).getComponentName());
      System.out.println("  MolarMass (kg/mol): " + sys.getComponent(i).getMolarMass());
      System.out.println("  MolarMass (g/mol):  " + sys.getComponent(i).getMolarMass() * 1000);
      System.out.println("  CriticalVolume:     " + sys.getComponent(i).getCriticalVolume());
      System.out.println("  CriticalTemp (K):   " + sys.getComponent(i).getTC());
      System.out.println("  CriticalPres (bara):" + sys.getComponent(i).getPC());
      System.out.println("  NormLiqDens (g/cm3):" + sys.getComponent(i).getNormalLiquidDensity());
      System.out.println(
          "  LJ diameter (A):    " + sys.getComponent(i).getLennardJonesMolecularDiameter());
      System.out
          .println("  LJ epsilon/k (K):   " + sys.getComponent(i).getLennardJonesEnergyParameter());
      System.out.println("  NormBoilPt (K):     " + sys.getComponent(i).getNormalBoilingPoint());
      System.out.println("  AcentricFactor:     " + sys.getComponent(i).getAcentricFactor());
    }

    // Verify Zc calculation for methane
    double Pc = sys.getComponent(0).getPC();
    double Vc = sys.getComponent(0).getCriticalVolume();
    double Tc = sys.getComponent(0).getTC();
    double R = 8.314472;
    double Zc = Pc * Vc / (R * Tc * 10.0);
    System.out.println("\n=== Methane Zc verification ===");
    System.out.println("Pc=" + Pc + " Vc=" + Vc + " Tc=" + Tc);
    System.out.println("Zc = Pc*Vc/(R*Tc*10) = " + Zc);
    System.out.println("Expected Zc ~ 0.286");

    // Test gas phase diffusion coefficients
    if (sys.hasPhaseType("gas")) {
      System.out.println("\n=== Gas Diffusion Coefficients (default Chapman-Enskog) ===");
      for (int i = 0; i < sys.getPhase("gas").getNumberOfComponents(); i++) {
        for (int j = i + 1; j < sys.getPhase("gas").getNumberOfComponents(); j++) {
          double D = sys.getPhase("gas").getPhysicalProperties().diffusivityCalc
              .calcBinaryDiffusionCoefficient(i, j, 0);
          System.out.println("  D(" + sys.getComponent(i).getComponentName() + "-"
              + sys.getComponent(j).getComponentName() + ") = " + D + " m2/s = " + (D * 1e4)
              + " cm2/s");
        }
      }

      // Switch to Fuller
      sys.getPhase("gas").getPhysicalProperties()
          .setDiffusionCoefficientModel("Fuller-Schettler-Giddings");
      System.out.println("\n=== Gas Diffusion Coefficients (Fuller-Schettler-Giddings) ===");
      for (int i = 0; i < sys.getPhase("gas").getNumberOfComponents(); i++) {
        for (int j = i + 1; j < sys.getPhase("gas").getNumberOfComponents(); j++) {
          double D = sys.getPhase("gas").getPhysicalProperties().diffusivityCalc
              .calcBinaryDiffusionCoefficient(i, j, 0);
          System.out.println("  D(" + sys.getComponent(i).getComponentName() + "-"
              + sys.getComponent(j).getComponentName() + ") = " + D + " m2/s = " + (D * 1e4)
              + " cm2/s");
        }
      }

      // Wilke-Lee
      sys.getPhase("gas").getPhysicalProperties().setDiffusionCoefficientModel("Wilke Lee");
      System.out.println("\n=== Gas Diffusion Coefficients (Wilke-Lee) ===");
      for (int i = 0; i < sys.getPhase("gas").getNumberOfComponents(); i++) {
        for (int j = i + 1; j < sys.getPhase("gas").getNumberOfComponents(); j++) {
          double D = sys.getPhase("gas").getPhysicalProperties().diffusivityCalc
              .calcBinaryDiffusionCoefficient(i, j, 0);
          System.out.println("  D(" + sys.getComponent(i).getComponentName() + "-"
              + sys.getComponent(j).getComponentName() + ") = " + D + " m2/s = " + (D * 1e4)
              + " cm2/s");
        }
      }
    }
  }
}
