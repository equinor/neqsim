package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Immutable mass-basis kinematic-viscosity screening result for a refinery blend.
 *
 * <p>
 * The empirical Refutas relation transforms each source kinematic viscosity to a viscosity blending number, mixes those
 * numbers by normalized mass fraction, and applies the analytical inverse. Every contributing viscosity must be
 * resolved at the same caller-supplied temperature. This class does not mutate an assay or thermodynamic system.
 * </p>
 */
public final class RefineryViscosityBlend implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double MINIMUM_KINEMATIC_VISCOSITY_CST = 0.2;
  private static final double REFUTAS_SCALE = 14.534;
  private static final double REFUTAS_OFFSET = 10.975;
  private static final double REFUTAS_VISCOSITY_OFFSET_CST = 0.8;

  private final double[] massFractions;
  private final double[] sourceViscosityBlendingNumbers;
  private final double temperatureCelsius;
  private final double viscosityBlendingNumber;
  private final double kinematicViscosityCSt;

  private RefineryViscosityBlend(double[] sourceMasses, double[] sourceKinematicViscositiesCSt,
      double temperatureCelsius) {
    validateInputs(sourceMasses, sourceKinematicViscositiesCSt, temperatureCelsius);

    double totalMass = 0.0;
    for (double sourceMass : sourceMasses) {
      if (!Double.isFinite(sourceMass) || sourceMass < 0.0) {
        throw new IllegalArgumentException("Source masses must be finite and non-negative");
      }
      totalMass += sourceMass;
      if (!Double.isFinite(totalMass)) {
        throw new IllegalArgumentException("Total source mass must be finite");
      }
    }
    if (!(totalMass > 0.0)) {
      throw new IllegalArgumentException("Blend must contain positive source mass");
    }

    double[] resolvedMassFractions = new double[sourceMasses.length];
    double[] resolvedSourceBlendingNumbers = new double[sourceMasses.length];
    Arrays.fill(resolvedSourceBlendingNumbers, Double.NaN);
    double resolvedBlendNumber = 0.0;
    double minimumSourceBlendNumber = Double.POSITIVE_INFINITY;
    double maximumSourceBlendNumber = Double.NEGATIVE_INFINITY;

    for (int i = 0; i < sourceMasses.length; i++) {
      double massFraction = sourceMasses[i] / totalMass;
      resolvedMassFractions[i] = massFraction;
      if (!(massFraction > 0.0)) {
        continue;
      }

      double sourceBlendNumber = calculateViscosityBlendingNumber(sourceKinematicViscositiesCSt[i]);
      resolvedSourceBlendingNumbers[i] = sourceBlendNumber;
      resolvedBlendNumber += massFraction * sourceBlendNumber;
      minimumSourceBlendNumber = Math.min(minimumSourceBlendNumber, sourceBlendNumber);
      maximumSourceBlendNumber = Math.max(maximumSourceBlendNumber, sourceBlendNumber);
    }

    if (!Double.isFinite(resolvedBlendNumber) || resolvedBlendNumber < minimumSourceBlendNumber
        || resolvedBlendNumber > maximumSourceBlendNumber) {
      throw new IllegalArgumentException("Blend viscosity number must be finite and bounded");
    }

    double resolvedKinematicViscosity = calculateKinematicViscosityCSt(resolvedBlendNumber);
    massFractions = resolvedMassFractions;
    sourceViscosityBlendingNumbers = resolvedSourceBlendingNumbers;
    this.temperatureCelsius = temperatureCelsius;
    viscosityBlendingNumber = resolvedBlendNumber;
    kinematicViscosityCSt = resolvedKinematicViscosity;
  }

  /**
   * Create an empirical mass-basis viscosity blend at one explicit common temperature.
   *
   * @param sourceMasses non-negative source masses in any common mass unit
   * @param sourceKinematicViscositiesCSt source kinematic viscosities in cSt at the common temperature
   * @param temperatureCelsius common source-viscosity temperature in degrees Celsius
   * @return immutable viscosity blend result
   * @throws IllegalArgumentException for invalid arrays, masses, temperature, or contributing viscosities
   */
  public static RefineryViscosityBlend fromMassBasis(double[] sourceMasses, double[] sourceKinematicViscositiesCSt,
      double temperatureCelsius) {
    return new RefineryViscosityBlend(sourceMasses, sourceKinematicViscositiesCSt, temperatureCelsius);
  }

  /**
   * Transform kinematic viscosity to the published Refutas viscosity blending number.
   *
   * @param kinematicViscosityCSt finite kinematic viscosity greater than 0.2 cSt
   * @return dimensionless viscosity blending number
   * @throws IllegalArgumentException if the viscosity is outside the mathematical domain
   */
  public static double calculateViscosityBlendingNumber(double kinematicViscosityCSt) {
    if (!Double.isFinite(kinematicViscosityCSt) || !(kinematicViscosityCSt > MINIMUM_KINEMATIC_VISCOSITY_CST)) {
      throw new IllegalArgumentException("Kinematic viscosity must be finite and greater than 0.2 cSt");
    }
    double blendingNumber = REFUTAS_SCALE * Math.log(Math.log(kinematicViscosityCSt + REFUTAS_VISCOSITY_OFFSET_CST))
        + REFUTAS_OFFSET;
    if (!Double.isFinite(blendingNumber)) {
      throw new IllegalArgumentException("Viscosity blending number must be finite");
    }
    return blendingNumber;
  }

  /**
   * Apply the analytical inverse Refutas relation.
   *
   * @param viscosityBlendingNumber finite dimensionless viscosity blending number
   * @return kinematic viscosity in cSt
   * @throws IllegalArgumentException if the input or calculated viscosity is non-finite
   */
  public static double calculateKinematicViscosityCSt(double viscosityBlendingNumber) {
    if (!Double.isFinite(viscosityBlendingNumber)) {
      throw new IllegalArgumentException("Viscosity blending number must be finite");
    }
    double kinematicViscosity = Math.exp(Math.exp((viscosityBlendingNumber - REFUTAS_OFFSET) / REFUTAS_SCALE))
        - REFUTAS_VISCOSITY_OFFSET_CST;
    if (!Double.isFinite(kinematicViscosity) || !(kinematicViscosity > MINIMUM_KINEMATIC_VISCOSITY_CST)) {
      throw new IllegalArgumentException("Calculated kinematic viscosity must be finite and greater than 0.2 cSt");
    }
    return kinematicViscosity;
  }

  /** @return defensive copy of normalized source mass fractions */
  public double[] getMassFractions() {
    return Arrays.copyOf(massFractions, massFractions.length);
  }

  /**
   * Return source viscosity blending numbers in source order.
   *
   * <p>
   * A zero-mass source is represented by {@link Double#NaN} because its viscosity is deliberately not resolved.
   * </p>
   *
   * @return defensive copy of source viscosity blending numbers
   */
  public double[] getSourceViscosityBlendingNumbers() {
    return Arrays.copyOf(sourceViscosityBlendingNumbers, sourceViscosityBlendingNumbers.length);
  }

  /** @return common source-viscosity temperature in degrees Celsius */
  public double getTemperatureCelsius() {
    return temperatureCelsius;
  }

  /** @return mass-weighted dimensionless viscosity blending number */
  public double getViscosityBlendingNumber() {
    return viscosityBlendingNumber;
  }

  /** @return estimated blend kinematic viscosity in cSt at the common temperature */
  public double getKinematicViscosityCSt() {
    return kinematicViscosityCSt;
  }

  private static void validateInputs(double[] sourceMasses, double[] sourceKinematicViscositiesCSt,
      double temperatureCelsius) {
    if (sourceMasses == null || sourceKinematicViscositiesCSt == null || sourceMasses.length == 0
        || sourceMasses.length != sourceKinematicViscositiesCSt.length) {
      throw new IllegalArgumentException(
          "Source mass and kinematic-viscosity arrays must be non-empty and equal length");
    }
    if (!Double.isFinite(temperatureCelsius)) {
      throw new IllegalArgumentException("Common viscosity temperature must be finite");
    }
  }
}
