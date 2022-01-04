package neqsim.processSimulation.mechanicalDesign.designStandards;

import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;

/**
 * <p>
 * JointEfficiencyPipelineStandard class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class JointEfficiencyPipelineStandard extends DesignStandard {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for JointEfficiencyPipelineStandard.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @param equipmentInn a {@link neqsim.processSimulation.mechanicalDesign.MechanicalDesign}
     *        object
     */
    public JointEfficiencyPipelineStandard(String name, MechanicalDesign equipmentInn) {
        super(name, equipmentInn);
    }

    /**
     * <p>
     * getJEFactor.
     * </p>
     *
     * @return the JEFactor
     */
    public double getJEFactor() {
        return JEFactor;
    }

    /**
     * <p>
     * setJEFactor.
     * </p>
     *
     * @param JEFactor the JEFactor to set
     */
    public void setJEFactor(double JEFactor) {
        this.JEFactor = JEFactor;
    }

    String typeName = "Double welded Butt joints";
    String radiagraphType = "Fully rediographed";
    private double JEFactor = 1.0;

    /**
     * <p>
     * readJointEfficiencyStandard.
     * </p>
     *
     * @param typeName a {@link java.lang.String} object
     * @param radiagraphType a {@link java.lang.String} object
     */
    public void readJointEfficiencyStandard(String typeName, String radiagraphType) {
        // ... to be implemented
        JEFactor = 1.0;
    }
}
