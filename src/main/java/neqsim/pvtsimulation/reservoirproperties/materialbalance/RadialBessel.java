package neqsim.pvtsimulation.reservoirproperties.materialbalance;

/**
 * Order-one Bessel functions for radial diffusion. See NIST DLMF sections 10.8 and 10.17.
 *
 * @author NeqSim
 * @version 1.0
 */
final class RadialBessel {
  /** Euler's constant in the small-argument series. */
  private static final double EULER = 0.5772156649015328606;
  /** Transition from convergent series to Hankel expansion. */
  private static final double HANKEL_START = 12.0;

  /** Prevents construction of the internal utility. */
  private RadialBessel() {
  }

  /**
   * Evaluates the squared order-one modulus without oscillatory cancellation.
   *
   * @param x positive argument
   * @return squared modulus
   */
  static double modulusSquared(double x) {
    if (x < HANKEL_START) {
      double[] value = series(x);
      return value[0] * value[0] + value[1] * value[1];
    }
    double[] amplitude = hankel(x);
    return 2.0 / (Math.PI * x) * (amplitude[0] * amplitude[0] + amplitude[1] * amplitude[1]);
  }

  /**
   * Evaluates the order-one Bessel functions of the first and second kind.
   *
   * @param x positive argument
   * @return J1 and Y1, respectively
   */
  static double[] orderOne(double x) {
    if (x < HANKEL_START) {
      return series(x);
    }
    double[] amplitude = hankel(x);
    double angle = x - 0.75 * Math.PI;
    double scale = Math.sqrt(2.0 / (Math.PI * x));
    double cos = Math.cos(angle);
    double sin = Math.sin(angle);
    return new double[] {scale * (cos * amplitude[0] - sin * amplitude[1]),
        scale * (sin * amplitude[0] + cos * amplitude[1])};
  }

  /**
   * Evaluates the convergent order-one power and logarithmic series.
   *
   * @param x positive argument below the Hankel transition
   * @return J1 and Y1, respectively
   */
  private static double[] series(double x) {
    double term = 0.5 * x;
    double j = term;
    double harmonic = 0.0;
    double sum = term * (1.0 - 2.0 * EULER);
    for (int k = 1; k < 100; k++) {
      term *= -0.25 * x * x / (k * (k + 1.0));
      harmonic += 1.0 / k;
      j += term;
      sum += term * (2.0 * harmonic + 1.0 / (k + 1.0) - 2.0 * EULER);
      if (Math.abs(term) < 1.0e-20) {
        double y = (2.0 * Math.log(0.5 * x) * j - 2.0 / x - sum) / Math.PI;
        return new double[] {j, y};
      }
    }
    throw new IllegalStateException("Aquifer Bessel series did not converge");
  }

  /**
   * Evaluates the even and odd Hankel amplitude polynomials through their least term.
   *
   * @param x argument at or above the Hankel transition
   * @return nonoscillatory amplitudes P and Q
   */
  private static double[] hankel(double x) {
    double p = 1.0;
    double q = 0.0;
    double term = 1.0;
    double previous = 1.0;
    for (int k = 1; k <= 64; k++) {
      term *= (4.0 - (2.0 * k - 1.0) * (2.0 * k - 1.0)) / (8.0 * k * x);
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
    return new double[] {p, q};
  }
}
