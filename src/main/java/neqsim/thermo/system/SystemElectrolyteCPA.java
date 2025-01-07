package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseElectrolyteCPA;
import neqsim.thermo.util.constants.FurstElectrolyteConstants;

/**
 * This class defines a thermodynamic system using the Electrolyte CPA EoS of Equinor.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemElectrolyteCPA extends SystemFurstElectrolyteEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemElectrolyteCPA.
   * </p>
   */
  public SystemElectrolyteCPA() {
    this(298.15, 1.0);
  }

  /**
   * <p>
   * Constructor for SystemElectrolyteCPA.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemElectrolyteCPA(double T, double P) {
    super(T, P);
    modelName = "Electrolyte-CPA-EOS";
    attractiveTermNumber = 0;

    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseElectrolyteCPA();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }
    FurstElectrolyteConstants.setFurstParams("electrolyteCPA");
    this.useVolumeCorrection(false);
  }

  /** {@inheritDoc} */
  @Override
  public SystemElectrolyteCPA clone() {
    SystemElectrolyteCPA clonedSystem = null;
    try {
      clonedSystem = (SystemElectrolyteCPA) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
