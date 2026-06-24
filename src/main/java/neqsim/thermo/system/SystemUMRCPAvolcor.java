package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseUMRCPAvolcor;

/**
 * Thermodynamic system using the volume-translated UMR-CPA equation of state.
 *
 * <p>
 * This model extends {@link SystemUMRCPAEoS} (Peng-Robinson physical term with Universal Mixing Rule driven by UNIFAC
 * group contributions, the 3-parameter Mathias-Copeman alpha, and a CPA association term) with a consistent
 * per-component Peneloux volume translation handled inside the equation of state through {@link PhaseUMRCPAvolcor}. In
 * contrast to the legacy density-only volume correction, the translation parameter and its mole/temperature derivatives
 * enter the reduced residual Helmholtz energy, so the improved volumetric behaviour is reflected consistently in
 * density, fugacity coefficients, partial molar volumes and density-derived caloric and acoustic properties (enthalpy,
 * heat capacity, Joule-Thomson coefficient, speed of sound).
 * </p>
 *
 * <p>
 * The translation acts only on the cubic part (Option A): the association term is evaluated on the physical co-volume
 * and the translated molar volume. The per-component translation equals the inherited UMR-CPA Peneloux shift (PR shift
 * for non-associating compounds, zero for associating compounds until a dedicated UMR-CPA Rackett Z is regressed). The
 * legacy density-only volume correction is therefore switched off to avoid applying the translation twice.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemUMRCPAvolcor extends SystemUMRCPAEoS {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for SystemUMRCPAvolcor.
   */
  public SystemUMRCPAvolcor() {
    this(298.15, 1.0);
  }

  /**
   * Constructor for SystemUMRCPAvolcor.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemUMRCPAvolcor(double T, double P) {
    super(T, P);
    modelName = "UMR-CPA-volcor";
    // The translation is handled inside the EOS (Helmholtz C machinery); disable the legacy
    // density-only volume correction so the shift is not applied twice.
    useVolumeCorrection(false);

    // Recreate the phases created by the SystemUMRCPAEoS / SystemPrEos super constructors.
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseUMRCPAvolcor();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
      phaseArray[i].useVolumeCorrection(false);
    }
  }
}
