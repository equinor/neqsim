package neqsim.process.mechanicaldesign.separator.sectiontype;

import neqsim.process.equipment.separator.sectiontype.SeparatorSection;

/**
 * <p>
 * DistillationTraySection class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class DistillationTraySection extends SepDesignSection {
  /**
   * <p>
   * Constructor for DistillationTraySection.
   * </p>
   *
   * @param separatorSection a
   *        {@link neqsim.process.equipment.separator.sectiontype.SeparatorSection}
   *        object
   */
  public DistillationTraySection(SeparatorSection separatorSection) {
    super(separatorSection);
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    double vesselDiameter = separatorSection.getOuterDiameter() * 1e3;
    if (vesselDiameter <= 616) {
      totalWeight = 32.0;
    } else if (vesselDiameter <= 770) {
      totalWeight = 48.0;
    } else if (vesselDiameter <= 925) {
      totalWeight = 73.0;
    } else if (vesselDiameter <= 1078) {
      totalWeight = 95.0;
    } else if (vesselDiameter <= 1232) {
      totalWeight = 127.0;
    } else if (vesselDiameter <= 1386) {
      totalWeight = 159.0;
    } else if (vesselDiameter <= 1540) {
      totalWeight = 200.0;
    } else if (vesselDiameter <= 1694) {
      totalWeight = 236.0;
    } else if (vesselDiameter <= 1848) {
      totalWeight = 284.0;
    } else if (vesselDiameter <= 2002) {
      totalWeight = 331.0;
    } else if (vesselDiameter <= 2156) {
      totalWeight = 386.0;
    } else if (vesselDiameter <= 2310) {
      totalWeight = 440.0;
    } else if (vesselDiameter <= 2464) {
      totalWeight = 504.0;
    } else if (vesselDiameter <= 2618) {
      totalWeight = 563.0;
    } else if (vesselDiameter <= 2772) {
      totalWeight = 635.0;
    } else if (vesselDiameter <= 2926) {
      totalWeight = 703.0;
    } else if (vesselDiameter <= 3080) {
      totalWeight = 794.0;
    } else if (vesselDiameter <= 3234) {
      totalWeight = 862.0;
    } else {
      totalWeight = 900.0;
    }

    totalHeight = 0.6;
  }
}
