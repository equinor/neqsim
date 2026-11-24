package neqsim.process.fielddevelopment.hybrid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Hybrid reservoir, well and network production model: reservoir simulator results supply the depletion behaviour,
 * NeqSim supplies the well, network and fluid response.
 *
 * <p>
 * The model is the coupling
 * </p>
 *
 * <p>
 * simulator results, reservoir-state proxy, inflow performance, lift curve and network, well operating point, molar
 * feed.
 * </p>
 *
 * <p>
 * At every time step the reservoir pressure, water cut, gas-oil ratio and productivity index of each well are read from
 * {@link ReservoirStateModel} proxies at the current cumulative volumes. The well rates and the node pressures of the
 * {@link GatheringNetwork} are then solved together: each well produces what its inflow performance and tubing lift
 * curve allow against its node pressure, and the node pressures follow from the commingled rates through the branches.
 * Finally the cumulative volumes are advanced. A well rate therefore changes when a choke, another well, the separator
 * pressure or the routing changes, which a production profile replayed from the simulator cannot do.
 * </p>
 *
 * <p>
 * Three fidelity modes share the same network and wells:
 * </p>
 * <ul>
 * <li>{@link Mode#SURROGATE}: fast lift curves (typically {@link VfpTableLookup}) for tubing and branches.</li>
 * <li>{@link Mode#FULL_NETWORK}: the reference models (live multiphase pipe and tubing models) in every iteration.</li>
 * <li>{@link Mode#HYBRID}: fast lift curves that are corrected against the reference models at a fixed interval of time
 * steps.</li>
 * </ul>
 *
 * <p>
 * Optional {@link FacilityConstraints} hold wells back when the facility cannot take what the network delivers; the
 * wells with the highest water cut or gas-oil ratio are restricted first. The model is a proxy of the simulator runs it
 * was built from: {@link HybridStepResult#isOutsideProxyValidity()} tells when a new simulator run is needed.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class HybridProductionModel {
  private static final Logger logger = LogManager.getLogger(HybridProductionModel.class);

  /** Fidelity of the lift and network models. */
  public enum Mode {
    /** Fast lift curves only. */
    SURROGATE,
    /** Reference (high-fidelity) models in every iteration. */
    FULL_NETWORK,
    /** Fast lift curves corrected against the reference models at an interval of time steps. */
    HYBRID
  }

  /** Time integration of the cumulative volumes. */
  public enum TimeIntegration {
    /** Rates at the start of the step are held over the step. First order. */
    EXPLICIT_EULER,
    /** Average of the rates at the start and at the predicted end of the step. Second order. */
    PREDICTOR_CORRECTOR,
    /**
     * Rates at the end of the step are held over the step, found by iteration. First order; this is how a reservoir
     * simulator relates its reported rates and cumulative volumes, so it reproduces the run a proxy was taken from.
     */
    IMPLICIT_EULER
  }

  private final String name;
  private final GatheringNetwork network;
  private Mode mode = Mode.SURROGATE;
  private TimeIntegration timeIntegration = TimeIntegration.PREDICTOR_CORRECTOR;
  private double implicitTolerance = 1.0e-6;
  private int maxImplicitIterations = 40;
  private int lastTimeIterations = 0;
  private int hybridUpdateInterval = 12;
  private int maxCorrectionPasses = 3;
  private double correctionToleranceBar = 0.05;
  private double pressureToleranceBar = 1.0e-5;
  private int maxIterations = 100;
  private FacilityConstraints constraints;
  private double allocationTolerance = 1.0e-3;
  private double allocationTieTolerance = 0.02;
  private final Map<ReservoirStateModel, double[]> injectionRates = new IdentityHashMap<ReservoirStateModel, double[]>();
  private double timeDays = 0.0;
  private int stepIndex = 0;
  private boolean solved = false;
  private boolean initialCorrectionDone = false;
  private double[] solvedSignature = new double[0];
  private Map<String, Double> nodePressures = new LinkedHashMap<String, Double>();
  private boolean lastConverged = false;
  private int lastIterations = 0;
  private double lastResidualBar = Double.NaN;
  private boolean lastCalibrated = false;
  private double lastOffsetChangeBar = 0.0;
  private int lastAllocationPasses = 0;
  private String lastBindingConstraint = "";
  private double lastSolveSeconds = 0.0;

  /**
   * Creates a model on a gathering network whose wells are already connected.
   *
   * @param name model name
   * @param network gathering network with wells
   */
  public HybridProductionModel(String name, GatheringNetwork network) {
    if (network == null) {
      throw new IllegalArgumentException("Network must not be null");
    }
    this.name = name;
    this.network = network;
  }

  /**
   * Gets the model name.
   *
   * @return model name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the gathering network.
   *
   * @return network
   */
  public GatheringNetwork getNetwork() {
    return network;
  }

  /**
   * Selects the fidelity mode.
   *
   * @param mode fidelity mode
   * @return this model
   */
  public HybridProductionModel setMode(Mode mode) {
    this.mode = mode;
    this.solved = false;
    return this;
  }

  /**
   * Gets the fidelity mode.
   *
   * @return fidelity mode
   */
  public Mode getMode() {
    return mode;
  }

  /**
   * Sets how often the lift curves are corrected against the reference models in {@link Mode#HYBRID}.
   *
   * @param steps number of time steps between corrections (at least 1)
   * @return this model
   */
  public HybridProductionModel setHybridUpdateInterval(int steps) {
    this.hybridUpdateInterval = Math.max(1, steps);
    return this;
  }

  /**
   * Sets the convergence controls of the well-network iteration.
   *
   * @param pressureToleranceBar largest accepted node pressure residual in bar
   * @param maxIterations iteration limit
   * @return this model
   */
  public HybridProductionModel setConvergence(double pressureToleranceBar, int maxIterations) {
    this.pressureToleranceBar = pressureToleranceBar;
    this.maxIterations = Math.max(1, maxIterations);
    return this;
  }

  /**
   * Sets the controls of the hybrid correction.
   *
   * @param toleranceBar correction passes stop when the largest offset change is below this value in bar
   * @param maxPasses largest number of correction passes at one time step
   * @return this model
   */
  public HybridProductionModel setCorrectionControls(double toleranceBar, int maxPasses) {
    this.correctionToleranceBar = toleranceBar;
    this.maxCorrectionPasses = Math.max(1, maxPasses);
    return this;
  }

  /**
   * Selects the time integration of the cumulative volumes: explicit Euler (rates at the start of the step) or the
   * second-order predictor-corrector (average of the rates at the start and at the predicted end of the step).
   *
   * @param corrector true for the predictor-corrector (default)
   * @return this model
   */
  public HybridProductionModel setCorrector(boolean corrector) {
    this.timeIntegration = corrector ? TimeIntegration.PREDICTOR_CORRECTOR : TimeIntegration.EXPLICIT_EULER;
    return this;
  }

  /**
   * Selects the time integration of the cumulative volumes.
   *
   * @param timeIntegration integration scheme
   * @return this model
   */
  public HybridProductionModel setTimeIntegration(TimeIntegration timeIntegration) {
    this.timeIntegration = timeIntegration;
    return this;
  }

  /**
   * Sets the controls of the implicit time integration.
   *
   * @param relativeTolerance the end-of-step rates are accepted when they change by less than this fraction of the
   * largest well rate; keep it above the rate noise left by the node pressure tolerance
   * @param maxIterations iteration limit per time step
   * @return this model
   */
  public HybridProductionModel setImplicitControls(double relativeTolerance, int maxIterations) {
    this.implicitTolerance = relativeTolerance;
    this.maxImplicitIterations = Math.max(1, maxIterations);
    return this;
  }

  /**
   * Gets the time integration of the cumulative volumes.
   *
   * @return integration scheme
   */
  public TimeIntegration getTimeIntegration() {
    return timeIntegration;
  }

  /**
   * Gets the number of iterations the implicit time integration used in the last step.
   *
   * @return iteration count, 0 for the explicit schemes
   */
  public int getLastTimeIterations() {
    return lastTimeIterations;
  }

  /**
   * Sets the handling capacities of the receiving facility.
   *
   * @param constraints facility capacities, or null for none
   * @return this model
   */
  public HybridProductionModel setFacilityConstraints(FacilityConstraints constraints) {
    this.constraints = constraints;
    this.solved = false;
    return this;
  }

  /**
   * Sets how close the intensities of two wells must be for the wells to be held back together under a binding facility
   * capacity. The intensity is the constrained quantity per oil volume, for example the gas-oil ratio under a gas
   * capacity. Wells within the tolerance of the highest intensity share the restriction in proportion to their rates;
   * with a tolerance of zero one well is restricted at a time.
   *
   * @param relativeTolerance relative intensity difference treated as a tie, default 0.02
   * @return this model
   */
  public HybridProductionModel setAllocationTieTolerance(double relativeTolerance) {
    if (!(relativeTolerance >= 0.0)) {
      throw new IllegalArgumentException("Tie tolerance must be zero or positive");
    }
    this.allocationTieTolerance = relativeTolerance;
    this.solved = false;
    return this;
  }

  /**
   * Sets the injection rates into a drainage unit. The cumulative injection is added to the unit and to the proxies of
   * the wells that belong to it, so that scenario blends on cumulative injection follow the strategy.
   *
   * @param unit drainage unit (or the state proxy of a single well)
   * @param waterSm3PerDay water injection rate in Sm3/day
   * @param gasSm3PerDay gas injection rate in Sm3/day
   * @return this model
   */
  public HybridProductionModel setInjectionRate(ReservoirStateModel unit, double waterSm3PerDay, double gasSm3PerDay) {
    injectionRates.put(unit, new double[] {waterSm3PerDay, gasSm3PerDay});
    return this;
  }

  /**
   * Replaces a drainage unit with a proxy from a new simulator run, carrying the cumulative volumes over.
   *
   * @param current unit in use
   * @param replacement proxy from the new simulator run
   */
  public void refreshDrainageUnit(ReservoirStateModel current, ReservoirStateModel replacement) {
    replacement.setCumulativeState(current.getCumulativeState());
    for (HybridWell well : network.getWells()) {
      if (well.getDrainageUnit() == current) {
        well.setDrainageUnit(replacement);
      }
    }
    double[] injection = injectionRates.remove(current);
    if (injection != null) {
      injectionRates.put(replacement, injection);
    }
    solved = false;
  }

  /**
   * Gets the model time.
   *
   * @return time in days
   */
  public double getTimeDays() {
    return timeDays;
  }

  /**
   * Sets the model time, for example to start part-way through field life.
   *
   * @param timeDays time in days
   */
  public void setTimeDays(double timeDays) {
    this.timeDays = timeDays;
  }

  /**
   * Switches every lift model between its corrected surrogate and its reference.
   *
   * @param useReference true to evaluate the reference models
   */
  private void applyFidelity(boolean useReference) {
    for (GatheringNetwork.Branch branch : network.getBranches()) {
      branch.getLiftModel().setUseReference(useReference);
    }
    for (HybridWell well : network.getWells()) {
      well.getTubing().setUseReference(useReference);
    }
  }

  /**
   * Removes the rate limits set by an earlier facility-capacity allocation.
   */
  private void resetAllocation() {
    for (HybridWell well : network.getWells()) {
      well.setAllocationLimit(Double.POSITIVE_INFINITY);
    }
  }

  /**
   * Solves the well rates and node pressures together by fixed-point iteration on the node pressures with Aitken
   * relaxation.
   */
  private void iterate() {
    List<HybridWell> wells = network.getWells();
    List<String> nodes = new ArrayList<String>();
    for (HybridWell well : wells) {
      String node = network.getNodeOf(well);
      if (!nodes.contains(node)) {
        nodes.add(node);
      }
    }
    int n = nodes.size();
    double[] pressure = new double[n];
    for (int i = 0; i < n; i++) {
      Double known = nodePressures.get(nodes.get(i));
      pressure[i] = known != null ? known.doubleValue() : network.getBoundaryPressure();
    }
    double[] previous = null;
    double omega = 1.0;
    lastConverged = false;
    lastResidualBar = Double.NaN;
    int iteration = 0;
    Map<String, Double> solvedPressures = nodePressures;
    for (iteration = 1; iteration <= maxIterations; iteration++) {
      for (HybridWell well : wells) {
        well.solveOperatingPoint(pressure[nodes.indexOf(network.getNodeOf(well))]);
      }
      try {
        solvedPressures = network.solvePressures();
      } catch (IllegalStateException ex) {
        logger.warn("Network pressure solve failed in model {}: {}", name, ex.getMessage());
        break;
      }
      double[] residual = new double[n];
      double largest = 0.0;
      for (int i = 0; i < n; i++) {
        residual[i] = solvedPressures.get(nodes.get(i)).doubleValue() - pressure[i];
        largest = Math.max(largest, Math.abs(residual[i]));
      }
      lastResidualBar = largest;
      if (largest < pressureToleranceBar) {
        lastConverged = true;
        break;
      }
      if (previous != null) {
        double numerator = 0.0;
        double denominator = 0.0;
        for (int i = 0; i < n; i++) {
          double difference = residual[i] - previous[i];
          numerator += previous[i] * difference;
          denominator += difference * difference;
        }
        if (denominator > 1.0e-30) {
          omega = -omega * numerator / denominator;
        }
        omega = Math.max(0.05, Math.min(1.0, omega));
      }
      for (int i = 0; i < n; i++) {
        pressure[i] = Math.max(0.1, pressure[i] + omega * residual[i]);
      }
      previous = residual;
    }
    lastIterations = Math.min(iteration, maxIterations);
    nodePressures = new LinkedHashMap<String, Double>(solvedPressures);
    if (!lastConverged) {
      logger.warn("Model {} did not converge at {} days: residual {} bar after {} iterations", name, timeDays,
          lastResidualBar, lastIterations);
    }
  }

  /**
   * Sums the well rates.
   *
   * @return field oil, water and gas rates in Sm3/day
   */
  private double[] totals() {
    double[] total = new double[3];
    for (HybridWell well : network.getWells()) {
      total[0] += well.getOilRate();
      total[1] += well.getWaterRate();
      total[2] += well.getGasRate();
    }
    return total;
  }

  /**
   * Ranks a well for restriction under a binding facility constraint: the well that uses most of the constrained
   * capacity per oil volume is held back first.
   *
   * @param quantity binding constraint
   * @param well flowing well
   * @return intensity; a higher value is restricted earlier
   */
  private static double intensity(FacilityConstraints.Quantity quantity, HybridWell well) {
    double oil = well.getOilRate();
    switch (quantity) {
    case WATER:
      return well.getWaterRate() / oil;
    case GAS:
      return well.getGasRate() / oil;
    default:
      return well.getWaterRate() / oil + 1.0e-6 * well.getGasRate() / oil;
    }
  }

  /**
   * Sets the allocation limit of every well in a group to a common fraction of its rate before the restriction.
   *
   * @param group wells restricted together
   * @param baseRates oil rate of each well before the restriction in Sm3/day
   * @param fraction fraction of the base rate that is allowed, 0 to 1
   */
  private static void restrict(List<HybridWell> group, double[] baseRates, double fraction) {
    for (int i = 0; i < group.size(); i++) {
      group.get(i).setAllocationLimit(fraction * baseRates[i]);
    }
  }

  /**
   * Holds wells back until the field rates fit the facility capacities. For the most utilised capacity the flowing well
   * with the highest intensity is restricted, together with the wells whose intensity lies within the tie tolerance of
   * it. The group is cut to a common fraction of its rates, found so that the capacity is just met with the other wells
   * responding through the network; if closing the group is not enough it stays closed and the next group is
   * restricted.
   */
  private void allocate() {
    lastAllocationPasses = 0;
    lastBindingConstraint = "";
    if (constraints == null) {
      return;
    }
    int maxPasses = 4 * network.getWells().size() + 4;
    for (int pass = 0; pass < maxPasses; pass++) {
      double[] total = totals();
      FacilityConstraints.Quantity quantity = constraints.mostUtilised(total[0], total[1], total[2]);
      if (quantity == null
          || constraints.utilisation(quantity, total[0], total[1], total[2]) <= 1.0 + allocationTolerance) {
        return;
      }
      double highestIntensity = Double.NEGATIVE_INFINITY;
      for (HybridWell well : network.getWells()) {
        if (well.getOilRate() > 0.0) {
          highestIntensity = Math.max(highestIntensity, intensity(quantity, well));
        }
      }
      List<HybridWell> swing = new ArrayList<HybridWell>();
      for (HybridWell well : network.getWells()) {
        if (well.getOilRate() > 0.0
            && intensity(quantity, well) >= highestIntensity - allocationTieTolerance * Math.abs(highestIntensity)) {
          swing.add(well);
        }
      }
      if (swing.isEmpty()) {
        return;
      }
      double[] baseRates = new double[swing.size()];
      for (int i = 0; i < baseRates.length; i++) {
        baseRates[i] = swing.get(i).getOilRate();
      }
      lastAllocationPasses++;
      lastBindingConstraint = quantity.name();
      double capacity = constraints.getCapacity(quantity);
      double high = 1.0;
      double excessHigh = FacilityConstraints.rateOf(quantity, total[0], total[1], total[2]) - capacity;
      double low = 0.0;
      restrict(swing, baseRates, 0.0);
      iterate();
      total = totals();
      double excessLow = FacilityConstraints.rateOf(quantity, total[0], total[1], total[2]) - capacity;
      if (excessLow > capacity * allocationTolerance) {
        continue;
      }
      int side = 0;
      for (int i = 0; i < 40; i++) {
        double limit = low + (high - low) * (-excessLow) / (excessHigh - excessLow);
        if (!(limit > low && limit < high)) {
          limit = 0.5 * (low + high);
        }
        restrict(swing, baseRates, limit);
        iterate();
        total = totals();
        double excess = FacilityConstraints.rateOf(quantity, total[0], total[1], total[2]) - capacity;
        if (excess > 0.0) {
          high = limit;
          excessHigh = excess;
          if (side == 1) {
            excessLow *= 0.5;
          }
          side = 1;
        } else {
          low = limit;
          excessLow = excess;
          if (side == -1) {
            excessHigh *= 0.5;
          }
          side = -1;
        }
        if (Math.abs(excess) <= capacity * allocationTolerance || high - low < 1.0e-9) {
          break;
        }
      }
      total = totals();
      if (FacilityConstraints.rateOf(quantity, total[0], total[1], total[2]) > capacity * (1.0 + allocationTolerance)) {
        restrict(swing, baseRates, low);
        iterate();
      }
    }
  }

  /**
   * Builds a signature of everything a caller can change between steps, to detect when the start-of-step solution is
   * stale.
   *
   * @return configuration values
   */
  private double[] signature() {
    List<Double> values = new ArrayList<Double>();
    values.add(network.getBoundaryPressure());
    values.add((double) mode.ordinal());
    for (HybridWell well : network.getWells()) {
      values.add(well.isOpen() ? 1.0 : 0.0);
      values.add(well.getOilRateTarget());
      values.add(well.getGasLiftRate());
      values.add(well.getChokeCoefficient());
      values.add(well.getMinimumBottomHolePressure());
      values.add((double) network.getNodeOf(well).hashCode());
      values.add((double) System.identityHashCode(well.getState()));
      values.add(well.getDrainageUnit() == null ? 0.0 : (double) System.identityHashCode(well.getDrainageUnit()));
    }
    for (GatheringNetwork.Branch branch : network.getBranches()) {
      values.add(branch.getLiftModel().getOffsetBar());
    }
    if (constraints != null) {
      for (FacilityConstraints.Quantity quantity : FacilityConstraints.Quantity.values()) {
        values.add(constraints.getCapacity(quantity));
      }
    }
    double[] out = new double[values.size()];
    for (int i = 0; i < out.length; i++) {
      out[i] = values.get(i).doubleValue();
    }
    return out;
  }

  /**
   * Solves the current state in the selected fidelity mode, including the facility-capacity allocation.
   */
  private void solveCurrentState() {
    long start = System.nanoTime();
    List<String> problems = network.validate();
    if (!problems.isEmpty()) {
      throw new IllegalStateException("Invalid gathering network: " + problems);
    }
    applyFidelity(mode == Mode.FULL_NETWORK);
    resetAllocation();
    iterate();
    allocate();
    lastSolveSeconds = (System.nanoTime() - start) / 1.0e9;
    solved = true;
    solvedSignature = signature();
  }

  /**
   * Corrects the fast lift curves of the branches and the tubing against their reference models at the current
   * operating point, repeating until the offsets settle, and leaves the model solved with the corrected curves.
   *
   * @return largest change of a pressure offset in bar
   */
  public double calibrateSurrogates() {
    long start = System.nanoTime();
    double largest = 0.0;
    for (int pass = 0; pass < maxCorrectionPasses; pass++) {
      applyFidelity(false);
      resetAllocation();
      iterate();
      allocate();
      double change = network.calibrate();
      for (HybridWell well : network.getWells()) {
        if (well.getOilRate() > 0.0 && well.getTubing().hasReference()) {
          change = Math.max(change, well.getTubing().calibrate(well.getOilRate(), well.getWellheadPressure(),
              well.getState().getWaterCut(), well.getState().getGasOilRatio(), well.getGasLiftRate()));
        }
      }
      largest = Math.max(largest, change);
      if (change < correctionToleranceBar) {
        break;
      }
    }
    applyFidelity(false);
    resetAllocation();
    iterate();
    allocate();
    lastCalibrated = true;
    lastOffsetChangeBar = largest;
    lastSolveSeconds = (System.nanoTime() - start) / 1.0e9;
    solved = true;
    solvedSignature = signature();
    initialCorrectionDone = true;
    return largest;
  }

  /**
   * Solves the well rates and node pressures at the current reservoir state without advancing time.
   *
   * @return snapshot of the solution
   */
  public HybridStepResult solve() {
    lastCalibrated = false;
    lastOffsetChangeBar = 0.0;
    if (mode == Mode.HYBRID && !initialCorrectionDone) {
      calibrateSurrogates();
    } else {
      solveCurrentState();
    }
    return snapshot();
  }

  /**
   * Collects the distinct state proxies of the model.
   *
   * @return state proxies of wells, drainage units and injection targets
   */
  private List<ReservoirStateModel> distinctStates() {
    Map<ReservoirStateModel, Boolean> seen = new IdentityHashMap<ReservoirStateModel, Boolean>();
    List<ReservoirStateModel> states = new ArrayList<ReservoirStateModel>();
    for (HybridWell well : network.getWells()) {
      if (seen.put(well.getState(), Boolean.TRUE) == null) {
        states.add(well.getState());
      }
      if (well.getDrainageUnit() != null && seen.put(well.getDrainageUnit(), Boolean.TRUE) == null) {
        states.add(well.getDrainageUnit());
      }
    }
    for (ReservoirStateModel unit : injectionRates.keySet()) {
      if (seen.put(unit, Boolean.TRUE) == null) {
        states.add(unit);
      }
    }
    return states;
  }

  /**
   * Reads the current well rates.
   *
   * @return oil, water and gas rate in Sm3/day for each well
   */
  private double[][] wellRates() {
    List<HybridWell> wells = network.getWells();
    double[][] rates = new double[wells.size()][];
    for (int i = 0; i < rates.length; i++) {
      HybridWell well = wells.get(i);
      rates[i] = new double[] {well.getOilRate(), well.getWaterRate(), well.getGasRate() - well.getGasLiftRate()};
    }
    return rates;
  }

  /**
   * Advances the cumulative volumes of all proxies.
   *
   * @param rates oil, water and gas rate in Sm3/day for each well
   * @param dtDays time step in days
   */
  private void advance(double[][] rates, double dtDays) {
    List<HybridWell> wells = network.getWells();
    for (int i = 0; i < rates.length; i++) {
      HybridWell well = wells.get(i);
      double oil = rates[i][0] * dtDays;
      double water = rates[i][1] * dtDays;
      double gas = Math.max(0.0, rates[i][2]) * dtDays;
      well.getState().produce(oil, water, gas);
      if (well.getDrainageUnit() != null && well.getDrainageUnit() != well.getState()) {
        well.getDrainageUnit().produce(oil, water, gas);
      }
    }
    for (Map.Entry<ReservoirStateModel, double[]> entry : injectionRates.entrySet()) {
      ReservoirStateModel unit = entry.getKey();
      double water = entry.getValue()[0] * dtDays;
      double gas = entry.getValue()[1] * dtDays;
      unit.inject(water, gas);
      for (HybridWell well : wells) {
        if (well.getDrainageUnit() == unit && well.getState() != unit) {
          well.getState().inject(water, gas);
        }
      }
    }
    for (ReservoirStateModel state : distinctStates()) {
      state.elapse(dtDays);
    }
  }

  /**
   * Advances the model one time step and solves the new state.
   *
   * <p>
   * Changes made by the caller since the last solve (rate targets, well status, routing, separator pressure) are taken
   * into account from the start of the step.
   * </p>
   *
   * @param dtDays time step in days
   * @return snapshot at the end of the step
   * @throws IllegalArgumentException if the time step is not positive
   */
  public HybridStepResult step(double dtDays) {
    if (!(dtDays > 0.0)) {
      throw new IllegalArgumentException("Time step must be positive: " + dtDays);
    }
    if (!solved || !Arrays.equals(solvedSignature, signature())) {
      if (mode == Mode.HYBRID && !initialCorrectionDone) {
        calibrateSurrogates();
      } else {
        solveCurrentState();
      }
    }
    double[][] startRates = wellRates();
    lastTimeIterations = 0;
    if (timeIntegration == TimeIntegration.IMPLICIT_EULER) {
      advance(implicitEndRates(startRates, dtDays), dtDays);
    } else if (timeIntegration == TimeIntegration.PREDICTOR_CORRECTOR) {
      List<ReservoirStateModel> states = distinctStates();
      List<double[]> saved = new ArrayList<double[]>();
      for (ReservoirStateModel state : states) {
        saved.add(state.getCumulativeState());
      }
      advance(startRates, dtDays);
      solveCurrentState();
      double[][] endRates = wellRates();
      for (int i = 0; i < states.size(); i++) {
        states.get(i).setCumulativeState(saved.get(i));
      }
      double[][] average = new double[startRates.length][3];
      for (int i = 0; i < average.length; i++) {
        for (int k = 0; k < 3; k++) {
          average[i][k] = 0.5 * (startRates[i][k] + endRates[i][k]);
        }
      }
      advance(average, dtDays);
    } else {
      advance(startRates, dtDays);
    }
    timeDays += dtDays;
    stepIndex++;
    lastCalibrated = false;
    lastOffsetChangeBar = 0.0;
    if (mode == Mode.HYBRID && stepIndex % hybridUpdateInterval == 0) {
      calibrateSurrogates();
    } else {
      solveCurrentState();
    }
    return snapshot();
  }

  /**
   * Finds the well rates at the end of a step that are consistent with the state they lead to (implicit Euler). The oil
   * rate of every well is iterated with a per-well secant on the difference between the rate assumed over the step and
   * the rate solved at the resulting state; water and gas follow the solved ratios. The cumulative volumes are left at
   * their start-of-step values.
   *
   * @param startRates oil, water and gas rate in Sm3/day for each well at the start of the step
   * @param dtDays time step in days
   * @return oil, water and gas rate in Sm3/day for each well at the end of the step
   */
  private double[][] implicitEndRates(double[][] startRates, double dtDays) {
    List<ReservoirStateModel> states = distinctStates();
    List<double[]> saved = new ArrayList<double[]>();
    for (ReservoirStateModel state : states) {
      saved.add(state.getCumulativeState());
    }
    int n = startRates.length;
    double[][] end = new double[n][];
    for (int i = 0; i < n; i++) {
      end[i] = startRates[i].clone();
    }
    double[] previousOil = null;
    double[] previousResidual = null;
    for (int iteration = 1; iteration <= maxImplicitIterations; iteration++) {
      lastTimeIterations = iteration;
      advance(end, dtDays);
      solveCurrentState();
      double[][] solvedRates = wellRates();
      for (int i = 0; i < states.size(); i++) {
        states.get(i).setCumulativeState(saved.get(i));
      }
      double[] residual = new double[n];
      double[] oil = new double[n];
      double largest = 0.0;
      double scale = 1.0;
      for (int i = 0; i < n; i++) {
        oil[i] = end[i][0];
        residual[i] = solvedRates[i][0] - end[i][0];
        largest = Math.max(largest, Math.abs(residual[i]));
        scale = Math.max(scale, Math.abs(solvedRates[i][0]));
      }
      if (largest <= implicitTolerance * scale) {
        return solvedRates;
      }
      for (int i = 0; i < n; i++) {
        double slope = -2.0;
        if (previousOil != null && Math.abs(oil[i] - previousOil[i]) > 1.0e-12 * scale) {
          slope = Math.min(-1.0, (residual[i] - previousResidual[i]) / (oil[i] - previousOil[i]));
        }
        double updated = Math.max(0.0, oil[i] - residual[i] / slope);
        double solvedOil = solvedRates[i][0];
        if (solvedOil > 0.0) {
          end[i][0] = updated;
          end[i][1] = updated * solvedRates[i][1] / solvedOil;
          end[i][2] = updated * solvedRates[i][2] / solvedOil;
        } else {
          double ratio = oil[i] > 0.0 ? updated / oil[i] : 0.0;
          end[i][0] = updated;
          end[i][1] *= ratio;
          end[i][2] *= ratio;
        }
      }
      previousOil = oil;
      previousResidual = residual;
    }
    logger.warn("Implicit time integration of model {} did not converge at {} days", name, timeDays);
    return end;
  }

  /**
   * Runs a production profile with constant settings.
   *
   * @param totalDays length of the profile in days
   * @param dtDays time step in days
   * @return snapshots at the start and after every step
   */
  public List<HybridStepResult> run(double totalDays, double dtDays) {
    List<HybridStepResult> profile = new ArrayList<HybridStepResult>();
    profile.add(solve());
    double end = timeDays + totalDays;
    while (timeDays < end - 1.0e-9) {
      profile.add(step(Math.min(dtDays, end - timeDays)));
    }
    return profile;
  }

  /**
   * Captures the current solution.
   *
   * @return snapshot of wells, node pressures and diagnostics
   */
  private HybridStepResult snapshot() {
    List<HybridStepResult.WellResult> wellResults = new ArrayList<HybridStepResult.WellResult>();
    long lift = 0;
    long reference = 0;
    for (HybridWell well : network.getWells()) {
      wellResults.add(new HybridStepResult.WellResult(well, network.getNodeOf(well)));
      lift += well.getTubing().getSurrogateEvaluations();
      reference += well.getTubing().getReferenceEvaluations();
    }
    for (GatheringNetwork.Branch branch : network.getBranches()) {
      lift += branch.getLiftModel().getSurrogateEvaluations();
      reference += branch.getLiftModel().getReferenceEvaluations();
    }
    return new HybridStepResult(timeDays, lastConverged, lastIterations, lastResidualBar, lastCalibrated,
        lastOffsetChangeBar, lastAllocationPasses, lastBindingConstraint, lift, reference, lastSolveSeconds,
        nodePressures, wellResults);
  }
}
