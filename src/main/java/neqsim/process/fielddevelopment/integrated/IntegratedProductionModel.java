package neqsim.process.fielddevelopment.integrated;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Integrated "reservoir to market" production model.
 *
 * <p>
 * This high-level orchestrator couples the four classic production layers - reservoir, wells, flowlines/gathering
 * network and the export boundary - into a single self-consistent model, in the spirit of Petex IPM (MBAL + PROSPER +
 * GAP) and Schlumberger Pipesim. Each well is represented by a fast {@link WellDeliverabilityCurve} surrogate; the
 * gathering hydraulics by {@link FlowlineBranch}; the reservoir depletion by a {@link ReservoirDrive}; and the whole
 * system is balanced by the {@link NetworkNewtonSolver}.
 * </p>
 *
 * <p>
 * Two workflows are provided:
 * </p>
 * <ul>
 * <li>{@link #solve()} - a single steady-state well-to-export balance at the current reservoir pressures.</li>
 * <li>{@link #runProfile(double, double)} - a time-marched production profile in which each well depletes its own
 * reservoir drive, so rates decline and the field life emerges naturally.</li>
 * </ul>
 *
 * <p>
 * A simple economics layer (hydrocarbon price) turns the export rate into revenue, giving the objective used by
 * {@link ReservoirToMarketOptimizer}.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 * @see NetworkNewtonSolver
 * @see ReservoirDrive
 * @see WellDeliverabilityCurve
 * @see ReservoirToMarketOptimizer
 */
public class IntegratedProductionModel implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Export boundary node name. */
  public static final String EXPORT_NODE = "EXPORT";

  /**
   * Internal representation of a single well and its tie-in.
   *
   * @author NeqSim
   * @version 1.0
   */
  public static class WellUnit implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final ReservoirDrive drive;
    private final WellBranch wellBranch;
    private FlowlineBranch flowlineBranch;
    private final String reservoirNode;
    private final String wellheadNode;
    private double lastRate; // Sm3/day

    /**
     * Creates a well unit.
     *
     * @param name well name
     * @param drive reservoir drive feeding the well
     * @param wellBranch well deliverability branch
     * @param flowlineBranch flowline branch to the export node
     * @param reservoirNode reservoir node name
     * @param wellheadNode wellhead node name
     */
    WellUnit(String name, ReservoirDrive drive, WellBranch wellBranch, FlowlineBranch flowlineBranch,
        String reservoirNode, String wellheadNode) {
      this.name = name;
      this.drive = drive;
      this.wellBranch = wellBranch;
      this.flowlineBranch = flowlineBranch;
      this.reservoirNode = reservoirNode;
      this.wellheadNode = wellheadNode;
    }

    /**
     * Returns the well name.
     *
     * @return well name
     */
    public String getName() {
      return name;
    }

    /**
     * Returns the reservoir drive.
     *
     * @return reservoir drive
     */
    public ReservoirDrive getDrive() {
      return drive;
    }

    /**
     * Returns the well branch.
     *
     * @return well branch
     */
    public WellBranch getWellBranch() {
      return wellBranch;
    }

    /**
     * Returns the flowline branch.
     *
     * @return flowline branch
     */
    public FlowlineBranch getFlowlineBranch() {
      return flowlineBranch;
    }

    /**
     * Returns the most recent solved rate.
     *
     * @return rate in Sm3/day
     */
    public double getLastRate() {
      return lastRate;
    }
  }

  private final String name;
  private double exportPressure = 30.0; // bara
  private final List<WellUnit> wells = new ArrayList<WellUnit>();
  private double hydrocarbonPrice = 0.0; // currency per Sm3
  private double energyPerSm3 = 0.0; // kWh per Sm3 (compression/processing energy intensity)
  private double co2PerSm3 = 0.0; // kg CO2 per Sm3

  /**
   * Creates an integrated production model.
   *
   * @param name field/model name
   */
  public IntegratedProductionModel(String name) {
    this.name = name;
  }

  /**
   * Sets the export boundary pressure (separator/pipeline header).
   *
   * @param pressureBara export pressure in bara
   * @return this model for chaining
   */
  public IntegratedProductionModel setExportPressure(double pressureBara) {
    this.exportPressure = pressureBara;
    return this;
  }

  /**
   * Adds a well with a direct (low-resistance) tie-in to the export node.
   *
   * @param wellName unique well name
   * @param drive reservoir drive feeding the well
   * @param curve well deliverability curve
   * @return the created well unit (for further configuration)
   */
  public WellUnit addWell(String wellName, ReservoirDrive drive, WellDeliverabilityCurve curve) {
    FlowlineBranch defaultLine = new FlowlineBranch(wellName + "_line", wellName + "_WH", EXPORT_NODE, 0.0, 1.0e-10,
        0.0);
    return addWell(wellName, drive, curve, defaultLine);
  }

  /**
   * Adds a well with an explicit flowline to the export node.
   *
   * @param wellName unique well name
   * @param drive reservoir drive feeding the well
   * @param curve well deliverability curve
   * @param flowline flowline branch from the wellhead to the export node
   * @return the created well unit (for further configuration)
   */
  public WellUnit addWell(String wellName, ReservoirDrive drive, WellDeliverabilityCurve curve,
      FlowlineBranch flowline) {
    String resNode = wellName + "_RES";
    String whNode = wellName + "_WH";
    WellBranch wb = new WellBranch(wellName + "_well", resNode, whNode, curve, drive.getReservoirPressure());
    WellUnit unit = new WellUnit(wellName, drive, wb, flowline, resNode, whNode);
    wells.add(unit);
    return unit;
  }

  /**
   * Sets the hydrocarbon sales price used for revenue calculations.
   *
   * @param pricePerSm3 price in currency per Sm3
   * @return this model for chaining
   */
  public IntegratedProductionModel setHydrocarbonPrice(double pricePerSm3) {
    this.hydrocarbonPrice = pricePerSm3;
    return this;
  }

  /**
   * Sets the processing/compression energy intensity for energy and emission reporting.
   *
   * @param kWhPerSm3 energy intensity in kWh per Sm3 of export
   * @return this model for chaining
   */
  public IntegratedProductionModel setEnergyIntensity(double kWhPerSm3) {
    this.energyPerSm3 = kWhPerSm3;
    return this;
  }

  /**
   * Sets the CO2 emission intensity for emission reporting.
   *
   * @param kgCo2PerSm3 emission intensity in kg CO2 per Sm3 of export
   * @return this model for chaining
   */
  public IntegratedProductionModel setEmissionIntensity(double kgCo2PerSm3) {
    this.co2PerSm3 = kgCo2PerSm3;
    return this;
  }

  /**
   * Returns the wells in the model.
   *
   * @return list of well units
   */
  public List<WellUnit> getWells() {
    return wells;
  }

  /**
   * Builds a fresh network solver from the current model state and reservoir pressures.
   *
   * @return a configured network solver
   */
  private NetworkNewtonSolver buildSolver() {
    NetworkNewtonSolver solver = new NetworkNewtonSolver();
    solver.addNode(NetworkNode.sink(EXPORT_NODE, exportPressure));
    for (WellUnit w : wells) {
      double pRes = w.drive.getReservoirPressure();
      solver.addNode(NetworkNode.reservoir(w.reservoirNode, pRes));
      double whGuess = Math.max(exportPressure + 1.0, 0.5 * (pRes + exportPressure));
      solver.addNode(NetworkNode.manifold(w.wellheadNode, whGuess));
      solver.addBranch(w.wellBranch);
      solver.addBranch(w.flowlineBranch);
    }
    solver.setTolerance(10.0);
    solver.setMinPressure(Math.max(1.0, exportPressure));
    return solver;
  }

  /**
   * Solves the steady-state well-to-export balance at the current reservoir pressures.
   *
   * @return the integrated solution result
   */
  public IntegratedSolveResult solve() {
    NetworkNewtonSolver solver = buildSolver();
    NetworkNewtonSolver.NetworkSolutionResult net = solver.solve();
    double fieldRate = 0.0;
    Map<String, Double> wellRates = new LinkedHashMap<String, Double>();
    for (WellUnit w : wells) {
      double q = net.getBranchFlows().containsKey(w.flowlineBranch.getName())
          ? net.getBranchFlows().get(w.flowlineBranch.getName())
          : 0.0;
      w.lastRate = q;
      wellRates.put(w.name, q);
      fieldRate += q;
    }
    double revenue = fieldRate * hydrocarbonPrice;
    double energy = fieldRate * energyPerSm3;
    double emissions = fieldRate * co2PerSm3;
    return new IntegratedSolveResult(net.isConverged(), net.getIterations(), fieldRate, wellRates,
        net.getNodePressures(), revenue, energy, emissions, net.getMethod());
  }

  /**
   * Marches a production profile forward in time, depleting each well's reservoir drive.
   *
   * @param years total horizon in years
   * @param dtYears time-step length in years
   * @return the production profile
   */
  public ProductionProfile runProfile(double years, double dtYears) {
    ProductionProfile profile = new ProductionProfile();
    int nSteps = (int) Math.ceil(years / dtYears);
    double dtDays = dtYears * 365.25;
    double cumulative = 0.0;
    double cumulativeRevenue = 0.0;
    for (int step = 0; step <= nSteps; step++) {
      double t = step * dtYears;
      IntegratedSolveResult res = solve();
      double rate = res.getFieldRate();
      profile.add(t, rate, res.getRevenue(), res.getEnergyKWhPerDay(), res.getEmissionsKgPerDay(),
          avgReservoirPressure());
      // Deplete each well's drive by the volume it produced over the step.
      for (WellUnit w : wells) {
        double producedVol = w.lastRate * dtDays;
        w.drive.produce(producedVol, dtDays);
        cumulative += producedVol;
      }
      cumulativeRevenue += rate * dtDays * hydrocarbonPrice;
    }
    profile.setCumulativeProduction(cumulative);
    profile.setCumulativeRevenue(cumulativeRevenue);
    return profile;
  }

  /**
   * Returns the production-weighted average reservoir pressure across wells.
   *
   * @return average reservoir pressure in bara
   */
  private double avgReservoirPressure() {
    if (wells.isEmpty()) {
      return 0.0;
    }
    double sum = 0.0;
    for (WellUnit w : wells) {
      sum += w.drive.getReservoirPressure();
    }
    return sum / wells.size();
  }

  /**
   * Returns the model name.
   *
   * @return model name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns a compact JSON summary of the current steady-state solution.
   *
   * @return JSON string
   */
  public String toJson() {
    IntegratedSolveResult res = solve();
    StringBuilder sb = new StringBuilder();
    sb.append("{\"schemaVersion\":\"1.0\",\"model\":\"").append(name).append("\",");
    sb.append("\"converged\":").append(res.isConverged()).append(",");
    sb.append("\"fieldRateSm3PerDay\":").append(fmt(res.getFieldRate())).append(",");
    sb.append("\"exportPressureBara\":").append(fmt(exportPressure)).append(",");
    sb.append("\"revenue\":").append(fmt(res.getRevenue())).append(",");
    sb.append("\"wells\":{");
    int i = 0;
    for (Map.Entry<String, Double> e : res.getWellRates().entrySet()) {
      if (i++ > 0) {
        sb.append(",");
      }
      sb.append("\"").append(e.getKey()).append("\":").append(fmt(e.getValue()));
    }
    sb.append("}}");
    return sb.toString();
  }

  /**
   * Formats a double for JSON output.
   *
   * @param v value
   * @return formatted string
   */
  private String fmt(double v) {
    if (Double.isNaN(v) || Double.isInfinite(v)) {
      return "null";
    }
    return String.format(java.util.Locale.US, "%.6g", v);
  }
}
