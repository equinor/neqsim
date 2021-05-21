/*
 * SeparatorInterface.java
 *
 * Created on 22. august 2001, 17:22
 */

package neqsim.processSimulation.processEquipment.absorber;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;

/**
 *
 * @author esol
 * @version
 */
public interface AbsorberInterface extends ProcessEquipmentInterface{
    public void setName(String name);

    public String getName();

    public void setAproachToEquilibrium(double eff);
}
