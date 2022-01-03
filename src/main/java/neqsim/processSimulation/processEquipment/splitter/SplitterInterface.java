/*
 * SeparatorInterface.java
 *
 * Created on 22. august 2001, 17:22
 */

package neqsim.processSimulation.processEquipment.splitter;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;

/**
 * <p>SplitterInterface interface.</p>
 *
 * @author esol
 */
public interface SplitterInterface extends ProcessEquipmentInterface {
    /** {@inheritDoc} */
    @Override
	public void setName(String name);

    /**
     * <p>setSplitNumber.</p>
     *
     * @param i a int
     */
    public void setSplitNumber(int i);

    /**
     * <p>setInletStream.</p>
     *
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    public void setInletStream(StreamInterface inletStream);

    /**
     * <p>getSplitStream.</p>
     *
     * @param i a int
     * @return a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
     */
    public Stream getSplitStream(int i);

    /** {@inheritDoc} */
    @Override
	public String getName();

}
