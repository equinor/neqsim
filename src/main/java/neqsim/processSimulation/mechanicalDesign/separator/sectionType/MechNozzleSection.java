package neqsim.processSimulation.mechanicalDesign.separator.sectionType;

import neqsim.processSimulation.processEquipment.separator.sectionType.SeparatorSection;

/**
 *
 * @author esol
 */
public class MechNozzleSection extends SepDesignSection {
    private static final long serialVersionUID = 1000;

    public MechNozzleSection(SeparatorSection separatorSection) {
        super(separatorSection);
        nominalSize = "DN 150";
    }

    @Override
    public void calcDesign() {
        if (nominalSize.equals("DN 50")) {
            if (getANSIclass() == 150) {
                totalWeight = 4;
            }
            if (getANSIclass() == 300) {
                totalWeight = 5;
            }
            if (getANSIclass() == 600) {
                totalWeight = 7;
            }
            if (getANSIclass() == 600) {
                totalWeight = 13;
            }

            totalHeight = 0.05;
        } else if (nominalSize.equals("DN 100")) {
            if (getANSIclass() == 150) {
                totalWeight = 11;
            }
            if (getANSIclass() == 300) {
                totalWeight = 18;
            }
            if (getANSIclass() == 600) {
                totalWeight = 27;
            }
            if (getANSIclass() == 600) {
                totalWeight = 34;
            }

            totalHeight = 0.05;
        } else { // DN 400
            if (getANSIclass() == 150) {
                totalWeight = 97;
            }
            if (getANSIclass() == 300) {
                totalWeight = 168;
            }
            if (getANSIclass() == 600) {
                totalWeight = 315;
            }
            if (getANSIclass() == 600) {
                totalWeight = 437;
            }

            totalHeight = 0.05;
        }
    }
}
