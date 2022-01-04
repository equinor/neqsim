package neqsim.processSimulation.processEquipment.compressor;

/**
 * <p>CompressorCurve class.</p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class CompressorCurve implements java.io.Serializable {
    private static final long serialVersionUID = 1000;
    public double[] flow;
    public double[] head;
    public double[] polytropicEfficiency;
    public double speed = 1000.0;

    /**
     * <p>Constructor for CompressorCurve.</p>
     */
    public CompressorCurve() {
        flow = new double[] {453.2, 600.0, 750.0};
        head = new double[] {1000.0, 900.0, 800.0};
        polytropicEfficiency = new double[] {78.0, 79.0, 78.0};
    }

    /**
     * <p>Constructor for CompressorCurve.</p>
     *
     * @param speed a double
     * @param flow an array of {@link double} objects
     * @param head an array of {@link double} objects
     * @param polytropicEfficiency an array of {@link double} objects
     */
    public CompressorCurve(double speed, double[] flow, double[] head,
            double[] polytropicEfficiency) {
        this.speed = speed;
        this.flow = flow;
        this.head = head;
        this.polytropicEfficiency = polytropicEfficiency;
    }
}
