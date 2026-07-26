package neqsim.process.equipment.energy;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.stream.EnergyNetworkReport;

/** Immutable result from an {@link EnergyTimeSeriesSimulator} study. */
public final class EnergyTimeSeriesResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Immutable result for one simulation interval. */
  public static final class IntervalResult implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final int intervalIndex;
    private final double startTimeSeconds;
    private final double durationSeconds;
    private final List<EnergyNetworkReport> networkReports;

    IntervalResult(int intervalIndex, double startTimeSeconds, double durationSeconds,
        List<EnergyNetworkReport> networkReports) {
      this.intervalIndex = intervalIndex;
      this.startTimeSeconds = startTimeSeconds;
      this.durationSeconds = durationSeconds;
      this.networkReports = Collections.unmodifiableList(new ArrayList<EnergyNetworkReport>(networkReports));
    }

    public int getIntervalIndex() {
      return intervalIndex;
    }

    public double getStartTimeSeconds() {
      return startTimeSeconds;
    }

    public double getDurationSeconds() {
      return durationSeconds;
    }

    public List<EnergyNetworkReport> getNetworkReports() {
      return networkReports;
    }
  }

  private final List<IntervalResult> intervals;
  private final double totalDurationSeconds;
  private final double servedEnergyMWh;
  private final double unmetEnergyMWh;
  private final double curtailedEnergyMWh;
  private final double operatingCost;
  private final double co2EmissionsKg;

  EnergyTimeSeriesResult(List<IntervalResult> intervals, double totalDurationSeconds, double servedEnergyMWh,
      double unmetEnergyMWh, double curtailedEnergyMWh, double operatingCost, double co2EmissionsKg) {
    this.intervals = Collections.unmodifiableList(new ArrayList<IntervalResult>(intervals));
    this.totalDurationSeconds = totalDurationSeconds;
    this.servedEnergyMWh = servedEnergyMWh;
    this.unmetEnergyMWh = unmetEnergyMWh;
    this.curtailedEnergyMWh = curtailedEnergyMWh;
    this.operatingCost = operatingCost;
    this.co2EmissionsKg = co2EmissionsKg;
  }

  public List<IntervalResult> getIntervals() {
    return intervals;
  }

  public double getTotalDurationSeconds() {
    return totalDurationSeconds;
  }

  public double getServedEnergyMWh() {
    return servedEnergyMWh;
  }

  public double getUnmetEnergyMWh() {
    return unmetEnergyMWh;
  }

  public double getCurtailedEnergyMWh() {
    return curtailedEnergyMWh;
  }

  public double getOperatingCost() {
    return operatingCost;
  }

  public double getCo2EmissionsKg() {
    return co2EmissionsKg;
  }

  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(this);
  }
}
