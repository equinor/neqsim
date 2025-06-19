package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentWhitsonSoreide;

/**
 * PhaseWhitsonSoreide implements a phase for the Whitson-Søreide EoS.
 */
public class PhaseWhitsonSoreide extends PhasePrEos {
  private static final long serialVersionUID = 1L;

  public PhaseWhitsonSoreide() {
    super();
    thermoPropertyModelName = "Soreide-Whitson-PR-EoS";
  }

  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentWhitsonSoreide(name, moles, molesInPhase, compNumber);
  }
}
