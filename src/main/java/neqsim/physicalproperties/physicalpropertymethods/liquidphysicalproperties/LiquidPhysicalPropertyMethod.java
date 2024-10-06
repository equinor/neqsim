package neqsim.physicalproperties.physicalpropertymethods.liquidphysicalproperties;

/**
 * <p>
 * LiquidPhysicalPropertyMethod class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class LiquidPhysicalPropertyMethod
    extends neqsim.physicalproperties.physicalpropertymethods.PhysicalPropertyMethod {
  private static final long serialVersionUID = 1000;

  protected neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface liquidPhase;

  /**
   * <p>
   * Constructor for LiquidPhysicalPropertyMethod.
   * </p>
   */
  public LiquidPhysicalPropertyMethod() {
  }

  /**
   * <p>
   * Constructor for LiquidPhysicalPropertyMethod.
   * </p>
   *
   * @param liquidPhase a
   *        {@link neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface}
   *        object
   */
  public LiquidPhysicalPropertyMethod(
      neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface liquidPhase) {
    this.liquidPhase = liquidPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void setPhase(
      neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface phase) {
    this.liquidPhase = phase;
  }
}
