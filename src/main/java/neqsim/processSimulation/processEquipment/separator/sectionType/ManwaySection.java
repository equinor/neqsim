package neqsim.processSimulation.processEquipment.separator.sectionType;

import neqsim.processSimulation.mechanicalDesign.separator.sectionType.MechManwaySection;
import neqsim.processSimulation.processEquipment.separator.Separator;

/**
 * <p>
 * ManwaySection class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class ManwaySection extends SeparatorSection {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for ManwaySection.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param type a {@link java.lang.String} object
   * @param sep a {@link neqsim.processSimulation.processEquipment.separator.Separator} object
   */
  public ManwaySection(String name, String type, Separator sep) {
    super(name, type, sep);
  }

  /** {@inheritDoc} */
  @Override
  public MechManwaySection getMechanicalDesign() {
    return new MechManwaySection(this);
  }

  /** {@inheritDoc} */
  @Override
  public double calcEfficiency() {
    return 1.0;
  }
}
