package neqsim.processSimulation.processEquipment.distillation;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;

/**
 * <p>TrayInterface interface.</p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public interface TrayInterface extends ProcessEquipmentInterface {

    /** {@inheritDoc} */
    @Override
	public void run();

    /**
     * <p>addStream.</p>
     *
     * @param newStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    public void addStream(StreamInterface newStream);

    /** {@inheritDoc} */
    @Override
	public void setName(String name);

    /** {@inheritDoc} */
    @Override
	public String getName();

    /**
     * <p>setHeatInput.</p>
     *
     * @param heatinp a double
     */
    public void setHeatInput(double heatinp);

    /**
     * <p>runTransient.</p>
     */
    public void runTransient();
}
