/*
 * PallRingPacking.java
 *
 * Created on 25. august 2001, 23:58
 */

package neqsim.fluidmechanics.geometrydefinitions.internalgeometry.packings;

/**
 * PallRingPacking class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class PallRingPacking extends Packing {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for PallRingPacking.
   */
  public PallRingPacking() {
    super("PallRingPacking");
  }

  /**
   * Constructor for PallRingPacking.
   *
   * @param material a {@link java.lang.String} object
   * @param size a int
   */
  public PallRingPacking(String material, int size) {
    super("PallRingPacking", material, size);
  }
}
