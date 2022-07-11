package neqsim.processSimulation.processEquipment.valve;

import neqsim.processSimulation.processEquipment.stream.StreamInterface;

/**
 * <p>
 * SafetyValve class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class SafetyValve extends ThrottlingValve {
    private static final long serialVersionUID = 1000;

    private double pressureSpec = 10.0;
    private double fullOpenPressure = 10.0;

    /**
     * <p>
     * Constructor for SafetyValve.
     * </p>
     */
    @Deprecated
    public SafetyValve() {
        this("SafetyValve");
    }

    /**
     * <p>
     * Constructor for SafetyValve.
     * </p>
     *
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    @Deprecated
    public SafetyValve(StreamInterface inletStream) {
        this("SafetyValve", inletStream);
    }

    /**
     * Constructor for SafetyValve.
     * 
     * @param name name of valve
     */
    public SafetyValve(String name) {
        super(name);
    }

    /**
     * <p>
     * Constructor for SafetyValve.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     */
    public SafetyValve(String name, StreamInterface inletStream) {
        super(name, inletStream);
    }

    /**
     * <p>
     * Getter for the field <code>pressureSpec</code>.
     * </p>
     *
     * @return the pressureSpec
     */
    public double getPressureSpec() {
        return pressureSpec;
    }

    /**
     * <p>
     * Setter for the field <code>pressureSpec</code>.
     * </p>
     *
     * @param pressureSpec the pressureSpec to set
     */
    public void setPressureSpec(double pressureSpec) {
        this.pressureSpec = pressureSpec;
    }

    /**
     * <p>
     * Getter for the field <code>fullOpenPressure</code>.
     * </p>
     *
     * @return the fullOpenPressure
     */
    public double getFullOpenPressure() {
        return fullOpenPressure;
    }

    /**
     * <p>
     * Setter for the field <code>fullOpenPressure</code>.
     * </p>
     *
     * @param fullOpenPressure the fullOpenPressure to set
     */
    public void setFullOpenPressure(double fullOpenPressure) {
        this.fullOpenPressure = fullOpenPressure;
    }
}
