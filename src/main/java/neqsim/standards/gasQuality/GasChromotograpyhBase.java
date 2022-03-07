package neqsim.standards.gasQuality;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * GasChromotograpyhBase class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class GasChromotograpyhBase extends neqsim.standards.Standard {
    String componentName = "", unit = "mol%";

    public GasChromotograpyhBase() {
        super("gas cromotography");
        standardDescription = "Gas composition";
    }

    /**
     * <p>
     * Constructor for GasChromotograpyhBase.
     * </p>
     *
     * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
     * @param component a {@link java.lang.String} object
     */
    public GasChromotograpyhBase(SystemInterface thermoSystem, String component) {
        super("gas cromotography", thermoSystem);
        this.componentName = component;
    }

    /** {@inheritDoc} */
    @Override
    public void calculate() {
        thermoSystem.init(0);
        thermoSystem.init(0);
    }

    /** {@inheritDoc} */
    @Override
    public double getValue(String returnParameter, java.lang.String returnUnit) {
        unit = returnUnit;
        if (returnUnit.equals("mol%")) {
            return 100 * thermoSystem.getPhase(0).getComponent(componentName).getz();
        }
        if (returnUnit.equals("mg/m3")) {
            return thermoSystem.getPhase(0).getComponent(componentName).getz() * 1.0e6;
        } else {
            return thermoSystem.getPhase(0).getComponent(componentName).getz();
        }
    }

    /** {@inheritDoc} */
    @Override
    public double getValue(String returnParameter) {
        return thermoSystem.getPhase(0).getComponent(componentName).getz();
    }

    /** {@inheritDoc} */
    @Override
    public String getUnit(String returnParameter) {
        return unit;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isOnSpec() {
        return true;
    }
}
