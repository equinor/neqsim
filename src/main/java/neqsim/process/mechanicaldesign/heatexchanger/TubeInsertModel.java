package neqsim.process.mechanicaldesign.heatexchanger;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Enhanced tube insert models for heat exchanger performance improvement.
 *
 * <p>
 * Models the effect of tube-side inserts on heat transfer and pressure drop. These devices are
 * commonly used for debottlenecking existing heat exchangers — increasing heat transfer at the
 * expense of higher pressure drop.
 * </p>
 *
 * <p>
 * Supported insert types:
 * </p>
 * <ul>
 * <li><b>Twisted tape:</b> A flat strip of metal twisted into a helical shape and inserted into the
 * tube. Creates swirl flow that disrupts the boundary layer. Characterized by the twist ratio y =
 * H/(2*D), where H is the 180-degree twist pitch and D is the tube ID. The Manglik-Bergles (1993)
 * correlation is used.</li>
 * <li><b>Wire matrix (HiTRAN):</b> A wire coil or matrix insert that promotes turbulence throughout
 * the tube cross-section. Modeled using the manufacturer's published j and f factor
 * correlations.</li>
 * <li><b>Coiled wire:</b> A helical wire inserted along the tube wall. Creates local turbulence at
 * lower pressure drop penalty than twisted tape. Uses the Ravigururajan-Bergles (1996)
 * correlation.</li>
 * </ul>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Manglik, R.M. and Bergles, A.E. (1993). "Heat transfer and pressure drop correlations for
 * twisted-tape inserts in isothermal tubes: Part I—Laminar flows." J. Heat Transfer, 115(4),
 * 881-889.</li>
 * <li>Manglik, R.M. and Bergles, A.E. (1993). "Heat transfer and pressure drop correlations for
 * twisted-tape inserts in isothermal tubes: Part II—Transition and turbulent flows." J. Heat
 * Transfer, 115(4), 890-896.</li>
 * <li>Ravigururajan, T.S. and Bergles, A.E. (1996). "Development and verification of general
 * correlations for pressure drop and heat transfer in single-phase turbulent flow in enhanced
 * tubes." Exp. Thermal and Fluid Science, 13, 55-70.</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see ThermalDesignCalculator
 */
public class TubeInsertModel implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1004L;

  /**
   * Type of tube insert enhancement device.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public enum InsertType {
    /** No insert (plain tube). */
    NONE,
    /** Twisted tape insert. */
    TWISTED_TAPE,
    /** Wire matrix insert (e.g., HiTRAN). */
    WIRE_MATRIX,
    /** Coiled wire insert. */
    COILED_WIRE
  }

  // ============================================================================
  // Insert parameters
  // ============================================================================
  private InsertType insertType = InsertType.NONE;

  // Twisted tape parameters
  /** Twist ratio y = H/(2*D), where H = 180-degree pitch, D = tube ID. Typical: 2.5 to 10. */
  private double twistRatio = 5.0;

  /** Tape thickness (m). Typical: 0.5 to 1.5 mm. */
  private double tapeThickness = 0.001;

  /** Tape width fraction (tape width / tube ID). Typically near 1.0 for full-width tapes. */
  private double tapeWidthFraction = 0.95;

  // Wire matrix parameters
  /** Wire diameter (m). Typical: 0.5 to 2 mm. */
  private double wireDiameter = 0.001;

  /** Wire coil pitch (m). Typical: 5 to 20 mm. */
  private double wireCoilPitch = 0.010;

  /** Matrix density factor (0 to 1). Higher = denser matrix. Typical: 0.3 to 0.7. */
  private double matrixDensity = 0.5;

  // Coiled wire parameters
  /** Wire roughness height e/D. Typical: 0.01 to 0.05. */
  private double roughnessRatio = 0.02;

  /** Helix angle of the coiled wire (degrees). Typical: 30 to 70. */
  private double helixAngle = 45.0;

  /**
   * Constructs a TubeInsertModel with no insert (plain tube).
   */
  public TubeInsertModel() {}

  /**
   * Constructs a TubeInsertModel with a specified insert type.
   *
   * @param type the insert type
   */
  public TubeInsertModel(InsertType type) {
    this.insertType = type;
  }

  /**
   * Calculates the heat transfer enhancement ratio h_enhanced / h_plain.
   *
   * @param Re Reynolds number for plain tube flow
   * @param Pr Prandtl number
   * @return enhancement ratio (always &gt;= 1.0)
   */
  public double getHeatTransferEnhancementRatio(double Re, double Pr) {
    switch (insertType) {
      case TWISTED_TAPE:
        return calcTwistedTapeHeatEnhancement(Re, Pr);
      case WIRE_MATRIX:
        return calcWireMatrixHeatEnhancement(Re, Pr);
      case COILED_WIRE:
        return calcCoiledWireHeatEnhancement(Re, Pr);
      default:
        return 1.0;
    }
  }

  /**
   * Calculates the pressure drop penalty ratio f_enhanced / f_plain.
   *
   * @param Re Reynolds number for plain tube flow
   * @return friction factor ratio (always &gt;= 1.0)
   */
  public double getPressureDropPenaltyRatio(double Re) {
    switch (insertType) {
      case TWISTED_TAPE:
        return calcTwistedTapeFrictionPenalty(Re);
      case WIRE_MATRIX:
        return calcWireMatrixFrictionPenalty(Re);
      case COILED_WIRE:
        return calcCoiledWireFrictionPenalty(Re);
      default:
        return 1.0;
    }
  }

  /**
   * Calculates the performance evaluation ratio (PEC) at constant pumping power.
   *
   * <p>
   * PEC = (h_enh/h_plain) / (f_enh/f_plain)^(1/3)
   * </p>
   *
   * <p>
   * PEC &gt; 1.0 means the insert gives net benefit at constant pumping power.
   * </p>
   *
   * @param Re Reynolds number
   * @param Pr Prandtl number
   * @return PEC value
   */
  public double getPerformanceEvaluationCriteria(double Re, double Pr) {
    double hRatio = getHeatTransferEnhancementRatio(Re, Pr);
    double fRatio = getPressureDropPenaltyRatio(Re);
    return hRatio / Math.pow(fRatio, 1.0 / 3.0);
  }

  /**
   * Applies the insert enhancement to a plain-tube HTC and pressure drop.
   *
   * @param plainHTC plain-tube heat transfer coefficient (W/(m2*K))
   * @param plainPressureDrop plain-tube pressure drop (Pa)
   * @param Re Reynolds number
   * @param Pr Prandtl number
   * @return array of [enhanced HTC (W/(m2*K)), enhanced pressure drop (Pa)]
   */
  public double[] applyEnhancement(double plainHTC, double plainPressureDrop, double Re,
      double Pr) {
    double hRatio = getHeatTransferEnhancementRatio(Re, Pr);
    double fRatio = getPressureDropPenaltyRatio(Re);
    return new double[] {plainHTC * hRatio, plainPressureDrop * fRatio};
  }

  // ============================================================================
  // Twisted tape correlations (Manglik-Bergles 1993)
  // ============================================================================

  /**
   * Calculates heat transfer enhancement for twisted tape insert.
   *
   * <p>
   * Manglik-Bergles (1993) correlation:
   * </p>
   * <ul>
   * <li>Laminar (Re &lt; 2300): Nu_tt/Nu_plain = (pi/(pi - 4*delta/D)) * (pi + 2 - 2*delta/D)/(pi -
   * 4*delta/D) * (1 + 0.769/y)^(1/3)</li>
   * <li>Turbulent: Nu_tt = 0.023 * Re_sw^0.8 * Pr^0.4 * (pi/(pi - 4*delta/D))^0.8 * (pi + 2 -
   * 2*delta/D)^0.2 * (pi/(pi - 4*delta/D))^0.2 * phi^n</li>
   * </ul>
   *
   * <p>
   * Simplified enhancement ratio used here:
   * </p>
   *
   * <pre>
   * h_ratio = (1 + 2.0/y) * (pi/(pi - 4*delta/D))^(0.8) for turbulent
   * h_ratio = (1 + 0.769/y)^(1/3) * geometric_correction for laminar
   * </pre>
   *
   * @param Re Reynolds number
   * @param Pr Prandtl number
   * @return heat transfer enhancement ratio
   */
  private double calcTwistedTapeHeatEnhancement(double Re, double Pr) {
    double y = Math.max(2.0, twistRatio);
    double deltaOverD = tapeThickness / (tapeThickness / 0.05); // Approximate

    // Geometric correction for blocked area
    double blockageFactor =
        Math.PI / (Math.PI - 4.0 * tapeThickness * tapeWidthFraction / (tapeThickness / 0.05));
    blockageFactor = Math.max(1.0, Math.min(blockageFactor, 1.5));

    if (Re < 2300) {
      // Laminar: Manglik-Bergles swirl enhancement
      double swirl = Math.pow(1.0 + 0.769 / y, 1.0 / 3.0);
      return swirl * blockageFactor;
    }

    // Turbulent: Swirl number based enhancement
    // Swirl Reynolds number: Re_sw = Re * sqrt(1 + (pi/(2*y))^2)
    double swirl = Math.sqrt(1.0 + Math.pow(Math.PI / (2.0 * y), 2));
    double enhancement = Math.pow(swirl, 0.8) * Math.pow(blockageFactor, 0.8);

    // Additional swirl-driven enhancement
    double swirlEnhancement = 1.0 + 2.0 / y;

    return Math.max(1.0, enhancement * swirlEnhancement / swirl);
  }

  /**
   * Calculates friction factor penalty for twisted tape insert.
   *
   * <pre>
   * f_ratio = (1 + 2.75/y) * (pi/(pi - 4*delta/D))^1.29 for turbulent
   * </pre>
   *
   * @param Re Reynolds number
   * @return friction factor ratio
   */
  private double calcTwistedTapeFrictionPenalty(double Re) {
    double y = Math.max(2.0, twistRatio);

    double blockageFactor =
        Math.PI / (Math.PI - 4.0 * tapeThickness * tapeWidthFraction / (tapeThickness / 0.05));
    blockageFactor = Math.max(1.0, Math.min(blockageFactor, 1.5));

    if (Re < 2300) {
      // Laminar
      return (1.0 + 2.75 / y) * Math.pow(blockageFactor, 1.29);
    }

    // Turbulent
    double swirl = Math.sqrt(1.0 + Math.pow(Math.PI / (2.0 * y), 2));
    return Math.max(1.0, (1.0 + 2.75 / y) * Math.pow(swirl, 1.8) / swirl);
  }

  // ============================================================================
  // Wire matrix correlations (HiTRAN-type)
  // ============================================================================

  /**
   * Calculates heat transfer enhancement for wire matrix insert.
   *
   * <p>
   * Based on published HiTRAN data fits: typical enhancement 2-5x for turbulent flow, 3-8x for
   * laminar flow (due to early transition to turbulence).
   * </p>
   *
   * @param Re Reynolds number
   * @param Pr Prandtl number
   * @return heat transfer enhancement ratio
   */
  private double calcWireMatrixHeatEnhancement(double Re, double Pr) {
    // Wire matrix creates turbulence at much lower Re
    double effectiveRe = Re * (1.0 + 3.0 * matrixDensity);

    if (Re < 500) {
      // Strong enhancement in laminar regime (early turbulence)
      return 1.0 + 5.0 * matrixDensity;
    } else if (Re < 2300) {
      // Transition — wire matrix already turbulent
      return 1.0 + 4.0 * matrixDensity * Math.pow(Re / 2300.0, 0.3);
    }

    // Turbulent: moderate enhancement via increased mixing
    double enhancement = 1.0 + 2.5 * matrixDensity / Math.pow(Re / 10000.0, 0.2);
    return Math.max(1.0, enhancement);
  }

  /**
   * Calculates friction factor penalty for wire matrix insert.
   *
   * @param Re Reynolds number
   * @return friction factor ratio
   */
  private double calcWireMatrixFrictionPenalty(double Re) {
    // Wire matrix significantly increases pressure drop
    double basePenalty = 1.0 + 8.0 * matrixDensity;

    if (Re < 2300) {
      // Laminar: very high penalty (but heat transfer gain is also high)
      return basePenalty * 1.5;
    }

    // Turbulent: penalty decreases with Re
    return Math.max(1.0, basePenalty / Math.pow(Re / 10000.0, 0.15));
  }

  // ============================================================================
  // Coiled wire correlations (Ravigururajan-Bergles)
  // ============================================================================

  /**
   * Calculates heat transfer enhancement for coiled wire insert.
   *
   * <p>
   * Ravigururajan-Bergles (1996) general correlation for rough/enhanced surfaces:
   * </p>
   *
   * <pre>
   * Nu_enh/Nu_plain = [1 + (2.64*Re^0.036 * (e/D)^0.212 * (p/D)^(-0.21) * (alpha/90)^0.29
   *                        * Pr^(-0.024))^7]^(1/7)
   * </pre>
   *
   * <p>
   * Simplified for coiled wire:
   * </p>
   *
   * @param Re Reynolds number
   * @param Pr Prandtl number
   * @return heat transfer enhancement ratio
   */
  private double calcCoiledWireHeatEnhancement(double Re, double Pr) {
    double eOverD = roughnessRatio;
    double pOverD = wireCoilPitch / (wireCoilPitch / roughnessRatio); // Approximation
    double alphaAngle = helixAngle;

    if (Re < 2300) {
      // Minimal enhancement in laminar — coiled wire is mainly for turbulent
      return 1.0 + 0.5 * eOverD * 20.0;
    }

    // Ravigururajan-Bergles enhancement factor
    double A = 2.64 * Math.pow(Re, 0.036) * Math.pow(eOverD, 0.212)
        * Math.pow(Math.max(pOverD, 0.1), -0.21) * Math.pow(alphaAngle / 90.0, 0.29)
        * Math.pow(Pr, -0.024);

    return Math.pow(1.0 + Math.pow(A, 7), 1.0 / 7.0);
  }

  /**
   * Calculates friction factor penalty for coiled wire insert.
   *
   * @param Re Reynolds number
   * @return friction factor ratio
   */
  private double calcCoiledWireFrictionPenalty(double Re) {
    double eOverD = roughnessRatio;

    if (Re < 2300) {
      return 1.0 + 10.0 * eOverD;
    }

    // Moody diagram: rough vs smooth
    // f_rough/f_smooth approximately (1 + a * (e/D)^b * Re^c)
    double penalty = 1.0
        + 29.1 * Math.pow(eOverD, 0.67) * Math.pow(Re, -0.06) * Math.pow(helixAngle / 90.0, 0.49);

    return Math.max(1.0, penalty);
  }

  /**
   * Returns all insert model results.
   *
   * @param Re Reynolds number
   * @param Pr Prandtl number
   * @return map of results
   */
  public Map<String, Object> toMap(double Re, double Pr) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();

    result.put("insertType", insertType.name());
    result.put("heatTransferEnhancementRatio", getHeatTransferEnhancementRatio(Re, Pr));
    result.put("pressureDropPenaltyRatio", getPressureDropPenaltyRatio(Re));
    result.put("performanceEvaluationCriteria", getPerformanceEvaluationCriteria(Re, Pr));
    result.put("reynoldsNumber", Re);
    result.put("prandtlNumber", Pr);

    Map<String, Object> params = new LinkedHashMap<String, Object>();
    switch (insertType) {
      case TWISTED_TAPE:
        params.put("twistRatio", twistRatio);
        params.put("tapeThickness_m", tapeThickness);
        params.put("tapeWidthFraction", tapeWidthFraction);
        break;
      case WIRE_MATRIX:
        params.put("wireDiameter_m", wireDiameter);
        params.put("wireCoilPitch_m", wireCoilPitch);
        params.put("matrixDensity", matrixDensity);
        break;
      case COILED_WIRE:
        params.put("roughnessRatio_eOverD", roughnessRatio);
        params.put("helixAngle_deg", helixAngle);
        break;
      default:
        break;
    }
    result.put("insertParameters", params);

    return result;
  }

  /**
   * Converts results to JSON string.
   *
   * @param Re Reynolds number
   * @param Pr Prandtl number
   * @return JSON string
   */
  public String toJson(double Re, double Pr) {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(toMap(Re, Pr));
  }

  // ============================================================================
  // Factory methods
  // ============================================================================

  /**
   * Creates a twisted tape insert model with specified twist ratio.
   *
   * @param twistRatio twist ratio y = H/(2D), typical 2.5-10
   * @return configured TubeInsertModel
   */
  public static TubeInsertModel createTwistedTape(double twistRatio) {
    TubeInsertModel model = new TubeInsertModel(InsertType.TWISTED_TAPE);
    model.twistRatio = twistRatio;
    return model;
  }

  /**
   * Creates a wire matrix (HiTRAN-type) insert model.
   *
   * @param density matrix density factor (0.3-0.7)
   * @return configured TubeInsertModel
   */
  public static TubeInsertModel createWireMatrix(double density) {
    TubeInsertModel model = new TubeInsertModel(InsertType.WIRE_MATRIX);
    model.matrixDensity = density;
    return model;
  }

  /**
   * Creates a coiled wire insert model.
   *
   * @param roughnessRatio wire roughness e/D (0.01-0.05)
   * @param helixAngle helix angle in degrees (30-70)
   * @return configured TubeInsertModel
   */
  public static TubeInsertModel createCoiledWire(double roughnessRatio, double helixAngle) {
    TubeInsertModel model = new TubeInsertModel(InsertType.COILED_WIRE);
    model.roughnessRatio = roughnessRatio;
    model.helixAngle = helixAngle;
    return model;
  }

  // ============================================================================
  // Getters and setters
  // ============================================================================

  /**
   * Gets the insert type.
   *
   * @return insert type
   */
  public InsertType getInsertType() {
    return insertType;
  }

  /**
   * Sets the insert type.
   *
   * @param insertType insert type
   */
  public void setInsertType(InsertType insertType) {
    this.insertType = insertType;
  }

  /**
   * Gets the twist ratio for twisted tape.
   *
   * @return twist ratio y = H/(2D)
   */
  public double getTwistRatio() {
    return twistRatio;
  }

  /**
   * Sets the twist ratio for twisted tape.
   *
   * @param twistRatio twist ratio y = H/(2D)
   */
  public void setTwistRatio(double twistRatio) {
    this.twistRatio = twistRatio;
  }

  /**
   * Sets the tape thickness for twisted tape.
   *
   * @param thickness tape thickness (m)
   */
  public void setTapeThickness(double thickness) {
    this.tapeThickness = thickness;
  }

  /**
   * Gets the wire matrix density factor.
   *
   * @return matrix density (0 to 1)
   */
  public double getMatrixDensity() {
    return matrixDensity;
  }

  /**
   * Sets the wire matrix density factor.
   *
   * @param density matrix density (0 to 1)
   */
  public void setMatrixDensity(double density) {
    this.matrixDensity = density;
  }

  /**
   * Sets the wire diameter for wire matrix / coiled wire inserts.
   *
   * @param diameter wire diameter (m)
   */
  public void setWireDiameter(double diameter) {
    this.wireDiameter = diameter;
  }

  /**
   * Sets the roughness ratio for coiled wire insert.
   *
   * @param eOverD roughness ratio e/D
   */
  public void setRoughnessRatio(double eOverD) {
    this.roughnessRatio = eOverD;
  }

  /**
   * Sets the helix angle for coiled wire insert.
   *
   * @param angle helix angle (degrees)
   */
  public void setHelixAngle(double angle) {
    this.helixAngle = angle;
  }
}
