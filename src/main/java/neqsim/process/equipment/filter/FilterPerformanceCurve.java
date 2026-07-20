package neqsim.process.equipment.filter;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Particle-size dependent filtration performance represented by beta ratios.
 *
 * <p>
 * The beta ratio is the upstream particle count divided by the downstream count for particles at or above a stated
 * size. Fractional removal is therefore {@code 1 - 1 / beta}. Curves can hold supplier or laboratory multi-pass test
 * results such as data produced using ISO 16889. NeqSim does not invent a standard performance class or certify an
 * element; the caller supplies the applicable test points.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class FilterPerformanceCurve implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private double[] particleSizesMicrometre = new double[0];
  private double[] betaRatios = new double[0];
  private String testStandard = "";

  /** Empty performance curve. */
  public FilterPerformanceCurve() {}

  /**
   * Creates a performance curve.
   *
   * @param particleSizesMicrometre strictly increasing particle sizes in micrometres
   * @param betaRatios beta ratios, each greater than or equal to one
   */
  public FilterPerformanceCurve(double[] particleSizesMicrometre, double[] betaRatios) {
    setPoints(particleSizesMicrometre, betaRatios);
  }

  /**
   * Replaces all curve points.
   *
   * @param particleSizesMicrometre strictly increasing particle sizes in micrometres
   * @param betaRatios beta ratios, each greater than or equal to one
   */
  public final void setPoints(double[] particleSizesMicrometre, double[] betaRatios) {
    validatePoints(particleSizesMicrometre, betaRatios);
    this.particleSizesMicrometre = Arrays.copyOf(particleSizesMicrometre, particleSizesMicrometre.length);
    this.betaRatios = Arrays.copyOf(betaRatios, betaRatios.length);
  }

  /**
   * Returns the beta ratio at a particle size using log-linear interpolation.
   *
   * <p>
   * Values outside the supplied size range use the nearest endpoint rather than extrapolation.
   * </p>
   *
   * @param particleSizeMicrometre particle size in micrometres
   * @return interpolated beta ratio, or one when the curve is empty
   */
  public double getBetaRatio(double particleSizeMicrometre) {
    if (particleSizesMicrometre.length == 0) {
      return 1.0;
    }
    if (particleSizeMicrometre <= particleSizesMicrometre[0]) {
      return betaRatios[0];
    }
    int last = particleSizesMicrometre.length - 1;
    if (particleSizeMicrometre >= particleSizesMicrometre[last]) {
      return betaRatios[last];
    }
    for (int i = 1; i < particleSizesMicrometre.length; i++) {
      if (particleSizeMicrometre <= particleSizesMicrometre[i]) {
        double fraction = (particleSizeMicrometre - particleSizesMicrometre[i - 1])
            / (particleSizesMicrometre[i] - particleSizesMicrometre[i - 1]);
        double lowerLog = Math.log(betaRatios[i - 1]);
        double upperLog = Math.log(betaRatios[i]);
        return Math.exp(lowerLog + fraction * (upperLog - lowerLog));
      }
    }
    return betaRatios[last];
  }

  /**
   * Returns fractional removal at a particle size.
   *
   * @param particleSizeMicrometre particle size in micrometres
   * @return removal efficiency from zero to one
   */
  public double getRemovalEfficiency(double particleSizeMicrometre) {
    double beta = getBetaRatio(particleSizeMicrometre);
    return Math.max(0.0, Math.min(1.0, 1.0 - 1.0 / beta));
  }

  /** @return number of supplied curve points */
  public int size() {
    return particleSizesMicrometre.length;
  }

  /** @return defensive copy of particle-size points in micrometres */
  public double[] getParticleSizesMicrometre() {
    return Arrays.copyOf(particleSizesMicrometre, particleSizesMicrometre.length);
  }

  /** @return defensive copy of beta-ratio points */
  public double[] getBetaRatios() {
    return Arrays.copyOf(betaRatios, betaRatios.length);
  }

  /**
   * Records the standard or supplier method used to obtain the curve.
   *
   * @param testStandard reference such as {@code ISO 16889:2022}
   */
  public void setTestStandard(String testStandard) {
    this.testStandard = testStandard == null ? "" : testStandard;
  }

  /** @return recorded test standard or an empty string */
  public String getTestStandard() {
    return testStandard;
  }

  private void validatePoints(double[] sizes, double[] ratios) {
    if (sizes == null || ratios == null || sizes.length == 0 || sizes.length != ratios.length) {
      throw new IllegalArgumentException("Particle sizes and beta ratios must have the same non-zero length");
    }
    double previous = 0.0;
    for (int i = 0; i < sizes.length; i++) {
      if (!Double.isFinite(sizes[i]) || sizes[i] <= 0.0 || (i > 0 && sizes[i] <= previous)) {
        throw new IllegalArgumentException("Particle sizes must be finite, positive, and strictly increasing");
      }
      if (!Double.isFinite(ratios[i]) || ratios[i] < 1.0) {
        throw new IllegalArgumentException("Beta ratios must be finite and greater than or equal to one");
      }
      previous = sizes[i];
    }
  }
}
