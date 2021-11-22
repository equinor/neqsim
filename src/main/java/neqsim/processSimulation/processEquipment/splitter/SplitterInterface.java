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
 *
 * @author esol
 * @version
 */
public interface SplitterInterface extends ProcessEquipmentInterface {
    @Override
    public void setName(String name);

    public void setSplitNumber(int i);

    public void setInletStream(StreamInterface inletStream);

    public Stream getSplitStream(int i);

    @Override
    public String getName();
}
