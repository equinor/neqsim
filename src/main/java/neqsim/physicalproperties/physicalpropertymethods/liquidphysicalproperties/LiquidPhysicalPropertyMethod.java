package neqsim.physicalproperties.physicalpropertymethods.liquidphysicalproperties;

import neqsim.physicalproperties.physicalpropertymethods.PhysicalPropertyMethod;
import neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface;

/**
 * <p>
 * LiquidPhysicalPropertyMethod class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public abstract class LiquidPhysicalPropertyMethod extends PhysicalPropertyMethod {
  private static final long serialVersionUID = 1000;

  protected neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface liquidPhase;

  /**
   * <p>
   * Constructor for LiquidPhysicalPropertyMethod.
   * </p>
   *
   * @param liquidPhase a
   *        {@link neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface}
   *        object
   */
  public LiquidPhysicalPropertyMethod(PhysicalPropertiesInterface liquidPhase) {
    setPhase(liquidPhase);
  }

  /** {@inheritDoc} */
  @Override
  public void setPhase(PhysicalPropertiesInterface phase) {
    this.liquidPhase = phase;
  }
}
