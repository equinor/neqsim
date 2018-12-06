/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.processSimulation.mechanicalDesign.designStandards;

import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;

/**
 *
 * @author esol
 */
public class DesignStandard {

    private static final long serialVersionUID = 1000;

    public MechanicalDesign equipment = null;
    public String standardName = "";

    public DesignStandard() {

    }

    public DesignStandard(String name, MechanicalDesign equipmentInn) {
        equipment = equipmentInn;
        standardName = name;
    }

    public void setDesignStandardName(String name) {
        setStandardName(name);
    }

    /**
     * @return the equipment
     */
    public MechanicalDesign getEquipment() {
        return equipment;
    }

    /**
     * @param equipment the equipment to set
     */
    public void setEquipment(MechanicalDesign equipment) {
        this.equipment = equipment;
    }

    /**
     * @return the standardName
     */
    public String getStandardName() {
        return standardName;
    }

    /**
     * @param standardName the standardName to set
     */
    public void setStandardName(String standardName) {
        this.standardName = standardName;
    }
}
