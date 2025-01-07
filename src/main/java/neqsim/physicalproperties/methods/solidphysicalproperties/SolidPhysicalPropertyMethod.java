package neqsim.physicalproperties.methods.solidphysicalproperties;

import neqsim.physicalproperties.methods.PhysicalPropertyMethod;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * <p>
 * SolidPhysicalPropertyMethod class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public abstract class SolidPhysicalPropertyMethod extends PhysicalPropertyMethod {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  protected PhysicalProperties solidPhase;

  /**
   * <p>
   * Constructor for SolidPhysicalPropertyMethod.
   * </p>
   *
   * @param solidPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public SolidPhysicalPropertyMethod(PhysicalProperties solidPhase) {
    super(solidPhase);
  }

  /** {@inheritDoc} */
  @Override
  public void setPhase(PhysicalProperties solidPhase) {
    this.solidPhase = solidPhase;
  }
}
