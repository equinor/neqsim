/*
 * PallRingPacking.java
 *
 * Created on 25. august 2001, 23:58
 */

package neqsim.fluidMechanics.geometryDefinitions.internalGeometry.packings;

/**
 * <p>
 * PallRingPacking class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class PallRingPacking extends Packing {
  private static final long serialVersionUID = 1L;

  /**
   * <p>
   * Constructor for PallRingPacking.
   * </p>
   */
  public PallRingPacking() {
    super("PallRingPacking");
  }

  /**
   * <p>
   * Constructor for PallRingPacking.
   * </p>
   *
   * @param material a {@link java.lang.String} object
   * @param size a int
   */
  public PallRingPacking(String material, int size) {
    super("PallRingPacking", material, size);
  }
}
