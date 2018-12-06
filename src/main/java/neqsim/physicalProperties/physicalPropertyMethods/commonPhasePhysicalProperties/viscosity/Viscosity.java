/*
 * Conductivity.java
 *
 * Created on 1. november 2000, 19:00
 */

package neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.viscosity;
//import physicalProperties.gasPhysicalProperties.*;
/**
 *
 * @author  Even Solbraa
 * @version
 */
abstract class Viscosity extends neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.CommonPhysicalPropertyMethod implements  neqsim.physicalProperties.physicalPropertyMethods.methodInterface.ViscosityInterface{

    private static final long serialVersionUID = 1000;
    
    
    /** Creates new Conductivity */
    public Viscosity() {
    }
    
    public Viscosity(neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface phase) {
        super(phase);
    }
    
    public Object clone(){
        Viscosity properties = null;
        
        try{
            properties = (Viscosity) super.clone();
        }
        catch(Exception e) {
            e.printStackTrace(System.err);
        }
        
        return properties;
    }
}
