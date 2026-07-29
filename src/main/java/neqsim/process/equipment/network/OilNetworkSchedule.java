package neqsim.process.equipment.network;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Deterministic discrete-time oil terminal, parcel, blend, and cargo scheduler.
 */
public class OilNetworkSchedule implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final OilTerminalNode terminal;
  private final List<NetworkPeriod> periods = new ArrayList<NetworkPeriod>();
  private final List<ScheduledReceipt> receipts = new ArrayList<ScheduledReceipt>();
  private final List<CargoNomination> cargoNominations = new ArrayList<CargoNomination>();
  private final Map<String, boolean[]> tankAvailability = new LinkedHashMap<String, boolean[]>();
  private transient LoopedPipeNetwork hydraulicNetwork;

  /**
   * Create a schedule.
   *
   * @param terminal terminal opening state
   */
  public OilNetworkSchedule(OilTerminalNode terminal) {
    this.terminal = terminal;
  }

  /**
   * Add consecutive hourly periods.
   *
   * @param startIso ISO-8601 start
   * @param count period count
   */
  public void addHourlyPeriods(String startIso, int count) {
    Instant start = Instant.parse(startIso);
    for (int index = 0; index < count; index++) {
      periods.add(new NetworkPeriod(periods.size(), start.plusSeconds(index * 3600L).toString(), 3600.0));
    }
  }

  /**
   * Add an explicit period.
   *
   * @param startIso ISO-8601 start
   * @param durationSeconds duration
   */
  public void addPeriod(String startIso, double durationSeconds) {
    periods.add(new NetworkPeriod(periods.size(), startIso, durationSeconds));
  }

  /**
   * Add a scheduled pipeline/route receipt.
   *
   * @param tankName receiving tank
   * @param parcel parcel; entryPeriod selects the period
   */
  public void addReceipt(String tankName, CrudeParcel parcel) {
    receipts.add(new ScheduledReceipt(tankName, parcel));
  }

  /**
   * Add a cargo nomination.
   *
   * @param nomination cargo
   */
  public void addCargoNomination(CargoNomination nomination) {
    cargoNominations.add(nomination);
  }

  /**
   * Couple period hydraulic feasibility, pipeline capacity, and pump residuals.
   *
   * @param network oil pipeline network
   */
  public void setHydraulicNetwork(LoopedPipeNetwork network) {
    hydraulicNetwork = network;
  }

  /**
   * Set tank/cavern availability over a period range.
   *
   * @param tankName tank
   * @param fromPeriod inclusive start
   * @param toPeriod exclusive end
   * @param available availability
   */
  public void setTankAvailability(String tankName, int fromPeriod, int toPeriod, boolean available) {
    boolean[] schedule = tankAvailability.get(tankName);
    if (schedule == null) {
      schedule = new boolean[periods.size()];
      Arrays.fill(schedule, true);
      tankAvailability.put(tankName, schedule);
    }
    for (int index = fromPeriod; index < toPeriod; index++) {
      schedule[index] = available;
    }
  }

  /**
   * Build a deterministic feasible-first schedule.
   *
   * @return schedule result
   */
  public OilNetworkScheduleResult optimize() {
    if (periods.isEmpty()) {
      throw new IllegalStateException("Oil schedule has no periods");
    }
    OilTerminalNode working = terminal.copy();
    Map<String, TankInventoryState> initial = snapshotTerminal(working);
    double initialMass = totalMass(initial);
    Map<String, Double> initialComponents = totalComponents(initial);
    double totalReceiptMass = 0.0;
    Map<String, Double> receiptComponents = new LinkedHashMap<String, Double>();
    double totalCargoMass = 0.0;
    Map<String, Double> cargoComponents = new LinkedHashMap<String, Double>();
    Map<String, CargoLoadingResult> cargoResults = new LinkedHashMap<String, CargoLoadingResult>();
    List<OilSchedulePeriodResult> periodResults = new ArrayList<OilSchedulePeriodResult>();
    Map<String, Double> activeConstraints = new LinkedHashMap<String, Double>();
    boolean scheduleFeasible = true;

    for (NetworkPeriod period : periods) {
      int periodIndex = period.getIndex();
      applyTankAvailability(working, periodIndex);
      for (OilTerminalTank tank : working.getTanks().values()) {
        tank.beginPeriod(periodIndex);
      }
      Map<String, TankInventoryState> opening = snapshotTerminal(working);
      List<String> receivedIds = new ArrayList<String>();
      List<CargoLoadingResult> loaded = new ArrayList<CargoLoadingResult>();
      Map<String, Double> residuals = new LinkedHashMap<String, Double>();
      Set<String> busyBerths = new HashSet<String>();

      for (ScheduledReceipt receipt : receipts) {
        if (receipt.parcel.getEntryPeriod() != periodIndex) {
          continue;
        }
        try {
          if (!working.isRouteAvailable(receipt.parcel.getRoute())) {
            throw new IllegalStateException("Receipt route is unavailable: " + receipt.parcel.getRoute());
          }
          working.getTank(receipt.tankName).receive(receipt.parcel, period.getDurationSeconds());
          receivedIds.add(receipt.parcel.getId());
          totalReceiptMass += receipt.parcel.getMassKg();
          addComponentMass(receiptComponents, receipt.parcel);
        } catch (Exception ex) {
          residuals.put("receipt." + receipt.parcel.getId(), 1.0);
        }
      }

      for (CargoNomination cargo : cargoNominations) {
        if (cargoResults.containsKey(cargo.getCargoId()) || periodIndex < cargo.getEarliestPeriod()
            || periodIndex > cargo.getLatestPeriod()) {
          continue;
        }
        if (busyBerths.contains(cargo.getBerth())) {
          continue;
        }
        double requiredRate = cargo.getMassKg() / period.getDurationSeconds();
        if (cargo.getMaximumLoadingRateKgS() > 0.0 && requiredRate > cargo.getMaximumLoadingRateKgS() + 1.0e-12) {
          residuals.put("cargo." + cargo.getCargoId() + ".loadingRate",
              requiredRate - cargo.getMaximumLoadingRateKgS());
          continue;
        }

        List<String> tankOrder = cargo.getPreferredTanks().isEmpty()
            ? new ArrayList<String>(working.getTanks().keySet())
            : cargo.getPreferredTanks();
        CargoLoadingResult cargoResult = null;
        for (String tankName : tankOrder) {
          OilTerminalTank tank = working.getTank(tankName);
          if (!tank.isAvailable() || tank.getMassKg() - cargo.getMassKg() < tank.getHeelKg() - 1.0e-9) {
            continue;
          }
          try {
            CrudeParcel parcel = tank.withdraw(cargo.getCargoId(), cargo.getMassKg(), periodIndex,
                "berth:" + cargo.getBerth(), period.getDurationSeconds());
            NetworkQualityComplianceReport qualityReport = null;
            if (cargo.getQualityProfile() != null) {
              qualityReport = NetworkQualityEvaluator.evaluate("cargo." + cargo.getCargoId(), cargo.getQualityProfile(),
                  parcel.getAssay().getFluid(), parcel.getAssay().getAttributes());
            }
            cargoResult = new CargoLoadingResult(cargo.getCargoId(), periodIndex, tankName, parcel, qualityReport);
            if (qualityReport != null && !qualityReport.isCompliant()) {
              residuals.put("cargo." + cargo.getCargoId() + ".quality", 1.0);
            }
            break;
          } catch (Exception ex) {
            // Try the next preferred compatible tank.
          }
        }
        if (cargoResult != null) {
          cargoResults.put(cargo.getCargoId(), cargoResult);
          loaded.add(cargoResult);
          busyBerths.add(cargo.getBerth());
          totalCargoMass += cargo.getMassKg();
          addComponentMass(cargoComponents, cargoResult.getParcel());
        } else if (periodIndex == cargo.getLatestPeriod()) {
          residuals.put("cargo." + cargo.getCargoId() + ".unfulfilled", cargo.getMassKg());
        }
      }

      if (hydraulicNetwork != null) {
        try {
          hydraulicNetwork.run();
          if (!hydraulicNetwork.isConverged()) {
            residuals.put("hydraulics.convergence", 1.0);
          }
          List<String> hydraulicViolations = hydraulicNetwork.checkConstraints();
          if (!hydraulicViolations.isEmpty()) {
            residuals.put("hydraulics.constraints", (double) hydraulicViolations.size());
          }
          for (String edgeName : hydraulicNetwork.getPipeNames()) {
            LoopedPipeNetwork.NetworkPipe edge = hydraulicNetwork.getPipe(edgeName);
            if (edge.getElementType() == LoopedPipeNetwork.NetworkElementType.PUMP) {
              if (edge.getPumpPowerResidualKW() > 0.0) {
                residuals.put("pump." + edgeName + ".power", edge.getPumpPowerResidualKW());
              }
              if (edge.getPumpMinimumFlowResidualKgS() > 0.0) {
                residuals.put("pump." + edgeName + ".minimumFlow", edge.getPumpMinimumFlowResidualKgS());
              }
              if (Double.isFinite(edge.getPumpNpshResidualM()) && edge.getPumpNpshResidualM() > 0.0) {
                residuals.put("pump." + edgeName + ".npsh", edge.getPumpNpshResidualM());
              }
            }
          }
        } catch (Exception ex) {
          residuals.put("hydraulics.exception", 1.0);
        }
      }

      Map<String, TankInventoryState> closing = snapshotTerminal(working);
      boolean periodFeasible = residuals.isEmpty();
      scheduleFeasible &= periodFeasible;
      for (Map.Entry<String, Double> residual : residuals.entrySet()) {
        activeConstraints.put("period." + periodIndex + "." + residual.getKey(), residual.getValue());
      }
      periodResults
          .add(new OilSchedulePeriodResult(period, periodFeasible, opening, closing, receivedIds, loaded, residuals));
    }

    for (CargoNomination cargo : cargoNominations) {
      if (!cargoResults.containsKey(cargo.getCargoId())) {
        scheduleFeasible = false;
        activeConstraints.put("cargo." + cargo.getCargoId() + ".notLoaded", cargo.getMassKg());
      }
    }

    Map<String, TankInventoryState> terminalState = snapshotTerminal(working);
    double closingMass = totalMass(terminalState);
    double massResidual = initialMass + totalReceiptMass - totalCargoMass - closingMass;
    Map<String, Double> closingComponents = totalComponents(terminalState);
    double componentResidual = calculateComponentClosure(initialComponents, receiptComponents, cargoComponents,
        closingComponents);
    if (Math.abs(massResidual) > 1.0e-6) {
      scheduleFeasible = false;
      activeConstraints.put("schedule.massBalance", Math.abs(massResidual));
    }
    if (componentResidual > 1.0e-6) {
      scheduleFeasible = false;
      activeConstraints.put("schedule.componentBalance", componentResidual);
    }

    return new OilNetworkScheduleResult(scheduleFeasible, periodResults, cargoResults, terminalState, massResidual,
        componentResidual, activeConstraints, scheduleFeasible ? "Oil schedule feasible"
            : "Oil schedule contains infeasible movements, quality checks, or balances");
  }

  private void applyTankAvailability(OilTerminalNode working, int periodIndex) {
    for (Map.Entry<String, boolean[]> entry : tankAvailability.entrySet()) {
      working.getTank(entry.getKey()).setAvailable(entry.getValue()[periodIndex]);
    }
  }

  private Map<String, TankInventoryState> snapshotTerminal(OilTerminalNode working) {
    Map<String, TankInventoryState> states = new LinkedHashMap<String, TankInventoryState>();
    for (OilTerminalTank tank : working.getTanks().values()) {
      states.put(tank.getName(), tank.snapshot());
    }
    return states;
  }

  private double totalMass(Map<String, TankInventoryState> states) {
    double mass = 0.0;
    for (TankInventoryState state : states.values()) {
      mass += state.getMassKg();
    }
    return mass;
  }

  private Map<String, Double> totalComponents(Map<String, TankInventoryState> states) {
    Map<String, Double> components = new LinkedHashMap<String, Double>();
    for (TankInventoryState state : states.values()) {
      addValues(components, state.getComponentMassKg());
    }
    return components;
  }

  private void addComponentMass(Map<String, Double> target, CrudeParcel parcel) {
    SystemInterface fluid = parcel.getAssay().getFluid();
    double[] composition = fluid.getMolarComposition();
    double averageMolarMass = fluid.getMolarMass();
    for (int index = 0; index < fluid.getNumberOfComponents(); index++) {
      ComponentInterface component = fluid.getPhase(0).getComponent(index);
      double mass = parcel.getMassKg() * composition[index] * component.getMolarMass() / averageMolarMass;
      Double existing = target.get(component.getComponentName());
      target.put(component.getComponentName(), (existing == null ? 0.0 : existing) + mass);
    }
  }

  private void addValues(Map<String, Double> target, Map<String, Double> values) {
    for (Map.Entry<String, Double> entry : values.entrySet()) {
      Double existing = target.get(entry.getKey());
      target.put(entry.getKey(), (existing == null ? 0.0 : existing) + entry.getValue());
    }
  }

  private double calculateComponentClosure(Map<String, Double> initial, Map<String, Double> receiptsByComponent,
      Map<String, Double> cargoesByComponent, Map<String, Double> closing) {
    Set<String> componentNames = new HashSet<String>();
    componentNames.addAll(initial.keySet());
    componentNames.addAll(receiptsByComponent.keySet());
    componentNames.addAll(cargoesByComponent.keySet());
    componentNames.addAll(closing.keySet());
    double maxResidual = 0.0;
    for (String componentName : componentNames) {
      double residual = value(initial, componentName) + value(receiptsByComponent, componentName)
          - value(cargoesByComponent, componentName) - value(closing, componentName);
      maxResidual = Math.max(maxResidual, Math.abs(residual));
    }
    return maxResidual;
  }

  private double value(Map<String, Double> values, String key) {
    Double value = values.get(key);
    return value == null ? 0.0 : value;
  }

  /** @return immutable periods */
  public List<NetworkPeriod> getPeriods() {
    return Collections.unmodifiableList(periods);
  }

  private static final class ScheduledReceipt implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String tankName;
    private final CrudeParcel parcel;

    private ScheduledReceipt(String tankName, CrudeParcel parcel) {
      this.tankName = tankName;
      this.parcel = parcel;
    }
  }
}
