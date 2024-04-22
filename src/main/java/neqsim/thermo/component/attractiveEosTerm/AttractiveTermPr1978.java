package neqsim.thermo.component.attractiveEosTerm;

import neqsim.thermo.component.ComponentEosInterface;

/**
 * <p>
 * AttractiveTermPr1978 class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class AttractiveTermPr1978 extends AttractiveTermPr {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for AttractiveTermPr1978.
   * </p>
   *
   * @param component a {@link neqsim.thermo.component.ComponentEosInterface} object
   */
  public AttractiveTermPr1978(ComponentEosInterface component) {
    super(component);
    if (component.getAcentricFactor() > 0.49) {
      m = (0.379642 + 1.48503 * component.getAcentricFactor()
          - 0.164423 * component.getAcentricFactor() * component.getAcentricFactor()
          + 0.01666 * Math.pow(component.getAcentricFactor(), 3.0));
    } else {
      m = (0.37464 + 1.54226 * component.getAcentricFactor()
          - 0.26992 * component.getAcentricFactor() * component.getAcentricFactor());
    }
  }

  /** {@inheritDoc} */
  @Override
  public AttractiveTermPr1978 clone() {
    AttractiveTermPr1978 attractiveTerm = null;
    try {
      attractiveTerm = (AttractiveTermPr1978) super.clone();
    } catch (Exception ex) {
      
    }

    return attractiveTerm;
  }

  /** {@inheritDoc} */
  @Override
  public void init() {
    if (getComponent().getAcentricFactor() > 0.49) {
      m = (0.379642 + 1.48503 * getComponent().getAcentricFactor()
          - 0.164423 * getComponent().getAcentricFactor() * getComponent().getAcentricFactor()
          + 0.01666 * Math.pow(getComponent().getAcentricFactor(), 3.0));
    } else {
      m = (0.37464 + 1.54226 * getComponent().getAcentricFactor()
          - 0.26992 * getComponent().getAcentricFactor() * getComponent().getAcentricFactor());
    }
  }
}
