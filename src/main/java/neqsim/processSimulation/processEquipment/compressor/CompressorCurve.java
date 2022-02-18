package neqsim.processSimulation.processEquipment.compressor;

import java.util.Arrays;
import java.util.Objects;

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

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(flow);
        result = prime * result + Arrays.hashCode(head);
        result = prime * result + Arrays.hashCode(polytropicEfficiency);
        result = prime * result + Objects.hash(speed);
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CompressorCurve other = (CompressorCurve) obj;
        return Arrays.equals(flow, other.flow) && Arrays.equals(head, other.head)
                && Arrays.equals(polytropicEfficiency, other.polytropicEfficiency)
                && Double.doubleToLongBits(speed) == Double.doubleToLongBits(other.speed);
    }
}
