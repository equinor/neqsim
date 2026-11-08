package neqsim.pvtsimulation.reservoirproperties.materialbalance;

/**
 * Positive spectral quadrature of the infinite radial diffusion influence function.
 *
 * <p>
 * Integrates the unit-rate response over frequency using the modulus of the order-one Bessel functions. A low-frequency
 * remainder and positive resolved high-frequency tail preserve both representable time limits. Controlled Taylor and
 * exponential bounds avoid unnecessary transcendental evaluations. This internal scalar kernel is independent of fluid
 * models and reservoir histories.
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
  /** Taylor boundary chosen from a relative remainder below 4.18e-14, not fitted to reference values. */
  private static final double SMALL_ARGUMENT = 1.0e-3;
  /** Exponential boundary with pressure omission below 1.16e-33 for the checked spectral-weight sum. */
  private static final double NEGLIGIBLE_ARGUMENT = 80.0;
  /** Tail endpoint makes omitted pressure below 2.6e-183, including at the smallest positive time. */
  private static final double TAIL_UPPER_LOG = 420.0;
  /** Reciprocal Bessel-modulus asymptotic coefficients, in increasing even powers. */
  private static final double[] TAIL = {1.0, -3.0 / 8.0, 63.0 / 128.0, -1899.0 / 1024.0, 543483.0 / 32768.0};
  /** Positive quadrature nodes and ordinary integration weights on zero to one. */
  private static final double[][] LOW_QUADRATURE = gaussPanels(0.0, 1.0, 16);
  /** Same-quadrature anchor for a monotone logarithmic continuation. */
  private static final double EIN_AT_FIFTY = smallFrequencyIntegral(50.0)[0];
  /** Positive squared frequencies and pressure weights; initialized once and never exposed. */
  private static final double[][] QUADRATURE = quadrature();
  /** Tail frequencies, positive weights and exact suffix maxima; none are exposed. */
  private static final double[][] TAIL_QUADRATURE = tailQuadrature();

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
      double loss;
      double slope;
      if (argument <= SMALL_ARGUMENT) {
        loss = argument * (1.0 - argument * (0.5 - argument * (1.0 / 6.0 - argument / 24.0)));
        slope = 1.0 - argument * (1.0 - argument * (0.5 - argument / 6.0));
      } else if (argument >= NEGLIGIBLE_ARGUMENT) {
        loss = 1.0;
        slope = 0.0;
      } else {
        loss = -Math.expm1(-argument);
        slope = Math.exp(-argument);
      }
      pressure += QUADRATURE[1][i] * loss;
      derivative += QUADRATURE[1][i] * frequencySquared * slope;
    }
    double rootTime = Math.sqrt(time);
    for (int i = 0; i < TAIL_QUADRATURE[0].length; i++) {
      double scaledFrequency = rootTime * TAIL_QUADRATURE[0][i];
      double argument = scaledFrequency * scaledFrequency;
      if (argument >= NEGLIGIBLE_ARGUMENT && TAIL_QUADRATURE[2][i] < 0.25 * Math.ulp(pressure)) {
        // Every remaining positive addition rounds away individually; clipped derivatives are already zero.
        break;
      }
      double weight = TAIL_QUADRATURE[1][i];
      if (argument >= NEGLIGIBLE_ARGUMENT) {
        pressure += weight;
      } else {
        double loss;
        double slope;
        if (argument <= SMALL_ARGUMENT) {
          loss = argument * (1.0 - argument * (0.5 - argument * (1.0 / 6.0 - argument / 24.0)));
          slope = 1.0 - argument * (1.0 - argument * (0.5 - argument / 6.0));
        } else {
          loss = -Math.expm1(-argument);
          slope = Math.exp(-argument);
        }
        pressure += weight * loss;
        // This ordering avoids squaring very large frequencies before exponential damping.
        derivative += weight * argument * slope / time;
      }
    }
    return new double[] {pressure, derivative};
  }

  /**
   * Evaluates Ein(x) and its derivative using bounded Taylor remainders or positive fixed quadrature.
   *
   * @param x finite nonnegative argument
   * @return entire exponential integral and its derivative with respect to x
   */
  private static double[] smallFrequencyIntegral(double x) {
    if (x <= SMALL_ARGUMENT) {
      double value = x * (1.0 - x * (0.25 - x * (1.0 / 18.0 - x / 96.0)));
      double derivative = 1.0 - x * (0.5 - x * (1.0 / 6.0 - x / 24.0));
      return new double[] {value, derivative};
    }
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
   * Resolves the same reciprocal-modulus tail with positive fixed nodes, avoiding cancellation in its analytic form.
   *
   * @return private frequency, pressure-weight and remaining-maximum-weight arrays
   */
  private static double[][] tailQuadrature() {
    int panels = (int) Math.ceil((TAIL_UPPER_LOG - Math.log(UPPER)) / 0.5);
    double[][] nodes = gaussPanels(Math.log(UPPER), TAIL_UPPER_LOG, panels);
    double[][] result = new double[3][nodes[0].length];
    for (int i = 0; i < nodes[0].length; i++) {
      double u = Math.exp(nodes[0][i]);
      double inverseSquared = (1.0 / u) / u;
      double polynomial = TAIL[TAIL.length - 1];
      for (int k = TAIL.length - 2; k >= 0; k--) {
        polynomial = TAIL[k] + inverseSquared * polynomial;
      }
      result[0][i] = u;
      result[1][i] = (2.0 / Math.PI) * nodes[1][i] / u * polynomial;
    }
    double maximum = 0.0;
    for (int i = result[0].length - 1; i >= 0; i--) {
      maximum = Math.max(maximum, result[1][i]);
      result[2][i] = maximum;
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
