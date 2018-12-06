/*
 * SeparatorInterface.java
 *
 * Created on 22. august 2001, 17:22
 */

package neqsim.processSimulation.processEquipment.absorber;

/**
 *
 * @author  esol
 * @version
 */
public interface AbsorberInterface {
    public void setName(String name);
    public String getName();
    public void setAproachToEquilibrium(double eff);
}

