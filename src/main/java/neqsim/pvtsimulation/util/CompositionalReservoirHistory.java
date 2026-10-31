package neqsim.pvtsimulation.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.SerializationUtils;
import neqsim.process.equipment.reservoir.SimpleReservoir;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Measured component-history replay and bounded single-volume matching with a frozen laboratory-calibrated EOS.
 *
 * <p>
 * Reuses SimpleReservoir's fixed-volume, isothermal TV flash. Every trial restores the accepted tuned fluid and the
 * same initial pressure, temperature and phase-volume fractions. Only total initial pore volume is fitted. Pressure
 * errors are independent supplied one-sigma scales; held-out pressures never enter the objective. Component amounts are
 * measured inputs, so produced composition/GOR is not an independent prediction. Aquifer influx, rock expansion, energy
 * balances, spatial displacement, correlated errors and EOS retuning are outside this bounded model.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class CompositionalReservoirHistory implements Serializable {
  private static final long serialVersionUID = 1L;
  private final CalibratedPvtModel model;
  private final String historyReference;
  private final String[] components;
  private final double temperatureK;
  private final double pressureBara;
  private final double[] fractions;
  private final Step[] steps;

  /** Immutable measured amounts and optional end-of-interval pressure observation. */
  public static final class Step implements Serializable {
    private static final long serialVersionUID = 1L;
    private final double durationSeconds;
    private final double[] produced;
    private final double[] injected;
    private final double pressure;
    private final double sigma;
    private final boolean holdout;

    /**
     * Freezes one complete measured interval, using the history's named component order.
     *
     * @param durationSeconds positive interval duration in seconds
     * @param producedMoles nonnegative produced amounts, mol per component per interval
     * @param injectedMoles nonnegative injected amounts, mol per component per interval
     * @param pressureBara measured average end pressure in bara, or NaN for prediction only
     * @param sigmaBara positive one-sigma pressure error in bara, or NaN for prediction only
     * @param holdout true to exclude this pressure from volume fitting
     */
    public Step(double durationSeconds, double[] producedMoles, double[] injectedMoles, double pressureBara,
        double sigmaBara, boolean holdout) {
      requirePositive(durationSeconds, "Interval duration");
      if (producedMoles == null || injectedMoles == null || producedMoles.length == 0
          || producedMoles.length != injectedMoles.length) {
        throw new IllegalArgumentException("Produced and injected component arrays must have equal positive length");
      }
      this.produced = producedMoles.clone();
      this.injected = injectedMoles.clone();
      for (int i = 0; i < produced.length; i++) {
        if (!Double.isFinite(produced[i]) || produced[i] < 0.0 || !Double.isFinite(injected[i]) || injected[i] < 0.0) {
          throw new IllegalArgumentException("Component amounts must be finite and nonnegative");
        }
      }
      if (!(Double.isNaN(pressureBara) && Double.isNaN(sigmaBara))) {
        requirePositive(pressureBara, "Measured pressure");
        requirePositive(sigmaBara, "Pressure sigma");
      }
      this.durationSeconds = durationSeconds;
      this.pressure = pressureBara;
      this.sigma = sigmaBara;
      this.holdout = holdout;
    }

    /**
     * Returns interval duration in seconds.
     *
     * @return interval duration in seconds
     */
    public double getDurationSeconds() {
      return durationSeconds;
    }

    /**
     * Returns measured produced component amounts in mol.
     *
     * @return measured produced component amounts in mol
     */
    public double[] getProducedMoles() {
      return produced.clone();
    }

    /**
     * Returns measured injected component amounts in mol.
     *
     * @return measured injected component amounts in mol
     */
    public double[] getInjectedMoles() {
      return injected.clone();
    }

    /**
     * Returns measured end pressure in bara, or NaN for prediction only.
     *
     * @return measured end pressure in bara, or NaN for prediction only
     */
    public double getPressureBara() {
      return pressure;
    }

    /**
     * Returns supplied one-sigma pressure error in bara, or NaN for prediction only.
     *
     * @return supplied one-sigma pressure error in bara, or NaN for prediction only
     */
    public double getSigmaBara() {
      return sigma;
    }

    /**
     * Returns whether this pressure is excluded from fitting.
     *
     * @return whether this pressure is excluded from fitting
     */
    public boolean isHoldout() {
      return holdout;
    }
  }

  /** Immutable trial evidence; arrays include the initial state where indicated. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1L;
    private final double volume;
    private final double[] time;
    private final double[] pressure;
    private final double[][] inventory;
    private final double[] residuals;
    private final double calibrationChiSquare;
    private final double holdoutChiSquare;
    private final byte[] finalFluid;

    /**
     * Freezes successful replay evidence.
     *
     * @param volume initial pore volume in m3
     * @param time times including initial state, seconds
     * @param pressure pressures including initial state, bara
     * @param inventory component amounts including initial state, mol
     * @param residuals measured-minus-predicted standardized pressure residuals, one per interval
     * @param calibrationChiSquare sum of squared standardized calibration residuals
     * @param holdoutChiSquare sum of squared standardized held-out residuals
     * @param finalFluid accepted final fluid to freeze
     */
    private Result(double volume, double[] time, double[] pressure, double[][] inventory, double[] residuals,
        double calibrationChiSquare, double holdoutChiSquare, SystemInterface finalFluid) {
      this.volume = volume;
      this.time = time;
      this.pressure = pressure;
      this.inventory = inventory;
      this.residuals = residuals;
      this.calibrationChiSquare = calibrationChiSquare;
      this.holdoutChiSquare = holdoutChiSquare;
      this.finalFluid = SerializationUtils.serialize(finalFluid);
    }

    /**
     * Returns fitted or supplied initial pore volume in reservoir m3, not recoverable reserves.
     *
     * @return fitted or supplied initial pore volume in reservoir m3, not recoverable reserves
     */
    public double getInitialVolumeM3() {
      return volume;
    }

    /**
     * Returns times including the initial state, seconds.
     *
     * @return times including the initial state, seconds
     */
    public double[] getTimesSeconds() {
      return time.clone();
    }

    /**
     * Returns predicted pressures including the initial state, bara.
     *
     * @return predicted pressures including the initial state, bara
     */
    public double[] getPressuresBara() {
      return pressure.clone();
    }

    /**
     * Returns independent component-mole arrays including the initial state.
     *
     * @return independent component-mole arrays including the initial state
     */
    public double[][] getComponentMoles() {
      double[][] copy = new double[inventory.length][];
      for (int i = 0; i < copy.length; i++) {
        copy[i] = inventory[i].clone();
      }
      return copy;
    }

    /**
     * Returns standardized measured-minus-predicted residuals, NaN for prediction-only rows.
     *
     * @return standardized measured-minus-predicted residuals, NaN for prediction-only rows
     */
    public double[] getStandardizedResiduals() {
      return residuals.clone();
    }

    /**
     * Returns pressure-error weighted objective excluding held-out and prediction-only rows.
     *
     * @return pressure-error weighted objective excluding held-out and prediction-only rows
     */
    public double getCalibrationChiSquare() {
      return calibrationChiSquare;
    }

    /**
     * Returns squared standardized held-out pressure residual sum, never used in fitting.
     *
     * @return squared standardized held-out pressure residual sum, never used in fitting
     */
    public double getHoldoutChiSquare() {
      return holdoutChiSquare;
    }

    /**
     * Returns independent final EOS state, including phase and component compositions.
     *
     * @return independent final EOS state, including phase and component compositions
     */
    public SystemInterface getFinalFluid() {
      return SerializationUtils.deserialize(finalFluid);
    }
  }

  /** Bounded-search diagnostics; objective values and bounds are not uncertainty intervals. */
  public static final class Fit implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Result result;
    private final boolean atBound;
    private final String[] rejectedTrials;

    /**
     * Freezes the selected replay and unsuccessful trial diagnostics.
     *
     * @param result selected complete replay
     * @param atBound whether the selected result equals either supplied search bound
     * @param rejectedTrials rejected trial volumes and causes
     */
    private Fit(Result result, boolean atBound, List<String> rejectedTrials) {
      this.result = result;
      this.atBound = atBound;
      this.rejectedTrials = rejectedTrials.toArray(new String[rejectedTrials.size()]);
    }

    /**
     * Returns selected complete compositional replay.
     *
     * @return selected complete compositional replay
     */
    public Result getResult() {
      return result;
    }

    /**
     * Returns true if the selected volume is a supplied search bound.
     *
     * @return true if the selected volume is a supplied search bound
     */
    public boolean isAtBound() {
      return atBound;
    }

    /**
     * Returns rejected volumes and interval-specific failure messages.
     *
     * @return rejected volumes and interval-specific failure messages
     */
    public String[] getRejectedTrials() {
      return rejectedTrials.clone();
    }
  }

  /**
   * Defines a replay without calibrating or mutating either EOS checkpoint.
   *
   * @param model immutable original/tuned laboratory model bundle
   * @param historyReference measured amounts, composition/basis conversions and pressure provenance reference
   * @param components exact named component basis; all EOS components required, arbitrary order allowed
   * @param temperatureK fixed reservoir temperature in K
   * @param pressureBara initial reservoir pressure in bara
   * @param phaseVolumeFractions initial gas, oil and aqueous fractions of pore volume, summing to one
   * @param steps complete measured intervals in time order
   */
  public CompositionalReservoirHistory(CalibratedPvtModel model, String historyReference, String[] components,
      double temperatureK, double pressureBara, double[] phaseVolumeFractions, Step[] steps) {
    if (model == null || components == null || components.length == 0 || steps == null || steps.length == 0
        || phaseVolumeFractions == null || phaseVolumeFractions.length != 3) {
      throw new IllegalArgumentException("Model, named component basis, three phase fractions and history required");
    }
    requirePositive(temperatureK, "Reservoir temperature");
    requirePositive(pressureBara, "Initial pressure");
    this.model = model;
    this.historyReference = CalibratedPvtModel.reference(historyReference);
    this.components = components.clone();
    this.temperatureK = temperatureK;
    this.pressureBara = pressureBara;
    this.fractions = phaseVolumeFractions.clone();
    this.steps = steps.clone();
    double sum = 0.0;
    for (double fraction : fractions) {
      if (!Double.isFinite(fraction) || fraction < 0.0) {
        throw new IllegalArgumentException("Phase fractions must be finite and nonnegative");
      }
      sum += fraction;
    }
    if (Math.abs(sum - 1.0) > 1.0e-10) {
      throw new IllegalArgumentException("Initial phase-volume fractions must sum to one");
    }
    componentOrder(model.getTunedFluid());
    for (Step step : this.steps) {
      if (step == null || step.produced.length != components.length) {
        throw new IllegalArgumentException("Every interval must use the complete named component basis");
      }
    }
  }

  /**
   * Returns immutable laboratory calibration bundle, separate from reservoir fitting.
   *
   * @return immutable laboratory calibration bundle, separate from reservoir fitting
   */
  public CalibratedPvtModel getModel() {
    return model;
  }

  /**
   * Returns measured history provenance and basis-conversion reference.
   *
   * @return measured history provenance and basis-conversion reference
   */
  public String getHistoryReference() {
    return historyReference;
  }

  /**
   * Returns named component order for all input and result arrays.
   *
   * @return named component order for all input and result arrays
   */
  public String[] getComponents() {
    return components.clone();
  }

  /**
   * Returns immutable measured intervals, copied in time order.
   *
   * @return immutable measured intervals, copied in time order
   */
  public Step[] getSteps() {
    return steps.clone();
  }

  /**
   * Replays the complete schedule from the frozen initial state for one volume.
   *
   * @param initialVolumeM3 total initial gas/oil/aqueous pore volume, m3
   * @return complete immutable pressure/component evidence
   * @throws IllegalStateException if initialization or any interval is infeasible
   */
  public Result replay(double initialVolumeM3) {
    requirePositive(initialVolumeM3, "Initial volume");
    SystemInterface fluid = model.getTunedFluid();
    fluid.setTemperature(temperatureK);
    fluid.setPressure(pressureBara);
    new ThermodynamicOperations(fluid).TPflash();
    String[] phases = {"gas", "oil", "aqueous"};
    for (int i = 0; i < phases.length; i++) {
      if (fractions[i] > 0.0 && !fluid.hasPhaseType(phases[i])) {
        throw new IllegalStateException("Requested initial phase is absent from the accepted EOS: " + phases[i]);
      }
    }
    SimpleReservoir tank = new SimpleReservoir("measured compositional history");
    tank.setRecordProductionHistory(false);
    tank.setReservoirFluid(fluid, initialVolumeM3 * fractions[0], initialVolumeM3 * fractions[1],
        initialVolumeM3 * fractions[2]);
    tank.getReservoirFluid().init(1);
    double initialVolume = tank.getReservoirFluid().getVolume("m3");
    if (!Double.isFinite(initialVolume) || Math.abs(initialVolume - initialVolumeM3) > initialVolumeM3 * 1.0e-7
        || Math.abs(tank.getReservoirFluid().getPressure() - pressureBara) > pressureBara * 1.0e-8) {
      throw new IllegalStateException("Initial tank state does not reproduce the supplied volume and pressure");
    }
    int[] order = componentOrder(tank.getReservoirFluid());
    double[] times = new double[steps.length + 1];
    double[] pressures = new double[times.length];
    double[][] inventories = new double[times.length][];
    double[] residuals = new double[steps.length];
    pressures[0] = tank.getReservoirFluid().getPressure();
    inventories[0] = inventory(tank.getReservoirFluid(), order);
    double calibration = 0.0;
    double holdout = 0.0;
    for (int row = 0; row < steps.length; row++) {
      Step step = steps[row];
      double[] produced = new double[order.length];
      double[] injected = new double[order.length];
      for (int i = 0; i < order.length; i++) {
        produced[order[i]] = step.produced[i];
        injected[order[i]] = step.injected[i];
      }
      try {
        tank.advanceComponentAmounts(step.durationSeconds, produced, injected, UUID.randomUUID());
      } catch (RuntimeException failure) {
        throw new IllegalStateException(
            "History interval " + row + " at volume " + initialVolumeM3 + " m3: " + failure.getMessage(), failure);
      }
      times[row + 1] = tank.getTime();
      pressures[row + 1] = tank.getReservoirFluid().getPressure();
      inventories[row + 1] = inventory(tank.getReservoirFluid(), order);
      residuals[row] = (step.pressure - pressures[row + 1]) / step.sigma;
      if (!Double.isNaN(step.pressure)) {
        double square = residuals[row] * residuals[row];
        if (!Double.isFinite(square)) {
          throw new IllegalStateException("Non-finite pressure residual at interval " + row);
        }
        if (step.holdout) {
          holdout += square;
        } else {
          calibration += square;
        }
      }
    }
    if (!Double.isFinite(calibration) || !Double.isFinite(holdout)) {
      throw new IllegalStateException("Non-finite pressure objective");
    }
    return new Result(initialVolumeM3, times, pressures, inventories, residuals, calibration, holdout,
        tank.getReservoirFluid());
  }

  /**
   * Searches bounded log-volume using 17 grid trials and golden-section refinement of the best grid basin.
   *
   * <p>
   * Requires two calibration pressure observations. Failed trials are reported and excluded, never replaced by a
   * pressure prediction. The bounds and fixed phase fractions are supplied geological assumptions. The search is a
   * deterministic one-parameter estimate, not a global-optimum proof, posterior, confidence interval or EOS fit.
   * </p>
   *
   * @param minimumVolumeM3 positive lower pore-volume bound in m3
   * @param maximumVolumeM3 finite upper bound larger than the lower bound
   * @return best complete replay with rejected-trial and bound diagnostics
   * @throws IllegalStateException if no informative feasible calibration trials exist
   */
  public Fit fitVolume(double minimumVolumeM3, double maximumVolumeM3) {
    requirePositive(minimumVolumeM3, "Minimum volume");
    requirePositive(maximumVolumeM3, "Maximum volume");
    if (maximumVolumeM3 <= minimumVolumeM3) {
      throw new IllegalArgumentException("Volume bounds must increase strictly");
    }
    int observations = 0;
    for (Step step : steps) {
      if (!step.holdout && !Double.isNaN(step.pressure)) {
        observations++;
      }
    }
    if (observations < 2) {
      throw new IllegalArgumentException("Volume matching requires at least two calibration pressure observations");
    }
    List<String> rejected = new ArrayList<String>();
    double low = Math.log(minimumVolumeM3);
    double high = Math.log(maximumVolumeM3);
    double width = (high - low) / 16.0;
    Result best = null;
    double largest = Double.NEGATIVE_INFINITY;
    int selected = -1;
    for (int i = 0; i <= 16; i++) {
      double volume = i == 0 ? minimumVolumeM3 : i == 16 ? maximumVolumeM3 : Math.exp(low + i * width);
      Result trial = trial(volume, rejected);
      if (trial != null) {
        largest = Math.max(largest, trial.calibrationChiSquare);
        if (best == null || trial.calibrationChiSquare < best.calibrationChiSquare) {
          best = trial;
          selected = i;
        }
      }
    }
    if (best == null || largest - best.calibrationChiSquare <= 1.0e-10 * Math.max(1.0, largest)) {
      throw new IllegalStateException("No informative feasible volume trials; rejected: " + rejected);
    }
    double left = low + Math.max(0, selected - 1) * width;
    double right = low + Math.min(16, selected + 1) * width;
    double ratio = (Math.sqrt(5.0) - 1.0) / 2.0;
    double x1 = right - ratio * (right - left);
    double x2 = left + ratio * (right - left);
    Result r1 = trial(Math.exp(x1), rejected);
    Result r2 = trial(Math.exp(x2), rejected);
    for (int iteration = 0; iteration < 80 && right - left > 1.0e-7; iteration++) {
      if (r1 != null && r1.calibrationChiSquare < best.calibrationChiSquare) {
        best = r1;
      }
      if (r2 != null && r2.calibrationChiSquare < best.calibrationChiSquare) {
        best = r2;
      }
      if (objective(r1) < objective(r2)) {
        right = x2;
        x2 = x1;
        r2 = r1;
        x1 = right - ratio * (right - left);
        r1 = trial(Math.exp(x1), rejected);
      } else {
        left = x1;
        x1 = x2;
        r1 = r2;
        x2 = left + ratio * (right - left);
        r2 = trial(Math.exp(x2), rejected);
      }
    }
    return new Fit(best, best.volume == minimumVolumeM3 || best.volume == maximumVolumeM3, rejected);
  }

  /**
   * Evaluates one complete volume trial, retaining infeasibility evidence.
   *
   * @param volume trial pore volume in m3
   * @param rejected trial rejection evidence collector
   * @return successful replay or null for a rejected trial
   */
  private Result trial(double volume, List<String> rejected) {
    try {
      return replay(volume);
    } catch (IllegalArgumentException | IllegalStateException failure) {
      rejected.add(volume + " m3: " + failure.getMessage());
      return null;
    }
  }

  /**
   * Returns a calibration-only search objective.
   *
   * @param result complete replay, or null if infeasible
   * @return chi-square or positive infinity for an infeasible trial
   */
  private static double objective(Result result) {
    return result == null ? Double.POSITIVE_INFINITY : result.calibrationChiSquare;
  }

  /**
   * Maps the named measured basis to the EOS component indices and rejects missing/duplicate identities.
   *
   * @param fluid accepted EOS state
   * @return EOS index for each measured component
   */
  private int[] componentOrder(SystemInterface fluid) {
    String[] names = fluid.getComponentNames();
    if (names.length != components.length) {
      throw new IllegalArgumentException("Measured component basis must include every accepted EOS component");
    }
    int[] order = new int[components.length];
    boolean[] used = new boolean[names.length];
    for (int i = 0; i < components.length; i++) {
      int match = -1;
      for (int j = 0; j < names.length; j++) {
        if (names[j].equals(components[i]) && !used[j]) {
          match = j;
          break;
        }
      }
      if (match < 0) {
        throw new IllegalArgumentException("Unknown or duplicate measured component: " + components[i]);
      }
      order[i] = match;
      used[match] = true;
    }
    return order;
  }

  /**
   * Captures inventories on the measured named basis.
   *
   * @param fluid accepted tank state
   * @param order EOS indices in measured order
   * @return component amounts in mol
   */
  private static double[] inventory(SystemInterface fluid, int[] order) {
    double[] amounts = new double[order.length];
    for (int i = 0; i < order.length; i++) {
      amounts[i] = fluid.getComponent(order[i]).getNumberOfmoles();
    }
    return amounts;
  }

  /**
   * Rejects nonpositive or non-finite physical scales.
   *
   * @param value supplied physical scale
   * @param label field name for diagnostics
   */
  private static void requirePositive(double value, String label) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(label + " must be finite and positive");
    }
  }
}
