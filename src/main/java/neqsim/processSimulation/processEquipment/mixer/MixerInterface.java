/*
 * MixerInterface.java
 *
 * Created on 21. august 2001, 22:28
 */
package neqsim.processSimulation.processEquipment.mixer;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>MixerInterface interface.</p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface MixerInterface extends ProcessEquipmentInterface {

	/** {@inheritDoc} */
    @Override
	public void run();

    /**
     * <p>addStream.</p>
     *
     * @param newStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    public void addStream(StreamInterface newStream);

    /**
     * <p>getOutStream.</p>
     *
     * @return a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     */
    public Stream getOutStream();

	/** {@inheritDoc} */
    @Override
	public void setName(String name);

    /**
     * <p>replaceStream.</p>
     *
     * @param i a int
     * @param newStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    public void replaceStream(int i, StreamInterface newStream);

	/** {@inheritDoc} */
    @Override
	public String getName();

	/** {@inheritDoc} */
    @Override
	public SystemInterface getThermoSystem();

    /**
     * <p>runTransient.</p>
     */
    public void runTransient();
}
