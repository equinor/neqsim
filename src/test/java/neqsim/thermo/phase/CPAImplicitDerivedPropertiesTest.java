package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkCPAstatoilFullyImplicit;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests verifying that derived thermodynamic properties (enthalpy, entropy, Cp, fugacity
 * coefficients, fugacity derivatives) match between fully implicit and standard CPA solver.
 *
 * <p>
 * This ensures the finalization step (solveX, initCPAMatrix, calcdFdNtemp) correctly sets all
 * derivatives that feed into property calculations downstream.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class CPAImplicitDerivedPropertiesTest extends neqsim.NeqSimTest {

  /** Relative tolerance for property comparison. */
  private static final double REL_TOL = 1.0e-6;

  /**
   * Test enthalpy, entropy, Cp for pure water system.
   */
  @Test
  void testThermodynamicPropertiesPureWater() {
    double T = 273.15 + 50.0;
    double P = 10.0;

    SystemInterface standard = createSystem(new SystemSrkCPAstatoil(T, P));
    standard.addComponent("water", 1.0);
    standard.setMixingRule(10);

    SystemInterface implicit = createSystem(new SystemSrkCPAstatoilFullyImplicit(T, P));
    implicit.addComponent("water", 1.0);
    implicit.setMixingRule(10);

    runAndInit(standard);
    runAndInit(implicit);

    comparePhaseProperties(standard, implicit, "PureWater");
  }

  /**
   * Test derived properties for water + methane binary.
   */
  @Test
  void testThermodynamicPropertiesWaterMethane() {
    double T = 273.15 + 30.0;
    double P = 50.0;

    SystemInterface standard = createSystem(new SystemSrkCPAstatoil(T, P));
    standard.addComponent("methane", 0.9);
    standard.addComponent("water", 0.1);
    standard.setMixingRule(10);
    standard.setMultiPhaseCheck(true);

    SystemInterface implicit = createSystem(new SystemSrkCPAstatoilFullyImplicit(T, P));
    implicit.addComponent("methane", 0.9);
    implicit.addComponent("water", 0.1);
    implicit.setMixingRule(10);
    implicit.setMultiPhaseCheck(true);

    runAndInit(standard);
    runAndInit(implicit);

    comparePhaseProperties(standard, implicit, "WaterMethane");
  }

  /**
   * Test derived properties for MEG + water binary.
   */
  @Test
  void testThermodynamicPropertiesMEGWater() {
    double T = 273.15 + 25.0;
    double P = 5.0;

    SystemInterface standard = createSystem(new SystemSrkCPAstatoil(T, P));
    standard.addComponent("MEG", 0.4);
    standard.addComponent("water", 0.6);
    standard.setMixingRule(10);

    SystemInterface implicit = createSystem(new SystemSrkCPAstatoilFullyImplicit(T, P));
    implicit.addComponent("MEG", 0.4);
    implicit.addComponent("water", 0.6);
    implicit.setMixingRule(10);

    runAndInit(standard);
    runAndInit(implicit);

    comparePhaseProperties(standard, implicit, "MEGWater");
  }

  /**
   * Test derived properties for multi-component oil+gas+water+MEG system.
   */
  @Test
  void testThermodynamicPropertiesOilGasWaterMEG() {
    double T = 273.15 + 50.0;
    double P = 80.0;

    SystemInterface standard = createOilGasWaterMEG(new SystemSrkCPAstatoil(T, P));
    SystemInterface implicit = createOilGasWaterMEG(new SystemSrkCPAstatoilFullyImplicit(T, P));

    runAndInit(standard);
    runAndInit(implicit);

    comparePhaseProperties(standard, implicit, "OilGasWaterMEG");
  }

  /**
   * Test fugacity coefficients match for each component in each phase.
   */
  @Test
  void testFugacityCoefficients() {
    double T = 273.15 + 40.0;
    double P = 60.0;

    SystemInterface standard = createOilGasWaterMEG(new SystemSrkCPAstatoil(T, P));
    SystemInterface implicit = createOilGasWaterMEG(new SystemSrkCPAstatoilFullyImplicit(T, P));

    runAndInit(standard);
    runAndInit(implicit);

    int nPhases = standard.getNumberOfPhases();
    assertEquals(nPhases, implicit.getNumberOfPhases(), "Phase count mismatch");

    System.out.println("\n=== Fugacity Coefficient Comparison ===");
    for (int p = 0; p < nPhases; p++) {
      String phName = standard.getPhase(p).getType().toString();
      int nComp = standard.getPhase(p).getNumberOfComponents();
      for (int c = 0; c < nComp; c++) {
        double fugStd = standard.getPhase(p).getComponent(c).getFugacityCoefficient();
        double fugImpl = implicit.getPhase(p).getComponent(c).getFugacityCoefficient();
        String compName = standard.getPhase(p).getComponent(c).getComponentName();

        double tolerance = Math.max(Math.abs(fugStd) * REL_TOL, 1.0e-12);
        assertEquals(fugStd, fugImpl, tolerance,
            phName + "/" + compName + " fugacity coefficient mismatch");
      }
    }
    System.out.println("Fugacity coefficients: ALL MATCH within " + REL_TOL);
  }

  /**
   * Test fugacity coefficient derivatives (dfugdn, dfugdp, dfugdt).
   */
  @Test
  void testFugacityDerivatives() {
    double T = 273.15 + 40.0;
    double P = 60.0;

    SystemInterface standard = createOilGasWaterMEG(new SystemSrkCPAstatoil(T, P));
    SystemInterface implicit = createOilGasWaterMEG(new SystemSrkCPAstatoilFullyImplicit(T, P));

    runAndInit(standard);
    runAndInit(implicit);

    int nPhases = standard.getNumberOfPhases();
    assertEquals(nPhases, implicit.getNumberOfPhases(), "Phase count mismatch");

    System.out.println("\n=== Fugacity Derivative Comparison ===");
    boolean allMatch = true;
    int totalChecks = 0;
    int failCount = 0;

    for (int p = 0; p < nPhases; p++) {
      String phName = standard.getPhase(p).getType().toString();
      int nComp = standard.getPhase(p).getNumberOfComponents();

      for (int c = 0; c < nComp; c++) {
        String compName = standard.getPhase(p).getComponent(c).getComponentName();

        // dfugdp
        double dfugdpStd = standard.getPhase(p).getComponent(c).getdfugdp();
        double dfugdpImpl = implicit.getPhase(p).getComponent(c).getdfugdp();
        double tolP = Math.max(Math.abs(dfugdpStd) * REL_TOL, 1.0e-15);
        totalChecks++;
        if (Math.abs(dfugdpStd - dfugdpImpl) > tolP) {
          System.out.printf("  MISMATCH %s/%s dfugdp: std=%.8e impl=%.8e diff=%.2e%n", phName,
              compName, dfugdpStd, dfugdpImpl, Math.abs(dfugdpStd - dfugdpImpl));
          failCount++;
          allMatch = false;
        }

        // dfugdt
        double dfugdtStd = standard.getPhase(p).getComponent(c).getdfugdt();
        double dfugdtImpl = implicit.getPhase(p).getComponent(c).getdfugdt();
        double tolT = Math.max(Math.abs(dfugdtStd) * REL_TOL, 1.0e-15);
        totalChecks++;
        if (Math.abs(dfugdtStd - dfugdtImpl) > tolT) {
          System.out.printf("  MISMATCH %s/%s dfugdt: std=%.8e impl=%.8e diff=%.2e%n", phName,
              compName, dfugdtStd, dfugdtImpl, Math.abs(dfugdtStd - dfugdtImpl));
          failCount++;
          allMatch = false;
        }

        // dfugdn (cross-derivatives)
        for (int c2 = 0; c2 < nComp; c2++) {
          double dfugdnStd = standard.getPhase(p).getComponent(c).getdfugdn(c2);
          double dfugdnImpl = implicit.getPhase(p).getComponent(c).getdfugdn(c2);
          double tolN = Math.max(Math.abs(dfugdnStd) * REL_TOL, 1.0e-15);
          totalChecks++;
          if (Math.abs(dfugdnStd - dfugdnImpl) > tolN) {
            System.out.printf("  MISMATCH %s/%s dfugdn[%s]: std=%.8e impl=%.8e diff=%.2e%n", phName,
                compName, standard.getPhase(p).getComponent(c2).getComponentName(), dfugdnStd,
                dfugdnImpl, Math.abs(dfugdnStd - dfugdnImpl));
            failCount++;
            allMatch = false;
          }
        }
      }
    }

    System.out.printf("Derivs checked: %d  Failures: %d%n", totalChecks, failCount);
    assertTrue(allMatch,
        "Fugacity derivative mismatch: " + failCount + " out of " + totalChecks + " checks failed");
  }

  /**
   * Test enthalpy and entropy at multiple T,P conditions to verify consistency.
   */
  @Test
  void testEnthalpyEntropyConsistency() {
    double[][] conditions =
        {{273.15 + 10, 1.0}, {273.15 + 50, 50.0}, {273.15 + 80, 100.0}, {273.15 + 120, 200.0}};

    System.out.println("\n=== Enthalpy/Entropy Consistency ===");
    System.out.printf("%-12s %12s %12s %12s %12s %12s%n", "T(C)/P(bar)", "H_std(J/mol)", "H_impl",
        "S_std(J/K)", "S_impl", "Cp_match");

    for (double[] tp : conditions) {
      double T = tp[0];
      double P = tp[1];

      SystemInterface standard = new SystemSrkCPAstatoil(T, P);
      standard.addComponent("water", 0.7);
      standard.addComponent("MEG", 0.3);
      standard.setMixingRule(10);

      SystemInterface implicit = new SystemSrkCPAstatoilFullyImplicit(T, P);
      implicit.addComponent("water", 0.7);
      implicit.addComponent("MEG", 0.3);
      implicit.setMixingRule(10);

      runAndInit(standard);
      runAndInit(implicit);

      double hStd = standard.getEnthalpy();
      double hImpl = implicit.getEnthalpy();
      double sStd = standard.getEntropy();
      double sImpl = implicit.getEntropy();
      double cpStd = standard.getCp();
      double cpImpl = implicit.getCp();

      boolean cpMatch = Math.abs(cpStd - cpImpl) < Math.max(Math.abs(cpStd) * REL_TOL, 1.0e-6);

      System.out.printf("%.0f/%.0f %12.2f %12.2f %12.4f %12.4f %s%n", T - 273.15, P, hStd, hImpl,
          sStd, sImpl, cpMatch ? "YES" : "NO");

      assertEquals(hStd, hImpl, Math.max(Math.abs(hStd) * REL_TOL, 1.0),
          "Enthalpy mismatch at T=" + (T - 273.15) + " P=" + P);
      assertEquals(sStd, sImpl, Math.max(Math.abs(sStd) * REL_TOL, 0.01),
          "Entropy mismatch at T=" + (T - 273.15) + " P=" + P);
      assertEquals(cpStd, cpImpl, Math.max(Math.abs(cpStd) * REL_TOL, 1.0e-3),
          "Cp mismatch at T=" + (T - 273.15) + " P=" + P);
    }
  }

  // ------- Helper methods -------

  private SystemInterface createSystem(SystemInterface sys) {
    return sys;
  }

  private SystemInterface createOilGasWaterMEG(SystemInterface sys) {
    sys.addComponent("methane", 0.40);
    sys.addComponent("ethane", 0.05);
    sys.addComponent("propane", 0.04);
    sys.addComponent("n-butane", 0.03);
    sys.addComponent("n-pentane", 0.03);
    sys.addComponent("n-hexane", 0.04);
    sys.addComponent("n-heptane", 0.08);
    sys.addComponent("n-octane", 0.05);
    sys.addComponent("water", 0.25);
    sys.addComponent("MEG", 0.03);
    sys.setMixingRule(10);
    sys.setMultiPhaseCheck(true);
    return sys;
  }

  private void runAndInit(SystemInterface sys) {
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();
    sys.initProperties();
  }

  private void comparePhaseProperties(SystemInterface standard, SystemInterface implicit,
      String label) {
    int nPhStd = standard.getNumberOfPhases();
    int nPhImpl = implicit.getNumberOfPhases();
    assertEquals(nPhStd, nPhImpl, label + " phase count mismatch");

    System.out.println("\n=== " + label + " Phase Properties ===");
    for (int p = 0; p < nPhStd; p++) {
      String phName = standard.getPhase(p).getType().toString();

      // Density
      double densStd = standard.getPhase(p).getDensity("kg/m3");
      double densImpl = implicit.getPhase(p).getDensity("kg/m3");
      assertEquals(densStd, densImpl, Math.max(Math.abs(densStd) * REL_TOL, 0.01),
          label + "/" + phName + " density");

      // Enthalpy
      double hStd = standard.getPhase(p).getEnthalpy();
      double hImpl = implicit.getPhase(p).getEnthalpy();
      assertEquals(hStd, hImpl, Math.max(Math.abs(hStd) * REL_TOL, 1.0),
          label + "/" + phName + " enthalpy");

      // Entropy
      double sStd = standard.getPhase(p).getEntropy();
      double sImpl = implicit.getPhase(p).getEntropy();
      assertEquals(sStd, sImpl, Math.max(Math.abs(sStd) * REL_TOL, 0.01),
          label + "/" + phName + " entropy");

      // Cp
      double cpStd = standard.getPhase(p).getCp();
      double cpImpl = implicit.getPhase(p).getCp();
      assertEquals(cpStd, cpImpl, Math.max(Math.abs(cpStd) * REL_TOL, 1.0e-3),
          label + "/" + phName + " Cp");

      // Compressibility
      double zStd = standard.getPhase(p).getZ();
      double zImpl = implicit.getPhase(p).getZ();
      assertEquals(zStd, zImpl, Math.max(Math.abs(zStd) * REL_TOL, 1.0e-8),
          label + "/" + phName + " Z");

      System.out.printf("  %s: dens=%.4f/%.4f  H=%.1f/%.1f  S=%.4f/%.4f  Cp=%.4f/%.4f  MATCH%n",
          phName, densStd, densImpl, hStd, hImpl, sStd, sImpl, cpStd, cpImpl);
    }
  }
}
