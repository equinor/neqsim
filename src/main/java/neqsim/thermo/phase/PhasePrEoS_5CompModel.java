package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentPR_5CompModel;

/**
 * <p>
 * PhasePrEos class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhasePrEoS_5CompModel extends PhasePrEos {
  private static final long serialVersionUID = 1000;



  /**
   * <p>
   * Constructor for PhasePrEoS_5CompModel.
   * </p>
   */
  public PhasePrEoS_5CompModel() {
    super();
    thermoPropertyModelName = "PR-EoS-5-Component";
  }

  /** {@inheritDoc} */
  @Override
  public PhasePrEoS_5CompModel clone() {
    PhasePrEoS_5CompModel clonedPhase = null;
    try {
      clonedPhase = (PhasePrEoS_5CompModel) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentPR_5CompModel(name, moles, molesInPhase, compNumber);
  }
}
