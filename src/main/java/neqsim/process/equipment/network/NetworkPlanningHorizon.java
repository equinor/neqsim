package neqsim.process.equipment.network;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.thermo.system.SystemInterface;

/**
 * Deterministic discrete-time gas-network planning horizon with EOS linepack.
 *
 * <p>
 * The v1 formulation performs period-by-period hydraulic screening and exact mass/component inventory accounting. It is
 * suitable for nominations, packing/drafting, outages, ramps, and rolling-horizon warm starts. A high-fidelity
 * transient pipeline model remains the recommended validation step for fast dynamics.
 * </p>
 */
public class NetworkPlanningHorizon implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final LoopedPipeNetwork network;
  private final List<NetworkPeriod> periods = new ArrayList<NetworkPeriod>();
  private final Map<String, NetworkNomination> nominations = new LinkedHashMap<String, NetworkNomination>();
  private final Map<String, NetworkAvailabilitySchedule> availabilitySchedules = new LinkedHashMap<String, NetworkAvailabilitySchedule>();
  private final Map<String, FlowSchedule> flowSchedules = new LinkedHashMap<String, FlowSchedule>();
  private final Map<String, double[]> fuelSchedulesKgS = new LinkedHashMap<String, double[]>();
  private final Map<String, double[]> lossSchedulesKgS = new LinkedHashMap<String, double[]>();
  private final Map<String, GasLinepackState> initialLinepack = new LinkedHashMap<String, GasLinepackState>();
  private final Map<String, double[]> linepackBoundsKg = new LinkedHashMap<String, double[]>();
  private final Map<String, Double> terminalTargetsKg = new LinkedHashMap<String, Double>();
  private final Map<String, Double> sourceRampLimitsKgHr = new LinkedHashMap<String, Double>();

  /**
   * Create a horizon around a network.
   *
   * @param network network
   */
  public NetworkPlanningHorizon(LoopedPipeNetwork network) {
    if (network == null) {
      throw new IllegalArgumentException("Network cannot be null");
    }
    this.network = network;
  }

  /**
   * Add consecutive hourly periods.
   *
   * @param startIso ISO-8601 instant
   * @param count number of periods
   */
  public void addHourlyPeriods(String startIso, int count) {
    if (count < 1) {
      throw new IllegalArgumentException("Period count must be positive");
    }
    Instant start = Instant.parse(startIso);
    for (int index = 0; index < count; index++) {
      periods.add(new NetworkPeriod(periods.size(), start.plusSeconds(index * 3600L).toString(), 3600.0));
    }
  }

  /**
   * Add one explicit period.
   *
   * @param startIso ISO-8601 start
   * @param durationSeconds duration
   */
  public void addPeriod(String startIso, double durationSeconds) {
    periods.add(new NetworkPeriod(periods.size(), startIso, durationSeconds));
  }

  /** @return immutable period list */
  public List<NetworkPeriod> getPeriods() {
    return Collections.unmodifiableList(periods);
  }

  /**
   * Set explicit EOS-based initial linepack from the current solved state.
   */
  public void setInitialLinepackFromSolvedState() {
    network.run();
    initialLinepack.clear();
    for (String edgeName : network.getPipeNames()) {
      LoopedPipeNetwork.NetworkPipe edge = network.getPipe(edgeName);
      if ((edge.getElementType() == LoopedPipeNetwork.NetworkElementType.PIPE
          || edge.getElementType() == LoopedPipeNetwork.NetworkElementType.MULTIPHASE_PIPE) && edge.getLength() > 0.0
          && edge.getDiameter() > 0.0) {
        initialLinepack.put(edgeName, GasLinepackState.fromSolvedState(network, edgeName));
      }
    }
  }

  /**
   * Override initial linepack for an edge.
   *
   * @param edgeName edge
   * @param state initial state
   */
  public void setInitialLinepack(String edgeName, GasLinepackState state) {
    initialLinepack.put(edgeName, state);
  }

  /**
   * Add a point nomination with inferred basis.
   *
   * @param pointName source or sink node
   * @param values period series
   * @param unit rate unit
   */
  public void addNomination(String pointName, double[] values, String unit) {
    addNomination(pointName, values, unit, inferRateBasis(unit), 0.0);
  }

  /**
   * Add a point nomination.
   *
   * @param pointName source or sink node
   * @param values period series
   * @param unit rate unit
   * @param basis explicit rate basis
   * @param toleranceFraction allowed deviation
   */
  public void addNomination(String pointName, double[] values, String unit, NetworkDecisionVariable.RateBasis basis,
      double toleranceFraction) {
    nominations.put(pointName, new NetworkNomination(pointName, values, unit, basis, toleranceFraction));
  }

  /**
   * Add explicit inlet/outlet mass-rate schedules for a pipe.
   *
   * @param edgeName edge
   * @param inletValues inlet values
   * @param outletValues outlet values
   * @param unit kg/s or kg/hr
   */
  public void addPipeFlowSchedule(String edgeName, double[] inletValues, double[] outletValues, String unit) {
    flowSchedules.put(edgeName,
        new FlowSchedule(toKgPerSecondSeries(inletValues, unit), toKgPerSecondSeries(outletValues, unit)));
  }

  /**
   * Set period fuel withdrawal for a pipe/station.
   *
   * @param edgeName edge
   * @param values fuel values
   * @param unit kg/s or kg/hr
   */
  public void setFuelSchedule(String edgeName, double[] values, String unit) {
    fuelSchedulesKgS.put(edgeName, toKgPerSecondSeries(values, unit));
  }

  /**
   * Set other transported-inventory losses.
   *
   * @param edgeName edge
   * @param values loss values
   * @param unit kg/s or kg/hr
   */
  public void setLossSchedule(String edgeName, double[] values, String unit) {
    lossSchedulesKgS.put(edgeName, toKgPerSecondSeries(values, unit));
  }

  /**
   * Derate or outage an edge over a period range.
   *
   * @param edgeName edge
   * @param fromPeriod inclusive start
   * @param toPeriod exclusive end
   * @param availability availability fraction
   */
  public void derateElement(String edgeName, int fromPeriod, int toPeriod, double availability) {
    NetworkAvailabilitySchedule schedule = availabilitySchedules.get(edgeName);
    if (schedule == null) {
      schedule = new NetworkAvailabilitySchedule(edgeName, periods.size());
      availabilitySchedules.put(edgeName, schedule);
    }
    schedule.derate(fromPeriod, toPeriod, availability);
  }

  /**
   * Set linepack mass bounds.
   *
   * @param edgeName edge
   * @param minimumKg minimum
   * @param maximumKg maximum
   */
  public void setLinepackBounds(String edgeName, double minimumKg, double maximumKg) {
    linepackBoundsKg.put(edgeName, new double[] { minimumKg, maximumKg });
  }

  /**
   * Set terminal linepack target.
   *
   * @param edgeName edge
   * @param targetKg target mass
   */
  public void setTerminalLinepackTarget(String edgeName, double targetKg) {
    terminalTargetsKg.put(edgeName, targetKg);
  }

  /**
   * Set maximum source period-to-period ramp.
   *
   * @param sourceName source node
   * @param maximumRampKgHr maximum absolute rate change in kg/hr
   */
  public void setSourceRampLimit(String sourceName, double maximumRampKgHr) {
    sourceRampLimitsKgHr.put(sourceName, maximumRampKgHr);
  }

  /**
   * Solve the deterministic period schedule.
   *
   * @return structured schedule result
   */
  public NetworkScheduleResult optimize() {
    validateSeriesLengths();
    if (periods.isEmpty()) {
      throw new IllegalStateException("Planning horizon has no periods");
    }
    if (initialLinepack.isEmpty()) {
      setInitialLinepackFromSolvedState();
    }

    Map<String, Double> originalAvailability = new LinkedHashMap<String, Double>();
    for (String edgeName : network.getPipeNames()) {
      originalAvailability.put(edgeName, network.getPipe(edgeName).getAvailability());
    }
    Map<String, Double> originalDemand = new LinkedHashMap<String, Double>();
    for (String nodeName : network.getNodeNames()) {
      originalDemand.put(nodeName, network.getNode(nodeName).getDemand());
    }

    Map<String, GasLinepackState> current = new LinkedHashMap<String, GasLinepackState>(initialLinepack);
    List<NetworkSchedulePeriodResult> periodResults = new ArrayList<NetworkSchedulePeriodResult>();
    Map<String, Double> activeConstraints = new LinkedHashMap<String, Double>();
    boolean scheduleFeasible = true;
    double objectiveValue = 0.0;
    Map<String, Double> previousSourceRates = new LinkedHashMap<String, Double>();

    try {
      for (NetworkPeriod period : periods) {
        int periodIndex = period.getIndex();
        applyAvailability(periodIndex);
        applyNominations(periodIndex);
        network.run();
        Map<String, Double> residuals = new LinkedHashMap<String, Double>();
        boolean periodFeasible = network.isConverged();
        if (!network.isConverged()) {
          residuals.put("solver.convergence", 1.0);
        }

        evaluateNominationResiduals(periodIndex, residuals);
        evaluateRampResiduals(periodIndex, previousSourceRates, residuals);

        Map<String, GasLinepackState> opening = new LinkedHashMap<String, GasLinepackState>(current);
        Map<String, GasLinepackState> closing = new LinkedHashMap<String, GasLinepackState>();
        Map<String, double[]> periodFlows = new LinkedHashMap<String, double[]>();

        for (Map.Entry<String, GasLinepackState> entry : current.entrySet()) {
          String edgeName = entry.getKey();
          LoopedPipeNetwork.NetworkPipe edge = network.getPipe(edgeName);
          FlowSchedule flowSchedule = flowSchedules.get(edgeName);
          double hydraulicFlow = Math.abs(edge.getFlowRate());
          double inletKgS = flowSchedule == null ? hydraulicFlow : flowSchedule.inletKgS[periodIndex];
          double outletKgS = flowSchedule == null ? hydraulicFlow : flowSchedule.outletKgS[periodIndex];
          double fuelKgS = seriesValue(fuelSchedulesKgS.get(edgeName), periodIndex);
          double lossKgS = seriesValue(lossSchedulesKgS.get(edgeName), periodIndex);
          SystemInterface inletFluid = edge.getInletFluid();
          if (inletFluid == null) {
            boolean forward = edge.getFlowRate() >= 0.0;
            inletFluid = network.getNodeFluid(forward ? edge.getFromNode() : edge.getToNode());
          }
          GasLinepackState closingState = entry.getValue().advance(period.getDurationSeconds(), inletKgS, outletKgS,
              fuelKgS, lossKgS, inletFluid);
          closing.put(edgeName, closingState);
          periodFlows.put(edgeName, new double[] { inletKgS, outletKgS, fuelKgS, lossKgS });

          double massResidual = Math.abs(closingState.getMassBalanceResidualKg());
          if (massResidual > 1.0e-6) {
            residuals.put("linepack." + edgeName + ".massBalance", massResidual);
          }
          double componentResidual = closingState.getMaxComponentBalanceResidualMol();
          if (componentResidual > 1.0e-6) {
            residuals.put("linepack." + edgeName + ".componentBalance", componentResidual);
          }
          double[] bounds = linepackBoundsKg.get(edgeName);
          if (bounds != null) {
            double boundResidual = Math
                .max(Math.max(bounds[0] - closingState.getMassKg(), closingState.getMassKg() - bounds[1]), 0.0);
            if (boundResidual > 0.0) {
              residuals.put("linepack." + edgeName + ".bounds", boundResidual);
            }
          }
        }
        current = closing;

        Map<String, NetworkQualityComplianceReport> qualityReports = new LinkedHashMap<String, NetworkQualityComplianceReport>(
            network.evaluateQualityProfiles());
        for (Map.Entry<String, NetworkQualityComplianceReport> qualityEntry : qualityReports.entrySet()) {
          if (!qualityEntry.getValue().isCompliant()) {
            residuals.put("quality." + qualityEntry.getKey(), 1.0);
          }
        }

        for (Map.Entry<String, Double> residual : residuals.entrySet()) {
          if (residual.getValue() > 0.0) {
            periodFeasible = false;
            activeConstraints.put("period." + periodIndex + "." + residual.getKey(), residual.getValue());
            objectiveValue -= residual.getValue();
          }
        }
        scheduleFeasible &= periodFeasible;
        periodResults.add(new NetworkSchedulePeriodResult(period, periodFeasible, opening, closing, periodFlows,
            residuals, qualityReports));
      }

      for (Map.Entry<String, Double> target : terminalTargetsKg.entrySet()) {
        GasLinepackState terminal = current.get(target.getKey());
        double residual = terminal == null ? Double.POSITIVE_INFINITY
            : Math.abs(terminal.getMassKg() - target.getValue());
        if (!(residual <= 1.0e-6)) {
          scheduleFeasible = false;
          activeConstraints.put("terminalLinepack." + target.getKey(), residual);
          objectiveValue -= residual;
        }
      }
    } finally {
      for (Map.Entry<String, Double> entry : originalAvailability.entrySet()) {
        network.getPipe(entry.getKey()).setAvailability(entry.getValue());
      }
      for (Map.Entry<String, Double> entry : originalDemand.entrySet()) {
        network.getNode(entry.getKey()).setDemand(entry.getValue());
      }
      try {
        network.run();
      } catch (Exception ex) {
        scheduleFeasible = false;
        activeConstraints.put("stateRestoration", 1.0);
      }
    }

    return new NetworkScheduleResult(scheduleFeasible, objectiveValue, periodResults, initialLinepack, current,
        activeConstraints, scheduleFeasible ? "Schedule feasible" : "Schedule contains infeasible periods or targets");
  }

  private void applyAvailability(int periodIndex) {
    for (String edgeName : network.getPipeNames()) {
      NetworkAvailabilitySchedule schedule = availabilitySchedules.get(edgeName);
      if (schedule != null) {
        network.getPipe(edgeName).setAvailability(schedule.getAvailability(periodIndex));
      }
    }
  }

  private void applyNominations(int periodIndex) {
    for (NetworkNomination nomination : nominations.values()) {
      LoopedPipeNetwork.NetworkNode node = network.getNode(nomination.getPointName());
      double massRate = nominationToKgS(nomination, periodIndex);
      if (node.getType() == LoopedPipeNetwork.NodeType.SOURCE) {
        node.setDemand(-massRate);
      } else if (node.getType() == LoopedPipeNetwork.NodeType.SINK) {
        node.setDemand(massRate);
      } else {
        throw new IllegalArgumentException("Nominations require a source or sink node: " + nomination.getPointName());
      }
    }
  }

  private void evaluateNominationResiduals(int periodIndex, Map<String, Double> residuals) {
    for (NetworkNomination nomination : nominations.values()) {
      double nominated = nominationToKgS(nomination, periodIndex);
      double actual = calculatePhysicalPointFlow(nomination.getPointName());
      double tolerance = nomination.getToleranceFraction() * Math.max(nominated, 1.0e-12);
      double residual = Math.max(0.0, Math.abs(actual - nominated) - tolerance);
      if (residual > 0.0) {
        residuals.put("nomination." + nomination.getPointName(), residual);
      }
    }
  }

  private void evaluateRampResiduals(int periodIndex, Map<String, Double> previousSourceRates,
      Map<String, Double> residuals) {
    for (Map.Entry<String, Double> limit : sourceRampLimitsKgHr.entrySet()) {
      NetworkNomination nomination = nominations.get(limit.getKey());
      if (nomination == null) {
        continue;
      }
      double currentRateKgHr = nominationToKgS(nomination, periodIndex) * 3600.0;
      Double previous = previousSourceRates.get(limit.getKey());
      if (previous != null) {
        double residual = Math.max(0.0, Math.abs(currentRateKgHr - previous) - limit.getValue());
        if (residual > 0.0) {
          residuals.put("ramp." + limit.getKey(), residual);
        }
      }
      previousSourceRates.put(limit.getKey(), currentRateKgHr);
    }
  }

  private double calculatePhysicalPointFlow(String nodeName) {
    LoopedPipeNetwork.NetworkNode node = network.getNode(nodeName);
    double netOutflow = 0.0;
    for (String edgeName : network.getPipeNames()) {
      LoopedPipeNetwork.NetworkPipe edge = network.getPipe(edgeName);
      if (edge.getFromNode().equals(nodeName)) {
        netOutflow += edge.getFlowRate();
      } else if (edge.getToNode().equals(nodeName)) {
        netOutflow -= edge.getFlowRate();
      }
    }
    return node.getType() == LoopedPipeNetwork.NodeType.SOURCE ? Math.max(0.0, netOutflow) : Math.max(0.0, -netOutflow);
  }

  private double nominationToKgS(NetworkNomination nomination, int periodIndex) {
    double value = nomination.getValue(periodIndex);
    String unit = nomination.getUnit();
    NetworkDecisionVariable.RateBasis basis = nomination.getBasis();
    if (basis == NetworkDecisionVariable.RateBasis.MASS) {
      return toKgPerSecond(value, unit);
    }
    SystemInterface fluid = network.getNodeFluid(nomination.getPointName());
    if (basis == NetworkDecisionVariable.RateBasis.MOLAR) {
      double molarRate;
      if ("mol/s".equalsIgnoreCase(unit)) {
        molarRate = value;
      } else if ("kmol/hr".equalsIgnoreCase(unit)) {
        molarRate = value * 1000.0 / 3600.0;
      } else {
        throw new IllegalArgumentException("Unsupported molar nomination unit: " + unit);
      }
      return molarRate * fluid.getMolarMass();
    }
    if (basis == NetworkDecisionVariable.RateBasis.STANDARD_VOLUME) {
      double standardVolumeM3S;
      if ("MSm3/day".equals(unit)) {
        standardVolumeM3S = value * 1.0e6 / 86400.0;
      } else if ("Sm3/day".equals(unit)) {
        standardVolumeM3S = value / 86400.0;
      } else if ("Sm3/hr".equals(unit)) {
        standardVolumeM3S = value / 3600.0;
      } else {
        throw new IllegalArgumentException("Unsupported standard-volume nomination unit: " + unit);
      }
      double molarRate = 1.01325e5 * standardVolumeM3S / (8.314462618 * 288.15);
      return molarRate * fluid.getMolarMass();
    }
    throw new IllegalArgumentException(
        "Nomination basis is explicit but conversion is not " + "available for " + basis);
  }

  private void validateSeriesLengths() {
    int periodCount = periods.size();
    for (NetworkNomination nomination : nominations.values()) {
      if (nomination.size() != periodCount) {
        throw new IllegalArgumentException(
            "Nomination length for " + nomination.getPointName() + " does not match period count");
      }
    }
    for (FlowSchedule schedule : flowSchedules.values()) {
      if (schedule.inletKgS.length != periodCount || schedule.outletKgS.length != periodCount) {
        throw new IllegalArgumentException("Pipe flow schedule length does not match period count");
      }
    }
    validateArrayMap(fuelSchedulesKgS, periodCount, "fuel");
    validateArrayMap(lossSchedulesKgS, periodCount, "loss");
  }

  private void validateArrayMap(Map<String, double[]> schedules, int periodCount, String name) {
    for (Map.Entry<String, double[]> entry : schedules.entrySet()) {
      if (entry.getValue().length != periodCount) {
        throw new IllegalArgumentException(
            name + " schedule length for " + entry.getKey() + " does not match period count");
      }
    }
  }

  private static NetworkDecisionVariable.RateBasis inferRateBasis(String unit) {
    if (unit != null && (unit.contains("Sm3") || unit.contains("scf"))) {
      return NetworkDecisionVariable.RateBasis.STANDARD_VOLUME;
    }
    if (unit != null && unit.contains("mol")) {
      return NetworkDecisionVariable.RateBasis.MOLAR;
    }
    return NetworkDecisionVariable.RateBasis.MASS;
  }

  private static double[] toKgPerSecondSeries(double[] values, String unit) {
    double[] converted = values.clone();
    for (int index = 0; index < converted.length; index++) {
      converted[index] = toKgPerSecond(converted[index], unit);
    }
    return converted;
  }

  private static double toKgPerSecond(double value, String unit) {
    if ("kg/s".equalsIgnoreCase(unit) || "kg/sec".equalsIgnoreCase(unit)) {
      return value;
    }
    if ("kg/hr".equalsIgnoreCase(unit)) {
      return value / 3600.0;
    }
    throw new IllegalArgumentException("Unsupported mass-rate unit: " + unit);
  }

  private static double seriesValue(double[] values, int index) {
    return values == null ? 0.0 : values[index];
  }

  private static final class FlowSchedule implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double[] inletKgS;
    private final double[] outletKgS;

    private FlowSchedule(double[] inletKgS, double[] outletKgS) {
      this.inletKgS = inletKgS;
      this.outletKgS = outletKgS;
    }
  }
}
