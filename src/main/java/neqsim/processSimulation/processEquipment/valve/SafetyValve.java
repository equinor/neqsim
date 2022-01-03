/*
 * ThrottelValve.java
 *
 * Created on 22. august 2001, 17:20
 */
package neqsim.processSimulation.processEquipment.valve;

import neqsim.processSimulation.processEquipment.stream.Stream;

/**
 * <p>SafetyValve class.</p>
 *
 * @author esol
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

    /**
     * <p>Constructor for SafetyValve.</p>
     *
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     */
    public SafetyValve(Stream inletStream) {
        super(inletStream);
    }

    /**
     * <p>Constructor for SafetyValve.</p>
     *
     * @param name a {@link java.lang.String} object
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     */
    public SafetyValve(String name, Stream inletStream) {
        super(name, inletStream);
    }

    /**
     * <p>Getter for the field <code>pressureSpec</code>.</p>
     *
     * @return the pressureSpec
     */
    public double getPressureSpec() {
        return pressureSpec;
    }

    /**
     * <p>Setter for the field <code>pressureSpec</code>.</p>
     *
     * @param pressureSpec the pressureSpec to set
     */
    public void setPressureSpec(double pressureSpec) {
        this.pressureSpec = pressureSpec;
    }

    /**
     * <p>Getter for the field <code>fullOpenPressure</code>.</p>
     *
     * @return the fullOpenPressure
     */
    public double getFullOpenPressure() {
        return fullOpenPressure;
    }

    /**
     * <p>Setter for the field <code>fullOpenPressure</code>.</p>
     *
     * @param fullOpenPressure the fullOpenPressure to set
     */
    public void setFullOpenPressure(double fullOpenPressure) {
        this.fullOpenPressure = fullOpenPressure;
    }

}
