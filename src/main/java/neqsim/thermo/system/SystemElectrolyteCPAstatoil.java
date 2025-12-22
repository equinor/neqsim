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

  /**
   * Set the mixed-solvent enhancement exponent for electrolyte terms.
   *
   * <p>
   * This parameter controls how much the electrolyte contributions (MSA long-range, Born solvation,
   * and short-range SR2 terms) are enhanced in mixed solvents with lower dielectric constant than
   * pure water. The enhancement factor is calculated as (78.4 / eps_solvent)^exponent.
   * </p>
   *
   * <p>
   * Use this when modeling electrolytes in mixed organic-aqueous solvents such as MEG-water or
   * methanol-water. The standard electrolyte CPA model underestimates ionic interactions in lower
   * dielectric media.
   * </p>
   *
   * <p>
   * Recommended values:
   * </p>
   * <ul>
   * <li>0.0 (default): Pure water, no enhancement</li>
   * <li>0.5-1.0: EG-water mixtures (0-40% EG)</li>
   * <li>1.0-2.0: Methanol-water or higher organic content</li>
   * </ul>
   *
   * @param exponent The enhancement exponent (0.0 to disable)
   */
  public void setMixedSolventEnhancementExponent(double exponent) {
    for (int i = 0; i < numberOfPhases; i++) {
      if (phaseArray[i] instanceof PhaseModifiedFurstElectrolyteEos) {
        ((PhaseModifiedFurstElectrolyteEos) phaseArray[i])
            .setMixedSolventEnhancementExponent(exponent);
      }
    }
  }

  /**
   * Get the mixed-solvent enhancement exponent for electrolyte terms.
   *
   * @return The enhancement exponent from phase 0
   */
  public double getMixedSolventEnhancementExponent() {
    if (phaseArray[0] instanceof PhaseModifiedFurstElectrolyteEos) {
      return ((PhaseModifiedFurstElectrolyteEos) phaseArray[0])
          .getMixedSolventEnhancementExponent();
    }
    return 0.0;
  }
}
