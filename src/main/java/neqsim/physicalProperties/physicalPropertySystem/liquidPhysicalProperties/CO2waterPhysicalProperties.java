package neqsim.physicalProperties.physicalPropertySystem.liquidPhysicalProperties;



import neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.conductivity.Conductivity;
import neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.density.Density;
import neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.diffusivity.CO2water;
import neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.viscosity.Viscosity;
import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * CO2waterPhysicalProperties class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class CO2waterPhysicalProperties
    extends neqsim.physicalProperties.physicalPropertySystem.PhysicalProperties {
  private static final long serialVersionUID = 1000;
  

  /**
   * <p>
   * Constructor for CO2waterPhysicalProperties.
   * </p>
   */
  public CO2waterPhysicalProperties() {}

  /**
   * <p>
   * Constructor for CO2waterPhysicalProperties.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param binaryDiffusionCoefficientMethod a int
   * @param multicomponentDiffusionMethod a int
   */
  public CO2waterPhysicalProperties(PhaseInterface phase, int binaryDiffusionCoefficientMethod,
      int multicomponentDiffusionMethod) {
    super(phase, binaryDiffusionCoefficientMethod, multicomponentDiffusionMethod);
    conductivityCalc = new Conductivity(this);
    viscosityCalc = new Viscosity(this);
    diffusivityCalc = new CO2water(this);
    densityCalc = new Density(this);
  }

  /** {@inheritDoc} */
  @Override
  public CO2waterPhysicalProperties clone() {
    CO2waterPhysicalProperties properties = null;

    try {
      properties = (CO2waterPhysicalProperties) super.clone();
    } catch (Exception ex) {
      
    }
    return properties;
  }
}
