package neqsim.physicalproperties.interfaceproperties.surfacetension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Comprehensive benchmark of the cDFT surface tension model against experimental data and other
 * NeqSim IFT models (Parachor, Gradient Theory) across multiple pure components and EOS types.
 */
public class CDFTSurfaceTensionBenchmarkTest {

  /**
   * Computes IFT using a specified model.
   *
   * @param system the thermodynamic system (after VLE flash)
   * @param model the IFT model name
   * @return IFT in mN/m, or -1 if calculation fails
   */
  private double computeIFT(SystemInterface system, String model) {
    try {
      system.getInterphaseProperties().setInterfacialTensionModel("gas", "oil", model);
      double sigma = system.getInterphaseProperties().getSurfaceTension(0, 1);
      return sigma * 1000.0;
    } catch (Exception ex) {
      return -1.0;
    }
  }

  /**
   * Sets up a pure-component system at VLE.
   *
   * @param eosType "PR" or "SRK"
   * @param component component name
   * @param tempK temperature in Kelvin
   * @return system at VLE, or null if flash fails
   */
  private SystemInterface setupVLE(String eosType, String component, double tempK) {
    SystemInterface sys;
    if ("SRK".equals(eosType)) {
      sys = new SystemSrkEos(tempK, 1.0);
    } else {
      sys = new SystemPrEos(tempK, 1.0);
    }
    sys.addComponent(component, 1.0);
    sys.setMixingRule("classic");
    sys.setMultiPhaseCheck(true);

    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    try {
      ops.bubblePointPressureFlash(false);
    } catch (Exception ex) {
      return null;
    }
    sys.initProperties();
    if (sys.getNumberOfPhases() < 2) {
      return null;
    }
    return sys;
  }

  /**
   * Prints a benchmark comparison table row.
   */
  private void printRow(String component, String eos, double tempK, double sigmaExp,
      double sigmaCDFT, double sigmaGT, double sigmaParachor) {
    double devCDFT = sigmaExp > 0 ? 100.0 * (sigmaCDFT - sigmaExp) / sigmaExp : Double.NaN;
    double devGT = sigmaExp > 0 ? 100.0 * (sigmaGT - sigmaExp) / sigmaExp : Double.NaN;
    double devParachor = sigmaExp > 0 ? 100.0 * (sigmaParachor - sigmaExp) / sigmaExp : Double.NaN;
    System.out.printf(
        "| %-12s | %-3s | %6.1f | %6.2f | %6.2f (%+5.1f%%) | %6.2f (%+5.1f%%) | %6.2f (%+5.1f%%) |%n",
        component, eos, tempK, sigmaExp, sigmaCDFT, devCDFT, sigmaGT, devGT, sigmaParachor,
        devParachor);
  }

  @Test
  void benchmarkPureComponents() {
    // Experimental IFT data (mN/m) from NIST / literature
    // Format: {component, T(K), sigma_exp (mN/m)}
    String[][] testCases = {{"methane", "90.7", "18.9"}, // Near triple point
        {"methane", "111.0", "14.9"}, // Normal boiling point
        {"methane", "120.0", "13.0"}, {"methane", "150.0", "6.6"}, {"methane", "170.0", "2.8"},
        {"ethane", "184.0", "17.1"}, {"ethane", "230.0", "9.4"}, {"ethane", "270.0", "3.6"},
        {"propane", "230.0", "13.6"}, {"propane", "270.0", "8.6"}, {"propane", "320.0", "3.4"},
        {"n-butane", "270.0", "13.2"}, {"n-butane", "320.0", "7.6"}, {"n-pentane", "300.0", "14.6"},
        {"n-pentane", "350.0", "9.1"}, {"n-hexane", "300.0", "16.3"}, {"n-hexane", "340.0", "12.5"},
        {"n-hexane", "400.0", "5.8"}, {"nitrogen", "77.0", "9.4"}, {"nitrogen", "90.0", "6.2"},
        {"CO2", "220.0", "15.5"}, {"CO2", "250.0", "8.5"}, {"CO2", "280.0", "2.6"},};

    String[] eosTypes = {"PR", "SRK"};

    System.out.println();
    System.out.println("=== cDFT Surface Tension Benchmark ===");
    System.out.printf("| %-12s | %-3s | %6s | %6s | %19s | %19s | %19s |%n", "Component", "EOS",
        "T (K)", "Exp", "cDFT", "Full GT", "Parachor");
    System.out.println("|--------------|-----|--------|--------|"
        + "---------------------|---------------------|---------------------|");

    int totalTests = 0;
    int cdftWithin50pct = 0;
    double sumAbsDevCDFT = 0;

    for (String eosType : eosTypes) {
      for (String[] tc : testCases) {
        String component = tc[0];
        double tempK = Double.parseDouble(tc[1]);
        double sigmaExp = Double.parseDouble(tc[2]);

        SystemInterface sys = setupVLE(eosType, component, tempK);
        if (sys == null) {
          continue;
        }

        double sigmaCDFT = computeIFT(sys, "cDFT");
        double sigmaGT = computeIFT(sys, "Full Gradient Theory");
        double sigmaParachor = computeIFT(sys, "Parachor");

        printRow(component, eosType, tempK, sigmaExp, sigmaCDFT, sigmaGT, sigmaParachor);

        if (sigmaCDFT > 0) {
          totalTests++;
          double absDevPct = Math.abs(100.0 * (sigmaCDFT - sigmaExp) / sigmaExp);
          sumAbsDevCDFT += absDevPct;
          if (absDevPct < 50.0) {
            cdftWithin50pct++;
          }
        }
      }
    }

    double avgAbsDev = totalTests > 0 ? sumAbsDevCDFT / totalTests : 999;
    System.out.println();
    System.out.println("cDFT summary: " + totalTests + " systems computed, " + cdftWithin50pct
        + " within 50% of experiment, avg abs dev = " + String.format("%.1f", avgAbsDev) + "%");

    // Basic assertions
    assertTrue(totalTests >= 20, "Should compute at least 20 IFT values, got " + totalTests);
    assertTrue(cdftWithin50pct >= totalTests / 2,
        "At least half should be within 50% of experiment");
  }

  @Test
  void testMethaneTemperatureSweep_PR() {
    // Sweep methane from 95K to 180K (near Tc=190.6K)
    System.out.println();
    System.out.println("=== Methane IFT Temperature Sweep (PR EOS) ===");
    System.out.printf("| %6s | %8s | %8s | %8s |%n", "T (K)", "cDFT", "Full GT", "Parachor");

    double[] temps = {95, 100, 110, 120, 130, 140, 150, 160, 170, 180};
    int validCount = 0;
    for (double t : temps) {
      SystemInterface sys = setupVLE("PR", "methane", t);
      if (sys == null) {
        continue;
      }
      double cdft = computeIFT(sys, "cDFT");
      double gt = computeIFT(sys, "Full Gradient Theory");
      double par = computeIFT(sys, "Parachor");

      System.out.printf("| %6.1f | %8.3f | %8.3f | %8.3f |%n", t, cdft, gt, par);
      if (cdft > 0) {
        validCount++;
      }
    }
    assertTrue(validCount >= 8, "At least 8 of 10 temperature points should give valid IFT");
  }

  @Test
  void testHeavierAlkanes_PR() {
    // Test heavier alkanes at 300K
    System.out.println();
    System.out.println("=== Heavier Alkanes at 300K (PR EOS) ===");
    System.out.printf("| %-12s | %8s | %8s |%n", "Component", "cDFT", "Parachor");

    String[] components = {"n-pentane", "n-hexane", "n-heptane", "n-octane", "n-nonane", "nC10"};

    for (String comp : components) {
      SystemInterface sys = setupVLE("PR", comp, 300.0);
      if (sys == null) {
        System.out.printf("| %-12s |  no VLE  |  no VLE  |%n", comp);
        continue;
      }
      double cdft = computeIFT(sys, "cDFT");
      double par = computeIFT(sys, "Parachor");
      System.out.printf("| %-12s | %8.3f | %8.3f |%n", comp, cdft, par);
      assertTrue(cdft > 0, comp + " cDFT IFT should be positive: " + cdft);
    }
  }
}
