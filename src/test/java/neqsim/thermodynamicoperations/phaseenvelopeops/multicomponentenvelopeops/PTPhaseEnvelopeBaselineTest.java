package neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression baselines for {@link PTPhaseEnvelopeMichelsen}.
 *
 * <p>
 * This class pins the phase envelope output for a representative fluid library so that future
 * solver changes (e.g. arc-length continuation in step C-2) cannot silently drift accuracy. Each
 * baseline records cricondenbar, cricondentherm, critical point, and the number of valid points
 * on each branch (excluding NaN branch-break sentinels). Tolerances are deliberately loose enough
 * (~2% on T/P, +/-20% on point counts) that step-control changes are accepted as long as the
 * envelope shape and critical features match within engineering accuracy.
 * </p>
 *
 * <p>
 * Run {@code main} to print a refreshed table after an intentional algorithm change, then update
 * the BASELINES list in this file.
 * </p>
 */
class PTPhaseEnvelopeBaselineTest {

  /** Tolerance on T (Kelvin). 2 K covers ~1% of typical envelope temperatures. */
  private static final double T_TOL_K = 2.5;

  /** Tolerance on P (bar). 2.5 bar covers ~2% of typical envelope pressures. */
  private static final double P_TOL_BAR = 2.5;

  /** Tolerance on point counts (fraction). */
  private static final double COUNT_TOL_FRAC = 0.25;

  /** Immutable baseline record for one fluid. */
  private static final class Baseline {
    final String name;
    final double cpT;
    final double cpP;
    final double cbT;
    final double cbP;
    final double ctT;
    final double ctP;
    final int dewPoints;
    final int bubPoints;

    Baseline(String name, double cpT, double cpP, double cbT, double cbP, double ctT, double ctP,
        int dewPoints, int bubPoints) {
      this.name = name;
      this.cpT = cpT;
      this.cpP = cpP;
      this.cbT = cbT;
      this.cbP = cbP;
      this.ctT = ctT;
      this.ctP = ctP;
      this.dewPoints = dewPoints;
      this.bubPoints = bubPoints;
    }
  }

  /**
   * Baseline library. Values captured on commit 655b3e492 (envelope-segment-api) with SRK EOS
   * (and PR EOS for retrogradeCondensate) and classic mixing rule. When solver changes are
   * intentional, regenerate via {@link #main}. Point counts are informational only; they vary
   * legitimately with step-control changes and are not asserted.
   */
  private static final Baseline[] BASELINES = new Baseline[] {
      // name, cpT, cpP, cricondenbarT, cricondenbarP, cricondenthermT, cricondenthermP, nDew, nBub
      new Baseline("lightGasC1C2C3", 216.71, 64.14, 222.52, 67.43, 230.96, 52.14, 30, 33),
      new Baseline("naturalGasStandard", 233.89, 84.36, 252.86, 91.74, 270.14, 56.29, 33, 36),
      new Baseline("richGasNearCricondenbar", 227.85, 82.68, 259.03, 100.33, 282.99, 57.75, 36, 41),
      new Baseline("binaryCO2Methane", 254.44, 86.98, 188.51, 184.12, 261.52, 77.83, 24, 35),
      new Baseline("retrogradeCondensate", 340.96, 178.55, 330.57, 179.03, 410.75, 74.63, 35, 36)
  };

  /**
   * Compute envelope metrics for one fluid.
   */
  private static double[] metrics(ThermodynamicOperations ops) {
    ops.calcPTphaseEnvelope();
    double[] cp = ops.get("criticalPoint1");
    double[] cb = ops.get("cricondenbar");
    double[] ct = ops.get("cricondentherm");
    double[] dewT = ops.get("dewT");
    double[] bubT = ops.get("bubT");
    int nDew = 0;
    int nBub = 0;
    for (double t : dewT) {
      if (!Double.isNaN(t)) {
        nDew++;
      }
    }
    for (double t : bubT) {
      if (!Double.isNaN(t)) {
        nBub++;
      }
    }
    return new double[] {cp[0], cp[1], cb[0], cb[1], ct[0], ct[1], nDew, nBub};
  }

  /**
   * Build a fluid by name. Keeps test data close to the assertions.
   */
  private static SystemInterface makeFluid(String name) {
    SystemInterface f;
    switch (name) {
      case "lightGasC1C2C3":
        f = new SystemSrkEos(273.15, 50.0);
        f.addComponent("methane", 0.90);
        f.addComponent("ethane", 0.07);
        f.addComponent("propane", 0.03);
        break;
      case "naturalGasStandard":
        f = new SystemSrkEos(273.15, 50.0);
        f.addComponent("nitrogen", 0.02);
        f.addComponent("CO2", 0.03);
        f.addComponent("methane", 0.80);
        f.addComponent("ethane", 0.08);
        f.addComponent("propane", 0.04);
        f.addComponent("i-butane", 0.015);
        f.addComponent("n-butane", 0.015);
        break;
      case "richGasNearCricondenbar":
        f = new SystemSrkEos(273.15, 50.0);
        f.addComponent("nitrogen", 0.005);
        f.addComponent("CO2", 0.02);
        f.addComponent("methane", 0.85);
        f.addComponent("ethane", 0.06);
        f.addComponent("propane", 0.03);
        f.addComponent("i-butane", 0.01);
        f.addComponent("n-butane", 0.015);
        f.addComponent("i-pentane", 0.005);
        f.addComponent("n-pentane", 0.005);
        break;
      case "binaryCO2Methane":
        f = new SystemSrkEos(250.0, 50.0);
        f.addComponent("CO2", 0.50);
        f.addComponent("methane", 0.50);
        break;
      case "retrogradeCondensate":
        f = new SystemPrEos(273.15, 50.0);
        f.addComponent("methane", 0.70);
        f.addComponent("ethane", 0.08);
        f.addComponent("propane", 0.05);
        f.addComponent("n-butane", 0.04);
        f.addComponent("n-pentane", 0.03);
        f.addComponent("n-hexane", 0.03);
        f.addComponent("n-heptane", 0.07);
        break;
      default:
        throw new IllegalArgumentException("Unknown fluid: " + name);
    }
    f.setMixingRule("classic");
    return f;
  }

  @Test
  @DisplayName("Envelope metrics remain within tolerance of locked baselines")
  void testBaselines() {
    List<String> failures = new ArrayList<>();
    for (Baseline b : BASELINES) {
      SystemInterface fluid = makeFluid(b.name);
      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
      double[] m = metrics(ops);
      assertNotNull(m);

      if (Math.abs(m[0] - b.cpT) > T_TOL_K) {
        failures.add(String.format("%s: CP_T actual=%.2f expected=%.2f dT=%.2f", b.name, m[0], b.cpT,
            m[0] - b.cpT));
      }
      if (Math.abs(m[1] - b.cpP) > P_TOL_BAR) {
        failures.add(String.format("%s: CP_P actual=%.2f expected=%.2f dP=%.2f", b.name, m[1], b.cpP,
            m[1] - b.cpP));
      }
      if (Math.abs(m[2] - b.cbT) > T_TOL_K) {
        failures.add(String.format("%s: cricondenbar_T actual=%.2f expected=%.2f", b.name, m[2],
            b.cbT));
      }
      if (Math.abs(m[3] - b.cbP) > P_TOL_BAR) {
        failures.add(String.format("%s: cricondenbar_P actual=%.2f expected=%.2f", b.name, m[3],
            b.cbP));
      }
      if (Math.abs(m[4] - b.ctT) > T_TOL_K) {
        failures.add(String.format("%s: cricondentherm_T actual=%.2f expected=%.2f", b.name, m[4],
            b.ctT));
      }
      if (Math.abs(m[5] - b.ctP) > P_TOL_BAR) {
        failures.add(String.format("%s: cricondentherm_P actual=%.2f expected=%.2f", b.name, m[5],
            b.ctP));
      }

      double dewDiff = Math.abs(m[6] - b.dewPoints) / Math.max(1.0, b.dewPoints);
      double bubDiff = Math.abs(m[7] - b.bubPoints) / Math.max(1.0, b.bubPoints);
      if (dewDiff > COUNT_TOL_FRAC) {
        failures.add(String.format("%s: dewPoints actual=%d expected=%d frac=%.2f", b.name,
            (int) m[6], b.dewPoints, dewDiff));
      }
      if (bubDiff > COUNT_TOL_FRAC) {
        failures.add(String.format("%s: bubPoints actual=%d expected=%d frac=%.2f", b.name,
            (int) m[7], b.bubPoints, bubDiff));
      }

      // Universal sanity: each envelope must close and produce non-empty branches.
      assertTrue((int) m[6] > 0, b.name + ": dew branch is empty");
      assertTrue((int) m[7] > 0, b.name + ": bubble branch is empty");
      assertTrue(m[3] >= m[1] - P_TOL_BAR,
          b.name + ": cricondenbar P must be >= critical P (within tol)");
      assertTrue(m[4] >= m[0] - T_TOL_K,
          b.name + ": cricondentherm T must be >= critical T (within tol)");
    }
    if (!failures.isEmpty()) {
      throw new AssertionError(
          "Envelope baselines drifted (" + failures.size() + " violations):\n  "
              + String.join("\n  ", failures)
              + "\n\nIf the change is intentional, regenerate via PTPhaseEnvelopeBaselineTest.main.");
    }
  }

  /**
   * Critical point lies on the envelope. The dew and bubble curves meet at the critical point, so
   * there must be a dew-point neighbour within tolerance.
   */
  @Test
  @DisplayName("Critical point is within tolerance of at least one dew curve neighbour")
  void testCriticalPointOnEnvelope() {
    SystemInterface fluid = makeFluid("naturalGasStandard");
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    double[] m = metrics(ops);
    double cpT = m[0];
    double cpP = m[1];
    double[] dewT = ops.get("dewT");
    double[] dewP = ops.get("dewP");

    double minDist = Double.POSITIVE_INFINITY;
    for (int i = 0; i < dewT.length; i++) {
      if (Double.isNaN(dewT[i]) || Double.isNaN(dewP[i])) {
        continue;
      }
      double d = Math.hypot(dewT[i] - cpT, dewP[i] - cpP);
      if (d < minDist) {
        minDist = d;
      }
    }
    // Envelope sampling typically gets within 5 K and 5 bar of the critical point.
    assertTrue(minDist < Math.hypot(5.0, 5.0),
        "No dew-curve point close to critical point. Min distance: " + minDist);
  }

  /**
   * Regenerate the baseline table. Run manually when an intentional algorithm change is made.
   *
   * <pre>
   *   mvn -q test-compile exec:java -Dexec.mainClass="neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops.PTPhaseEnvelopeBaselineTest" -Dexec.classpathScope=test
   * </pre>
   *
   * @param args unused
   */
  public static void main(String[] args) {
    String[] names = {"lightGasC1C2C3", "naturalGasStandard", "richGasNearCricondenbar",
        "binaryCO2Methane", "retrogradeCondensate"};
    System.out.println("=== Phase Envelope Baselines ===");
    System.out.println("name | CP_T | CP_P | cb_T | cb_P | ct_T | ct_P | nDew | nBub");
    for (String name : names) {
      SystemInterface f = makeFluid(name);
      ThermodynamicOperations ops = new ThermodynamicOperations(f);
      double[] m = metrics(ops);
      System.out.printf("%s | %.2f | %.2f | %.2f | %.2f | %.2f | %.2f | %d | %d%n", name, m[0], m[1],
          m[2], m[3], m[4], m[5], (int) m[6], (int) m[7]);
      System.out.printf(
          "  new Baseline(\"%s\", %.1f, %.1f, %.1f, %.1f, %.1f, %.1f, %d, %d),%n",
          name, m[0], m[1], m[2], m[3], m[4], m[5], (int) m[6], (int) m[7]);
    }
    // Silence unused import warning if compiled standalone.
    List<String> unused = Arrays.asList("");
    if (unused.isEmpty()) {
      return;
    }
  }
}
