package neqsim.processSimulation.processEquipment.pump;

/**
 * <p>PumpCurve class.</p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class PumpCurve implements java.io.Serializable {
    private static final long serialVersionUID = 1000;
    public double[] flow;
    public double[] head;
    public double[] efficiency;
    public double speed = 1000.0;

    /**
     * <p>Constructor for PumpCurve.</p>
     */
    public PumpCurve() {
        flow = new double[] {453.2, 600.0, 750.0};
        head = new double[] {1000.0, 900.0, 800.0};
        efficiency = new double[] {78.0, 79.0, 78.0};
    }

    /**
     * <p>Constructor for PumpCurve.</p>
     *
     * @param speed a double
     * @param flow an array of {@link double} objects
     * @param head an array of {@link double} objects
     * @param efficiency an array of {@link double} objects
     */
    public PumpCurve(double speed, double[] flow, double[] head, double[] efficiency) {
        this.speed = speed;
        this.flow = flow;
        this.head = head;
        this.efficiency = efficiency;
    }
}
