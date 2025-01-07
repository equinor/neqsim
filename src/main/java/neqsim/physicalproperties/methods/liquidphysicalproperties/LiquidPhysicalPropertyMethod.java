package neqsim.physicalproperties.methods.liquidphysicalproperties;

import neqsim.physicalproperties.methods.PhysicalPropertyMethod;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * <p>
 * LiquidPhysicalPropertyMethod class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public abstract class LiquidPhysicalPropertyMethod extends PhysicalPropertyMethod {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  protected PhysicalProperties liquidPhase;

  /**
   * <p>
   * Constructor for LiquidPhysicalPropertyMethod.
   * </p>
   *
   * @param liquidPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public LiquidPhysicalPropertyMethod(PhysicalProperties liquidPhase) {
    super(liquidPhase);
  }

  /** {@inheritDoc} */
  @Override
  public void setPhase(PhysicalProperties phase) {
    this.liquidPhase = phase;
  }
}
