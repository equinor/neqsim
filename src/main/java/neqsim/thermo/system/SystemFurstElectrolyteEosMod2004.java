package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseModifiedFurstElectrolyteEosMod2004;

/**
 * This class defines a thermodynamic system using the electrolyte the Modified Furst Electrolyte
 * Eos.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemFurstElectrolyteEosMod2004 extends SystemSrkEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemFurstElectrolyteEosMod2004.
   * </p>
   */
  public SystemFurstElectrolyteEosMod2004() {
    this(298.15, 1.0);
  }

  /**
   * <p>
   * Constructor for SystemFurstElectrolyteEosMod2004.
   * </p>
   *
   * @param T a double
   * @param P a double
   */
  public SystemFurstElectrolyteEosMod2004(double T, double P) {
    super(T, P);
    modelName = "Electrolyte-ScRK-EOS";
    attractiveTermNumber = 2;

    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseModifiedFurstElectrolyteEosMod2004();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }
  }

  /** {@inheritDoc} */
  @Override
  public SystemFurstElectrolyteEosMod2004 clone() {
    SystemFurstElectrolyteEosMod2004 clonedSystem = null;
    try {
      clonedSystem = (SystemFurstElectrolyteEosMod2004) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
