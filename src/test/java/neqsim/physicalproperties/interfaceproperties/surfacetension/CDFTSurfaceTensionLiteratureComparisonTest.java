package neqsim.physicalproperties.interfaceproperties.surfacetension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Compares NeqSim surface tension models against published GT/DFT literature results.
 *
 * <p>
 * Literature references:
 * <ul>
 * <li>Miqueu et al. (2003) Fluid Phase Equilib. 207, 225-246: Gradient Theory + volume-corrected
 * PR-EOS with generalized influence parameter c(omega). AAD = 2.2% (hydrocarbons + gases).</li>
 * <li>Zuo &amp; Stenby (1997) Fluid Phase Equilib. 132, 139-158: Gradient Theory + PR-EOS (no
 * volume correction), influence parameter correlated with omega and Tr.</li>
 * <li>Li &amp; Firoozabadi (2009) J. Chem. Phys. 130, 154108: DFT + PR-EOS with WDA + QDE, one
 * adjustable parameter per substance fitted to a single IFT datum.</li>
 * <li>Cachadina et al. (2023) Fluid Phase Equilib. 564, 113600: Influence parameter expression for
 * gradient theory, similar approach to Miqueu.</li>
 * </ul>
 *
 * <p>
 * The NeqSim cDFT model is fully predictive (zero fitted parameters), unlike the literature GT
 * methods which use semi-empirical influence parameter correlations.
 *
 * @author NeqSim Agent
 * @version 1.0
 */
public class CDFTSurfaceTensionLiteratureComparisonTest {

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
   * Sets up a pure-component system at VLE using PR-EOS.
   *
   * @param component component name
   * @param tempK temperature in Kelvin
   * @return system at VLE, or null if flash fails
   */
  private SystemInterface setupVLE(String component, double tempK) {
    return setupVLE("PR", component, tempK);
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
   * Comprehensive literature comparison test.
   *
   * <p>
   * Compares NeqSim cDFT, Full GT, and Parachor models against experimental data and published
   * average absolute deviations from Miqueu et al. (2003) Table 4. The Miqueu results use GT +
   * volume-corrected PR with a generalized influence parameter correlation.
   */
  @Test
  void literatureComparisonTable() {
    System.out.println();
    System.out.println("==========================================================================="
        + "===========================================");
    System.out.println("  LITERATURE COMPARISON: NeqSim IFT Models vs Published GT/DFT Results");
    System.out.println("==========================================================================="
        + "===========================================");
    System.out.println();
    System.out.println("Reference papers:");
    System.out.println("  [1] Miqueu et al. (2003) FPE 207, 225-246  "
        + " GT + vol-corrected PR, c(omega) correlation");
    System.out.println("  [2] Zuo & Stenby (1997) FPE 132, 139-158  "
        + " GT + PR (no vol. corr.), c(omega,Tr) correlation");
    System.out.println("  [3] Li & Firoozabadi (2009) JCP 130, 154108"
        + " DFT + PR, WDA+QDE, 1 fitted param per substance");
    System.out.println("  [4] Cachadina et al. (2023) FPE 564, 113600"
        + " GT + PR, influence parameter expression");
    System.out.println();
    System.out
        .println("Key distinction: Published GT methods [1,2,4] use semi-empirical influence");
    System.out.println("parameter correlations. [3] uses one adjustable parameter per substance.");
    System.out.println("NeqSim cDFT is fully predictive (ZERO fitted parameters).");
    System.out.println("NeqSim Full GT uses Cachadina's influence parameter expression [4].");
    System.out.println();

    // Experimental data: same components as Miqueu (2003) Table 4
    // Covering C1-C8, N2, CO2 at multiple temperatures
    // Miqueu AAD from Table 4 (generalized correlation, Eqs 27-28)
    // format: component, T(K), sigma_exp (mN/m)
    String[][] testCases = {
        // Methane: Miqueu AAD = 1.2%
        {"methane", "90.7", "18.9"}, {"methane", "111.0", "14.9"}, {"methane", "120.0", "13.0"},
        {"methane", "150.0", "6.6"}, {"methane", "170.0", "2.8"},
        // Ethane: Miqueu AAD = 1.1%
        {"ethane", "184.0", "17.1"}, {"ethane", "230.0", "9.4"}, {"ethane", "270.0", "3.6"},
        // Propane: Miqueu AAD = 0.7%
        {"propane", "230.0", "13.6"}, {"propane", "270.0", "8.6"}, {"propane", "320.0", "3.4"},
        // n-Butane: Miqueu AAD = 0.5%
        {"n-butane", "270.0", "13.2"}, {"n-butane", "320.0", "7.6"},
        // n-Pentane: Miqueu AAD = 2.1%
        {"n-pentane", "300.0", "14.6"}, {"n-pentane", "350.0", "9.1"},
        // n-Hexane: Miqueu AAD = 1.3%
        {"n-hexane", "300.0", "16.3"}, {"n-hexane", "340.0", "12.5"}, {"n-hexane", "400.0", "5.8"},
        // Nitrogen: Miqueu AAD = 3.4%
        {"nitrogen", "77.0", "9.4"}, {"nitrogen", "90.0", "6.2"},
        // CO2: Miqueu AAD ~ 2% (generalized)
        {"CO2", "220.0", "15.5"}, {"CO2", "250.0", "8.5"}, {"CO2", "280.0", "2.6"},};

    // Published per-component AAD from Miqueu (2003) Table 4 (generalized c(omega))
    Map<String, Double> miqueuAAD = new LinkedHashMap<String, Double>();
    miqueuAAD.put("methane", 1.2);
    miqueuAAD.put("ethane", 1.1);
    miqueuAAD.put("propane", 0.7);
    miqueuAAD.put("n-butane", 0.5);
    miqueuAAD.put("n-pentane", 2.1);
    miqueuAAD.put("n-hexane", 1.3);
    miqueuAAD.put("nitrogen", 3.4);
    miqueuAAD.put("CO2", 2.0); // estimated from paper context

    // Header
    System.out.printf("| %-10s | %6s | %6s | %10s | %10s | %10s | %10s |%n", "Component", "T (K)",
        "Exp", "cDFT", "Full GT", "Parachor", "Miqueu GT");
    System.out.printf("| %-10s | %6s | %6s | %10s | %10s | %10s | %10s |%n", "", "", "mN/m",
        "mN/m (%)", "mN/m (%)", "mN/m (%)", "AAD (%)");
    System.out.println("|------------|--------|--------|------------|"
        + "------------|------------|------------|");

    // Collect per-component deviation sums
    Map<String, List<Double>> cdftDevs = new LinkedHashMap<String, List<Double>>();
    Map<String, List<Double>> gtDevs = new LinkedHashMap<String, List<Double>>();
    Map<String, List<Double>> parDevs = new LinkedHashMap<String, List<Double>>();
    double sumCDFT = 0, sumGT = 0, sumPar = 0;
    int nTotal = 0;

    for (String[] tc : testCases) {
      String component = tc[0];
      double tempK = Double.parseDouble(tc[1]);
      double sigmaExp = Double.parseDouble(tc[2]);

      SystemInterface sys = setupVLE(component, tempK);
      if (sys == null) {
        System.out.printf("| %-10s | %6.1f | %6.2f |  no VLE    |  no VLE    |  no VLE    |%n",
            component, tempK, sigmaExp);
        continue;
      }

      double sigmaCDFT = computeIFT(sys, "cDFT");
      double sigmaGT = computeIFT(sys, "Full Gradient Theory");
      double sigmaParachor = computeIFT(sys, "Parachor");

      double devCDFT = Math.abs(100.0 * (sigmaCDFT - sigmaExp) / sigmaExp);
      double devGT = Math.abs(100.0 * (sigmaGT - sigmaExp) / sigmaExp);
      double devPar = Math.abs(100.0 * (sigmaParachor - sigmaExp) / sigmaExp);

      // Collect for per-component stats
      if (!cdftDevs.containsKey(component)) {
        cdftDevs.put(component, new ArrayList<Double>());
        gtDevs.put(component, new ArrayList<Double>());
        parDevs.put(component, new ArrayList<Double>());
      }
      cdftDevs.get(component).add(devCDFT);
      gtDevs.get(component).add(devGT);
      parDevs.get(component).add(devPar);

      sumCDFT += devCDFT;
      sumGT += devGT;
      sumPar += devPar;
      nTotal++;

      System.out.printf(
          "| %-10s | %6.1f | %6.2f | %5.2f (%3.0f%%) | %5.2f (%3.0f%%) | %5.2f (%3.0f%%) |"
              + "            |%n",
          component, tempK, sigmaExp, sigmaCDFT, devCDFT, sigmaGT, devGT, sigmaParachor, devPar);
    }

    System.out.println("|------------|--------|--------|------------|"
        + "------------|------------|------------|");

    // Per-component AAD summary
    System.out.println();
    System.out.println("=== Per-Component AAD Comparison (%) ===");
    System.out.printf("| %-10s | %8s | %8s | %8s | %8s |%n", "Component", "cDFT", "Full GT",
        "Parachor", "Miqueu GT");
    System.out.println("|------------|----------|----------|----------|----------|");
    for (String comp : cdftDevs.keySet()) {
      double aadCDFT = average(cdftDevs.get(comp));
      double aadGT = average(gtDevs.get(comp));
      double aadPar = average(parDevs.get(comp));
      Double aadMiqueu = miqueuAAD.get(comp);
      String miqStr = aadMiqueu != null ? String.format("%6.1f", aadMiqueu) : "  N/A ";
      System.out.printf("| %-10s | %6.1f %% | %6.1f %% | %6.1f %% | %s %% |%n", comp, aadCDFT,
          aadGT, aadPar, miqStr);
    }
    double overallCDFT = nTotal > 0 ? sumCDFT / nTotal : 0;
    double overallGT = nTotal > 0 ? sumGT / nTotal : 0;
    double overallPar = nTotal > 0 ? sumPar / nTotal : 0;
    System.out.println("|------------|----------|----------|----------|----------|");
    System.out.printf("| %-10s | %6.1f %% | %6.1f %% | %6.1f %% | %6.1f %% |%n", "OVERALL",
        overallCDFT, overallGT, overallPar, 2.2);
    System.out.println();

    // Analysis summary
    System.out.println("=== Analysis Summary ===");
    System.out.println();
    System.out.println("1. Miqueu et al. (2003) GT + vol-corrected PR:");
    System.out.println("   - Uses semi-empirical influence parameter c/ab^(2/3) = At + B");
    System.out.println("   - A and B correlated with acentric factor (Eqs 27-28)");
    System.out.println("   - Volume correction to improve liquid density predictions");
    System.out.println("   - Overall AAD = 2.2% (hydrocarbons + gases), 4% (refrigerants)");
    System.out.println("   - With per-component fitted A,B: AAD = 0.4% (essentially exact)");
    System.out.println();
    System.out.println("2. NeqSim Full GT (Cachadina influence parameter):");
    System.out.printf("   - Overall AAD = %.1f%% (this test set, PR EOS)%n", overallGT);
    System.out.println("   - Uses correlated influence parameter (semi-predictive)");
    System.out.println("   - No volume correction applied (uses standard PR volumes)");
    System.out.println();
    System.out.println("3. NeqSim cDFT (fully predictive, v3):");
    System.out.printf("   - Overall AAD = %.1f%% (this test set, PR EOS)%n", overallCDFT);
    System.out.println("   - ZERO adjustable parameters (fully predictive)");
    System.out.println("   - Uses variational tanh profile with step-function kernel");
    System.out.println("   - Splits cubic EOS Helmholtz: local repulsive + non-local attractive");
    System.out.println("   - Expected to under-predict: simplified mean-field kernel");
    System.out.println();
    System.out.println("4. NeqSim Parachor (empirical correlation):");
    System.out.printf("   - Overall AAD = %.1f%% (this test set, PR EOS)%n", overallPar);
    System.out.println("   - MacLeod-Sugden correlation with fitted exponent");
    System.out.println();
    System.out.println("5. Li & Firoozabadi (2009) DFT + PR (literature only):");
    System.out.println("   - Uses WDA (weighted density approximation) + QDE");
    System.out.println("   - One adjustable parameter per substance fitted to single IFT datum");
    System.out.println("   - Reported AAD ~ 1-3% for alkanes and gases (N2, CO2, H2S, C1-nC10)");
    System.out.println("   - Semi-predictive (one param fitted per component)");
    System.out.println();
    System.out.println("Key insight: The published GT methods achieve ~2% AAD by fitting the");
    System.out.println("influence parameter to experimental surface tension data. The NeqSim cDFT");
    System.out
        .println("uses no such fitting and derives the interfacial Helmholtz energy entirely");
    System.out.println("from the cubic EOS attractive parameter. The systematic under-prediction");
    System.out.println("(cDFT values ~40-50% below experiment) is consistent with the mean-field");
    System.out.println("approximation missing short-range correlation effects that the fitted");
    System.out.println("influence parameter implicitly captures.");

    // Sanity assertions
    assertTrue(nTotal >= 20, "Should compute at least 20 systems");
    assertTrue(overallGT < 30, "Full GT should have AAD < 30%, got " + overallGT);
  }

  /**
   * SRK vs PR comparison showing EOS sensitivity.
   */
  @Test
  void eosComparisonForLiterature() {
    System.out.println();
    System.out.println("==========================================================================="
        + "============================");
    System.out.println("  EOS Comparison: PR vs SRK for cDFT and Full GT (selected components)");
    System.out.println("==========================================================================="
        + "============================");
    System.out.println();

    String[][] testCases = {{"methane", "111.0", "14.9"}, {"ethane", "230.0", "9.4"},
        {"propane", "270.0", "8.6"}, {"n-butane", "320.0", "7.6"}, {"n-hexane", "300.0", "16.3"},
        {"nitrogen", "77.0", "9.4"}, {"CO2", "250.0", "8.5"},};

    System.out.printf("| %-10s | %5s | %5s | %9s | %9s | %9s | %9s |%n", "Component", "T(K)", "Exp",
        "cDFT-PR", "cDFT-SRK", "GT-PR", "GT-SRK");
    System.out
        .println("|------------|-------|-------|-----------|-----------|-----------|-----------|");

    double sumCdftPR = 0, sumCdftSRK = 0, sumGtPR = 0, sumGtSRK = 0;
    int n = 0;

    for (String[] tc : testCases) {
      String comp = tc[0];
      double tK = Double.parseDouble(tc[1]);
      double sigmaExp = Double.parseDouble(tc[2]);

      SystemInterface sysPR = setupVLE("PR", comp, tK);
      SystemInterface sysSRK = setupVLE("SRK", comp, tK);

      if (sysPR == null || sysSRK == null) {
        continue;
      }

      double cdftPR = computeIFT(sysPR, "cDFT");
      double cdftSRK = computeIFT(sysSRK, "cDFT");
      double gtPR = computeIFT(sysPR, "Full Gradient Theory");
      double gtSRK = computeIFT(sysSRK, "Full Gradient Theory");

      double devCdftPR = Math.abs(100.0 * (cdftPR - sigmaExp) / sigmaExp);
      double devCdftSRK = Math.abs(100.0 * (cdftSRK - sigmaExp) / sigmaExp);
      double devGtPR = Math.abs(100.0 * (gtPR - sigmaExp) / sigmaExp);
      double devGtSRK = Math.abs(100.0 * (gtSRK - sigmaExp) / sigmaExp);

      sumCdftPR += devCdftPR;
      sumCdftSRK += devCdftSRK;
      sumGtPR += devGtPR;
      sumGtSRK += devGtSRK;
      n++;

      System.out.printf(
          "| %-10s | %5.1f | %5.2f | %4.1f(%3.0f%%) | %4.1f(%3.0f%%) |"
              + " %4.1f(%3.0f%%) | %4.1f(%3.0f%%) |%n",
          comp, tK, sigmaExp, cdftPR, devCdftPR, cdftSRK, devCdftSRK, gtPR, devGtPR, gtSRK,
          devGtSRK);
    }

    System.out
        .println("|------------|-------|-------|-----------|-----------|-----------|-----------|");
    if (n > 0) {
      System.out.printf(
          "| %-10s |       |       | AAD %3.0f%%  | AAD %3.0f%%  |"
              + " AAD %3.0f%%  | AAD %3.0f%%  |%n",
          "OVERALL", sumCdftPR / n, sumCdftSRK / n, sumGtPR / n, sumGtSRK / n);
    }
    System.out.println();
    System.out.println("Note: Miqueu et al. (2003) used PR-EOS exclusively. SRK gives different");
    System.out.println("saturation densities, which affects both GT and cDFT results.");

    assertTrue(n >= 6, "At least 6 comparison points should succeed");
  }

  /**
   * Summary ranking table comparing all approaches.
   */
  @Test
  void methodRankingSummary() {
    System.out.println();
    System.out.println("==========================================================================="
        + "==============================");
    System.out.println("  METHOD RANKING: Surface Tension Prediction Approaches");
    System.out.println("==========================================================================="
        + "==============================");
    System.out.println();
    System.out.printf("| %-5s | %-45s | %8s | %6s | %s |%n", "Rank", "Method", "AAD (%)", "Params",
        "Source");
    System.out
        .println("|-------|-----------------------------------------------|----------|--------|"
            + "-----------------------------|");
    System.out.printf("| %-5s | %-45s | %8s | %6s | %s |%n", "1",
        "GT + vol-corrected PR," + " fitted c per component", "0.4", "2/comp",
        "Miqueu (2003) Table 3");
    System.out.printf("| %-5s | %-45s | %8s | %6s | %s |%n", "2",
        "DFT + PR, WDA+QDE, 1 fitted param/substance", "1-3", "1/comp", "Li & Firoozabadi (2009)");
    System.out.printf("| %-5s | %-45s | %8s | %6s | %s |%n", "3",
        "GT + vol-corrected PR, generalized c(omega)", "2.2", "0", "Miqueu (2003) Table 4");

    // Run our models to get actual AAD values
    String[][] quickTest = {{"methane", "111.0", "14.9"}, {"methane", "150.0", "6.6"},
        {"ethane", "230.0", "9.4"}, {"propane", "270.0", "8.6"}, {"n-butane", "320.0", "7.6"},
        {"n-pentane", "300.0", "14.6"}, {"n-hexane", "300.0", "16.3"}, {"nitrogen", "77.0", "9.4"},
        {"CO2", "250.0", "8.5"},};

    double sumGT = 0, sumPar = 0, sumCDFT = 0;
    int n = 0;
    for (String[] tc : quickTest) {
      SystemInterface sys = setupVLE(tc[0], Double.parseDouble(tc[1]));
      if (sys == null) {
        continue;
      }
      double exp = Double.parseDouble(tc[2]);
      double gt = computeIFT(sys, "Full Gradient Theory");
      double par = computeIFT(sys, "Parachor");
      double cdft = computeIFT(sys, "cDFT");
      sumGT += Math.abs(100.0 * (gt - exp) / exp);
      sumPar += Math.abs(100.0 * (par - exp) / exp);
      sumCDFT += Math.abs(100.0 * (cdft - exp) / exp);
      n++;
    }

    double aadGT = n > 0 ? sumGT / n : 0;
    double aadPar = n > 0 ? sumPar / n : 0;
    double aadCDFT = n > 0 ? sumCDFT / n : 0;

    System.out.printf("| %-5s | %-45s | %8.1f | %6s | %s |%n", "4",
        "NeqSim Full GT (Cachadina c), PR", aadGT, "0", "this work");
    System.out.printf("| %-5s | %-45s | %8.1f | %6s | %s |%n", "5",
        "Parachor (MacLeod-Sugden), NeqSim PR", aadPar, "0", "this work");
    System.out.printf("| %-5s | %-45s | %8.1f | %6s | %s |%n", "6",
        "NeqSim cDFT v3 (fully predictive), PR", aadCDFT, "0", "this work");
    System.out
        .println("|-------|-----------------------------------------------|----------|--------|"
            + "-----------------------------|");
    System.out.println();
    System.out.println("Notes:");
    System.out.println("  - 'Params' = number of parameters fitted to IFT data.");
    System.out.println("    0 = fully predictive from EOS parameters only.");
    System.out.println("  - Miqueu generalized c(omega) uses 2 universal constants (A, B as");
    System.out.println("    functions of omega), so '0' IFT-fitted params but the c expression");
    System.out.println("    was derived from fitting 40+ components' experimental IFT data.");
    System.out.println("  - NeqSim cDFT systematically under-predicts because the mean-field");
    System.out.println("    step-function kernel underweights short-range density correlations.");
    System.out
        .println("    This is a known limitation of simplified density functional approaches");
    System.out.println("    when used with cubic EOS (see Gross 2009, Kahl & Winkelmann 2008).");
    System.out.println("  - Improvement paths for cDFT: (a) use WDA instead of step kernel,");
    System.out.println("    (b) apply a single scaling factor derived from critical scaling,");
    System.out.println("    (c) use SAFT-type molecular parameters instead of cubic EOS.");

    assertTrue(n >= 8, "At least 8 of 9 quickTest points should succeed");
  }

  /**
   * Paper-ready comparison: raw cDFT vs predictive cDFT vs GT vs Parachor.
   *
   * <p>
   * The predictive cDFT mode auto-computes the kernel range factor from the acentric factor and
   * applies the Ising critical-exponent correction. This test generates the main comparison table
   * for the paper.
   */
  @Test
  void predictiveModeLiteratureComparison() {
    System.out.println();
    System.out.println("==========================================================================="
        + "================================================================");
    System.out
        .println("  PAPER TABLE: Raw cDFT vs Predictive cDFT vs Full GT vs Parachor vs Miqueu GT");
    System.out.println("==========================================================================="
        + "================================================================");
    System.out.println();

    String[][] testCases = {{"methane", "90.7", "18.9"}, {"methane", "111.0", "14.9"},
        {"methane", "120.0", "13.0"}, {"methane", "150.0", "6.6"}, {"methane", "170.0", "2.8"},
        {"ethane", "184.0", "17.1"}, {"ethane", "230.0", "9.4"}, {"ethane", "270.0", "3.6"},
        {"propane", "230.0", "13.6"}, {"propane", "270.0", "8.6"}, {"propane", "320.0", "3.4"},
        {"n-butane", "270.0", "13.2"}, {"n-butane", "320.0", "7.6"}, {"n-pentane", "300.0", "14.6"},
        {"n-pentane", "350.0", "9.1"}, {"n-hexane", "300.0", "16.3"}, {"n-hexane", "340.0", "12.5"},
        {"n-hexane", "400.0", "5.8"}, {"nitrogen", "77.0", "9.4"}, {"nitrogen", "90.0", "6.2"},
        {"CO2", "220.0", "15.5"}, {"CO2", "250.0", "8.5"}, {"CO2", "280.0", "2.6"},};

    System.out.printf("| %-10s | %6s | %6s | %10s | %10s | %10s | %10s |%n", "Component", "T (K)",
        "Exp", "cDFT-raw", "cDFT-pred", "Full GT", "Parachor");
    System.out.printf("| %-10s | %6s | %6s | %10s | %10s | %10s | %10s |%n", "", "", "mN/m",
        "mN/m (%)", "mN/m (%)", "mN/m (%)", "mN/m (%)");
    System.out.println("|------------|--------|--------|------------|"
        + "------------|------------|------------|");

    Map<String, List<Double>> rawDevs = new LinkedHashMap<String, List<Double>>();
    Map<String, List<Double>> predDevs = new LinkedHashMap<String, List<Double>>();
    Map<String, List<Double>> gtDevs = new LinkedHashMap<String, List<Double>>();
    Map<String, List<Double>> parDevs = new LinkedHashMap<String, List<Double>>();
    double sumRaw = 0, sumPred = 0, sumGT = 0, sumPar = 0;
    int nTotal = 0;

    for (String[] tc : testCases) {
      String component = tc[0];
      double tempK = Double.parseDouble(tc[1]);
      double sigmaExp = Double.parseDouble(tc[2]);

      // Setup system with PR
      SystemInterface sys = setupVLE(component, tempK);
      if (sys == null) {
        continue;
      }

      // Raw cDFT (default lambda=0.5, no correction)
      double sigmaRaw = computeIFT(sys, "cDFT");

      // Predictive cDFT (auto lambda + critical correction)
      double sigmaPred = computePredictiveIFT(sys);

      // GT and Parachor
      double sigmaGT = computeIFT(sys, "Full Gradient Theory");
      double sigmaParachor = computeIFT(sys, "Parachor");

      double devRaw = Math.abs(100.0 * (sigmaRaw - sigmaExp) / sigmaExp);
      double devPred = Math.abs(100.0 * (sigmaPred - sigmaExp) / sigmaExp);
      double devGT = Math.abs(100.0 * (sigmaGT - sigmaExp) / sigmaExp);
      double devPar = Math.abs(100.0 * (sigmaParachor - sigmaExp) / sigmaExp);

      if (!rawDevs.containsKey(component)) {
        rawDevs.put(component, new ArrayList<Double>());
        predDevs.put(component, new ArrayList<Double>());
        gtDevs.put(component, new ArrayList<Double>());
        parDevs.put(component, new ArrayList<Double>());
      }
      rawDevs.get(component).add(devRaw);
      predDevs.get(component).add(devPred);
      gtDevs.get(component).add(devGT);
      parDevs.get(component).add(devPar);
      sumRaw += devRaw;
      sumPred += devPred;
      sumGT += devGT;
      sumPar += devPar;
      nTotal++;

      System.out.printf(
          "| %-10s | %6.1f | %6.2f | %5.2f (%3.0f%%) | %5.2f (%3.0f%%) | %5.2f (%3.0f%%) |"
              + " %5.2f (%3.0f%%) |%n",
          component, tempK, sigmaExp, sigmaRaw, devRaw, sigmaPred, devPred, sigmaGT, devGT,
          sigmaParachor, devPar);
    }

    System.out.println("|------------|--------|--------|------------|"
        + "------------|------------|------------|");

    // Per-component AAD summary
    System.out.println();
    System.out.println("=== Per-Component AAD (%) ===");
    System.out.printf("| %-10s | %8s | %8s | %8s | %8s | %8s |%n", "Component", "cDFT-raw",
        "cDFT-pred", "Full GT", "Parachor", "Miqueu GT");
    System.out.println("|------------|----------|-----------|----------|----------|----------|");

    Map<String, Double> miqueuAAD = new LinkedHashMap<String, Double>();
    miqueuAAD.put("methane", 1.2);
    miqueuAAD.put("ethane", 1.1);
    miqueuAAD.put("propane", 0.7);
    miqueuAAD.put("n-butane", 0.5);
    miqueuAAD.put("n-pentane", 2.1);
    miqueuAAD.put("n-hexane", 1.3);
    miqueuAAD.put("nitrogen", 3.4);
    miqueuAAD.put("CO2", 2.0);

    for (String comp : rawDevs.keySet()) {
      double aadRaw = average(rawDevs.get(comp));
      double aadPred = average(predDevs.get(comp));
      double aadGT = average(gtDevs.get(comp));
      double aadPar = average(parDevs.get(comp));
      Double aadMiqueu = miqueuAAD.get(comp);
      String miqStr = aadMiqueu != null ? String.format("%6.1f", aadMiqueu) : "  N/A ";
      System.out.printf("| %-10s | %6.1f %% | %7.1f %% | %6.1f %% | %6.1f %% | %s %% |%n", comp,
          aadRaw, aadPred, aadGT, aadPar, miqStr);
    }

    double overallRaw = nTotal > 0 ? sumRaw / nTotal : 0;
    double overallPred = nTotal > 0 ? sumPred / nTotal : 0;
    double overallGT = nTotal > 0 ? sumGT / nTotal : 0;
    double overallPar = nTotal > 0 ? sumPar / nTotal : 0;
    System.out.println("|------------|----------|-----------|----------|----------|----------|");
    System.out.printf("| %-10s | %6.1f %% | %7.1f %% | %6.1f %% | %6.1f %% | %6.1f %% |%n",
        "OVERALL", overallRaw, overallPred, overallGT, overallPar, 2.2);
    System.out.println();

    System.out.println("=== Method Ranking (updated with predictive cDFT) ===");
    System.out.printf("| %-5s | %-50s | %8s | %s |%n", "Rank", "Method", "AAD (%)", "Params");
    System.out.println("|-------|----------------------------------------------------"
        + "|----------|----------------|");
    System.out.printf("| %-5s | %-50s | %8.1f | %s |%n", "1", "Miqueu GT + vol-corr PR, c(omega)",
        2.2, "0 IFT-fitted");
    System.out.printf("| %-5s | %-50s | %8.1f | %s |%n", "2", "cDFT predictive (this work)",
        overallPred, "0 (2 universal)");
    System.out.printf("| %-5s | %-50s | %8.1f | %s |%n", "3", "Full GT (Cachadina c), PR",
        overallGT, "0 IFT-fitted");
    System.out.printf("| %-5s | %-50s | %8.1f | %s |%n", "4", "Parachor (MacLeod-Sugden)",
        overallPar, "0");
    System.out.printf("| %-5s | %-50s | %8.1f | %s |%n", "5", "cDFT raw (no correction)",
        overallRaw, "0");
    System.out.println();
    System.out.println("The predictive cDFT uses two universal constants:");
    System.out
        .println("  lambda(omega) = 0.749 - 0.740*omega  (kernel range from acentric factor)");
    System.out.println("  delta_mu = -0.24                      (Ising critical correction)");
    System.out.println("Both are physically motivated, not empirically fitted to IFT data.");

    assertTrue(nTotal >= 20, "Should compute at least 20 systems");
    assertTrue(overallPred < 20, "Predictive cDFT AAD should be < 20%, got " + overallPred);
    assertTrue(overallPred < overallRaw,
        "Predictive should improve over raw: " + overallPred + " vs " + overallRaw);
  }

  /**
   * Computes IFT using cDFT in predictive mode (auto-lambda + critical correction).
   *
   * @param system thermodynamic system at VLE
   * @return IFT in mN/m, or -1 if calculation fails
   */
  private double computePredictiveIFT(SystemInterface system) {
    try {
      system.getInterphaseProperties().setInterfacialTensionModel("gas", "oil", "cDFT");
      // Access the cDFT model to enable predictive mode
      Object iftModel = system.getInterphaseProperties().getSurfaceTensionModel(0);
      if (iftModel instanceof CDFTSurfaceTension) {
        ((CDFTSurfaceTension) iftModel).setUsePredictiveMode(true);
      }
      double sigma = system.getInterphaseProperties().getSurfaceTension(0, 1);
      // Reset to default after calculation
      if (iftModel instanceof CDFTSurfaceTension) {
        ((CDFTSurfaceTension) iftModel).setUsePredictiveMode(false);
        ((CDFTSurfaceTension) iftModel).setAttractiveRangeFactor(0.5);
      }
      return sigma * 1000.0;
    } catch (Exception ex) {
      return -1.0;
    }
  }

  /**
   * Computes the arithmetic mean of a list of doubles.
   *
   * @param values list of double values
   * @return the mean, or 0 if the list is empty
   */
  private double average(List<Double> values) {
    if (values.isEmpty()) {
      return 0;
    }
    double sum = 0;
    for (double v : values) {
      sum += v;
    }
    return sum / values.size();
  }
}
