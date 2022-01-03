/*
 * NaturalGasPhysicalProperties.java
 *
 * Created on 13. august 2001, 10:32
 */

package neqsim.physicalProperties.physicalPropertySystem.commonPhasePhysicalProperties;

import neqsim.physicalProperties.physicalPropertySystem.PhysicalProperties;
import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * DefaultPhysicalProperties class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class DefaultPhysicalProperties extends PhysicalProperties {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for DefaultPhysicalProperties.
     * </p>
     */
    public DefaultPhysicalProperties() {}

    /**
     * <p>
     * Constructor for DefaultPhysicalProperties.
     * </p>
     *
     * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
     * @param binaryDiffusionCoefficientMethod a int
     * @param multicomponentDiffusionMethod a int
     */
    public DefaultPhysicalProperties(PhaseInterface phase, int binaryDiffusionCoefficientMethod,
            int multicomponentDiffusionMethod) {
        super(phase, binaryDiffusionCoefficientMethod, multicomponentDiffusionMethod);
        conductivityCalc =
                new neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.conductivity.PFCTConductivityMethodMod86(
                        this);

        // viscosityCalc = new
        // physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.viscosity.PFCTViscosityMethod(this);
        // viscosityCalc = new
        // physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.viscosity.PFCTViscosityMethodMod86(this);
        // viscosityCalc = new
        // physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.viscosity.LBCViscosityMethod(this);
        viscosityCalc =
                new neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.viscosity.FrictionTheoryViscosityMethod(
                        this);
        diffusivityCalc =
                new neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.diffusivity.CorrespondingStatesDiffusivity(
                        this);

        densityCalc =
                new neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.density.Density(
                        this);
        this.init(phase);
    }
}
