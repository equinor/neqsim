package neqsim.processSimulation.mechanicalDesign.designStandards;

import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;

/**
 * <p>DesignStandard class.</p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class DesignStandard implements java.io.Serializable {

    private static final long serialVersionUID = 1000;

    public MechanicalDesign equipment = null;
    public String standardName = "";

    /**
     * <p>Constructor for DesignStandard.</p>
     */
    public DesignStandard() {

    }

    /**
     * <p>Constructor for DesignStandard.</p>
     *
     * @param name a {@link java.lang.String} object
     * @param equipmentInn a {@link neqsim.processSimulation.mechanicalDesign.MechanicalDesign} object
     */
    public DesignStandard(String name, MechanicalDesign equipmentInn) {
        equipment = equipmentInn;
        standardName = name;
    }

    /**
     * <p>setDesignStandardName.</p>
     *
     * @param name a {@link java.lang.String} object
     */
    public void setDesignStandardName(String name) {
        setStandardName(name);
    }

    /**
     * <p>Getter for the field <code>equipment</code>.</p>
     *
     * @return the equipment
     */
    public MechanicalDesign getEquipment() {
        return equipment;
    }

    /**
     * <p>Setter for the field <code>equipment</code>.</p>
     *
     * @param equipment the equipment to set
     */
    public void setEquipment(MechanicalDesign equipment) {
        this.equipment = equipment;
    }

    /**
     * <p>Getter for the field <code>standardName</code>.</p>
     *
     * @return the standardName
     */
    public String getStandardName() {
        return standardName;
    }

    /**
     * <p>Setter for the field <code>standardName</code>.</p>
     *
     * @param standardName the standardName to set
     */
    public void setStandardName(String standardName) {
        this.standardName = standardName;
    }
}
