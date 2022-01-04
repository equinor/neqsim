package neqsim.physicalProperties.physicalPropertySystem.solidPhysicalProperties;

import neqsim.physicalProperties.physicalPropertySystem.PhysicalProperties;
import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * SolidPhysicalProperties class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class SolidPhysicalProperties extends PhysicalProperties {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for SolidPhysicalProperties.
     * </p>
     */
    public SolidPhysicalProperties() {}

    /**
     * <p>
     * Constructor for SolidPhysicalProperties.
     * </p>
     *
     * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
     */
    public SolidPhysicalProperties(PhaseInterface phase) {
        super(phase);
        conductivityCalc =
                new neqsim.physicalProperties.physicalPropertyMethods.solidPhysicalProperties.conductivity.Conductivity(
                        this);
        viscosityCalc =
                new neqsim.physicalProperties.physicalPropertyMethods.solidPhysicalProperties.viscosity.Viscosity(
                        this);
        diffusivityCalc =
                new neqsim.physicalProperties.physicalPropertyMethods.solidPhysicalProperties.diffusivity.Diffusivity(
                        this);
        densityCalc =
                new neqsim.physicalProperties.physicalPropertyMethods.solidPhysicalProperties.density.Density(
                        this);
        this.init(phase);
    }
}
