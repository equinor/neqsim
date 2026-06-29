package neqsim.standards.gasquality;

import java.io.Serializable;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Gas-well flow rate calculator using the GPSA critical-flow prover (orifice well tester) method. The critical-flow
 * prover discharges gas to atmosphere through an orifice plate sized so that the flow is sonic (the downstream pressure
 * is less than the critical ratio of the upstream pressure), which fixes the flow as a function of the upstream static
 * pressure only.
 *
 * <p>
 * The GPSA Engineering Data Book gives the rate as:
 * </p>
 *
 * <p>
 * Q<sub>g</sub> = C &middot; P<sub>f</sub> &middot; F<sub>tf</sub> &middot; F<sub>g</sub> &middot; F<sub>pv</sub>
 * </p>
 *
 * <ul>
 * <li>C &ndash; orifice coefficient from the prover plate tables (input, depends on plate bore and tap type)</li>
 * <li>P<sub>f</sub> &ndash; upstream static (flowing) pressure, psia</li>
 * <li>F<sub>tf</sub> = &radic;(520 / T<sub>f</sub>) &ndash; flowing-temperature factor (T<sub>f</sub> in &deg;R)</li>
 * <li>F<sub>g</sub> = &radic;(1 / G) &ndash; specific-gravity factor (G = gas gravity, air = 1)</li>
 * <li>F<sub>pv</sub> &ndash; supercompressibility factor (1/&radic;Z)</li>
 * </ul>
 *
 * <p>
 * The result Q<sub>g</sub> is in Mscf/day at the standard base of the supplied coefficient table.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class OrificeWellTester implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(OrificeWellTester.class);

  /** Orifice coefficient C from the prover plate table (Mscf/day per psia). */
  private double orificeCoefficient = 0.0;

  /** Upstream static (flowing) pressure in psia. */
  private double staticPressurePsia = 100.0;

  /** Flowing temperature in degrees Rankine. */
  private double flowingTemperatureRankine = 520.0;

  /** Gas specific gravity (air = 1). */
  private double specificGravity = 0.65;

  /** Gas compressibility factor Z at flowing conditions. */
  private double compressibilityFactor = 1.0;

  // ====================== Results ======================
  private double temperatureFactor = 0.0;
  private double gravityFactor = 0.0;
  private double supercompressibilityFactor = 0.0;
  private double gasRate = 0.0;

  /**
   * Default constructor for OrificeWellTester.
   */
  public OrificeWellTester() {
  }

  /**
   * Sets the orifice coefficient from the prover plate table.
   *
   * @param coefficient orifice coefficient C in Mscf/day per psia (must be &gt; 0)
   */
  public void setOrificeCoefficient(double coefficient) {
    this.orificeCoefficient = coefficient;
  }

  /**
   * Sets the flowing conditions.
   *
   * @param pressurePsia upstream static pressure in psia (must be &gt; 0)
   * @param temperatureRankine flowing temperature in degrees Rankine (must be &gt; 0)
   */
  public void setFlowingConditions(double pressurePsia, double temperatureRankine) {
    this.staticPressurePsia = pressurePsia;
    this.flowingTemperatureRankine = temperatureRankine;
  }

  /**
   * Sets the gas properties.
   *
   * @param gravity gas specific gravity (air = 1, must be &gt; 0)
   * @param zFactor compressibility factor Z at flowing conditions (must be &gt; 0)
   */
  public void setGasProperties(double gravity, double zFactor) {
    this.specificGravity = gravity;
    this.compressibilityFactor = zFactor;
  }

  /**
   * Runs the GPSA critical-flow prover rate calculation.
   */
  public void calcRate() {
    temperatureFactor = Math.sqrt(520.0 / flowingTemperatureRankine);
    gravityFactor = Math.sqrt(1.0 / specificGravity);
    supercompressibilityFactor = 1.0 / Math.sqrt(compressibilityFactor);
    gasRate = orificeCoefficient * staticPressurePsia * temperatureFactor * gravityFactor * supercompressibilityFactor;

    logger.debug("Orifice well tester: Ftf={}, Fg={}, Fpv={}, Qg={} Mscf/day", temperatureFactor, gravityFactor,
        supercompressibilityFactor, gasRate);
  }

  /**
   * Returns the flowing-temperature factor F<sub>tf</sub>.
   *
   * @return temperature factor (dimensionless)
   */
  public double getTemperatureFactor() {
    return temperatureFactor;
  }

  /**
   * Returns the specific-gravity factor F<sub>g</sub>.
   *
   * @return gravity factor (dimensionless)
   */
  public double getGravityFactor() {
    return gravityFactor;
  }

  /**
   * Returns the supercompressibility factor F<sub>pv</sub>.
   *
   * @return supercompressibility factor (dimensionless)
   */
  public double getSupercompressibilityFactor() {
    return supercompressibilityFactor;
  }

  /**
   * Returns the calculated gas rate.
   *
   * @return gas rate in Mscf/day
   */
  public double getGasRate() {
    return gasRate;
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
