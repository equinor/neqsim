package neqsim.pvtsimulation.reservoirproperties.materialbalance;

/**
 * Prepared constant-terminal-rate response of a homogeneous, slightly compressible, sealed radial annulus.
 *
 * <p>
 * The dimensionless inner radius is one, the initial pressure perturbation is zero and the imposed inner wall flux is
 * unity. The outer wall has zero flux. For outer/inner radius ratios from 2 to 10, the response uses the
 * infinite-domain spectrum before the outer reflection becomes significant and the finite radial diffusion eigenmodes
 * thereafter. One immutable instance may be shared across histories at the same radius ratio.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class FiniteRadialAquiferInfluence {
  /** Number of positive modes, excluding the separately integrated constant storage mode. */
  private static final int MODE_COUNT = 16;
  /** Steady pressure rate for closed storage. */
  private final double storageSlope;
  /** Pseudo-steady wall excess above the mean pressure. */
  private final double intercept;
  /** Last time evaluated with the infinite-domain spectrum. */
  private final double earlyTime;
  /** Positive eigenvalues of the dimensionless negative radial Laplacian. */
  private final double[] eigenvalues = new double[MODE_COUNT];
  /** Wall derivative weights of the orthogonal modes. */
  private final double[] weights = new double[MODE_COUNT];

  /**
   * Prepares the finite response for one dimensionless annulus.
   *
   * @param outerRadiusRatio outer radius divided by inner radius, from 2 through 10 inclusive
   * @throws IllegalArgumentException if the ratio is nonfinite or outside the supported interval
   */
  public FiniteRadialAquiferInfluence(double outerRadiusRatio) {
    if (!Double.isFinite(outerRadiusRatio) || outerRadiusRatio < 2.0 || outerRadiusRatio > 10.0) {
      throw new IllegalArgumentException("Sealed radial response requires an outer/inner radius ratio in [2, 10]");
    }
    double width = outerRadiusRatio - 1.0;
    double radiusSquared = outerRadiusRatio * outerRadiusRatio;
    double storageArea = radiusSquared - 1.0;
    storageSlope = 2.0 / storageArea;
    intercept = Math.log(outerRadiusRatio) * radiusSquared * radiusSquared / (storageArea * storageArea)
        - (3.0 * radiusSquared - 1.0) / (4.0 * storageArea);
    earlyTime = width * width / 64.0;
    for (int i = 0; i < MODE_COUNT; i++) {
      double frequency = frequency(i + 1, outerRadiusRatio, width);
      eigenvalues[i] = frequency * frequency;
      double ratio = RadialBessel.modulusSquared(frequency) / RadialBessel.modulusSquared(outerRadiusRatio * frequency);
      weights[i] = 2.0 / (ratio - 1.0);
      if (!(weights[i] > 0.0) || !Double.isFinite(weights[i])) {
        throw new IllegalStateException("Invalid wall weight for sealed radial mode " + (i + 1));
      }
    }
  }

  /**
   * Evaluates the pressure perturbation at the inner wall for unit rate.
   *
   * @param dimensionlessTime finite nonnegative radial diffusion time
   * @return dimensionless wall pressure, zero at the origin
   */
  public double dimensionlessPressure(double dimensionlessTime) {
    return evaluate(dimensionlessTime)[0];
  }

  /**
   * Evaluates the derivative of the wall pressure with respect to dimensionless time.
   *
   * @param dimensionlessTime finite nonnegative radial diffusion time
   * @return derivative, with a bookkeeping value of zero at the origin and a divergent physical right limit
   */
  public double dimensionlessPressureDerivative(double dimensionlessTime) {
    return evaluate(dimensionlessTime)[1];
  }

  /**
   * Evaluates pressure, derivative and pressure minus time times derivative. The third quantity is the directly
   * evaluated Carter–Tracy denominator contribution, with the constant storage term canceled analytically.
   *
   * @param time finite nonnegative dimensionless time
   * @return pressure, derivative and stable pressure excess
   */
  double[] evaluate(double time) {
    if (!Double.isFinite(time) || time < 0.0) {
      throw new IllegalArgumentException("Dimensionless aquifer time must be finite and nonnegative");
    }
    if (time <= earlyTime) {
      double[] infinite = RadialInfiniteInfluence.evaluate(time);
      return new double[] {infinite[0], infinite[1], infinite[0] - time * infinite[1]};
    }
    double slope = storageSlope;
    double pressureExcess = intercept;
    double denominatorExcess = intercept;
    for (int i = 0; i < MODE_COUNT; i++) {
      double argument = eigenvalues[i] * time;
      if (argument >= 80.0) {
        break;
      }
      double transientSlope = weights[i] * Math.exp(-argument);
      double transientPressure = transientSlope / eigenvalues[i];
      slope += transientSlope;
      pressureExcess -= transientPressure;
      denominatorExcess -= transientPressure + time * transientSlope;
    }
    return new double[] {storageSlope * time + pressureExcess, slope, denominatorExcess};
  }

  /**
   * Bisects the Neumann determinant in the disjoint Bessel-phase bracket for mode n. The order-one phase minus its
   * argument decreases between -pi/2 and -3pi/4, so the root lies between n*pi/L and (n*pi+pi/4)/L.
   *
   * @param mode positive mode index
   * @param radius outer radius ratio
   * @param width dimensionless annular width
   * @return positive radial frequency
   */
  private static double frequency(int mode, double radius, double width) {
    double low = mode * Math.PI / width;
    double high = (mode + 0.25) * Math.PI / width;
    double lowValue = determinant(low, radius);
    double highValue = determinant(high, radius);
    if (Math.signum(lowValue) == Math.signum(highValue)) {
      throw new IllegalStateException("Cannot bracket sealed radial mode " + mode);
    }
    for (int step = 0; step < 52; step++) {
      double middle = low + 0.5 * (high - low);
      if (middle <= low || middle >= high) {
        break;
      }
      double value = determinant(middle, radius);
      if (value == 0.0) {
        return middle;
      }
      if (Math.signum(value) == Math.signum(lowValue)) {
        low = middle;
      } else {
        high = middle;
      }
    }
    return low + 0.5 * (high - low);
  }

  /**
   * Evaluates the order-one Neumann determinant for the two wall radii.
   *
   * @param frequency positive dimensionless radial frequency
   * @param radius outer radius ratio
   * @return determinant
   */
  private static double determinant(double frequency, double radius) {
    double[] inner = RadialBessel.orderOne(frequency);
    double[] outer = RadialBessel.orderOne(radius * frequency);
    return inner[0] * outer[1] - inner[1] * outer[0];
  }
}
