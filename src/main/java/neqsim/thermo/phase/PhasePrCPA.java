package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentPrCPA;

/**
 * Peng-Robinson CPA phase using the existing PR-based association solver and analytical site derivatives.
 *
 * <p>
 * The solver is shared with {@link PhaseUMRCPA}, while {@link ComponentPrCPA} selects the generic PR-CPA database
 * parameters and PR alpha rather than UMR-CPA parameters. The public phase type and PR cubic inheritance are retained.
 * Legacy Java-serialized instances are deliberately rejected: their SRK components and incomplete association state
 * cannot represent this repaired model. Recreate them with {@link neqsim.thermo.system.SystemPrCPA}.
 * </p>
 *
 * @author Even Solbraa
 * @version 2.0
 */
public class PhasePrCPA extends PhaseUMRCPA {
  /** Rejects archives containing the former incomplete legacy association implementation. */
  private static final long serialVersionUID = 1001;

  /** Creates a PR-CPA phase without selecting UMR mixing or parameter sets. */
  public PhasePrCPA() {
    thermoPropertyModelName = "PR-CPA-EoS";
  }

  /** {@inheritDoc} */
  @Override
  public PhasePrCPA clone() {
    return (PhasePrCPA) super.clone();
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, moles, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentPrCPA(name, moles, molesInPhase, compNumber);
  }

  /**
   * Returns the legacy association temperature-derivative quantity from the consistent Helmholtz derivative.
   *
   * @return minus twice the association Helmholtz temperature derivative at fixed volume and amounts
   */
  public double calc_hCPAdT() {
    return -2.0 * dFCPAdT();
  }

  /**
   * Returns the legacy second-temperature-derivative quantity including association-site response.
   *
   * @return minus twice the second association Helmholtz temperature derivative at fixed volume and amounts
   */
  public double calc_hCPAdTdT() {
    return -2.0 * dFCPAdTdT();
  }
}
