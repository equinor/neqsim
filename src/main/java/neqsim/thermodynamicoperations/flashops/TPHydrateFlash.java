package neqsim.thermodynamicoperations.flashops;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import Jama.Matrix;
import neqsim.thermo.component.ComponentHydrate;
import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * Conservative TP flash for a non-reactive fluid and one stable hydrate structure.
 *
 * <p>
 * Each trial withdraws structural water and guests from the feed and reflashes the remaining fluid. Guest amounts are
 * coupled to both cavity occupancies, including empty cavities. A bracketed water-extent solve matches the hydrate
 * host-water fugacity to the fluid-water fugacity. Trial states are private; failure leaves the supplied system
 * unchanged.
 * </p>
 *
 * <p>
 * The hydrate component model returns host-water fugacity divided by pressure. It must not be multiplied by the
 * hydrate's material-balance water mole fraction when evaluating equilibrium.
 * </p>
 */
public class TPHydrateFlash extends TPflash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Maximum iterations in each bounded solve. */
  private static final int MAX_HYDRATE_ITERATIONS = 100;
  /** Log water-fugacity convergence tolerance. */
  private static final double HYDRATE_TOLERANCE = 1e-8;
  /** Composition and feed-fraction material-balance tolerance. */
  private static final double BALANCE_TOLERANCE = 1e-8;
  /** Relative tolerance for the guest inventory fixed point. */
  private static final double GUEST_TOLERANCE = 1e-10;
  /** Whether the last successful calculation formed hydrate. */
  private boolean hydrateFormed;
  /** Hydrate mole fraction on the original feed basis. */
  private double hydrateFraction;
  /** Selected structure, numbered 1 or 2. */
  private int stableHydrateStructure = 1;
  /** Compatibility flag; phase disappearance is always determined by equilibrium. */
  private boolean gasHydrateOnlyMode;
  /** Whether a balanced equilibrium result was accepted. */
  private boolean converged;
  /** Last accepted log host-water/fluid-water fugacity ratio. */
  private double lastResidual = Double.NaN;
  /** Configurable iteration budget for the outer water solve. */
  private int maximumIterations = MAX_HYDRATE_ITERATIONS;
  /** Number of residual-fluid flashes performed in the last run. */
  private int fluidFlashCount;
  /** Maximum component balance error in the accepted state. */
  private double maximumBalanceResidual = Double.NaN;

  /**
   * Create a hydrate amount flash.
   *
   * @param system fluid whose state is updated after successful convergence
   */
  public TPHydrateFlash(SystemInterface system) {
    super(system);
  }

  /**
   * Create a hydrate amount flash with optional solid checks in the residual fluid.
   *
   * @param system fluid whose state is updated after successful convergence
   * @param checkForSolids whether the residual-fluid flash also checks solid phases
   */
  public TPHydrateFlash(SystemInterface system, boolean checkForSolids) {
    super(system, checkForSolids);
  }

  /**
   * {@inheritDoc}
   *
   * @throws UnsupportedOperationException for Pitzer or reactive systems
   * @throws IllegalStateException if equilibrium or the component balance cannot be verified
   */
  @Override
  public void run() {
    converged = false;
    hydrateFormed = false;
    hydrateFraction = 0.0;
    lastResidual = Double.NaN;
    stableHydrateStructure = 1;
    fluidFlashCount = 0;
    maximumBalanceResidual = Double.NaN;
    if (!Double.isFinite(system.getTotalNumberOfMoles()) || system.getTotalNumberOfMoles() <= 0.0
        || !Double.isFinite(system.getTemperature()) || system.getTemperature() <= 0.0
        || !Double.isFinite(system.getPressure()) || system.getPressure() <= 0.0) {
      throw new IllegalArgumentException("TPHydrateFlash requires positive finite inventory, temperature and pressure");
    }
    if (system instanceof neqsim.thermo.system.SystemPitzer) {
      throw new UnsupportedOperationException(
          "Pitzer supports incipient hydrate equilibrium only; use hydrateFormationTemperature or hydrateFormationPressure");
    }
    if (system.isChemicalSystem()) {
      throw new UnsupportedOperationException("TPHydrateFlash requires a non-reactive component inventory");
    }
    SystemInterface feed = system.clone();
    feed.setHydrateCheck(true);
    new TPflash(feed, solidCheck).run();
    fluidFlashCount++;
    double[] z = new double[feed.getNumberOfComponents()];
    for (int i = 0; i < z.length; i++) {
      z[i] = system.getComponent(i).getNumberOfmoles() / system.getTotalNumberOfMoles();
    }
    verifyBalance(feed, z);
    if (!feed.getPhase(0).hasComponent("water")) {
      publish(feed);
      converged = true;
      return;
    }
    int water = feed.getComponent("water").getComponentNumber();
    boolean hasGuest = false;
    for (int i = 0; i < z.length; i++) {
      hasGuest |= i != water && z[i] > 0.0 && feed.getComponent(i).isHydrateFormer();
    }
    if (z[water] <= 0.0 || !hasGuest) {
      publish(feed);
      converged = true;
      return;
    }
    Trial initial = evaluate(feed, z, water, 0.0, null);
    if (initial.residual >= -HYDRATE_TOLERANCE) {
      lastResidual = initial.residual;
      publish(feed);
      converged = true;
      return;
    }

    double lower = 0.0;
    Trial low = initial;
    double extentEstimate = z[water];
    for (int i = 0; i < z.length; i++) {
      if (initial.guestRatios[i] > 0.0) {
        extentEstimate = Math.min(extentEstimate, z[i] / initial.guestRatios[i]);
      }
    }
    double upper = 0.5 * extentEstimate;
    Trial high = evaluate(feed, z, water, upper, initial);
    for (int bracket = 0; high.residual < 0.0 && bracket < maximumIterations; bracket++) {
      lower = upper;
      low = high;
      upper = Math.min(2.0 * upper, 0.5 * (upper + z[water]));
      if (upper >= z[water]) {
        break;
      }
      high = evaluate(feed, z, water, upper, low);
    }
    if (high.residual < 0.0) {
      throw new IllegalStateException(
          "TPHydrateFlash could not bracket water-fugacity equality before water depletion");
    }
    Trial solution = Math.abs(high.residual) < HYDRATE_TOLERANCE ? high : null;
    for (int iteration = 0; solution == null && iteration < maximumIterations; iteration++) {
      double width = upper - lower;
      double extent = lower - low.residual * width / (high.residual - low.residual);
      // Safeguard interpolation by bisection near the endpoints, and at least every fourth step.
      if (!Double.isFinite(extent) || extent < lower + 0.05 * width || extent > upper - 0.05 * width
          || iteration % 4 == 3) {
        extent = 0.5 * (lower + upper);
      }
      Trial seed = extent - lower < upper - extent ? low : high;
      Trial trial = evaluate(feed, z, water, extent, seed);
      if (Math.abs(trial.residual) < HYDRATE_TOLERANCE) {
        solution = trial;
        break;
      }
      if (trial.residual < 0.0) {
        lower = extent;
        low = trial;
      } else {
        upper = extent;
        high = trial;
      }
    }
    if (solution == null) {
      throw new IllegalStateException("TPHydrateFlash did not converge to water-fugacity equality");
    }
    verifyFluidEquilibrium(solution.fluid);
    SystemInterface result = assemble(feed, solution, z);
    verifyBalance(result, z);
    publish(result);
    hydrateFraction = solution.hydrateMoles;
    hydrateFormed = hydrateFraction > 0.0;
    stableHydrateStructure = solution.hydrate.getStableHydrateStructure();
    lastResidual = solution.residual;
    converged = true;
  }

  /**
   * Equilibrate guest inventories at a fixed amount of structural water.
   *
   * @param feed fluid-only feed state
   * @param z original feed composition
   * @param water water component index
   * @param extent hydrate water per mole of feed
   * @param seed nearby accepted guest solution, or null
   * @return a trial satisfying the guest component balances
   */
  private Trial evaluate(SystemInterface feed, double[] z, int water, double extent, Trial seed) {
    double[] remaining = z.clone();
    remaining[water] -= extent;
    List<Integer> guests = new ArrayList<Integer>();
    for (int i = 0; i < z.length; i++) {
      if (i != water && z[i] > 0.0 && feed.getComponent(i).isHydrateFormer()) {
        guests.add(i);
        if (seed != null && seed.guestRatios[i] > 0.0) {
          remaining[i] = z[i] / (1.0 + extent * seed.guestRatios[i] / seed.fluid.getComponent(i).getNumberOfmoles());
        }
      }
    }
    Trial trial = evaluateComposition(feed, z, water, extent, remaining);
    for (int iteration = 0; iteration < MAX_HYDRATE_ITERATIONS; iteration++) {
      if (trial.guestError < GUEST_TOLERANCE) {
        return trial;
      }
      // Use inexpensive distribution-ratio substitution first. A damped Newton fallback in log fluid amounts
      // handles guest-limited feeds where substitution becomes nearly stationary at phase disappearance.
      if (iteration >= 4) {
        int size = guests.size();
        double[][] jacobian = new double[size][size];
        double[][] rhs = new double[size][1];
        for (int row = 0; row < size; row++) {
          rhs[row][0] = -trial.guestResiduals[guests.get(row)];
        }
        for (int column = 0; column < size; column++) {
          int index = guests.get(column);
          double step = remaining[index] * Math.exp(1e-4) < z[index] ? 1e-4 : -1e-4;
          double[] perturbed = remaining.clone();
          perturbed[index] *= Math.exp(step);
          Trial probe = evaluateComposition(feed, z, water, extent, perturbed);
          for (int row = 0; row < size; row++) {
            int component = guests.get(row);
            jacobian[row][column] = (probe.guestResiduals[component] - trial.guestResiduals[component]) / step;
          }
        }
        try {
          Matrix direction = new Matrix(jacobian).solve(new Matrix(rhs));
          boolean accepted = false;
          for (double damping = 1.0; damping >= 1.0 / 128.0; damping *= 0.5) {
            double[] candidate = remaining.clone();
            for (int row = 0; row < size; row++) {
              int index = guests.get(row);
              double change = Math.max(-3.0, Math.min(3.0, direction.get(row, 0)));
              candidate[index] = Math.min(z[index], remaining[index] * Math.exp(damping * change));
            }
            Trial next = evaluateComposition(feed, z, water, extent, candidate);
            if (next.guestError < trial.guestError * (1.0 - 1e-4 * damping)) {
              trial = next;
              remaining = candidate;
              accepted = true;
              break;
            }
          }
          if (accepted) {
            continue;
          }
        } catch (RuntimeException ex) {
          // A singular local Jacobian is not convergence. Retain the bounded substitution fallback.
        }
      }
      for (int index : guests) {
        double demand = extent * trial.guestRatios[index];
        remaining[index] = z[index] / (1.0 + demand / remaining[index]);
      }
      trial = evaluateComposition(feed, z, water, extent, remaining);
    }
    throw new IllegalStateException("TPHydrateFlash guest inventories did not converge; residual=" + trial.guestError
        + ", extent=" + extent + ", remaining=" + Arrays.toString(remaining));
  }

  /**
   * Reflash an explicit residual inventory and calculate hydrate composition and equilibrium residuals.
   *
   * @param feed fluid-only feed template
   * @param z original feed composition
   * @param water water component index
   * @param extent structural water per mole of feed
   * @param remaining residual-fluid component amounts per mole of feed
   * @return evaluated state, which still requires guest-balance convergence
   */
  private Trial evaluateComposition(SystemInterface feed, double[] z, int water, double extent, double[] remaining) {
    SystemInterface fluid = feed.clone();
    fluid.setMolarFlowRates(remaining);
    new TPflash(fluid, solidCheck).run();
    fluidFlashCount++;
    PhaseHydrate hydrate = (PhaseHydrate) fluid.getPhases()[4];
    int waterPhase = 0;
    int referencePhase = 0;
    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      if (fluid.getPhase(p).getType() == PhaseType.GAS) {
        referencePhase = p;
      }
      if (fluid.getPhase(p).getComponent(water).getx() > fluid.getPhase(waterPhase).getComponent(water).getx()) {
        waterPhase = p;
      }
    }
    for (int i = 0; i < z.length; i++) {
      for (int j = 0; j < z.length; j++) {
        double fugacity = z[j] > 0.0 ? fluid.getPhase(referencePhase).getFugacity(j) : 0.0;
        if (j != water && !hydrate.getComponent(j).isHydrateFormer()) {
          fugacity = 0.0;
        }
        ((ComponentHydrate) hydrate.getComponent(i)).setRefFug(j, fugacity);
      }
    }
    ComponentHydrate host = (ComponentHydrate) hydrate.getComponent(water);
    double hydrateFugacity = host.fugcoef(hydrate) * fluid.getPressure();
    int structure = host.getHydrateStructure();
    double fluidFugacity = fluid.getPhase(waterPhase).getFugacity(water);
    if (!Double.isFinite(hydrateFugacity) || hydrateFugacity <= 0.0 || !Double.isFinite(fluidFugacity)
        || fluidFugacity <= 0.0 || structure < 0 || structure > 1) {
      throw new IllegalStateException("TPHydrateFlash received invalid water fugacity or hydrate structure");
    }
    double[] ratios = new double[z.length];
    double[] amounts = new double[z.length];
    double[] errors = new double[z.length];
    amounts[water] = extent;
    double error = 0.0;
    double totalHydrate = extent;
    for (int i = 0; i < z.length; i++) {
      if (i != water && z[i] > 0.0 && hydrate.getComponent(i).isHydrateFormer()) {
        ComponentHydrate guest = (ComponentHydrate) hydrate.getComponent(i);
        for (int cavity = 0; cavity < 2; cavity++) {
          double occupancy = guest.calcYKI(structure, cavity, hydrate);
          if (!Double.isFinite(occupancy) || occupancy < 0.0 || occupancy > 1.0) {
            throw new IllegalStateException("TPHydrateFlash received an invalid cavity occupancy");
          }
          ratios[i] += host.getCavprwat(structure, cavity) * occupancy;
        }
        amounts[i] = extent * ratios[i];
        errors[i] = Math.log((remaining[i] + amounts[i]) / z[i]);
        error = Math.max(error, Math.abs(errors[i]));
        totalHydrate += amounts[i];
      }
    }
    // Initialize from component amounts, including exact zeros. Component.setx(0) leaves the
    // previous x unchanged, which otherwise carries feed hydrocarbons/inhibitors into the hydrate lattice.
    for (int i = 0; i < z.length; i++) {
      hydrate.getComponent(i).setNumberOfmoles(totalHydrate > 0.0 ? amounts[i] : (i == water ? 1.0 : 0.0));
      ((ComponentHydrate) hydrate.getComponent(i)).setHydrateStructure(structure);
    }
    hydrate.init(totalHydrate > 0.0 ? totalHydrate : 1.0, z.length, 0, PhaseType.HYDRATE, 1.0);
    return new Trial(fluid, hydrate, totalHydrate, Math.log(hydrateFugacity / fluidFugacity), ratios, errors, error);
  }

  /**
   * Assemble all phases on the original feed basis, retaining their solved compositions.
   *
   * @param feed original feed state
   * @param trial converged residual fluid and hydrate
   * @param z original feed composition
   * @return the complete equilibrium state
   */
  private SystemInterface assemble(SystemInterface feed, Trial trial, double[] z) {
    SystemInterface result = feed.clone();
    int fluidPhases = trial.fluid.getNumberOfPhases();
    result.setNumberOfPhases(fluidPhases + 1);
    for (int p = 0; p < fluidPhases; p++) {
      int slot = trial.fluid.getPhaseIndex(p);
      result.setPhase(trial.fluid.getPhase(p).clone(), slot);
      result.setPhaseIndex(p, slot);
      result.setPhaseType(p, trial.fluid.getPhase(p).getType());
      result.setBeta(p, trial.fluid.getBeta(p) * trial.fluid.getTotalNumberOfMoles());
    }
    result.setPhase(trial.hydrate.clone(), 4);
    result.setPhaseIndex(fluidPhases, 4);
    result.setPhaseType(fluidPhases, PhaseType.HYDRATE);
    result.setBeta(fluidPhases, trial.hydrateMoles);
    for (PhaseInterface phase : result.getPhases()) {
      if (phase != null) {
        for (int i = 0; i < z.length; i++) {
          phase.getComponent(i).setz(z[i]);
          phase.getComponent(i).setNumberOfmoles(z[i] * result.getTotalNumberOfMoles());
        }
      }
    }
    result.init(1);
    result.orderByDensity();
    result.init(1);
    // Component fugacity evaluation independently selects water's structure; keep the guest metadata consistent.
    int structure = ((ComponentHydrate) result.getPhases()[4].getComponent("water")).getHydrateStructure();
    for (int i = 0; i < z.length; i++) {
      ((ComponentHydrate) result.getPhases()[4].getComponent(i)).setHydrateStructure(structure);
    }
    return result;
  }

  /**
   * Check normalized phases and every component inventory before publishing a state.
   *
   * @param result candidate result
   * @param z original feed composition
   */
  private void verifyBalance(SystemInterface result, double[] z) {
    double[] balance = new double[z.length];
    double sumBeta = 0.0;
    for (int p = 0; p < result.getNumberOfPhases(); p++) {
      double beta = result.getBeta(p);
      if (!Double.isFinite(beta) || beta < 0.0 || beta > 1.0) {
        throw new IllegalStateException("TPHydrateFlash returned an invalid phase fraction");
      }
      sumBeta += beta;
      double sumX = 0.0;
      for (int i = 0; i < z.length; i++) {
        double x = result.getPhase(p).getComponent(i).getx();
        if (!Double.isFinite(x) || x < 0.0 || x > 1.0) {
          throw new IllegalStateException("TPHydrateFlash returned an invalid phase composition");
        }
        sumX += x;
        balance[i] += beta * x;
      }
      if (Math.abs(sumX - 1.0) > BALANCE_TOLERANCE) {
        throw new IllegalStateException("TPHydrateFlash returned an unnormalized phase composition: phase=" + p
            + ", type=" + result.getPhase(p).getType() + ", sum=" + sumX);
      }
    }
    if (Math.abs(sumBeta - 1.0) > BALANCE_TOLERANCE) {
      throw new IllegalStateException("TPHydrateFlash phase fractions do not sum to one");
    }
    double maxError = 0.0;
    for (int i = 0; i < z.length; i++) {
      maxError = Math.max(maxError, Math.abs(balance[i] - z[i]));
      if (Math.abs(balance[i] - z[i]) > BALANCE_TOLERANCE) {
        throw new IllegalStateException(
            "TPHydrateFlash failed component balance for " + result.getComponent(i).getName());
      }
    }
    maximumBalanceResidual = maxError;
  }

  /**
   * Require matching fugacities for material components present in two fluid phases.
   *
   * @param fluid converged residual fluid
   */
  private void verifyFluidEquilibrium(SystemInterface fluid) {
    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      PhaseInterface first = fluid.getPhase(p);
      if (first.getType() != PhaseType.GAS && first.getType() != PhaseType.OIL && first.getType() != PhaseType.AQUEOUS
          && first.getType() != PhaseType.LIQUID) {
        continue;
      }
      for (int q = 0; q < p; q++) {
        PhaseInterface second = fluid.getPhase(q);
        if (second.getType() != PhaseType.GAS && second.getType() != PhaseType.OIL
            && second.getType() != PhaseType.AQUEOUS && second.getType() != PhaseType.LIQUID) {
          continue;
        }
        for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
          if (first.getComponent(i).getx() > 1e-12 && second.getComponent(i).getx() > 1e-12) {
            double residual = Math.log(first.getFugacity(i) / second.getFugacity(i));
            if (!Double.isFinite(residual) || Math.abs(residual) > 1e-6) {
              throw new IllegalStateException("TPHydrateFlash residual fluid is not at equilibrium for "
                  + first.getComponent(i).getName() + "; log-fugacity residual=" + residual);
            }
          }
        }
      }
    }
  }

  /**
   * Copy an accepted state into the caller's system without changing its total feed amount.
   *
   * @param result validated state
   */
  private void publish(SystemInterface result) {
    if (!system.getHydrateCheck()) {
      system.setHydrateCheck(true);
    }
    for (int p = 0; p < result.getPhases().length; p++) {
      if (result.getPhases()[p] != null) {
        system.getPhases()[p] = result.getPhases()[p].clone();
      }
    }
    system.setNumberOfPhases(result.getNumberOfPhases());
    for (int p = 0; p < result.getNumberOfPhases(); p++) {
      system.setPhaseIndex(p, result.getPhaseIndex(p));
      system.setPhaseType(p, result.getPhase(p).getType());
      system.setBeta(p, result.getBeta(p));
    }
  }

  /** Private residual-fluid state evaluated on a one-mole feed basis. */
  private static final class Trial {
    private final SystemInterface fluid;
    private final PhaseHydrate hydrate;
    private final double hydrateMoles;
    private final double residual;
    private final double[] guestRatios;
    private final double[] guestResiduals;
    private final double guestError;

    private Trial(SystemInterface fluid, PhaseHydrate hydrate, double hydrateMoles, double residual,
        double[] guestRatios, double[] guestResiduals, double guestError) {
      this.fluid = fluid;
      this.hydrate = hydrate;
      this.hydrateMoles = hydrateMoles;
      this.residual = residual;
      this.guestRatios = guestRatios;
      this.guestResiduals = guestResiduals;
      this.guestError = guestError;
    }
  }

  /**
   * Set the iteration limit for bracketing and solving the water extent.
   *
   * @param limit strictly positive iteration limit
   */
  public void setMaximumIterations(int limit) {
    if (limit < 1) {
      throw new IllegalArgumentException("Hydrate iteration limit must be positive");
    }
    maximumIterations = limit;
  }

  /**
   * Get the number of fluid flashes, including the initial stability state.
   *
   * @return fluid flash count from the last run
   */
  public int getFluidFlashCount() {
    return fluidFlashCount;
  }

  /**
   * Get the largest accepted component error in moles per mole of original feed.
   *
   * @return maximum absolute component-balance residual, or NaN before verification
   */
  public double getMaximumBalanceResidual() {
    return converged ? maximumBalanceResidual : Double.NaN;
  }

  /**
   * Report whether the last run accepted a balanced equilibrium state.
   *
   * @return true after a successful run, including a stable hydrate-free state
   */
  public boolean isConverged() {
    return converged;
  }

  /**
   * Get the accepted logarithm of host-water fugacity divided by fluid-water fugacity.
   *
   * @return residual near zero for hydrate, nonnegative for a stable hydrate-free state, or NaN when not applicable
   */
  public double getLastResidual() {
    return lastResidual;
  }

  /**
   * Check if hydrate has formed at the current conditions.
   *
   * @return true if hydrate has formed, false otherwise
   */
  public boolean isHydrateFormed() {
    return hydrateFormed;
  }

  /**
   * Get the calculated hydrate phase fraction.
   *
   * @return the hydrate fraction (mole basis)
   */
  public double getHydrateFraction() {
    return hydrateFraction;
  }

  /**
   * Get the stable hydrate structure type.
   *
   * @return 1 for Structure I, 2 for Structure II
   */
  public int getStableHydrateStructure() {
    return stableHydrateStructure;
  }

  /**
   * Get the cavity occupancy for a specific component.
   *
   * @param componentName the name of the component
   * @param structure the hydrate structure (1 or 2)
   * @param cavityType the cavity type (0=small, 1=large)
   * @return the cavity occupancy fraction
   */
  public double getCavityOccupancy(String componentName, int structure, int cavityType) {
    PhaseInterface hydratePhase = system.getPhases()[4];
    if (hydratePhase.hasComponent(componentName)) {
      ComponentHydrate comp = (ComponentHydrate) hydratePhase.getComponent(componentName);
      return comp.calcYKI(structure - 1, cavityType, hydratePhase);
    }
    return 0.0;
  }

  /**
   * Check if gas-hydrate only mode is enabled.
   *
   * <p>
   * The flag is retained for API compatibility. Both modes let the residual-fluid equilibrium determine whether an
   * aqueous phase is present; neither mode discards water or forces a phase to disappear.
   * </p>
   *
   * @return true if gas-hydrate only mode is enabled
   */
  public boolean isGasHydrateOnlyMode() {
    return gasHydrateOnlyMode;
  }

  /**
   * Enable or disable gas-hydrate only mode.
   *
   * <p>
   * This compatibility option does not force phase removal. Water remaining in the fluid is determined by fugacity
   * equality and the component balance, including in trace-water systems.
   * </p>
   *
   * @param gasHydrateOnlyMode true to enable gas-hydrate only mode
   */
  public void setGasHydrateOnlyMode(boolean gasHydrateOnlyMode) {
    this.gasHydrateOnlyMode = gasHydrateOnlyMode;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!super.equals(obj)) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    TPHydrateFlash other = (TPHydrateFlash) obj;
    if (hydrateFormed != other.hydrateFormed) {
      return false;
    }
    if (Double.compare(hydrateFraction, other.hydrateFraction) != 0) {
      return false;
    }
    if (maximumIterations != other.maximumIterations) {
      return false;
    }
    if (gasHydrateOnlyMode != other.gasHydrateOnlyMode) {
      return false;
    }
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + (hydrateFormed ? 1231 : 1237);
    result = prime * result + Double.hashCode(hydrateFraction);
    result = prime * result + (gasHydrateOnlyMode ? 1231 : 1237);
    result = prime * result + maximumIterations;
    return result;
  }
}
