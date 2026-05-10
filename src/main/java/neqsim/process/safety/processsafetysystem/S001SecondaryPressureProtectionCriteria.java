package neqsim.process.safety.processsafetysystem;

import java.io.Serializable;

/**
 * NORSOK S-001 Clause 10.4.7 screening criteria for instrumented secondary pressure protection.
 *
 * <p>
 * The helper checks the documented pressure basis, annual event frequency target, leakage basis,
 * and proof-test evidence for instrumented secondary pressure protection. It is intentionally a
 * screening class; detailed SIL verification remains in the SIS classes.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class S001SecondaryPressureProtectionCriteria implements Serializable {
  private static final long serialVersionUID = 1000L;

  private double maximumEventPressureBara = Double.NaN;
  private double designPressureBara = Double.NaN;
  private double testPressureBara = Double.NaN;
  private double demandFrequencyPerYear = Double.NaN;
  private double targetFrequencyPerYear = Double.NaN;
  private Boolean reliefLeakageAssessed;
  private Boolean reliefLeakageToSafeLocation;
  private double proofTestIntervalMonths = Double.NaN;

  /**
   * Creates criteria from a normalized review item.
   *
   * @param item normalized review item
   * @return populated criteria
   */
  public static S001SecondaryPressureProtectionCriteria fromItem(
      ProcessSafetySystemReviewItem item) {
    S001SecondaryPressureProtectionCriteria criteria =
        new S001SecondaryPressureProtectionCriteria();
    if (item == null) {
      return criteria;
    }
    criteria.setMaximumEventPressureBara(item.getDouble(Double.NaN, "maximumEventPressureBara",
        "eventPressureBara", "protectedSystemMaximumPressureBara"));
    criteria.setDesignPressureBara(item.getDouble(Double.NaN, "designPressureBara",
        "protectedEquipmentDesignPressureBara"));
    criteria.setTestPressureBara(item.getDouble(Double.NaN, "testPressureBara",
        "hydrotestPressureBara", "pressureTestBara"));
    criteria.setDemandFrequencyPerYear(item.getDouble(Double.NaN, "demandFrequencyPerYear",
        "annualDemandFrequency", "eventFrequencyPerYear"));
    criteria.setTargetFrequencyPerYear(item.getDouble(Double.NaN, "targetFrequencyPerYear",
        "maximumAllowedFrequencyPerYear"));
    criteria.setReliefLeakageAssessed(item.getBooleanObject("reliefLeakageAssessed",
        "psvLeakageAssessed", "reliefValveLeakageDocumented"));
    criteria.setReliefLeakageToSafeLocation(item.getBooleanObject("reliefLeakageToSafeLocation",
        "psvLeakageToSafeLocation", "leakageRoutedSafe"));
    criteria.setProofTestIntervalMonths(item.getDouble(Double.NaN, "proofTestIntervalMonths",
        "testIntervalMonths", "proofTestMonths"));
    return criteria;
  }

  /**
   * Sets maximum event pressure.
   *
   * @param pressureBara maximum event pressure in bara
   * @return this criteria for chaining
   */
  public S001SecondaryPressureProtectionCriteria setMaximumEventPressureBara(
      double pressureBara) {
    this.maximumEventPressureBara = pressureBara;
    return this;
  }

  /**
   * Sets design pressure.
   *
   * @param pressureBara design pressure in bara
   * @return this criteria for chaining
   */
  public S001SecondaryPressureProtectionCriteria setDesignPressureBara(double pressureBara) {
    this.designPressureBara = pressureBara;
    return this;
  }

  /**
   * Sets pressure test pressure.
   *
   * @param pressureBara test pressure in bara
   * @return this criteria for chaining
   */
  public S001SecondaryPressureProtectionCriteria setTestPressureBara(double pressureBara) {
    this.testPressureBara = pressureBara;
    return this;
  }

  /**
   * Sets demand frequency.
   *
   * @param frequencyPerYear demand frequency per year
   * @return this criteria for chaining
   */
  public S001SecondaryPressureProtectionCriteria setDemandFrequencyPerYear(
      double frequencyPerYear) {
    this.demandFrequencyPerYear = frequencyPerYear;
    return this;
  }

  /**
   * Sets explicit target frequency.
   *
   * @param frequencyPerYear target frequency per year
   * @return this criteria for chaining
   */
  public S001SecondaryPressureProtectionCriteria setTargetFrequencyPerYear(
      double frequencyPerYear) {
    this.targetFrequencyPerYear = frequencyPerYear;
    return this;
  }

  /**
   * Sets relief leakage assessment status.
   *
   * @param assessed true when leakage has been assessed, null when not supplied
   * @return this criteria for chaining
   */
  public S001SecondaryPressureProtectionCriteria setReliefLeakageAssessed(Boolean assessed) {
    this.reliefLeakageAssessed = assessed;
    return this;
  }

  /**
   * Sets whether leakage is routed to a safe location.
   *
   * @param safeLocation true when leakage is routed to a safe location, null when not supplied
   * @return this criteria for chaining
   */
  public S001SecondaryPressureProtectionCriteria setReliefLeakageToSafeLocation(
      Boolean safeLocation) {
    this.reliefLeakageToSafeLocation = safeLocation;
    return this;
  }

  /**
   * Sets proof-test interval.
   *
   * @param months proof-test interval in months
   * @return this criteria for chaining
   */
  public S001SecondaryPressureProtectionCriteria setProofTestIntervalMonths(double months) {
    this.proofTestIntervalMonths = months;
    return this;
  }

  /**
   * Tests whether enough pressure data exist to run the pressure part of the check.
   *
   * @return true when event, design, and test pressure are finite and positive
   */
  public boolean hasPressureBasis() {
    return isPositive(maximumEventPressureBara) && isPositive(designPressureBara)
        && isPositive(testPressureBara);
  }

  /**
   * Tests whether no criteria fields have been supplied.
   *
   * @return true when the helper has no evidence fields
   */
  public boolean isEmpty() {
    return !Double.isFinite(maximumEventPressureBara) && !Double.isFinite(designPressureBara)
        && !Double.isFinite(testPressureBara) && !Double.isFinite(demandFrequencyPerYear)
        && reliefLeakageAssessed == null && reliefLeakageToSafeLocation == null
        && !Double.isFinite(proofTestIntervalMonths);
  }

  /**
   * Evaluates the criteria.
   *
   * @return screening result
   */
  public S001SecondaryPressureProtectionResult evaluate() {
    double resolvedTarget = Double.isFinite(targetFrequencyPerYear) ? targetFrequencyPerYear
        : getDefaultTargetFrequencyPerYear(maximumEventPressureBara, designPressureBara,
            testPressureBara);
    boolean pressureBasisComplete = hasPressureBasis();
    boolean testPressureValid = !pressureBasisComplete || testPressureBara >= designPressureBara;
    boolean pressureWithinTest = !pressureBasisComplete || maximumEventPressureBara <= testPressureBara;
    boolean frequencyConfigured = Double.isFinite(demandFrequencyPerYear)
        && demandFrequencyPerYear >= 0.0 && Double.isFinite(resolvedTarget);
    boolean frequencyMet = !frequencyConfigured || demandFrequencyPerYear <= resolvedTarget;
    boolean leakageAssessed = Boolean.TRUE.equals(reliefLeakageAssessed);
    boolean leakageSafe = Boolean.TRUE.equals(reliefLeakageToSafeLocation);
    boolean proofTestConfigured = Double.isFinite(proofTestIntervalMonths);
    boolean proofTestMet = !proofTestConfigured || proofTestIntervalMonths <= 12.0;
    boolean acceptable = pressureBasisComplete && testPressureValid && pressureWithinTest
        && frequencyConfigured && frequencyMet && leakageAssessed && leakageSafe && proofTestMet;
    return new S001SecondaryPressureProtectionResult(maximumEventPressureBara, designPressureBara,
        testPressureBara, demandFrequencyPerYear, resolvedTarget, pressureBasisComplete,
        testPressureValid, pressureWithinTest, frequencyConfigured, frequencyMet, leakageAssessed,
        leakageSafe, proofTestConfigured, proofTestMet, acceptable);
  }

  /**
   * Gets a default target frequency from pressure severity bands.
   *
   * @param eventPressureBara event pressure in bara
   * @param designPressureBara design pressure in bara
   * @param testPressureBara test pressure in bara
   * @return target annual frequency, or NaN when inputs are incomplete
   */
  public static double getDefaultTargetFrequencyPerYear(double eventPressureBara,
      double designPressureBara, double testPressureBara) {
    if (!isPositive(eventPressureBara) || !isPositive(designPressureBara)
        || !isPositive(testPressureBara)) {
      return Double.NaN;
    }
    if (eventPressureBara > 2.0 * designPressureBara) {
      return 1.0e-5;
    }
    if (eventPressureBara > testPressureBara) {
      return 1.0e-4;
    }
    if (eventPressureBara > designPressureBara) {
      return 1.0e-3;
    }
    return 1.0e-2;
  }

  /**
   * Tests whether a number is finite and positive.
   *
   * @param value value to test
   * @return true when the value is finite and positive
   */
  private static boolean isPositive(double value) {
    return Double.isFinite(value) && value > 0.0;
  }
}