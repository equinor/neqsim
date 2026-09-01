package neqsim.process.equipment.energy;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.EnergyBus;
import neqsim.process.equipment.stream.EnergyNetworkReport;
import neqsim.process.equipment.stream.EnergyPort;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Iterates a complete {@link ProcessSystem} until process states and connected energy networks converge together.
 *
 * <p>
 * A graph-ordered process run evaluates energy producers, an {@link EnergyNetworkSolver}, and energy consumers in
 * causal order. Equipment downstream of the network solver can, however, publish a revised request for the next run.
 * This class performs that outer fixed-point iteration and checks both stream-state changes and energy-network changes.
 * </p>
 *
 * <p>
 * Under-relaxation is applied to {@link EnergyPortMode#SPECIFICATION} requests between process runs. It damps power
 * feedback without overwriting calculated process stream states or calculated generation.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class CoupledProcessEnergySolver implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final ProcessSystem process;
  private final List<EnergyBus> explicitlyConfiguredBuses = new ArrayList<EnergyBus>();
  private int maximumIterations = 50;
  private int minimumIterations = 2;
  private double processTolerance = 1.0e-6;
  private double powerTolerance = 1.0;
  private double relaxationFactor = 0.5;

  /**
   * Creates a coupled solver for one process system.
   *
   * @param process process containing energy-network solvers and process equipment
   */
  public CoupledProcessEnergySolver(ProcessSystem process) {
    if (process == null) {
      throw new IllegalArgumentException("Process system is required");
    }
    this.process = process;
  }

  /**
   * Gets the process system.
   *
   * @return configured process
   */
  public ProcessSystem getProcess() {
    return process;
  }

  /**
   * Adds an energy bus that is not owned by an {@link EnergyNetworkSolver} in the process.
   *
   * <p>
   * Explicit buses are solved after each complete process run when their allocation is stale. Normally buses should be
   * attached to an {@code EnergyNetworkSolver} so graph scheduling places the balance between producers and consumers.
   * </p>
   *
   * @param energyBus additional bus to include in convergence checks
   */
  public void addEnergyBus(EnergyBus energyBus) {
    if (energyBus == null) {
      throw new IllegalArgumentException("Energy bus cannot be null");
    }
    for (EnergyBus existing : explicitlyConfiguredBuses) {
      if (existing == energyBus) {
        return;
      }
    }
    explicitlyConfiguredBuses.add(energyBus);
  }

  /**
   * Gets all discovered and explicitly configured buses.
   *
   * @return immutable identity-deduplicated bus list
   */
  public List<EnergyBus> getEnergyBuses() {
    return Collections.unmodifiableList(collectEnergyBuses());
  }

  /**
   * Sets maximum coupled iterations.
   *
   * @param maximumIterations positive iteration limit
   */
  public void setMaximumIterations(int maximumIterations) {
    if (maximumIterations <= 0) {
      throw new IllegalArgumentException("Maximum iterations must be greater than zero");
    }
    this.maximumIterations = maximumIterations;
  }

  /**
   * Gets maximum coupled iterations.
   *
   * @return iteration limit
   */
  public int getMaximumIterations() {
    return maximumIterations;
  }

  /**
   * Sets minimum completed runs before convergence can be declared.
   *
   * @param minimumIterations positive minimum iteration count
   */
  public void setMinimumIterations(int minimumIterations) {
    if (minimumIterations <= 0) {
      throw new IllegalArgumentException("Minimum iterations must be greater than zero");
    }
    this.minimumIterations = minimumIterations;
  }

  /**
   * Gets minimum completed runs before convergence can be declared.
   *
   * @return minimum iteration count
   */
  public int getMinimumIterations() {
    return minimumIterations;
  }

  /**
   * Sets maximum relative stream-state change.
   *
   * @param processTolerance positive finite relative tolerance
   */
  public void setProcessTolerance(double processTolerance) {
    if (!Double.isFinite(processTolerance) || processTolerance <= 0.0) {
      throw new IllegalArgumentException("Process tolerance must be positive and finite");
    }
    this.processTolerance = processTolerance;
  }

  /**
   * Gets maximum relative stream-state change.
   *
   * @return dimensionless tolerance
   */
  public double getProcessTolerance() {
    return processTolerance;
  }

  /**
   * Sets maximum absolute energy-network change.
   *
   * @param powerTolerance positive finite tolerance in W
   */
  public void setPowerTolerance(double powerTolerance) {
    if (!Double.isFinite(powerTolerance) || powerTolerance <= 0.0) {
      throw new IllegalArgumentException("Power tolerance must be positive and finite");
    }
    this.powerTolerance = powerTolerance;
  }

  /**
   * Gets maximum absolute energy-network change.
   *
   * @return tolerance in W
   */
  public double getPowerTolerance() {
    return powerTolerance;
  }

  /**
   * Sets specification-request under-relaxation.
   *
   * @param relaxationFactor value in (0, 1], where one disables damping
   */
  public void setRelaxationFactor(double relaxationFactor) {
    if (!Double.isFinite(relaxationFactor) || relaxationFactor <= 0.0 || relaxationFactor > 1.0) {
      throw new IllegalArgumentException("Relaxation factor must be in (0, 1]");
    }
    this.relaxationFactor = relaxationFactor;
  }

  /**
   * Gets specification-request under-relaxation.
   *
   * @return relaxation factor
   */
  public double getRelaxationFactor() {
    return relaxationFactor;
  }

  /**
   * Runs the coupled fixed-point calculation.
   *
   * @return immutable convergence result with iteration history and final network reports
   */
  public CoupledProcessEnergyResult solve() {
    if (minimumIterations > maximumIterations) {
      throw new IllegalStateException("Minimum iterations cannot exceed maximum iterations");
    }

    List<EnergyBus> energyBuses = collectEnergyBuses();
    if (energyBuses.isEmpty()) {
      throw new IllegalStateException(
          "No energy buses were found. Add an EnergyNetworkSolver to the process or call addEnergyBus(bus)");
    }

    Map<EnergyPort, Double> appliedRequests = new IdentityHashMap<EnergyPort, Double>();
    initializeAppliedRequests(energyBuses, appliedRequests);

    Map<String, Double> previousProcessState = null;
    Map<String, Double> previousEnergyState = null;
    List<CoupledProcessEnergyResult.IterationResult> history = new ArrayList<CoupledProcessEnergyResult.IterationResult>();
    double processResidual = Double.POSITIVE_INFINITY;
    double powerResidual = Double.POSITIVE_INFINITY;

    for (int iteration = 1; iteration <= maximumIterations; iteration++) {
      process.run(UUID.randomUUID());
      solveStaleBuses(energyBuses);

      Map<String, Double> processState = captureProcessState();
      Map<String, Double> energyState = captureEnergyState(energyBuses);
      if (previousProcessState != null && previousEnergyState != null) {
        processResidual = maximumRelativeChange(previousProcessState, processState);
        powerResidual = maximumAbsoluteChange(previousEnergyState, energyState);
      }

      boolean converged = iteration >= minimumIterations && processResidual <= processTolerance
          && powerResidual <= powerTolerance;
      history.add(new CoupledProcessEnergyResult.IterationResult(iteration, processResidual, powerResidual, converged));
      if (converged) {
        return createResult(true, CoupledProcessEnergyResult.TerminationReason.CONVERGED, iteration, processResidual,
            powerResidual, history, energyBuses);
      }

      previousProcessState = processState;
      previousEnergyState = energyState;
      if (iteration < maximumIterations) {
        relaxSpecificationRequests(energyBuses, appliedRequests);
      }
    }

    return createResult(false, CoupledProcessEnergyResult.TerminationReason.MAXIMUM_ITERATIONS, maximumIterations,
        processResidual, powerResidual, history, energyBuses);
  }

  /**
   * Alias for {@link #solve()}.
   *
   * @return coupled convergence result
   */
  public CoupledProcessEnergyResult run() {
    return solve();
  }

  /** Collects buses from process units and explicit configuration using identity semantics. */
  private List<EnergyBus> collectEnergyBuses() {
    Set<EnergyBus> uniqueBuses = Collections.newSetFromMap(new IdentityHashMap<EnergyBus, Boolean>());
    List<EnergyBus> result = new ArrayList<EnergyBus>();
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit instanceof EnergyNetworkSolver) {
        for (EnergyBus energyBus : ((EnergyNetworkSolver) unit).getEnergyBuses()) {
          if (uniqueBuses.add(energyBus)) {
            result.add(energyBus);
          }
        }
      }
    }
    for (EnergyBus energyBus : explicitlyConfiguredBuses) {
      if (uniqueBuses.add(energyBus)) {
        result.add(energyBus);
      }
    }
    return result;
  }

  /** Solves explicitly configured or invalidated buses after the process pass. */
  private static void solveStaleBuses(List<EnergyBus> energyBuses) {
    for (EnergyBus energyBus : energyBuses) {
      if (!energyBus.hasSolution()) {
        energyBus.solveBalance();
      }
    }
  }

  /** Stores the request actually applied before the first coupled iteration. */
  private static void initializeAppliedRequests(List<EnergyBus> energyBuses, Map<EnergyPort, Double> appliedRequests) {
    for (EnergyBus energyBus : energyBuses) {
      for (EnergyPort port : energyBus.getRegisteredPorts().values()) {
        if (port.getMode() == EnergyPortMode.SPECIFICATION) {
          appliedRequests.put(port, port.getRequestedPower());
        }
      }
    }
  }

  /** Applies fixed-point under-relaxation to requests published for the next process pass. */
  private void relaxSpecificationRequests(List<EnergyBus> energyBuses, Map<EnergyPort, Double> appliedRequests) {
    for (EnergyBus energyBus : energyBuses) {
      for (EnergyPort port : energyBus.getRegisteredPorts().values()) {
        if (port.getMode() != EnergyPortMode.SPECIFICATION) {
          continue;
        }
        double rawRequest = port.getRequestedPower();
        Double previousApplied = appliedRequests.get(port);
        double prior = previousApplied == null ? rawRequest : previousApplied.doubleValue();
        double relaxedRequest = prior + relaxationFactor * (rawRequest - prior);
        if (Double.doubleToLongBits(relaxedRequest) != Double.doubleToLongBits(prior)) {
          port.setRequestedPower(relaxedRequest);
        }
        appliedRequests.put(port, relaxedRequest);
      }
    }
  }

  /** Captures all distinct inlet, outlet, and standalone streams in deterministic discovery order. */
  private Map<String, Double> captureProcessState() {
    Set<StreamInterface> uniqueStreams = Collections.newSetFromMap(new IdentityHashMap<StreamInterface, Boolean>());
    List<StreamInterface> streams = new ArrayList<StreamInterface>();
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit instanceof StreamInterface && uniqueStreams.add((StreamInterface) unit)) {
        streams.add((StreamInterface) unit);
      }
      addUniqueStreams(unit.getInletStreams(), uniqueStreams, streams);
      addUniqueStreams(unit.getOutletStreams(), uniqueStreams, streams);
    }

    Map<String, Double> state = new LinkedHashMap<String, Double>();
    for (int index = 0; index < streams.size(); index++) {
      StreamInterface stream = streams.get(index);
      String name = stream.getName() == null ? "stream" : stream.getName();
      String prefix = index + ":" + name + ":";
      putFinite(state, prefix + "pressurePa", stream.getPressure("Pa"));
      putFinite(state, prefix + "temperatureK", stream.getTemperature("K"));
      putFinite(state, prefix + "massFlowKgPerSec", stream.getFlowRate("kg/sec"));
    }
    return state;
  }

  /** Adds non-null streams once using identity semantics. */
  private static void addUniqueStreams(List<StreamInterface> candidates, Set<StreamInterface> uniqueStreams,
      List<StreamInterface> streams) {
    for (StreamInterface candidate : candidates) {
      if (candidate != null && uniqueStreams.add(candidate)) {
        streams.add(candidate);
      }
    }
  }

  /** Captures allocations, requests, contributions, limits, and report totals for every energy bus. */
  private static Map<String, Double> captureEnergyState(List<EnergyBus> energyBuses) {
    Map<String, Double> state = new LinkedHashMap<String, Double>();
    for (int busIndex = 0; busIndex < energyBuses.size(); busIndex++) {
      EnergyBus energyBus = energyBuses.get(busIndex);
      String busName = energyBus.getName() == null ? "bus" : energyBus.getName();
      String prefix = busIndex + ":" + busName + ":";
      putFinite(state, prefix + "residualW", energyBus.getDuty());
      for (Map.Entry<String, EnergyPort> entry : energyBus.getRegisteredPorts().entrySet()) {
        EnergyPort port = entry.getValue();
        String participantPrefix = prefix + entry.getKey() + ":";
        putFinite(state, participantPrefix + "contributionW", energyBus.getContribution(entry.getKey()));
        putFinite(state, participantPrefix + "allocationW", energyBus.getAllocation(entry.getKey()));
        putFinite(state, participantPrefix + "requestW", port.getRequestedPower());
        putFinite(state, participantPrefix + "balanceGenerationLimitW", port.getMaximumBalanceGeneration());
        putFinite(state, participantPrefix + "balanceConsumptionLimitW", port.getMaximumBalanceConsumption());
      }
      EnergyNetworkReport report = energyBus.getLastReport();
      if (report != null) {
        putFinite(state, prefix + "offeredSupplyW", report.getOfferedSupply());
        putFinite(state, prefix + "acceptedSupplyW", report.getAcceptedSupply());
        putFinite(state, prefix + "requestedDemandW", report.getRequestedDemand());
        putFinite(state, prefix + "servedDemandW", report.getServedDemand());
        putFinite(state, prefix + "unmetDemandW", report.getUnmetDemand());
        putFinite(state, prefix + "curtailedSupplyW", report.getCurtailedSupply());
        putFinite(state, prefix + "balancingGenerationW", report.getBalancingGeneration());
        putFinite(state, prefix + "balancingConsumptionW", report.getBalancingConsumption());
        putFinite(state, prefix + "conversionLossW", report.getConversionLoss());
        putFinite(state, prefix + "fuelEnergyRateW", report.getFuelEnergyRate());
      }
    }
    return state;
  }

  /** Adds a finite value to a state vector. */
  private static void putFinite(Map<String, Double> state, String key, double value) {
    if (Double.isFinite(value)) {
      state.put(key, value);
    }
  }

  /** Calculates the largest relative state change across the union of state keys. */
  private static double maximumRelativeChange(Map<String, Double> previous, Map<String, Double> current) {
    double maximum = 0.0;
    for (String key : unionKeys(previous, current)) {
      double previousValue = valueOrZero(previous, key);
      double currentValue = valueOrZero(current, key);
      double scale = Math.max(1.0e-12, Math.max(Math.abs(previousValue), Math.abs(currentValue)));
      maximum = Math.max(maximum, Math.abs(currentValue - previousValue) / scale);
    }
    return maximum;
  }

  /** Calculates the largest absolute change across the union of energy-state keys. */
  private static double maximumAbsoluteChange(Map<String, Double> previous, Map<String, Double> current) {
    double maximum = 0.0;
    for (String key : unionKeys(previous, current)) {
      maximum = Math.max(maximum, Math.abs(valueOrZero(current, key) - valueOrZero(previous, key)));
    }
    return maximum;
  }

  /** Creates a deterministic union of two state-vector key sets. */
  private static Set<String> unionKeys(Map<String, Double> first, Map<String, Double> second) {
    Set<String> keys = new LinkedHashSet<String>();
    keys.addAll(first.keySet());
    keys.addAll(second.keySet());
    return keys;
  }

  /** Gets a state value, treating a missing key as zero. */
  private static double valueOrZero(Map<String, Double> state, String key) {
    Double value = state.get(key);
    return value == null ? 0.0 : value.doubleValue();
  }

  /** Creates a final immutable result and snapshots the latest bus reports. */
  private static CoupledProcessEnergyResult createResult(boolean converged,
      CoupledProcessEnergyResult.TerminationReason reason, int iterations, double processResidual, double powerResidual,
      List<CoupledProcessEnergyResult.IterationResult> history, List<EnergyBus> energyBuses) {
    List<EnergyNetworkReport> reports = new ArrayList<EnergyNetworkReport>();
    for (EnergyBus energyBus : energyBuses) {
      if (energyBus.getLastReport() != null) {
        reports.add(energyBus.getLastReport());
      }
    }
    return new CoupledProcessEnergyResult(converged, reason, iterations, processResidual, powerResidual, history,
        reports);
  }
}
