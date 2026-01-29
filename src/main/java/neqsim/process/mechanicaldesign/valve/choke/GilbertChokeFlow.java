package neqsim.process.mechanicaldesign.valve.choke;

import neqsim.thermo.system.SystemInterface;

/**
 * Gilbert (1954) empirical correlation for two-phase critical choke flow.
 *
 * <p>
 * This is the pioneering empirical correlation for multiphase flow through production chokes,
 * developed from California oil field data. It is widely used for quick estimates and field
 * calculations.
 * </p>
 *
 * <p>
 * <b>Gilbert Equation:</b>
 * </p>
 * 
 * <pre>
 * q_L = (P_wh * d ^ 1.89) / (C * GLR ^ 0.546)
 * </pre>
 *
 * <p>
 * where:
 * </p>
 * <ul>
 * <li>q_L = liquid flow rate (STB/day)</li>
 * <li>P_wh = upstream/wellhead pressure (psig)</li>
 * <li>d = choke diameter (64ths of an inch)</li>
 * <li>GLR = gas-liquid ratio (scf/STB)</li>
 * <li>C = 10 (Gilbert's original constant)</li>
 * </ul>
 *
 * <p>
 * <b>Key Assumptions:</b>
 * </p>
 * <ul>
 * <li>Critical (choked) flow only - downstream pressure has no effect</li>
 * <li>No slip between gas and liquid phases</li>
 * <li>Based on California oil wells with specific fluid properties</li>
 * </ul>
 *
 * <p>
 * <b>Applicability Range:</b>
 * </p>
 * <ul>
 * <li>GLR: 300 - 50,000 scf/STB</li>
 * <li>Oil gravity: API 20-40</li>
 * <li>Pressure: 100-5000 psig</li>
 * </ul>
 *
 * <p>
 * Related correlations with different constants:
 * </p>
 * <ul>
 * <li><b>Baxendell (1958):</b> C = 9.56, exponents: d^1.93, GLR^0.546</li>
 * <li><b>Ros (1960):</b> C = 17.4, exponents: d^2.0, GLR^0.5</li>
 * <li><b>Achong (1961):</b> C = 3.82, exponents: d^1.88, GLR^0.65</li>
 * </ul>
 *
 * <p>
 * <b>Reference:</b> Gilbert, W.E. (1954). "Flowing and Gas-Lift Well Performance." API Drilling and
 * Production Practice.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class GilbertChokeFlow extends MultiphaseChokeFlow {
  private static final long serialVersionUID = 1L;

  /** Correlation constant (Gilbert = 10, Baxendell = 9.56, Ros = 17.4, Achong = 3.82). */
  private double correlationConstant = 10.0;

  /** Diameter exponent (Gilbert = 1.89, Baxendell = 1.93, Ros = 2.0, Achong = 1.88). */
  private double diameterExponent = 1.89;

  /** GLR exponent (Gilbert = 0.546, Baxendell = 0.546, Ros = 0.5, Achong = 0.65). */
  private double glrExponent = 0.546;

  /** Correlation type for identifying which variant is used. */
  public enum CorrelationType {
    /** Original Gilbert (1954) correlation. */
    GILBERT,
    /** Baxendell (1958) correlation. */
    BAXENDELL,
    /** Ros (1960) correlation. */
    ROS,
    /** Achong (1961) correlation. */
    ACHONG,
    /** Custom user-defined constants. */
    CUSTOM
  }

  private CorrelationType correlationType = CorrelationType.GILBERT;

  /**
   * Default constructor using Gilbert's original constants.
   */
  public GilbertChokeFlow() {
    super();
    this.dischargeCoefficient = 1.0; // Already embedded in correlation constant
    setCorrelationType(CorrelationType.GILBERT);
  }

  /**
   * Constructor with choke diameter.
   *
   * @param chokeDiameter choke throat diameter in meters
   */
  public GilbertChokeFlow(double chokeDiameter) {
    super(chokeDiameter);
    this.dischargeCoefficient = 1.0;
    setCorrelationType(CorrelationType.GILBERT);
  }

  /**
   * Constructor with correlation type selection.
   *
   * @param correlationType the correlation variant to use
   */
  public GilbertChokeFlow(CorrelationType correlationType) {
    super();
    this.dischargeCoefficient = 1.0;
    setCorrelationType(correlationType);
  }

  /**
   * Sets the correlation type and updates constants accordingly.
   *
   * @param type correlation type
   */
  public void setCorrelationType(CorrelationType type) {
    this.correlationType = type;
    switch (type) {
      case GILBERT:
        correlationConstant = 10.0;
        diameterExponent = 1.89;
        glrExponent = 0.546;
        break;
      case BAXENDELL:
        correlationConstant = 9.56;
        diameterExponent = 1.93;
        glrExponent = 0.546;
        break;
      case ROS:
        correlationConstant = 17.4;
        diameterExponent = 2.0;
        glrExponent = 0.5;
        break;
      case ACHONG:
        correlationConstant = 3.82;
        diameterExponent = 1.88;
        glrExponent = 0.65;
        break;
      case CUSTOM:
        // Keep current values
        break;
    }
  }

  /**
   * Gets the correlation type.
   *
   * @return correlation type
   */
  public CorrelationType getCorrelationType() {
    return correlationType;
  }

  /** {@inheritDoc} */
  @Override
  public double calculateMassFlowRate(SystemInterface fluid, double upstreamPressure,
      double downstreamPressure) {
    // Convert to field units
    double P_psig = upstreamPressure / 6894.76 - 14.696; // Pa to psig
    double d_64ths = chokeDiameter / 0.0254 * 64.0; // m to 64ths inch

    // Calculate GLR in scf/STB
    double GLR_scf_stb = calculateGLR_scf_stb(fluid);

    // Handle edge cases
    if (GLR_scf_stb <= 0) {
      // Pure liquid - use liquid formula
      return calculateLiquidOnlyMassFlow(fluid, upstreamPressure, downstreamPressure);
    }
    if (P_psig <= 0 || d_64ths <= 0) {
      return 0.0;
    }

    // Gilbert equation: q_L = P * d^a / (C * GLR^b) in STB/day
    double q_stb_day = P_psig * Math.pow(d_64ths, diameterExponent)
        / (correlationConstant * Math.pow(GLR_scf_stb, glrExponent));

    // Convert STB/day to m3/s
    double q_m3_s = q_stb_day * 0.158987 / 86400.0;

    // Convert to mass flow using liquid density
    double rhoLiquid = getLiquidDensity(fluid);
    double massFlowLiquid = q_m3_s * rhoLiquid;

    // Add gas mass flow based on GLR
    double GLR_sm3_sm3 = GLR_scf_stb * 0.02832 / 0.158987; // Convert to Sm3/Sm3
    double gasStdDensity = 1.2; // Approximate standard gas density kg/Sm3
    double massFlowGas = q_m3_s * GLR_sm3_sm3 * gasStdDensity;

    return massFlowLiquid + massFlowGas;
  }

  /**
   * Calculates GLR in field units (scf/STB).
   *
   * @param fluid thermodynamic system
   * @return GLR in scf/STB
   */
  private double calculateGLR_scf_stb(SystemInterface fluid) {
    double GLR_sm3_sm3 = calculateGLR(fluid);
    if (Double.isInfinite(GLR_sm3_sm3)) {
      return Double.POSITIVE_INFINITY;
    }
    // Convert Sm3/Sm3 to scf/STB
    // 1 Sm3 = 35.3147 scf, 1 Sm3 = 6.28981 STB
    return GLR_sm3_sm3 * 35.3147 / 6.28981;
  }

  /**
   * Gets liquid density from fluid.
   *
   * @param fluid thermodynamic system
   * @return liquid density in kg/m3
   */
  private double getLiquidDensity(SystemInterface fluid) {
    if (fluid.hasPhaseType("oil")) {
      return fluid.getPhase(fluid.getPhaseIndex("oil")).getDensity("kg/m3");
    }
    if (fluid.hasPhaseType("aqueous")) {
      return fluid.getPhase(fluid.getPhaseIndex("aqueous")).getDensity("kg/m3");
    }
    return 800.0; // Default oil density
  }

  /**
   * Calculates liquid-only mass flow (pure liquid case).
   *
   * @param fluid thermodynamic system
   * @param P1 upstream pressure in Pa
   * @param P2 downstream pressure in Pa
   * @return mass flow rate in kg/s
   */
  private double calculateLiquidOnlyMassFlow(SystemInterface fluid, double P1, double P2) {
    double rhoLiquid = getLiquidDensity(fluid);
    double deltaP = Math.max(P1 - P2, 0);
    double A = getChokeArea();
    return 0.85 * A * Math.sqrt(2.0 * rhoLiquid * deltaP);
  }

  /** {@inheritDoc} */
  @Override
  public double calculateDownstreamPressure(SystemInterface fluid, double upstreamPressure,
      double massFlowRate) {
    // Gilbert correlation assumes critical flow - downstream pressure doesn't affect flow
    // Return critical pressure (approximately)
    double gasQuality = calculateGasQuality(fluid);
    double gamma = fluid.getGamma2();
    double criticalRatio = calculateCriticalPressureRatio(gasQuality, gamma);
    return upstreamPressure * criticalRatio;
  }

  /** {@inheritDoc} */
  @Override
  public double calculateCriticalPressureRatio(double gasQuality, double specificHeatRatio) {
    // Gilbert assumes critical flow - use empirical critical ratio
    // For two-phase, use Sachdeva-like correlation
    if (gasQuality <= 0.01) {
      return 0.85; // Liquid-dominated
    }
    if (gasQuality >= 0.99) {
      return Math.pow(2.0 / (specificHeatRatio + 1.0),
          specificHeatRatio / (specificHeatRatio - 1.0));
    }
    // Two-phase approximation
    return 0.5847 - 0.0227 * Math.log(Math.max(0.02, gasQuality));
  }

  /**
   * Calculates the required choke size for a given flow rate.
   *
   * <p>
   * Inverts the Gilbert equation to find choke diameter.
   * </p>
   *
   * @param fluid thermodynamic system
   * @param upstreamPressure upstream pressure in Pa
   * @param liquidFlowRate liquid flow rate in m3/s
   * @return required choke diameter in meters
   */
  public double calculateRequiredChokeDiameter(SystemInterface fluid, double upstreamPressure,
      double liquidFlowRate) {
    // Convert to field units
    double P_psig = upstreamPressure / 6894.76 - 14.696;
    double q_stb_day = liquidFlowRate * 86400.0 / 0.158987;
    double GLR_scf_stb = calculateGLR_scf_stb(fluid);

    if (GLR_scf_stb <= 0 || P_psig <= 0 || q_stb_day <= 0) {
      return 0.0;
    }

    // Invert Gilbert: d = ((q * C * GLR^b) / P)^(1/a)
    double d_64ths =
        Math.pow(q_stb_day * correlationConstant * Math.pow(GLR_scf_stb, glrExponent) / P_psig,
            1.0 / diameterExponent);

    // Convert to meters
    return d_64ths / 64.0 * 0.0254;
  }

  // Setters for custom correlation constants

  /**
   * Sets the correlation constant.
   *
   * @param constant correlation constant C
   */
  public void setCorrelationConstant(double constant) {
    this.correlationConstant = constant;
    this.correlationType = CorrelationType.CUSTOM;
  }

  /**
   * Gets the correlation constant.
   *
   * @return correlation constant
   */
  public double getCorrelationConstant() {
    return correlationConstant;
  }

  /**
   * Sets the diameter exponent.
   *
   * @param exponent diameter exponent
   */
  public void setDiameterExponent(double exponent) {
    this.diameterExponent = exponent;
    this.correlationType = CorrelationType.CUSTOM;
  }

  /**
   * Gets the diameter exponent.
   *
   * @return diameter exponent
   */
  public double getDiameterExponent() {
    return diameterExponent;
  }

  /**
   * Sets the GLR exponent.
   *
   * @param exponent GLR exponent
   */
  public void setGlrExponent(double exponent) {
    this.glrExponent = exponent;
    this.correlationType = CorrelationType.CUSTOM;
  }

  /**
   * Gets the GLR exponent.
   *
   * @return GLR exponent
   */
  public double getGlrExponent() {
    return glrExponent;
  }

  /** {@inheritDoc} */
  @Override
  public String getModelName() {
    switch (correlationType) {
      case BAXENDELL:
        return "Baxendell (1958)";
      case ROS:
        return "Ros (1960)";
      case ACHONG:
        return "Achong (1961)";
      case CUSTOM:
        return "Custom Gilbert-type";
      case GILBERT:
      default:
        return "Gilbert (1954)";
    }
  }
}
