package neqsim.physicalproperties.physicalpropertymethods.commonphasephysicalproperties;

import neqsim.physicalproperties.physicalpropertymethods.PhysicalPropertyMethod;
import neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface;

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

  protected neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface phase;

  /**
   * <p>
   * Constructor for CommonPhysicalPropertyMethod.
   * </p>
   *
   * @param phase a
   *        {@link neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface}
   *        object
   */
  public CommonPhysicalPropertyMethod(PhysicalPropertiesInterface phase) {
    this.phase = phase;
  }

  /** {@inheritDoc} */
  @Override
  public void setPhase(PhysicalPropertiesInterface phase) {
    this.phase = phase;
  }
}
