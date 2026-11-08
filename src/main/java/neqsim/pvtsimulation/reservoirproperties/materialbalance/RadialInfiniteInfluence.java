package neqsim.pvtsimulation.reservoirproperties.materialbalance;

import org.apache.commons.math3.special.Erf;

/**
 * Positive spectral quadrature of the infinite radial diffusion influence function.
 *
 * <p>
 * Integrates the unit-rate response over frequency using the modulus of the order-one Bessel functions. Analytic low-
 * and high-frequency remainders preserve both time limits. This is an internal scalar numerical kernel, independent of
 * fluid models and reservoir histories.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
final class RadialInfiniteInfluence {
  /** Euler's constant in the small-frequency integral. */
  private static final double EULER = 0.5772156649015328606;
  /** Lower logarithmic frequency. */
  private static final double LOWER_LOG = -40.0;
  /** Upper frequency for the numerical integral. */
  private static final double UPPER = 64.0;
  /** Squared lower frequency, used by the analytic remainder. */
  private static final double LOWER_SQUARED = Math.exp(2.0 * LOWER_LOG);
  /** Reciprocal Bessel-modulus asymptotic coefficients, in increasing even powers. */
  private static final double[] TAIL = {1.0, -3.0 / 8.0, 63.0 / 128.0, -1899.0 / 1024.0, 543483.0 / 32768.0};
  /** Positive quadrature nodes and ordinary integration weights on zero to one. */
  private static final double[][] LOW_QUADRATURE = gaussPanels(0.0, 1.0, 16);
  /** Same-quadrature anchor for a monotone logarithmic continuation. */
  private static final double EIN_AT_FIFTY = smallFrequencyIntegral(50.0)[0];
  /** Positive squared frequencies and pressure weights; initialized once and never exposed. */
  private static final double[][] QUADRATURE = quadrature();

  /** Prevents construction of the stateless internal kernel. */
  private RadialInfiniteInfluence() {
  }

  /**
   * Evaluates the pressure and its analytic time derivative from the same spectral measure.
   *
   * @param time finite nonnegative dimensionless time
   * @return independent pair containing pressure and derivative; zero at the bookkeeping origin
   */
  static double[] evaluate(double time) {
    if (!Double.isFinite(time) || time < 0.0) {
      throw new IllegalArgumentException("Dimensionless aquifer time must be finite and nonnegative");
    }
    if (time == 0.0) {
      return new double[] {0.0, 0.0};
    }
    double x = time * LOWER_SQUARED;
    double[] low = x > 50.0 ? new double[] {0.5 * (EIN_AT_FIFTY + Math.log(x / 50.0)), 0.5 / time}
        : smallFrequencyIntegral(x);
    if (x <= 50.0) {
      low[0] *= 0.5;
      low[1] *= 0.5 * LOWER_SQUARED;
    }
    double pressure = low[0];
    double derivative = low[1];
    for (int i = 0; i < QUADRATURE[0].length; i++) {
      double frequencySquared = QUADRATURE[0][i];
      double argument = time * frequencySquared;
      pressure += QUADRATURE[1][i] * -Math.expm1(-argument);
      derivative += QUADRATURE[1][i] * frequencySquared * Math.exp(-argument);
    }
    double rootTime = Math.sqrt(time);
    double z = UPPER * rootTime;
    double tailPressure = 0.0;
    double tailDerivative = 0.0;
    if (z > 26.0) {
      // Exponential remainders are below exp(-676); avoid overflow times zero at large times.
      for (int i = 0; i < TAIL.length; i++) {
        tailPressure += TAIL[i] / ((2 * i + 1) * Math.pow(UPPER, 2 * i + 1));
      }
    } else {
      double argument = z * z;
      double exponential = Math.exp(-argument);
      double loss = -Math.expm1(-argument);
      double integral = 0.5 * Math.sqrt(Math.PI) / rootTime * Erf.erfc(z);
      for (int i = 0; i < TAIL.length; i++) {
        int power = 2 * i + 1;
        double inversePower = 1.0 / Math.pow(UPPER, power);
        tailPressure += TAIL[i] * (loss * inversePower + 2.0 * time * integral) / power;
        tailDerivative += TAIL[i] * integral;
        integral = (exponential * inversePower - 2.0 * time * integral) / power;
      }
    }
    return new double[] {pressure + 2.0 / Math.PI * tailPressure, derivative + 2.0 / Math.PI * tailDerivative};
  }

  /**
   * Evaluates Ein(x) and its derivative using a positive fixed quadrature, without an origin singularity.
   *
   * @param x finite nonnegative argument
   * @return entire exponential integral and its derivative with respect to x
   */
  private static double[] smallFrequencyIntegral(double x) {
    double value = 0.0;
    double derivative = 0.0;
    for (int i = 0; i < LOW_QUADRATURE[0].length; i++) {
      double node = LOW_QUADRATURE[0][i];
      double weight = LOW_QUADRATURE[1][i];
      value += weight * -Math.expm1(-x * node) / node;
      derivative += weight * Math.exp(-x * node);
    }
    return new double[] {value, derivative};
  }

  /**
   * Builds eight-point Gauss-Legendre panels in logarithmic frequency.
   *
   * @return private arrays of squared frequencies and positive pressure weights
   */
  private static double[][] quadrature() {
    int panels = (int) Math.ceil((Math.log(UPPER) - LOWER_LOG) / 0.5);
    double[][] result = gaussPanels(LOWER_LOG, Math.log(UPPER), panels);
    for (int i = 0; i < result[0].length; i++) {
      double u = Math.exp(result[0][i]);
      result[0][i] = u * u;
      result[1][i] *= 4.0 / (Math.PI * Math.PI * u * u * modulusSquared(u));
    }
    return result;
  }

  /**
   * Creates positive eight-point Gauss-Legendre panels on a finite interval.
   *
   * @param lower interval start
   * @param upper interval end
   * @param panels positive number of equal-width panels
   * @return private node and ordinary integration-weight arrays
   */
  private static double[][] gaussPanels(double lower, double upper, int panels) {
    double[] nodes = {-0.9602898564975363, -0.7966664774136267, -0.5255324099163290, -0.1834346424956498,
        0.1834346424956498, 0.5255324099163290, 0.7966664774136267, 0.9602898564975363};
    double[] weights = {0.1012285362903763, 0.2223810344533745, 0.3137066458778873, 0.3626837833783620,
        0.3626837833783620, 0.3137066458778873, 0.2223810344533745, 0.1012285362903763};
    double width = (upper - lower) / panels;
    double[][] result = new double[2][panels * nodes.length];
    for (int k = 0; k < panels; k++) {
      for (int j = 0; j < nodes.length; j++) {
        int index = k * nodes.length + j;
        result[0][index] = lower + width * (k + 0.5 * (nodes[j] + 1.0));
        result[1][index] = weights[j] * width * 0.5;
      }
    }
    return result;
  }

  /**
   * Evaluates J1(u)^2+Y1(u)^2 using convergent small-argument series or the nonoscillatory modulus.
   *
   * @param u positive frequency
   * @return positive squared Bessel modulus
   */
  private static double modulusSquared(double u) {
    if (u < 12.0) {
      double term = 0.5 * u;
      double j = term;
      double harmonic = 0.0;
      double sum = term * (1.0 - 2.0 * EULER);
      for (int k = 1; k < 100; k++) {
        term *= -0.25 * u * u / (k * (k + 1.0));
        harmonic += 1.0 / k;
        j += term;
        sum += term * (2.0 * harmonic + 1.0 / (k + 1.0) - 2.0 * EULER);
        if (Math.abs(term) < 1.0e-20) {
          double y = (2.0 * Math.log(0.5 * u) * j - 2.0 / u - sum) / Math.PI;
          return j * j + y * y;
        }
      }
      throw new IllegalStateException("Aquifer Bessel modulus series did not converge");
    }
    double p = 1.0;
    double q = 0.0;
    double term = 1.0;
    double previous = 1.0;
    for (int k = 1; k <= 64; k++) {
      term *= (4.0 - (2.0 * k - 1.0) * (2.0 * k - 1.0)) / (8.0 * k * u);
      if (Math.abs(term) > previous) {
        break;
      }
      double signed = (k / 2) % 2 == 0 ? term : -term;
      if (k % 2 == 0) {
        p += signed;
      } else {
        q += signed;
      }
      previous = Math.abs(term);
    }
    return 2.0 / (Math.PI * u) * (p * p + q * q);
  }
}
