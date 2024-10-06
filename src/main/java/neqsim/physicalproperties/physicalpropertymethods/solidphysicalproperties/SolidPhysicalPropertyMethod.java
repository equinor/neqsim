package neqsim.physicalproperties.physicalpropertymethods.solidphysicalproperties;

/**
 * <p>
 * SolidPhysicalPropertyMethod class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SolidPhysicalPropertyMethod
    extends neqsim.physicalproperties.physicalpropertymethods.PhysicalPropertyMethod {
  private static final long serialVersionUID = 1000;

  protected neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface solidPhase;

  /**
   * <p>
   * Constructor for SolidPhysicalPropertyMethod.
   * </p>
   */
  public SolidPhysicalPropertyMethod() {
  }

  /**
   * <p>
   * Constructor for SolidPhysicalPropertyMethod.
   * </p>
   *
   * @param solidPhase a
   *        {@link neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface}
   *        object
   */
  public SolidPhysicalPropertyMethod(
      neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface solidPhase) {
    this.solidPhase = solidPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void setPhase(
      neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface solidPhase) {
    this.solidPhase = solidPhase;
  }
}
