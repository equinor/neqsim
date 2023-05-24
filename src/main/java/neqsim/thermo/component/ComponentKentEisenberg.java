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
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for ComponentKentEisenberg.
   * </p>
   *
   * @param component_name a {@link java.lang.String} object
   * @param moles a double
   * @param molesInPhase a double
   * @param compnumber a int
   */
  public ComponentKentEisenberg(String component_name, double moles, double molesInPhase,
      int compnumber) {
    super(component_name, moles, molesInPhase, compnumber);
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
