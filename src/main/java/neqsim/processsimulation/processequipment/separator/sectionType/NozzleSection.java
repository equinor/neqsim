package neqsim.processsimulation.processequipment.separator.sectionType;

import neqsim.processsimulation.mechanicaldesign.separator.sectionType.MechNozzleSection;
import neqsim.processsimulation.processequipment.separator.Separator;

/**
 * <p>
 * NozzleSection class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class NozzleSection extends SeparatorSection {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for NozzleSection.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param type a {@link java.lang.String} object
   * @param sep a {@link neqsim.processsimulation.processequipment.separator.Separator} object
   */
  public NozzleSection(String name, String type, Separator sep) {
    super(name, type, sep);
  }

  /** {@inheritDoc} */
  @Override
  public MechNozzleSection getMechanicalDesign() {
    return new MechNozzleSection(this);
  }

  /** {@inheritDoc} */
  @Override
  public double calcEfficiency() {
    return 1.0;
  }
}
