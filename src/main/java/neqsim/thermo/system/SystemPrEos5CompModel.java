package neqsim.thermo.system;

import neqsim.thermo.phase.PhasePrEoS_5CompModel;

/**
 * This class defines a thermodynamic system using the Peng Robinson 5 component model
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemPrEos5CompModel extends SystemPrEos1978 {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemPrEos5CompModel.
   * </p>
   */
  public SystemPrEos5CompModel() {
    this(298.15, 1.0, false);
  }

  /**
   * <p>
   * Constructor for SystemPrEos_5CompModel.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemPrEos5CompModel(double T, double P) {
    this(T, P, false);
  }

  /**
   * <p>
   * Constructor for mSystemPrEos5CompModel.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemPrEos5CompModel(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    attractiveTermNumber = 6;
    modelName = "PR78-EoS-5-component-model";

    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhasePrEoS_5CompModel();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }
  }

  /** {@inheritDoc} */
  @Override
  public SystemPrEos5CompModel clone() {
    SystemPrEos5CompModel clonedSystem = null;
    try {
      clonedSystem = (SystemPrEos5CompModel) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
