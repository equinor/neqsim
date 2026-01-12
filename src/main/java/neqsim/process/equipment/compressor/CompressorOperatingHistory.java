package neqsim.process.equipment.compressor;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Records and tracks compressor operating history for analysis and trending.
 *
 * <p>
 * This class stores operating points over time, enabling post-simulation analysis of compressor
 * behavior, surge events, and performance trends.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class CompressorOperatingHistory implements Serializable {
  private static final long serialVersionUID = 1L;

  private final List<OperatingPoint> history = new ArrayList<>();
  private int surgeEventCount = 0;
  private double totalTimeInSurge = 0.0;
  private OperatingPoint peakPowerPoint = null;
  private OperatingPoint peakHeadPoint = null;
  private OperatingPoint peakFlowPoint = null;
  private double lastRecordedTime = 0.0;
  private boolean inSurgeCondition = false;
  private double surgeEntryTime = 0.0;

  /**
   * Inner class representing a single operating point.
   */
  public static class OperatingPoint implements Serializable {
    private static final long serialVersionUID = 1L;

    private final double time;
    private final double flow;
    private final double head;
    private final double speed;
    private final double power;
    private final double efficiency;
    private final double surgeMargin;
    private final double stoneWallMargin;
    private final CompressorState state;
    private final double inletPressure;
    private final double outletPressure;
    private final double inletTemperature;
    private final double outletTemperature;

    /**
     * Constructor for OperatingPoint.
     *
     * @param time simulation time in seconds
     * @param flow volumetric flow rate in m³/hr
     * @param head polytropic head in kJ/kg
     * @param speed rotational speed in RPM
     * @param power shaft power in kW
     * @param efficiency polytropic efficiency (0-1)
     * @param surgeMargin margin to surge line (ratio)
     * @param stoneWallMargin margin to stone wall (ratio)
     * @param state compressor operating state
     * @param inletPressure inlet pressure in bara
     * @param outletPressure outlet pressure in bara
     * @param inletTemperature inlet temperature in K
     * @param outletTemperature outlet temperature in K
     */
    public OperatingPoint(double time, double flow, double head, double speed, double power,
        double efficiency, double surgeMargin, double stoneWallMargin, CompressorState state,
        double inletPressure, double outletPressure, double inletTemperature,
        double outletTemperature) {
      this.time = time;
      this.flow = flow;
      this.head = head;
      this.speed = speed;
      this.power = power;
      this.efficiency = efficiency;
      this.surgeMargin = surgeMargin;
      this.stoneWallMargin = stoneWallMargin;
      this.state = state;
      this.inletPressure = inletPressure;
      this.outletPressure = outletPressure;
      this.inletTemperature = inletTemperature;
      this.outletTemperature = outletTemperature;
    }

    /**
     * Get the simulation time.
     *
     * @return time in seconds
     */
    public double getTime() {
      return time;
    }

    /**
     * Get the flow rate.
     *
     * @return flow in m³/hr
     */
    public double getFlow() {
      return flow;
    }

    /**
     * Get the polytropic head.
     *
     * @return head in kJ/kg
     */
    public double getHead() {
      return head;
    }

    /**
     * Get the rotational speed.
     *
     * @return speed in RPM
     */
    public double getSpeed() {
      return speed;
    }

    /**
     * Get the shaft power.
     *
     * @return power in kW
     */
    public double getPower() {
      return power;
    }

    /**
     * Get the polytropic efficiency.
     *
     * @return efficiency as ratio (0-1)
     */
    public double getEfficiency() {
      return efficiency;
    }

    /**
     * Get the surge margin.
     *
     * @return surge margin as ratio
     */
    public double getSurgeMargin() {
      return surgeMargin;
    }

    /**
     * Get the stone wall margin.
     *
     * @return stone wall margin as ratio
     */
    public double getStoneWallMargin() {
      return stoneWallMargin;
    }

    /**
     * Get the compressor state.
     *
     * @return the operating state
     */
    public CompressorState getState() {
      return state;
    }

    /**
     * Get the inlet pressure.
     *
     * @return pressure in bara
     */
    public double getInletPressure() {
      return inletPressure;
    }

    /**
     * Get the outlet pressure.
     *
     * @return pressure in bara
     */
    public double getOutletPressure() {
      return outletPressure;
    }

    /**
     * Get the inlet temperature.
     *
     * @return temperature in K
     */
    public double getInletTemperature() {
      return inletTemperature;
    }

    /**
     * Get the outlet temperature.
     *
     * @return temperature in K
     */
    public double getOutletTemperature() {
      return outletTemperature;
    }

    /**
     * Check if this point is in surge.
     *
     * @return true if surge margin is negative
     */
    public boolean isInSurge() {
      return surgeMargin < 0;
    }

    @Override
    public String toString() {
      return String.format(
          "t=%.1fs, Q=%.1f m³/hr, H=%.1f kJ/kg, N=%.0f RPM, P=%.1f kW, η=%.1f%%, SM=%.1f%%", time,
          flow, head, speed, power, efficiency * 100, surgeMargin * 100);
    }
  }

  /**
   * Record a new operating point from a compressor.
   *
   * @param time simulation time in seconds
   * @param compressor the compressor to record data from
   */
  public void recordOperatingPoint(double time, Compressor compressor) {
    double flow = 0.0;
    double head = 0.0;
    double speed = compressor.getSpeed();
    double power = compressor.getPower("kW");
    double efficiency = compressor.getPolytropicEfficiency();
    double surgeMargin = compressor.getDistanceToSurge();
    double stoneWallMargin = compressor.getDistanceToStoneWall();
    CompressorState state = compressor.getOperatingState();
    double inletPressure = 0.0;
    double outletPressure = 0.0;
    double inletTemperature = 0.0;
    double outletTemperature = 0.0;

    if (compressor.getInletStream() != null) {
      flow = compressor.getInletStream().getFlowRate("m3/hr");
      inletPressure = compressor.getInletStream().getPressure("bara");
      inletTemperature = compressor.getInletStream().getTemperature("K");
    }

    if (compressor.getOutletStream() != null) {
      outletPressure = compressor.getOutletStream().getPressure("bara");
      outletTemperature = compressor.getOutletStream().getTemperature("K");
    }

    head = compressor.getPolytropicFluidHead();

    OperatingPoint point =
        new OperatingPoint(time, flow, head, speed, power, efficiency, surgeMargin, stoneWallMargin,
            state, inletPressure, outletPressure, inletTemperature, outletTemperature);

    recordOperatingPoint(point);
  }

  /**
   * Record a new operating point.
   *
   * @param point the operating point to record
   */
  public void recordOperatingPoint(OperatingPoint point) {
    history.add(point);

    // Track surge conditions
    if (point.isInSurge()) {
      if (!inSurgeCondition) {
        // Entering surge
        inSurgeCondition = true;
        surgeEntryTime = point.getTime();
        surgeEventCount++;
      }
    } else {
      if (inSurgeCondition) {
        // Exiting surge
        totalTimeInSurge += point.getTime() - surgeEntryTime;
        inSurgeCondition = false;
      }
    }

    // Track peak values
    if (peakPowerPoint == null || point.getPower() > peakPowerPoint.getPower()) {
      peakPowerPoint = point;
    }
    if (peakHeadPoint == null || point.getHead() > peakHeadPoint.getHead()) {
      peakHeadPoint = point;
    }
    if (peakFlowPoint == null || point.getFlow() > peakFlowPoint.getFlow()) {
      peakFlowPoint = point;
    }

    lastRecordedTime = point.getTime();
  }

  /**
   * Get the complete operating history.
   *
   * @return list of all recorded operating points
   */
  public List<OperatingPoint> getHistory() {
    return new ArrayList<>(history);
  }

  /**
   * Get the total time spent in surge condition.
   *
   * @return time in seconds
   */
  public double getTimeInSurge() {
    double time = totalTimeInSurge;
    if (inSurgeCondition) {
      time += lastRecordedTime - surgeEntryTime;
    }
    return time;
  }

  /**
   * Get the number of surge events.
   *
   * @return count of times the compressor entered surge
   */
  public int getSurgeEventCount() {
    return surgeEventCount;
  }

  /**
   * Get the operating point where peak power occurred.
   *
   * @return the peak power operating point, or null if no data recorded
   */
  public OperatingPoint getPeakPower() {
    return peakPowerPoint;
  }

  /**
   * Get the operating point where peak head occurred.
   *
   * @return the peak head operating point, or null if no data recorded
   */
  public OperatingPoint getPeakHead() {
    return peakHeadPoint;
  }

  /**
   * Get the operating point where peak flow occurred.
   *
   * @return the peak flow operating point, or null if no data recorded
   */
  public OperatingPoint getPeakFlow() {
    return peakFlowPoint;
  }

  /**
   * Get the minimum surge margin observed.
   *
   * @return minimum surge margin as ratio, or 1.0 if no data
   */
  public double getMinimumSurgeMargin() {
    double minMargin = 1.0;
    for (OperatingPoint point : history) {
      if (point.getSurgeMargin() < minMargin) {
        minMargin = point.getSurgeMargin();
      }
    }
    return minMargin;
  }

  /**
   * Get the average efficiency over the recorded period.
   *
   * @return average polytropic efficiency
   */
  public double getAverageEfficiency() {
    if (history.isEmpty()) {
      return 0.0;
    }
    double sum = 0.0;
    for (OperatingPoint point : history) {
      sum += point.getEfficiency();
    }
    return sum / history.size();
  }

  /**
   * Get the number of recorded operating points.
   *
   * @return count of points in history
   */
  public int getPointCount() {
    return history.size();
  }

  /**
   * Clear all recorded history.
   */
  public void clear() {
    history.clear();
    surgeEventCount = 0;
    totalTimeInSurge = 0.0;
    peakPowerPoint = null;
    peakHeadPoint = null;
    peakFlowPoint = null;
    lastRecordedTime = 0.0;
    inSurgeCondition = false;
    surgeEntryTime = 0.0;
  }

  /**
   * Export the operating history to a CSV file.
   *
   * @param filename the path to the output file
   * @throws IOException if file cannot be written
   */
  public void exportToCSV(String filename) throws IOException {
    try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
      // Header
      writer.println(
          "Time[s],Flow[m3/hr],Head[kJ/kg],Speed[RPM],Power[kW],Efficiency[-],SurgeMargin[-],"
              + "StoneWallMargin[-],State,InletP[bara],OutletP[bara],InletT[K],OutletT[K]");

      // Data
      for (OperatingPoint point : history) {
        writer.printf("%.3f,%.3f,%.3f,%.1f,%.3f,%.4f,%.4f,%.4f,%s,%.3f,%.3f,%.2f,%.2f%n",
            point.getTime(), point.getFlow(), point.getHead(), point.getSpeed(), point.getPower(),
            point.getEfficiency(), point.getSurgeMargin(), point.getStoneWallMargin(),
            point.getState().name(), point.getInletPressure(), point.getOutletPressure(),
            point.getInletTemperature(), point.getOutletTemperature());
      }
    }
  }

  /**
   * Generate a summary report of the operating history.
   *
   * @return multi-line summary string
   */
  public String generateSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Compressor Operating History Summary ===\n");
    sb.append(String.format("Total points recorded: %d\n", history.size()));

    if (!history.isEmpty()) {
      OperatingPoint first = history.get(0);
      OperatingPoint last = history.get(history.size() - 1);
      sb.append(String.format("Time range: %.1f s to %.1f s\n", first.getTime(), last.getTime()));
      sb.append(String.format("Surge events: %d\n", surgeEventCount));
      sb.append(String.format("Total time in surge: %.1f s\n", getTimeInSurge()));
      sb.append(String.format("Minimum surge margin: %.1f%%\n", getMinimumSurgeMargin() * 100));
      sb.append(String.format("Average efficiency: %.1f%%\n", getAverageEfficiency() * 100));

      if (peakPowerPoint != null) {
        sb.append(String.format("Peak power: %.1f kW at t=%.1f s\n", peakPowerPoint.getPower(),
            peakPowerPoint.getTime()));
      }
      if (peakHeadPoint != null) {
        sb.append(String.format("Peak head: %.1f kJ/kg at t=%.1f s\n", peakHeadPoint.getHead(),
            peakHeadPoint.getTime()));
      }
      if (peakFlowPoint != null) {
        sb.append(String.format("Peak flow: %.1f m³/hr at t=%.1f s\n", peakFlowPoint.getFlow(),
            peakFlowPoint.getTime()));
      }
    }

    return sb.toString();
  }
}
