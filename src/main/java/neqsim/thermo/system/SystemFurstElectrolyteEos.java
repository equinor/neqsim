package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseModifiedFurstElectrolyteEos;

/**
 * This class defines a thermodynamic system using the electrolyte the Furst Electrolyte Eos.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemFurstElectrolyteEos extends SystemSrkEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemFurstElectrolyteEos.
   * </p>
   */
  public SystemFurstElectrolyteEos() {
    this(298.15, 1.0);
  }

  /**
   * <p>
   * Constructor for SystemFurstElectrolyteEos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemFurstElectrolyteEos(double T, double P) {
    super(T, P);
    modelName = "Electrolyte-ScRK-EOS";
    attractiveTermNumber = 2;

    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseModifiedFurstElectrolyteEos();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }
  }

  /** {@inheritDoc} */
  @Override
  public SystemFurstElectrolyteEos clone() {
    SystemFurstElectrolyteEos clonedSystem = null;
    try {
      clonedSystem = (SystemFurstElectrolyteEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
