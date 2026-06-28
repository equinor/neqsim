package neqsim.process.costestimation;

/**
 * AACE-style estimate classes for NeqSim cost estimates.
 *
 * <p>
 * The classes indicate estimate maturity and typical accuracy range. They are used to prevent early screening estimates
 * from being reported as detailed vendor or quantity-takeoff estimates.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public enum EstimateClass {
  /** Final estimate based mainly on vendor quotes and detailed quantities. */
  CLASS_1(1, "Final", -0.10, 0.15, "vendor-quote"),

  /** Detailed estimate based on firm quantities and major quotes. */
  CLASS_2(2, "Detailed", -0.15, 0.20, "detailed-quantity-takeoff"),

  /** Budget or FEED estimate based on sized equipment and bulk quantities. */
  CLASS_3(3, "Budget/FEED", -0.20, 0.30, "sized-equipment-and-bulk-mto"),

  /** Study estimate based on equipment-specific correlations and factors. */
  CLASS_4(4, "Study", -0.30, 0.50, "equipment-factored"),

  /** Concept screening estimate based on capacity factors or analogues. */
  CLASS_5(5, "Concept", -0.50, 1.00, "capacity-factored");

  private final int classNumber;
  private final String maturity;
  private final double lowAccuracyFraction;
  private final double highAccuracyFraction;
  private final String defaultMethod;

  /**
   * Creates an estimate class descriptor.
   *
   * @param classNumber the AACE class number from 1 to 5
   * @param maturity the maturity label
   * @param lowAccuracyFraction lower accuracy fraction, e.g. -0.30 for minus thirty percent
   * @param highAccuracyFraction upper accuracy fraction, e.g. 0.50 for plus fifty percent
   * @param defaultMethod the default estimating method normally associated with the class
   */
  EstimateClass(int classNumber, String maturity, double lowAccuracyFraction, double highAccuracyFraction,
      String defaultMethod) {
    this.classNumber = classNumber;
    this.maturity = maturity;
    this.lowAccuracyFraction = lowAccuracyFraction;
    this.highAccuracyFraction = highAccuracyFraction;
    this.defaultMethod = defaultMethod;
  }

  /**
   * Gets the AACE class number.
   *
   * @return class number from 1 to 5
   */
  public int getClassNumber() {
    return classNumber;
  }

  /**
   * Gets the maturity label.
   *
   * @return maturity label
   */
  public String getMaturity() {
    return maturity;
  }

  /**
   * Gets the lower accuracy fraction.
   *
   * @return lower accuracy fraction
   */
  public double getLowAccuracyFraction() {
    return lowAccuracyFraction;
  }

  /**
   * Gets the upper accuracy fraction.
   *
   * @return upper accuracy fraction
   */
  public double getHighAccuracyFraction() {
    return highAccuracyFraction;
  }

  /**
   * Gets the default estimating method.
   *
   * @return default method identifier
   */
  public String getDefaultMethod() {
    return defaultMethod;
  }

  /**
   * Resolves an estimate class by its numeric AACE class.
   *
   * @param classNumber the class number from 1 to 5
   * @return the matching estimate class
   * @throws IllegalArgumentException if the class number is outside 1 to 5
   */
  public static EstimateClass fromClassNumber(int classNumber) {
    for (EstimateClass estimateClass : values()) {
      if (estimateClass.getClassNumber() == classNumber) {
        return estimateClass;
      }
    }
    throw new IllegalArgumentException("Unknown estimate class: " + classNumber);
  }
}
