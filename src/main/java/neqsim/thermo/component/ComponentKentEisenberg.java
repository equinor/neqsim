package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * ComponentKentEisenberg class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentKentEisenberg extends ComponentGeNRTL {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for ComponentKentEisenberg.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentKentEisenberg(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
  }

  /** {@inheritDoc} */
  @Override
  public double fugcoef(PhaseInterface phase) {
    double gamma = 1.0;
    if (referenceStateType.equals("solvent")) {
      fugacityCoefficient =
          gamma * getAntoineVaporPressure(phase.getTemperature()) / phase.getPressure();
      gammaRefCor = gamma;
    } else {
      double activinf = 1.0;
      if (ionicCharge == 0) {
        fugacityCoefficient = activinf * getHenryCoef(phase.getTemperature()) / phase.getPressure();
      } else {
        fugacityCoefficient = 1e8;
      }
      gammaRefCor = activinf;
    }

    return fugacityCoefficient;
  }
}
