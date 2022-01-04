/*
 * LiquidPhysicalProperties.java
 *
 * Created on 29. oktober 2000, 16:17
 */
package neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.density.Density;
import neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.diffusivity.SiddiqiLucasMethod;
import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * LiquidPhysicalProperties class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class LiquidPhysicalProperties
        extends neqsim.physicalProperties.physicalPropertySystem.PhysicalProperties {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(LiquidPhysicalProperties.class);

    /**
     * <p>
     * Constructor for LiquidPhysicalProperties.
     * </p>
     */
    public LiquidPhysicalProperties() {}

    /**
     * <p>
     * Constructor for LiquidPhysicalProperties.
     * </p>
     *
     * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
     * @param binaryDiffusionCoefficientMethod a int
     * @param multicomponentDiffusionMethod a int
     */
    public LiquidPhysicalProperties(PhaseInterface phase, int binaryDiffusionCoefficientMethod,
            int multicomponentDiffusionMethod) {
        super(phase, binaryDiffusionCoefficientMethod, multicomponentDiffusionMethod);
        // conductivityCalc = new Conductivity(this);
        conductivityCalc =
                new neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.conductivity.PFCTConductivityMethodMod86(
                        this);
        // viscosityCalc = new Viscosity(this);
        // viscosityCalc = new
        // neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.viscosity.FrictionTheoryViscosityMethod(this);
        // viscosityCalc = new
        // neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.viscosity.PFCTViscosityMethodMod86(this);
        // viscosityCalc = new
        // neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.viscosity.LBCViscosityMethod(this);
        viscosityCalc =
                new neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.viscosity.PFCTViscosityMethodHeavyOil(
                        this);

        diffusivityCalc = new SiddiqiLucasMethod(this);
        densityCalc = new Density(this);
    }

    /** {@inheritDoc} */
    @Override
    public LiquidPhysicalProperties clone() {
        LiquidPhysicalProperties properties = null;

        try {
            properties = (LiquidPhysicalProperties) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }
        return properties;
    }
}
