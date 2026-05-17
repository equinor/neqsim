package neqsim.process.equipment.separator;

import neqsim.process.equipment.stream.StreamInterface;

/**
 * Screw press for bio-processing dewatering.
 *
 * <p>
 * A mechanical press that uses a rotating screw to compress biomass or slurry, squeezing out
 * liquid. Commonly used for fiber dewatering, press-mud removal in sugar mills, and biosolids
 * dewatering in wastewater treatment.
 * </p>
 *
 * <p>
 * Typical parameters:
 * </p>
 * <ul>
 * <li>Solids recovery: 85-95%</li>
 * <li>Cake moisture: 55-75%</li>
 * <li>Specific energy: 0.5-2 kWh/m3 (very low energy)</li>
 * </ul>
 *
 * @author NeqSim team
 * @version 1.0
 */
public class ScrewPress extends SolidsSeparator {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Screw speed in RPM. */
  private double screwSpeed = 5.0;

  /** Compression ratio. */
  private double compressionRatio = 3.0;

  /**
   * Constructor for ScrewPress.
   *
   * @param name name of the screw press
   */
  public ScrewPress(String name) {
    super(name);
    this.equipmentType = "ScrewPress";
    setSpecificEnergy(1.0);
    setMoistureContent(0.65);
  }

  /**
   * Constructor for ScrewPress with inlet stream.
   *
   * @param name name of the screw press
   * @param inletStream the feed stream
   */
  public ScrewPress(String name, StreamInterface inletStream) {
    super(name, inletStream);
    this.equipmentType = "ScrewPress";
    setSpecificEnergy(1.0);
    setMoistureContent(0.65);
  }

  /**
   * Set the screw speed.
   *
   * @param rpm screw speed in RPM
   */
  public void setScrewSpeed(double rpm) {
    this.screwSpeed = rpm;
  }

  /**
   * Get the screw speed.
   *
   * @return speed in RPM
   */
  public double getScrewSpeed() {
    return screwSpeed;
  }

  /**
   * Set the compression ratio.
   *
   * @param ratio compression ratio
   */
  public void setCompressionRatio(double ratio) {
    this.compressionRatio = ratio;
  }

  /**
   * Get the compression ratio.
   *
   * @return compression ratio
   */
  public double getCompressionRatio() {
    return compressionRatio;
  }
}
