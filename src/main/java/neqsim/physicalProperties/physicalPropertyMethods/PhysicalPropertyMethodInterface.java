/*
 * PhysicalPropertyMethodInterface.java
 *
 * Created on 21. august 2001, 13:20
 */

package neqsim.physicalProperties.physicalPropertyMethods;
/**
 *
 * @author  esol
 * @version
 */
public interface PhysicalPropertyMethodInterface extends Cloneable, java.io.Serializable{
    public Object clone();
    public void setPhase(neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface phase);
    public void tuneModel(double val, double temperature, double pressure);
    
}

