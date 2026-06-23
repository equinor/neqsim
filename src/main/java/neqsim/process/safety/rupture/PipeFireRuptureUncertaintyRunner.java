package neqsim.process.safety.rupture;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic uncertainty and sensitivity runner for pipe fire-rupture studies.
 *
 * <p>
 * The runner perturbs a small set of high-impact inputs and records the rupture-time spread. It is a lightweight
 * screening tool for agentic handoffs; formal studies should replace the default perturbations with project-specific
 * uncertainty distributions and acceptance criteria.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class PipeFireRuptureUncertaintyRunner implements Serializable {
  private static final long serialVersionUID = 1L;

  private final double timeStepSeconds;
  private final double maxTimeSeconds;
  private final boolean spreadsheetGasThermalMass;

  /**
   * Creates an uncertainty runner.
   *
   * @param timeStepSeconds calculation time step in seconds
   * @param maxTimeSeconds maximum simulation time in seconds
   * @param spreadsheetGasThermalMass true to include spreadsheet gas thermal mass
   */
  public PipeFireRuptureUncertaintyRunner(double timeStepSeconds, double maxTimeSeconds,
      boolean spreadsheetGasThermalMass) {
    validatePositive(timeStepSeconds, "timeStepSeconds");
    validatePositive(maxTimeSeconds, "maxTimeSeconds");
    this.timeStepSeconds = timeStepSeconds;
    this.maxTimeSeconds = maxTimeSeconds;
    this.spreadsheetGasThermalMass = spreadsheetGasThermalMass;
  }

  /**
   * Runs the default deterministic uncertainty cases.
   *
   * @param dataSource study data source
   * @return uncertainty summary
   */
  public UncertaintySummary run(PipeFireRuptureDataSource dataSource) {
    if (dataSource == null || !dataSource.readiness().isReadyForCalculation()) {
      return new UncertaintySummary(Collections.<CaseResult>emptyList(), "Data source is not ready for calculation.");
    }
    List<CaseResult> cases = new ArrayList<CaseResult>();
    cases.add(runCase("base", dataSource.getInput(), dataSource.getScenario(), dataSource));
    cases.add(runCase("wall_thickness_minus_10_pct", scaleWall(dataSource.getInput(), 0.90), dataSource.getScenario(),
	dataSource));
    cases.add(runCase("wall_thickness_plus_10_pct", scaleWall(dataSource.getInput(), 1.10), dataSource.getScenario(),
	dataSource));
    cases.add(runCase("corrosion_allowance_plus_1_mm", addCorrosionAllowance(dataSource.getInput(), 0.001),
	dataSource.getScenario(), dataSource));
    cases.add(runCase("fire_heat_flux_factor_minus_20_pct", dataSource.getInput(),
	scaleHeatFlux(dataSource.getScenario(), 0.80), dataSource));
    cases.add(runCase("initial_temperature_minus_20_C", shiftInitialTemperature(dataSource.getInput(), -20.0),
	dataSource.getScenario(), dataSource));
    cases.add(runCase("initial_temperature_plus_20_C", shiftInitialTemperature(dataSource.getInput(), 20.0),
	dataSource.getScenario(), dataSource));
    return new UncertaintySummary(cases, "Deterministic one-at-a-time perturbation screening.");
  }

  /**
   * Runs one perturbation case.
   *
   * @param caseName case name
   * @param input perturbed input
   * @param scenario perturbed scenario
   * @param dataSource base data source
   * @return case result
   */
  private CaseResult runCase(String caseName, PipeFireRuptureInput input, PipeFireRuptureScenario scenario,
      PipeFireRuptureDataSource dataSource) {
    PipeFireRuptureResult result = PipeFireRuptureStudy
	.builder(input, dataSource.getMaterial(), scenario, dataSource.getPressureProfile())
	.timeStepSeconds(timeStepSeconds).maxTimeSeconds(maxTimeSeconds)
	.spreadsheetGasThermalMass(spreadsheetGasThermalMass).build().run();
    return new CaseResult(caseName, result.getStatus().name(), result.getRuptureTimeSeconds(),
	result.getRupturePressureBarg());
  }

  /**
   * Scales nominal wall thickness.
   *
   * @param input base input
   * @param factor wall-thickness factor
   * @return perturbed input
   */
  private static PipeFireRuptureInput scaleWall(PipeFireRuptureInput input, double factor) {
    return input.toBuilder().nominalWallThickness(input.getNominalWallThicknessM() * factor, "m").build();
  }

  /**
   * Adds corrosion allowance.
   *
   * @param input base input
   * @param deltaM added allowance in m
   * @return perturbed input
   */
  private static PipeFireRuptureInput addCorrosionAllowance(PipeFireRuptureInput input, double deltaM) {
    double allowance = Math.max(0.0, input.getCorrosionAllowanceM() + deltaM);
    double maximumAllowance = Math.max(0.0, input.getNominalWallThicknessM() * 0.5);
    return input.toBuilder().corrosionAllowance(Math.min(allowance, maximumAllowance), "m").build();
  }

  /**
   * Shifts initial temperature.
   *
   * @param input base input
   * @param deltaC temperature shift in degrees Celsius
   * @return perturbed input
   */
  private static PipeFireRuptureInput shiftInitialTemperature(PipeFireRuptureInput input, double deltaC) {
    return input.toBuilder().initialTemperatureC(input.getInitialTemperatureC() + deltaC).build();
  }

  /**
   * Scales passive heat-flux factor.
   *
   * @param scenario base scenario
   * @param factor heat-flux factor multiplier
   * @return perturbed scenario
   */
  private static PipeFireRuptureScenario scaleHeatFlux(PipeFireRuptureScenario scenario, double factor) {
    double scaledFactor = Math.max(0.0, Math.min(1.0, scenario.getPassiveProtectionHeatFluxFactor() * factor));
    return scenario.withPassiveProtectionFactor(scaledFactor);
  }

  /**
   * Validates a positive value.
   *
   * @param value value to validate
   * @param name parameter name
   * @throws IllegalArgumentException if value is invalid
   */
  private static void validatePositive(double value, String name) {
    if (value <= 0.0 || Double.isNaN(value) || Double.isInfinite(value)) {
      throw new IllegalArgumentException(name + " must be positive and finite");
    }
  }

  /** One uncertainty case result. */
  public static final class CaseResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String caseName;
    private final String status;
    private final double ruptureTimeSeconds;
    private final double rupturePressureBarg;

    /**
     * Creates a case result.
     *
     * @param caseName case name
     * @param status result status
     * @param ruptureTimeSeconds rupture time in seconds, or NaN
     * @param rupturePressureBarg rupture pressure in barg, or NaN
     */
    public CaseResult(String caseName, String status, double ruptureTimeSeconds, double rupturePressureBarg) {
      this.caseName = caseName == null ? "" : caseName;
      this.status = status == null ? "" : status;
      this.ruptureTimeSeconds = ruptureTimeSeconds;
      this.rupturePressureBarg = rupturePressureBarg;
    }

    /**
     * Gets rupture time.
     *
     * @return rupture time in seconds
     */
    public double getRuptureTimeSeconds() {
      return ruptureTimeSeconds;
    }

    /**
     * Converts case result to a map.
     *
     * @return ordered map representation
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("caseName", caseName);
      map.put("status", status);
      map.put("ruptureTimeSeconds", finiteOrNull(ruptureTimeSeconds));
      map.put("ruptureTimeMinutes", finiteOrNull(ruptureTimeSeconds / 60.0));
      map.put("rupturePressureBarg", finiteOrNull(rupturePressureBarg));
      return map;
    }
  }

  /** Uncertainty summary for default perturbation cases. */
  public static final class UncertaintySummary implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<CaseResult> cases;
    private final String basis;

    /**
     * Creates an uncertainty summary.
     *
     * @param cases case result list
     * @param basis basis text
     */
    public UncertaintySummary(List<CaseResult> cases, String basis) {
      this.cases = Collections.unmodifiableList(new ArrayList<CaseResult>(cases));
      this.basis = basis == null ? "" : basis;
    }

    /**
     * Gets case results.
     *
     * @return immutable case results
     */
    public List<CaseResult> getCases() {
      return cases;
    }

    /**
     * Gets P50 rupture time.
     *
     * @return P50 time in seconds, or NaN
     */
    public double getP50RuptureTimeSeconds() {
      return percentile(50.0);
    }

    /**
     * Converts summary to a map.
     *
     * @return ordered map representation
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("schemaVersion", "pipe_fire_rupture_uncertainty.v1");
      map.put("basis", basis);
      map.put("p10RuptureTimeSeconds", finiteOrNull(percentile(10.0)));
      map.put("p50RuptureTimeSeconds", finiteOrNull(percentile(50.0)));
      map.put("p90RuptureTimeSeconds", finiteOrNull(percentile(90.0)));
      List<Map<String, Object>> caseMaps = new ArrayList<Map<String, Object>>();
      for (CaseResult item : cases) {
	caseMaps.add(item.toMap());
      }
      map.put("cases", caseMaps);
      return map;
    }

    /**
     * Converts summary to JSON.
     *
     * @return JSON representation
     */
    public String toJson() {
      return new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(toMap());
    }

    /**
     * Calculates a finite rupture-time percentile.
     *
     * @param percentile percentile from 0 to 100
     * @return percentile value or NaN
     */
    private double percentile(double percentile) {
      List<Double> values = new ArrayList<Double>();
      for (CaseResult item : cases) {
	if (Double.isFinite(item.getRuptureTimeSeconds())) {
	  values.add(Double.valueOf(item.getRuptureTimeSeconds()));
	}
      }
      if (values.isEmpty()) {
	return Double.NaN;
      }
      Collections.sort(values, new Comparator<Double>() {
	@Override
	public int compare(Double first, Double second) {
	  return first.compareTo(second);
	}
      });
      double rank = Math.max(0.0, Math.min(100.0, percentile)) / 100.0 * (values.size() - 1);
      int lower = (int) Math.floor(rank);
      int upper = (int) Math.ceil(rank);
      if (lower == upper) {
	return values.get(lower).doubleValue();
      }
      double fraction = rank - lower;
      return values.get(lower).doubleValue() * (1.0 - fraction) + values.get(upper).doubleValue() * fraction;
    }
  }

  /**
   * Converts finite values to boxed values and non-finite values to null.
   *
   * @param value numeric value
   * @return boxed value or null
   */
  private static Object finiteOrNull(double value) {
    return Double.isFinite(value) ? Double.valueOf(value) : null;
  }
}
