package neqsim.processSimulation.mechanicalDesign.designStandards;

import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;

/**
 *
 * @author esol
 */
public class JointEfficiencyPipelineStandard extends DesignStandard {
    private static final long serialVersionUID = 1000;

    public JointEfficiencyPipelineStandard(String name, MechanicalDesign equipmentInn) {
        super(name, equipmentInn);
    }

    /**
     * @return the JEFactor
     */
    public double getJEFactor() {
        return JEFactor;
    }

    /**
     * @param JEFactor the JEFactor to set
     */
    public void setJEFactor(double JEFactor) {
        this.JEFactor = JEFactor;
    }

    String typeName = "Double welded Butt joints";
    String radiagraphType = "Fully rediographed";
    private double JEFactor = 1.0;

    public void readJointEfficiencyStandard(String typeName, String radiagraphType) {
        // ... to be implemented
        JEFactor = 1.0;
    }
}
