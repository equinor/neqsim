package neqsim.process.mechanicaldesign.separator.sectiontype;

import neqsim.process.equipment.separator.sectiontype.SeparatorSection;

/**
 * <p>
 * MechManwaySection class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class MechManwaySection extends SepDesignSection {
  /**
   * <p>
   * Constructor for MechManwaySection.
   * </p>
   *
   * @param separatorSection a
   *                         {@link neqsim.process.equipment.separator.sectiontype.SeparatorSection}
   *                         object
   */
  public MechManwaySection(SeparatorSection separatorSection) {
    super(separatorSection);
    nominalSize = "DN 500";
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    if (nominalSize.equals("DN 500")) {
      if (getANSIclass() == 150) {
        totalWeight = 317;
      }
      if (getANSIclass() == 300) {
        totalWeight = 544;
      }
      if (getANSIclass() == 600) {
        totalWeight = 952;
      }
      if (getANSIclass() == 600) {
        totalWeight = 900;
      }

      totalHeight = 0.5;
    } else {
      if (separatorSection.getOuterDiameter() > 0.616) {
        totalWeight = 500.0;
      } else {
        totalWeight = 200.0;
      }
      totalHeight = 0.6;
    }
  }
}
