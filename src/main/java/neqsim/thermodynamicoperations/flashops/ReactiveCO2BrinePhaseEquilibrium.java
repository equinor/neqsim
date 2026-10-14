package neqsim.thermodynamicoperations.flashops;

import java.util.concurrent.CancellationException;
import neqsim.thermo.system.SystemInterface;

/**
 * Compatibility adapter for the shared aqueous reaction/phase coupling.
 *
 * <p>
 * The calculation now admits neutral molecules by their thermodynamic properties rather than a CO2/water whitelist. Use
 * the ordinary TP-flash entry point for new calculations.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 * @deprecated use {@link TPflash} for fluid and aqueous reaction equilibrium
 */
@Deprecated
public final class ReactiveCO2BrinePhaseEquilibrium {
  private final AqueousReactionEquilibrium calculation;

  /**
   * Creates a compatibility calculation without changing the supplied fluid.
   *
   * @param system reactive ionic fluid
   */
  public ReactiveCO2BrinePhaseEquilibrium(SystemInterface system) {
    calculation = new AqueousReactionEquilibrium(system);
  }

  /**
   * Checks physical phase and reaction capabilities, without a component-name whitelist.
   *
   * @param fluid fluid to inspect
   * @return whether the shared aqueous reaction coupling supports the phase roles
   */
  public static boolean isApplicable(SystemInterface fluid) {
    return AqueousReactionEquilibrium.isApplicable(fluid);
  }

  /**
   * Solves phase and reaction equilibrium with the shared coupling.
   *
   * @throws IllegalArgumentException if the physical phase roles are unsupported
   * @throws IllegalStateException if equilibrium or conservation fails
   * @throws CancellationException if interrupted
   */
  public void run() {
    calculation.run();
  }

  /**
   * Returns the number of phase/reaction coupling iterations.
   *
   * @return iterations in the most recent calculation
   */
  public int getIterations() {
    return calculation.getIterations();
  }

  /**
   * Returns stability evidence from the general fluid calculation.
   *
   * @return minimum normalized molecular trial distance, or NaN when unavailable
   */
  public double getMinimumTrialDistance() {
    return calculation.getMinimumTrialDistance();
  }
}
