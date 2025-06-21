package neqsim.thermo.component;

/**
 * ComponentWhitsonSoreide implements a component for the Whitson-Søreide EoS.
 */
public class ComponentWhitsonSoreide extends ComponentPR {
  private static final long serialVersionUID = 1L;

  public ComponentWhitsonSoreide(String componentName, double moles, double molesInPhase, int compIndex) {
    super(componentName, moles, molesInPhase, compIndex);
  }

}
