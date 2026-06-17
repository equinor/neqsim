package neqsim.process.mechanicaldesign.separator.internals;

import java.io.Serializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Base class for demisting internals used in separators and gas scrubbers.
 *
 * <p>
 * Models wire mesh, vane pack, and cyclone demisting devices. Provides methods to calculate
 * allowable gas velocity (Souders-Brown), pressure drop (Euler number), and liquid carry-over
 * fraction.
 * </p>
 *
 * <p>
 * Typical Euler numbers (Eu):
 * </p>
 * <table>
 * <caption>Typical Euler numbers by demister type</caption>
 * <tr>
 * <th>Demister Type</th>
 * <th>Eu</th>
 * </tr>
 * <tr>
 * <td>Wire mesh (standard)</td>
 * <td>100–200</td>
 * </tr>
 * <tr>
 * <td>Wire mesh (high-capacity)</td>
 * <td>50–100</td>
 * </tr>
 * <tr>
 * <td>Vane pack (horizontal flow)</td>
 * <td>20–60</td>
 * </tr>
 * <tr>
 * <td>Multi-cyclone</td>
 * <td>40–80</td>
 * </tr>
 * </table>
 *
 * @author NeqSim
 * @version 1.0
 */
public class DemistingInternal implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(DemistingInternal.class);

  /** Name identifier for this internal. */
  private String name = "";

  /** Cross-sectional area available for gas flow [m2]. */
  private double area = 0.0;

  /** Euler number used for pressure drop calculation [-]. */
  private double euNumber = 150.0;

  /**
   * Souders-Brown K-factor for maximum allowable gas velocity [m/s]. Typical values: wire mesh
   * 0.05-0.12, vane pack 0.10-0.20, cyclone 0.15-0.30.
   */
  private double kFactor = 0.107;

  /** Void fraction of the demisting element [-]. */
  private double voidFraction = 0.97;

  /** Thickness of the demisting pad [m]. */
  private double thickness = 0.15;

  /** Wire diameter for wire mesh type [m]. */
  private double wireDiameter = 0.00028;

  /** Demister type: "wire_mesh", "vane_pack", or "cyclone". */
  private String type = "wire_mesh";

  /**
   * Constructs a DemistingInternal with default parameters.
   */
  public DemistingInternal() {}

  /**
   * Constructs a DemistingInternal with a name.
   *
   * @param name the name of this demisting internal
   */
  public DemistingInternal(String name) {
    this.name = name;
  }

  /**
   * Constructs a DemistingInternal with a name and type.
   *
   * @param name the name of this demisting internal
   * @param type the demister type ("wire_mesh", "vane_pack", or "cyclone")
   */
  public DemistingInternal(String name, String type) {
    this.name = name;
    this.type = type;
    applyTypeDefaults(type);
  }

  /**
   * Applies default parameters based on the demister type.
   *
   * @param demisterType the demister type string
   */
  private void applyTypeDefaults(String demisterType) {
    if ("wire_mesh".equalsIgnoreCase(demisterType)) {
      this.kFactor = 0.107;
      this.voidFraction = 0.97;
      this.thickness = 0.15;
      this.wireDiameter = 0.00028;
      this.euNumber = 150.0;
    } else if ("vane_pack".equalsIgnoreCase(demisterType)) {
      this.kFactor = 0.15;
      this.voidFraction = 0.90;
      this.thickness = 0.30;
      this.wireDiameter = 0.0;
      this.euNumber = 40.0;
    } else if ("cyclone".equalsIgnoreCase(demisterType)) {
      this.kFactor = 0.20;
      this.voidFraction = 0.85;
      this.thickness = 0.50;
      this.wireDiameter = 0.0;
      this.euNumber = 60.0;
    }
  }

  /**
   * Calculates the maximum allowable gas velocity through the demister using the Souders-Brown
   * equation.
   *
   * <p>
   * $$ v_{gas,max} = K \sqrt{\frac{\rho_L - \rho_G}{\rho_G}} $$
   * </p>
   *
   * @param gasVelocitySuperficial superficial gas velocity [m/s] (not used in max calc, but
   *        retained for signature compatibility)
   * @param gasDensity gas phase density [kg/m3]
   * @param liquidDensity liquid phase density [kg/m3]
   * @return maximum allowable gas velocity [m/s]
   */
  public double calcGasVelocity(double gasVelocitySuperficial, double gasDensity,
      double liquidDensity) {
    if (gasDensity <= 0 || liquidDensity <= gasDensity) {
      logger.warn("Invalid densities for gas velocity calculation: gasRho={}, liqRho={}",
          gasDensity, liquidDensity);
      return 0.0;
    }
    return kFactor * Math.sqrt((liquidDensity - gasDensity) / gasDensity);
  }

  /**
   * Calculates the pressure drop across the demister using the Euler number correlation.
   *
   * <p>
   * $$ \Delta P = Eu \cdot \frac{1}{2} \rho_G v_{gas}^2 $$
   * </p>
   *
   * @param gasVelocity gas velocity through the demister [m/s]
   * @param gasDensity gas phase density [kg/m3]
   * @return pressure drop [Pa]
   */
  public double calcPressureDrop(double gasVelocity, double gasDensity) {
    return euNumber * 0.5 * gasDensity * gasVelocity * gasVelocity;
  }

  /**
   * Calculates the liquid carry-over fraction past the demister. Uses a simple exponential decay
   * model based on the ratio of actual velocity to maximum velocity.
   *
   * <p>
   * For velocity ratios below 1.0, carry-over is near zero. Above 1.0, carry-over rises rapidly
   * towards 1.0 (flooding).
   * </p>
   *
   * @param gasVelocity actual gas velocity [m/s]
   * @param maxGasVelocity maximum allowable gas velocity [m/s]
   * @return liquid carry-over fraction [0..1], where 0 = no carry-over, 1 = total flooding
   */
  public double calcLiquidCarryOver(double gasVelocity, double maxGasVelocity) {
    if (maxGasVelocity <= 0) {
      return 1.0;
    }
    double ratio = gasVelocity / maxGasVelocity;
    if (ratio < 0.8) {
      return 0.0;
    }
    // Exponential rise from 80% to 120% of max velocity
    double exponent = 5.0 * (ratio - 0.8);
    return Math.min(1.0, 1.0 - Math.exp(-exponent));
  }

  /**
   * Gets the name of this demisting internal.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the name of this demisting internal.
   *
   * @param name the name to set
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Gets the cross-sectional area available for gas flow.
   *
   * @return area [m2]
   */
  public double getArea() {
    return area;
  }

  /**
   * Sets the cross-sectional area available for gas flow.
   *
   * @param area area [m2]
   */
  public void setArea(double area) {
    this.area = area;
  }

  /**
   * Gets the Euler number for pressure drop calculation.
   *
   * @return Euler number [-]
   */
  public double getEuNumber() {
    return euNumber;
  }

  /**
   * Sets the Euler number for pressure drop calculation.
   *
   * @param euNumber Euler number [-]
   */
  public void setEuNumber(double euNumber) {
    this.euNumber = euNumber;
  }

  /**
   * Gets the Souders-Brown K-factor.
   *
   * @return K-factor [m/s]
   */
  public double getKFactor() {
    return kFactor;
  }

  /**
   * Sets the Souders-Brown K-factor.
   *
   * @param kFactor K-factor [m/s]
   */
  public void setKFactor(double kFactor) {
    this.kFactor = kFactor;
  }

  /**
   * Gets the void fraction of the demisting element.
   *
   * @return void fraction [-]
   */
  public double getVoidFraction() {
    return voidFraction;
  }

  /**
   * Sets the void fraction of the demisting element.
   *
   * @param voidFraction void fraction [-]
   */
  public void setVoidFraction(double voidFraction) {
    this.voidFraction = voidFraction;
  }

  /**
   * Gets the thickness of the demisting pad.
   *
   * @return thickness [m]
   */
  public double getThickness() {
    return thickness;
  }

  /**
   * Sets the thickness of the demisting pad.
   *
   * @param thickness thickness [m]
   */
  public void setThickness(double thickness) {
    this.thickness = thickness;
  }

  /**
   * Gets the wire diameter for wire mesh type.
   *
   * @return wire diameter [m]
   */
  public double getWireDiameter() {
    return wireDiameter;
  }

  /**
   * Sets the wire diameter for wire mesh type.
   *
   * @param wireDiameter wire diameter [m]
   */
  public void setWireDiameter(double wireDiameter) {
    this.wireDiameter = wireDiameter;
  }

  /**
   * Gets the demister type.
   *
   * @return demister type string
   */
  public String getType() {
    return type;
  }

  /**
   * Sets the demister type and applies default parameters for that type.
   *
   * @param type demister type ("wire_mesh", "vane_pack", or "cyclone")
   */
  public void setType(String type) {
    this.type = type;
    applyTypeDefaults(type);
  }
}
