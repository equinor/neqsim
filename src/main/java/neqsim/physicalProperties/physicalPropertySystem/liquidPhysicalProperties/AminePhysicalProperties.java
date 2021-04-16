/*
 * AminePhysicalProperties.java
 *
 * Created on 13. august 2001, 10:31
 */

package neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties;

import neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.density.Density;
import neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.conductivity.Conductivity;
import neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.diffusivity.AmineDiffusivity;
import neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.viscosity.AmineViscosity;
import neqsim.thermo.phase.PhaseInterface;

/**
 *
 * @author esol
 * @version
 */
public class AminePhysicalProperties extends LiquidPhysicalProperties {

    private static final long serialVersionUID = 1000;

    /** Creates new AminePhysicalProperties */
    public AminePhysicalProperties() {
    }

    public AminePhysicalProperties(PhaseInterface phase, int binaryDiffusionCoefficientMethod,
            int multicomponentDiffusionMethod) {
        super(phase, binaryDiffusionCoefficientMethod, multicomponentDiffusionMethod);
        conductivityCalc = new Conductivity(this);
        viscosityCalc = new AmineViscosity(this);
        diffusivityCalc = new AmineDiffusivity(this);
        densityCalc = new Density(this);
    }
}
