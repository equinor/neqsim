package neqsim.fluidMechanics.geometryDefinitions.internalGeometry.packings;

/**
 * <p>
 * RachigRingPacking class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class RachigRingPacking extends Packing {
  private static final long serialVersionUID = 1L;

  /**
   * <p>
   * Constructor for RachigRingPacking.
   * </p>
   */
  public RachigRingPacking() {
    super("RachigRingPacking");
  }

  /**
   * <p>
   * Constructor for RachigRingPacking.
   * </p>
   *
   * @param material a {@link java.lang.String} object
   * @param size a int
   */
  public RachigRingPacking(String material, int size) {
    super("RachigRingPacking", material, size);
  }
}
