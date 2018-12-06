/*
 * SeparatorInterface.java
 *
 * Created on 22. august 2001, 17:22
 */
package neqsim.processSimulation.processEquipment.separator;

import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author esol
 * @version
 */
public interface SeparatorInterface {

    public void setName(String name);

    public String getName();

    public SystemInterface getThermoSystem();

    public void setInternalDiameter(double diam);
}
