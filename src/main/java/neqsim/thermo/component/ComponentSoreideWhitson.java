package neqsim.thermo.component;

import neqsim.thermo.component.attractiveeosterm.AttractiveTermSoreideWhitson;

/**
 * ComponentSoreideWhitson for Søreide-Whitson Peng-Robinson EoS with modified
 * alpha.
 */
public class ComponentSoreideWhitson extends ComponentEos {
  private static final long serialVersionUID = 1L;

  public ComponentSoreideWhitson(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
    a = .45724333333 * R * R * criticalTemperature * criticalTemperature / criticalPressure;
    b = .077803333 * R * criticalTemperature / criticalPressure;
    delta1 = 1.0 + Math.sqrt(2.0);
    delta2 = 1.0 - Math.sqrt(2.0);
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
