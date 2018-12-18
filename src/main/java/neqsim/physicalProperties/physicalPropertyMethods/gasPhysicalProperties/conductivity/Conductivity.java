/*
 * Conductivity.java
 *
 * Created on 1. november 2000, 19:00
 */

package neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.conductivity;

import org.apache.log4j.Logger;

/**
 *
 * @author  Even Solbraa
 * @version
 */
abstract class Conductivity extends neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.GasPhysicalPropertyMethod implements  neqsim.physicalProperties.physicalPropertyMethods.methodInterface.ConductivityInterface{

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(Conductivity.class);
    
    double conductivity=0;
    /** Creates new Conductivity */
    public Conductivity() {
    }
    
    public Conductivity(neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface gasPhase) {
        super(gasPhase);
    }
    
      public Object clone(){
        Conductivity properties = null;
        
        try{
            properties = (Conductivity) super.clone();
        }
        catch(Exception e) {
            logger.error("Cloning failed.",e);
        }
        
        return properties;
    }
    
}
