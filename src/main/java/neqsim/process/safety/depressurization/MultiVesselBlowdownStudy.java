package neqsim.process.safety.depressurization;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.safety.depressurization.DepressurizationSimulator.DepressurizationResult;

/**
 * Coupled simultaneous blowdown of multiple vessels into a shared flare/disposal header per API 521 §7.
 *
 * <p>
 * Each registered source is blown down (or supplied as a pre-computed {@link DepressurizationResult}) and the
 * instantaneous discharge mass flows are superimposed on a common time grid to build the combined relief load profile
 * seen by the flare header. The study reports:
 * <ul>
 * <li>Peak total mass flow and the time at which it occurs (the controlling load case for header sizing)</li>
 * <li>Header gas velocity and Mach number at the peak (API 521 §7 / NORSOK P-002 limit, default 0.7)</li>
 * <li>Per-source contribution to the peak load</li>
 * </ul>
 *
 * <p>
 * <b>References:</b> API STD 521 (7th Ed) §7 Disposal Systems, NORSOK P-002 (flare/blowdown line velocity criteria).
 *
 * @author ESOL
 * @version 1.0
 */
public class MultiVesselBlowdownStudy implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Universal gas constant [J/(mol·K)]. */
  private static final double R_GAS = 8.314;

  private final Map<String, DepressurizationSimulator> sources = new LinkedHashMap<>();
  private final Map<String, DepressurizationResult> precomputed = new LinkedHashMap<>();

  private double headerDiameterM = Double.NaN;
  private double headerPressureBara = 1.5;
  private double headerTemperatureK = 288.15;
  private double headerMolarMassKgPerMol = 0.020;
  private double headerGamma = 1.30;
  private double maxAllowableMach = 0.70;
  private double gridStepS = 1.0;

  /**
   * Register a vessel by its configured depressurization simulator. The simulator is run when {@link #run()} is called.
   *
   * @param name unique source name
   * @param simulator configured depressurization simulator
   * @return this study for chaining
   */
  public MultiVesselBlowdownStudy addSource(String name, DepressurizationSimulator simulator) {
    if (name == null || simulator == null) {
      throw new IllegalArgumentException("name and simulator must not be null");
    }
    sources.put(name, simulator);
    return this;
  }

  /**
   * Register a vessel by a pre-computed depressurization result (avoids re-running the transient).
   *
   * @param name unique source name
   * @param result a completed depressurization result
   * @return this study for chaining
   */
  public MultiVesselBlowdownStudy addSourceResult(String name, DepressurizationResult result) {
    if (name == null || result == null) {
      throw new IllegalArgumentException("name and result must not be null");
    }
    precomputed.put(name, result);
    return this;
  }

  /**
   * Configure the flare header geometry and gas state used for the Mach check at the peak load.
   *
   * @param diameterM header internal diameter in m
   * @param pressureBara header absolute pressure in bara
   * @param temperatureK header gas temperature in K
   * @param molarMassKgPerMol average gas molar mass in kg/mol
   * @param gamma gas isentropic exponent (Cp/Cv)
   * @return this study for chaining
   */
  public MultiVesselBlowdownStudy setHeader(double diameterM, double pressureBara, double temperatureK,
      double molarMassKgPerMol, double gamma) {
    if (diameterM <= 0.0) {
      throw new IllegalArgumentException("header diameter must be positive");
    }
    this.headerDiameterM = diameterM;
    this.headerPressureBara = pressureBara;
    this.headerTemperatureK = temperatureK;
    this.headerMolarMassKgPerMol = molarMassKgPerMol;
    this.headerGamma = gamma;
    return this;
  }

  /**
   * Set the maximum allowable header Mach number (default 0.70 per API 521 / NORSOK P-002).
   *
   * @param mach maximum allowable Mach number
   * @return this study for chaining
   */
  public MultiVesselBlowdownStudy setMaxAllowableMach(double mach) {
    this.maxAllowableMach = mach;
    return this;
  }

  /**
   * Set the common time-grid step used to superimpose source profiles (default 1 s).
   *
   * @param dt grid step in s
   * @return this study for chaining
   */
  public MultiVesselBlowdownStudy setGridStep(double dt) {
    if (dt <= 0.0) {
      throw new IllegalArgumentException("grid step must be positive");
    }
    this.gridStepS = dt;
    return this;
  }

  /**
   * Run all sources and build the combined flare load profile.
   *
   * @return the aggregated multi-vessel blowdown result
   */
  public MultiVesselBlowdownResult run() {
    Map<String, DepressurizationResult> all = new LinkedHashMap<>(precomputed);
    for (Map.Entry<String, DepressurizationSimulator> entry : sources.entrySet()) {
      all.put(entry.getKey(), entry.getValue().run());
    }
    if (all.isEmpty()) {
      throw new IllegalStateException("no blowdown sources registered");
    }

    double endTime = 0.0;
    for (DepressurizationResult r : all.values()) {
      if (!r.time.isEmpty()) {
	endTime = Math.max(endTime, r.time.get(r.time.size() - 1));
      }
    }

    List<Double> grid = new ArrayList<>();
    for (double t = 0.0; t <= endTime + 1.0e-9; t += gridStepS) {
      grid.add(t);
    }
    if (grid.isEmpty()) {
      grid.add(0.0);
    }

    List<Double> total = new ArrayList<>();
    Map<String, List<Double>> perSource = new LinkedHashMap<>();
    for (String name : all.keySet()) {
      perSource.put(name, new ArrayList<Double>());
    }

    double peakTotal = 0.0;
    double peakTime = 0.0;
    int peakIndex = 0;
    for (int i = 0; i < grid.size(); i++) {
      double t = grid.get(i);
      double sum = 0.0;
      for (Map.Entry<String, DepressurizationResult> entry : all.entrySet()) {
	double q = interpolateMassFlow(entry.getValue(), t);
	perSource.get(entry.getKey()).add(q);
	sum += q;
      }
      total.add(sum);
      if (sum > peakTotal) {
	peakTotal = sum;
	peakTime = t;
	peakIndex = i;
      }
    }

    Map<String, Double> peakContribution = new LinkedHashMap<>();
    for (Map.Entry<String, List<Double>> entry : perSource.entrySet()) {
      peakContribution.put(entry.getKey(), entry.getValue().get(peakIndex));
    }

    double velocity = Double.NaN;
    double mach = Double.NaN;
    boolean machOk = true;
    if (!Double.isNaN(headerDiameterM)) {
      double area = Math.PI * 0.25 * headerDiameterM * headerDiameterM;
      double pPa = headerPressureBara * 1.0e5;
      double rho = pPa * headerMolarMassKgPerMol / (R_GAS * headerTemperatureK);
      velocity = peakTotal / (rho * area);
      double sonic = Math.sqrt(headerGamma * R_GAS * headerTemperatureK / headerMolarMassKgPerMol);
      mach = velocity / sonic;
      machOk = mach <= maxAllowableMach;
    }

    return new MultiVesselBlowdownResult(grid, total, perSource, peakTotal, peakTime, peakContribution, velocity, mach,
	maxAllowableMach, machOk);
  }

  /**
   * Step-linear interpolation of a source's discharge mass flow at the given time. Returns 0 outside the source's own
   * time window (a source that has finished blowing down no longer contributes).
   *
   * @param r the source result
   * @param t the query time in s
   * @return interpolated mass flow in kg/s
   */
  private double interpolateMassFlow(DepressurizationResult r, double t) {
    List<Double> times = r.time;
    List<Double> flows = r.massFlowKgPerS;
    if (times.isEmpty()) {
      return 0.0;
    }
    if (t <= times.get(0)) {
      return flows.get(0);
    }
    if (t >= times.get(times.size() - 1)) {
      return 0.0;
    }
    for (int i = 1; i < times.size(); i++) {
      if (t <= times.get(i)) {
	double t0 = times.get(i - 1);
	double t1 = times.get(i);
	double f0 = flows.get(i - 1);
	double f1 = flows.get(i);
	double frac = (t1 > t0) ? (t - t0) / (t1 - t0) : 0.0;
	return f0 + frac * (f1 - f0);
      }
    }
    return 0.0;
  }

  /**
   * Aggregated result of a simultaneous multi-vessel blowdown study.
   */
  public static class MultiVesselBlowdownResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<Double> timeS;
    private final List<Double> totalMassFlowKgPerS;
    private final Map<String, List<Double>> sourceMassFlowKgPerS;
    private final double peakTotalMassFlowKgPerS;
    private final double peakTimeS;
    private final Map<String, Double> peakContributionKgPerS;
    private final double headerVelocityMPerS;
    private final double headerMach;
    private final double maxAllowableMach;
    private final boolean headerMachAcceptable;

    MultiVesselBlowdownResult(List<Double> timeS, List<Double> totalMassFlowKgPerS,
	Map<String, List<Double>> sourceMassFlowKgPerS, double peakTotalMassFlowKgPerS, double peakTimeS,
	Map<String, Double> peakContributionKgPerS, double headerVelocityMPerS, double headerMach,
	double maxAllowableMach, boolean headerMachAcceptable) {
      this.timeS = timeS;
      this.totalMassFlowKgPerS = totalMassFlowKgPerS;
      this.sourceMassFlowKgPerS = sourceMassFlowKgPerS;
      this.peakTotalMassFlowKgPerS = peakTotalMassFlowKgPerS;
      this.peakTimeS = peakTimeS;
      this.peakContributionKgPerS = peakContributionKgPerS;
      this.headerVelocityMPerS = headerVelocityMPerS;
      this.headerMach = headerMach;
      this.maxAllowableMach = maxAllowableMach;
      this.headerMachAcceptable = headerMachAcceptable;
    }

    /**
     * Common time grid in seconds.
     *
     * @return time grid
     */
    public List<Double> getTimeS() {
      return timeS;
    }

    /**
     * Combined header mass flow at each grid time in kg/s.
     *
     * @return total mass flow profile
     */
    public List<Double> getTotalMassFlowKgPerS() {
      return totalMassFlowKgPerS;
    }

    /**
     * Per-source mass flow profiles in kg/s.
     *
     * @return map of source name to mass flow profile
     */
    public Map<String, List<Double>> getSourceMassFlowKgPerS() {
      return sourceMassFlowKgPerS;
    }

    /**
     * Peak combined header mass flow in kg/s (controlling load case).
     *
     * @return peak total mass flow
     */
    public double getPeakTotalMassFlowKgPerS() {
      return peakTotalMassFlowKgPerS;
    }

    /**
     * Time of the peak combined load in seconds.
     *
     * @return peak time
     */
    public double getPeakTimeS() {
      return peakTimeS;
    }

    /**
     * Per-source contribution to the peak combined load in kg/s.
     *
     * @return map of source name to contribution at peak
     */
    public Map<String, Double> getPeakContributionKgPerS() {
      return peakContributionKgPerS;
    }

    /**
     * Header gas velocity at the peak load in m/s (NaN if header not configured).
     *
     * @return header velocity
     */
    public double getHeaderVelocityMPerS() {
      return headerVelocityMPerS;
    }

    /**
     * Header gas Mach number at the peak load (NaN if header not configured).
     *
     * @return header Mach number
     */
    public double getHeaderMach() {
      return headerMach;
    }

    /**
     * Maximum allowable header Mach number used for the acceptance check.
     *
     * @return allowable Mach limit
     */
    public double getMaxAllowableMach() {
      return maxAllowableMach;
    }

    /**
     * Whether the header Mach number is within the allowable limit.
     *
     * @return true if acceptable or header not configured
     */
    public boolean isHeaderMachAcceptable() {
      return headerMachAcceptable;
    }

    /**
     * Build a brief human-readable summary.
     *
     * @return summary string
     */
    public String summary() {
      StringBuilder sb = new StringBuilder();
      sb.append("Multi-vessel blowdown (API 521 §7):\n");
      sb.append(
	  String.format("  Peak total relief load : %.3f kg/s at t = %.0f s%n", peakTotalMassFlowKgPerS, peakTimeS));
      for (Map.Entry<String, Double> entry : peakContributionKgPerS.entrySet()) {
	sb.append(String.format("    %-20s : %.3f kg/s%n", entry.getKey(), entry.getValue()));
      }
      if (!Double.isNaN(headerMach)) {
	sb.append(String.format("  Header velocity        : %.1f m/s%n", headerVelocityMPerS));
	sb.append(String.format("  Header Mach            : %.3f (limit %.2f) -> %s%n", headerMach, maxAllowableMach,
	    headerMachAcceptable ? "OK" : "EXCEEDED"));
      }
      return sb.toString();
    }
  }
}
