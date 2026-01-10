package neqsim.process.equipment.watertreatment;

import java.util.UUID;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Hydrocyclone for produced water treatment.
 *
 * <p>
 * Hydrocyclones use centrifugal force to separate oil droplets from water. The swirling flow
 * creates a centrifugal force many times gravity, causing oil droplets to migrate to the center and
 * exit through the reject stream.
 * </p>
 *
 * <h2>Performance Characteristics</h2>
 * <ul>
 * <li><b>d50 cut size:</b> ~10-15 μm (50% removal efficiency)</li>
 * <li><b>d100:</b> ~20-30 μm (near 100% removal)</li>
 * <li><b>Reject ratio:</b> 1-3% of feed</li>
 * <li><b>Pressure drop:</b> 1-3 bar</li>
 * </ul>
 *
 * <h2>Separation Efficiency Model</h2>
 * 
 * <pre>
 * η = 1 - exp(-A × (d / d50)^n)
 * </pre>
 * 
 * <p>
 * where d50 is the cut size and n is typically 2-4.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class Hydrocyclone extends Separator {
  private static final long serialVersionUID = 1000L;

  /** Typical d50 cut size in microns. */
  private double d50Microns = 12.0;

  /** Reject ratio (oil-rich stream / feed). */
  private double rejectRatio = 0.02;

  /** Pressure drop across cyclone (bar). */
  private double pressureDrop = 2.0;

  /** Overall oil removal efficiency. */
  private double oilRemovalEfficiency = 0.95;

  /** Inlet oil concentration (mg/L). */
  private double inletOilMgL = 1000.0;

  /** Outlet oil concentration (mg/L). */
  private double outletOilMgL = 50.0;

  // ============================================================================
  // CONSTRUCTORS
  // ============================================================================

  /**
   * Creates a hydrocyclone.
   *
   * @param name equipment name
   */
  public Hydrocyclone(String name) {
    super(name);
    setOrientation("vertical");
  }

  /**
   * Creates a hydrocyclone with inlet stream.
   *
   * @param name equipment name
   * @param inletStream water stream containing oil
   */
  public Hydrocyclone(String name, StreamInterface inletStream) {
    super(name, inletStream);
    setOrientation("vertical");
  }

  // ============================================================================
  // CONFIGURATION
  // ============================================================================

  /**
   * Sets the d50 cut size.
   *
   * @param d50 cut size in microns
   */
  public void setD50Microns(double d50) {
    this.d50Microns = d50;
  }

  /**
   * Gets the d50 cut size.
   *
   * @return d50 in microns
   */
  public double getD50Microns() {
    return d50Microns;
  }

  /**
   * Sets the reject ratio.
   *
   * @param ratio reject/feed ratio (0.01-0.05 typical)
   */
  public void setRejectRatio(double ratio) {
    this.rejectRatio = Math.min(0.10, Math.max(0.005, ratio));
  }

  /**
   * Gets the reject ratio.
   *
   * @return reject ratio
   */
  public double getRejectRatio() {
    return rejectRatio;
  }

  /**
   * Sets overall oil removal efficiency.
   *
   * @param efficiency efficiency (0.0-1.0)
   */
  public void setOilRemovalEfficiency(double efficiency) {
    this.oilRemovalEfficiency = Math.min(1.0, Math.max(0.0, efficiency));
  }

  /**
   * Gets oil removal efficiency.
   *
   * @return efficiency (0.0-1.0)
   */
  public double getOilRemovalEfficiency() {
    return oilRemovalEfficiency;
  }

  /**
   * Sets inlet oil concentration.
   *
   * @param oilMgL oil concentration in mg/L
   */
  public void setInletOilConcentration(double oilMgL) {
    this.inletOilMgL = oilMgL;
  }

  // ============================================================================
  // CALCULATIONS
  // ============================================================================

  /**
   * Calculates removal efficiency for a given droplet size.
   *
   * <p>
   * Uses a modified Rosin-Rammler distribution:
   * </p>
   * 
   * <pre>
   * η(d) = 1 - exp(-0.693 × (d / d50)^n)
   * </pre>
   *
   * @param dropletSizeMicrons droplet diameter in microns
   * @return removal efficiency (0.0-1.0)
   */
  public double getEfficiencyForDropletSize(double dropletSizeMicrons) {
    double n = 3.0; // Sharpness factor
    double ratio = dropletSizeMicrons / d50Microns;
    return 1.0 - Math.exp(-0.693 * Math.pow(ratio, n));
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    super.run(id);

    // Calculate outlet concentration
    outletOilMgL = inletOilMgL * (1.0 - oilRemovalEfficiency);

    setCalculationIdentifier(id);
  }

  /**
   * Gets outlet oil concentration.
   *
   * @return oil concentration in mg/L
   */
  public double getOutletOilMgL() {
    return outletOilMgL;
  }

  /**
   * Gets pressure drop.
   *
   * @return pressure drop in bar
   */
  public double getPressureDropBar() {
    return pressureDrop;
  }

  /**
   * Sets pressure drop.
   *
   * @param dp pressure drop in bar
   */
  public void setPressureDropBar(double dp) {
    this.pressureDrop = dp;
  }
}
