package neqsim.process.mechanicaldesign.separator.primaryseparation;

import java.io.Serializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Base class for primary separation inlet devices in a separator vessel.
 *
 * <p>
 * The primary separation zone is the region immediately after the inlet nozzle. The inlet device
 * reduces the momentum of the incoming two-phase stream and performs initial bulk liquid
 * separation. Key metrics are the inlet momentum (rho * v^2) and the resulting liquid carry-over
 * into the gravity section.
 * </p>
 *
 * <p>
 * Industry standards (NORSOK P-001, API 12J) limit inlet momentum to prevent re-entrainment:
 * </p>
 *
 * <table>
 * <caption>Typical inlet momentum limits</caption>
 * <tr>
 * <th>Device Type</th>
 * <th>Max rho*v^2 [Pa]</th>
 * </tr>
 * <tr>
 * <td>No inlet device (bare pipe)</td>
 * <td>1500</td>
 * </tr>
 * <tr>
 * <td>Deflector plate / half-pipe</td>
 * <td>3000</td>
 * </tr>
 * <tr>
 * <td>Inlet vane</td>
 * <td>6000</td>
 * </tr>
 * <tr>
 * <td>Inlet cyclone</td>
 * <td>8000</td>
 * </tr>
 * </table>
 *
 * @author NeqSim
 * @version 1.0
 */
public class PrimarySeparation implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PrimarySeparation.class);

  /** Name of the inlet device. */
  private String name = "";

  /** Inlet nozzle internal diameter [m]. */
  private double inletNozzleDiameter = 0.254;

  /** Maximum allowable inlet momentum rho*v^2 [Pa]. */
  private double maxInletMomentum = 3000.0;

  /** Liquid bulk separation efficiency at the inlet [0..1]. */
  private double bulkSeparationEfficiency = 0.70;

  /**
   * Constructs a PrimarySeparation with default parameters.
   */
  public PrimarySeparation() {}

  /**
   * Constructs a PrimarySeparation with a name.
   *
   * @param name the name of this inlet device
   */
  public PrimarySeparation(String name) {
    this.name = name;
  }

  /**
   * Calculates the inlet momentum (rho * v^2) at the inlet nozzle.
   *
   * <p>
   * $$ \rho v^2 = \rho_{mix} \left(\frac{Q_{mix}}{A_{nozzle}}\right)^2 $$
   * </p>
   *
   * @param mixtureDensity density of the two-phase mixture [kg/m3]
   * @param volumetricFlowRate total volumetric flow rate [m3/s]
   * @return inlet momentum [Pa]
   */
  public double calcInletMomentum(double mixtureDensity, double volumetricFlowRate) {
    double nozzleArea = Math.PI * inletNozzleDiameter * inletNozzleDiameter / 4.0;
    if (nozzleArea <= 0) {
      logger.warn("Inlet nozzle diameter is zero or negative");
      return 0.0;
    }
    double velocity = volumetricFlowRate / nozzleArea;
    return mixtureDensity * velocity * velocity;
  }

  /**
   * Checks whether the inlet momentum is within the allowable limit for this device type.
   *
   * @param mixtureDensity density of the two-phase mixture [kg/m3]
   * @param volumetricFlowRate total volumetric flow rate [m3/s]
   * @return true if within limits, false if inlet momentum exceeds maximum
   */
  public boolean checkInletMomentum(double mixtureDensity, double volumetricFlowRate) {
    return calcInletMomentum(mixtureDensity, volumetricFlowRate) <= maxInletMomentum;
  }

  /**
   * Calculates the liquid carry-over fraction past the inlet device. A simple model where
   * carry-over is (1 - bulkSeparationEfficiency), modified by how close the momentum is to the
   * limit.
   *
   * @param mixtureDensity density of the two-phase mixture [kg/m3]
   * @param volumetricFlowRate total volumetric flow rate [m3/s]
   * @return liquid carry-over fraction [0..1]
   */
  public double calcLiquidCarryOver(double mixtureDensity, double volumetricFlowRate) {
    double momentum = calcInletMomentum(mixtureDensity, volumetricFlowRate);
    double momentumRatio = momentum / maxInletMomentum;
    if (momentumRatio <= 1.0) {
      // Within design range: base carry-over
      return 1.0 - bulkSeparationEfficiency;
    }
    // Above design: efficiency degrades linearly
    double degradation = Math.min(1.0, (momentumRatio - 1.0) * 2.0);
    return (1.0 - bulkSeparationEfficiency) + bulkSeparationEfficiency * degradation;
  }

  /**
   * Gets the name of this inlet device.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the name of this inlet device.
   *
   * @param name the name to set
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Gets the inlet nozzle internal diameter.
   *
   * @return inlet nozzle diameter [m]
   */
  public double getInletNozzleDiameter() {
    return inletNozzleDiameter;
  }

  /**
   * Sets the inlet nozzle internal diameter.
   *
   * @param inletNozzleDiameter inlet nozzle diameter [m]
   */
  public void setInletNozzleDiameter(double inletNozzleDiameter) {
    this.inletNozzleDiameter = inletNozzleDiameter;
  }

  /**
   * Gets the maximum allowable inlet momentum.
   *
   * @return max inlet momentum [Pa]
   */
  public double getMaxInletMomentum() {
    return maxInletMomentum;
  }

  /**
   * Sets the maximum allowable inlet momentum.
   *
   * @param maxInletMomentum max inlet momentum [Pa]
   */
  public void setMaxInletMomentum(double maxInletMomentum) {
    this.maxInletMomentum = maxInletMomentum;
  }

  /**
   * Gets the bulk separation efficiency.
   *
   * @return bulk separation efficiency [0..1]
   */
  public double getBulkSeparationEfficiency() {
    return bulkSeparationEfficiency;
  }

  /**
   * Sets the bulk separation efficiency.
   *
   * @param bulkSeparationEfficiency bulk separation efficiency [0..1]
   */
  public void setBulkSeparationEfficiency(double bulkSeparationEfficiency) {
    this.bulkSeparationEfficiency = bulkSeparationEfficiency;
  }
}
