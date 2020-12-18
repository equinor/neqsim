/*
 * SeparatorInterface.java
 *
 * Created on 22. august 2001, 17:22
 */
package neqsim.processSimulation.processEquipment.heatExchanger;

/**
 *
 * @author esol
 * @version
 */
public interface HeaterInterface {

    public void setName(String name);

    public String getName();

    public void setdT(double dT);

    public void setOutTP(double temperature, double pressure);
    
    public void setOutTemperature(double temperature, String unit);
    
    public void setOutPressure(double pressure, String unit);
}
