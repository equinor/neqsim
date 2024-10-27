package neqsim.physicalproperties.physicalpropertymethods.commonphasephysicalproperties;

import neqsim.physicalproperties.physicalpropertymethods.PhysicalPropertyMethod;
import neqsim.physicalproperties.physicalpropertysystem.PhysicalProperties;

/**
 * <p>
 * CommonPhysicalPropertyMethod class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public abstract class CommonPhysicalPropertyMethod extends PhysicalPropertyMethod {
  private static final long serialVersionUID = 1000;

  protected neqsim.physicalproperties.physicalpropertysystem.PhysicalProperties phase;

  /**
   * <p>
   * Constructor for CommonPhysicalPropertyMethod.
   * </p>
   *
   * @param phase a {@link neqsim.physicalproperties.physicalpropertysystem.PhysicalProperties}
   *        object
   */
  public CommonPhysicalPropertyMethod(PhysicalProperties phase) {
    super(phase);
  }

  /** {@inheritDoc} */
  @Override
  public void setPhase(PhysicalProperties phase) {
    this.phase = phase;
  }
}
