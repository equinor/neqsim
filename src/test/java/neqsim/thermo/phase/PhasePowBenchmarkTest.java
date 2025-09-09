package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/**
 * Simple benchmark comparing Math.pow implementations with explicit multiplications
 * for selected expressions.
 */
public class PhasePowBenchmarkTest {
  private static double calcLngVV_old(double t, double b) {
    return 2.0
        * (640.0 * Math.pow(t, 3.0) - 216.0 * b * t * t + 24.0 * Math.pow(b, 2.0) * t
            - Math.pow(b, 3.0))
        * b / (t * t) / Math.pow(8.0 * t - b, 2.0) / Math.pow(4.0 * t - b, 2.0);
  }

  private static double calcLngVV_new(double t, double b) {
    double t2 = t * t;
    double t3 = t2 * t;
    double b2 = b * b;
    double b3 = b2 * b;
    double denom1 = 8.0 * t - b;
    double denom1Sq = denom1 * denom1;
    double denom2 = 4.0 * t - b;
    double denom2Sq = denom2 * denom2;
    double term = 640.0 * t3 - 216.0 * b * t2 + 24.0 * b2 * t - b3;
    return 2.0 * term * b / t2 / denom1Sq / denom2Sq;
  }

  private static double calcLngVVV_old(double t, double b) {
    return 4.0
        * (Math.pow(b, 5.0) + 17664.0 * Math.pow(t, 4.0) * b
            - 4192.0 * Math.pow(t, 3.0) * Math.pow(b, 2.0)
            + 528.0 * Math.pow(b, 3.0) * t * t - 36.0 * t * Math.pow(b, 4.0)
            - 30720.0 * Math.pow(t, 5.0))
        * b / Math.pow(t, 3.0) / Math.pow(b - 8.0 * t, 3.0) / Math.pow(b - 4.0 * t, 3.0);
  }

  private static double calcLngVVV_new(double t, double b) {
    double t2 = t * t;
    double t3 = t2 * t;
    double t4 = t3 * t;
    double t5 = t4 * t;
    double b2 = b * b;
    double b3 = b2 * b;
    double b4 = b3 * b;
    double b5 = b4 * b;
    double term =
        b5 + 17664.0 * t4 * b - 4192.0 * t3 * b2 + 528.0 * b3 * t2 - 36.0 * t * b4
            - 30720.0 * t5;
    double denom1 = b - 8.0 * t;
    double denom1Cubed = denom1 * denom1 * denom1;
    double denom2 = b - 4.0 * t;
    double denom2Cubed = denom2 * denom2 * denom2;
    return 4.0 * term * b / t3 / denom1Cubed / denom2Cubed;
  }

  @Test
  public void benchmarkPowReplacements() {
    double t = 1.0;
    double b = 0.5;
    int loops = 1_000_000;
    double res = 0.0;
    long start = System.nanoTime();
    for (int i = 0; i < loops; i++) {
      res += calcLngVV_old(t, b);
      res += calcLngVVV_old(t, b);
    }
    long mid = System.nanoTime();
    for (int i = 0; i < loops; i++) {
      res += calcLngVV_new(t, b);
      res += calcLngVVV_new(t, b);
    }
    long end = System.nanoTime();
    System.out.println("old implementation: " + (mid - start));
    System.out.println("new implementation: " + (end - mid));
    assertNotEquals(0.0, res);
  }
}

