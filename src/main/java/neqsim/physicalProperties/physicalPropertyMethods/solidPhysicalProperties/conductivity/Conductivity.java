/*
 * Conductivity.java
 *
 * Created on 1. november 2000, 19:00
 */

package neqsim.physicalProperties.physicalPropertyMethods.solidPhysicalProperties.conductivity;



import neqsim.thermo.phase.PhaseType;

/**
 * <p>
 * Conductivity class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Conductivity extends
    neqsim.physicalProperties.physicalPropertyMethods.solidPhysicalProperties.SolidPhysicalPropertyMethod
    implements
    neqsim.physicalProperties.physicalPropertyMethods.methodInterface.ConductivityInterface {
  private static final long serialVersionUID = 1000;
  

  double conductivity = 0;

  /**
   * <p>
   * Constructor for Conductivity.
   * </p>
   */
  public Conductivity() {}

  /**
   * <p>
   * Constructor for Conductivity.
   * </p>
   *
   * @param solidPhase a
   *        {@link neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface}
   *        object
   */
  public Conductivity(
      neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface solidPhase) {
    super(solidPhase);
  }

  /** {@inheritDoc} */
  @Override
  public Conductivity clone() {
    Conductivity properties = null;

    try {
      properties = (Conductivity) super.clone();
    } catch (Exception ex) {
      
    }

    return properties;
  }

  /** {@inheritDoc} */
  @Override
  public double calcConductivity() {
    // using default value of parafin wax
    if (solidPhase.getPhase().getType() == PhaseType.WAX) {
      conductivity = 0.25;
    } else {
      conductivity = 2.18;
    }

    return conductivity;
  }
}
