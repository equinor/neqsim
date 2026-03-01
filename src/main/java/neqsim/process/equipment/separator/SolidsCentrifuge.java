package neqsim.process.equipment.separator;

import neqsim.process.equipment.stream.StreamInterface;

/**
 * Solids centrifuge for bio-processing solid-liquid separation.
 *
 * <p>
 * Centrifugal separation of solids from liquids. Typically used for cell mass recovery, protein
 * precipitation, and other bio-slurry separations. Higher energy consumption but better separation
 * efficiency than gravity-based methods.
 * </p>
 *
 * <p>
 * Typical parameters:
 * </p>
 * <ul>
 * <li>Solids recovery: 95-99.5%</li>
 * <li>Cake moisture: 30-60%</li>
 * <li>Specific energy: 3-10 kWh/m3</li>
 * <li>G-force: 1000-10000 g</li>
 * </ul>
 *
 * @author NeqSim team
 * @version 1.0
 */
public class SolidsCentrifuge extends SolidsSeparator {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** G-force (relative centrifugal force). */
  private double gForce = 3000.0;

  /**
   * Constructor for SolidsCentrifuge.
   *
   * @param name name of the centrifuge
   */
  public SolidsCentrifuge(String name) {
    super(name);
    this.equipmentType = "SolidsCentrifuge";
    setSpecificEnergy(5.0);
    setMoistureContent(0.40);
  }

  /**
   * Constructor for SolidsCentrifuge with inlet stream.
   *
   * @param name name of the centrifuge
   * @param inletStream the feed stream
   */
  public SolidsCentrifuge(String name, StreamInterface inletStream) {
    super(name, inletStream);
    this.equipmentType = "SolidsCentrifuge";
    setSpecificEnergy(5.0);
    setMoistureContent(0.40);
  }

  /**
   * Set the relative centrifugal force (G-force).
   *
   * @param gForce G-force value
   */
  public void setGForce(double gForce) {
    this.gForce = gForce;
  }

  /**
   * Get the G-force.
   *
   * @return G-force value
   */
  public double getGForce() {
    return gForce;
  }
}
