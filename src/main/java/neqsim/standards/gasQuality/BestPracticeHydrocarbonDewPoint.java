package neqsim.standards.gasQuality;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * BestPracticeHydrocarbonDewPoint class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class BestPracticeHydrocarbonDewPoint extends neqsim.standards.Standard {
    String dewPointTemperatureUnit = "C", pressureUnit = "bar";
    double dewPointTemperature = 273.0, dewPointTemperatureSpec = -12.0;
    double specPressure = 50.0;
    double initTemperature = 273.15 - 20.0;
    SystemInterface thermoSystem;
    ThermodynamicOperations thermoOps;

    public BestPracticeHydrocarbonDewPoint() {
        super("StatoilBestPracticeHydrocarbonDewPoint", "hydrocarbon dew point calculation method");
    }

    /**
     * <p>
     * Constructor for BestPracticeHydrocarbonDewPoint.
     * </p>
     *
     * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
     */
    public BestPracticeHydrocarbonDewPoint(SystemInterface thermoSystem) {
        this();

        // System.out.println("setting model GERG water...");
        this.thermoSystem = new SystemSrkEos(initTemperature, specPressure);
        for (int i = 0; i < thermoSystem.getPhase(0).getNumberOfComponents(); i++) {
            if (!thermoSystem.getPhase(0).getComponent(i).getName().equals("water")) {
                this.thermoSystem.addComponent(thermoSystem.getPhase(0).getComponent(i).getName(),
                        thermoSystem.getPhase(0).getComponent(i).getNumberOfmoles());
            }
        }

        this.thermoSystem.setMixingRule(2);
        thermoSystem.init(0);
        thermoSystem.init(1);

        this.thermoOps = new ThermodynamicOperations(this.thermoSystem);
    }

    /** {@inheritDoc} */
    @Override
    public void calculate() {
        this.thermoSystem.setTemperature(initTemperature);
        this.thermoSystem.setPressure(specPressure);
        try {
            this.thermoOps.dewPointTemperatureFlash();
        } catch (Exception e) {
            e.printStackTrace();
        }
        dewPointTemperature = this.thermoSystem.getTemperature() - 273.15;
    }

    /** {@inheritDoc} */
    @Override
    public double getValue(String returnParameter, java.lang.String returnUnit) {
        if (returnParameter.equals("hydrocarbondewpointTemperature")) {
            return dewPointTemperature;
        } else {
            return dewPointTemperature;
        }
    }

    /** {@inheritDoc} */
    @Override
    public double getValue(String returnParameter) {
        if (returnParameter.equals("hydrocarbondewpointTemperature")) {
            return dewPointTemperature;
        }
        if (returnParameter.equals("pressure")) {
            return this.thermoSystem.getPressure();
        } else {
            return dewPointTemperature;
        }
    }

    /** {@inheritDoc} */
    @Override
    public String getUnit(String returnParameter) {
        if (returnParameter.equals("hydrocarbondewpointTemperature")) {
            return dewPointTemperatureUnit;
        }
        if (returnParameter.equals("pressureUnit")) {
            return this.pressureUnit;
        } else {
            return dewPointTemperatureUnit;
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isOnSpec() {
        return dewPointTemperature < getSalesContract().getWaterDewPointTemperature();
    }
}
