package neqsim.physicalproperties.physicalpropertymethods.commonphasephysicalproperties;

/**
 * <p>
 * CommonPhysicalPropertyMethod class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class CommonPhysicalPropertyMethod
    extends neqsim.physicalproperties.physicalpropertymethods.PhysicalPropertyMethod {
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
  public CommonPhysicalPropertyMethod(
      neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface phase) {
    this.phase = phase;
  }

  /** {@inheritDoc} */
  @Override
  public void setPhase(
      neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface phase) {
    this.phase = phase;
  }
}
