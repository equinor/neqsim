package neqsim.thermodynamicoperations.flashops;

import java.util.concurrent.CancellationException;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.EosGeFlashModel;
import neqsim.thermo.system.SystemInterface;

/**
 * Conservative local equilibration of ions shared by multiple aqueous liquid phases.
 *
 * <p>
 * The ordinary fixed-species amount Newton solver is projected onto charge-neutral transfer directions. A detached
 * candidate is adopted only after molecular and electrochemical stationarity, inventory, charge and Gibbs checks. The
 * fixed phase set and volume-root choices are retained; phase stability and model accuracy remain separate.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
final class ElectroneutralLiquidPartition {
  /** Prevents construction of the stateless helper. */
  private ElectroneutralLiquidPartition() {
  }

  /**
   * Identifies the explicitly supported aqueous-liquid role at an initialized endpoint.
   *
   * <p>
   * Both composition-based AQUEOUS classification and a configured liquid-family root are required. A LIQUID root
   * reported as GAS is not an ionic host, and a GAS-root trial that acquires an aqueous label does not create another
   * host. Root selection and broader phase stability remain separate operations; this helper never relabels a phase to
   * make it admissible.
   * </p>
   *
   * @param system initialized fluid
   * @param phase logical phase index
   * @return whether this endpoint phase is a supported aqueous liquid
   */
  static boolean isLiquidHost(SystemInterface system, int phase) {
    PhaseType root = system.getPhaseType(phase);
    return system.getPhase(phase).getType() == PhaseType.AQUEOUS
        && (root == PhaseType.LIQUID || root == PhaseType.OIL || root == PhaseType.AQUEOUS);
  }

  /**
   * Counts aqueous material liquids that can support distributed ionic inventory.
   *
   * @param system initialized fluid
   * @return number of eligible liquid hosts
   */
  static int liquidHosts(SystemInterface system) {
    int count = 0;
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      if (isLiquidHost(system, phase)) {
        count++;
      }
    }
    return count;
  }

  /**
   * Detects actual occupation of multiple eligible aqueous liquids by charged species.
   *
   * @param system initialized fluid
   * @return whether more than one eligible liquid contains a positive ionic amount
   */
  static boolean hasDistributedIons(SystemInterface system) {
    int occupied = 0;
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      if (!isLiquidHost(system, phase)) {
        continue;
      }
      boolean ionic = false;
      for (int component = 0; component < system.getNumberOfComponents(); component++) {
        ionic |= system.getComponent(component).getIonicCharge() != 0.0
            && system.getPhase(phase).getComponent(component).getx() > 0.0;
      }
      if (ionic && ++occupied > 1) {
        return true;
      }
    }
    return false;
  }

  /**
   * Equilibrates a supported multiple-liquid ionic active set without modifying the incumbent on failure.
   *
   * @param system qualified incumbent fluid endpoint
   * @return whether the endpoint was refined
   * @throws IllegalStateException if a supported endpoint cannot be qualified in the larger charge-neutral space
   */
  static boolean refine(SystemInterface system) {
    if (system instanceof EosGeFlashModel || system.isChemicalSystem() || system.doSolidPhaseCheck()
        || system.isMultiphaseWaxCheck() || !FlashPhaseConstraints.hasIonicInventory(system)
        || liquidHosts(system) < 2) {
      return false;
    }
    for (int component = 0; component < system.getNumberOfComponents(); component++) {
      // Legacy undissociated salts may be tagged aqueous-only without an ionic charge. Their existing
      // single-host constraint remains supported, but they cannot enter this fully charged null-space basis.
      if (system.getComponent(component).getNumberOfmoles() > 0.0
          && FlashPhaseConstraints.isIon(system.getComponent(component))
          && system.getComponent(component).getIonicCharge() == 0.0) {
        return false;
      }
    }
    double[] input = FluidEquilibriumChecks.inventory(system);
    double initialGibbs = system.getGibbsEnergy();
    RuntimeException lastFailure = null;
    for (double fraction : new double[] {1.0e-3, 1.0e-2}) {
      checkInterrupted();
      SystemInterface candidate = system.clone();
      try {
        ElectroneutralPhaseCoordinates coordinates = new ElectroneutralPhaseCoordinates(candidate);
        coordinates.seed(candidate, fraction);
        SysNewtonRhapsonTPflash solver = new SysNewtonRhapsonTPflash(candidate, candidate.getNumberOfPhases(),
            candidate.getNumberOfComponents(), true);
        boolean converged = false;
        for (int iteration = 0; iteration < 50; iteration++) {
          checkInterrupted();
          if (solver.solve() <= 1.0e-10) {
            converged = true;
            break;
          }
        }
        if (!converged) {
          throw new IllegalStateException("Electroneutral liquid partition did not converge");
        }
        candidate.init(3);
        FluidEquilibriumChecks.validate(candidate, input);
        for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
          if (candidate.getPhaseType(phase) != system.getPhaseType(phase)) {
            throw new IllegalStateException("Electroneutral liquid partition changed its fixed volume root");
          }
        }
        double gibbs = candidate.getGibbsEnergy();
        double roundoff = 32.0 * Math.ulp(Math.abs(initialGibbs));
        if (!Double.isFinite(initialGibbs) || !Double.isFinite(gibbs) || gibbs > initialGibbs + roundoff) {
          throw new IllegalStateException("Electroneutral liquid partition increased Gibbs energy");
        }
        checkInterrupted();
        FlashPhaseState.copyInitializedPhases(candidate, system);
        return true;
      } catch (CancellationException interrupted) {
        throw interrupted;
      } catch (RuntimeException failure) {
        checkInterrupted();
        lastFailure = failure;
      }
    }
    throw new IllegalStateException("Unable to equilibrate ions between aqueous liquids", lastFailure);
  }

  /**
   * Preserves caller cancellation while all work remains detached from the incumbent.
   *
   * @throws CancellationException if the current thread is interrupted
   */
  private static void checkInterrupted() {
    if (Thread.currentThread().isInterrupted()) {
      throw new CancellationException("Electroneutral liquid partition interrupted");
    }
  }

}
