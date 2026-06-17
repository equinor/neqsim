package neqsim.thermo.util.gerg;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.netlib.util.StringW;
import org.netlib.util.doubleW;
import org.netlib.util.intW;

/**
 * Diagnostic test that prints a comparison table of GERG2008NH3 vs NIST data.
 *
 * @author NeqSim team
 * @version 1.0
 */
public class GERG2008NH3DiagnosticTest {
  private GERG2008NH3 gerg;

  /**
   * Set up model.
   */
  @BeforeEach
  public void setUp() {
    gerg = new GERG2008NH3();
    gerg.SetupGERG();
  }

  /**
   * Print comprehensive deviation table for pure ammonia.
   */
  @Test
  public void printDeviationTable() {
    System.out.println("\n========================================================");
    System.out.println("GERG-2008-NH3 vs NIST WebBook — Pure Ammonia Deviations");
    System.out.println("(Tillner-Roth SFE-12 amplitudes + Gao critical params)");
    System.out.println("========================================================");
    System.out.printf("%-6s %-8s %-12s %-12s %-8s %-10s %-10s %-8s%n", "T(K)", "P(MPa)", "rho_NIST",
        "rho_calc", "err(%)", "Cp_calc", "W_calc", "Z_calc");
    System.out.println("------------------------------------------------------------------------");

    // NIST data: {T(K), P(kPa), rho(mol/L), Cp(J/molK), W(m/s)}
    double[][] nistData = {
        // 300 K
        {300, 100, 0.040501, 36.827, 434.47},
        // 400 K
        {400, 500, 0.15299, 40.083, 493.91}, {400, 1000, 0.31160, 41.617, 488.91},
        {400, 2000, 0.64791, 45.119, 478.46}, {400, 3000, 1.0143, 49.362, 467.32},
        {400, 5000, 1.8705, 61.423, 442.30},
        // 430 K
        {430, 1000, 0.28739, 41.776, 507.74}, {430, 3000, 0.91503, 46.901, 491.57},
        {430, 5000, 1.6327, 53.639, 474.14},
        // 500 K
        {500, 1000, 0.24426, 43.067, 547.34}, {500, 5000, 1.3042, 48.514, 529.23},
        {500, 10000, 2.8633, 57.522, 507.36}};

    for (double[] pt : nistData) {
      double T = pt[0];
      double P_kPa = pt[1];
      double rhoNIST = pt[2];

      double[] x = new double[23];
      x[22] = 1.0;
      doubleW D = new doubleW(0.0);
      intW ierr = new intW(0);
      StringW herr = new StringW("");
      gerg.DensityGERG(0, T, P_kPa, x, D, ierr, herr);

      if (ierr.val != 0 || D.val > 20.0) {
        System.out.printf("%-6.0f %-8.1f %-12.5f %-12s %-8s%n", T, P_kPa / 1000, rhoNIST, "FAILED",
            "---");
        continue;
      }

      double relErr = (D.val - rhoNIST) / rhoNIST * 100.0;

      // Get properties
      doubleW P = new doubleW(0);
      doubleW Z = new doubleW(0);
      doubleW dPdD = new doubleW(0);
      doubleW d2PdD2 = new doubleW(0);
      doubleW d2PdTD = new doubleW(0);
      doubleW dPdT = new doubleW(0);
      doubleW U = new doubleW(0);
      doubleW H = new doubleW(0);
      doubleW S = new doubleW(0);
      doubleW Cv = new doubleW(0);
      doubleW Cp = new doubleW(0);
      doubleW W = new doubleW(0);
      doubleW G = new doubleW(0);
      doubleW JT = new doubleW(0);
      doubleW Kappa = new doubleW(0);
      doubleW A = new doubleW(0);
      gerg.PropertiesGERG(T, D.val, x, P, Z, dPdD, d2PdD2, d2PdTD, dPdT, U, H, S, Cv, Cp, W, G, JT,
          Kappa, A);

      System.out.printf("%-6.0f %-8.1f %-12.5f %-12.5f %-+8.2f %-10.3f %-10.2f %-8.5f%n", T,
          P_kPa / 1000, rhoNIST, D.val, relErr, Cp.val, W.val, Z.val);
    }

    System.out.println("------------------------------------------------------------------------");
    System.out.println("Positive err% = model overpredicts density");
    System.out
        .println("Full Gao et al. (2020) reference EOS: 20-term (8 power + 10 Gaussian + 2 GaoB)");
    System.out
        .println("Coefficients from CoolProp (Gao-JPCRD-2020), Tc=405.56K, rhoc=13.696 mol/L");
    System.out.println("========================================================\n");
  }
}
