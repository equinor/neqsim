package neqsim.processSimulation.mechanicalDesign.designStandards;

import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;

/**
 * <p>
 * ValveDesignStandard class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class ValveDesignStandard extends DesignStandard {
    private static final long serialVersionUID = 1000;

    public double valveCvMax = 1.0;

    /**
     * <p>
     * Getter for the field <code>valveCvMax</code>.
     * </p>
     *
     * @return a double
     */
    public double getValveCvMax() {
        return valveCvMax;
    }

    /**
     * <p>
     * Constructor for ValveDesignStandard.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @param equipmentInn a {@link neqsim.processSimulation.mechanicalDesign.MechanicalDesign}
     *        object
     */
    public ValveDesignStandard(String name, MechanicalDesign equipmentInn) {
        super(name, equipmentInn);
    }
}
