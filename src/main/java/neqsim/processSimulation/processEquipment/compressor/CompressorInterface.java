/*
 * ValveInterface.java
 *
 * Created on 22. august 2001, 17:20
 */
package neqsim.processSimulation.processEquipment.compressor;

import java.io.Serializable;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;

/**
 *
 * @author  esol
 * @version
 */
public interface CompressorInterface extends ProcessEquipmentInterface, Serializable {

    public void run();

    public void setOutletPressure(double pressure);

    public void setInletStream(StreamInterface inletStream);

    public double getEnergy();

    public String getName();

    public StreamInterface getOutStream();

    public double getIsentropicEfficiency();

    public void setIsentropicEfficiency(double isentropicEfficientcy);

    public void runTransient();
   
    public double getPolytropicEfficiency();

    public void setPolytropicEfficiency(double polytropicEfficiency);
    
	public AntiSurge getAntiSurge();
}

