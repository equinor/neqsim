/*
 * AirPhysicalProperties.java
 *
 * Created on 13. august 2001, 10:34
 */

package neqsim.physicalProperties.physicalPropertySystem.gasPhysicalProperties;

import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>AirPhysicalProperties class.</p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class AirPhysicalProperties extends GasPhysicalProperties {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new AirPhysicalProperties
     */
    public AirPhysicalProperties() {
    }

    /**
     * <p>Constructor for AirPhysicalProperties.</p>
     *
     * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
     * @param binaryDiffusionCoefficientMethod a int
     * @param multicomponentDiffusionMethod a int
     */
    public AirPhysicalProperties(PhaseInterface phase, int binaryDiffusionCoefficientMethod,
            int multicomponentDiffusionMethod) {
        super(phase, binaryDiffusionCoefficientMethod, multicomponentDiffusionMethod);
        conductivityCalc = new neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.conductivity.ChungConductivityMethod(
                this);
        viscosityCalc = new neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.viscosity.ChungViscosityMethod(
                this);
        diffusivityCalc = new neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.diffusivity.Diffusivity(
                this);
        // diffusivityCalc = new
        // physicalProperties.physicalPropertyMethods.gasPhysicalProperties.diffusivity.WilkeLeeDiffusivity(this);

        densityCalc = new neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.density.Density(this);
        this.init(phase);
    }
}
