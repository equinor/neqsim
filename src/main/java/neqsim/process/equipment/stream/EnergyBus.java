package neqsim.process.equipment.stream;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.util.unit.PowerUnit;

/**
 * Multi-party energy connection with deterministic allocation and balancing.
 *
 * <p>
 * Positive contributions inject power and negative contributions withdraw power. Calculated ports publish fixed offers
 * or demands, specification ports publish dispatchable requests, and balance ports absorb a surplus or cover a shortage
 * within configured limits. Demand is always served by priority, proportionally within equal-priority groups.
 * Generation defaults to the same policy and can optionally use minimum-cost or minimum-emissions merit order.
 * </p>
 *
 * @author NeqSim
 * @version 3.0
 */
public class EnergyBus extends EnergyStream {
  private static final long serialVersionUID = 1000L;

  private Map<String, Double> contributions = new LinkedHashMap<String, Double>();
  private Map<String, Double> allocations = new LinkedHashMap<String, Double>();
  private Map<String, EnergyPort> registeredPorts = new LinkedHashMap<String, EnergyPort>();
  private Map<String, Double> realizedBalancePowers = new LinkedHashMap<String, Double>();
  private boolean solutionValid = false;
  private EnergyNetworkReport lastReport = null;
  private EnergyDispatchStrategy dispatchStrategy = EnergyDispatchStrategy.PRIORITY_PROPORTIONAL;

  /** Creates an unnamed bus with an unspecified energy domain. */
  public EnergyBus() {
    super();
  }

  /**
   * Creates a named bus with an unspecified energy domain.
   *
   * @param name bus name
   */
  public EnergyBus(String name) {
    super(name);
  }

  /**
   * Creates a named, typed energy bus.
   *
   * @param name bus name
   * @param energyType physical energy domain
   */
  public EnergyBus(String name, EnergyType energyType) {
    super(name, energyType);
  }

  /**
   * Restores collection and strategy defaults for energy buses serialized before allocation support was introduced.
   *
   * @param input serialized object input
   * @throws IOException if the stream cannot be read
   * @throws ClassNotFoundException if a serialized class cannot be resolved
   */
  private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
    input.defaultReadObject();
    if (contributions == null) {
      contributions = new LinkedHashMap<String, Double>();
    }
    if (allocations == null) {
      allocations = new LinkedHashMap<String, Double>();
    }
    if (registeredPorts == null) {
      registeredPorts = new LinkedHashMap<String, EnergyPort>();
    }
    if (realizedBalancePowers == null) {
      realizedBalancePowers = new LinkedHashMap<String, Double>();
    }
    if (dispatchStrategy == null) {
      dispatchStrategy = EnergyDispatchStrategy.PRIORITY_PROPORTIONAL;
    }
  }

  /** {@inheritDoc} */
  @Override
  public EnergyBus clone() {
    EnergyBus clonedBus = (EnergyBus) super.clone();
    clonedBus.contributions = new LinkedHashMap<String, Double>(contributions);
    clonedBus.allocations = new LinkedHashMap<String, Double>();
    clonedBus.registeredPorts = new LinkedHashMap<String, EnergyPort>();
    clonedBus.realizedBalancePowers = new LinkedHashMap<String, Double>();
    clonedBus.solutionValid = false;
    clonedBus.lastReport = null;
    return clonedBus;
  }

  /**
   * Registers a connected port using its stable participant identifier.
   *
   * @param port connected port
   */
  void registerPort(EnergyPort port) {
    EnergyPort previous = registeredPorts.get(port.getParticipantId());
    if (previous != null && previous != port) {
      do {
        port.regenerateParticipantId();
      } while (registeredPorts.containsKey(port.getParticipantId()));
    }
    registeredPorts.put(port.getParticipantId(), port);
    invalidateSolution();
  }

  /**
   * Unregisters a disconnected port and removes its network state.
   *
   * @param port disconnected port
   */
  void unregisterPort(EnergyPort port) {
    registeredPorts.remove(port.getParticipantId());
    contributions.remove(port.getParticipantId());
    allocations.remove(port.getParticipantId());
    realizedBalancePowers.remove(port.getParticipantId());
    invalidateSolution();
  }

  /**
   * Gets registered ports keyed by persistent identifier.
   *
   * @return immutable registered-port map
   */
  public Map<String, EnergyPort> getRegisteredPorts() {
    return Collections.unmodifiableMap(registeredPorts);
  }

  /**
   * Gets the producer dispatch strategy.
   *
   * @return current strategy
   */
  public EnergyDispatchStrategy getDispatchStrategy() {
    return dispatchStrategy;
  }

  /**
   * Sets the producer dispatch strategy.
   *
   * <p>
   * The strategy applies separately to normal producers and balancing generators. Demand continues to use its existing
   * priority/proportional allocation, and balancing generation remains reserve that is considered only after normal
   * generation.
   * </p>
   *
   * @param dispatchStrategy producer dispatch strategy
   */
  public void setDispatchStrategy(EnergyDispatchStrategy dispatchStrategy) {
    if (dispatchStrategy == null) {
      throw new IllegalArgumentException("Energy dispatch strategy is required");
    }
    if (this.dispatchStrategy != dispatchStrategy) {
      this.dispatchStrategy = dispatchStrategy;
      invalidateSolution();
    }
  }

  /**
   * Sets a named power contribution in watts.
   *
   * @param participant unique participant identifier or legacy participant name
   * @param power signed power contribution in W
   */
  public void setContribution(String participant, double power) {
    validateParticipant(participant);
    if (!Double.isFinite(power)) {
      throw new IllegalArgumentException("Energy bus contribution must be finite");
    }
    contributions.put(resolveParticipantId(participant), power);
    invalidateSolution();
  }

  /**
   * Sets a named power contribution in a specified unit.
   *
   * @param participant unique participant identifier or legacy participant name
   * @param power signed power contribution
   * @param unit power unit
   */
  public void setContribution(String participant, double power, String unit) {
    setContribution(participant, new PowerUnit(power, unit).getValue("W"));
  }

  /**
   * Gets a participant contribution in watts.
   *
   * @param participant participant identifier or legacy name
   * @return signed contribution in W, or zero when absent
   */
  public double getContribution(String participant) {
    Double contribution = contributions.get(resolveParticipantId(participant));
    return contribution == null ? 0.0 : contribution.doubleValue();
  }

  /**
   * Gets a participant contribution in a requested unit.
   *
   * @param participant participant identifier or legacy name
   * @param unit requested power unit
   * @return signed contribution in the requested unit
   */
  public double getContribution(String participant, String unit) {
    return new PowerUnit(getContribution(participant), "W").getValue(unit);
  }

  /**
   * Gets a participant's most recent signed allocation.
   *
   * @param participant stable participant identifier
   * @return positive injection, negative withdrawal, or zero when not allocated
   */
  public double getAllocation(String participant) {
    Double allocation = allocations.get(resolveParticipantId(participant));
    return allocation == null ? 0.0 : allocation.doubleValue();
  }

  /**
   * Gets a participant allocation in a requested unit.
   *
   * @param participant stable participant identifier
   * @param unit requested power unit
   * @return signed allocation in the requested unit
   */
  public double getAllocation(String participant, String unit) {
    return new PowerUnit(getAllocation(participant), "W").getValue(unit);
  }

  /**
   * Removes a participant contribution.
   *
   * @param participant participant identifier or legacy name
   */
  public void removeContribution(String participant) {
    String participantId = resolveParticipantId(participant);
    contributions.remove(participantId);
    allocations.remove(participantId);
    invalidateSolution();
  }

  /** Removes all contributions and allocations while preserving the inherited external duty. */
  public void clearContributions() {
    contributions.clear();
    allocations.clear();
    realizedBalancePowers.clear();
    invalidateSolution();
  }

  /** Clears realized powers reported by balance equipment before a new transient dispatch. */
  public void clearRealizedBalancePowers() {
    if (!realizedBalancePowers.isEmpty()) {
      realizedBalancePowers.clear();
      invalidateSolution();
    }
  }

  /**
   * Clears one balance participant's previously realized power.
   *
   * @param participantId stable participant identifier
   */
  void clearRealizedBalancePower(String participantId) {
    if (realizedBalancePowers.remove(participantId) != null) {
      invalidateSolution();
    }
  }

  /**
   * Records a balance participant's physically realized power and redispatches the remaining network.
   *
   * <p>
   * This keeps transient ramp, trip, and stored-energy constraints consistent with allocations and network
   * shortfall/curtailment reporting. The realized participant is treated as fixed until it is cleared for the next
   * transient dispatch.
   * </p>
   *
   * @param port reporting balance port
   * @param actualPower positive generation or negative consumption in W
   * @return updated network report
   */
  EnergyNetworkReport reportRealizedBalancePower(EnergyPort port, double actualPower) {
    if (port.getMode() != EnergyPortMode.BALANCE || registeredPorts.get(port.getParticipantId()) != port) {
      throw new IllegalArgumentException("Realized power can only be reported by a registered balance port");
    }
    if (!Double.isFinite(actualPower)) {
      throw new IllegalArgumentException("Realized balance power must be finite");
    }
    if (port.getDirection() == EnergyPortDirection.INPUT && actualPower > 0.0) {
      throw new IllegalArgumentException("An input balance port cannot report generated power");
    }
    if (port.getDirection() == EnergyPortDirection.OUTPUT && actualPower < 0.0) {
      throw new IllegalArgumentException("An output balance port cannot report consumed power");
    }
    realizedBalancePowers.put(port.getParticipantId(), actualPower);
    invalidateSolution();
    return solveBalance();
  }

  /** {@inheritDoc} */
  @Override
  public void setDuty(double duty) {
    if (!Double.isFinite(duty)) {
      throw new IllegalArgumentException("Energy-bus duty must be finite");
    }
    super.setDuty(duty);
    invalidateSolution();
  }

  /**
   * Gets an immutable view of named contributions in watts.
   *
   * @return contributions keyed by participant
   */
  public Map<String, Double> getContributions() {
    return Collections.unmodifiableMap(contributions);
  }

  /**
   * Gets an immutable view of signed allocations in watts.
   *
   * @return allocations keyed by stable participant identifier
   */
  public Map<String, Double> getAllocations() {
    return Collections.unmodifiableMap(allocations);
  }

  /**
   * Checks whether the current offers and requests have been solved.
   *
   * @return {@code true} when allocation results are current
   */
  public boolean hasSolution() {
    return solutionValid;
  }

  /** Marks allocation results stale without removing the previous report. */
  public void invalidateSolution() {
    solutionValid = false;
  }

  /**
   * Solves generation, demand, balancing, curtailment, and shortage deterministically.
   *
   * @return auditable network report
   */
  public EnergyNetworkReport solveBalance() {
    List<DispatchEntry> producers = new ArrayList<DispatchEntry>();
    List<DispatchEntry> demands = new ArrayList<DispatchEntry>();
    List<DispatchEntry> balancingGenerators = new ArrayList<DispatchEntry>();
    List<DispatchEntry> balancingConsumers = new ArrayList<DispatchEntry>();
    List<DispatchEntry> realizedBalancingGenerators = new ArrayList<DispatchEntry>();
    List<DispatchEntry> realizedBalancingConsumers = new ArrayList<DispatchEntry>();
    double offeredBalancingGeneration = 0.0;

    double externalSupply = Math.max(0.0, super.getDuty());
    double externalDemand = Math.max(0.0, -super.getDuty());
    for (EnergyPort port : registeredPorts.values()) {
      double contribution = getContribution(port.getParticipantId());
      if (port.getMode() == EnergyPortMode.CALCULATED) {
        if (contribution > 0.0) {
          producers.add(new DispatchEntry(port, contribution));
        } else if (contribution < 0.0) {
          demands.add(new DispatchEntry(port, -contribution));
        }
      } else if (port.getMode() == EnergyPortMode.SPECIFICATION) {
        double request = boundedRequest(port);
        if (port.getDirection() == EnergyPortDirection.OUTPUT) {
          producers.add(new DispatchEntry(port, request));
        } else {
          demands.add(new DispatchEntry(port, request));
        }
      } else if (port.getMode() == EnergyPortMode.BALANCE) {
        offeredBalancingGeneration += port.getMaximumBalanceGeneration();
        Double realizedPower = realizedBalancePowers.get(port.getParticipantId());
        if (realizedPower != null) {
          if (realizedPower.doubleValue() > 0.0) {
            realizedBalancingGenerators.add(new DispatchEntry(port, realizedPower.doubleValue(), true));
          } else if (realizedPower.doubleValue() < 0.0) {
            realizedBalancingConsumers.add(new DispatchEntry(port, -realizedPower.doubleValue(), true));
          }
          continue;
        }
        if (port.getDirection() != EnergyPortDirection.INPUT && port.getMaximumBalanceGeneration() > 0.0) {
          balancingGenerators.add(new DispatchEntry(port, port.getMaximumBalanceGeneration()));
        }
        if (port.getDirection() != EnergyPortDirection.OUTPUT && port.getMaximumBalanceConsumption() > 0.0) {
          balancingConsumers.add(new DispatchEntry(port, port.getMaximumBalanceConsumption()));
        }
      }
    }

    for (Map.Entry<String, Double> entry : contributions.entrySet()) {
      if (registeredPorts.containsKey(entry.getKey())) {
        continue;
      }
      if (entry.getValue().doubleValue() > 0.0) {
        externalSupply += entry.getValue().doubleValue();
      } else {
        externalDemand -= entry.getValue().doubleValue();
      }
    }

    double normalSupply = externalSupply + sumRequested(producers);
    double requestedDemand = externalDemand + sumRequested(demands);
    double availableBalancingGeneration = sumRequested(balancingGenerators);
    double realizedBalancingGeneration = sumRequested(realizedBalancingGenerators);
    double realizedBalancingConsumption = sumRequested(realizedBalancingConsumers);
    double totalAvailableSupply = normalSupply + realizedBalancingGeneration + availableBalancingGeneration;
    double demandAllocationLimit = Math.min(requestedDemand,
        Math.max(0.0, totalAvailableSupply - realizedBalancingConsumption));

    double participantDemandLimit = Math.max(0.0, demandAllocationLimit - externalDemand);
    allocateByPriority(demands, participantDemandLimit);
    double servedParticipantDemand = sumAllocated(demands);
    double servedExternalDemand = Math.min(externalDemand, demandAllocationLimit);
    double servedDemand = servedParticipantDemand + servedExternalDemand;

    double availableBalancingConsumption = sumRequested(balancingConsumers);
    double balancingConsumptionTarget = Math.min(
        Math.max(0.0, normalSupply + realizedBalancingGeneration - servedDemand - realizedBalancingConsumption),
        availableBalancingConsumption);
    double normalGenerationTarget = Math.min(normalSupply, Math.max(0.0,
        servedDemand + realizedBalancingConsumption + balancingConsumptionTarget - realizedBalancingGeneration));
    double participantGenerationTarget = Math.max(0.0, normalGenerationTarget - externalSupply);
    allocateGeneration(producers, participantGenerationTarget);
    double acceptedParticipantSupply = sumAllocated(producers);
    double acceptedExternalSupply = Math.min(externalSupply, normalGenerationTarget);
    double acceptedNormalSupply = acceptedParticipantSupply + acceptedExternalSupply;

    double balancingGenerationTarget = Math.max(0.0, servedDemand + realizedBalancingConsumption
        + balancingConsumptionTarget - acceptedNormalSupply - realizedBalancingGeneration);
    allocateGeneration(balancingGenerators, balancingGenerationTarget);
    double balancingGeneration = realizedBalancingGeneration + sumAllocated(balancingGenerators);

    double actualSurplus = Math.max(0.0,
        acceptedNormalSupply + balancingGeneration - servedDemand - realizedBalancingConsumption);
    allocateByPriority(balancingConsumers, actualSurplus);
    double balancingConsumption = realizedBalancingConsumption + sumAllocated(balancingConsumers);

    allocations.clear();
    updatePortNetworkState(producers, true);
    updatePortNetworkState(demands, false);
    updatePortNetworkState(balancingGenerators, true);
    updatePortNetworkState(balancingConsumers, false);
    updatePortNetworkState(realizedBalancingGenerators, true);
    updatePortNetworkState(realizedBalancingConsumers, false);
    for (EnergyPort port : registeredPorts.values()) {
      if (port.getMode() != EnergyPortMode.CALCULATED) {
        contributions.put(port.getParticipantId(), getAllocation(port.getParticipantId()));
      }
    }

    double acceptedSupply = acceptedNormalSupply + balancingGeneration;
    double offeredSupply = normalSupply + offeredBalancingGeneration;
    double unmetDemand = Math.max(0.0, requestedDemand - servedDemand);
    double curtailedSupply = Math.max(0.0, normalSupply - acceptedNormalSupply);

    List<EnergyAllocation> allocationResults = createAllocationResults();
    double conversionLoss = 0.0;
    double operatingCostPerHour = 0.0;
    double co2EmissionRate = 0.0;
    for (EnergyPort port : registeredPorts.values()) {
      conversionLoss += port.getConversionLoss();
      double allocatedGeneration = Math.max(0.0, getAllocation(port.getParticipantId()));
      double allocatedMWhPerHour = allocatedGeneration / 1.0e6;
      operatingCostPerHour += allocatedMWhPerHour * port.getEnergyPricePerMWh();
      co2EmissionRate += allocatedMWhPerHour * port.getEmissionFactorKgPerMWh();
    }

    lastReport = new EnergyNetworkReport(getName(), allocationResults, offeredSupply, acceptedSupply, requestedDemand,
        servedDemand, unmetDemand, curtailedSupply, balancingGeneration, balancingConsumption, conversionLoss,
        getEnergyType() == EnergyType.CHEMICAL ? acceptedSupply : 0.0, operatingCostPerHour, co2EmissionRate);
    solutionValid = true;
    return lastReport;
  }

  /**
   * Alias for {@link #solveBalance()}.
   *
   * @return auditable network report
   */
  public EnergyNetworkReport solve() {
    return solveBalance();
  }

  /**
   * Gets the most recent network report.
   *
   * @return report, or {@code null} before the first solution
   */
  public EnergyNetworkReport getLastReport() {
    return lastReport;
  }

  /**
   * Gets the net unsatisfied bus duty in watts.
   *
   * <p>
   * A positive result is unallocated published generation. A negative value can represent unsolved demand, while solved
   * shortages are reported by {@link EnergyNetworkReport#getUnmetDemand()}. The value uses published calculated
   * contributions and solved specification/balance contributions, so it remains a useful convergence residual.
   * </p>
   *
   * @return net residual power in W
   */
  @Override
  public double getDuty() {
    double netDuty = super.getDuty();
    for (Double contribution : contributions.values()) {
      netDuty += contribution.doubleValue();
    }
    return netDuty;
  }

  /**
   * Alias for {@link #getDuty()} emphasizing the bus residual.
   *
   * @return net bus power in W
   */
  public double getNetPower() {
    return getDuty();
  }

  /**
   * Gets the net bus power while excluding one participant's contribution.
   *
   * @param participant participant identifier or legacy name to exclude
   * @return net power excluding that contribution in W
   */
  public double getNetPowerExcluding(String participant) {
    String participantId = resolveParticipantId(participant);
    double netPower = super.getDuty();
    for (Map.Entry<String, Double> entry : contributions.entrySet()) {
      if (!entry.getKey().equals(participantId)) {
        netPower += entry.getValue().doubleValue();
      }
    }
    return netPower;
  }

  /**
   * Gets net bus power excluding one participant in a requested unit.
   *
   * @param participant participant identifier or legacy name to exclude
   * @param unit requested power unit
   * @return net power excluding that contribution in the requested unit
   */
  public double getNetPowerExcluding(String participant, String unit) {
    return new PowerUnit(getNetPowerExcluding(participant), "W").getValue(unit);
  }

  /**
   * Gets net bus power in a requested unit.
   *
   * @param unit requested power unit
   * @return net bus power in the requested unit
   */
  public double getNetPower(String unit) {
    return getDuty(unit);
  }

  /**
   * Applies port minimum and maximum limits to a request.
   *
   * @param port requesting port
   * @return bounded request in W
   */
  private static double boundedRequest(EnergyPort port) {
    double request = Math.min(port.getRequestedPower(), port.getMaximumPower());
    if (request > 0.0) {
      request = Math.max(request, port.getMinimumPower());
    }
    return request;
  }

  /** Allocates producer entries using the selected strategy. */
  private void allocateGeneration(List<DispatchEntry> entries, double available) {
    if (dispatchStrategy == EnergyDispatchStrategy.PRIORITY_PROPORTIONAL) {
      allocateByPriority(entries, available);
    } else {
      allocateByMeritOrder(entries, available, dispatchStrategy);
    }
  }

  /**
   * Allocates a capacity across entries by priority and proportionally within an equal-priority group.
   *
   * @param entries dispatch entries
   * @param available available power in W
   */
  private static void allocateByPriority(List<DispatchEntry> entries, double available) {
    Collections.sort(entries, new Comparator<DispatchEntry>() {
      /** {@inheritDoc} */
      @Override
      public int compare(DispatchEntry first, DispatchEntry second) {
        int priorityComparison = Integer.compare(first.port.getPriority(), second.port.getPriority());
        if (priorityComparison != 0) {
          return priorityComparison;
        }
        return first.port.getParticipantId().compareTo(second.port.getParticipantId());
      }
    });
    double remaining = Math.max(0.0, available);
    int start = 0;
    while (start < entries.size()) {
      int end = start + 1;
      int priority = entries.get(start).port.getPriority();
      double groupRequest = entries.get(start).requested;
      while (end < entries.size() && entries.get(end).port.getPriority() == priority) {
        groupRequest += entries.get(end).requested;
        end++;
      }
      double groupAllocation = Math.min(remaining, groupRequest);
      allocateGroup(entries, start, end, groupRequest, groupAllocation);
      remaining -= groupAllocation;
      start = end;
    }
  }

  /**
   * Allocates generation by marginal cost or emissions and uses priority as a secondary operational discriminator.
   *
   * @param entries generation entries
   * @param available accepted generation target in W
   * @param strategy merit-order strategy
   */
  private static void allocateByMeritOrder(List<DispatchEntry> entries, double available,
      final EnergyDispatchStrategy strategy) {
    Collections.sort(entries, new Comparator<DispatchEntry>() {
      /** {@inheritDoc} */
      @Override
      public int compare(DispatchEntry first, DispatchEntry second) {
        int metricComparison = Double.compare(getDispatchMetric(first, strategy), getDispatchMetric(second, strategy));
        if (metricComparison != 0) {
          return metricComparison;
        }
        int priorityComparison = Integer.compare(first.port.getPriority(), second.port.getPriority());
        if (priorityComparison != 0) {
          return priorityComparison;
        }
        return first.port.getParticipantId().compareTo(second.port.getParticipantId());
      }
    });

    double remaining = Math.max(0.0, available);
    int start = 0;
    while (start < entries.size()) {
      int end = start + 1;
      double metric = getDispatchMetric(entries.get(start), strategy);
      int priority = entries.get(start).port.getPriority();
      double groupRequest = entries.get(start).requested;
      while (end < entries.size() && Double.compare(getDispatchMetric(entries.get(end), strategy), metric) == 0
          && entries.get(end).port.getPriority() == priority) {
        groupRequest += entries.get(end).requested;
        end++;
      }
      double groupAllocation = Math.min(remaining, groupRequest);
      allocateGroup(entries, start, end, groupRequest, groupAllocation);
      remaining -= groupAllocation;
      start = end;
    }
  }

  /** Gets the merit-order metric for one producer. */
  private static double getDispatchMetric(DispatchEntry entry, EnergyDispatchStrategy strategy) {
    if (strategy == EnergyDispatchStrategy.MINIMUM_COST) {
      return entry.port.getEnergyPricePerMWh();
    }
    if (strategy == EnergyDispatchStrategy.MINIMUM_EMISSIONS) {
      return entry.port.getEmissionFactorKgPerMWh();
    }
    throw new IllegalArgumentException("Merit-order allocation requires a cost or emissions strategy");
  }

  /** Allocates one equal-merit group proportionally. */
  private static void allocateGroup(List<DispatchEntry> entries, int start, int end, double groupRequest,
      double groupAllocation) {
    for (int index = start; index < end; index++) {
      DispatchEntry entry = entries.get(index);
      entry.allocated = groupRequest > 0.0 ? groupAllocation * entry.requested / groupRequest : 0.0;
    }
  }

  /**
   * Sums requested power.
   *
   * @param entries dispatch entries
   * @return requested power in W
   */
  private static double sumRequested(List<DispatchEntry> entries) {
    double total = 0.0;
    for (DispatchEntry entry : entries) {
      total += entry.requested;
    }
    return total;
  }

  /**
   * Sums allocated power.
   *
   * @param entries dispatch entries
   * @return allocated power in W
   */
  private static double sumAllocated(List<DispatchEntry> entries) {
    double total = 0.0;
    for (DispatchEntry entry : entries) {
      total += entry.allocated;
    }
    return total;
  }

  /**
   * Accumulates signed allocations for one dispatch leg.
   *
   * @param entries dispatch entries
   * @param generation {@code true} for generation, {@code false} for consumption
   */
  private void updatePortNetworkState(List<DispatchEntry> entries, boolean generation) {
    for (DispatchEntry entry : entries) {
      double signedAllocation = generation ? entry.allocated : -entry.allocated;
      Double previousAllocation = allocations.get(entry.port.getParticipantId());
      allocations.put(entry.port.getParticipantId(),
          (previousAllocation == null ? 0.0 : previousAllocation.doubleValue()) + signedAllocation);
    }
  }

  /**
   * Creates immutable results for every registered participant.
   *
   * @return allocation results
   */
  private List<EnergyAllocation> createAllocationResults() {
    List<EnergyAllocation> results = new ArrayList<EnergyAllocation>();
    for (EnergyPort port : registeredPorts.values()) {
      double requested;
      double contribution = getContribution(port.getParticipantId());
      if (port.getMode() == EnergyPortMode.CALCULATED) {
        requested = Math.abs(contribution);
      } else if (port.getMode() == EnergyPortMode.BALANCE) {
        requested = getAllocation(port.getParticipantId()) >= 0.0 ? port.getMaximumBalanceGeneration()
            : port.getMaximumBalanceConsumption();
      } else {
        requested = boundedRequest(port);
      }
      double allocated = Math.abs(getAllocation(port.getParticipantId()));
      double unmet = port.getDirection() == EnergyPortDirection.INPUT ? Math.max(0.0, requested - allocated) : 0.0;
      double curtailed = port.getDirection() == EnergyPortDirection.OUTPUT ? Math.max(0.0, requested - allocated) : 0.0;
      results.add(new EnergyAllocation(port.getParticipantId(), port.getParticipantName(), port.getMode(),
          port.getDirection(), port.getPriority(), requested, allocated, unmet, curtailed));
    }
    return results;
  }

  /**
   * Validates a participant key.
   *
   * @param participant participant key
   */
  private static void validateParticipant(String participant) {
    if (participant == null || participant.trim().isEmpty()) {
      throw new IllegalArgumentException("Energy bus participant cannot be null or empty");
    }
  }

  /**
   * Resolves a legacy owner-name and port-name key to a stable participant identifier.
   *
   * @param participant stable identifier or legacy display key
   * @return stable identifier when a registered display key matches, otherwise the supplied key
   */
  private String resolveParticipantId(String participant) {
    if (registeredPorts.containsKey(participant)) {
      return participant;
    }
    for (EnergyPort port : registeredPorts.values()) {
      if (port.getParticipantName().equals(participant)) {
        return port.getParticipantId();
      }
    }
    return participant;
  }

  /** Mutable working state used during one deterministic dispatch. */
  private static final class DispatchEntry {
    private final EnergyPort port;
    private final double requested;
    private double allocated = 0.0;

    /**
     * Creates a dispatch entry.
     *
     * @param port participant port
     * @param requested requested or offered power in W
     */
    private DispatchEntry(EnergyPort port, double requested) {
      this.port = port;
      this.requested = Math.max(0.0, requested);
    }

    /**
     * Creates a dispatch entry with optional fixed full allocation.
     *
     * @param port participant port
     * @param requested requested or offered power in W
     * @param fixedAllocation whether allocated power is fixed at the requested value
     */
    private DispatchEntry(EnergyPort port, double requested, boolean fixedAllocation) {
      this(port, requested);
      if (fixedAllocation) {
        allocated = this.requested;
      }
    }
  }
}
