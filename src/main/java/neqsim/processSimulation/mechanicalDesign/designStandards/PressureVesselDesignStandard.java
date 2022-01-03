package neqsim.processSimulation.mechanicalDesign.designStandards;

import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;
import neqsim.processSimulation.processEquipment.separator.Separator;

/**
 * <p>PressureVesselDesignStandard class.</p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class PressureVesselDesignStandard extends DesignStandard {

    private static final long serialVersionUID = 1000;

    /**
     * <p>Constructor for PressureVesselDesignStandard.</p>
     *
     * @param name a {@link java.lang.String} object
     * @param equipmentInn a {@link neqsim.processSimulation.mechanicalDesign.MechanicalDesign} object
     */
    public PressureVesselDesignStandard(String name, MechanicalDesign equipmentInn) {
        super(name, equipmentInn);
    }

    /**
     * <p>calcWallThickness.</p>
     *
     * @return a double
     */
    public double calcWallThickness() {
        Separator separator = (Separator) equipment.getProcessEquipment();
        double wallT = 0;
        MaterialPlateDesignStandard matPlateStyandard = ((MaterialPlateDesignStandard) equipment.getDesignStandard()
                .get("material plate design codes"));
        JointEfficiencyPlateStandard JEPlateStyandard = ((JointEfficiencyPlateStandard) equipment.getDesignStandard()
                .get("plate Joint Efficiency design codes"));
        double maxAllowableStress = matPlateStyandard.getDivisionClass();
        double jointEfficiency = JEPlateStyandard.getJEFactor();

        if (standardName.equals("ASME - Pressure Vessel Code")) {
            wallT = equipment.getMaxOperationPressure() / 10.0 * separator.getInternalDiameter() * 1e3
                    / (2.0 * maxAllowableStress * jointEfficiency - 1.2 * equipment.getMaxOperationPressure() / 10.0)
                    + equipment.getCorrosionAllowanse();
        } else if (standardName.equals("BS 5500 - Pressure Vessel")) {
            wallT = equipment.getMaxOperationPressure() / 10.0 * separator.getInternalDiameter() * 1e3
                    / (2.0 * maxAllowableStress - jointEfficiency / 10.0) + equipment.getCorrosionAllowanse();
        } else if (standardName.equals("European Code")) {
            wallT = equipment.getMaxOperationPressure() / 10.0 * separator.getInternalDiameter() / 2.0 * 1e3
                    / (2.0 * maxAllowableStress * jointEfficiency - 0.2 * equipment.getMaxOperationPressure() / 10.0)
                    + equipment.getCorrosionAllowanse();
        } else {
            wallT = equipment.getMaxOperationPressure() / 10.0 * separator.getInternalDiameter() / 2.0 * 1e3
                    / (2.0 * maxAllowableStress * jointEfficiency - 0.2 * equipment.getMaxOperationPressure() / 10.0)
                    + equipment.getCorrosionAllowanse();
        }

        return wallT / 1000.0; // return wall thcikness in meter
    }
}
