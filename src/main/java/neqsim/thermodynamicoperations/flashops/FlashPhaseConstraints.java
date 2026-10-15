package neqsim.thermodynamicoperations.flashops;

import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * Species participation in an initialized fluid-phase topology.
 *
 * <p>
 * Neutral species may partition between every fluid phase. Charged or explicitly tagged ionic species belong to the
 * aqueous material phase. The aqueous role is captured before iteration so a density-based classification change during
 * a trial cannot move the ionic inventory. This contract does not describe multiple aqueous electrolyte phases or solid
 * participation.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
final class FlashPhaseConstraints implements java.io.Serializable {
  private static final long serialVersionUID = 1000;
  private final boolean[] ions;
  private final int phaseCount;
  private final int aqueousPhase;
  private final boolean hasIons;

  /**
   * Captures initialized phase roles without changing compositions or root selectors.
   *
   * @param system initialized fluid topology
   */
  private FlashPhaseConstraints(SystemInterface system) {
    phaseCount = system.getNumberOfPhases();
    ions = new boolean[system.getNumberOfComponents()];
    boolean ionic = false;
    for (int component = 0; component < ions.length; component++) {
      ions[component] = isIon(system.getPhase(0).getComponent(component));
      ionic |= ions[component] && system.getPhase(0).getComponent(component).getNumberOfmoles() > 0.0;
    }
    hasIons = ionic;
    int requestedAqueous = -1;
    int classifiedAqueous = -1;
    int requestedCount = 0;
    int classifiedCount = 0;
    int liquidAqueous = -1;
    int liquidAqueousCount = 0;
    int requestedLiquid = -1;
    int requestedLiquidCount = 0;
    for (int phase = 0; phase < phaseCount; phase++) {
      PhaseType requestedType = system.getPhaseType(phase);
      if (requestedType == PhaseType.LIQUID || requestedType == PhaseType.OIL || requestedType == PhaseType.AQUEOUS) {
        requestedLiquid = phase;
        requestedLiquidCount++;
      }
      if (system.getPhaseType(phase) == PhaseType.AQUEOUS) {
        requestedAqueous = phase;
        requestedCount++;
      }
      if (system.getPhase(phase).getType() == PhaseType.AQUEOUS) {
        classifiedAqueous = phase;
        classifiedCount++;
        if (system.getPhaseType(phase) != PhaseType.GAS) {
          liquidAqueous = phase;
          liquidAqueousCount++;
        }
      }
    }
    // The homogeneous feed can be labelled aqueous on both cubic roots. Prefer its requested liquid root;
    // a dense gas trial must not become a second admissible host merely because the composition label changed.
    // Conversely a gas-rich feed may have no aqueous display label yet: its unique configured liquid root hosts
    // the ions until partitioning establishes the aqueous composition. Multiple liquid roots remain ambiguous.
    aqueousPhase = requestedCount == 1 ? requestedAqueous
        : requestedCount == 0 && liquidAqueousCount == 1 ? liquidAqueous
            : requestedCount == 0 && classifiedCount == 1 ? classifiedAqueous
                : requestedCount == 0 && classifiedCount == 0 && ionic && requestedLiquidCount == 1 ? requestedLiquid
                    : -1;
  }

  /**
   * Captures the participation mask for an initialized phase topology.
   *
   * @param system fluid to inspect
   * @return immutable phase participation constraints
   */
  static FlashPhaseConstraints capture(SystemInterface system) {
    return new FlashPhaseConstraints(system);
  }

  /**
   * Identifies species restricted to the aqueous material phase.
   *
   * @param component species to inspect
   * @return whether the species is charged or explicitly tagged as an ion
   */
  static boolean isIon(ComponentInterface component) {
    return component.getIonicCharge() != 0.0 || component.isIsIon();
  }

  /**
   * Checks whether the current feed contains any strictly positive ionic inventory.
   *
   * <p>
   * Phase extraction retains registered components even when their amount is exactly zero. Those absent species do not
   * require an aqueous material host. Every positive amount, however small, retains its phase constraint; there is no
   * concentration cutoff. This does not change the public species-presence meaning of {@code hasIons()}.
   * </p>
   *
   * @param system feed whose overall species amounts are inspected
   * @return whether an ionic species has a strictly positive overall amount
   */
  static boolean hasIonicInventory(SystemInterface system) {
    for (int component = 0; component < system.getNumberOfComponents(); component++) {
      ComponentInterface species = system.getPhase(0).getComponent(component);
      if (isIon(species) && species.getNumberOfmoles() > 0.0) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks whether a species may occur in a captured logical phase.
   *
   * @param phase captured logical phase index
   * @param component species index
   * @return whether the species participates in the phase
   */
  boolean isAllowed(int phase, int component) {
    return !ions[component] || phase == aqueousPhase;
  }

  /**
   * Returns a finite stored K-value for a confined species while its amount constraint remains exact.
   *
   * @param component species index
   * @return finite constrained ratio, or NaN for a freely partitioning species
   * @throws IllegalStateException if the captured topology cannot host its ions
   */
  double constrainedK(int component) {
    if (!ions[component]) {
      return Double.NaN;
    }
    if (!supportsTwoPhaseSplit()) {
      throw new IllegalStateException("Constrained ionic ratios require one aqueous phase in a two-phase topology");
    }
    return aqueousPhase == 0 ? 1.0e40 : 1.0e-40;
  }

  /**
   * Checks that a two-phase solve has an unambiguous host for its ionic inventory.
   *
   * @return whether the current topology supports the two-phase constrained equations
   */
  boolean supportsTwoPhaseSplit() {
    return phaseCount == 2 && (!hasIons || aqueousPhase >= 0);
  }

  /**
   * Returns the captured aqueous role.
   *
   * @return aqueous logical phase index, or minus one when absent or ambiguous
   */
  int getAqueousPhase() {
    return aqueousPhase;
  }
}
