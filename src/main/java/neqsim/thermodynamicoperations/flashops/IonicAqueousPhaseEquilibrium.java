package neqsim.thermodynamicoperations.flashops;

import neqsim.thermo.system.SystemInterface;

/**
 * Dual-cubic-root phase search for non-reactive water-rich ionic feeds.
 *
 * <p>
 * The search starts from a conserved aqueous reference and independently seeds every positive molecular solute on both
 * GAS and OIL roots. Ions remain confined to the aqueous phase. A result is accepted only after the shared solver
 * qualifies tangent-plane instability, component inventory, molecular fugacity equality, normalization, and Gibbs
 * energy. This is a numerical phase-selection operation and does not alter thermodynamic parameters or model equations.
 * </p>
 *
 * @author OpenAI
 * @version 1.0
 */
final class IonicAqueousPhaseEquilibrium {
  private final CO2BrinePhaseEquilibrium rootSearch;

  /**
   * Creates a root search without modifying the supplied system.
   *
   * @param system non-reactive water-rich electrolyte-CPA feed
   */
  IonicAqueousPhaseEquilibrium(SystemInterface system) {
    rootSearch = new CO2BrinePhaseEquilibrium(system);
  }

  /**
   * Checks the strict model and composition scope of the numerical rescue.
   *
   * @param system system to inspect
   * @return true when a dual-root ionic aqueous search is supported
   */
  static boolean isApplicable(SystemInterface system) {
    return CO2BrinePhaseEquilibrium.isIonicAqueousRootSearchApplicable(system);
  }

  /** Runs the conserved dual-root search and copies only a qualified result to the caller. */
  void run() {
    rootSearch.runIonicAqueousRootSearch();
  }
}
