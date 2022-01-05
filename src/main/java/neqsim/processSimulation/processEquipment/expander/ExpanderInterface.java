package neqsim.processSimulation.processEquipment.expander;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;

/**
 * <p>
 * ExpanderInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface ExpanderInterface extends ProcessEquipmentInterface {
    /** {@inheritDoc} */
    @Override
    public void run();

    /**
     * <p>
     * setOutletPressure.
     * </p>
     *
     * @param pressure a double
     */
    public void setOutletPressure(double pressure);

    /**
     * <p>
     * setInletStream.
     * </p>
     *
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    public void setInletStream(StreamInterface inletStream);

    /**
     * <p>
     * getEnergy.
     * </p>
     *
     * @return a double
     */
    public double getEnergy();

    /** {@inheritDoc} */
    @Override
    public String getName();

    /**
     * <p>
     * getOutStream.
     * </p>
     *
     * @return a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    public StreamInterface getOutStream();

    /**
     * <p>
     * runTransient.
     * </p>
     */
    public void runTransient();
}
