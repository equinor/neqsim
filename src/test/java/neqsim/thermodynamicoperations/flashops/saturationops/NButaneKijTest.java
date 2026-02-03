package neqsim.thermodynamicoperations.flashops.saturationops;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Test to check kij parameters between n-butane and ions.
 *
 * @author ESOL
 */
public class NButaneKijTest {

  /**
   * Check kij parameters between all components.
   */
  @Test
  @DisplayName("Check kij between n-butane and ions")
  public void testKijNButaneIons() {
    System.out.println("=== kij Parameter Check for n-butane and ions ===\n");

    SystemInterface fluid = new SystemElectrolyteCPAstatoil(273.15 + 10.0, 50.0);

    fluid.addComponent("water", 0.5);
    fluid.addComponent("MEG", 0.1);
    fluid.addComponent("methane", 0.2);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("i-butane", 0.02);
    fluid.addComponent("n-butane", 0.02);
    fluid.addComponent("Na+", 0.04);
    fluid.addComponent("Cl-", 0.04);

    fluid.setMixingRule(10); // CPA mixing rule

    fluid.init(0);
    fluid.init(1);

    System.out.println("Component indices:");
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      System.out.println("  " + i + ": " + fluid.getComponent(i).getName() + " (ionic charge: "
          + fluid.getComponent(i).getIonicCharge() + ")");
    }

    System.out.println("\n=== Binary Interaction Parameters (kij) ===");

    // Get mixing rule
    PhaseEos phase = (PhaseEos) fluid.getPhase(0);

    // Find indices
    int nButaneIdx = -1;
    int naIdx = -1;
    int clIdx = -1;
    int waterIdx = -1;
    int megIdx = -1;
    int methaneIdx = -1;

    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      String name = fluid.getComponent(i).getName();
      if (name.equals("n-butane")) {
        nButaneIdx = i;
      }
      if (name.equals("Na+")) {
        naIdx = i;
      }
      if (name.equals("Cl-")) {
        clIdx = i;
      }
      if (name.equals("water")) {
        waterIdx = i;
      }
      if (name.equals("MEG")) {
        megIdx = i;
      }
      if (name.equals("methane")) {
        methaneIdx = i;
      }
    }

    System.out.println("\nn-butane index: " + nButaneIdx);
    System.out.println("Na+ index: " + naIdx);
    System.out.println("Cl- index: " + clIdx);

    // Check kij between n-butane and all components
    System.out.println("\n=== kij values for n-butane with all components ===");
    for (int j = 0; j < fluid.getNumberOfComponents(); j++) {
      double kij = phase.getEosMixingRule().getBinaryInteractionParameter(nButaneIdx, j);
      System.out.println("kij(n-butane, " + fluid.getComponent(j).getName() + ") = " + kij);
    }

    // Check kij for Na+ and Cl- with all components
    System.out.println("\n=== kij values for Na+ with all components ===");
    for (int j = 0; j < fluid.getNumberOfComponents(); j++) {
      double kij = phase.getEosMixingRule().getBinaryInteractionParameter(naIdx, j);
      System.out.println("kij(Na+, " + fluid.getComponent(j).getName() + ") = " + kij);
    }

    System.out.println("\n=== kij values for Cl- with all components ===");
    for (int j = 0; j < fluid.getNumberOfComponents(); j++) {
      double kij = phase.getEosMixingRule().getBinaryInteractionParameter(clIdx, j);
      System.out.println("kij(Cl-, " + fluid.getComponent(j).getName() + ") = " + kij);
    }

    // Check specific problematic pairs
    System.out.println("\n=== Potentially problematic kij values ===");
    double kij_nbutane_na =
        phase.getEosMixingRule().getBinaryInteractionParameter(nButaneIdx, naIdx);
    double kij_nbutane_cl =
        phase.getEosMixingRule().getBinaryInteractionParameter(nButaneIdx, clIdx);
    System.out.println("kij(n-butane, Na+) = " + kij_nbutane_na);
    System.out.println("kij(n-butane, Cl-) = " + kij_nbutane_cl);

    if (Math.abs(kij_nbutane_na) > 1e-10) {
      System.out.println("WARNING: kij(n-butane, Na+) is non-zero! This may cause issues.");
    }
    if (Math.abs(kij_nbutane_cl) > 1e-10) {
      System.out.println("WARNING: kij(n-butane, Cl-) is non-zero! This may cause issues.");
    }

    // Check if n-butane parameters look unusual
    System.out.println("\n=== Component Critical Properties ===");
    System.out.println("n-butane TC = " + fluid.getComponent("n-butane").getTC() + " K");
    System.out.println("n-butane PC = " + fluid.getComponent("n-butane").getPC() + " bar");
    System.out.println("n-butane omega = " + fluid.getComponent("n-butane").getAcentricFactor());
  }

  /**
   * Test fugacity coefficient calculations with ions.
   */
  @Test
  @DisplayName("Check fugacity coefficients for n-butane with ions")
  public void testFugacityWithIons() {
    System.out.println("\n=== Fugacity Coefficient Check ===\n");

    SystemInterface fluid = new SystemElectrolyteCPAstatoil(273.15 - 10.0, 50.0);

    fluid.addComponent("water", 0.5);
    fluid.addComponent("MEG", 0.1);
    fluid.addComponent("methane", 0.2);
    fluid.addComponent("n-butane", 0.02);
    fluid.addComponent("Na+", 0.09);
    fluid.addComponent("Cl-", 0.09);

    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    System.out.println("Number of phases: " + fluid.getNumberOfPhases());

    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      System.out.println("\nPhase " + p + ": " + fluid.getPhase(p).getPhaseTypeName());
      System.out.println("  n-butane:");
      System.out.println("    x = " + fluid.getPhase(p).getComponent("n-butane").getx());
      System.out.println(
          "    phi = " + fluid.getPhase(p).getComponent("n-butane").getFugacityCoefficient());
      System.out.println("    ln(phi) = "
          + fluid.getPhase(p).getComponent("n-butane").getLogFugacityCoefficient());

      // Check if fugacity coefficient is unreasonably large or small
      double phi = fluid.getPhase(p).getComponent("n-butane").getFugacityCoefficient();
      if (phi > 1e10) {
        System.out.println("    WARNING: Extremely large fugacity coefficient!");
      }
      if (phi < 1e-10 && phi > 0) {
        System.out.println("    WARNING: Extremely small fugacity coefficient!");
      }
      if (Double.isNaN(phi) || Double.isInfinite(phi)) {
        System.out.println("    ERROR: Invalid fugacity coefficient (NaN or Inf)!");
      }
    }
  }

  /**
   * Compare K-values with and without ions.
   */
  @Test
  @DisplayName("Compare n-butane K-values with and without ions")
  public void testKValuesComparison() {
    System.out.println("\n=== K-value Comparison ===\n");

    // Without ions
    SystemInterface fluid1 = new SystemElectrolyteCPAstatoil(273.15 - 10.0, 50.0);
    fluid1.addComponent("water", 0.5);
    fluid1.addComponent("MEG", 0.1);
    fluid1.addComponent("methane", 0.3);
    fluid1.addComponent("n-butane", 0.1);
    fluid1.setMixingRule(10);
    fluid1.setMultiPhaseCheck(true);

    ThermodynamicOperations ops1 = new ThermodynamicOperations(fluid1);
    ops1.TPflash();

    System.out.println("WITHOUT ions:");
    System.out.println("  Phases: " + fluid1.getNumberOfPhases());
    double K1 = fluid1.getPhase(0).getComponent("n-butane").getK();
    System.out.println("  K(n-butane) = " + K1);

    // With ions
    SystemInterface fluid2 = new SystemElectrolyteCPAstatoil(273.15 - 10.0, 50.0);
    fluid2.addComponent("water", 0.4);
    fluid2.addComponent("MEG", 0.1);
    fluid2.addComponent("methane", 0.3);
    fluid2.addComponent("n-butane", 0.1);
    fluid2.addComponent("Na+", 0.05);
    fluid2.addComponent("Cl-", 0.05);
    fluid2.setMixingRule(10);
    fluid2.setMultiPhaseCheck(true);

    ThermodynamicOperations ops2 = new ThermodynamicOperations(fluid2);
    ops2.TPflash();

    System.out.println("\nWITH ions:");
    System.out.println("  Phases: " + fluid2.getNumberOfPhases());
    double K2 = fluid2.getPhase(0).getComponent("n-butane").getK();
    System.out.println("  K(n-butane) = " + K2);

    // K-values for ions should be near zero
    if (fluid2.getPhase(0).hasComponent("Na+")) {
      double Kna = fluid2.getPhase(0).getComponent("Na+").getK();
      System.out.println("  K(Na+) = " + Kna);
      if (Math.abs(Kna) > 1e-30) {
        System.out.println("  WARNING: K(Na+) should be ~0 for ions!");
      }
    }

    System.out.println("\nK-value ratio (with/without ions): " + (K2 / K1));
  }
}
