/*
 * WaterPhysicalProperties.java
 *
 * Created on 13. august 2001, 10:34
 */

package neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties;

import neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.conductivity.Conductivity;
import neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.density.Density;
import neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.diffusivity.SiddiqiLucasMethod;
import neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.viscosity.Viscosity;
import neqsim.thermo.phase.PhaseInterface;

/**
 *
 * @author esol
 * @version
 */
public class WaterPhysicalProperties extends LiquidPhysicalProperties {
    private static final long serialVersionUID = 1000;

    /** Creates new WaterPhysicalProperties */
    public WaterPhysicalProperties() {}

    public WaterPhysicalProperties(PhaseInterface phase, int binaryDiffusionCoefficientMethod,
            int multicomponentDiffusionMethod) {
        super(phase, binaryDiffusionCoefficientMethod, multicomponentDiffusionMethod);
        conductivityCalc = new Conductivity(this);
        viscosityCalc = new Viscosity(this);
        diffusivityCalc = new SiddiqiLucasMethod(this);
        densityCalc = new Density(this);
    }
}
