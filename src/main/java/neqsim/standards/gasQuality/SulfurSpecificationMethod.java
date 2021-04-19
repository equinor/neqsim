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
public class SulfurSpecificationMethod extends neqsim.standards.Standard {

    private static final long serialVersionUID = 1000;

    SystemInterface thermoSystem;
    String unit = "ppm";
    double H2Scontent = 0.0;

    public SulfurSpecificationMethod() {
        name = "SulfurSpecificationMethod";
        standardDescription = "SulfurSpecificationMethod";
    }

    public SulfurSpecificationMethod(SystemInterface thermoSystem) {
        this();
        this.thermoSystem = thermoSystem;
    }

    @Override
	public void calculate() {
        thermoSystem.init(0);
    }

    @Override
	public double getValue(String returnParameter, java.lang.String returnUnit) {
        thermoSystem.init(0);
        if (thermoSystem.getPhase(0).hasComponent("H2S")) {
            if (returnParameter.equals("H2S content")) {
                H2Scontent = thermoSystem.getPhase(0).getComponent("H2S").getx() * 1e6;
                return H2Scontent;
            }
        }
        return 0.0;
    }

    @Override
	public double getValue(String returnParameter) {
        return getValue(returnParameter, "");
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
