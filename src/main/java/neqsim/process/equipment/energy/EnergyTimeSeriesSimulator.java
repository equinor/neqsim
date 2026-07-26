package neqsim.process.equipment.energy;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import neqsim.process.equipment.stream.EnergyBus;
import neqsim.process.equipment.stream.EnergyNetworkReport;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Runs a process and its energy buses over a sequence of fixed-duration intervals.
 *
 * <p>
 * Profiles are evaluated at the start of each interval. The process can be executed either as a repeated steady-state
 * model or through {@link ProcessSystem#runTransient(double, UUID)}. Each interval stores immutable energy-network
 * reports and contributes to integrated energy, cost, and emission KPIs.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class EnergyTimeSeriesSimulator implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Simulation execution mode. */
  public enum ExecutionMode {
    /** Re-run the complete steady-state process for every interval. */
    STEADY_STATE,
    /** Advance equipment dynamics using the interval duration. */
    TRANSIENT
  }

  /** Target that receives one evaluated profile value. */
  public interface ProfileTarget {
    /** Applies a scalar input value before one interval is executed. */
    void apply(double value);
  }

  /** Immutable profile-to-target binding. */
  public static final class ProfileBinding {
    private final EnergyTimeSeriesProfile profile;
    private final ProfileTarget target;

    public ProfileBinding(EnergyTimeSeriesProfile profile, ProfileTarget target) {
      if (profile == null || target == null) {
        throw new IllegalArgumentException("Profile and target are required");
      }
      this.profile = profile;
      this.target = target;
    }

    public EnergyTimeSeriesProfile getProfile() {
      return profile;
    }

    void apply(double timeSeconds) {
      target.apply(profile.getValue(timeSeconds));
    }
  }

  private final ProcessSystem process;
  private final List<EnergyBus> energyBuses = new ArrayList<EnergyBus>();
  private final List<ProfileBinding> profileBindings = new ArrayList<ProfileBinding>();
  private ExecutionMode executionMode = ExecutionMode.STEADY_STATE;
  private double intervalSeconds = 3600.0;
  private double durationSeconds = 86400.0;

  public EnergyTimeSeriesSimulator(ProcessSystem process) {
    if (process == null) {
      throw new IllegalArgumentException("Process system is required");
    }
    this.process = process;
  }

  public ProcessSystem getProcess() {
    return process;
  }

  public void addEnergyBus(EnergyBus energyBus) {
    if (energyBus == null) {
      throw new IllegalArgumentException("Energy bus cannot be null");
    }
    for (EnergyBus existing : energyBuses) {
      if (existing == energyBus) {
        return;
      }
    }
    energyBuses.add(energyBus);
  }

  public List<EnergyBus> getEnergyBuses() {
    return Collections.unmodifiableList(energyBuses);
  }

  public void addProfile(EnergyTimeSeriesProfile profile, ProfileTarget target) {
    profileBindings.add(new ProfileBinding(profile, target));
  }

  public List<ProfileBinding> getProfileBindings() {
    return Collections.unmodifiableList(profileBindings);
  }

  public ExecutionMode getExecutionMode() {
    return executionMode;
  }

  public void setExecutionMode(ExecutionMode executionMode) {
    if (executionMode == null) {
      throw new IllegalArgumentException("Execution mode is required");
    }
    this.executionMode = executionMode;
  }

  public double getIntervalSeconds() {
    return intervalSeconds;
  }

  public void setIntervalSeconds(double intervalSeconds) {
    if (!Double.isFinite(intervalSeconds) || intervalSeconds <= 0.0) {
      throw new IllegalArgumentException("Time-series interval must be positive and finite");
    }
    this.intervalSeconds = intervalSeconds;
  }

  public double getDurationSeconds() {
    return durationSeconds;
  }

  public void setDurationSeconds(double durationSeconds) {
    if (!Double.isFinite(durationSeconds) || durationSeconds <= 0.0) {
      throw new IllegalArgumentException("Time-series duration must be positive and finite");
    }
    this.durationSeconds = durationSeconds;
  }

  /** Runs the configured study. */
  public EnergyTimeSeriesResult run() {
    if (energyBuses.isEmpty()) {
      throw new IllegalStateException("Add at least one energy bus before running a time-series study");
    }

    List<EnergyTimeSeriesResult.IntervalResult> intervals = new ArrayList<EnergyTimeSeriesResult.IntervalResult>();
    double servedEnergyMWh = 0.0;
    double unmetEnergyMWh = 0.0;
    double curtailedEnergyMWh = 0.0;
    double operatingCost = 0.0;
    double co2EmissionsKg = 0.0;

    double timeSeconds = 0.0;
    int intervalIndex = 0;
    while (timeSeconds < durationSeconds) {
      double stepSeconds = Math.min(intervalSeconds, durationSeconds - timeSeconds);
      for (ProfileBinding binding : profileBindings) {
        binding.apply(timeSeconds);
      }

      UUID calculationId = UUID.randomUUID();
      if (executionMode == ExecutionMode.TRANSIENT) {
        process.runTransient(stepSeconds, calculationId);
      } else {
        process.run(calculationId);
      }

      List<EnergyNetworkReport> reports = new ArrayList<EnergyNetworkReport>(energyBuses.size());
      double hours = stepSeconds / 3600.0;
      for (EnergyBus energyBus : energyBuses) {
        EnergyNetworkReport report = energyBus.hasSolution() ? energyBus.getLastReport() : energyBus.solveBalance();
        if (report == null) {
          report = energyBus.solveBalance();
        }
        reports.add(report);
        servedEnergyMWh += report.getServedDemand() * hours / 1.0e6;
        unmetEnergyMWh += report.getUnmetDemand() * hours / 1.0e6;
        curtailedEnergyMWh += report.getCurtailedSupply() * hours / 1.0e6;
        operatingCost += report.getOperatingCostPerHour() * hours;
        co2EmissionsKg += report.getCo2EmissionRate() * hours;
      }

      intervals.add(new EnergyTimeSeriesResult.IntervalResult(intervalIndex, timeSeconds, stepSeconds, reports));
      timeSeconds += stepSeconds;
      intervalIndex++;
    }

    return new EnergyTimeSeriesResult(intervals, durationSeconds, servedEnergyMWh, unmetEnergyMWh, curtailedEnergyMWh,
        operatingCost, co2EmissionsKg);
  }
}
