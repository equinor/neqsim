/*
 * ThrottelValve.java
 *
 * Created on 22. august 2001, 17:20
 */
package neqsim.processSimulation.processEquipment.valve;

import neqsim.processSimulation.processEquipment.stream.Stream;

/**
 *
 * @author esol
 * @version
 */
public class SafetyValve extends ThrottlingValve {

    private static final long serialVersionUID = 1000;

    private double pressureSpec = 10.0;
    private double fullOpenPressure = 10.0;

    /**
     * Creates new ThrottelValve
     */
    public SafetyValve() {
        super();
    }

    public SafetyValve(Stream inletStream) {
        super(inletStream);
    }

    public SafetyValve(String name, Stream inletStream) {
        super(name, inletStream);
    }

    /**
     * @return the pressureSpec
     */
    public double getPressureSpec() {
        return pressureSpec;
    }

    /**
     * @param pressureSpec the pressureSpec to set
     */
    public void setPressureSpec(double pressureSpec) {
        this.pressureSpec = pressureSpec;
    }

    /**
     * @return the fullOpenPressure
     */
    public double getFullOpenPressure() {
        return fullOpenPressure;
    }

    /**
     * @param fullOpenPressure the fullOpenPressure to set
     */
    public void setFullOpenPressure(double fullOpenPressure) {
        this.fullOpenPressure = fullOpenPressure;
    }

}
