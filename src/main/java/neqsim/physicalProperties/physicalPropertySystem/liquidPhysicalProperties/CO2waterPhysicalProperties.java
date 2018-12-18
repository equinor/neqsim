/*
 * LiquidPhysicalProperties.java
 *
 * Created on 29. oktober 2000, 16:17
 */

package neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties;

import neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.conductivity.Conductivity;
import neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.density.Density;
import neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.diffusivity.CO2water;
import neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.viscosity.Viscosity;
import neqsim.thermo.phase.PhaseInterface;
import org.apache.log4j.Logger;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class CO2waterPhysicalProperties extends neqsim.physicalProperties.physicalPropertySystem.PhysicalProperties {

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(CO2waterPhysicalProperties.class);
    
    
    
    public CO2waterPhysicalProperties() {
    }
    
    public CO2waterPhysicalProperties(PhaseInterface phase, int binaryDiffusionCoefficientMethod, int multicomponentDiffusionMethod) {
        super(phase, binaryDiffusionCoefficientMethod, multicomponentDiffusionMethod);
        conductivityCalc = new Conductivity(this);
        viscosityCalc = new Viscosity(this);
        diffusivityCalc = new CO2water(this);
        densityCalc = new Density(this);
    }
    
    public Object clone(){
        CO2waterPhysicalProperties properties = null;
        
        try{
            properties = (CO2waterPhysicalProperties) super.clone();
        }
        catch(Exception e) {
            logger.error("Cloning failed.",e);
        }
        return properties;
    }
  
}
