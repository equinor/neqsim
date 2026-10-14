package neqsim.thermodynamicoperations.flashops;

import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Common physical acceptance checks for a frozen-species fluid equilibrium.
 *
 * <p>
 * These checks establish feasibility and molecular equilibrium of present phases. They do not replace stability
 * analysis, prove a global Gibbs minimum, or validate a thermodynamic model against measurements.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
final class FluidEquilibriumChecks {
  /** Composition and inventory tolerance on the original total-mole basis. */
  private static final double BALANCE_TOLERANCE = 1.0e-10;
  /** Maximum molecular log-fugacity mismatch between material phases. */
  private static final double FUGACITY_TOLERANCE = 1.0e-8;

  /** Prevents instantiation of the stateless checks. */
  private FluidEquilibriumChecks() {
  }

  /**
   * Captures original species amounts, independently of the current phase split.
   *
   * @param system source fluid
   * @return species amounts in component order
   */
  static double[] inventory(SystemInterface system) {
    double[] moles = new double[system.getNumberOfComponents()];
    for (int component = 0; component < moles.length; component++) {
      moles[component] = system.getPhase(0).getComponent(component).getNumberOfmoles();
    }
    return moles;
  }

  /**
   * Rejects non-conservative, non-neutral, unnormalized or non-equilibrated material phases.
   *
   * @param system initialized candidate equilibrium
   * @param originalMoles frozen input species amounts, before phase iteration
   * @throws IllegalStateException if any physical acceptance condition fails
   */
  static void validate(SystemInterface system, double[] originalMoles) {
    if (originalMoles.length != system.getNumberOfComponents() || system.getNumberOfPhases() < 1) {
      throw new IllegalStateException("Fluid equilibrium changed the component basis or lost all material phases");
    }
    double total = 0.0;
    for (double amount : originalMoles) {
      if (!Double.isFinite(amount) || amount < 0.0) {
        throw new IllegalStateException("Fluid equilibrium has invalid input species amounts");
      }
      total += amount;
    }
    if (!(total > 0.0) || !Double.isFinite(total)) {
      throw new IllegalStateException("Fluid equilibrium requires a finite positive feed");
    }
    FlashPhaseConstraints constraints = FlashPhaseConstraints.capture(system);
    double betaSum = 0.0;
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      double beta = system.getBeta(phase);
      if (!Double.isFinite(beta) || beta <= 0.0 || beta > 1.0) {
        throw new IllegalStateException("Fluid equilibrium has an invalid material phase fraction");
      }
      double phaseAmount = system.getPhase(phase).getNumberOfMolesInPhase() / total;
      if (!Double.isFinite(phaseAmount) || Math.abs(phaseAmount - beta) > BALANCE_TOLERANCE) {
        throw new IllegalStateException("Fluid equilibrium has inconsistent stored phase amounts");
      }
      betaSum += beta;
      double sum = 0.0;
      double charge = 0.0;
      for (int component = 0; component < originalMoles.length; component++) {
        ComponentInterface species = system.getPhase(phase).getComponent(component);
        double x = species.getx();
        if (!Double.isFinite(x) || x < 0.0 || x > 1.0) {
          throw new IllegalStateException("Fluid equilibrium has an invalid phase composition");
        }
        double amount = species.getNumberOfMolesInPhase() / total;
        if (!Double.isFinite(amount) || Math.abs(amount - beta * x) > BALANCE_TOLERANCE) {
          throw new IllegalStateException("Fluid equilibrium has inconsistent stored species amounts");
        }
        if (!constraints.isAllowed(phase, component) && x > 1.0e-40) {
          throw new IllegalStateException("Fluid equilibrium placed an aqueous-only ion in another material phase");
        }
        sum += x;
        charge += x * species.getIonicCharge();
      }
      if (Math.abs(sum - 1.0) > BALANCE_TOLERANCE || !Double.isFinite(charge) || Math.abs(charge) > BALANCE_TOLERANCE) {
        throw new IllegalStateException("Fluid equilibrium failed phase normalization or electroneutrality");
      }
    }
    if (Math.abs(betaSum - 1.0) > 1.0e-12) {
      throw new IllegalStateException("Fluid equilibrium has unnormalized phase fractions");
    }
    for (int component = 0; component < originalMoles.length; component++) {
      double recovered = 0.0;
      for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
        recovered += system.getBeta(phase) * system.getPhase(phase).getComponent(component).getx();
      }
      if (Math.abs(recovered - originalMoles[component] / total) > BALANCE_TOLERANCE
          || Math.abs(system.getPhase(0).getComponent(component).getNumberOfmoles() / total
              - originalMoles[component] / total) > BALANCE_TOLERANCE) {
        throw new IllegalStateException("Fluid equilibrium failed original species conservation");
      }
      if (originalMoles[component] / total <= 1.0e-50
          || FlashPhaseConstraints.isIon(system.getPhase(0).getComponent(component))) {
        continue;
      }
      double reference = system.getPhase(0).getFugacity(component);
      if (!(reference > 0.0) || !Double.isFinite(reference)) {
        throw new IllegalStateException("Fluid equilibrium has an invalid molecular fugacity");
      }
      for (int phase = 1; phase < system.getNumberOfPhases(); phase++) {
        double fugacity = system.getPhase(phase).getFugacity(component);
        if (!(fugacity > 0.0) || !Double.isFinite(fugacity)
            || Math.abs(Math.log(fugacity) - Math.log(reference)) > FUGACITY_TOLERANCE) {
          throw new IllegalStateException("Fluid equilibrium failed molecular fugacity equality");
        }
      }
    }
  }
}
