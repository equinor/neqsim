package neqsim.process.mechanicaldesign.valve.choke;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import neqsim.thermo.system.SystemInterface;

/**
 * Abstract base class for multiphase choke flow calculations.
 *
 * <p>
 * This class provides the foundation for calculating two-phase (gas-liquid) flow through production
 * chokes. Unlike single-phase IEC 60534 models, these correlations account for the interaction
 * between gas and liquid phases during flow through a restriction.
 * </p>
 *
 * <p>
 * Key concepts:
 * </p>
 * <ul>
 * <li><b>Critical flow</b>: Flow is choked when downstream pressure changes have no effect on flow
 * rate. Occurs when pressure ratio P2/P1 falls below critical pressure ratio.</li>
 * <li><b>Subcritical flow</b>: Both upstream and downstream pressures affect flow rate.</li>
 * <li><b>Gas quality (x_g)</b>: Mass fraction of gas in the two-phase mixture.</li>
 * </ul>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Sachdeva, R., et al. (1986). "Two-Phase Flow Through Chokes." SPE 15657.</li>
 * <li>Perkins, T.K. (1990). "Critical and Subcritical Flow of Multiphase Mixtures Through Chokes."
 * SPE 20633.</li>
 * <li>Gilbert, W.E. (1954). "Flowing and Gas-Lift Well Performance." API Drilling and Production
 * Practice.</li>
 * </ul>
 *
 * @author esol
 * @version 1.0
 */
public abstract class MultiphaseChokeFlow implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Discharge coefficient for the choke (typically 0.75-0.90). */
  protected double dischargeCoefficient = 0.84;

  /** Choke throat diameter in meters. */
  protected double chokeDiameter = 0.0254; // 1 inch default

  /** Upstream pipe diameter in meters. */
  protected double upstreamDiameter = 0.1016; // 4 inch default

  /** Polytropic exponent for gas expansion (1.0 = isothermal, gamma = isentropic). */
  protected double polytropicExponent = 1.2;

  /** Flow regime: CRITICAL or SUBCRITICAL. */
  public enum FlowRegime {
    /** Critical (choked) flow - downstream pressure has no effect. */
    CRITICAL,
    /** Subcritical flow - both pressures affect flow rate. */
    SUBCRITICAL,
    /** Unknown or not yet determined. */
    UNKNOWN
  }

  /**
   * Default constructor for MultiphaseChokeFlow.
   */
  public MultiphaseChokeFlow() {}

  /**
   * Constructor with choke diameter.
   *
   * @param chokeDiameter choke throat diameter in meters
   */
  public MultiphaseChokeFlow(double chokeDiameter) {
    this.chokeDiameter = chokeDiameter;
  }

  /**
   * Calculates the mass flow rate through the choke.
   *
   * @param fluid the thermodynamic system representing the fluid
   * @param upstreamPressure upstream pressure in Pa
   * @param downstreamPressure downstream pressure in Pa
   * @return mass flow rate in kg/s
   */
  public abstract double calculateMassFlowRate(SystemInterface fluid, double upstreamPressure,
      double downstreamPressure);

  /**
   * Calculates the downstream pressure for a given mass flow rate.
   *
   * @param fluid the thermodynamic system representing the fluid
   * @param upstreamPressure upstream pressure in Pa
   * @param massFlowRate mass flow rate in kg/s
   * @return downstream pressure in Pa
   */
  public abstract double calculateDownstreamPressure(SystemInterface fluid, double upstreamPressure,
      double massFlowRate);

  /**
   * Calculates the critical pressure ratio for two-phase flow.
   *
   * @param gasQuality gas mass fraction (0 to 1)
   * @param specificHeatRatio ratio of specific heats (Cp/Cv)
   * @return critical pressure ratio (P2/P1 at choking)
   */
  public abstract double calculateCriticalPressureRatio(double gasQuality,
      double specificHeatRatio);

  /**
   * Determines the flow regime (critical or subcritical).
   *
   * @param fluid the thermodynamic system
   * @param upstreamPressure upstream pressure in Pa
   * @param downstreamPressure downstream pressure in Pa
   * @return the flow regime
   */
  public FlowRegime determineFlowRegime(SystemInterface fluid, double upstreamPressure,
      double downstreamPressure) {
    double gasQuality = calculateGasQuality(fluid);
    double gamma = fluid.getGamma2();
    double criticalRatio = calculateCriticalPressureRatio(gasQuality, gamma);
    double actualRatio = downstreamPressure / upstreamPressure;

    if (actualRatio <= criticalRatio) {
      return FlowRegime.CRITICAL;
    } else {
      return FlowRegime.SUBCRITICAL;
    }
  }

  /**
   * Calculates the gas quality (gas mass fraction) from the fluid.
   *
   * @param fluid the thermodynamic system
   * @return gas mass fraction (0 to 1)
   */
  public double calculateGasQuality(SystemInterface fluid) {
    if (!fluid.hasPhaseType("gas")) {
      return 0.0;
    }
    if (!fluid.hasPhaseType("oil") && !fluid.hasPhaseType("aqueous")) {
      return 1.0;
    }

    double gasMass = 0.0;
    double totalMass = fluid.getFlowRate("kg/sec");

    if (totalMass <= 0) {
      // Use mole fractions and molecular weights
      totalMass = fluid.getTotalNumberOfMoles() * fluid.getMolarMass("kg/mol");
      if (fluid.hasPhaseType("gas")) {
        int gasPhaseIndex = fluid.getPhaseIndex("gas");
        gasMass = fluid.getPhase(gasPhaseIndex).getNumberOfMolesInPhase()
            * fluid.getPhase(gasPhaseIndex).getMolarMass("kg/mol");
      }
    } else {
      if (fluid.hasPhaseType("gas")) {
        int gasPhaseIndex = fluid.getPhaseIndex("gas");
        gasMass = fluid.getPhase(gasPhaseIndex).getFlowRate("kg/sec");
      }
    }

    if (totalMass <= 0) {
      return 0.0;
    }
    return Math.max(0.0, Math.min(1.0, gasMass / totalMass));
  }

  /**
   * Calculates the gas-liquid ratio (GLR) from the fluid.
   *
   * @param fluid the thermodynamic system
   * @return GLR in Sm3/Sm3 (standard cubic meters gas per standard cubic meter liquid)
   */
  public double calculateGLR(SystemInterface fluid) {
    double gasStdVolFlow = 0.0;
    double liquidStdVolFlow = 0.0;

    if (fluid.hasPhaseType("gas")) {
      int gasPhaseIndex = fluid.getPhaseIndex("gas");
      // Approximate standard conditions flow
      gasStdVolFlow =
          fluid.getPhase(gasPhaseIndex).getFlowRate("m3/sec") * fluid.getPhase(gasPhaseIndex).getZ()
              * fluid.getTemperature() / 288.15 * 101325.0 / fluid.getPressure("Pa");
    }

    if (fluid.hasPhaseType("oil")) {
      int oilPhaseIndex = fluid.getPhaseIndex("oil");
      liquidStdVolFlow += fluid.getPhase(oilPhaseIndex).getFlowRate("m3/sec");
    }
    if (fluid.hasPhaseType("aqueous")) {
      int waterPhaseIndex = fluid.getPhaseIndex("aqueous");
      liquidStdVolFlow += fluid.getPhase(waterPhaseIndex).getFlowRate("m3/sec");
    }

    if (liquidStdVolFlow <= 0) {
      return Double.POSITIVE_INFINITY; // All gas
    }
    return gasStdVolFlow / liquidStdVolFlow;
  }

  /**
   * Calculates the choke throat area.
   *
   * @return throat area in m^2
   */
  public double getChokeArea() {
    return Math.PI * chokeDiameter * chokeDiameter / 4.0;
  }

  /**
   * Calculates complete sizing results including all parameters.
   *
   * @param fluid the thermodynamic system
   * @param upstreamPressure upstream pressure in Pa
   * @param downstreamPressure downstream pressure in Pa
   * @return map containing all sizing results
   */
  public Map<String, Object> calculateSizingResults(SystemInterface fluid, double upstreamPressure,
      double downstreamPressure) {
    Map<String, Object> results = new HashMap<>();

    double gasQuality = calculateGasQuality(fluid);
    double gamma = fluid.getGamma2();
    double criticalRatio = calculateCriticalPressureRatio(gasQuality, gamma);
    double actualRatio = downstreamPressure / upstreamPressure;
    FlowRegime regime = determineFlowRegime(fluid, upstreamPressure, downstreamPressure);
    double massFlow = calculateMassFlowRate(fluid, upstreamPressure, downstreamPressure);

    results.put("massFlowRate", massFlow);
    results.put("massFlowRateUnit", "kg/s");
    results.put("gasQuality", gasQuality);
    results.put("criticalPressureRatio", criticalRatio);
    results.put("actualPressureRatio", actualRatio);
    results.put("flowRegime", regime.toString());
    results.put("isChoked", regime == FlowRegime.CRITICAL);
    results.put("dischargeCoefficient", dischargeCoefficient);
    results.put("chokeDiameter", chokeDiameter);
    results.put("chokeDiameterUnit", "m");
    results.put("chokeArea", getChokeArea());
    results.put("chokeAreaUnit", "m2");
    results.put("GLR", calculateGLR(fluid));

    return results;
  }

  // Getters and setters

  /**
   * Gets the discharge coefficient.
   *
   * @return discharge coefficient
   */
  public double getDischargeCoefficient() {
    return dischargeCoefficient;
  }

  /**
   * Sets the discharge coefficient.
   *
   * @param dischargeCoefficient discharge coefficient (typically 0.75-0.90)
   */
  public void setDischargeCoefficient(double dischargeCoefficient) {
    this.dischargeCoefficient = dischargeCoefficient;
  }

  /**
   * Gets the choke diameter.
   *
   * @return choke diameter in meters
   */
  public double getChokeDiameter() {
    return chokeDiameter;
  }

  /**
   * Sets the choke diameter.
   *
   * @param chokeDiameter choke diameter in meters
   */
  public void setChokeDiameter(double chokeDiameter) {
    this.chokeDiameter = chokeDiameter;
  }

  /**
   * Sets the choke diameter with unit specification.
   *
   * @param diameter choke diameter value
   * @param unit unit of diameter ("m", "mm", "in", "64ths")
   */
  public void setChokeDiameter(double diameter, String unit) {
    switch (unit.toLowerCase()) {
      case "m":
        this.chokeDiameter = diameter;
        break;
      case "mm":
        this.chokeDiameter = diameter / 1000.0;
        break;
      case "in":
      case "inch":
      case "inches":
        this.chokeDiameter = diameter * 0.0254;
        break;
      case "64ths":
      case "64th":
        // Bean size in 64ths of an inch (common oilfield unit)
        this.chokeDiameter = diameter / 64.0 * 0.0254;
        break;
      default:
        this.chokeDiameter = diameter;
    }
  }

  /**
   * Gets the upstream pipe diameter.
   *
   * @return upstream diameter in meters
   */
  public double getUpstreamDiameter() {
    return upstreamDiameter;
  }

  /**
   * Sets the upstream pipe diameter.
   *
   * @param upstreamDiameter upstream diameter in meters
   */
  public void setUpstreamDiameter(double upstreamDiameter) {
    this.upstreamDiameter = upstreamDiameter;
  }

  /**
   * Gets the polytropic exponent.
   *
   * @return polytropic exponent
   */
  public double getPolytropicExponent() {
    return polytropicExponent;
  }

  /**
   * Sets the polytropic exponent for gas expansion.
   *
   * @param polytropicExponent polytropic exponent (1.0 = isothermal, gamma = isentropic)
   */
  public void setPolytropicExponent(double polytropicExponent) {
    this.polytropicExponent = polytropicExponent;
  }

  /**
   * Returns the name of this choke flow model.
   *
   * @return model name
   */
  public abstract String getModelName();
}
