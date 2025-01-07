package neqsim.fluidmechanics.geometrydefinitions.internalgeometry.packings;

/**
 * <p>
 * RachigRingPacking class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class RachigRingPacking extends Packing {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

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
