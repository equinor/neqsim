package neqsim.thermo.component;

import neqsim.thermo.component.attractiveeosterm.AttractiveTermSoreideWhitson;

/**
 * ComponentSoreideWhitson for Søreide-Whitson Peng-Robinson EoS with modified alpha.
 */
public class ComponentSoreideWhitson extends ComponentPR {
  private static final long serialVersionUID = 1L;

  public ComponentSoreideWhitson(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
    setAttractiveParameter(new AttractiveTermSoreideWhitson(this));
  }

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
