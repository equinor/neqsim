package neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.conductivity;




/**
 * Class for physical property Conductivity.
 *
 * @author Even Solbraa
 */
abstract class Conductivity extends
    neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.CommonPhysicalPropertyMethod
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
   * @param phase a
   *        {@link neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface}
   *        object
   */
  public Conductivity(
      neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface phase) {
    super(phase);
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
}
