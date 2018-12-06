/*
 * ValveInterface.java
 *
 * Created on 22. august 2001, 17:20
 */

package neqsim.processSimulation.processEquipment.valve;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;

/**
 *
 * @author  esol
 * @version
 */
public interface ValveInterface extends ProcessEquipmentInterface {
    public void run();
    public void setOutletPressure(double pressure);
    public void setInletStream(StreamInterface inletStream);
    public StreamInterface getOutStream();
    public String getName();
    public boolean isIsoThermal();
    public void setIsoThermal(boolean isoThermal);
    public double getPercentValveOpening();
    public void setPercentValveOpening(double percentValveOpening);
    public double getCv();
    public void setCv(double Cv);
}

