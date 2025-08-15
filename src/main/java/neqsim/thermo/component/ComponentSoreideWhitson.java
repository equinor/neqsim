package neqsim.thermo.component;

import neqsim.thermo.component.attractiveeosterm.AttractiveTermSoreideWhitson;

/**
 * ComponentSoreideWhitson for Søreide-Whitson Peng-Robinson EoS with modified alpha.
 *
 * @author sviat
 */
public class ComponentSoreideWhitson extends ComponentPR {
  private static final long serialVersionUID = 1L;

  /**
   * <p>
   * Constructor for ComponentSoreideWhitson.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param moles a double
   * @param molesInPhase a double
   * @param compIndex a int
   */
  public ComponentSoreideWhitson(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
    setAttractiveParameter(new AttractiveTermSoreideWhitson(this));
  }

  /** {@inheritDoc} */
  @Override
  public ComponentSoreideWhitson clone() {
    ComponentSoreideWhitson clonedComponent = null;
    try {
      clonedComponent = (ComponentSoreideWhitson) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return clonedComponent;
  }
}
