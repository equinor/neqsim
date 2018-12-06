/*
 * StreamInterface.java
 *
 * Created on 21. august 2001, 22:49
 */
package neqsim.processSimulation.processEquipment.stream;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author esol
 * @version
 */
public interface StreamInterface extends ProcessEquipmentInterface {

    public SystemInterface getThermoSystem();

    public void setThermoSystem(SystemInterface thermoSystem);

    public void setName(String name);

    public double getTemperature();

    public double getMolarRate();

    public double getPressure();

    public Object clone();

    public void flashStream();

    public double getHydrateEquilibriumTemperature();

    public void setThermoSystemFromPhase(SystemInterface thermoSystem, String phaseTypeName);

    public void setEmptyThermoSystem(SystemInterface thermoSystem);

}
