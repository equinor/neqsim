package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentCoutinhoWax;
import neqsim.thermo.component.ComponentWax;
import neqsim.thermo.component.ComponentWaxWilson;
import neqsim.thermo.component.ComponentWonWax;

/**
 * <p>
 * PhaseWax class.
 * </p>
 *
 * <p>
 * Supports multiple wax thermodynamic models selectable via {@link #setWaxComponentModel(String)}.
 * Available models:
 * </p>
 * <ul>
 * <li><b>"Pedersen"</b> (default) - Simple Clausius-Clapeyron model (ComponentWax)</li>
 * <li><b>"Won"</b> - Won model with activity coefficient (ComponentWonWax)</li>
 * <li><b>"Wilson"</b> - Wilson local-composition model (ComponentWaxWilson)</li>
 * <li><b>"Coutinho"</b> - Predictive UNIQUAC model (ComponentCoutinhoWax)</li>
 * </ul>
 *
 * @author esol
 * @version $Id: $Id
 */
public class PhaseWax extends PhaseSolid {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** The selected wax component model name. Default is Pedersen (ComponentWax). */
  private String waxComponentModelName = "Pedersen";

  /**
   * <p>
   * Constructor for PhaseWax.
   * </p>
   */
  public PhaseWax() {
    setType(PhaseType.WAX);
  }

  /** {@inheritDoc} */
  @Override
  public PhaseWax clone() {
    PhaseWax clonedPhase = null;
    try {
      clonedPhase = (PhaseWax) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    setType(PhaseType.WAX);
  }

  /**
   * Sets the wax component model to use for fugacity calculations.
   *
   * @param modelName one of "Pedersen", "Won", "Wilson", "Coutinho"
   */
  public void setWaxComponentModel(String modelName) {
    this.waxComponentModelName = modelName;
  }

  /**
   * Gets the name of the currently selected wax component model.
   *
   * @return the wax component model name
   */
  public String getWaxComponentModel() {
    return waxComponentModelName;
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    if ("Won".equalsIgnoreCase(waxComponentModelName)) {
      componentArray[compNumber] = new ComponentWonWax(name, moles, molesInPhase, compNumber);
    } else if ("Wilson".equalsIgnoreCase(waxComponentModelName)) {
      componentArray[compNumber] = new ComponentWaxWilson(name, moles, molesInPhase, compNumber);
    } else if ("Coutinho".equalsIgnoreCase(waxComponentModelName)) {
      componentArray[compNumber] = new ComponentCoutinhoWax(name, moles, molesInPhase, compNumber);
    } else {
      componentArray[compNumber] = new ComponentWax(name, moles, molesInPhase, compNumber);
    }
  }
}
