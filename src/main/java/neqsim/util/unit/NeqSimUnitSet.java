package neqsim.util.unit;

/**
 *
 * @author ESOL
 */
public class NeqSimUnitSet {

    /**
     * @return the componentConcentrationUnit
     */
    public String getComponentConcentrationUnit() {
        return componentConcentrationUnit;
    }

    /**
     * @param componentConcentrationUnit the componentConcentrationUnit to set
     */
    public void setComponentConcentrationUnit(String componentConcentrationUnit) {
        this.componentConcentrationUnit = componentConcentrationUnit;
    }

    private static final long serialVersionUID = 1000;

    /**
     * @return the flowRateUnit
     */
    public String getFlowRateUnit() {
        return flowRateUnit;
    }

    /**
     * @param flowRateUnit the flowRateUnit to set
     */
    public void setFlowRateUnit(String flowRateUnit) {
        this.flowRateUnit = flowRateUnit;
    }

    /**
     * @return the pressureUnit
     */
    public String getPressureUnit() {
        return pressureUnit;
    }

    /**
     * @param pressureUnit the pressureUnit to set
     */
    public void setPressureUnit(String pressureUnit) {
        this.pressureUnit = pressureUnit;
    }

    /**
     * @return the temperatureUnit
     */
    public String getTemperatureUnit() {
        return temperatureUnit;
    }

    /**
     * @param temperatureUnit the temperatureUnit to set
     */
    public void setTemperatureUnit(String temperatureUnit) {
        this.temperatureUnit = temperatureUnit;
    }

    private String temperatureUnit = "K";
    private String pressureUnit = "bara";
    private String flowRateUnit = "mol/sec";
    private String componentConcentrationUnit = "molefraction";

}
