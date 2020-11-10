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

    public void setFlowRate(double flowrate, String unit);
    
    public double getPressure(String unit);
    public void runTPflash() ;
    public double getTemperature(String unit);
    public void setName(String name);
	public double CCT(String unit);
    public double getTemperature();
	public double CCB(String unit);
    public double getFlowRate(String unit);
    
    public double TVP(double temperature, String unit);
    
    public void setFluid(SystemInterface fluid);

    public double getMolarRate();

    public double getPressure();

    public Object clone();

    public void flashStream();

    public double getHydrateEquilibriumTemperature();

    public void setThermoSystemFromPhase(SystemInterface thermoSystem, String phaseTypeName);

    public void setEmptyThermoSystem(SystemInterface thermoSystem);
    
    public void setPressure(double pressure, String unit);
    
    public void setTemperature(double temperature, String unit);
    
    public double GCV();
    
    public double LCV();

}
