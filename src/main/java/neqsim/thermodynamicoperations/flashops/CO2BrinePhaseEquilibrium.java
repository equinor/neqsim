package neqsim.thermodynamicoperations.flashops;

import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;

/**
 * Constrained fluid equilibrium for non-reactive CO2/water/electrolyte-CPA hydrate calculations.
 *
 * <p>
 * Independent vapour and liquid CO2 trials are compared with a conserved aqueous feed. Ions have zero partition
 * coefficients. An absent CO2 phase must pass a normalized tangent-plane trial; an interior split must close component
 * balances and molecular fugacities. Only accepted states are copied to the caller. This uses the existing EOS
 * parameters, without hydrate or salt-solid material phases, and does not establish experimental model accuracy.
 * </p>
 */
public final class CO2BrinePhaseEquilibrium {
  private static final double FLOOR = 1.0e-50;
  private static final double TOLERANCE = 1.0e-8;
  private final SystemInterface system;
  private double minimumTrialDistance = Double.NaN;

  /**
   * Creates a fluid-phase calculation without modifying the supplied system.
   *
   * @param system non-reactive electrolyte-CPA CO2/water feed
   */
  public CO2BrinePhaseEquilibrium(SystemInterface system) {
    this.system = system;
  }

  /**
   * Checks the composition/model scope of this constrained two-molecular-component calculation.
   *
   * @param fluid system to inspect
   * @return true for water-rich, non-reactive electrolyte-CPA CO2/water with optional explicit ions
   */
  public static boolean isApplicable(SystemInterface fluid) {
    if (!(fluid instanceof SystemElectrolyteCPAstatoil) || fluid.isChemicalSystem() || fluid.doSolidPhaseCheck()
        || fluid.isForcePhaseTypes() || !fluid.getPhase(0).hasComponent("CO2")
        || !fluid.getPhase(0).hasComponent("water")) {
      return false;
    }
    double water = fluid.getPhase(0).getComponent("water").getNumberOfmoles();
    double co2 = fluid.getPhase(0).getComponent("CO2").getNumberOfmoles();
    if (!(co2 > 0.0) || !(water > co2)) {
      return false;
    }
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      ComponentInterface species = fluid.getPhase(0).getComponent(component);
      if (!isIon(species) && !"CO2".equals(species.getComponentName()) && !"water".equals(species.getComponentName())
          && species.getNumberOfmoles() > 0.0) {
        return false;
      }
    }
    return true;
  }

  /**
   * Solves the fluid state, preserving the input species inventory and aqueous identity.
   *
   * @throws IllegalArgumentException if the feed is outside the supported composition/model scope
   * @throws IllegalStateException if a trial or the accepted equilibrium cannot be qualified numerically
   */
  public void run() {
    minimumTrialDistance = Double.NaN;
    if (!isApplicable(system)) {
      throw new IllegalArgumentException("CO2/brine phase selection requires non-reactive electrolyte-CPA CO2/water");
    }
    if (!(system.getTemperature() > 0.0) || !Double.isFinite(system.getTemperature()) || !(system.getPressure() > 0.0)
        || !Double.isFinite(system.getPressure())) {
      throw new IllegalArgumentException("CO2/brine phase selection requires positive finite temperature and pressure");
    }
    SystemInterface aqueous = initializedFeed();
    aqueous.setNumberOfPhases(1);
    aqueous.setPhaseType(0, PhaseType.AQUEOUS);
    aqueous.setBeta(0, 1.0);
    aqueous.init(1);
    if (aqueous.getPhase(0).getType() != PhaseType.AQUEOUS) {
      throw new IllegalStateException("CO2/brine feed did not produce a liquid aqueous reference");
    }
    double aqueousGibbs = aqueous.getGibbsEnergy();
    if (!Double.isFinite(aqueousGibbs)) {
      throw new IllegalStateException("CO2/brine aqueous reference has non-finite Gibbs energy");
    }
    SystemInterface best = null;
    PhaseType bestRoot = PhaseType.AQUEOUS;
    boolean aqueousStable = true;
    minimumTrialDistance = Double.POSITIVE_INFINITY;
    for (PhaseType root : new PhaseType[] { PhaseType.GAS, PhaseType.OIL }) {
      SystemInterface trial = initializedFeed();
      trial.setPhaseType(0, root);
      trial.setPhaseType(1, PhaseType.AQUEOUS);
      trial.setBeta(0, 0.1);
      trial.setBeta(1, 0.9);
      for (int component = 0; component < trial.getNumberOfComponents(); component++) {
        ComponentInterface species = trial.getPhase(0).getComponent(component);
        species.setx("CO2".equals(species.getComponentName()) ? 0.9999
            : "water".equals(species.getComponentName()) ? 0.0001 : FLOOR);
      }
      double distance = tangentPlaneTrial(trial, aqueous);
      minimumTrialDistance = Math.min(minimumTrialDistance, distance);
      if (distance < -TOLERANCE) {
        aqueousStable = false;
        SystemInterface split = solveSplit(trial);
        if (split == null) {
          throw new IllegalStateException("Unstable CO2/brine aqueous feed has no converged " + root + " split");
        }
        double gibbs = split.getGibbsEnergy();
        if (!Double.isFinite(gibbs) || gibbs > aqueousGibbs + Math.max(1.0e-7, Math.abs(aqueousGibbs) * 1.0e-12)) {
          throw new IllegalStateException("CO2/brine split failed the conserved-inventory Gibbs comparison");
        }
        if (best == null || gibbs < best.getGibbsEnergy()) {
          best = split;
          bestRoot = root;
        }
      }
    }
    if (aqueousStable) {
      best = aqueous;
      bestRoot = PhaseType.AQUEOUS;
    }
    if (best == null) {
      throw new IllegalStateException("No qualified CO2/brine fluid phase state");
    }
    verify(best);
    system.setNumberOfPhases(best.getNumberOfPhases());
    for (int phase = 0; phase < best.getNumberOfPhases(); phase++) {
      system.setPhaseIndex(phase, phase);
      system.setPhase(best.getPhase(phase).clone(), phase);
      // Preserve the EOS root requested during the calculation, not its density-based display label.
      system.setPhaseType(phase, phase == 0 ? bestRoot : PhaseType.AQUEOUS);
      system.setBeta(phase, best.getBeta(phase));
    }
  }

  /**
   * Returns the most negative dimensionless CO2 trial tangent-plane distance of the aqueous feed.
   *
   * @return minimum of the vapour/liquid trials; negative means the single-aqueous feed is unstable
   */
  public double getMinimumTrialDistance() {
    return minimumTrialDistance;
  }

  /** @return an independently initialized clone with the original molecular and ionic feed */
  private SystemInterface initializedFeed() {
    SystemInterface feed = system.clone();
    // init(0) must precede setting root types and compositions: it resets the storage mapping.
    feed.init(0);
    double total = feed.getTotalNumberOfMoles();
    if (!(total > 0.0) || !Double.isFinite(total)) {
      throw new IllegalStateException("CO2/brine feed has invalid total moles");
    }
    double sum = 0.0;
    double charge = 0.0;
    for (int component = 0; component < feed.getNumberOfComponents(); component++) {
      ComponentInterface species = feed.getPhase(0).getComponent(component);
      double moles = species.getNumberOfmoles();
      if (!Double.isFinite(moles) || moles < 0.0) {
        throw new IllegalStateException("CO2/brine feed has an invalid component amount");
      }
      sum += moles;
      charge += moles * species.getIonicCharge();
    }
    if (Math.abs(sum / total - 1.0) > 1.0e-10 || Math.abs(charge / total) > 1.0e-10) {
      throw new IllegalStateException("CO2/brine feed must conserve moles and be electrically neutral");
    }
    return feed;
  }

  /**
   * Normalizes a molecular trial while retaining the aqueous reference fugacities.
   *
   * @param trial CO2-rich phase on the requested EOS root
   * @param aqueous homogeneous conserved aqueous feed
   * @return stationary dimensionless tangent-plane distance
   */
  private double tangentPlaneTrial(SystemInterface trial, SystemInterface aqueous) {
    int count = trial.getNumberOfComponents();
    double[] weights = new double[count];
    double distance = Double.NaN;
    for (int iteration = 0; iteration < 100; iteration++) {
      trial.init(1);
      double sum = 0.0;
      for (int component = 0; component < count; component++) {
        ComponentInterface species = trial.getPhase(0).getComponent(component);
        if (isIon(species) || species.getz() <= FLOOR) {
          weights[component] = 0.0;
        } else {
          double fugacity = aqueous.getPhase(0).getFugacity(component);
          double coefficient = species.getFugacityCoefficient();
          if (!(fugacity > 0.0) || !Double.isFinite(fugacity) || !(coefficient > 0.0)
              || !Double.isFinite(coefficient)) {
            throw new IllegalStateException("CO2/brine stability trial has invalid molecular fugacity");
          }
          weights[component] = fugacity / (trial.getPressure() * coefficient);
          if (!(weights[component] > 0.0) || !Double.isFinite(weights[component])) {
            throw new IllegalStateException("CO2/brine stability trial has invalid molecular fugacity weights");
          }
        }
        sum += weights[component];
      }
      if (!(sum > 0.0) || !Double.isFinite(sum)) {
        throw new IllegalStateException("CO2/brine stability trial has non-finite fugacity weights");
      }
      double change = 0.0;
      for (int component = 0; component < count; component++) {
        double x = Math.max(FLOOR, weights[component] / sum);
        change = Math.max(change, Math.abs(x - trial.getPhase(0).getComponent(component).getx()));
        // Component.setx(0) retains the old value, so ion-free trials require an explicit positive floor.
        trial.getPhase(0).getComponent(component).setx(x);
      }
      distance = -Math.log(sum);
      if (change < 1.0e-12) {
        trial.init(1);
        return distance;
      }
    }
    throw new IllegalStateException("CO2/brine normalized stability trial did not converge");
  }

  /**
   * Solves molecular partitioning with ions confined by zero K-values.
   *
   * @param trial normalized unstable CO2 trial and the aqueous feed
   * @return conservative equilibrium, or null if no converged interior solution is found
   */
  private SystemInterface solveSplit(SystemInterface trial) {
    int count = trial.getNumberOfComponents();
    double[] ratios = new double[count];
    for (int iteration = 0; iteration < 150; iteration++) {
      trial.init(1);
      for (int component = 0; component < count; component++) {
        ComponentInterface species = trial.getPhase(0).getComponent(component);
        if (isIon(species)) {
          ratios[component] = 0.0;
        } else {
          double aqueousCoefficient = trial.getPhase(1).getComponent(component).getFugacityCoefficient();
          double co2Coefficient = species.getFugacityCoefficient();
          if (!(aqueousCoefficient > 0.0) || !Double.isFinite(aqueousCoefficient) || !(co2Coefficient > 0.0)
              || !Double.isFinite(co2Coefficient)) {
            return null;
          }
          ratios[component] = aqueousCoefficient / co2Coefficient;
          if (!(ratios[component] > 0.0) || !Double.isFinite(ratios[component])) {
            return null;
          }
        }
      }
      double lower = 0.0;
      double upper = 1.0 - 1.0e-12;
      if (!(rachfordRice(trial, ratios, lower) > 0.0) || !(rachfordRice(trial, ratios, upper) < 0.0)) {
        return null;
      }
      for (int step = 0; step < 80; step++) {
        double beta = 0.5 * (lower + upper);
        if (rachfordRice(trial, ratios, beta) > 0.0) {
          lower = beta;
        } else {
          upper = beta;
        }
      }
      double beta = 0.5 * (lower + upper);
      double change = Math.abs(beta - trial.getBeta(0));
      trial.setBeta(0, beta);
      trial.setBeta(1, 1.0 - beta);
      for (int component = 0; component < count; component++) {
        double x = trial.getPhase(0).getComponent(component).getz() / (1.0 + beta * (ratios[component] - 1.0));
        double y = Math.max(FLOOR, ratios[component] * x);
        change = Math.max(change, Math.abs(x - trial.getPhase(1).getComponent(component).getx()));
        change = Math.max(change, Math.abs(y - trial.getPhase(0).getComponent(component).getx()));
        trial.getPhase(1).getComponent(component).setx(Math.max(FLOOR, x));
        trial.getPhase(0).getComponent(component).setx(y);
      }
      if (change < 1.0e-12) {
        trial.init(1);
        verify(trial);
        return trial;
      }
    }
    return null;
  }

  /**
   * Evaluates the constrained Rachford-Rice function.
   *
   * @param trial system providing the overall composition
   * @param ratios molecular equilibrium ratios and zero ionic ratios
   * @param beta CO2-rich phase fraction
   * @return dimensionless phase-fraction residual
   */
  private static double rachfordRice(SystemInterface trial, double[] ratios, double beta) {
    double residual = 0.0;
    for (int component = 0; component < ratios.length; component++) {
      double difference = ratios[component] - 1.0;
      residual += trial.getPhase(0).getComponent(component).getz() * difference / (1.0 + beta * difference);
    }
    return residual;
  }

  /**
   * @param species component to inspect
   * @return whether the species is confined to the aqueous phase
   */
  private static boolean isIon(ComponentInterface species) {
    return species.getIonicCharge() != 0.0 || species.isIsIon();
  }

  /**
   * Rejects unbalanced, unnormalized or non-equilibrated candidate states.
   *
   * @param candidate candidate material phases
   * @throws IllegalStateException when a numerical or phase-state invariant fails
   */
  private static void verify(SystemInterface candidate) {
    double betaSum = 0.0;
    if (!candidate.hasPhaseType(PhaseType.AQUEOUS)) {
      throw new IllegalStateException("CO2/brine equilibrium lost its aqueous phase");
    }
    if (candidate.getNumberOfPhases() == 2 && (candidate.getPhase(0).getComponent("CO2").getx() <= 0.5
        || candidate.getPhase(1).getType() != PhaseType.AQUEOUS)) {
      throw new IllegalStateException("CO2/brine split must contain distinct CO2-rich and aqueous phases");
    }
    for (int phase = 0; phase < candidate.getNumberOfPhases(); phase++) {
      double beta = candidate.getBeta(phase);
      if (!Double.isFinite(beta) || beta <= 0.0 || beta > 1.0) {
        throw new IllegalStateException("CO2/brine equilibrium has an invalid phase fraction");
      }
      betaSum += beta;
      double sum = 0.0;
      for (int component = 0; component < candidate.getNumberOfComponents(); component++) {
        ComponentInterface species = candidate.getPhase(phase).getComponent(component);
        double x = species.getx();
        if (!Double.isFinite(x) || x < 0.0 || x > 1.0
            || (isIon(species) && candidate.getPhase(phase).getType() != PhaseType.AQUEOUS && x > 1.0e-40)) {
          throw new IllegalStateException("CO2/brine equilibrium has an invalid composition or unconfined ion");
        }
        sum += x;
      }
      if (Math.abs(sum - 1.0) > 1.0e-10) {
        throw new IllegalStateException("CO2/brine equilibrium has an unnormalized phase");
      }
    }
    if (Math.abs(betaSum - 1.0) > 1.0e-12) {
      throw new IllegalStateException("CO2/brine equilibrium has unnormalized phase fractions");
    }
    for (int component = 0; component < candidate.getNumberOfComponents(); component++) {
      double recovered = 0.0;
      for (int phase = 0; phase < candidate.getNumberOfPhases(); phase++) {
        recovered += candidate.getBeta(phase) * candidate.getPhase(phase).getComponent(component).getx();
      }
      ComponentInterface species = candidate.getPhase(0).getComponent(component);
      if (Math.abs(recovered - species.getz()) > 1.0e-10) {
        throw new IllegalStateException("CO2/brine equilibrium failed component conservation");
      }
      if (!isIon(species) && species.getz() > FLOOR && candidate.getNumberOfPhases() == 2) {
        double ratio = candidate.getPhase(0).getFugacity(component) / candidate.getPhase(1).getFugacity(component);
        if (!(ratio > 0.0) || !Double.isFinite(ratio) || Math.abs(Math.log(ratio)) > TOLERANCE) {
          throw new IllegalStateException("CO2/brine equilibrium failed molecular fugacity equality");
        }
      }
    }
  }
}
