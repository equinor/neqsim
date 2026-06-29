package neqsim.standards.gasquality;

import java.io.Serializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;

/**
 * Critical (choked / sonic) flow calculator for a gas flowing through an orifice or restriction. This is the basis for
 * estimating the maximum (rupture) discharge rate through a fixed restriction such as a hole, broken instrument tapping
 * or an orifice in a blowdown line, where the downstream pressure is low enough that the flow becomes sonic at the
 * throat.
 *
 * <p>
 * Choked flow occurs when the downstream-to-upstream pressure ratio falls to or below the critical pressure ratio:
 * </p>
 *
 * <p>
 * r<sub>c</sub> = (2 / (k + 1))<sup>k/(k-1)</sup>
 * </p>
 *
 * <p>
 * At and below this ratio the mass flow no longer increases with falling downstream pressure and is given by:
 * </p>
 *
 * <p>
 * W = C<sub>d</sub> &middot; A &middot; &radic;(k &middot; &rho;<sub>1</sub> &middot; P<sub>1</sub> &middot; (2 / (k +
 * 1))<sup>(k+1)/(k-1)</sup>)
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class CriticalFlowOrifice implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(CriticalFlowOrifice.class);

  /** Throat (orifice/hole) area in m2. */
  private double throatArea = 1.0e-4;

  /** Upstream stagnation pressure in Pa absolute. */
  private double upstreamPressure = 5.0e6;

  /** Upstream density in kg/m3. */
  private double upstreamDensity = 50.0;

  /** Isentropic exponent k = Cp/Cv. */
  private double isentropicExponent = 1.3;

  /** Discharge coefficient. */
  private double dischargeCoefficient = 0.85;

  // ====================== Results ======================
  private double criticalPressureRatio = 0.0;
  private double criticalExpansionFactor = 0.0;
  private double criticalMassFlow = 0.0;
  private boolean choked = false;

  /**
   * Default constructor for CriticalFlowOrifice.
   */
  public CriticalFlowOrifice() {
  }

  /**
   * Sets the restriction geometry and discharge coefficient.
   *
   * @param throatAreaM2 throat (hole/orifice) area in m2 (must be &gt; 0)
   * @param cd discharge coefficient (typically 0.62-0.85, must be &gt; 0)
   */
  public void setGeometry(double throatAreaM2, double cd) {
    this.throatArea = throatAreaM2;
    this.dischargeCoefficient = cd;
  }

  /**
   * Sets the upstream stagnation conditions.
   *
   * @param upstreamPressurePa upstream stagnation pressure in Pa absolute (must be &gt; 0)
   * @param upstreamDensityKgM3 upstream density in kg/m3 (must be &gt; 0)
   * @param isentropicExp isentropic exponent k = Cp/Cv (must be &gt; 1)
   */
  public void setUpstreamConditions(double upstreamPressurePa, double upstreamDensityKgM3, double isentropicExp) {
    this.upstreamPressure = upstreamPressurePa;
    this.upstreamDensity = upstreamDensityKgM3;
    this.isentropicExponent = isentropicExp;
  }

  /**
   * Computes the critical (choked) pressure ratio for the given isentropic exponent.
   *
   * @param k isentropic exponent (must be &gt; 1)
   * @return critical downstream-to-upstream pressure ratio (dimensionless)
   */
  public static double criticalPressureRatio(double k) {
    return Math.pow(2.0 / (k + 1.0), k / (k - 1.0));
  }

  /**
   * Returns whether the flow is choked for the given upstream and downstream pressures.
   *
   * @param p1 upstream pressure in Pa absolute (must be &gt; 0)
   * @param p2 downstream pressure in Pa absolute (must be &ge; 0)
   * @param k isentropic exponent (must be &gt; 1)
   * @return true if the flow is sonic (choked)
   */
  public static boolean isChoked(double p1, double p2, double k) {
    return (p2 / p1) <= criticalPressureRatio(k);
  }

  /**
   * Runs the critical mass flow calculation. The flow is assumed choked (downstream pressure at or below the critical
   * ratio); use {@link #isChoked(double, double, double)} to confirm the regime for a specific downstream pressure.
   */
  public void calcCriticalFlow() {
    criticalPressureRatio = criticalPressureRatio(isentropicExponent);
    double k = isentropicExponent;
    criticalExpansionFactor = Math.sqrt(k * Math.pow(2.0 / (k + 1.0), (k + 1.0) / (k - 1.0)));
    criticalMassFlow = dischargeCoefficient * throatArea
        * Math.sqrt(k * upstreamDensity * upstreamPressure * Math.pow(2.0 / (k + 1.0), (k + 1.0) / (k - 1.0)));
    choked = true;

    logger.debug("Critical-flow orifice: rc={}, Ycr={}, W={} kg/s", criticalPressureRatio, criticalExpansionFactor,
        criticalMassFlow);
  }

  /**
   * Returns the critical pressure ratio used in the calculation.
   *
   * @return critical pressure ratio (dimensionless)
   */
  public double getCriticalPressureRatio() {
    return criticalPressureRatio;
  }

  /**
   * Returns the critical expansion factor.
   *
   * @return critical expansion factor (dimensionless)
   */
  public double getCriticalExpansionFactor() {
    return criticalExpansionFactor;
  }

  /**
   * Returns the critical (choked) mass flow rate.
   *
   * @return critical mass flow rate in kg/s
   */
  public double getCriticalMassFlow() {
    return criticalMassFlow;
  }

  /**
   * Returns whether the calculated condition is choked.
   *
   * @return true if the flow is choked
   */
  public boolean isFlowChoked() {
    return choked;
  }

  /**
   * Serializes the calculation results to a pretty-printed JSON string.
   *
   * @return JSON representation of the results
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}
