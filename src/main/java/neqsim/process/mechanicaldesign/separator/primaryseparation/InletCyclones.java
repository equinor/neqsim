package neqsim.process.mechanicaldesign.separator.primaryseparation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Inlet cyclone device for primary separation in a separator vessel.
 *
 * <p>
 * Inlet cyclones use centrifugal force to separate bulk liquid from the gas stream at the inlet.
 * They handle the highest inlet momentum of all inlet device types (up to 8000 Pa rho*v^2) and
 * produce a more uniform gas distribution downstream. Multiple cyclone tubes are typically arranged
 * in a cluster.
 * </p>
 *
 * <p>
 * Inlet cyclones are preferred for high gas-to-liquid ratio applications (gas scrubbers, gas
 * production separators) where the liquid load is moderate.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class InletCyclones extends PrimarySeparation {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(InletCyclones.class);

  /** Number of cyclone tubes in the inlet cluster. */
  private int numberOfCyclones = 4;

  /** Individual cyclone tube diameter [m]. */
  private double cycloneDiameter = 0.10;

  /**
   * Constructs an InletCyclones device with default parameters.
   */
  public InletCyclones() {
    super();
    setMaxInletMomentum(8000.0);
    setBulkSeparationEfficiency(0.95);
  }

  /**
   * Constructs an InletCyclones device with a name.
   *
   * @param name the name of this inlet cyclone device
   */
  public InletCyclones(String name) {
    super(name);
    setMaxInletMomentum(8000.0);
    setBulkSeparationEfficiency(0.95);
  }

  /**
   * Gets the number of cyclone tubes.
   *
   * @return number of cyclone tubes
   */
  public int getNumberOfCyclones() {
    return numberOfCyclones;
  }

  /**
   * Sets the number of cyclone tubes.
   *
   * @param numberOfCyclones number of cyclone tubes
   */
  public void setNumberOfCyclones(int numberOfCyclones) {
    this.numberOfCyclones = numberOfCyclones;
  }

  /**
   * Gets the individual cyclone tube diameter.
   *
   * @return cyclone diameter [m]
   */
  public double getCycloneDiameter() {
    return cycloneDiameter;
  }

  /**
   * Sets the individual cyclone tube diameter.
   *
   * @param cycloneDiameter cyclone diameter [m]
   */
  public void setCycloneDiameter(double cycloneDiameter) {
    this.cycloneDiameter = cycloneDiameter;
  }
}
