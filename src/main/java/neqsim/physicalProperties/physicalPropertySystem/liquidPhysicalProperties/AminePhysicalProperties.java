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
 * <p>AminePhysicalProperties class.</p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class AminePhysicalProperties extends LiquidPhysicalProperties {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new AminePhysicalProperties
     */
    public AminePhysicalProperties() {
    }

    /**
     * <p>Constructor for AminePhysicalProperties.</p>
     *
     * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
     * @param binaryDiffusionCoefficientMethod a int
     * @param multicomponentDiffusionMethod a int
     */
    public AminePhysicalProperties(PhaseInterface phase, int binaryDiffusionCoefficientMethod,
            int multicomponentDiffusionMethod) {
        super(phase, binaryDiffusionCoefficientMethod, multicomponentDiffusionMethod);
        conductivityCalc = new Conductivity(this);
        viscosityCalc = new AmineViscosity(this);
        diffusivityCalc = new AmineDiffusivity(this);
        densityCalc = new Density(this);
    }
}
