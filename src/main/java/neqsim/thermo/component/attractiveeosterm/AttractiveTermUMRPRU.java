package neqsim.thermo.component.attractiveeosterm;

import neqsim.thermo.component.ComponentEosInterface;

/**
 * <p>
 * AttractiveTermUMRPRU class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class AttractiveTermUMRPRU extends AttractiveTermPr {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for AttractiveTermUMRPRU.
   * </p>
   *
   * @param component a {@link neqsim.thermo.component.ComponentEosInterface} object
   */
  public AttractiveTermUMRPRU(ComponentEosInterface component) {
    super(component);
    m = (0.384401 + 1.52276 * component.getAcentricFactor()
        - 0.213808 * component.getAcentricFactor() * component.getAcentricFactor()
        + 0.034616 * Math.pow(component.getAcentricFactor(), 3.0)
        - 0.001976 * Math.pow(component.getAcentricFactor(), 4.0));
  }

  /** {@inheritDoc} */
  @Override
  public AttractiveTermUMRPRU clone() {
    AttractiveTermUMRPRU attractiveTerm = null;
    try {
      attractiveTerm = (AttractiveTermUMRPRU) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return attractiveTerm;
  }

  /** {@inheritDoc} */
  @Override
  public void init() {
    m = (0.384401 + 1.52276 * getComponent().getAcentricFactor()
        - 0.213808 * getComponent().getAcentricFactor() * getComponent().getAcentricFactor()
        + 0.034616 * Math.pow(getComponent().getAcentricFactor(), 3.0)
        - 0.001976 * Math.pow(getComponent().getAcentricFactor(), 4.0));
  }
}
