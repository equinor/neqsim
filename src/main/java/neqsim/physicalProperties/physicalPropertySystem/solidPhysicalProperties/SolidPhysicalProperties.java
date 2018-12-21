/*
 * NaturalGasPhysicalProperties.java
 *
 * Created on 13. august 2001, 10:32
 */

package neqsim.physicalProperties.physicalPropertySystem.solidPhysicalProperties;

import neqsim.physicalProperties.physicalPropertySystem.PhysicalProperties;
import neqsim.thermo.phase.PhaseInterface;

/**
 *
 * @author  esol
 * @version
 */
public class SolidPhysicalProperties extends PhysicalProperties{

    private static final long serialVersionUID = 1000;
    
    /** Creates new NaturalGasPhysicalProperties */
    public SolidPhysicalProperties() {
    }
    
    public SolidPhysicalProperties(PhaseInterface phase) {
        super(phase);
        conductivityCalc = new neqsim.physicalProperties.physicalPropertyMethods.solidPhysicalProperties.conductivity.Conductivity(this);
        viscosityCalc = new neqsim.physicalProperties.physicalPropertyMethods.solidPhysicalProperties.viscosity.Viscosity(this);
        diffusivityCalc = new neqsim.physicalProperties.physicalPropertyMethods.solidPhysicalProperties.diffusivity.Diffusivity(this);
         
        densityCalc = new neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.density.Density(this);
        this.init(phase);
    }
}
