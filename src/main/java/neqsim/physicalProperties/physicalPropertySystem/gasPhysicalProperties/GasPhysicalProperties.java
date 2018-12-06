/*
 * GasPhysicalProperties.java
 *
 * Created on 29. oktober 2000, 16:18
 */
package neqsim.physicalProperties.physicalPropertySystem.gasPhysicalProperties;

import neqsim.thermo.phase.PhaseInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class GasPhysicalProperties extends neqsim.physicalProperties.physicalPropertySystem.PhysicalProperties {

    private static final long serialVersionUID = 1000;

    public GasPhysicalProperties() {
    }

    public GasPhysicalProperties(PhaseInterface phase, int binaryDiffusionCoefficientMethod, int multicomponentDiffusionMethod) {
        super(phase, binaryDiffusionCoefficientMethod, multicomponentDiffusionMethod);
        conductivityCalc = new neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.conductivity.ChungConductivityMethod(this);
       // conductivityCalc = new physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.conductivity.PFCTConductivityMethodMod86(this);

//viscosityCalc = new physicalProperties.physicalPropertyMethods.gasPhysicalProperties.viscosity.ChungViscosityMethod(this);
        viscosityCalc = new neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.viscosity.FrictionTheoryViscosityMethod(this);

        diffusivityCalc = new neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.diffusivity.Diffusivity(this);
        //diffusivityCalc = new physicalProperties.physicalPropertyMethods.gasPhysicalProperties.diffusivity.WilkeLeeDiffusivity(this);

        densityCalc = new neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties.density.Density(this);
      //  this.init(phase);
    }

    public Object clone() {
        GasPhysicalProperties properties = null;

        try {
            properties = (GasPhysicalProperties) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        return properties;
    }
}
