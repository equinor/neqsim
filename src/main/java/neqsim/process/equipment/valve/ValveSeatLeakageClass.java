package neqsim.process.equipment.valve;

/**
 * Qualitative and, where the underlying standard defines a closed-form expression, quantitative valve seat-leakage
 * tightness classification.
 *
 * <p>
 * Covers the EN 12266-1 leakage rates A through G (used widely for on/off and isolation valves, e.g. ESDV/HIPPS block
 * valves) and the ANSI/FCI 70-2 control-valve seat-leakage classes II through VI (used for throttling control valves).
 * The enum constants carry only a qualitative description and an "essentially zero leakage" flag; the exact numeric
 * acceptance criteria for EN 12266-1 vary with valve size and test medium and must be taken from the standard itself.
 * </p>
 *
 * <p>
 * For ANSI/FCI 70-2 Class V, which defines a closed-form liquid leak-rate relation, this class also provides
 * {@link #estimateFciClassVLeakRate(double, double)} as an illustrative screening helper.
 * </p>
 *
 * <p>
 * This classification is intended for engineering screening (e.g. ranking candidate valve tightness requirements during
 * a safety study) and is not a substitute for a manufacturer seat leakage test certificate.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public enum ValveSeatLeakageClass {

  /** EN 12266-1 Rate A: least stringent metal-seated leakage rate. */
  EN_12266_RATE_A("EN 12266-1 Rate A",
      "Least stringent EN 12266-1 metal-seat leakage rate; a measurable leakage is permitted. "
          + "Refer to EN 12266-1 for the quantitative test pressure and allowable leakage for the specific valve size.",
      false),

  /** EN 12266-1 Rate B. */
  EN_12266_RATE_B("EN 12266-1 Rate B",
      "EN 12266-1 metal-seat leakage rate, more stringent than Rate A. "
          + "Refer to EN 12266-1 for the quantitative test pressure and allowable leakage for the specific valve size.",
      false),

  /** EN 12266-1 Rate C. */
  EN_12266_RATE_C("EN 12266-1 Rate C",
      "EN 12266-1 metal-seat leakage rate, more stringent than Rate B; a common baseline requirement "
          + "for general isolation duty. Refer to EN 12266-1 for the quantitative table.",
      false),

  /** EN 12266-1 Rate D. */
  EN_12266_RATE_D("EN 12266-1 Rate D",
      "EN 12266-1 leakage rate commonly specified for soft-seated isolation valves in general process duty. "
          + "Refer to EN 12266-1 for the quantitative test pressure and allowable leakage for the specific valve size.",
      false),

  /** EN 12266-1 Rate E. */
  EN_12266_RATE_E("EN 12266-1 Rate E",
      "EN 12266-1 leakage rate, more stringent than Rate D; typically specified for soft-seated valves in "
          + "critical isolation duty. Refer to EN 12266-1 for the quantitative table.",
      false),

  /** EN 12266-1 Rate F. */
  EN_12266_RATE_F("EN 12266-1 Rate F",
      "EN 12266-1 leakage rate, more stringent than Rate E; typically specified for safety-critical "
          + "isolation valves (e.g. ESDV, HIPPS) where minimal leakage is required. Refer to EN 12266-1 "
          + "for the quantitative table.",
      false),

  /** EN 12266-1 Rate G: most stringent, essentially bubble-tight metal seat. */
  EN_12266_RATE_G("EN 12266-1 Rate G",
      "Most stringent EN 12266-1 metal-seated leakage rate; commonly specified for severe-service or "
          + "fire-safe metal-seated valves where the allowable leakage is the smallest non-zero rate "
          + "defined by the standard for the tested valve size. Refer to EN 12266-1 for the quantitative table.",
      false),

  /** ANSI/FCI 70-2 Class II: 0.5% of rated capacity. */
  FCI_70_2_CLASS_II("ANSI/FCI 70-2 Class II",
      "Control valve seat leakage not to exceed 0.5% of the valve rated capacity at full travel, "
          + "measured with the standard test fluid and differential pressure per ANSI/FCI 70-2.",
      false),

  /** ANSI/FCI 70-2 Class III: 0.1% of rated capacity. */
  FCI_70_2_CLASS_III("ANSI/FCI 70-2 Class III",
      "Control valve seat leakage not to exceed 0.1% of the valve rated capacity at full travel per "
          + "ANSI/FCI 70-2.",
      false),

  /** ANSI/FCI 70-2 Class IV: 0.01% of rated capacity (typical default for control valves). */
  FCI_70_2_CLASS_IV("ANSI/FCI 70-2 Class IV",
      "Control valve seat leakage not to exceed 0.01% of the valve rated capacity at full travel per "
          + "ANSI/FCI 70-2; the typical default requirement for general control-valve duty.",
      false),

  /** ANSI/FCI 70-2 Class V: leak rate scales with port diameter and differential pressure. */
  FCI_70_2_CLASS_V("ANSI/FCI 70-2 Class V",
      "Control valve metal-seat leakage expressed as a measured liquid leak rate that scales with port "
          + "diameter and test differential pressure per ANSI/FCI 70-2; see "
          + "estimateFciClassVLeakRate(double, double) for the illustrative screening formula.",
      false),

  /** ANSI/FCI 70-2 Class VI: resilient (soft) seat, essentially zero liquid leakage. */
  FCI_70_2_CLASS_VI("ANSI/FCI 70-2 Class VI",
      "Control valve resilient (soft) seat leakage expressed as an allowable air-bubble rate per minute "
          + "that depends on the nominal port size per ANSI/FCI 70-2 Table 2; essentially zero liquid "
          + "leakage for screening purposes.",
      true);

  /** Pa per psi, used by the ANSI/FCI 70-2 Class V leak-rate correlation. */
  private static final double PA_PER_PSI = 6894.757293168;

  /** Meters per inch, used by the ANSI/FCI 70-2 Class V leak-rate correlation. */
  private static final double M_PER_INCH = 0.0254;

  /**
   * ANSI/FCI 70-2 Class V leak-rate coefficient: 5e-4 mL/min of water per inch of port diameter per psi of differential
   * pressure.
   */
  private static final double CLASS_V_LEAKAGE_COEFFICIENT_ML_PER_MIN_PER_INCH_PER_PSI = 5.0e-4;

  /** Human-readable standard and rate/class designation. */
  private final String standardDesignation;

  /** Qualitative description of the leakage rate. */
  private final String description;

  /** Whether the rate/class is considered essentially zero leakage for screening purposes. */
  private final boolean essentiallyZeroLeakage;

  /**
   * Constructs a ValveSeatLeakageClass constant.
   *
   * @param standardDesignation human-readable standard and rate/class designation
   * @param description qualitative description of the leakage rate
   * @param essentiallyZeroLeakage whether the rate/class is considered essentially zero leakage for screening purposes
   */
  ValveSeatLeakageClass(String standardDesignation, String description, boolean essentiallyZeroLeakage) {
    this.standardDesignation = standardDesignation;
    this.description = description;
    this.essentiallyZeroLeakage = essentiallyZeroLeakage;
  }

  /**
   * Gets the human-readable standard and rate/class designation.
   *
   * @return the standard designation, e.g. "ANSI/FCI 70-2 Class IV"
   */
  public String getStandardDesignation() {
    return standardDesignation;
  }

  /**
   * Gets a qualitative description of this leakage rate/class.
   *
   * @return the description text
   */
  public String getDescription() {
    return description;
  }

  /**
   * Indicates whether this rate/class is considered essentially zero leakage for screening purposes.
   *
   * @return {@code true} if leakage is considered essentially zero, {@code false} otherwise
   */
  public boolean isEssentiallyZeroLeakage() {
    return essentiallyZeroLeakage;
  }

  /**
   * Estimates the illustrative ANSI/FCI 70-2 Class V liquid seat-leakage rate for a given differential pressure and
   * port diameter.
   *
   * <p>
   * Implements the standard correlation {@code Q [mL/min] = 5e-4 * d [inch] * dP [psi]}, where the test medium is
   * water. This formula is only valid near the valve's rated test differential pressure and should not be extrapolated
   * to extreme overpressure scenarios without engineering judgement; it is intended for screening and comparison
   * purposes only, not as a certified leakage guarantee.
   * </p>
   *
   * @param diffPressurePa the differential pressure across the closed valve seat, in Pa, must be positive
   * @param portDiameterM the valve port (seat) diameter, in meters, must be positive
   * @return the estimated Class V liquid leak rate, in mL/min
   * @throws IllegalArgumentException if {@code diffPressurePa} or {@code portDiameterM} is not positive
   */
  public static double estimateFciClassVLeakRate(double diffPressurePa, double portDiameterM) {
    if (diffPressurePa <= 0.0) {
      throw new IllegalArgumentException("diffPressurePa must be positive");
    }
    if (portDiameterM <= 0.0) {
      throw new IllegalArgumentException("portDiameterM must be positive");
    }

    double diffPressurePsi = diffPressurePa / PA_PER_PSI;
    double portDiameterInch = portDiameterM / M_PER_INCH;
    return CLASS_V_LEAKAGE_COEFFICIENT_ML_PER_MIN_PER_INCH_PER_PSI * portDiameterInch * diffPressurePsi;
  }
}
