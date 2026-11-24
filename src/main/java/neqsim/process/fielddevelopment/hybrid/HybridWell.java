package neqsim.process.fielddevelopment.hybrid;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import neqsim.process.equipment.reservoir.InflowPerformance;
import neqsim.process.fielddevelopment.reservoir.VlpProvider;

/**
 * Production well of the hybrid production model: reservoir-state proxy, inflow performance, tubing lift curve and
 * choke.
 *
 * <p>
 * The well rate is not read from a production profile. For a given pressure at the network node the well is tied to,
 * {@link #solveOperatingPoint(double)} finds the oil rate where the flowing bottom-hole pressure offered by the
 * reservoir (inflow performance at the current reservoir pressure and productivity index) equals the pressure the
 * tubing needs to lift that rate to the node (lift curve at the current water cut and gas-oil ratio). A rate target or
 * a choke restricts the well below that potential. Reservoir pressure, productivity index, water cut and gas-oil ratio
 * come from {@link ReservoirStateModel} proxies that follow the cumulative volumes, so the operating point moves both
 * with depletion and with the network back-pressure.
 * </p>
 *
 * <p>
 * Basis: rates are stock-tank oil in Sm3/day, the productivity index is Sm3 oil/(day bar), water cut is the water
 * fraction of the liquid and the gas-oil ratio is Sm3/Sm3. The tubing lift model must use the same basis (see
 * {@link VfpTableLookup#onOilBasis()}).
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class HybridWell {
  /** What limits the well rate at the current solution. */
  public enum ControlMode {
    /** The well is closed. */
    SHUT,
    /** The well is open but cannot lift against the node pressure. */
    NO_FLOW,
    /** The well produces at its rate target; the choke takes the surplus pressure. */
    RATE_TARGET,
    /** The well is held back by the facility-capacity allocation. */
    FACILITY_LIMIT,
    /** The well is fully open and its rate is set by inflow, lift and network back-pressure. */
    NETWORK_PRESSURE,
    /** The well produces at its minimum flowing bottom-hole pressure. */
    BHP_LIMIT
  }

  private static final double NO_LIFT = -1.0e6;
  private final String name;
  private ReservoirStateModel state;
  private ReservoirStateModel drainageUnit;
  private final CorrectedVlpProvider tubing;
  private InflowPerformance.Model inflowModel = InflowPerformance.Model.LINEAR;
  private double bubblePointPressure = 0.0;
  private boolean open = true;
  private double oilRateTarget = Double.POSITIVE_INFINITY;
  private double allocationLimit = Double.POSITIVE_INFINITY;
  private double minBottomHolePressure = 1.01325;
  private double chokeCoefficient = 0.0;
  private double gasLiftRate = 0.0;
  private double oilRate = 0.0;
  private double waterRate = 0.0;
  private double gasRate = 0.0;
  private double bottomHolePressure = Double.NaN;
  private double wellheadPressure = Double.NaN;
  private double nodePressure = Double.NaN;
  private ControlMode controlMode = ControlMode.SHUT;
  private long liftEvaluations = 0;

  /**
   * Creates a well.
   *
   * @param name well name
   * @param state reservoir-state proxy following the well's own cumulative volumes; supplies water cut, gas-oil ratio
   * and productivity index, and the reservoir pressure unless a drainage unit is set
   * @param tubing tubing lift model on the oil rate, water cut and gas-oil ratio basis, giving the flowing bottom-hole
   * pressure at the well datum for a wellhead pressure
   */
  @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "The well is driven by the caller's state proxy, whose cumulative volumes the model advances")
  public HybridWell(String name, ReservoirStateModel state, VlpProvider tubing) {
    if (name == null || state == null || tubing == null) {
      throw new IllegalArgumentException("Well name, state proxy and tubing lift model must not be null");
    }
    this.name = name;
    this.state = state;
    this.tubing = tubing instanceof CorrectedVlpProvider ? (CorrectedVlpProvider) tubing
        : new CorrectedVlpProvider(tubing);
  }

  /**
   * Gets the well name.
   *
   * @return well name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the well's own reservoir-state proxy.
   *
   * @return state proxy
   */
  @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Callers set and read the cumulative volumes on the well's live state proxy")
  public ReservoirStateModel getState() {
    return state;
  }

  /**
   * Replaces the state proxy with one from a new simulator run and carries the cumulative volumes over.
   *
   * @param newState proxy from the new simulator run
   */
  @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "The replacement proxy becomes the live state that the model advances")
  public void refreshState(ReservoirStateModel newState) {
    if (newState == null) {
      throw new IllegalArgumentException("New state proxy must not be null");
    }
    newState.setCumulativeState(state.getCumulativeState());
    this.state = newState;
  }

  /**
   * Sets a drainage unit shared with other wells. The reservoir pressure is then taken from the unit, which follows the
   * cumulative volumes of all its wells, while water cut, gas-oil ratio and productivity index stay with the well's own
   * proxy.
   *
   * @param unit shared reservoir-state proxy, or null to use the well's own pressure
   * @return this well
   */
  @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "A drainage unit is one state object shared by all wells that drain it")
  public HybridWell setDrainageUnit(ReservoirStateModel unit) {
    this.drainageUnit = unit;
    return this;
  }

  /**
   * Gets the drainage unit.
   *
   * @return shared reservoir-state proxy, or null
   */
  @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The model groups wells by the identity of their shared drainage unit")
  public ReservoirStateModel getDrainageUnit() {
    return drainageUnit;
  }

  /**
   * Gets the tubing lift model with its correction state.
   *
   * @return corrected tubing lift model
   */
  @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Callers attach a reference model and read or set the correction on the live tubing model")
  public CorrectedVlpProvider getTubing() {
    return tubing;
  }

  /**
   * Selects the inflow performance relationship.
   *
   * @param model LINEAR (default, the well model of a reservoir simulator), VOGEL or COMPOSITE
   * @param bubblePointBara bubble point pressure in bara, used by COMPOSITE only
   * @return this well
   */
  public HybridWell setInflowModel(InflowPerformance.Model model, double bubblePointBara) {
    this.inflowModel = model;
    this.bubblePointPressure = bubblePointBara;
    return this;
  }

  /**
   * Opens or closes the well.
   *
   * @param open true for an open well
   * @return this well
   */
  public HybridWell setOpen(boolean open) {
    this.open = open;
    return this;
  }

  /**
   * Tells whether the well is open.
   *
   * @return true for an open well
   */
  public boolean isOpen() {
    return open;
  }

  /**
   * Sets the oil rate target (choke setting expressed as a rate). The well produces the target when it can deliver it
   * and its potential otherwise.
   *
   * @param targetSm3PerDay oil rate target in Sm3/day; {@link Double#POSITIVE_INFINITY} for a fully open well
   * @return this well
   */
  public HybridWell setOilRateTarget(double targetSm3PerDay) {
    this.oilRateTarget = Math.max(0.0, targetSm3PerDay);
    return this;
  }

  /**
   * Gets the oil rate target.
   *
   * @return oil rate target in Sm3/day, infinite for a fully open well
   */
  public double getOilRateTarget() {
    return oilRateTarget;
  }

  /**
   * Sets the rate limit imposed by the facility-capacity allocation. Managed by {@link HybridProductionModel}.
   *
   * @param limitSm3PerDay oil rate limit in Sm3/day; {@link Double#POSITIVE_INFINITY} for no limit
   */
  void setAllocationLimit(double limitSm3PerDay) {
    this.allocationLimit = Math.max(0.0, limitSm3PerDay);
  }

  /**
   * Gets the rate limit imposed by the facility-capacity allocation.
   *
   * @return oil rate limit in Sm3/day, infinite when the well is not held back
   */
  public double getAllocationLimit() {
    return allocationLimit;
  }

  /**
   * Sets the minimum flowing bottom-hole pressure.
   *
   * @param pressureBara minimum bottom-hole pressure in bara
   * @return this well
   */
  public HybridWell setMinimumBottomHolePressure(double pressureBara) {
    this.minBottomHolePressure = Math.max(0.0, pressureBara);
    return this;
  }

  /**
   * Sets a fixed choke as a quadratic pressure drop between the wellhead and the network node: dP = K q_liquid^2.
   *
   * @param barPerSm3PerDaySquared choke coefficient K in bar/(Sm3 liquid/day)^2; 0 for no choke loss
   * @return this well
   */
  public HybridWell setChokeCoefficient(double barPerSm3PerDaySquared) {
    this.chokeCoefficient = Math.max(0.0, barPerSm3PerDaySquared);
    return this;
  }

  /**
   * Gets the minimum flowing bottom-hole pressure.
   *
   * @return minimum bottom-hole pressure in bara
   */
  public double getMinimumBottomHolePressure() {
    return minBottomHolePressure;
  }

  /**
   * Gets the fixed choke coefficient.
   *
   * @return choke coefficient in bar/(Sm3 liquid/day)^2
   */
  public double getChokeCoefficient() {
    return chokeCoefficient;
  }

  /**
   * Sets the gas lift rate passed to the tubing lift model as artificial lift quantity and added to the produced gas.
   *
   * @param rateSm3PerDay lift gas rate in Sm3/day
   * @return this well
   */
  public HybridWell setGasLiftRate(double rateSm3PerDay) {
    this.gasLiftRate = Math.max(0.0, rateSm3PerDay);
    return this;
  }

  /**
   * Gets the gas lift rate.
   *
   * @return lift gas rate in Sm3/day
   */
  public double getGasLiftRate() {
    return gasLiftRate;
  }

  /**
   * Gets the current reservoir pressure, from the drainage unit when one is set.
   *
   * @return reservoir pressure in bara
   */
  public double getReservoirPressure() {
    return drainageUnit != null ? drainageUnit.getReservoirPressure() : state.getReservoirPressure();
  }

  /**
   * Builds the inflow performance relationship at the current reservoir state.
   *
   * @return inflow performance, or null when the well has no productivity or reservoir pressure
   * @throws IllegalStateException if the state proxy has no productivity index
   */
  public InflowPerformance createInflow() {
    double productivityIndex = state.getProductivityIndex();
    if (Double.isNaN(productivityIndex)) {
      throw new IllegalStateException("State proxy of well " + name + " has no productivity index column");
    }
    double reservoirPressure = getReservoirPressure();
    if (!(productivityIndex > 0.0) || !(reservoirPressure > 0.0)) {
      return null;
    }
    return new InflowPerformance(inflowModel, productivityIndex, reservoirPressure, bubblePointPressure);
  }

  /**
   * Pressure drop over the fixed choke.
   *
   * @param oilRateSm3PerDay oil rate in Sm3/day
   * @param waterCut water fraction of the liquid
   * @return choke pressure drop in bar
   */
  private double chokePressureDrop(double oilRateSm3PerDay, double waterCut) {
    if (chokeCoefficient <= 0.0) {
      return 0.0;
    }
    double liquid = oilRateSm3PerDay / (1.0 - waterCut);
    return chokeCoefficient * liquid * liquid;
  }

  /**
   * Bottom-hole pressure surplus: pressure offered by the reservoir minus pressure needed by the tubing.
   *
   * @param inflow inflow performance at the current state
   * @param rate oil rate in Sm3/day
   * @param node pressure of the network node in bara
   * @param waterCut water fraction of the liquid
   * @param gasOilRatio gas-oil ratio in Sm3/Sm3
   * @return surplus in bar; a large negative number when the tubing cannot lift the rate
   */
  private double surplus(InflowPerformance inflow, double rate, double node, double waterCut, double gasOilRatio) {
    liftEvaluations++;
    double required = tubing.bottomHolePressure(rate, node + chokePressureDrop(rate, waterCut), waterCut, gasOilRatio,
        gasLiftRate);
    if (Double.isNaN(required) || Double.isInfinite(required)) {
      return NO_LIFT;
    }
    return inflow.bottomHolePressure(rate) - required;
  }

  /**
   * Finds the highest oil rate at or below an upper rate that the well can lift to the node.
   *
   * @param inflow inflow performance at the current state
   * @param upperRate upper oil rate in Sm3/day
   * @param node pressure of the network node in bara
   * @param waterCut water fraction of the liquid
   * @param gasOilRatio gas-oil ratio in Sm3/Sm3
   * @return array of the oil rate in Sm3/day and 1.0 when the upper rate itself is deliverable, else 0.0
   */
  private double[] deliverableRate(InflowPerformance inflow, double upperRate, double node, double waterCut,
      double gasOilRatio) {
    double high = upperRate;
    double surplusHigh = surplus(inflow, high, node, waterCut, gasOilRatio);
    if (surplusHigh >= 0.0) {
      return new double[] {high, 1.0};
    }
    double low = Double.NaN;
    double surplusLow = Double.NaN;
    if (oilRate > 0.0 && 0.9 * oilRate < high) {
      double trialLow = 0.9 * oilRate;
      double trialSurplus = surplus(inflow, trialLow, node, waterCut, gasOilRatio);
      if (trialSurplus >= 0.0) {
        low = trialLow;
        surplusLow = trialSurplus;
        double trialHigh = 1.1 * oilRate;
        if (trialHigh < high) {
          double s = surplus(inflow, trialHigh, node, waterCut, gasOilRatio);
          if (s < 0.0) {
            high = trialHigh;
            surplusHigh = s;
          } else {
            low = trialHigh;
            surplusLow = s;
          }
        }
      } else {
        high = trialLow;
        surplusHigh = trialSurplus;
      }
    }
    if (Double.isNaN(low)) {
      int steps = 12;
      double top = high;
      for (int k = 1; k <= steps; k++) {
        double trial = k < steps ? top * (1.0 - (double) k / steps) : 0.01 * top;
        double s = surplus(inflow, trial, node, waterCut, gasOilRatio);
        if (s >= 0.0) {
          low = trial;
          surplusLow = s;
          break;
        }
        high = trial;
        surplusHigh = s;
      }
      if (Double.isNaN(low)) {
        return new double[] {0.0, 0.0};
      }
    }
    int side = 0;
    for (int i = 0; i < 80; i++) {
      if (high - low <= 1.0e-9 * upperRate || surplusLow < 1.0e-6) {
        break;
      }
      double trial = low + (high - low) * surplusLow / (surplusLow - surplusHigh);
      if (!(trial > low && trial < high)) {
        trial = 0.5 * (low + high);
      }
      double s = surplus(inflow, trial, node, waterCut, gasOilRatio);
      if (s >= 0.0) {
        low = trial;
        surplusLow = s;
        if (side == 1) {
          surplusHigh *= 0.5;
        }
        side = 1;
      } else {
        high = trial;
        surplusHigh = s;
        if (side == -1) {
          surplusLow *= 0.5;
        }
        side = -1;
      }
    }
    return new double[] {low, 0.0};
  }

  /**
   * Finds the wellhead pressure upstream of the choke for a well that produces below its potential.
   *
   * @param rate oil rate in Sm3/day
   * @param bottomHole flowing bottom-hole pressure in bara
   * @param start lowest possible wellhead pressure in bara
   * @param waterCut water fraction of the liquid
   * @param gasOilRatio gas-oil ratio in Sm3/Sm3
   * @return wellhead pressure in bara
   */
  private double upstreamWellheadPressure(double rate, double bottomHole, double start, double waterCut,
      double gasOilRatio) {
    double pressure = start;
    double slope = 1.0;
    double previousPressure = Double.NaN;
    double previousResidual = Double.NaN;
    for (int i = 0; i < 30; i++) {
      liftEvaluations++;
      double required = tubing.bottomHolePressure(rate, pressure, waterCut, gasOilRatio, gasLiftRate);
      if (Double.isNaN(required) || Double.isInfinite(required)) {
        return pressure;
      }
      double residual = required - bottomHole;
      if (Math.abs(residual) < 1.0e-5) {
        break;
      }
      if (!Double.isNaN(previousPressure) && Math.abs(pressure - previousPressure) > 1.0e-12) {
        double secant = (residual - previousResidual) / (pressure - previousPressure);
        if (secant > 0.2 && secant < 5.0) {
          slope = secant;
        }
      }
      previousPressure = pressure;
      previousResidual = residual;
      pressure = Math.max(start, pressure - residual / slope);
    }
    return pressure;
  }

  /**
   * Stores a zero-rate solution.
   *
   * @param mode control mode to report
   * @param node pressure of the network node in bara
   * @return 0.0
   */
  private double noFlow(ControlMode mode, double node) {
    oilRate = 0.0;
    waterRate = 0.0;
    gasRate = 0.0;
    bottomHolePressure = Double.NaN;
    wellheadPressure = node;
    controlMode = mode;
    return 0.0;
  }

  /**
   * Solves the well operating point against a network node pressure and stores the solution in the well.
   *
   * @param nodePressureBara pressure of the network node the well is tied to, in bara
   * @return oil rate in Sm3/day
   * @throws IllegalStateException if the state proxy lacks the productivity index or the gas-oil ratio
   */
  public double solveOperatingPoint(double nodePressureBara) {
    this.nodePressure = nodePressureBara;
    if (!open) {
      return noFlow(ControlMode.SHUT, nodePressureBara);
    }
    InflowPerformance inflow = createInflow();
    double waterCut = state.getWaterCut();
    double gasOilRatio = state.getGasOilRatio();
    if (Double.isNaN(gasOilRatio)) {
      throw new IllegalStateException("State proxy of well " + name + " has no gas-oil ratio column");
    }
    if (inflow == null) {
      return noFlow(ControlMode.NO_FLOW, nodePressureBara);
    }
    double maximumRate = inflow.rate(minBottomHolePressure);
    double cap = Math.min(oilRateTarget, allocationLimit);
    ControlMode capMode = allocationLimit < oilRateTarget ? ControlMode.FACILITY_LIMIT : ControlMode.RATE_TARGET;
    if (cap <= 0.0) {
      return noFlow(capMode == ControlMode.RATE_TARGET ? ControlMode.SHUT : capMode, nodePressureBara);
    }
    if (!(maximumRate > 0.0)) {
      return noFlow(ControlMode.NO_FLOW, nodePressureBara);
    }
    double upper = Math.min(cap, maximumRate);
    double[] solution = deliverableRate(inflow, upper, nodePressureBara, waterCut, gasOilRatio);
    double rate = solution[0];
    if (!(rate > 0.0)) {
      return noFlow(ControlMode.NO_FLOW, nodePressureBara);
    }
    oilRate = rate;
    waterRate = rate * waterCut / (1.0 - waterCut);
    gasRate = rate * gasOilRatio + gasLiftRate;
    bottomHolePressure = inflow.bottomHolePressure(rate);
    double downstreamOfTubing = nodePressureBara + chokePressureDrop(rate, waterCut);
    if (solution[1] > 0.5) {
      controlMode = cap <= maximumRate ? capMode : ControlMode.BHP_LIMIT;
      wellheadPressure = upstreamWellheadPressure(rate, bottomHolePressure, downstreamOfTubing, waterCut, gasOilRatio);
    } else {
      controlMode = ControlMode.NETWORK_PRESSURE;
      wellheadPressure = downstreamOfTubing;
    }
    return oilRate;
  }

  /**
   * Calculates the oil rate potential of the fully open well against a node pressure, without changing the stored
   * solution or the rate target.
   *
   * @param nodePressureBara pressure of the network node in bara
   * @return potential oil rate in Sm3/day
   */
  public double potentialOilRate(double nodePressureBara) {
    if (!open) {
      return 0.0;
    }
    InflowPerformance inflow = createInflow();
    if (inflow == null) {
      return 0.0;
    }
    double maximumRate = inflow.rate(minBottomHolePressure);
    if (!(maximumRate > 0.0)) {
      return 0.0;
    }
    return deliverableRate(inflow, maximumRate, nodePressureBara, state.getWaterCut(), state.getGasOilRatio())[0];
  }

  /**
   * Gets the solved oil rate.
   *
   * @return oil rate in Sm3/day
   */
  public double getOilRate() {
    return oilRate;
  }

  /**
   * Gets the solved water rate.
   *
   * @return water rate in Sm3/day
   */
  public double getWaterRate() {
    return waterRate;
  }

  /**
   * Gets the solved gas rate including lift gas.
   *
   * @return gas rate in Sm3/day
   */
  public double getGasRate() {
    return gasRate;
  }

  /**
   * Gets the solved flowing bottom-hole pressure.
   *
   * @return bottom-hole pressure at the well datum in bara, NaN for a well that does not flow
   */
  public double getBottomHolePressure() {
    return bottomHolePressure;
  }

  /**
   * Gets the solved wellhead pressure upstream of the choke.
   *
   * @return wellhead pressure in bara
   */
  public double getWellheadPressure() {
    return wellheadPressure;
  }

  /**
   * Gets the node pressure used in the last solve.
   *
   * @return node pressure in bara
   */
  public double getNodePressure() {
    return nodePressure;
  }

  /**
   * Gets what limits the well at the current solution.
   *
   * @return control mode
   */
  public ControlMode getControlMode() {
    return controlMode;
  }

  /**
   * Number of tubing lift evaluations made by this well so far.
   *
   * @return evaluation count
   */
  public long getLiftEvaluations() {
    return liftEvaluations;
  }
}
