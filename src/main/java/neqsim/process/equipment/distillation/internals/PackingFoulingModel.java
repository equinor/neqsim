package neqsim.process.equipment.distillation.internals;

import java.io.Serializable;

/**
 * Geometric derating of a packed bed caused by solid deposition (fouling).
 *
 * <p>
 * A packed bed that accumulates a solid deposit loses void volume. Because the classical packing factor is
 * geometrically equivalent to <i>a</i>/&epsilon;<sup>3</sup> and the dry-bed pressure drop of a packed bed scales with
 * <i>F<sub>p</sub></i>&nbsp;&rho;<sub>G</sub>&nbsp;u<sup>2</sup>/ &epsilon;<sup>3</sup> at a fixed superficial gas
 * velocity, a modest loss of void fraction produces a large pressure-drop increase. This class converts a fouling
 * measure - either a uniform deposit thickness or a fractional void loss - into the derated void fraction, specific
 * surface area and packing factor that a hydraulics calculation should use, and into the resulting pressure-drop and
 * flooding-velocity ratios relative to the clean bed.
 * </p>
 *
 * <p>
 * The inverse direction is often the useful one in operations: a measured pressure-drop ratio relative to the clean or
 * commissioned bed is converted back to the implied void loss with
 * {@link #voidLossFractionForPressureDropRatio(double)}.
 * </p>
 *
 * <p>
 * <b>Assumptions.</b> The specific surface area is held at its clean value. This is a first-order treatment valid while
 * the deposit thickness is small compared with the clean hydraulic diameter
 * 4&epsilon;<sub>0</sub>/<i>a</i><sub>0</sub>; the deposit is assumed uniform over the wetted surface. The derating is
 * geometric only - it does not describe the deposition kinetics, and it does not capture channelling caused by locally
 * blocked cross-section.
 * </p>
 *
 * <p>
 * The derated values are intended to be fed to a packing hydraulics calculation (for example
 * {@code PackingHydraulicsCalculator}) through its void-fraction, specific-surface-area and packing-factor setters.
 * This class deliberately holds no reference to a calculator so that it can be used standalone for screening.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class PackingFoulingModel implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Exponent linking the void-fraction ratio to the pressure-drop ratio. */
  private static final double PRESSURE_DROP_EXPONENT = 6.0;

  /** Clean (unfouled) void fraction [-]. */
  private final double cleanVoidFraction;

  /** Clean specific surface area [m2/m3]. */
  private final double cleanSpecificSurfaceArea;

  /** Clean packing factor [1/m]. */
  private final double cleanPackingFactor;

  /** Fraction of the clean void volume filled by deposit [-]. */
  private final double voidLossFraction;

  /** Uniform deposit thickness [m], or NaN when the model was built from a void loss. */
  private final double depositThickness;

  /**
   * Create a fouling model from already-validated quantities.
   *
   * @param cleanVoidFraction clean void fraction [-], in (0, 1)
   * @param cleanSpecificSurfaceArea clean specific surface area [m2/m3], positive
   * @param cleanPackingFactor clean packing factor [1/m], positive
   * @param voidLossFraction fraction of the clean void volume filled by deposit [-], in [0, 1)
   * @param depositThickness uniform deposit thickness [m], or {@link Double#NaN} when unknown
   */
  private PackingFoulingModel(double cleanVoidFraction, double cleanSpecificSurfaceArea, double cleanPackingFactor,
      double voidLossFraction, double depositThickness) {
    this.cleanVoidFraction = cleanVoidFraction;
    this.cleanSpecificSurfaceArea = cleanSpecificSurfaceArea;
    this.cleanPackingFactor = cleanPackingFactor;
    this.voidLossFraction = voidLossFraction;
    this.depositThickness = depositThickness;
  }

  /**
   * Build a fouling model from the fraction of the clean void volume that is filled by deposit.
   *
   * @param cleanVoidFraction clean void fraction [-], must be in (0, 1)
   * @param cleanSpecificSurfaceArea clean specific surface area [m2/m3], must be positive
   * @param cleanPackingFactor clean packing factor [1/m], must be positive
   * @param voidLossFraction fraction of the clean void volume filled by deposit [-], must be in [0, 1)
   * @return the fouling model
   * @throws IllegalArgumentException if any argument is outside its valid range or not finite
   */
  public static PackingFoulingModel fromVoidLossFraction(double cleanVoidFraction, double cleanSpecificSurfaceArea,
      double cleanPackingFactor, double voidLossFraction) {
    checkFraction("cleanVoidFraction", cleanVoidFraction);
    checkPositive("cleanSpecificSurfaceArea", cleanSpecificSurfaceArea);
    checkPositive("cleanPackingFactor", cleanPackingFactor);
    if (!isFinite(voidLossFraction) || voidLossFraction < 0.0 || voidLossFraction >= 1.0) {
      throw new IllegalArgumentException("voidLossFraction must be finite and in [0, 1)");
    }
    double thickness = cleanVoidFraction * voidLossFraction / cleanSpecificSurfaceArea;
    return new PackingFoulingModel(cleanVoidFraction, cleanSpecificSurfaceArea, cleanPackingFactor, voidLossFraction,
        thickness);
  }

  /**
   * Build a fouling model from a uniform deposit thickness on the packing surface.
   *
   * <p>
   * To first order the deposit removes {@code cleanSpecificSurfaceArea * thickness} of void volume per cubic metre of
   * bed.
   * </p>
   *
   * @param cleanVoidFraction clean void fraction [-], must be in (0, 1)
   * @param cleanSpecificSurfaceArea clean specific surface area [m2/m3], must be positive
   * @param cleanPackingFactor clean packing factor [1/m], must be positive
   * @param depositThickness uniform deposit thickness [m], must be non-negative and small enough that the void fraction
   * stays positive
   * @return the fouling model
   * @throws IllegalArgumentException if any argument is outside its valid range or not finite, or if the deposit would
   * consume the whole void volume
   */
  public static PackingFoulingModel fromDepositThickness(double cleanVoidFraction, double cleanSpecificSurfaceArea,
      double cleanPackingFactor, double depositThickness) {
    checkFraction("cleanVoidFraction", cleanVoidFraction);
    checkPositive("cleanSpecificSurfaceArea", cleanSpecificSurfaceArea);
    checkPositive("cleanPackingFactor", cleanPackingFactor);
    if (!isFinite(depositThickness) || depositThickness < 0.0) {
      throw new IllegalArgumentException("depositThickness must be finite and non-negative");
    }
    double loss = cleanSpecificSurfaceArea * depositThickness / cleanVoidFraction;
    if (loss >= 1.0) {
      throw new IllegalArgumentException("depositThickness consumes the whole void volume; reduce the thickness");
    }
    return new PackingFoulingModel(cleanVoidFraction, cleanSpecificSurfaceArea, cleanPackingFactor, loss,
        depositThickness);
  }

  /**
   * Void loss implied by an observed pressure-drop ratio relative to the clean bed.
   *
   * <p>
   * Inverts {@link #getPressureDropRatio()} at a fixed superficial gas and liquid load.
   * </p>
   *
   * @param pressureDropRatio observed pressure drop divided by the clean-bed pressure drop at the same load [-], must
   * be finite and at least 1
   * @return the implied fraction of the clean void volume filled by deposit [-]
   * @throws IllegalArgumentException if the ratio is not finite or is below 1
   */
  public static double voidLossFractionForPressureDropRatio(double pressureDropRatio) {
    if (!isFinite(pressureDropRatio) || pressureDropRatio < 1.0) {
      throw new IllegalArgumentException("pressureDropRatio must be finite and at least 1");
    }
    return 1.0 - Math.pow(pressureDropRatio, -1.0 / PRESSURE_DROP_EXPONENT);
  }

  /**
   * Get the clean void fraction.
   *
   * @return clean void fraction [-]
   */
  public double getCleanVoidFraction() {
    return cleanVoidFraction;
  }

  /**
   * Get the clean specific surface area.
   *
   * @return clean specific surface area [m2/m3]
   */
  public double getCleanSpecificSurfaceArea() {
    return cleanSpecificSurfaceArea;
  }

  /**
   * Get the clean packing factor.
   *
   * @return clean packing factor [1/m]
   */
  public double getCleanPackingFactor() {
    return cleanPackingFactor;
  }

  /**
   * Get the fraction of the clean void volume that is filled by deposit.
   *
   * @return void loss fraction [-]
   */
  public double getVoidLossFraction() {
    return voidLossFraction;
  }

  /**
   * Get the uniform deposit thickness that corresponds to the void loss.
   *
   * @return deposit thickness [m]
   */
  public double getDepositThickness() {
    return depositThickness;
  }

  /**
   * Get the derated void fraction of the fouled bed.
   *
   * @return fouled void fraction [-]
   */
  public double getFouledVoidFraction() {
    return cleanVoidFraction * (1.0 - voidLossFraction);
  }

  /**
   * Get the specific surface area used for the fouled bed.
   *
   * <p>
   * Held at the clean value; see the class-level assumptions.
   * </p>
   *
   * @return fouled specific surface area [m2/m3]
   */
  public double getFouledSpecificSurfaceArea() {
    return cleanSpecificSurfaceArea;
  }

  /**
   * Get the derated packing factor of the fouled bed.
   *
   * <p>
   * Scaled from the clean value with the geometric identity
   * <i>F<sub>p</sub></i>&nbsp;&#8733;&nbsp;<i>a</i>/&epsilon;<sup>3</sup>.
   * </p>
   *
   * @return fouled packing factor [1/m]
   */
  public double getFouledPackingFactor() {
    double ratio = cleanVoidFraction / getFouledVoidFraction();
    return cleanPackingFactor * ratio * ratio * ratio;
  }

  /**
   * Get the clean hydraulic diameter of the bed.
   *
   * @return clean hydraulic diameter [m]
   */
  public double getCleanHydraulicDiameter() {
    return 4.0 * cleanVoidFraction / cleanSpecificSurfaceArea;
  }

  /**
   * Get the hydraulic diameter of the fouled bed.
   *
   * @return fouled hydraulic diameter [m]
   */
  public double getFouledHydraulicDiameter() {
    return 4.0 * getFouledVoidFraction() / getFouledSpecificSurfaceArea();
  }

  /**
   * Get the pressure-drop multiplier of the fouled bed relative to the clean bed.
   *
   * <p>
   * Evaluated at the same superficial gas and liquid load.
   * </p>
   *
   * @return pressure drop divided by the clean-bed pressure drop [-]
   */
  public double getPressureDropRatio() {
    return Math.pow(cleanVoidFraction / getFouledVoidFraction(), PRESSURE_DROP_EXPONENT);
  }

  /**
   * Get the flooding-velocity multiplier of the fouled bed relative to the clean bed.
   *
   * @return flooding velocity divided by the clean-bed flooding velocity [-]
   */
  public double getFloodingVelocityRatio() {
    return Math.sqrt(cleanPackingFactor / getFouledPackingFactor());
  }

  /**
   * Check that a value is a finite fraction strictly inside (0, 1).
   *
   * @param name argument name used in the exception message
   * @param value value to check [-]
   * @throws IllegalArgumentException if the value is not finite or not inside (0, 1)
   */
  private static void checkFraction(String name, double value) {
    if (!isFinite(value) || value <= 0.0 || value >= 1.0) {
      throw new IllegalArgumentException(name + " must be finite and in (0, 1)");
    }
  }

  /**
   * Check that a value is finite and strictly positive.
   *
   * @param name argument name used in the exception message
   * @param value value to check
   * @throws IllegalArgumentException if the value is not finite or not positive
   */
  private static void checkPositive(String name, double value) {
    if (!isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }

  /**
   * Java 8 compatible finiteness test.
   *
   * @param value value to check
   * @return true when the value is neither NaN nor infinite
   */
  private static boolean isFinite(double value) {
    return !Double.isNaN(value) && !Double.isInfinite(value);
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "PackingFoulingModel[voidLoss=" + voidLossFraction + ", fouledVoid=" + getFouledVoidFraction() + ", dPratio="
        + getPressureDropRatio() + "]";
  }
}
