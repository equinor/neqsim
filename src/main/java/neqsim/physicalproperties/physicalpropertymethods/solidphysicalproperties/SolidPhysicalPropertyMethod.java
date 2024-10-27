package neqsim.physicalproperties.physicalpropertymethods.solidphysicalproperties;

import neqsim.physicalproperties.physicalpropertymethods.PhysicalPropertyMethod;

/**
 * <p>
 * SolidPhysicalPropertyMethod class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public abstract class SolidPhysicalPropertyMethod extends PhysicalPropertyMethod {
  private static final long serialVersionUID = 1000;

  protected neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface solidPhase;

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
    setPhase(solidPhase);
  }

  /** {@inheritDoc} */
  @Override
  public void setPhase(
      neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface phase) {
    this.solidPhase = phase;
  }
}
