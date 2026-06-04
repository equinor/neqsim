package neqsim.thermo.system;

import neqsim.thermo.phase.PhasePrEosvolcor;

/**
 * Thermodynamic system using the UMR-PRU equation of state with a 5-parameter Mathias-Copeman
 * pure-component alpha function (research / extended variant).
 *
 * <p>
 * This variant sets attractive term number 19, which selects a 5-parameter Mathias-Copeman alpha
 * function with its own set of pure-component parameters (see
 * {@code neqsim.thermo.component.attractiveeosterm.AtractiveTermMatCopPRUMRNew}). Like
 * {@link SystemUMRPRUMCEos} (term 13), this variant uses the {@code _umrmc} group-interaction
 * parameter tables; the two variants differ only in the pure-component alpha function. The
 * table-routing decision is centralised in
 * {@code neqsim.thermo.phase.PhaseGEUnifacUMRPRU.useMcInteractionParameters()}.
 * </p>
 *
 * <p>
 * The constructor instantiates {@link PhasePrEosvolcor} phases but explicitly disables the Peneloux
 * volume translation via {@code useVolumeCorrection(false)}. The volume-corrected phase class is
 * used so that the same code path can later be switched to apply Peneloux translation if needed; it
 * is disabled here to keep the predicted molar volumes consistent with the parent UMR-PRU-MC model,
 * which is regressed without volume translation.
 * </p>
 *
 * @author Even Solbraa
 */
public class SystemUMRPRUMCEosNew extends SystemUMRPRUMCEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemUMRPRUMCEosNew.
   * </p>
   */
  public SystemUMRPRUMCEosNew() {
    this(298.15, 1.0);
  }

  /**
   * <p>
   * Constructor for SystemUMRPRUMCEosNew.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemUMRPRUMCEosNew(double T, double P) {
    super(T, P);
    modelName = "UMR-PRU-MC-EoS-New";
    attractiveTermNumber = 19;

    useVolumeCorrection(false);
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhasePrEosvolcor();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
      phaseArray[i].useVolumeCorrection(false);
    }
  }
}
