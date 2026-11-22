package neqsim.fluidmechanics.geometrydefinitions.internalgeometry.packings;

/**
 * RachigRingPacking class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class RachigRingPacking extends Packing {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Create a ceramic 25 mm Raschig ring using the qualified geometry table row.
   */
  public RachigRingPacking() {
    super("RachigRingPacking");
  }

  /**
   * Constructor for RachigRingPacking.
   *
   * @param material a {@link java.lang.String} object
   * @param size a int
   */
  public RachigRingPacking(String material, int size) {
    super("RachigRingPacking", material, size);
  }
}
