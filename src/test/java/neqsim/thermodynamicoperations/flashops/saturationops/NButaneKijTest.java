package neqsim.thermodynamicoperations.flashops.saturationops;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Test to check kij parameters between n-butane and ions.
 *
 * @author ESOL
 */
@Tag("slow")
public class NButaneKijTest {
  private static final Logger logger = LogManager.getLogger(NButaneKijTest.class);

  /**
   * Check kij parameters between all components.
   */
  @Test
  @DisplayName("Check kij between n-butane and ions")
  public void testKijNButaneIons() {
    logger.info("=== kij Parameter Check for n-butane and ions ===\n");

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

    logger.info("Component indices:");
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      logger.info("  " + i + ": " + fluid.getComponent(i).getName() + " (ionic charge: "
	  + fluid.getComponent(i).getIonicCharge() + ")");
    }

    logger.info("\n=== Binary Interaction Parameters (kij) ===");

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

    logger.info("\nn-butane index: " + nButaneIdx);
    logger.info("Na+ index: " + naIdx);
    logger.info("Cl- index: " + clIdx);

    // Check kij between n-butane and all components
    logger.info("\n=== kij values for n-butane with all components ===");
    for (int j = 0; j < fluid.getNumberOfComponents(); j++) {
      double kij = phase.getEosMixingRule().getBinaryInteractionParameter(nButaneIdx, j);
      logger.info("kij(n-butane, " + fluid.getComponent(j).getName() + ") = " + kij);
    }

    // Check kij for Na+ and Cl- with all components
    logger.info("\n=== kij values for Na+ with all components ===");
    for (int j = 0; j < fluid.getNumberOfComponents(); j++) {
      double kij = phase.getEosMixingRule().getBinaryInteractionParameter(naIdx, j);
      logger.info("kij(Na+, " + fluid.getComponent(j).getName() + ") = " + kij);
    }

    logger.info("\n=== kij values for Cl- with all components ===");
    for (int j = 0; j < fluid.getNumberOfComponents(); j++) {
      double kij = phase.getEosMixingRule().getBinaryInteractionParameter(clIdx, j);
      logger.info("kij(Cl-, " + fluid.getComponent(j).getName() + ") = " + kij);
    }

    // Check specific problematic pairs
    logger.info("\n=== Potentially problematic kij values ===");
    double kij_nbutane_na = phase.getEosMixingRule().getBinaryInteractionParameter(nButaneIdx, naIdx);
    double kij_nbutane_cl = phase.getEosMixingRule().getBinaryInteractionParameter(nButaneIdx, clIdx);
    logger.info("kij(n-butane, Na+) = " + kij_nbutane_na);
    logger.info("kij(n-butane, Cl-) = " + kij_nbutane_cl);

    if (Math.abs(kij_nbutane_na) > 1e-10) {
      logger.info("WARNING: kij(n-butane, Na+) is non-zero! This may cause issues.");
    }
    if (Math.abs(kij_nbutane_cl) > 1e-10) {
      logger.info("WARNING: kij(n-butane, Cl-) is non-zero! This may cause issues.");
    }

    // Check if n-butane parameters look unusual
    logger.info("\n=== Component Critical Properties ===");
    logger.info("n-butane TC = " + fluid.getComponent("n-butane").getTC() + " K");
    logger.info("n-butane PC = " + fluid.getComponent("n-butane").getPC() + " bar");
    logger.info("n-butane omega = " + fluid.getComponent("n-butane").getAcentricFactor());
  }

  /**
   * Test fugacity coefficient calculations with ions.
   */
  @Test
  @DisplayName("Check fugacity coefficients for n-butane with ions")
  public void testFugacityWithIons() {
    logger.info("\n=== Fugacity Coefficient Check ===\n");

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

    logger.info("Number of phases: " + fluid.getNumberOfPhases());

    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      logger.info("\nPhase " + p + ": " + fluid.getPhase(p).getPhaseTypeName());
      logger.info("  n-butane:");
      logger.info("    x = " + fluid.getPhase(p).getComponent("n-butane").getx());
      logger.info("    phi = " + fluid.getPhase(p).getComponent("n-butane").getFugacityCoefficient());
      logger.info("    ln(phi) = " + fluid.getPhase(p).getComponent("n-butane").getLogFugacityCoefficient());

      // Check if fugacity coefficient is unreasonably large or small
      double phi = fluid.getPhase(p).getComponent("n-butane").getFugacityCoefficient();
      if (phi > 1e10) {
	logger.info("    WARNING: Extremely large fugacity coefficient!");
      }
      if (phi < 1e-10 && phi > 0) {
	logger.info("    WARNING: Extremely small fugacity coefficient!");
      }
      if (Double.isNaN(phi) || Double.isInfinite(phi)) {
	logger.info("    ERROR: Invalid fugacity coefficient (NaN or Inf)!");
      }
    }
  }

  /**
   * Compare K-values with and without ions.
   */
  @Test
  @DisplayName("Compare n-butane K-values with and without ions")
  public void testKValuesComparison() {
    logger.info("\n=== K-value Comparison ===\n");

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

    logger.info("WITHOUT ions:");
    logger.info("  Phases: " + fluid1.getNumberOfPhases());
    double K1 = fluid1.getPhase(0).getComponent("n-butane").getK();
    logger.info("  K(n-butane) = " + K1);

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

    logger.info("\nWITH ions:");
    logger.info("  Phases: " + fluid2.getNumberOfPhases());
    double K2 = fluid2.getPhase(0).getComponent("n-butane").getK();
    logger.info("  K(n-butane) = " + K2);

    // K-values for ions should be near zero
    if (fluid2.getPhase(0).hasComponent("Na+")) {
      double Kna = fluid2.getPhase(0).getComponent("Na+").getK();
      logger.info("  K(Na+) = " + Kna);
      if (Math.abs(Kna) > 1e-30) {
	logger.info("  WARNING: K(Na+) should be ~0 for ions!");
      }
    }

    logger.info("\nK-value ratio (with/without ions): " + (K2 / K1));
  }
}
