package neqsim.processSimulation.processEquipment.stream;

/**
 * <p>
 * EnergyStream class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class EnergyStream implements java.io.Serializable, Cloneable {
    private static final long serialVersionUID = 1000;

    private double duty = 0.0;

    /** {@inheritDoc} */
    @Override
    public EnergyStream clone() {
        EnergyStream clonedStream = null;
        try {
            clonedStream = (EnergyStream) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        return clonedStream;
    }

    /**
     * <p>
     * Getter for the field <code>duty</code>.
     * </p>
     *
     * @return a double
     */
    public double getDuty() {
        return duty;
    }

    /**
     * <p>
     * Setter for the field <code>duty</code>.
     * </p>
     *
     * @param duty a double
     */
    public void setDuty(double duty) {
        this.duty = duty;
    }
}
