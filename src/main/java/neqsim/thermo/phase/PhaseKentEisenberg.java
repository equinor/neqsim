package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentKentEisenberg;

/**
 * PhaseKentEisenberg class.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseKentEisenberg extends PhaseGENRTL {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for PhaseKentEisenberg.
   */
  public PhaseKentEisenberg() {
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentKentEisenberg(name, moles, molesInPhase, compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public double getActivityCoefficient(int k, int p) {
    return 1.0;
  }
}
