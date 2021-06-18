package neqsim.processSimulation.mechanicalDesign.separator.sectionType;

import neqsim.processSimulation.processEquipment.separator.sectionType.SeparatorSection;

/**
 *
 * @author esol
 */
public class MecMeshSection extends SepDesignSection {

    private static final long serialVersionUID = 1000;

    public MecMeshSection(SeparatorSection separatorSection) {
        super(separatorSection);
    }

    @Override
	public void calcDesign() {
        double vesselDiameter = separatorSection.getSeparator().getMechanicalDesign().getOuterDiameter() * 1e3;
        if (vesselDiameter <= 616) {
            totalWeight = 5.0;
        } else if (vesselDiameter <= 770) {
            totalWeight = 7.0;
        } else if (vesselDiameter <= 925) {
            totalWeight = 9.0;
        } else if (vesselDiameter <= 1078) {
            totalWeight = 10.0;
        } else if (vesselDiameter <= 1232) {
            totalWeight = 12.0;
        } else if (vesselDiameter <= 1386) {
            totalWeight = 15.0;
        } else if (vesselDiameter <= 1540) {
            totalWeight = 16.0;
        } else if (vesselDiameter <= 1694) {
            totalWeight = 16.0;
        } else if (vesselDiameter <= 1848) {
            totalWeight = 21.0;
        } else if (vesselDiameter <= 2002) {
            totalWeight = 23.0;
        } else if (vesselDiameter <= 2156) {
            totalWeight = 25.0;
        } else if (vesselDiameter <= 2310) {
            totalWeight = 28.0;
        } else if (vesselDiameter <= 2464) {
            totalWeight = 31.0;
        } else if (vesselDiameter <= 2618) {
            totalWeight = 34.0;
        } else if (vesselDiameter <= 2772) {
            totalWeight = 36.0;
        } else if (vesselDiameter <= 2926) {
            totalWeight = 39.0;
        } else if (vesselDiameter <= 3080) {
            totalWeight = 42.0;
        } else if (vesselDiameter <= 3234) {
            totalWeight = 45.0;
        } else {
            totalWeight = 50.0;
        }

        totalHeight = 0.6;
    }
}
