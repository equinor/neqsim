package neqsim.processSimulation.mechanicalDesign.designStandards;

import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;

/**
 *
 * @author esol
 */
public class ValveDesignStandard extends DesignStandard {
    private static final long serialVersionUID = 1000;

    public double valveCvMax = 1.0;

    public double getValveCvMax() {
        return valveCvMax;
    }

    public ValveDesignStandard(String name, MechanicalDesign equipmentInn) {
        super(name, equipmentInn);
    }
}
