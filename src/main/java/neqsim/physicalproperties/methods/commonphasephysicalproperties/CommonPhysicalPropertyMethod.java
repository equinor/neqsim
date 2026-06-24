package neqsim.physicalproperties.methods.commonphasephysicalproperties;

import neqsim.physicalproperties.methods.PhysicalPropertyMethod;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * CommonPhysicalPropertyMethod class.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public abstract class CommonPhysicalPropertyMethod extends PhysicalPropertyMethod {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  protected PhysicalProperties phase;

  /**
   * Constructor for CommonPhysicalPropertyMethod.
   *
   * @param phase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
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
