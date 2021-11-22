/*
 * ValveInterface.java
 *
 * Created on 22. august 2001, 17:20
 */

package neqsim.processSimulation.processEquipment.pump;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;

/**
 *
 * @author esol
 * @version
 */
public interface PumpInterface extends ProcessEquipmentInterface {
    @Override
    public void run();

    public void setOutletPressure(double pressure);

    public void setInletStream(StreamInterface inletStream);

    public double getEnergy();

    @Override
    public String getName();

    public StreamInterface getOutStream();

    public double getPower();

    public void runTransient();
}
