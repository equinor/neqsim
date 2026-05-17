package neqsim.process.equipment.reservoir;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;

/**
 * Injection conformance monitor for diagnosing out-of-zone injection.
 *
 * <p>
 * This class provides time-series analysis tools for injection well surveillance data. It uses Hall
 * plot analysis, slope change detection, and injection profile interpretation to identify
 * conformance issues such as:
 * </p>
 * <ul>
 * <li>Fracture initiation or extension (Hall slope decrease)</li>
 * <li>Near-wellbore plugging or scaling (Hall slope increase)</li>
 * <li>Out-of-zone injection (injection profile mismatch vs target)</li>
 * <li>Channel development through cement (progressive conformance loss)</li>
 * </ul>
 *
 * <h2>Hall Plot Analysis</h2>
 * <p>
 * The Hall plot is cumulative wellhead pressure times time (sum of WHP * dt) vs cumulative
 * injection volume. A constant Hall slope indicates stable injectivity. A decreasing slope
 * indicates fracture growth or improved injectivity. An increasing slope indicates plugging or skin
 * increase.
 * </p>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * InjectionConformanceMonitor monitor = new InjectionConformanceMonitor("Injector-1 Monitor");
 *
 * // Record daily injection data
 * monitor.recordInjectionData(0.0, 250.0, 5000.0); // day 0: 250 bar WHP, 5000 bbl/d
 * monitor.recordInjectionData(1.0, 252.0, 5000.0); // day 1
 * monitor.recordInjectionData(2.0, 248.0, 5200.0); // day 2
 * // ... more data points ...
 *
 * // Calculate Hall plot
 * monitor.calculateHallPlot();
 * double slope = monitor.getCurrentHallSlope();
 *
 * // Detect slope changes
 * boolean slopeChanged = monitor.detectSlopeChange(0.2); // 20% threshold
 * String diagnosis = monitor.getDiagnosis();
 *
 * System.out.println("Hall slope: " + slope + " bar/m3");
 * System.out.println("Diagnosis: " + diagnosis);
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class InjectionConformanceMonitor extends ProcessEquipmentBaseClass {
  private static final long serialVersionUID = 1000L;
  private static final Logger logger = LogManager.getLogger(InjectionConformanceMonitor.class);

  /**
   * Conformance diagnosis result.
   */
  public enum ConformanceDiagnosis {
    /** Normal injection, stable injectivity. */
    NORMAL,
    /** Fracture growth or improved injectivity detected. */
    FRACTURE_GROWTH,
    /** Near-wellbore plugging or scaling detected. */
    PLUGGING,
    /** Insufficient data for diagnosis. */
    INSUFFICIENT_DATA,
    /** Out-of-zone injection suspected based on profile. */
    OUT_OF_ZONE_SUSPECTED
  }

  /**
   * Single injection data record.
   */
  public static class InjectionDataPoint implements Serializable {
    private static final long serialVersionUID = 1001L;
    private final double timeDays;
    private final double wellheadPressureBar;
    private final double injectionRateM3perDay;

    /**
     * Create an injection data point.
     *
     * @param timeDays time in days
     * @param whpBar wellhead pressure in bar
     * @param rateM3d injection rate in m3/day
     */
    public InjectionDataPoint(double timeDays, double whpBar, double rateM3d) {
      this.timeDays = timeDays;
      this.wellheadPressureBar = whpBar;
      this.injectionRateM3perDay = rateM3d;
    }

    /**
     * Get time.
     *
     * @return time in days
     */
    public double getTimeDays() {
      return timeDays;
    }

    /**
     * Get wellhead pressure.
     *
     * @return wellhead pressure in bar
     */
    public double getWellheadPressureBar() {
      return wellheadPressureBar;
    }

    /**
     * Get injection rate.
     *
     * @return injection rate in m3/day
     */
    public double getInjectionRateM3perDay() {
      return injectionRateM3perDay;
    }
  }

  /**
   * Zone injection profile data point.
   */
  public static class ZoneProfilePoint implements Serializable {
    private static final long serialVersionUID = 1002L;
    private final String zoneName;
    private final double depthM;
    private final double allocationFraction;
    private final boolean isTargetZone;

    /**
     * Create a zone profile data point.
     *
     * @param zoneName zone name
     * @param depthM zone depth in meters
     * @param allocationFraction fraction of total injection going to this zone
     * @param isTargetZone whether this is the intended injection zone
     */
    public ZoneProfilePoint(String zoneName, double depthM, double allocationFraction,
        boolean isTargetZone) {
      this.zoneName = zoneName;
      this.depthM = depthM;
      this.allocationFraction = allocationFraction;
      this.isTargetZone = isTargetZone;
    }

    /**
     * Get zone name.
     *
     * @return zone name
     */
    public String getZoneName() {
      return zoneName;
    }

    /**
     * Get zone depth.
     *
     * @return zone depth in meters
     */
    public double getDepthM() {
      return depthM;
    }

    /**
     * Get allocation fraction.
     *
     * @return allocation fraction (0.0 to 1.0)
     */
    public double getAllocationFraction() {
      return allocationFraction;
    }

    /**
     * Check if this is a target zone.
     *
     * @return true if target zone
     */
    public boolean isTargetZone() {
      return isTargetZone;
    }
  }

  private final List<InjectionDataPoint> dataPoints = new ArrayList<InjectionDataPoint>();
  private final List<ZoneProfilePoint> injectionProfile = new ArrayList<ZoneProfilePoint>();

  // Hall plot results
  private final List<Double> hallCumulativePressureTime = new ArrayList<Double>();
  private final List<Double> hallCumulativeVolume = new ArrayList<Double>();
  private double currentHallSlope = 0.0;
  private double initialHallSlope = 0.0;
  private boolean hallPlotCalculated = false;

  // Diagnosis
  private ConformanceDiagnosis diagnosis = ConformanceDiagnosis.INSUFFICIENT_DATA;

  /**
   * Create an injection conformance monitor.
   *
   * @param name monitor name
   */
  public InjectionConformanceMonitor(String name) {
    super(name);
  }

  /**
   * Record an injection data point.
   *
   * @param timeDays time since start of monitoring (days)
   * @param wellheadPressureBar wellhead pressure (bar)
   * @param injectionRateM3perDay injection rate (m3/day)
   */
  public void recordInjectionData(double timeDays, double wellheadPressureBar,
      double injectionRateM3perDay) {
    dataPoints.add(new InjectionDataPoint(timeDays, wellheadPressureBar, injectionRateM3perDay));
    hallPlotCalculated = false;
  }

  /**
   * Add a zone to the injection profile.
   *
   * @param zoneName zone identifier
   * @param depthM zone depth (m)
   * @param allocationFraction fraction of total injection going to this zone (0.0-1.0)
   * @param isTargetZone whether this is the intended injection zone
   */
  public void addZoneProfile(String zoneName, double depthM, double allocationFraction,
      boolean isTargetZone) {
    injectionProfile.add(new ZoneProfilePoint(zoneName, depthM, allocationFraction, isTargetZone));
  }

  /**
   * Calculate the Hall plot from recorded injection data.
   *
   * <p>
   * Hall integral is the cumulative sum of (WHP * dt) plotted against cumulative injection volume.
   * The slope of this plot indicates injectivity behavior.
   * </p>
   */
  public void calculateHallPlot() {
    hallCumulativePressureTime.clear();
    hallCumulativeVolume.clear();

    if (dataPoints.size() < 2) {
      diagnosis = ConformanceDiagnosis.INSUFFICIENT_DATA;
      hallPlotCalculated = false;
      return;
    }

    double cumPT = 0.0;
    double cumVol = 0.0;

    hallCumulativePressureTime.add(0.0);
    hallCumulativeVolume.add(0.0);

    for (int i = 1; i < dataPoints.size(); i++) {
      InjectionDataPoint prev = dataPoints.get(i - 1);
      InjectionDataPoint curr = dataPoints.get(i);
      double dt = curr.getTimeDays() - prev.getTimeDays();

      // Trapezoidal integration for pressure*time
      double avgWHP = (prev.getWellheadPressureBar() + curr.getWellheadPressureBar()) / 2.0;
      cumPT += avgWHP * dt;

      // Trapezoidal integration for volume
      double avgRate = (prev.getInjectionRateM3perDay() + curr.getInjectionRateM3perDay()) / 2.0;
      cumVol += avgRate * dt;

      hallCumulativePressureTime.add(cumPT);
      hallCumulativeVolume.add(cumVol);
    }

    // Calculate current slope using last 20% of data
    calculateCurrentSlope();
    if (hallCumulativeVolume.size() > 5) {
      calculateInitialSlope();
    }

    hallPlotCalculated = true;
  }

  /**
   * Get the current Hall slope from the most recent data.
   *
   * @return current Hall slope (bar day/m3)
   */
  public double getCurrentHallSlope() {
    if (!hallPlotCalculated) {
      calculateHallPlot();
    }
    return currentHallSlope;
  }

  /**
   * Get the initial Hall slope from the early data.
   *
   * @return initial Hall slope (bar day/m3)
   */
  public double getInitialHallSlope() {
    if (!hallPlotCalculated) {
      calculateHallPlot();
    }
    return initialHallSlope;
  }

  /**
   * Detect a significant change in the Hall slope.
   *
   * @param thresholdFraction fractional change threshold (e.g., 0.2 for 20%)
   * @return true if slope change exceeds threshold
   */
  public boolean detectSlopeChange(double thresholdFraction) {
    if (!hallPlotCalculated) {
      calculateHallPlot();
    }
    if (initialHallSlope <= 0) {
      return false;
    }
    double fractionalChange = Math.abs(currentHallSlope - initialHallSlope) / initialHallSlope;
    return fractionalChange > thresholdFraction;
  }

  /**
   * Get the current conformance diagnosis.
   *
   * @return diagnosis string
   */
  public String getDiagnosis() {
    if (!hallPlotCalculated) {
      calculateHallPlot();
    }
    updateDiagnosis();
    return diagnosis.name() + ": " + getDiagnosisDescription();
  }

  /**
   * Get the conformance diagnosis enum.
   *
   * @return diagnosis enum value
   */
  public ConformanceDiagnosis getConformanceDiagnosis() {
    if (!hallPlotCalculated) {
      calculateHallPlot();
    }
    updateDiagnosis();
    return diagnosis;
  }

  /**
   * Get the injection efficiency from the zone profile.
   *
   * <p>
   * Injection efficiency = fraction of total injection going to target zone(s).
   * </p>
   *
   * @return injection efficiency (0.0 to 1.0), or -1 if no profile available
   */
  public double getInjectionEfficiency() {
    if (injectionProfile.isEmpty()) {
      return -1.0;
    }
    double targetFraction = 0.0;
    for (ZoneProfilePoint point : injectionProfile) {
      if (point.isTargetZone()) {
        targetFraction += point.getAllocationFraction();
      }
    }
    return targetFraction;
  }

  /**
   * Get the out-of-zone injection fraction from the profile.
   *
   * @return out-of-zone fraction (0.0 to 1.0), or -1 if no profile available
   */
  public double getOutOfZoneFraction() {
    double efficiency = getInjectionEfficiency();
    if (efficiency < 0) {
      return -1.0;
    }
    return 1.0 - efficiency;
  }

  /**
   * Get the list of Hall plot cumulative pressure times.
   *
   * @return list of cumulative (WHP * dt) values
   */
  public List<Double> getHallCumulativePressureTime() {
    if (!hallPlotCalculated) {
      calculateHallPlot();
    }
    return new ArrayList<Double>(hallCumulativePressureTime);
  }

  /**
   * Get the list of Hall plot cumulative injection volumes.
   *
   * @return list of cumulative injection volumes (m3)
   */
  public List<Double> getHallCumulativeVolume() {
    if (!hallPlotCalculated) {
      calculateHallPlot();
    }
    return new ArrayList<Double>(hallCumulativeVolume);
  }

  /**
   * Get the number of recorded data points.
   *
   * @return data point count
   */
  public int getDataPointCount() {
    return dataPoints.size();
  }

  /**
   * Get recorded data points.
   *
   * @return list of injection data points
   */
  public List<InjectionDataPoint> getDataPoints() {
    return new ArrayList<InjectionDataPoint>(dataPoints);
  }

  /**
   * Get the injection zone profile.
   *
   * @return list of zone profile points
   */
  public List<ZoneProfilePoint> getInjectionProfile() {
    return new ArrayList<ZoneProfilePoint>(injectionProfile);
  }

  /**
   * Clear all recorded data and reset the monitor.
   */
  public void reset() {
    dataPoints.clear();
    injectionProfile.clear();
    hallCumulativePressureTime.clear();
    hallCumulativeVolume.clear();
    currentHallSlope = 0.0;
    initialHallSlope = 0.0;
    hallPlotCalculated = false;
    diagnosis = ConformanceDiagnosis.INSUFFICIENT_DATA;
  }

  /**
   * Calculate the Hall slope from the last 20% of data using least-squares linear regression.
   */
  private void calculateCurrentSlope() {
    int n = hallCumulativeVolume.size();
    if (n < 3) {
      currentHallSlope = 0.0;
      return;
    }
    int startIdx = Math.max(1, (int) (n * 0.8));
    currentHallSlope = calculateSlopeLinearRegression(startIdx, n - 1);
  }

  /**
   * Calculate the Hall slope from the first 20% of data (initial injectivity baseline).
   */
  private void calculateInitialSlope() {
    int n = hallCumulativeVolume.size();
    if (n < 5) {
      initialHallSlope = currentHallSlope;
      return;
    }
    int endIdx = Math.max(3, (int) (n * 0.2));
    initialHallSlope = calculateSlopeLinearRegression(1, endIdx);
  }

  /**
   * Calculate slope using least-squares linear regression on the Hall plot.
   *
   * @param startIdx start index (inclusive)
   * @param endIdx end index (inclusive)
   * @return slope (bar day / m3)
   */
  private double calculateSlopeLinearRegression(int startIdx, int endIdx) {
    int count = endIdx - startIdx + 1;
    if (count < 2) {
      return 0.0;
    }
    double sumX = 0.0;
    double sumY = 0.0;
    double sumXY = 0.0;
    double sumX2 = 0.0;

    for (int i = startIdx; i <= endIdx; i++) {
      double x = hallCumulativeVolume.get(i);
      double y = hallCumulativePressureTime.get(i);
      sumX += x;
      sumY += y;
      sumXY += x * y;
      sumX2 += x * x;
    }

    double denominator = count * sumX2 - sumX * sumX;
    if (Math.abs(denominator) < 1e-30) {
      return 0.0;
    }
    return (count * sumXY - sumX * sumY) / denominator;
  }

  /**
   * Update the conformance diagnosis based on Hall slope analysis and injection profile.
   */
  private void updateDiagnosis() {
    if (hallCumulativeVolume.size() < 5) {
      diagnosis = ConformanceDiagnosis.INSUFFICIENT_DATA;
      return;
    }

    // Check injection profile first
    if (!injectionProfile.isEmpty()) {
      double oozFraction = getOutOfZoneFraction();
      if (oozFraction > 0.2) {
        diagnosis = ConformanceDiagnosis.OUT_OF_ZONE_SUSPECTED;
        return;
      }
    }

    // Hall slope analysis
    if (initialHallSlope <= 0) {
      diagnosis = ConformanceDiagnosis.NORMAL;
      return;
    }

    double slopeRatio = currentHallSlope / initialHallSlope;

    if (slopeRatio < 0.8) {
      diagnosis = ConformanceDiagnosis.FRACTURE_GROWTH;
    } else if (slopeRatio > 1.2) {
      diagnosis = ConformanceDiagnosis.PLUGGING;
    } else {
      diagnosis = ConformanceDiagnosis.NORMAL;
    }
  }

  /**
   * Get human-readable diagnosis description.
   *
   * @return description string
   */
  private String getDiagnosisDescription() {
    switch (diagnosis) {
      case NORMAL:
        return "Stable injectivity, no conformance issues detected.";
      case FRACTURE_GROWTH:
        return "Hall slope decreased — possible fracture growth or improved injectivity. "
            + "Risk of out-of-zone fracture extension.";
      case PLUGGING:
        return "Hall slope increased — possible near-wellbore plugging, scaling, or skin increase.";
      case OUT_OF_ZONE_SUSPECTED:
        return "Injection profile shows significant out-of-zone injection — "
            + String.format("%.1f%% going to non-target zones.", getOutOfZoneFraction() * 100);
      case INSUFFICIENT_DATA:
      default:
        return "Insufficient data for diagnosis (need at least 5 data points).";
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    calculateHallPlot();
  }
}
