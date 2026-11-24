package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseElectrolyteCPAstatoil;

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
   * Constructor for SystemElectrolyteCPAstatoil.
   */
  public SystemElectrolyteCPAstatoil() {
    this(298.15, 1.0);
  }

  /**
   * Constructor for SystemElectrolyteCPAstatoil.
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
    this.useVolumeCorrection(true);
  }

  /**
   * Select the caloric-data-fitted water alpha for this electrolyte CPA system.
   *
   * <p>
   * The default is legacy CPA. The selection can be made before or after component and reaction initialization. Apply
   * the mixing rule after the last species is added. Reflash and initialize properties after switching alpha. The water
   * attraction changes in all fluid phases and in their aqueous reference states. See docs/thermo/cpa_water_caloric.md
   * for the brine comparison and property-specific validation limits.
   * </p>
   *
   * @param enabled true to use the caloric water alpha, false to restore the legacy alpha
   */
  public void setUseCaloricWaterAlpha(boolean enabled) {
    setAttractiveTermAndInvalidateReferences(enabled ? 23 : 15);
  }

  /**
   * Report whether the caloric water alpha is selected.
   *
   * @return true if the caloric water alpha is selected
   */
  public boolean isUsingCaloricWaterAlpha() {
    return attractiveTermNumber == 23;
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
