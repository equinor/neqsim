package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseSrkCPAs;

/**
 * This class defines a thermodynamic system using the CPA-EOS of Equinor equation of state.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemSrkCPAstatoil extends SystemSrkCPAs {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor of a fluid object using the CPA-EoS version of Equinor.
   */
  public SystemSrkCPAstatoil() {
    this(298.15, 1.0, false);
  }

  /**
   * Constructor of a fluid object using the CPA-EoS version of Equinor.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemSrkCPAstatoil(double T, double P) {
    this(T, P, false);
  }

  /**
   * Constructor of a fluid object using the CPA-EoS version of Equinor.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemSrkCPAstatoil(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "CPAs-SRK-EOS-statoil";
    attractiveTermNumber = 15;

    // Recreates phases created in super constructor SystemSrkCPAs
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseSrkCPAs();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }
    this.useVolumeCorrection(true);

    if (solidPhaseCheck) {
      phaseArray[numberOfPhases - 1] = new PhasePureComponentSolid();
      phaseArray[numberOfPhases - 1].setTemperature(T);
      phaseArray[numberOfPhases - 1].setPressure(P);
      phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
    }

    if (hydrateCheck) {
      phaseArray[numberOfPhases - 1] = new PhaseHydrate();
      phaseArray[numberOfPhases - 1].setTemperature(T);
      phaseArray[numberOfPhases - 1].setPressure(P);
      phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
    }
  }

  /**
   * Select the caloric-data-fitted water alpha function while retaining legacy CPA for all other components.
   *
   * <p>
   * Disabled by default. Can be called before or after adding components; reflash and initialize properties after
   * changing the setting. Pure-water heat capacity is calibrated at 5-60 degrees C near 1 bar, with independent caloric
   * checks through 150 degrees C and 100 bar. Mixture equilibrium/caloric accuracy, electrolytes and near-critical
   * behavior require separate qualification. No ideal-gas heat capacity or association parameters are changed. See
   * docs/thermo/cpa_water_caloric.md.
   * </p>
   *
   * @param enabled true to use the caloric water alpha, false to restore the legacy alpha
   */
  public void setUseCaloricWaterAlpha(boolean enabled) {
    attractiveTermNumber = enabled ? 23 : 15;
    setAttractiveTerm(attractiveTermNumber);
    // Reference phases may already contain the other alpha, and clones can share those references.
    // Invalidate the arrays instead of modifying reference-phase objects in place.
    for (int i = 0; i < getMaxNumberOfPhases(); i++) {
      if (phaseArray[i] != null) {
        phaseArray[i].setRefPhase((PhaseInterface[]) null);
      }
    }
  }

  /**
   * Check whether the opt-in caloric water alpha is selected.
   *
   * @return true if the caloric water alpha is selected
   */
  public boolean isUsingCaloricWaterAlpha() {
    return attractiveTermNumber == 23;
  }

  /** {@inheritDoc} */
  @Override
  public SystemSrkCPAstatoil clone() {
    SystemSrkCPAstatoil clonedSystem = null;
    try {
      clonedSystem = (SystemSrkCPAstatoil) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
