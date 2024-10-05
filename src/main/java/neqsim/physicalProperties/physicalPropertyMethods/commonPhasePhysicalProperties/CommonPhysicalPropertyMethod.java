package neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties;

/**
 * <p>
 * CommonPhysicalPropertyMethod class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class CommonPhysicalPropertyMethod
    extends neqsim.physicalProperties.physicalPropertyMethods.PhysicalPropertyMethod {
  private static final long serialVersionUID = 1000;

  protected neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface phase;

  /**
   * <p>
   * Constructor for CommonPhysicalPropertyMethod.
   * </p>
   *
   * @param phase a
   *        {@link neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface}
   *        object
   */
  public CommonPhysicalPropertyMethod(
      neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface phase) {
    this.phase = phase;
  }

  /** {@inheritDoc} */
  @Override
  public void setPhase(
      neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface phase) {
    this.phase = phase;
  }
}
