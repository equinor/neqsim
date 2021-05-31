/*
 * Standard_ISO1992.java
 *
 * Created on 13. juni 2004, 23:30
 */
package neqsim.standards.gasQuality;

import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author ESOL
 */
public class GasChromotograpyhBase extends neqsim.standards.Standard {

    private static final long serialVersionUID = 1000;

    String componentName = "", unit = "mol%";

    /** Creates a new instance of Standard_ISO1992 */
    public GasChromotograpyhBase() {
        standardDescription = "Gas composition";
        name = "gas cromotography";
    }

    public GasChromotograpyhBase(SystemInterface thermoSystem, String component) {
        super(thermoSystem);
        this.componentName = component;
        name = "gas cromotography";
    }

    @Override
	public void calculate() {
        thermoSystem.init(0);
        thermoSystem.init(0);

    }

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

    @Override
	public double getValue(String returnParameter) {
        return thermoSystem.getPhase(0).getComponent(componentName).getz();
    }

    @Override
	public String getUnit(String returnParameter) {
        return unit;
    }

    @Override
	public boolean isOnSpec() {
        return true;
    }
}
