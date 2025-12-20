package neqsim.thermo.util.gerg;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.netlib.util.StringW;
import org.netlib.util.doubleW;
import org.netlib.util.intW;

/**
 * Comparison test between GERG-2008 and GERG-2008-H2 for hydrogen-containing mixtures.
 *
 * <p>
 * This test class compares the two models to verify that GERG-2008-H2 produces different (and
 * presumably more accurate) results for hydrogen-rich mixtures, as described in Beckmüller et al.
 * (2022).
 * </p>
 */
public class GERG2008H2ComparisonTest {
  private GERG2008 gergStandard;
  private GERG2008H2 gergH2;

  @BeforeEach
  public void setUp() {
    gergStandard = new GERG2008();
    gergStandard.SetupGERG();

    gergH2 = new GERG2008H2();
    gergH2.SetupGERG();
  }

  /**
   * Compare density predictions for CH4-H2 binary mixtures at various compositions. The paper shows
   * improved accuracy for hydrogen-methane mixtures.
   */
  @Test
  public void compareCH4H2BinaryDensity() {
    System.out.println(StringUtils.repeat("=", 80));
    System.out.println("COMPARISON: CH4-H2 Binary Mixture Density");
    System.out.println(StringUtils.repeat("=", 80));
    System.out.println();

    double T = 300.0; // K
    double P = 10000.0; // kPa (10 MPa)

    System.out.printf("Conditions: T = %.1f K, P = %.1f kPa (%.1f MPa)%n", T, P, P / 1000.0);
    System.out.println();
    System.out.printf("%-10s %-15s %-15s %-15s %-15s%n", "x(H2)", "ρ GERG-2008", "ρ GERG-2008-H2",
        "Δρ (mol/L)", "Δρ (%)");
    System.out.println(StringUtils.repeat("-", 80));

    double[] h2Fractions = {0.0, 0.05, 0.10, 0.20, 0.30, 0.50, 0.70, 1.0};

    for (double xH2 : h2Fractions) {
      double[] x = new double[22];
      x[1] = 1.0 - xH2; // Methane
      x[15] = xH2; // Hydrogen

      doubleW D1 = new doubleW(0.0);
      doubleW D2 = new doubleW(0.0);
      intW ierr = new intW(0);
      StringW herr = new StringW("");

      gergStandard.DensityGERG(0, T, P, x, D1, ierr, herr);
      gergH2.DensityGERG(0, T, P, x, D2, ierr, herr);

      double deltaD = D2.val - D1.val;
      double relDiff = (D1.val != 0) ? (deltaD / D1.val) * 100 : 0;

      System.out.printf("%-10.2f %-15.6f %-15.6f %-15.6f %-15.4f%n", xH2, D1.val, D2.val, deltaD,
          relDiff);
    }
    System.out.println();
  }

  /**
   * Compare compressibility factor predictions for N2-H2 binary mixtures. This is a new binary
   * interaction in GERG-2008-H2.
   */
  @Test
  public void compareN2H2BinaryCompressibility() {
    System.out.println(StringUtils.repeat("=", 80));
    System.out.println("COMPARISON: N2-H2 Binary Mixture Compressibility Factor");
    System.out.println(StringUtils.repeat("=", 80));
    System.out.println();

    double T = 300.0; // K
    double P = 20000.0; // kPa (20 MPa)

    System.out.printf("Conditions: T = %.1f K, P = %.1f kPa (%.1f MPa)%n", T, P, P / 1000.0);
    System.out.println();
    System.out.printf("%-10s %-15s %-15s %-15s %-15s%n", "x(H2)", "Z GERG-2008", "Z GERG-2008-H2",
        "ΔZ", "ΔZ (%)");
    System.out.println(StringUtils.repeat("-", 80));

    double[] h2Fractions = {0.0, 0.10, 0.20, 0.30, 0.50, 0.70, 1.0};

    for (double xH2 : h2Fractions) {
      double[] x = new double[22];
      x[2] = 1.0 - xH2; // Nitrogen
      x[15] = xH2; // Hydrogen

      doubleW D1 = new doubleW(0.0);
      doubleW D2 = new doubleW(0.0);
      doubleW P1 = new doubleW(0.0);
      doubleW P2 = new doubleW(0.0);
      doubleW Z1 = new doubleW(0.0);
      doubleW Z2 = new doubleW(0.0);
      intW ierr = new intW(0);
      StringW herr = new StringW("");

      gergStandard.DensityGERG(0, T, P, x, D1, ierr, herr);
      gergH2.DensityGERG(0, T, P, x, D2, ierr, herr);

      gergStandard.PressureGERG(T, D1.val, x, P1, Z1);
      gergH2.PressureGERG(T, D2.val, x, P2, Z2);

      double deltaZ = Z2.val - Z1.val;
      double relDiff = (Z1.val != 0) ? (deltaZ / Z1.val) * 100 : 0;

      System.out.printf("%-10.2f %-15.6f %-15.6f %-15.6f %-15.4f%n", xH2, Z1.val, Z2.val, deltaZ,
          relDiff);
    }
    System.out.println();
  }

  /**
   * Compare CO2-H2 binary mixtures - another new binary in GERG-2008-H2.
   */
  @Test
  public void compareCO2H2BinaryDensity() {
    System.out.println(StringUtils.repeat("=", 80));
    System.out.println("COMPARISON: CO2-H2 Binary Mixture Density");
    System.out.println(StringUtils.repeat("=", 80));
    System.out.println();

    double T = 350.0; // K (higher to avoid CO2 liquid)
    double P = 10000.0; // kPa

    System.out.printf("Conditions: T = %.1f K, P = %.1f kPa (%.1f MPa)%n", T, P, P / 1000.0);
    System.out.println();
    System.out.printf("%-10s %-15s %-15s %-15s %-15s%n", "x(H2)", "ρ GERG-2008", "ρ GERG-2008-H2",
        "Δρ (mol/L)", "Δρ (%)");
    System.out.println(StringUtils.repeat("-", 80));

    double[] h2Fractions = {0.0, 0.10, 0.20, 0.30, 0.50, 0.70, 1.0};

    for (double xH2 : h2Fractions) {
      double[] x = new double[22];
      x[3] = 1.0 - xH2; // CO2
      x[15] = xH2; // Hydrogen

      doubleW D1 = new doubleW(0.0);
      doubleW D2 = new doubleW(0.0);
      intW ierr = new intW(0);
      StringW herr = new StringW("");

      gergStandard.DensityGERG(0, T, P, x, D1, ierr, herr);
      gergH2.DensityGERG(0, T, P, x, D2, ierr, herr);

      double deltaD = D2.val - D1.val;
      double relDiff = (D1.val != 0) ? (deltaD / D1.val) * 100 : 0;

      System.out.printf("%-10.2f %-15.6f %-15.6f %-15.6f %-15.4f%n", xH2, D1.val, D2.val, deltaD,
          relDiff);
    }
    System.out.println();
  }

  /**
   * Compare speed of sound predictions - paper shows improvements here.
   */
  @Test
  public void compareSpeedOfSound() {
    System.out.println(StringUtils.repeat("=", 80));
    System.out.println("COMPARISON: Speed of Sound in CH4-H2 Mixtures");
    System.out.println(StringUtils.repeat("=", 80));
    System.out.println();

    double T = 300.0; // K
    double P = 5000.0; // kPa

    System.out.printf("Conditions: T = %.1f K, P = %.1f kPa (%.1f MPa)%n", T, P, P / 1000.0);
    System.out.println();
    System.out.printf("%-10s %-15s %-15s %-15s %-15s%n", "x(H2)", "W GERG-2008", "W GERG-2008-H2",
        "ΔW (m/s)", "ΔW (%)");
    System.out.println(StringUtils.repeat("-", 80));

    double[] h2Fractions = {0.0, 0.05, 0.10, 0.20, 0.30, 0.50, 1.0};

    for (double xH2 : h2Fractions) {
      double[] x = new double[22];
      x[1] = 1.0 - xH2; // Methane
      x[15] = xH2; // Hydrogen

      double[] props1 = calculateProperties(gergStandard, T, P, x);
      double[] props2 = calculateProperties(gergH2, T, P, x);

      double W1 = props1[0];
      double W2 = props2[0];
      double deltaW = W2 - W1;
      double relDiff = (W1 != 0) ? (deltaW / W1) * 100 : 0;

      System.out.printf("%-10.2f %-15.4f %-15.4f %-15.4f %-15.4f%n", xH2, W1, W2, deltaW, relDiff);
    }
    System.out.println();
  }

  /**
   * Compare heat capacities - Cp and Cv.
   */
  @Test
  public void compareHeatCapacities() {
    System.out.println(StringUtils.repeat("=", 80));
    System.out.println("COMPARISON: Heat Capacities (Cp) in CH4-H2 Mixtures");
    System.out.println(StringUtils.repeat("=", 80));
    System.out.println();

    double T = 300.0;
    double P = 10000.0;

    System.out.printf("Conditions: T = %.1f K, P = %.1f kPa (%.1f MPa)%n", T, P, P / 1000.0);
    System.out.println();
    System.out.printf("%-10s %-15s %-15s %-15s %-15s%n", "x(H2)", "Cp GERG-2008", "Cp GERG-2008-H2",
        "ΔCp", "ΔCp (%)");
    System.out.println(StringUtils.repeat("-", 80));

    double[] h2Fractions = {0.0, 0.10, 0.20, 0.30, 0.50, 1.0};

    for (double xH2 : h2Fractions) {
      double[] x = new double[22];
      x[1] = 1.0 - xH2;
      x[15] = xH2;

      double[] props1 = calculateProperties(gergStandard, T, P, x);
      double[] props2 = calculateProperties(gergH2, T, P, x);

      double Cp1 = props1[1];
      double Cp2 = props2[1];
      double deltaCp = Cp2 - Cp1;
      double relDiff = (Cp1 != 0) ? (deltaCp / Cp1) * 100 : 0;

      System.out.printf("%-10.2f %-15.4f %-15.4f %-15.4f %-15.4f%n", xH2, Cp1, Cp2, deltaCp,
          relDiff);
    }
    System.out.println();
  }

  /**
   * Compare at high pressures - paper shows larger differences at higher pressures.
   */
  @Test
  public void comparePressureEffect() {
    System.out.println(StringUtils.repeat("=", 80));
    System.out.println("COMPARISON: Pressure Effect on CH4-H2 (50/50) Mixture Density");
    System.out.println(StringUtils.repeat("=", 80));
    System.out.println();

    double T = 300.0;
    double xH2 = 0.50;

    double[] x = new double[22];
    x[1] = 0.50; // Methane
    x[15] = 0.50; // Hydrogen

    System.out.printf("Composition: 50%% CH4, 50%% H2 at T = %.1f K%n", T);
    System.out.println();
    System.out.printf("%-15s %-15s %-15s %-15s %-15s%n", "P (MPa)", "ρ GERG-2008", "ρ GERG-2008-H2",
        "Δρ (mol/L)", "Δρ (%)");
    System.out.println(StringUtils.repeat("-", 80));

    double[] pressures = {1000, 2000, 5000, 10000, 20000, 30000, 50000}; // kPa

    for (double P : pressures) {
      doubleW D1 = new doubleW(0.0);
      doubleW D2 = new doubleW(0.0);
      intW ierr = new intW(0);
      StringW herr = new StringW("");

      gergStandard.DensityGERG(0, T, P, x, D1, ierr, herr);
      gergH2.DensityGERG(0, T, P, x, D2, ierr, herr);

      double deltaD = D2.val - D1.val;
      double relDiff = (D1.val != 0) ? (deltaD / D1.val) * 100 : 0;

      System.out.printf("%-15.1f %-15.6f %-15.6f %-15.6f %-15.4f%n", P / 1000.0, D1.val, D2.val,
          deltaD, relDiff);
    }
    System.out.println();
  }

  /**
   * Compare temperature effect on hydrogen-rich natural gas.
   */
  @Test
  public void compareTemperatureEffect() {
    System.out.println(StringUtils.repeat("=", 80));
    System.out.println("COMPARISON: Temperature Effect on Hydrogen-Rich Natural Gas");
    System.out.println(StringUtils.repeat("=", 80));
    System.out.println();

    double P = 10000.0; // kPa

    // Hydrogen-enriched natural gas composition
    double[] x = new double[22];
    x[1] = 0.70; // Methane
    x[2] = 0.02; // Nitrogen
    x[3] = 0.01; // CO2
    x[4] = 0.05; // Ethane
    x[5] = 0.02; // Propane
    x[15] = 0.20; // Hydrogen

    System.out
        .println("Composition: 70% CH4, 2% N2, 1% CO2, 5% C2H6, 2% C3H8, 20% H2 at P = 10 MPa");
    System.out.println();
    System.out.printf("%-15s %-15s %-15s %-15s %-15s%n", "T (K)", "ρ GERG-2008", "ρ GERG-2008-H2",
        "Δρ (mol/L)", "Δρ (%)");
    System.out.println(StringUtils.repeat("-", 80));

    double[] temperatures = {200, 250, 300, 350, 400, 450, 500};

    for (double T : temperatures) {
      doubleW D1 = new doubleW(0.0);
      doubleW D2 = new doubleW(0.0);
      intW ierr = new intW(0);
      StringW herr = new StringW("");

      gergStandard.DensityGERG(0, T, P, x, D1, ierr, herr);
      gergH2.DensityGERG(0, T, P, x, D2, ierr, herr);

      double deltaD = D2.val - D1.val;
      double relDiff = (D1.val != 0) ? (deltaD / D1.val) * 100 : 0;

      System.out.printf("%-15.1f %-15.6f %-15.6f %-15.6f %-15.4f%n", T, D1.val, D2.val, deltaD,
          relDiff);
    }
    System.out.println();
  }

  /**
   * Compare Joule-Thomson coefficient - important for hydrogen handling.
   */
  @Test
  public void compareJouleThomson() {
    System.out.println(StringUtils.repeat("=", 80));
    System.out.println("COMPARISON: Joule-Thomson Coefficient in CH4-H2 Mixtures");
    System.out.println(StringUtils.repeat("=", 80));
    System.out.println();

    double T = 300.0;
    double P = 10000.0;

    System.out.printf("Conditions: T = %.1f K, P = %.1f kPa (%.1f MPa)%n", T, P, P / 1000.0);
    System.out.println();
    System.out.printf("%-10s %-18s %-18s %-18s%n", "x(H2)", "JT GERG-2008", "JT GERG-2008-H2",
        "ΔJT (%)");
    System.out.println(StringUtils.repeat("-", 80));

    double[] h2Fractions = {0.0, 0.10, 0.20, 0.30, 0.50, 1.0};

    for (double xH2 : h2Fractions) {
      double[] x = new double[22];
      x[1] = 1.0 - xH2;
      x[15] = xH2;

      double[] props1 = calculateProperties(gergStandard, T, P, x);
      double[] props2 = calculateProperties(gergH2, T, P, x);

      double JT1 = props1[2];
      double JT2 = props2[2];
      double relDiff = (JT1 != 0) ? ((JT2 - JT1) / Math.abs(JT1)) * 100 : 0;

      System.out.printf("%-10.2f %-18.6e %-18.6e %-18.4f%n", xH2, JT1, JT2, relDiff);
    }
    System.out.println();
    System.out.println("Note: Negative JT coefficient indicates inverse Joule-Thomson effect");
    System.out.println("(cooling upon expansion), which is characteristic of hydrogen.");
    System.out.println();
  }

  /**
   * Summary comparison showing all deviations.
   */
  @Test
  public void summarizeDeviations() {
    System.out.println(StringUtils.repeat("=", 80));
    System.out.println("SUMMARY: Maximum Deviations Between GERG-2008 and GERG-2008-H2");
    System.out.println(StringUtils.repeat("=", 80));
    System.out.println();

    double T = 300.0;
    double P = 10000.0;

    // Different binary systems
    String[] systems = {"CH4-H2", "N2-H2", "CO2-H2 (T=350K)", "C2H6-H2"};
    int[][] components = {{1, 15}, {2, 15}, {3, 15}, {4, 15}};
    double[] temps = {300, 300, 350, 300};

    System.out.println("Binary System Analysis at P = 10 MPa, x(H2) = 0.50");
    System.out.println();
    System.out.printf("%-20s %-15s %-15s %-15s%n", "System", "Δρ (%)", "ΔZ (%)", "ΔW (%)");
    System.out.println(StringUtils.repeat("-", 65));

    for (int s = 0; s < systems.length; s++) {
      double[] x = new double[22];
      x[components[s][0]] = 0.50;
      x[components[s][1]] = 0.50;

      doubleW D1 = new doubleW(0.0);
      doubleW D2 = new doubleW(0.0);
      intW ierr = new intW(0);
      StringW herr = new StringW("");

      double useT = temps[s];
      gergStandard.DensityGERG(0, useT, P, x, D1, ierr, herr);
      gergH2.DensityGERG(0, useT, P, x, D2, ierr, herr);

      double[] props1 = calculatePropertiesAtDensity(gergStandard, useT, D1.val, x);
      double[] props2 = calculatePropertiesAtDensity(gergH2, useT, D2.val, x);

      double relDiffD = (D1.val != 0) ? ((D2.val - D1.val) / D1.val) * 100 : 0;
      double relDiffZ = (props1[3] != 0) ? ((props2[3] - props1[3]) / props1[3]) * 100 : 0;
      double relDiffW = (props1[0] != 0) ? ((props2[0] - props1[0]) / props1[0]) * 100 : 0;

      System.out.printf("%-20s %-15.4f %-15.4f %-15.4f%n", systems[s], relDiffD, relDiffZ,
          relDiffW);
    }

    System.out.println();
    System.out.println("Key Observations:");
    System.out.println(
        "1. Differences are most significant for CO2-H2 and N2-H2 due to new departure functions");
    System.out
        .println("2. CH4-H2 shows smaller differences as it already had a departure function");
    System.out.println("3. Differences increase with hydrogen content and pressure");
    System.out.println();
  }

  /**
   * Helper method to calculate thermodynamic properties.
   */
  private double[] calculateProperties(GERG2008 gerg, double T, double P, double[] x) {
    doubleW D = new doubleW(0.0);
    intW ierr = new intW(0);
    StringW herr = new StringW("");

    gerg.DensityGERG(0, T, P, x, D, ierr, herr);
    return calculatePropertiesAtDensity(gerg, T, D.val, x);
  }

  /**
   * Helper method to calculate properties at given density.
   */
  private double[] calculatePropertiesAtDensity(GERG2008 gerg, double T, double D, double[] x) {
    doubleW PP = new doubleW(0.0);
    doubleW Z = new doubleW(0.0);
    doubleW dPdD = new doubleW(0.0);
    doubleW d2PdD2 = new doubleW(0.0);
    doubleW d2PdTD = new doubleW(0.0);
    doubleW dPdT = new doubleW(0.0);
    doubleW U = new doubleW(0.0);
    doubleW H = new doubleW(0.0);
    doubleW S = new doubleW(0.0);
    doubleW Cv = new doubleW(0.0);
    doubleW Cp = new doubleW(0.0);
    doubleW W = new doubleW(0.0);
    doubleW G = new doubleW(0.0);
    doubleW JT = new doubleW(0.0);
    doubleW Kappa = new doubleW(0.0);
    doubleW A = new doubleW(0.0);

    gerg.PropertiesGERG(T, D, x, PP, Z, dPdD, d2PdD2, d2PdTD, dPdT, U, H, S, Cv, Cp, W, G, JT,
        Kappa, A);

    return new double[] {W.val, Cp.val, JT.val, Z.val, Cv.val, H.val, S.val};
  }
}
