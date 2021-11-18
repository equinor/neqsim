package neqsim.standards.oilQuality;

import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author ESOL
 */
public class Standard_ASTM_D6377 extends neqsim.standards.Standard {

    private static final long serialVersionUID = 1000;
    String unit = "bara";
    double RVP = 1.0;

    public Standard_ASTM_D6377(SystemInterface thermoSystem) {
        super(thermoSystem);
    }

    @Override
    public void calculate() {
        this.thermoSystem.setTemperature(273.15 + 30.7);
        this.thermoSystem.setPressure(1.01325);

        try {
            this.thermoOps.bubblePointPressureFlash();
        } catch (Exception e) {
            e.printStackTrace();
        }
        RVP = this.thermoSystem.getPressure();

    }

    @Override
    public boolean isOnSpec() {
        return true;
    }

    @Override
    public String getUnit(String returnParameter) {
        return unit;
    }

    @Override
    public double getValue(String returnParameter, java.lang.String returnUnit) {
        return RVP;
    }

    @Override
    public double getValue(String returnParameter) {
        return RVP;
    }
}
