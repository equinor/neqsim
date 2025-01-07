package neqsim.process.equipment.separator.sectiontype;

import neqsim.process.equipment.separator.Separator;
import neqsim.process.mechanicaldesign.separator.sectiontype.MecMeshSection;

/**
 * <p>
 * MeshSection class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class MeshSection extends SeparatorSection {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for MeshSection.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param type a {@link java.lang.String} object
   * @param sep a {@link neqsim.process.equipment.separator.Separator} object
   */
  public MeshSection(String name, String type, Separator sep) {
    super(name, type, sep);
  }

  /** {@inheritDoc} */
  @Override
  public MecMeshSection getMechanicalDesign() {
    return new MecMeshSection(this);
  }

  /** {@inheritDoc} */
  @Override
  public double calcEfficiency() {
    double gasLoadF = getSeparator().getGasLoadFactor();
    if (gasLoadF > 0.1) {
      return 0.1 / gasLoadF;
    } else {
      return 1.0;
    }
  }
}
