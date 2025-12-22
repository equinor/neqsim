package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseElectrolyteCPAstatoil;
import neqsim.thermo.phase.PhaseModifiedFurstElectrolyteEos;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.util.constants.FurstElectrolyteConstants;

/**
 * This class defines a thermodynamic system using the electrolyte CPA EoS Statoil model.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemElectrolyteCPAstatoil extends SystemFurstElectrolyteEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemElectrolyteCPAstatoil.
   * </p>
   */
  public SystemElectrolyteCPAstatoil() {
    this(298.15, 1.0);
  }

  /**
   * <p>
   * Constructor for SystemElectrolyteCPAstatoil.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemElectrolyteCPAstatoil(double T, double P) {
    super(T, P);
    modelName = "Electrolyte-CPA-EOS-statoil";
    attractiveTermNumber = 15;

    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseElectrolyteCPAstatoil();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }
    FurstElectrolyteConstants.setFurstParams("electrolyteCPA");
    this.useVolumeCorrection(true);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Override to set phase 0 as AQUEOUS for electrolyte systems. This ensures the molar volume
   * solver uses a liquid-like initial guess and finds the correct liquid root at low pressures,
   * which is essential for accurate activity coefficient calculations in aqueous electrolyte
   * solutions.
   * </p>
   */
  @Override
  public void reInitPhaseType() {
    phaseType[0] = PhaseType.AQUEOUS;
    phaseType[1] = PhaseType.LIQUID;
    phaseType[2] = PhaseType.LIQUID;
    phaseType[3] = PhaseType.LIQUID;
  }

  /** {@inheritDoc} */
  @Override
  public SystemElectrolyteCPAstatoil clone() {
    SystemElectrolyteCPAstatoil clonedSystem = null;
    try {
      clonedSystem = (SystemElectrolyteCPAstatoil) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
