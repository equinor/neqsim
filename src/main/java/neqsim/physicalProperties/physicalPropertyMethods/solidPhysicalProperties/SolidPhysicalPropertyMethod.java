package neqsim.physicalProperties.physicalPropertyMethods.solidPhysicalProperties;

/**
 * <p>
 * SolidPhysicalPropertyMethod class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SolidPhysicalPropertyMethod
    extends neqsim.physicalProperties.physicalPropertyMethods.PhysicalPropertyMethod {
  private static final long serialVersionUID = 1000;

  protected neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface solidPhase;

  /**
   * <p>
   * Constructor for SolidPhysicalPropertyMethod.
   * </p>
   */
  public SolidPhysicalPropertyMethod() {
    super();
  }

  /**
   * <p>
   * Constructor for SolidPhysicalPropertyMethod.
   * </p>
   *
   * @param solidPhase a
   *        {@link neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface}
   *        object
   */
  public SolidPhysicalPropertyMethod(
      neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface solidPhase) {
    super();
    this.solidPhase = solidPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void setPhase(
      neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface solidPhase) {
    this.solidPhase = solidPhase;
  }
}
