/*
 * PhysicalPropertyMethod.java
 *
 * Created on 3. august 2001, 22:49
 */

package neqsim.physicalProperties.physicalPropertyMethods;
/**
 *
 * @author  esol
 * @version
 */
public class PhysicalPropertyMethod implements Cloneable, PhysicalPropertyMethodInterface, java.io.Serializable{

    private static final long serialVersionUID = 1000;
    
    /** Creates new PhysicalPropertyMethod */
    public PhysicalPropertyMethod() {
    }
    
    public Object clone(){
        PhysicalPropertyMethod properties = null;
        
        try{
            properties = (PhysicalPropertyMethod) super.clone();
        }
        catch(Exception e) {
            e.printStackTrace(System.err);
        }
        
        return properties;
    }
    
    public void setPhase(neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface phase){
    }
    
    public void tuneModel(double val, double temperature, double pressure){
        System.out.println("model tuning not implemented!");
    }
    // should contain phase objects ++ get diffusivity methods .. more ?
}
