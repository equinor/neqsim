package neqsim.physicalproperties.physicalpropertymethods.solidphysicalproperties;

import neqsim.physicalproperties.physicalpropertymethods.PhysicalPropertyMethod;
import neqsim.physicalproperties.physicalpropertysystem.PhysicalProperties;

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

  protected PhysicalProperties solidPhase;

  /**
   * <p>
   * Constructor for SolidPhysicalPropertyMethod.
   * </p>
   *
   * @param solidPhase a {@link neqsim.physicalproperties.physicalpropertysystem.PhysicalProperties}
   *        object
   */
  public SolidPhysicalPropertyMethod(PhysicalProperties solidPhase) {
    setPhase(solidPhase);
  }

  /** {@inheritDoc} */
  @Override
  public void setPhase(PhysicalProperties solidPhase) {
    this.solidPhase = solidPhase;
  }
}
