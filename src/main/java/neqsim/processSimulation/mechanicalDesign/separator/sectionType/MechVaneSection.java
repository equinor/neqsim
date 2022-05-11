package neqsim.processSimulation.mechanicalDesign.separator.sectionType;

import neqsim.processSimulation.processEquipment.separator.sectionType.SeparatorSection;

/**
 * <p>
 * MechVaneSection class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class MechVaneSection extends SepDesignSection {
    /**
     * <p>
     * Constructor for MechVaneSection.
     * </p>
     *
     * @param separatorSection a
     *        {@link neqsim.processSimulation.processEquipment.separator.sectionType.SeparatorSection}
     *        object
     */
    public MechVaneSection(SeparatorSection separatorSection) {
        super(separatorSection);
    }

    /** {@inheritDoc} */
    @Override
    public void calcDesign() {
        double vesselDiameter =
                separatorSection.getSeparator().getMechanicalDesign().getOuterDiameter() * 1e3;
        if (vesselDiameter <= 616) {
            totalWeight = 6.0;
        } else if (vesselDiameter <= 770) {
            totalWeight = 8.0;
        } else if (vesselDiameter <= 925) {
            totalWeight = 10.0;
        } else if (vesselDiameter <= 1078) {
            totalWeight = 13.0;
        } else if (vesselDiameter <= 1232) {
            totalWeight = 15.0;
        } else if (vesselDiameter <= 1386) {
            totalWeight = 18.0;
        } else if (vesselDiameter <= 1540) {
            totalWeight = 21.0;
        } else if (vesselDiameter <= 1694) {
            totalWeight = 25.0;
        } else if (vesselDiameter <= 1848) {
            totalWeight = 27.0;
        } else if (vesselDiameter <= 2002) {
            totalWeight = 31.0;
        } else if (vesselDiameter <= 2156) {
            totalWeight = 34.0;
        } else if (vesselDiameter <= 2310) {
            totalWeight = 38.0;
        } else if (vesselDiameter <= 2464) {
            totalWeight = 42.0;
        } else if (vesselDiameter <= 2618) {
            totalWeight = 47.0;
        } else if (vesselDiameter <= 2772) {
            totalWeight = 53.0;
        } else if (vesselDiameter <= 2926) {
            totalWeight = 57.0;
        } else if (vesselDiameter <= 3080) {
            totalWeight = 62.0;
        } else if (vesselDiameter <= 3234) {
            totalWeight = 65.0;
        } else {
            totalWeight = 70.0;
        }

        totalHeight = 0.6;
    }
}
