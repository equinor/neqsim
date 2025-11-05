package neqsim.process.equipment.valve;

import neqsim.process.equipment.stream.StreamInterface;

/**
 * <p>
 * RuptureDisk class - represents a rupture disk (bursting disc) safety device.
 * </p>
 * 
 * <p>
 * A rupture disk is a non-reclosing pressure relief device that bursts at a set pressure and
 * remains fully open. Unlike a safety valve, a rupture disk is a one-time use device that cannot
 * reseat after activation.
 * </p>
 * 
 * <p>
 * Typical applications:
 * </p>
 * <ul>
 * <li>Primary relief for rapid pressure rise scenarios</li>
 * <li>Backup protection in series with safety valves</li>
 * <li>Protection for highly corrosive or fouling services</li>
 * <li>Emergency relief for runaway reactions</li>
 * </ul>
 *
 * @author esol
 * @version $Id: $Id
 */
public class RuptureDisk extends ThrottlingValve {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private double burstPressure = 10.0; // Pressure at which disk ruptures (bara)
  private double fullOpenPressure = 10.5; // Pressure at which disk is fully open (bara)
  private boolean hasRuptured = false; // Track if disk has ruptured (one-time event)

  /**
   * Constructor for RuptureDisk.
   *
   * @param name name of rupture disk
   */
  public RuptureDisk(String name) {
    super(name);
  }

  /**
   * <p>
   * Constructor for RuptureDisk.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.process.equipment.stream.Stream} object
   */
  public RuptureDisk(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /**
   * <p>
   * Getter for the field <code>burstPressure</code>.
   * </p>
   *
   * @return the burstPressure in bara
   */
  public double getBurstPressure() {
    return burstPressure;
  }

  /**
   * <p>
   * Setter for the field <code>burstPressure</code>.
   * </p>
   *
   * @param burstPressure the burstPressure to set in bara
   */
  public void setBurstPressure(double burstPressure) {
    this.burstPressure = burstPressure;
    // Auto-set full open pressure if not explicitly set (typically 5% above burst)
    if (fullOpenPressure <= burstPressure) {
      this.fullOpenPressure = burstPressure * 1.05;
    }
  }

  /**
   * <p>
   * Getter for the field <code>fullOpenPressure</code>.
   * </p>
   *
   * @return the fullOpenPressure in bara
   */
  public double getFullOpenPressure() {
    return fullOpenPressure;
  }

  /**
   * <p>
   * Setter for the field <code>fullOpenPressure</code>.
   * </p>
   *
   * @param fullOpenPressure the fullOpenPressure to set in bara
   */
  public void setFullOpenPressure(double fullOpenPressure) {
    this.fullOpenPressure = fullOpenPressure;
  }

  /**
   * Check if the rupture disk has ruptured.
   *
   * @return true if disk has ruptured, false otherwise
   */
  public boolean hasRuptured() {
    return hasRuptured;
  }

  /**
   * Reset the rupture disk to unruptured state (for simulation purposes only - in reality, the disk
   * must be physically replaced).
   */
  public void reset() {
    hasRuptured = false;
    setPercentValveOpening(0.0);
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt, java.util.UUID id) {
    // Automatically adjust valve opening based on inlet pressure
    if (!getCalculateSteadyState()) {
      double inletPressure = getInletStream().getPressure("bara");
      double opening;

      if (!hasRuptured) {
        // Disk has not ruptured yet - check if burst pressure is exceeded
        if (inletPressure < burstPressure) {
          // Below burst pressure - stay closed
          opening = 0.0;
        } else {
          // Burst pressure exceeded - disk ruptures
          hasRuptured = true;

          if (inletPressure >= fullOpenPressure) {
            // Fully open immediately
            opening = 100.0;
          } else {
            // Rapid opening between burst and full open pressure
            opening = 100.0 * (inletPressure - burstPressure) / (fullOpenPressure - burstPressure);
          }
        }
      } else {
        // Disk has already ruptured - remains fully open regardless of pressure
        // This is the key difference from a safety valve
        opening = 100.0;
      }

      // Set the calculated opening
      setPercentValveOpening(opening);
    }

    // Call parent runTransient to perform the actual valve calculations
    super.runTransient(dt, id);
  }

  /** {@inheritDoc} */
  @Override
  public void run(java.util.UUID id) {
    // For steady-state runs, check if burst pressure is exceeded
    if (getCalculateSteadyState()) {
      double inletPressure = getInletStream().getPressure("bara");

      if (!hasRuptured && inletPressure >= burstPressure) {
        hasRuptured = true;
        setPercentValveOpening(100.0);
      } else if (hasRuptured) {
        setPercentValveOpening(100.0);
      } else {
        setPercentValveOpening(0.0);
      }
    }

    super.run(id);
  }
}
