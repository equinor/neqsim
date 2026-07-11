package neqsim.pvtsimulation.util;

import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for the least-squares fitting and Duong extensions of {@link DeclineCurveAnalysis}.
 */
public class DeclineCurveAnalysisFitTest {

  @Test
  public void testFitExponentialData() {
    double qiTrue = 1000.0;
    double diTrue = 0.001;
    int n = 40;
    double[] t = new double[n];
    double[] q = new double[n];
    for (int i = 0; i < n; i++) {
      t[i] = i * 90.0;
      q[i] = DeclineCurveAnalysis.rate(qiTrue, diTrue, 0.0, t[i]);
    }
    Map<String, Double> fit = DeclineCurveAnalysis.fitArps(t, q);
    Assertions.assertEquals(qiTrue, fit.get("qi"), 1.0, "qi should be recovered");
    Assertions.assertEquals(diTrue, fit.get("di"), 1.0e-5, "di should be recovered");
    Assertions.assertTrue(fit.get("b") < 0.02, "b should be near zero for exponential data");
    Assertions.assertTrue(fit.get("rSquared") > 0.9999);
  }

  @Test
  public void testFitHyperbolicData() {
    double qiTrue = 1000.0;
    double diTrue = 0.002;
    double bTrue = 0.5;
    int n = 40;
    double[] t = new double[n];
    double[] q = new double[n];
    for (int i = 0; i < n; i++) {
      t[i] = i * 90.0;
      q[i] = DeclineCurveAnalysis.rate(qiTrue, diTrue, bTrue, t[i]);
    }
    Map<String, Double> fit = DeclineCurveAnalysis.fitArps(t, q);
    Assertions.assertEquals(bTrue, fit.get("b"), 0.05, "b should be recovered");
    Assertions.assertEquals(qiTrue, fit.get("qi"), 20.0, "qi should be recovered");
    Assertions.assertTrue(fit.get("rSquared") > 0.999);

    double eur = DeclineCurveAnalysis.eurFromFit(fit, 50.0);
    Assertions.assertTrue(eur > 0.0, "EUR from fit should be positive");
  }

  @Test
  public void testWindowedFitIgnoresEarlyTransient() {
    double qiTrue = 800.0;
    double diTrue = 0.0015;
    int n = 30;
    double[] t = new double[n];
    double[] q = new double[n];
    for (int i = 0; i < n; i++) {
      t[i] = i * 60.0;
      q[i] = DeclineCurveAnalysis.rate(qiTrue, diTrue, 0.0, t[i]);
    }
    // Corrupt the first three points (transient/cleanup).
    q[0] = 200.0;
    q[1] = 400.0;
    q[2] = 650.0;
    Map<String, Double> fit = DeclineCurveAnalysis.fitArps(t, q, 3, n - 1);
    Assertions.assertEquals(diTrue, fit.get("di"), 1.0e-4, "Windowed fit should recover di");
    Assertions.assertTrue(fit.get("rSquared") > 0.999);
  }

  @Test
  public void testDuongRoundTrip() {
    double q1True = 1000.0;
    double aTrue = 1.5;
    double mTrue = 1.2;
    int n = 40;
    double[] t = new double[n];
    double[] q = new double[n];
    for (int i = 0; i < n; i++) {
      t[i] = 10.0 + i * 50.0;
      q[i] = DeclineCurveAnalysis.rateDuong(q1True, aTrue, mTrue, t[i]);
    }
    Map<String, Double> fit = DeclineCurveAnalysis.fitDuong(t, q);
    Assertions.assertTrue(fit.get("rSquared") > 0.98, "Duong fit should reproduce the rate history");
    // Reconstructed rate should track the input closely.
    double q1 = fit.get("q1");
    double a = fit.get("a");
    double m = fit.get("m");
    for (int i = 0; i < n; i++) {
      double pred = DeclineCurveAnalysis.rateDuong(q1, a, m, t[i]);
      Assertions.assertEquals(q[i], pred, q[i] * 0.10 + 1.0, "Predicted Duong rate should be close");
    }
  }

  @Test
  public void testDuongRateAndCumulativePositive() {
    double q = DeclineCurveAnalysis.rateDuong(1000.0, 1.5, 1.2, 100.0);
    double gp = DeclineCurveAnalysis.cumulativeDuong(1000.0, 1.5, 1.2, 100.0);
    Assertions.assertTrue(q > 0.0);
    Assertions.assertTrue(gp > 0.0);
  }
}
