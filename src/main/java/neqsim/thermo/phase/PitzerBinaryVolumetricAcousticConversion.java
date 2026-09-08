package neqsim.thermo.phase;

import java.io.Serializable;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;

/**
 * Parameter-neutral conversion of acoustic measurements to volumetric compressibility.
 *
 * <p>
 * Density and speed of sound determine isentropic compressibility. Temperature, volumetric thermal expansivity and
 * mass-specific isobaric heat capacity supply the thermodynamic correction to isothermal compressibility. This class
 * only evaluates those identities in SI units; it does not fit or install volumetric Pitzer parameters.
 * </p>
 */
public final class PitzerBinaryVolumetricAcousticConversion {
  /** Covariance index for temperature in K. */
  public static final int TEMPERATURE_INDEX = 0;
  /** Covariance index for density in kg/m3. */
  public static final int DENSITY_INDEX = 1;
  /** Covariance index for speed of sound in m/s. */
  public static final int SOUND_SPEED_INDEX = 2;
  /** Covariance index for volumetric thermal expansivity in 1/K. */
  public static final int THERMAL_EXPANSION_INDEX = 3;
  /** Covariance index for mass-specific isobaric heat capacity in J/(kg K). */
  public static final int HEAT_CAPACITY_INDEX = 4;
  /** Number of inputs represented by the covariance matrix and sensitivity Jacobian. */
  public static final int INPUT_COUNT = 5;

  private static final double COVARIANCE_SYMMETRY_TOLERANCE = 1.0e-12;
  private static final double CORRELATION_EIGENVALUE_TOLERANCE = 1.0e-10;

  private PitzerBinaryVolumetricAcousticConversion() {
  }

  /**
   * Calculate isentropic compressibility from density and speed of sound.
   *
   * @param densityKgPerM3 solution density in kg/m3
   * @param soundSpeedMPerS speed of sound in m/s
   * @return isentropic compressibility in 1/Pa
   */
  public static double calculateIsentropicCompressibility(double densityKgPerM3, double soundSpeedMPerS) {
    requirePositive(densityKgPerM3, "Density");
    requirePositive(soundSpeedMPerS, "Speed of sound");
    double compressibility = 1.0 / (densityKgPerM3 * soundSpeedMPerS * soundSpeedMPerS);
    requirePositive(compressibility, "Isentropic compressibility");
    return compressibility;
  }

  /**
   * Calculate isothermal compressibility from acoustic and caloric properties.
   *
   * @param temperatureK absolute temperature in K
   * @param densityKgPerM3 solution density in kg/m3
   * @param soundSpeedMPerS speed of sound in m/s
   * @param thermalExpansionPerK volumetric thermal expansivity in 1/K
   * @param specificHeatCapacityJPerKgK mass-specific isobaric heat capacity in J/(kg K)
   * @return isothermal compressibility in 1/Pa
   */
  public static double calculateIsothermalCompressibility(double temperatureK, double densityKgPerM3,
      double soundSpeedMPerS, double thermalExpansionPerK, double specificHeatCapacityJPerKgK) {
    validateInputs(temperatureK, densityKgPerM3, soundSpeedMPerS, thermalExpansionPerK, specificHeatCapacityJPerKgK);
    double isentropicCompressibility = calculateIsentropicCompressibility(densityKgPerM3, soundSpeedMPerS);
    double isothermalCompressibility = isentropicCompressibility
        + calculateThermalCorrection(temperatureK, densityKgPerM3, thermalExpansionPerK, specificHeatCapacityJPerKgK);
    requirePositive(isothermalCompressibility, "Isothermal compressibility");
    return isothermalCompressibility;
  }

  /**
   * Convert acoustic inputs and propagate their full covariance through the analytic Jacobian.
   *
   * <p>
   * Covariance rows and columns must follow the public input-index constants in this class. Entries therefore carry the
   * products of the corresponding SI input units. The matrix must be finite, symmetric and positive semidefinite.
   * </p>
   *
   * @param temperatureK absolute temperature in K
   * @param densityKgPerM3 solution density in kg/m3
   * @param soundSpeedMPerS speed of sound in m/s
   * @param thermalExpansionPerK volumetric thermal expansivity in 1/K
   * @param specificHeatCapacityJPerKgK mass-specific isobaric heat capacity in J/(kg K)
   * @param inputCovariance full 5-by-5 input covariance matrix in the documented order
   * @return immutable converted values, sensitivities and propagated standard uncertainty
   */
  public static ConversionResult convertWithUncertainty(double temperatureK, double densityKgPerM3,
      double soundSpeedMPerS, double thermalExpansionPerK, double specificHeatCapacityJPerKgK,
      double[][] inputCovariance) {
    validateInputs(temperatureK, densityKgPerM3, soundSpeedMPerS, thermalExpansionPerK, specificHeatCapacityJPerKgK);
    double[][] covariance = validateAndCopyCovariance(inputCovariance);

    double isentropicCompressibility = calculateIsentropicCompressibility(densityKgPerM3, soundSpeedMPerS);
    double thermalCorrection = calculateThermalCorrection(temperatureK, densityKgPerM3, thermalExpansionPerK,
        specificHeatCapacityJPerKgK);
    double isothermalCompressibility = isentropicCompressibility + thermalCorrection;
    requirePositive(isothermalCompressibility, "Isothermal compressibility");

    double[] jacobian = new double[INPUT_COUNT];
    jacobian[TEMPERATURE_INDEX] = thermalExpansionPerK * thermalExpansionPerK
        / (densityKgPerM3 * specificHeatCapacityJPerKgK);
    jacobian[DENSITY_INDEX] = -isothermalCompressibility / densityKgPerM3;
    jacobian[SOUND_SPEED_INDEX] = -2.0 * isentropicCompressibility / soundSpeedMPerS;
    jacobian[THERMAL_EXPANSION_INDEX] = 2.0 * temperatureK * thermalExpansionPerK
        / (densityKgPerM3 * specificHeatCapacityJPerKgK);
    jacobian[HEAT_CAPACITY_INDEX] = -thermalCorrection / specificHeatCapacityJPerKgK;

    double propagatedVariance = 0.0;
    double absoluteContributionSum = 0.0;
    for (int row = 0; row < INPUT_COUNT; row++) {
      for (int column = 0; column < INPUT_COUNT; column++) {
        double contribution = jacobian[row] * covariance[row][column] * jacobian[column];
        propagatedVariance += contribution;
        absoluteContributionSum += Math.abs(contribution);
      }
    }
    double negativeTolerance = COVARIANCE_SYMMETRY_TOLERANCE * absoluteContributionSum;
    if (!Double.isFinite(propagatedVariance) || propagatedVariance < -negativeTolerance) {
      throw new IllegalArgumentException("Input covariance produces an invalid compressibility variance");
    }
    propagatedVariance = Math.max(0.0, propagatedVariance);

    return new ConversionResult(isentropicCompressibility, thermalCorrection, isothermalCompressibility,
        Math.sqrt(propagatedVariance), jacobian);
  }

  private static double calculateThermalCorrection(double temperatureK, double densityKgPerM3,
      double thermalExpansionPerK, double specificHeatCapacityJPerKgK) {
    double correction = temperatureK * thermalExpansionPerK * thermalExpansionPerK
        / (densityKgPerM3 * specificHeatCapacityJPerKgK);
    if (!Double.isFinite(correction) || correction < 0.0) {
      throw new IllegalArgumentException("Thermal compressibility correction must be finite and non-negative");
    }
    return correction;
  }

  private static void validateInputs(double temperatureK, double densityKgPerM3, double soundSpeedMPerS,
      double thermalExpansionPerK, double specificHeatCapacityJPerKgK) {
    requirePositive(temperatureK, "Temperature");
    requirePositive(densityKgPerM3, "Density");
    requirePositive(soundSpeedMPerS, "Speed of sound");
    requireFinite(thermalExpansionPerK, "Thermal expansivity");
    requirePositive(specificHeatCapacityJPerKgK, "Specific heat capacity");
  }

  private static double[][] validateAndCopyCovariance(double[][] inputCovariance) {
    if (inputCovariance == null || inputCovariance.length != INPUT_COUNT) {
      throw new IllegalArgumentException("Acoustic input covariance must be a 5-by-5 matrix");
    }
    double[][] covariance = new double[INPUT_COUNT][INPUT_COUNT];
    for (int row = 0; row < INPUT_COUNT; row++) {
      if (inputCovariance[row] == null || inputCovariance[row].length != INPUT_COUNT) {
        throw new IllegalArgumentException("Acoustic input covariance must be a 5-by-5 matrix");
      }
      for (int column = 0; column < INPUT_COUNT; column++) {
        requireFinite(inputCovariance[row][column], "Acoustic input covariance");
        covariance[row][column] = inputCovariance[row][column];
      }
      if (covariance[row][row] < 0.0) {
        throw new IllegalArgumentException("Acoustic input covariance diagonal must be non-negative");
      }
    }

    for (int row = 0; row < INPUT_COUNT; row++) {
      for (int column = row + 1; column < INPUT_COUNT; column++) {
        double covarianceScale = Math.sqrt(covariance[row][row]) * Math.sqrt(covariance[column][column]);
        requireFinite(covarianceScale, "Acoustic input covariance scale");
        double difference = Math.abs(covariance[row][column] - covariance[column][row]);
        if (difference > COVARIANCE_SYMMETRY_TOLERANCE * covarianceScale) {
          throw new IllegalArgumentException("Acoustic input covariance must be symmetric");
        }
        if (covarianceScale == 0.0 && (covariance[row][column] != 0.0 || covariance[column][row] != 0.0)) {
          throw new IllegalArgumentException("Zero-variance acoustic inputs cannot have non-zero covariance");
        }
      }
    }

    validatePositiveSemidefinite(covariance);
    return covariance;
  }

  private static void validatePositiveSemidefinite(double[][] covariance) {
    int activeCount = 0;
    for (int index = 0; index < INPUT_COUNT; index++) {
      if (covariance[index][index] > 0.0) {
        activeCount++;
      }
    }
    if (activeCount == 0) {
      return;
    }

    double[][] correlation = new double[activeCount][activeCount];
    int correlationRow = 0;
    for (int row = 0; row < INPUT_COUNT; row++) {
      if (covariance[row][row] == 0.0) {
        continue;
      }
      int correlationColumn = 0;
      for (int column = 0; column < INPUT_COUNT; column++) {
        if (covariance[column][column] == 0.0) {
          continue;
        }
        correlation[correlationRow][correlationColumn] = covariance[row][column] / Math.sqrt(covariance[row][row])
            / Math.sqrt(covariance[column][column]);
        correlationColumn++;
      }
      correlationRow++;
    }

    double[] eigenvalues = new EigenDecomposition(new Array2DRowRealMatrix(correlation, false)).getRealEigenvalues();
    for (double eigenvalue : eigenvalues) {
      if (!Double.isFinite(eigenvalue) || eigenvalue < -CORRELATION_EIGENVALUE_TOLERANCE) {
        throw new IllegalArgumentException("Acoustic input covariance must be positive semidefinite");
      }
    }
  }

  private static void requirePositive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }

  private static void requireFinite(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(name + " must be finite");
    }
  }

  /** Immutable result of acoustic compressibility conversion and uncertainty propagation. */
  public static final class ConversionResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double isentropicCompressibilityPaInverse;
    private final double thermalCorrectionPaInverse;
    private final double isothermalCompressibilityPaInverse;
    private final double standardUncertaintyPaInverse;
    private final double[] sensitivityJacobian;

    private ConversionResult(double isentropicCompressibilityPaInverse, double thermalCorrectionPaInverse,
        double isothermalCompressibilityPaInverse, double standardUncertaintyPaInverse, double[] sensitivityJacobian) {
      this.isentropicCompressibilityPaInverse = isentropicCompressibilityPaInverse;
      this.thermalCorrectionPaInverse = thermalCorrectionPaInverse;
      this.isothermalCompressibilityPaInverse = isothermalCompressibilityPaInverse;
      this.standardUncertaintyPaInverse = standardUncertaintyPaInverse;
      this.sensitivityJacobian = sensitivityJacobian.clone();
    }

    /** @return isentropic compressibility in 1/Pa */
    public double getIsentropicCompressibilityPaInverse() {
      return isentropicCompressibilityPaInverse;
    }

    /** @return thermal correction from isentropic to isothermal compressibility in 1/Pa */
    public double getThermalCorrectionPaInverse() {
      return thermalCorrectionPaInverse;
    }

    /** @return isothermal compressibility in 1/Pa */
    public double getIsothermalCompressibilityPaInverse() {
      return isothermalCompressibilityPaInverse;
    }

    /** @return propagated absolute one-sigma uncertainty in isothermal compressibility, in 1/Pa */
    public double getStandardUncertaintyPaInverse() {
      return standardUncertaintyPaInverse;
    }

    /**
     * Return the isothermal-compressibility sensitivity to each input.
     *
     * @return defensive copy ordered by the public input-index constants
     */
    public double[] getSensitivityJacobian() {
      return sensitivityJacobian.clone();
    }
  }
}
