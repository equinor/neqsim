package neqsim.pvtsimulation.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for DeclineCurveAnalysis.
 */
class DeclineCurveAnalysisTest {

  // Standard test parameters
  private static final double QI = 1000.0; // bbl/d
  private static final double DI = 0.001; // 1/day
  private static final double B_EXP = 0.0; // exponential
  private static final double B_HYP = 0.5; // hyperbolic
  private static final double B_HAR = 1.0; // harmonic

  // ========== RATE ==========

  @Test
  void testRateAtTimeZero() {
    assertEquals(QI, DeclineCurveAnalysis.rate(QI, DI, B_HYP, 0.0), 1e-10,
        "Rate at t=0 should equal qi");
  }

  @Test
  void testExponentialRateDecline() {
    double q = DeclineCurveAnalysis.rateExponential(QI, DI, 365.25);
    double expected = QI * Math.exp(-DI * 365.25);
    assertEquals(expected, q, 1e-6, "Exponential rate formula");
  }

  @Test
  void testHarmonicRateDecline() {
    double q = DeclineCurveAnalysis.rateHarmonic(QI, DI, 365.25);
    double expected = QI / (1.0 + DI * 365.25);
    assertEquals(expected, q, 1e-6, "Harmonic rate formula");
  }

  @Test
  void testHyperbolicRateDecline() {
    double q = DeclineCurveAnalysis.rateHyperbolic(QI, DI, 0.5, 365.25);
    double expected = QI * Math.pow(1.0 + 0.5 * DI * 365.25, -1.0 / 0.5);
    assertEquals(expected, q, 1e-6, "Hyperbolic rate formula");
  }

  @Test
  void testRateMonotonicallyDecreases() {
    double prev = QI;
    for (int t = 100; t <= 3000; t += 100) {
      double q = DeclineCurveAnalysis.rate(QI, DI, B_HYP, t);
      assertTrue(q < prev, "Rate should monotonically decrease, t=" + t);
      assertTrue(q > 0, "Rate should remain positive");
      prev = q;
    }
  }

  @Test
  void testExponentialDeclinesFasterThanHyperbolic() {
    double qExp = DeclineCurveAnalysis.rate(QI, DI, B_EXP, 1000.0);
    double qHyp = DeclineCurveAnalysis.rate(QI, DI, B_HYP, 1000.0);
    double qHar = DeclineCurveAnalysis.rate(QI, DI, B_HAR, 1000.0);

    assertTrue(qExp < qHyp, "Exponential should decline faster than hyperbolic");
    assertTrue(qHyp < qHar, "Hyperbolic should decline faster than harmonic");
  }

  // ========== CUMULATIVE PRODUCTION ==========

  @Test
  void testCumulativeAtTimeZero() {
    assertEquals(0.0, DeclineCurveAnalysis.cumulativeProduction(QI, DI, B_HYP, 0.0), 1e-10);
  }

  @Test
  void testCumulativeExponential() {
    double np = DeclineCurveAnalysis.cumulativeExponential(QI, DI, 365.25);
    double expected = (QI / DI) * (1.0 - Math.exp(-DI * 365.25));
    assertEquals(expected, np, 1.0, "Exponential Np formula");
  }

  @Test
  void testCumulativeHarmonic() {
    double np = DeclineCurveAnalysis.cumulativeHarmonic(QI, DI, 365.25);
    double expected = (QI / DI) * Math.log(1.0 + DI * 365.25);
    assertEquals(expected, np, 1.0, "Harmonic Np formula");
  }

  @Test
  void testCumulativeIncreases() {
    double prev = 0.0;
    for (int t = 100; t <= 3000; t += 100) {
      double np = DeclineCurveAnalysis.cumulativeProduction(QI, DI, B_HYP, t);
      assertTrue(np > prev, "Cumulative should increase with time");
      prev = np;
    }
  }

  @Test
  void testHarmonicProducesMoreThanExponential() {
    double npExp = DeclineCurveAnalysis.cumulativeProduction(QI, DI, B_EXP, 3650.0);
    double npHyp = DeclineCurveAnalysis.cumulativeProduction(QI, DI, B_HYP, 3650.0);
    double npHar = DeclineCurveAnalysis.cumulativeProduction(QI, DI, B_HAR, 3650.0);

    assertTrue(npHar > npHyp, "Harmonic cumulative > hyperbolic");
    assertTrue(npHyp > npExp, "Hyperbolic cumulative > exponential");
  }

  @Test
  void testNoDeclinceMeansConstantRate() {
    double np = DeclineCurveAnalysis.cumulativeProduction(QI, 0.0, B_HYP, 365.25);
    assertEquals(QI * 365.25, np, 1.0, "Zero decline = constant rate * time");
  }

  // ========== DECLINE RATE CONVERSIONS ==========

  @Test
  void testNominalToEffectiveAnnual() {
    double dEff = DeclineCurveAnalysis.nominalToEffectiveAnnual(DI);
    assertTrue(dEff > 0 && dEff < 1, "Effective decline should be 0-1");
    // dEff = 1 - exp(-di * 365.25)
    double expected = 1.0 - Math.exp(-DI * 365.25);
    assertEquals(expected, dEff, 1e-10, "Effective annual decline formula");
  }

  @Test
  void testEffectiveToNominalRoundTrip() {
    double dEff = DeclineCurveAnalysis.nominalToEffectiveAnnual(DI);
    double dNomBack = DeclineCurveAnalysis.effectiveAnnualToNominal(dEff);
    assertEquals(DI, dNomBack, 1e-10, "Round-trip should recover original");
  }

  @Test
  void testEffectiveDeclineValidation() {
    assertThrows(IllegalArgumentException.class,
        () -> DeclineCurveAnalysis.effectiveAnnualToNominal(0.0));
    assertThrows(IllegalArgumentException.class,
        () -> DeclineCurveAnalysis.effectiveAnnualToNominal(1.0));
  }

  @Test
  void testInstantaneousDeclineRate() {
    // For exponential, D(t) = Di always
    assertEquals(DI, DeclineCurveAnalysis.instantaneousDeclineRate(DI, B_EXP, 500.0), 1e-12);

    // For hyperbolic, D(t) decreases with time
    double d0 = DeclineCurveAnalysis.instantaneousDeclineRate(DI, B_HYP, 0.0);
    double d1 = DeclineCurveAnalysis.instantaneousDeclineRate(DI, B_HYP, 1000.0);
    assertEquals(DI, d0, 1e-12, "D(0) should equal Di");
    assertTrue(d1 < d0, "Instantaneous decline should decrease with time for b>0");
  }

  // ========== TIME TO RATE AND EUR ==========

  @Test
  void testTimeToRateExponential() {
    double qTarget = 500.0;
    double tCalc = DeclineCurveAnalysis.timeToRate(QI, DI, B_EXP, qTarget);
    double qCheck = DeclineCurveAnalysis.rate(QI, DI, B_EXP, tCalc);
    assertEquals(qTarget, qCheck, 0.01, "Rate at calculated time should match target");
  }

  @Test
  void testTimeToRateHyperbolic() {
    double qTarget = 500.0;
    double tCalc = DeclineCurveAnalysis.timeToRate(QI, DI, B_HYP, qTarget);
    double qCheck = DeclineCurveAnalysis.rate(QI, DI, B_HYP, tCalc);
    assertEquals(qTarget, qCheck, 0.01, "Rate at calculated time should match target");
  }

  @Test
  void testEURPositive() {
    double eurVal = DeclineCurveAnalysis.eur(QI, DI, B_HYP, 50.0);
    assertTrue(eurVal > 0, "EUR should be positive");
    assertTrue(eurVal > QI * 365.0, "EUR should be > one year of production");
  }

  @Test
  void testEURExponentialLessThanHyperbolic() {
    double eurExp = DeclineCurveAnalysis.eur(QI, DI, B_EXP, 50.0);
    double eurHyp = DeclineCurveAnalysis.eur(QI, DI, B_HYP, 50.0);
    assertTrue(eurHyp > eurExp, "Hyperbolic EUR > exponential EUR");
  }

  @Test
  void testRemainingReserves() {
    double eurTotal = DeclineCurveAnalysis.eur(QI, DI, B_HYP, 50.0);
    double remaining = DeclineCurveAnalysis.remainingReserves(QI, DI, B_HYP, 365.25, 50.0);
    double produced = DeclineCurveAnalysis.cumulativeProduction(QI, DI, B_HYP, 365.25);

    assertEquals(eurTotal, remaining + produced, eurTotal * 0.001,
        "Remaining + produced should equal EUR");
  }

  // ========== FORECAST ==========

  @Test
  void testForecastShape() {
    double[][] profile = DeclineCurveAnalysis.forecast(QI, DI, B_HYP, 0, 3650, 365);

    assertEquals(11, profile[0].length, "Should have 11 time points");
    assertEquals(11, profile[1].length);
    assertEquals(11, profile[2].length);

    assertEquals(0.0, profile[0][0], 1e-6, "First time point");
    assertEquals(QI, profile[1][0], 1e-6, "Rate at t=0 should be qi");
    assertEquals(0.0, profile[2][0], 1e-6, "Cumulative at t=0 should be 0");

    // Rate should decrease
    for (int i = 1; i < profile[1].length; i++) {
      assertTrue(profile[1][i] < profile[1][i - 1], "Rate should decrease");
      assertTrue(profile[2][i] > profile[2][i - 1], "Cumulative should increase");
    }
  }

  @Test
  void testForecastInvalidStep() {
    assertThrows(IllegalArgumentException.class,
        () -> DeclineCurveAnalysis.forecast(QI, DI, B_HYP, 0, 3650, 0));
  }

  // ========== PARAMETER ESTIMATION ==========

  @Test
  void testEstimateExponentialDecline() {
    double diTrue = 0.002;
    double q1 = 1000.0 * Math.exp(-diTrue * 0);
    double q2 = 1000.0 * Math.exp(-diTrue * 365);

    double diEst = DeclineCurveAnalysis.estimateExponentialDecline(q1, 0.0, q2, 365.0);
    assertEquals(diTrue, diEst, 1e-6, "Should recover true decline rate");
  }

  @Test
  void testEstimateExponentialDeclineValidation() {
    assertThrows(IllegalArgumentException.class,
        () -> DeclineCurveAnalysis.estimateExponentialDecline(100, 0, 200, 365));
    assertThrows(IllegalArgumentException.class,
        () -> DeclineCurveAnalysis.estimateExponentialDecline(100, 365, 50, 0));
  }

  @Test
  void testEstimateHyperbolicParameters() {
    // Generate synthetic data
    double qiTrue = 1000.0;
    double diTrue = 0.002;
    double bTrue = 0.5;
    double q1 = DeclineCurveAnalysis.rate(qiTrue, diTrue, bTrue, 0.0);
    double q2 = DeclineCurveAnalysis.rate(qiTrue, diTrue, bTrue, 365.0);
    double q3 = DeclineCurveAnalysis.rate(qiTrue, diTrue, bTrue, 730.0);

    Map<String, Double> est =
        DeclineCurveAnalysis.estimateHyperbolicParameters(q1, 0.0, q2, 365.0, q3, 730.0);

    assertTrue(est.containsKey("qi"));
    assertTrue(est.containsKey("di"));
    assertTrue(est.containsKey("b"));

    // Check estimated b is close to true (within 0.1)
    assertEquals(bTrue, est.get("b"), 0.15, "Estimated b should be close to true");
  }

  // ========== SUMMARY ==========

  @Test
  void testSummaryContainsAllKeys() {
    Map<String, Double> summary = DeclineCurveAnalysis.summary(QI, DI, B_HYP, 50.0);

    assertTrue(summary.containsKey("qi"));
    assertTrue(summary.containsKey("di_perDay"));
    assertTrue(summary.containsKey("b"));
    assertTrue(summary.containsKey("EUR"));
    assertTrue(summary.containsKey("rate_1yr"));
    assertTrue(summary.containsKey("rate_5yr"));
    assertTrue(summary.containsKey("cumProd_1yr"));
    assertTrue(summary.containsKey("timeToEconomicLimit_years"));

    assertEquals(QI, summary.get("qi"), 1e-10);
    assertTrue(summary.get("EUR") > 0);
    assertTrue(summary.get("timeToEconomicLimit_years") > 0);
  }
}
