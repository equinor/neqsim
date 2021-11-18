/*
 * Standard_ISO1992.java
 *
 * Created on 13. juni 2004, 23:30
 */

package neqsim.standards.gasQuality;

import neqsim.thermo.system.SystemGERGwaterEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author ESOL
 */
public class Draft_ISO18453 extends neqsim.standards.Standard {
    private static final long serialVersionUID = 1000;
    String dewPointTemperatureUnit = "C", pressureUnit = "bar";
    double dewPointTemperature = 273.0, dewPointTemperatureSpec = -12.0;
    double specPressure = 70.0;
    double initTemperature = 273.15;
    SystemInterface thermoSystem;
    ThermodynamicOperations thermoOps;

    /** Creates a new instance of Standard_ISO1992 */
    public Draft_ISO18453() {
        name = "Draft_ISO18453";
        standardDescription = "water dew point calculation method";
    }

    public Draft_ISO18453(SystemInterface thermoSystem) {
        this();

        if (thermoSystem.getModelName().equals("GERGwater")) {
            this.thermoSystem = thermoSystem;
        } else {
            System.out.println("setting model GERG water...");
            this.thermoSystem = new SystemGERGwaterEos(initTemperature, specPressure);
            for (int i = 0; i < thermoSystem.getPhase(0).getNumberOfComponents(); i++) {
                this.thermoSystem.addComponent(thermoSystem.getPhase(0).getComponent(i).getName(),
                        thermoSystem.getPhase(0).getComponent(i).getNumberOfmoles());
            }
        }

        this.thermoSystem.setTemperature(273.15);
        this.thermoSystem.setPressure(1.0);
        this.thermoSystem.setMixingRule(8);
        thermoSystem.init(0);
        thermoSystem.init(1);

        this.thermoOps = new ThermodynamicOperations(this.thermoSystem);
    }

    @Override
    public void calculate() {
        this.thermoSystem.setTemperature(initTemperature);
        this.thermoSystem.setPressure(specPressure);

        try {
            this.thermoOps.waterDewPointTemperatureFlash();
        } catch (Exception e) {
            e.printStackTrace();
        }
        dewPointTemperature = this.thermoSystem.getTemperature() - 273.15;
    }

    @Override
    public double getValue(String returnParameter, java.lang.String returnUnit) {
        if (returnParameter.equals("dewPointTemperature")) {
            return dewPointTemperature;
        } else {
            return dewPointTemperature;
        }
    }

    @Override
    public double getValue(String returnParameter) {
        if (returnParameter.equals("dewPointTemperature")) {
            return dewPointTemperature;
        }
        if (returnParameter.equals("pressure")) {
            return this.thermoSystem.getPressure();
        } else {
            return dewPointTemperature;
        }
    }

    @Override
    public String getUnit(String returnParameter) {
        if (returnParameter.equals("dewPointTemperature")) {
            return dewPointTemperatureUnit;
        }
        if (returnParameter.equals("pressureUnit")) {
            return this.pressureUnit;
        } else {
            return dewPointTemperatureUnit;
        }
    }

    @Override
    public boolean isOnSpec() {
        return dewPointTemperature < getSalesContract().getWaterDewPointTemperature();
    }
}
