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
 *
 * @author  esol
 * @version
 */
public interface MixerInterface extends ProcessEquipmentInterface {

    public void run();

    public void addStream(StreamInterface newStream);

    public Stream getOutStream();

    public void setName(String name);
    public void replaceStream(int i, StreamInterface newStream);
    public String getName();

    public SystemInterface getThermoSystem();

    public void runTransient();
}

