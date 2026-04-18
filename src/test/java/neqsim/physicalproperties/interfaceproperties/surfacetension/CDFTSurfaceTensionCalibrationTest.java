package neqsim.physicalproperties.interfaceproperties.surfacetension;

import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Calibration test for the cDFT attractive range factor (lambda).
 *
 * <p>
 * Sweeps the kernel half-width parameter lambda over a range of values and evaluates the average
 * absolute deviation (AAD) against experimental surface tension data for pure alkanes (C1-C6), N2,
 * and CO2. The optimal universal lambda is the value that minimises AAD across all species
 * simultaneously.
 * </p>
 *
 * @author Agent
 * @version 1.0
 */
class CDFTSurfaceTensionCalibrationTest {

  /** Experimental reference data: {component, EOS, T(K), sigma_exp(mN/m)}. */
  private static final Object[][] DATA = {{"methane", "PR", 90.7, 18.90},
      {"methane", "PR", 111.0, 14.90}, {"methane", "PR", 150.0, 6.60},
      {"methane", "PR", 170.0, 2.80}, {"ethane", "PR", 184.0, 17.10}, {"ethane", "PR", 230.0, 9.40},
      {"ethane", "PR", 270.0, 3.60}, {"propane", "PR", 230.0, 13.60},
      {"propane", "PR", 270.0, 8.60}, {"propane", "PR", 320.0, 3.40},
      {"n-butane", "PR", 270.0, 13.20}, {"n-butane", "PR", 320.0, 7.60},
      {"n-pentane", "PR", 300.0, 14.60}, {"n-pentane", "PR", 350.0, 9.10},
      {"n-hexane", "PR", 300.0, 16.30}, {"n-hexane", "PR", 340.0, 12.50},
      {"n-hexane", "PR", 400.0, 5.80}, {"nitrogen", "PR", 77.0, 9.40},
      {"nitrogen", "PR", 90.0, 6.20}, {"CO2", "PR", 220.0, 15.50}, {"CO2", "PR", 250.0, 8.50},
      {"CO2", "PR", 280.0, 2.60},};

  /**
   * Sweeps attractiveRangeFactor and prints AAD at each value.
   *
   * <p>
   * Run this test to identify the optimal universal kernel range parameter. The output is a table
   * of lambda vs AAD that can be used for the paper figure.
   * </p>
   */
  @Test
  void sweepAttractiveRangeFactor() {
    System.out.println("\n=== cDFT Kernel Range Calibration ===");
    System.out.printf("%-8s | %-10s | %-10s | %-8s%n", "lambda", "AAD (%)", "MaxDev(%)", "nOK");
    System.out.println("---------|------------|------------|--------");

    double bestLambda = 0.5;
    double bestAAD = Double.MAX_VALUE;

    for (double lambda = 0.30; lambda <= 2.05; lambda += 0.05) {
      double sumAbsDev = 0.0;
      double maxAbsDev = 0.0;
      int count = 0;

      for (Object[] row : DATA) {
        String comp = (String) row[0];
        String eos = (String) row[1];
        double tempK = (Double) row[2];
        double sigmaExp = (Double) row[3];

        try {
          SystemInterface sys;
          if ("SRK".equals(eos)) {
            sys = new SystemSrkEos(tempK, 1.0);
          } else {
            sys = new SystemPrEos(tempK, 1.0);
          }
          sys.addComponent(comp, 1.0);
          sys.setMixingRule("classic");
          sys.setMultiPhaseCheck(true);

          ThermodynamicOperations ops = new ThermodynamicOperations(sys);
          ops.bubblePointPressureFlash(false);
          sys.initProperties();

          CDFTSurfaceTension cdft = new CDFTSurfaceTension(sys);
          cdft.setAttractiveRangeFactor(lambda);
          double sigma = cdft.calcSurfaceTension(0, 1);
          double sigmaMNm = sigma * 1000.0;

          double absDev = Math.abs((sigmaMNm - sigmaExp) / sigmaExp) * 100.0;
          sumAbsDev += absDev;
          if (absDev > maxAbsDev) {
            maxAbsDev = absDev;
          }
          count++;
        } catch (Exception ex) {
          // skip failed point
        }
      }

      double aad = (count > 0) ? sumAbsDev / count : 999.0;
      System.out.printf("%-8.3f | %10.2f | %10.2f | %4d%n", lambda, aad, maxAbsDev, count);

      if (aad < bestAAD) {
        bestAAD = aad;
        bestLambda = lambda;
      }
    }

    System.out.println();
    System.out.printf("Optimal lambda = %.3f with AAD = %.2f%%%n", bestLambda, bestAAD);
    System.out.println();

    // Print per-component results at optimal lambda
    System.out.println("=== Per-component results at optimal lambda ===");
    System.out.printf("%-12s | %-5s | %6s | %8s | %8s | %8s%n", "Component", "EOS", "T (K)", "Exp",
        "cDFT", "Dev(%)");
    System.out.println("-------------|-------|--------|----------|----------|--------");

    for (Object[] row : DATA) {
      String comp = (String) row[0];
      String eos = (String) row[1];
      double tempK = (Double) row[2];
      double sigmaExp = (Double) row[3];

      try {
        SystemInterface sys;
        if ("SRK".equals(eos)) {
          sys = new SystemSrkEos(tempK, 1.0);
        } else {
          sys = new SystemPrEos(tempK, 1.0);
        }
        sys.addComponent(comp, 1.0);
        sys.setMixingRule("classic");
        sys.setMultiPhaseCheck(true);

        ThermodynamicOperations ops = new ThermodynamicOperations(sys);
        ops.bubblePointPressureFlash(false);
        sys.initProperties();

        CDFTSurfaceTension cdft = new CDFTSurfaceTension(sys);
        cdft.setAttractiveRangeFactor(bestLambda);
        double sigma = cdft.calcSurfaceTension(0, 1);
        double sigmaMNm = sigma * 1000.0;
        double dev = (sigmaMNm - sigmaExp) / sigmaExp * 100.0;

        System.out.printf("%-12s | %-5s | %6.1f | %8.2f | %8.2f | %+7.1f%n", comp, eos, tempK,
            sigmaExp, sigmaMNm, dev);
      } catch (Exception ex) {
        System.out.printf("%-12s | %-5s | %6.1f | %8.2f | %8s | %8s%n", comp, eos, tempK, sigmaExp,
            "FAIL", "N/A");
      }
    }
  }

  /**
   * Fine-grained sweep around the expected optimal region (0.6-1.2) with SRK and PR.
   */
  @Test
  void fineSweepAroundOptimal() {
    System.out.println("\n=== Fine sweep with both PR and SRK ===");
    System.out.printf("%-8s | %-12s | %-12s%n", "lambda", "AAD-PR (%)", "AAD-SRK (%)");
    System.out.println("---------|--------------|-------------");

    for (double lambda = 0.50; lambda <= 1.60; lambda += 0.025) {
      double sumDevPR = 0.0;
      int cntPR = 0;
      double sumDevSRK = 0.0;
      int cntSRK = 0;

      for (Object[] row : DATA) {
        String comp = (String) row[0];
        double tempK = (Double) row[2];
        double sigmaExp = (Double) row[3];

        // PR run
        try {
          SystemInterface sysPR = new SystemPrEos(tempK, 1.0);
          sysPR.addComponent(comp, 1.0);
          sysPR.setMixingRule("classic");
          sysPR.setMultiPhaseCheck(true);
          ThermodynamicOperations ops = new ThermodynamicOperations(sysPR);
          ops.bubblePointPressureFlash(false);
          sysPR.initProperties();

          CDFTSurfaceTension cdft = new CDFTSurfaceTension(sysPR);
          cdft.setAttractiveRangeFactor(lambda);
          double sigma = cdft.calcSurfaceTension(0, 1) * 1000.0;
          sumDevPR += Math.abs((sigma - sigmaExp) / sigmaExp) * 100.0;
          cntPR++;
        } catch (Exception ex) {
          // skip
        }

        // SRK run
        try {
          SystemInterface sysSRK = new SystemSrkEos(tempK, 1.0);
          sysSRK.addComponent(comp, 1.0);
          sysSRK.setMixingRule("classic");
          sysSRK.setMultiPhaseCheck(true);
          ThermodynamicOperations ops = new ThermodynamicOperations(sysSRK);
          ops.bubblePointPressureFlash(false);
          sysSRK.initProperties();

          CDFTSurfaceTension cdft = new CDFTSurfaceTension(sysSRK);
          cdft.setAttractiveRangeFactor(lambda);
          double sigma = cdft.calcSurfaceTension(0, 1) * 1000.0;
          sumDevSRK += Math.abs((sigma - sigmaExp) / sigmaExp) * 100.0;
          cntSRK++;
        } catch (Exception ex) {
          // skip
        }
      }

      double aadPR = (cntPR > 0) ? sumDevPR / cntPR : 999.0;
      double aadSRK = (cntSRK > 0) ? sumDevSRK / cntSRK : 999.0;
      System.out.printf("%-8.3f | %12.2f | %12.2f%n", lambda, aadPR, aadSRK);
    }
  }
}
