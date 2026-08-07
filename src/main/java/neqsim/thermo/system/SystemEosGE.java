package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseType;

/**
 * Base class for gamma-phi systems combining equation-of-state and excess-Gibbs-energy phases.
 *
 * <p>
 * The default topology shared by Wilson, NRTL, UNIFAC and specialised activity models has creation-order slot
 * {@code phaseArray[0]} as the EOS vapour phase and subsequent fluid slots as independent clones of the GE liquid
 * phase. Active phases can later be reordered through {@code phaseIndex}, so {@code getPhase(0)} is not guaranteed to
 * return the EOS slot. Concrete systems remain responsible for selecting the EOS, activity model, mixing rules and
 * parameter sources. A future hybrid gas-oil-aqueous implementation can extend this base with separate EOS gas and oil
 * slots plus a GE aqueous slot; it must also provide a matching multiphase flash strategy rather than using the default
 * two-phase direct gamma-phi preparation.
 * </p>
 *
 * @author NeqSim
 */
public abstract class SystemEosGE extends SystemEos implements EosGeFlashModel {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for an EOS-GE system.
   *
   * @param temperature temperature in kelvin
   * @param pressure pressure in bara
   * @param checkForSolids whether a pure-component solid phase should be available
   */
  protected SystemEosGE(double temperature, double pressure, boolean checkForSolids) {
    super(temperature, pressure, checkForSolids);
  }

  /**
   * Configure the common EOS-GE phase topology.
   *
   * @param temperature temperature in kelvin
   * @param pressure pressure in bara
   * @param eosPhase equation-of-state vapour phase
   * @param gePhase excess-Gibbs-energy liquid phase
   */
  protected final void configureEosGePhases(double temperature, double pressure, PhaseInterface eosPhase,
      PhaseInterface gePhase) {
    phaseArray[0] = eosPhase;
    initialisePhase(0, temperature, pressure, PhaseType.GAS);
    phaseArray[1] = gePhase;
    initialisePhase(1, temperature, pressure, PhaseType.LIQUID);

    if (solidPhaseCheck) {
      setNumberOfPhases(4);
      phaseArray[2] = gePhase.clone();
      initialisePhase(2, temperature, pressure, PhaseType.LIQUID);
      phaseArray[3] = new PhasePureComponentSolid();
      initialisePhase(3, temperature, pressure, PhaseType.SOLID);
      phaseArray[3].setRefPhase(phaseArray[1].getRefPhase());
    }
  }

  /**
   * Restore the default two-phase direct gamma-phi active-phase contract after density ordering or a prior single-phase
   * collapse.
   *
   * <p>
   * Specialized hybrid systems may override this method to restore a different phase-role topology. Their flash solver
   * must use the same phase-role contract.
   * </p>
   */
  @Override
  public void prepareGammaPhiFlash() {
    setNumberOfPhases(2);
    setPhaseIndex(0, 0);
    setPhaseIndex(1, 1);
  }

  /**
   * Get the equation-of-state phase from its creation-order slot.
   *
   * <p>
   * This deliberately bypasses active-phase ordering: after density ordering, {@code getPhase(0)} may identify a
   * different phase through {@code phaseIndex}.
   * </p>
   *
   * @return EOS phase
   */
  public final PhaseInterface getEquationOfStatePhase() {
    return phaseArray[0];
  }

  /**
   * Check whether a phase index identifies an excess-Gibbs-energy phase.
   *
   * @param phaseNumber active phase number
   * @return {@code true} when the phase is a GE phase
   */
  public final boolean isExcessGibbsEnergyPhase(int phaseNumber) {
    return getPhase(phaseNumber) instanceof neqsim.thermo.phase.PhaseGEInterface;
  }

  /**
   * Initialise one phase and its system phase-type metadata.
   *
   * @param phaseNumber phase-array index to initialise
   * @param temperature temperature in kelvin
   * @param pressure pressure in bara
   * @param phaseType phase type
   */
  private void initialisePhase(int phaseNumber, double temperature, double pressure, PhaseType phaseType) {
    PhaseInterface phase = phaseArray[phaseNumber];
    phase.setTemperature(temperature);
    phase.setPressure(pressure);
    phase.setType(phaseType);
    setPhaseType(phaseNumber, phaseType);
  }
}
